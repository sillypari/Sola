package dev.sola.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Records incoming raw CAN frames to disk in standard SocketCAN / candump format.
 *
 * <p>Frames are buffered through a background writer thread so high-frequency
 * bursts (~1000 frames/s) never block the gRPC delivery thread or cause UI stutter.
 *
 * <p>Output format is standard Linux SocketCAN log format:
 * {@code (1793961869.837330) vcan0 11C#0688E1C726042000}
 * This format can be replayed directly via {@code canplayer}, opened in SavvyCAN,
 * or parsed with python-can ({@code can.LogReader}).
 */
public final class CanRecorder {

    private static final int QUEUE_CAPACITY = 20_000;
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB write buffer

    private static final class QueuedFrame {
        final long timeMs;
        final int id;
        final int dlc;
        final byte[] data;

        QueuedFrame(long timeMs, int id, int dlc, byte[] data) {
            this.timeMs = timeMs;
            this.id = id;
            this.dlc = dlc;
            this.data = data;
        }
    }

    // Special poison pill to gracefully terminate the worker thread
    private static final QueuedFrame POISON_PILL = new QueuedFrame(0, 0, 0, new byte[0]);

    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicLong frameCount = new AtomicLong(0);
    private final AtomicLong bytesWritten = new AtomicLong(0);
    private final AtomicLong droppedFrames = new AtomicLong(0);

    private volatile File currentFile;
    private volatile long startTimeMs;
    private volatile Thread workerThread;
    private BlockingQueue<QueuedFrame> queue;

    public CanRecorder() {
    }

    /**
     * Starts recording CAN frames to the given target file.
     *
     * @param targetFile destination log file.
     * @return true if recording successfully started, false if already recording or error.
     */
    public synchronized boolean start(File targetFile) {
        if (recording.get()) {
            return false;
        }
        if (targetFile == null) {
            return false;
        }

        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        this.currentFile = targetFile;
        this.startTimeMs = System.currentTimeMillis();
        this.frameCount.set(0);
        this.bytesWritten.set(0);
        this.droppedFrames.set(0);
        this.queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.recording.set(true);

        this.workerThread = new Thread(this::writerLoop, "CanRecorder-Writer");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
        return true;
    }

    /**
     * Records a frame if active. Thread-safe and non-blocking for callers.
     */
    public void record(Can.Frame frame) {
        if (!recording.get() || frame == null) {
            return;
        }

        byte[] copy = null;
        if (frame.data != null) {
            int len = Math.min(frame.dlc, frame.data.length);
            copy = new byte[len];
            System.arraycopy(frame.data, 0, copy, 0, len);
        }

        QueuedFrame qf = new QueuedFrame(System.currentTimeMillis(), frame.id, frame.dlc, copy);
        BlockingQueue<QueuedFrame> q = this.queue;
        if (q != null && !q.offer(qf)) {
            // Queue full - bump dropped count
            droppedFrames.incrementAndGet();
        }
    }

    /**
     * Stops the active recording session, flushes remaining frames, and closes the file.
     *
     * @return the saved File, or null if was not recording.
     */
    public synchronized File stop() {
        if (!recording.compareAndSet(true, false)) {
            return null;
        }

        File saved = currentFile;
        BlockingQueue<QueuedFrame> q = this.queue;
        if (q != null) {
            q.offer(POISON_PILL);
        }

        Thread t = workerThread;
        if (t != null) {
            try {
                t.join(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            workerThread = null;
        }
        return saved;
    }

    public boolean isRecording() {
        return recording.get();
    }

    public File currentFile() {
        return currentFile;
    }

    public long frameCount() {
        return frameCount.get();
    }

    public long bytesWritten() {
        return bytesWritten.get();
    }

    public long droppedFrames() {
        return droppedFrames.get();
    }

    public long startTimeMs() {
        return startTimeMs;
    }

    public long elapsedMs() {
        if (!recording.get()) {
            return 0;
        }
        return Math.max(0, System.currentTimeMillis() - startTimeMs);
    }

    // ----------------------------------------------------------- background writer

    private void writerLoop() {
        File file = currentFile;
        if (file == null) {
            return;
        }

        try (FileOutputStream fos = new FileOutputStream(file, true);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(fos, StandardCharsets.UTF_8), BUFFER_SIZE)) {

            // Header comments with metadata
            String header = String.format(Locale.ROOT,
                    "# CAN Recording Session\n# Started: %d (%s)\n# Interface: vcan0\n# Format: (timestamp) vcan0 ID#DATA\n",
                    startTimeMs, new java.util.Date(startTimeMs).toString());
            writer.write(header);
            bytesWritten.addAndGet(header.length());

            StringBuilder line = new StringBuilder(64);
            long count = 0;
            long flushInterval = 0;

            while (true) {
                QueuedFrame qf = queue.take();
                if (qf == POISON_PILL) {
                    break;
                }

                line.setLength(0);
                double ts = qf.timeMs / 1000.0;
                line.append('(');
                line.append(String.format(Locale.ROOT, "%.6f", ts));
                line.append(") vcan0 ");

                if (qf.id > 0x7FF) {
                    line.append(String.format(Locale.ROOT, "%08X#", qf.id));
                } else {
                    line.append(String.format(Locale.ROOT, "%03X#", qf.id));
                }

                if (qf.data != null) {
                    for (int i = 0; i < qf.dlc && i < qf.data.length; i++) {
                        line.append(String.format(Locale.ROOT, "%02X", qf.data[i] & 0xFF));
                    }
                }
                line.append('\n');

                String str = line.toString();
                writer.write(str);
                bytesWritten.addAndGet(str.length());
                count++;
                frameCount.set(count);

                // Periodic flush every 250 frames or 500 ms
                if (++flushInterval >= 250) {
                    writer.flush();
                    flushInterval = 0;
                }
            }

            // Drain any lingering items remaining in queue
            QueuedFrame leftover;
            while ((leftover = queue.poll()) != null && leftover != POISON_PILL) {
                line.setLength(0);
                double ts = leftover.timeMs / 1000.0;
                line.append('(').append(String.format(Locale.ROOT, "%.6f", ts)).append(") vcan0 ");
                if (leftover.id > 0x7FF) {
                    line.append(String.format(Locale.ROOT, "%08X#", leftover.id));
                } else {
                    line.append(String.format(Locale.ROOT, "%03X#", leftover.id));
                }
                if (leftover.data != null) {
                    for (int i = 0; i < leftover.dlc && i < leftover.data.length; i++) {
                        line.append(String.format(Locale.ROOT, "%02X", leftover.data[i] & 0xFF));
                    }
                }
                line.append('\n');
                String str = line.toString();
                writer.write(str);
                bytesWritten.addAndGet(str.length());
                count++;
                frameCount.set(count);
            }

            writer.flush();
        } catch (IOException | InterruptedException e) {
            // Log / silence without crashing
        }
    }

    public static String formatDuration(long ms) {
        long s = ms / 1000;
        long m = s / 60;
        long h = m / 60;
        if (h > 0) {
            return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m % 60, s % 60);
        }
        return String.format(Locale.ROOT, "%02d:%02d", m, s % 60);
    }

    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(Locale.ROOT, "%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }
}

