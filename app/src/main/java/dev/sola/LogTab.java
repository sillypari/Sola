package dev.sola;

import android.content.ContentValues;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.sola.core.CanRecorder;
import dev.sola.core.Live;

/**
 * The raw frame log and CAN session recorder.
 *
 * <p>Newest frame first, with the id, the message name from the table, and the
 * payload bytes. Includes a non-blocking session recorder with multiple export
 * options:
 * <ul>
 *   <li>Native Android Share (Google Drive, WhatsApp, Email, AirDrop)</li>
 *   <li>1-click direct export to public Downloads folder ({@code Downloads/SolaCAN/})</li>
 *   <li>CSV tabular export ({@code timestamp,can_id,name,dlc,data_hex}) for Excel/Pandas</li>
 *   <li>Direct ADB pull from PC</li>
 * </ul>
 */
public final class LogTab implements Tab {

    private static final int MAX_LINES = 200;
    private static final int TOP_IDS = 10;
    private static final long RENDER_MS = 300;

    private final MainActivity activity;
    private final Bus bus;
    private final LinearLayout body;
    private final TextView status;
    private final TextView log;
    private final TextView busiest;

    // Recording UI
    private final Button recordBtn;
    private final TextView recordPill;
    private final TextView recordStats;
    private final LinearLayout savedListContainer;

    private final SimpleDateFormat clock = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
    private final SimpleDateFormat fileDate = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT);

    private boolean visible;
    private long lastRender;
    private File lastSavedFile;

    private final Bus.Listener listener = snapshot -> {
        if (visible) {
            render(snapshot);
        }
    };

    public LogTab(MainActivity activity, Conn conn, Bus bus) {
        this.activity = activity;
        this.bus = bus;
        this.body = Ui.column(activity);

        // ---------------- Recording Card ----------------
        LinearLayout recordCard = Ui.column(activity);
        recordCard.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        recordCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 12, 0));

        LinearLayout btnRow = Ui.row(activity);
        recordBtn = Ui.accent(activity, "Start Recording", v -> toggleRecording());
        recordPill = Ui.pill(activity, "IDLE", Ui.DIM);
        btnRow.addView(recordBtn);
        View spacer = new View(activity);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 10), 1));
        btnRow.addView(spacer);
        btnRow.addView(recordPill);
        recordCard.addView(btnRow);

        recordStats = Ui.text(activity, "Output: candump format (.log). Replayable via canplayer or SavvyCAN.", 11, Ui.DIM);
        recordStats.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));
        recordCard.addView(recordStats);

        savedListContainer = Ui.column(activity);
        recordCard.addView(savedListContainer);

        body.addView(recordCard);
        body.addView(Ui.space(activity, 10));

        // ---------------- Live Bus Feed ----------------
        LinearLayout busFeedCard = Ui.column(activity);
        busFeedCard.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        busFeedCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 12, 0));

        busFeedCard.addView(Ui.heading(activity, "Live Bus Throughput & Frames"));

        status = Ui.text(activity, "Waiting for frames ...", 12, Ui.DIM);
        status.setPadding(0, 0, 0, Ui.dp(activity, 4));
        busFeedCard.addView(status);

        busiest = Ui.text(activity, "-", 11, Ui.PRIMARY);
        busiest.setTypeface(android.graphics.Typeface.MONOSPACE);
        busFeedCard.addView(busiest);

        log = Ui.text(activity, "", 11, Ui.TEXT);
        log.setTypeface(android.graphics.Typeface.MONOSPACE);
        log.setMaxLines(8);
        log.setEllipsize(android.text.TextUtils.TruncateAt.END);
        log.setTextIsSelectable(true);
        log.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 6));
        busFeedCard.addView(log);

        TextView footer = Ui.note(activity,
                "Pull recordings to PC:\nadb pull /sdcard/Android/data/dev.sola/files/can_recordings/ .");
        footer.setTypeface(android.graphics.Typeface.MONOSPACE);
        footer.setPadding(0, Ui.dp(activity, 4), 0, 0);
        busFeedCard.addView(footer);

        body.addView(busFeedCard);

        refreshSavedRecordings();
    }

    @Override
    public View view() {
        return body;
    }

    @Override
    public void onShow() {
        visible = true;
        bus.attach(listener);
        lastRender = 0;
        updateRecordingUi();
        refreshSavedRecordings();
        render(bus.snapshot());
    }

    @Override
    public void onHide() {
        visible = false;
        bus.detach(listener);
    }

    // --------------------------------------------------------------- recording

    private File getRecordingsDir() {
        File dir = activity.getExternalFilesDir("can_recordings");
        if (dir == null) {
            dir = new File(activity.getFilesDir(), "can_recordings");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private void toggleRecording() {
        CanRecorder recorder = bus.recorder();
        if (recorder.isRecording()) {
            // Stop recording
            File saved = recorder.stop();
            lastSavedFile = saved;
            recordBtn.setText("Start Recording");
            recordBtn.setBackground(Ui.round(activity, Ui.ACCENT, 10, 0));
            recordBtn.setTextColor(0xFF06231C);
            Ui.pill(recordPill, "SAVED", Ui.ACCENT);
            if (saved != null) {
                recordStats.setText(String.format(Locale.ROOT,
                        "Saved: %s\nSize: %s | Frames: %d",
                        saved.getName(),
                        CanRecorder.formatBytes(saved.length()),
                        recorder.frameCount()));
                Toast.makeText(activity, "CAN session saved: " + saved.getName(), Toast.LENGTH_LONG).show();
            }
            refreshSavedRecordings();
        } else {
            // Start recording
            String filename = "candump_" + fileDate.format(new Date()) + ".log";
            File target = new File(getRecordingsDir(), filename);
            boolean ok = recorder.start(target);
            if (ok) {
                recordBtn.setText("Stop Recording");
                recordBtn.setBackground(Ui.round(activity, 0xFF3A1C1F, 10, 0xFF5A2A2E));
                recordBtn.setTextColor(0xFFFFD9DA);
                Ui.pill(recordPill, "REC ● LIVE", Ui.BAD);
                recordStats.setText("Recording to: " + filename + " ...");
            } else {
                Toast.makeText(activity, "Failed to start recording", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void updateRecordingUi() {
        CanRecorder recorder = bus.recorder();
        if (recorder.isRecording()) {
            recordBtn.setText("Stop Recording");
            recordBtn.setBackground(Ui.round(activity, 0xFF3A1C1F, 10, 0xFF5A2A2E));
            recordBtn.setTextColor(0xFFFFD9DA);
            Ui.pill(recordPill, "REC ● LIVE", Ui.BAD);

            File cur = recorder.currentFile();
            String name = cur != null ? cur.getName() : "session.log";
            long elapsed = recorder.elapsedMs();
            long frames = recorder.frameCount();
            long bytes = recorder.bytesWritten();
            long dropped = recorder.droppedFrames();

            double fps = elapsed > 0 ? (frames * 1000.0 / elapsed) : 0;
            String text = String.format(Locale.ROOT,
                    "File: %s\nTime: %s | Frames: %,d (%.0f fps) | Size: %s%s",
                    name,
                    CanRecorder.formatDuration(elapsed),
                    frames,
                    fps,
                    CanRecorder.formatBytes(bytes),
                    dropped > 0 ? " | Dropped: " + dropped : "");
            recordStats.setText(text);
        } else {
            recordBtn.setText("Start Recording");
            recordBtn.setBackground(Ui.round(activity, Ui.ACCENT, 10, 0));
            recordBtn.setTextColor(0xFF06231C);
            if (lastSavedFile != null && lastSavedFile.exists()) {
                Ui.pill(recordPill, "SAVED", Ui.ACCENT);
                recordStats.setText(String.format(Locale.ROOT,
                        "Last saved: %s (%s)",
                        lastSavedFile.getName(),
                        CanRecorder.formatBytes(lastSavedFile.length())));
            } else {
                Ui.pill(recordPill, "IDLE", Ui.DIM);
                recordStats.setText("Output: candump format (.log). Replayable via canplayer or SavvyCAN.");
            }
        }
    }

    private void refreshSavedRecordings() {
        savedListContainer.removeAllViews();
        File dir = getRecordingsDir();
        File[] files = dir.listFiles((d, name) -> name.endsWith(".log") || name.endsWith(".candump") || name.endsWith(".csv"));
        if (files == null || files.length == 0) {
            return;
        }

        // Sort newest first
        Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        savedListContainer.addView(Ui.heading(activity, "Saved Sessions (" + files.length + ")"));

        int limit = Math.min(files.length, 6);
        for (int i = 0; i < limit; i++) {
            File f = files[i];
            LinearLayout fileRow = Ui.column(activity);
            fileRow.setPadding(0, Ui.dp(activity, 6), 0, Ui.dp(activity, 8));

            LinearLayout infoRow = Ui.row(activity);
            LinearLayout nameCol = Ui.column(activity);
            TextView nameView = Ui.text(activity, f.getName(), 12, Ui.TEXT);
            nameView.setTypeface(android.graphics.Typeface.MONOSPACE);
            TextView metaView = Ui.text(activity,
                    CanRecorder.formatBytes(f.length()) + "  •  "
                            + new SimpleDateFormat("MMM dd, HH:mm", Locale.ROOT).format(new Date(f.lastModified())),
                    11, Ui.DIM);
            metaView.setPadding(0, Ui.dp(activity, 2), 0, 0);
            nameCol.addView(nameView);
            nameCol.addView(metaView);
            infoRow.addView(nameCol, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f));

            // Top-right Red Delete Trash Button (Spacious 34x30dp, unmistakable, zero clipping)
            LinearLayout delBtn = Ui.row(activity);
            delBtn.setGravity(Gravity.CENTER);
            delBtn.setBackground(Ui.round(activity, Ui.alpha(Ui.ERROR, 0x1A), 8, Ui.alpha(Ui.ERROR, 0x55)));
            ImageView delIcon = new ImageView(activity);
            delIcon.setImageResource(R.drawable.ic_trash);
            delIcon.setColorFilter(Ui.ERROR);
            delBtn.addView(delIcon, new LinearLayout.LayoutParams(Ui.dp(activity, 16), Ui.dp(activity, 16)));
            delBtn.setOnClickListener(v -> {
                f.delete();
                refreshSavedRecordings();
            });
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(activity, 34), Ui.dp(activity, 30));
            dlp.leftMargin = Ui.dp(activity, 8);
            infoRow.addView(delBtn, dlp);

            fileRow.addView(infoRow);

            // Action buttons row (Share, To Downloads, To CSV - ample room)
            LinearLayout actions = Ui.row(activity);
            actions.setPadding(0, Ui.dp(activity, 6), 0, 0);

            // 1. Share via Android Sheet
            Button shareBtn = Ui.button(activity, "Share", v -> shareFile(f));
            shareBtn.setTextSize(11);
            shareBtn.setMinHeight(Ui.dp(activity, 30));
            shareBtn.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 4), Ui.dp(activity, 12), Ui.dp(activity, 4));
            actions.addView(shareBtn);
            addMargin(actions);

            // 2. Direct Export to public Downloads/SolaCAN/
            Button dlBtn = Ui.button(activity, "To Downloads", v -> exportToDownloads(f));
            dlBtn.setTextSize(11);
            dlBtn.setMinHeight(Ui.dp(activity, 30));
            dlBtn.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 4), Ui.dp(activity, 12), Ui.dp(activity, 4));
            actions.addView(dlBtn);
            addMargin(actions);

            // 3. Export as CSV (if not already CSV)
            if (!f.getName().endsWith(".csv")) {
                Button csvBtn = Ui.button(activity, "To CSV", v -> exportAsCsv(f));
                csvBtn.setTextSize(11);
                csvBtn.setMinHeight(Ui.dp(activity, 30));
                csvBtn.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 4), Ui.dp(activity, 12), Ui.dp(activity, 4));
                actions.addView(csvBtn);
            }

            fileRow.addView(actions);
            fileRow.addView(Ui.divider(activity));
            savedListContainer.addView(fileRow);
        }
    }

    private void addMargin(LinearLayout row) {
        View sp = new View(activity);
        sp.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 6), 1));
        row.addView(sp);
    }

    // ------------------------------------------------------------- export options

    /**
     * Share file via Android's native share sheet (Drive, Email, WhatsApp, Nearby Share).
     */
    private void shareFile(File file) {
        if (file == null || !file.exists()) {
            Toast.makeText(activity, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Uri uri = GenericFileProvider.getUriForFile(file);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType(file.getName().endsWith(".csv") ? "text/csv" : "text/plain");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.putExtra(Intent.EXTRA_SUBJECT, "CAN Session: " + file.getName());
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(Intent.createChooser(intent, "Export CAN Recording"));
        } catch (Exception e) {
            Toast.makeText(activity, "Cannot share: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Direct 1-click copy to the user's public Downloads directory (Downloads/SolaCAN/).
     */
    private void exportToDownloads(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            Toast.makeText(activity, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.getName());
                values.put(MediaStore.MediaColumns.MIME_TYPE, sourceFile.getName().endsWith(".csv") ? "text/csv" : "text/plain");
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SolaCAN");
                Uri uri = activity.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri != null) {
                    try (InputStream in = new FileInputStream(sourceFile);
                         OutputStream out = activity.getContentResolver().openOutputStream(uri)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                        }
                    }
                    Toast.makeText(activity, "Exported to Downloads/SolaCAN/" + sourceFile.getName(), Toast.LENGTH_LONG).show();
                    return;
                }
            }

            // Fallback for Android 7-9
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File targetDir = new File(dl, "SolaCAN");
            if (!targetDir.exists()) {
                targetDir.mkdirs();
            }
            File dest = new File(targetDir, sourceFile.getName());
            try (InputStream in = new FileInputStream(sourceFile);
                 OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            Toast.makeText(activity, "Exported to: " + dest.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(activity, "Export to Downloads failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Parses the candump file and exports a tabular CSV (timestamp,can_id_hex,can_id_dec,name,dlc,data_hex)
     * suitable for Microsoft Excel, Google Sheets, or Pandas.
     */
    private void exportAsCsv(File sourceFile) {
        if (sourceFile == null || !sourceFile.exists()) {
            Toast.makeText(activity, "File not found", Toast.LENGTH_SHORT).show();
            return;
        }

        String baseName = sourceFile.getName();
        if (baseName.endsWith(".log")) {
            baseName = baseName.substring(0, baseName.length() - 4);
        } else if (baseName.endsWith(".candump")) {
            baseName = baseName.substring(0, baseName.length() - 8);
        }
        File csvFile = new File(sourceFile.getParentFile(), baseName + ".csv");

        new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new FileReader(sourceFile));
                 BufferedWriter writer = new BufferedWriter(new FileWriter(csvFile))) {

                writer.write("timestamp,can_id_hex,can_id_dec,name,dlc,data_hex\n");
                dev.sola.core.Dbc dbc = bus.live() != null ? bus.live().dbc() : null;
                String line;
                int count = 0;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("#") || line.isEmpty()) {
                        continue;
                    }
                    int p1 = line.indexOf('(');
                    int p2 = line.indexOf(')');
                    int hash = line.indexOf('#');
                    if (p1 >= 0 && p2 > p1 && hash > p2) {
                        String ts = line.substring(p1 + 1, p2);
                        String idPart = line.substring(p2 + 1, hash).trim();
                        if (idPart.startsWith("vcan0")) {
                            idPart = idPart.substring(5).trim();
                        }
                        String dataHex = line.substring(hash + 1).trim();
                        try {
                            int cid = Integer.parseInt(idPart, 16);
                            String name = (dbc != null && dbc.message(cid) != null) ? dbc.message(cid).name : "Unknown";
                            int dlc = dataHex.length() / 2;
                            writer.write(ts + ",0x" + idPart + "," + cid + "," + name + "," + dlc + "," + dataHex + "\n");
                            count++;
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
                writer.flush();
                int finalCount = count;
                activity.runOnUiThread(() -> {
                    Toast.makeText(activity, "CSV generated (" + finalCount + " rows)", Toast.LENGTH_SHORT).show();
                    exportToDownloads(csvFile);
                    refreshSavedRecordings();
                });
            } catch (Exception e) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "CSV export failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }, "csv-export").start();
    }

    // --------------------------------------------------------------- render

    private void render(Live.Snapshot snapshot) {
        long now = System.currentTimeMillis();
        if (now - lastRender < RENDER_MS) {
            return;
        }
        lastRender = now;

        updateRecordingUi();

        Live live = bus.live();
        if (live == null || snapshot == null) {
            status.setText(bus.state());
            return;
        }

        status.setText("frames " + snapshot.frames
                + "   pps " + snapshot.pps
                + "   messages " + snapshot.messagesSeen
                + "   signals " + snapshot.signalsSeen
                + "\n" + bus.state());

        List<Live.Entry> entries = live.log();
        if (entries.isEmpty()) {
            log.setText("No frames yet. The vehicle may be asleep — press the red "
                    + "start button or plug in the charger.");
        } else {
            StringBuilder sb = new StringBuilder(entries.size() * 56);
            int shown = 0;
            for (Live.Entry entry : entries) {
                sb.append(clock.format(new Date(entry.at))).append("  ")
                        .append(String.format(Locale.ROOT, "0x%03X", entry.id)).append("  ")
                        .append(String.format(Locale.ROOT, "%-24s", trim(entry.name, 24)))
                        .append(entry.hex).append('\n');
                if (++shown >= MAX_LINES) {
                    break;
                }
            }
            log.setText(sb.toString());
        }

        busiest.setText(topIds(live));
    }

    private static String topIds(Live live) {
        List<Map.Entry<Integer, Integer>> list = live.busiest();
        if (list.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(list.size() * 24);
        int shown = 0;
        for (Map.Entry<Integer, Integer> entry : list) {
            dev.sola.core.Dbc.Message message = live.dbc().message(entry.getKey());
            sb.append(String.format(Locale.ROOT, "0x%03X %-22s %d%n",
                    entry.getKey(),
                    trim(message == null ? "Unknown" : message.name, 22),
                    entry.getValue()));
            if (++shown >= TOP_IDS) {
                break;
            }
        }
        return sb.toString();
    }

    private static String trim(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, max - 1) + "~";
    }
}
