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

    private static final String[] HEX_BYTES = new String[256];
    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    static {
        for (int i = 0; i < 256; i++) {
            HEX_BYTES[i] = "" + HEX_DIGITS[(i >>> 4) & 0x0F] + HEX_DIGITS[i & 0x0F];
        }
    }

    private static void appendHex3(StringBuilder sb, int id) {
        sb.append(HEX_DIGITS[(id >>> 8) & 0x0F]);
        sb.append(HEX_DIGITS[(id >>> 4) & 0x0F]);
        sb.append(HEX_DIGITS[id & 0x0F]);
        sb.append('#');
    }

    private static void appendHex8(StringBuilder sb, int id) {
        for (int i = 28; i >= 0; i -= 4) {
            sb.append(HEX_DIGITS[(id >>> i) & 0x0F]);
        }
        sb.append('#');
    }

    private static void appendTimestamp(StringBuilder sb, long timeMs) {
        long sec = timeMs / 1000;
        long millis = timeMs % 1000;
        sb.append(sec).append('.');
        if (millis < 10) sb.append("00");
        else if (millis < 100) sb.append('0');
        sb.append(millis).append("000");
    }

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
                line.append('(');
                appendTimestamp(line, qf.timeMs);
                line.append(") vcan0 ");

                if (qf.id > 0x7FF) {
                    appendHex8(line, qf.id);
                } else {
                    appendHex3(line, qf.id);
                }

                if (qf.data != null) {
                    int n = Math.min(qf.dlc, qf.data.length);
                    for (int i = 0; i < n; i++) {
                        line.append(HEX_BYTES[qf.data[i] & 0xFF]);
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
                line.append('(');
                appendTimestamp(line, leftover.timeMs);
                line.append(") vcan0 ");
                if (leftover.id > 0x7FF) {
                    appendHex8(line, leftover.id);
                } else {
                    appendHex3(line, leftover.id);
                }
                if (leftover.data != null) {
                    int n = Math.min(leftover.dlc, leftover.data.length);
                    for (int i = 0; i < n; i++) {
                        line.append(HEX_BYTES[leftover.data[i] & 0xFF]);
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

