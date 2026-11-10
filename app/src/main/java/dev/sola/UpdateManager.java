package dev.sola;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * In-App OTA Update Manager for Sola.
 * Checks GitHub Releases API for new builds, downloads APKs to internal app storage,
 * and launches the Android Package Installer via GenericFileProvider.
 * Zero emojis.
 */
public final class UpdateManager {

    private static final String RELEASES_API =
            "https://api.github.com/repos/sillypari/Sola/releases/latest";

    private static final String PREF_NAME = "sola_updates";
    private static final String KEY_LAST_CHECK_MS = "last_check_ms";
    private static final String KEY_PENDING_TAG = "pending_tag";
    private static final String KEY_PENDING_VERSION = "pending_version";
    private static final String KEY_PENDING_TITLE = "pending_title";
    private static final String KEY_PENDING_LOG = "pending_log";
    private static final String KEY_PENDING_URL = "pending_url";
    private static final String KEY_PENDING_SIZE = "pending_size";
    private static final long AUTO_CHECK_INTERVAL_MS = 2 * 60 * 1000L; // 2 minutes

    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());

    private static File pendingInstallApk = null;
    private static boolean isDialogShowing = false;

    public static final class ReleaseInfo {
        public final String tagName;
        public final String version;
        public final String releaseTitle;
        public final String changelog;
        public final String downloadUrl;
        public final long sizeBytes;

        public ReleaseInfo(String tagName, String version, String releaseTitle, String changelog, String downloadUrl, long sizeBytes) {
            this.tagName = tagName;
            this.version = version;
            this.releaseTitle = releaseTitle;
            this.changelog = changelog;
            this.downloadUrl = downloadUrl;
            this.sizeBytes = sizeBytes;
        }
    }

    public interface CheckCallback {
        void onUpdateAvailable(ReleaseInfo info);
        void onAlreadyLatest(String currentVersion);
        void onError(String message);
    }

    public interface DownloadCallback {
        void onProgress(int progressPercent, long downloadedBytes, long totalBytes);
        void onCompleted(File apkFile);
        void onError(String error);
    }

    private UpdateManager() {
    }

    public static void checkAuto(Activity activity, CheckCallback callback) {
        if (activity == null || activity.isFinishing()) return;
        SharedPreferences prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long last = prefs.getLong(KEY_LAST_CHECK_MS, 0);
        if (System.currentTimeMillis() - last < AUTO_CHECK_INTERVAL_MS) {
            return;
        }
        checkForUpdates(activity, true, callback);
    }

    public static void checkForUpdates(Activity activity, boolean silentIfCurrent, CheckCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(RELEASES_API);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "Sola-App/" + BuildConfig.VERSION_NAME);
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);

                int code = conn.getResponseCode();
                if (code != 200) {
                    if (callback != null && !silentIfCurrent) {
                        UI_HANDLER.post(() -> callback.onError("GitHub API error (HTTP " + code + ")"));
                    }
                    return;
                }

                String body = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(body);

                String rawTag = json.optString("tag_name", "").trim();
                String cleanTag = rawTag.replaceAll("^[vV]+", "").trim();
                String releaseTitle = json.optString("name", "v" + cleanTag);
                String changelog = json.optString("body", "").trim();

                JSONArray assets = json.optJSONArray("assets");
                String apkUrl = null;
                long apkSize = 0;

                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String assetName = asset.optString("name", "");
                        if (assetName.endsWith(".apk")) {
                            apkUrl = asset.optString("browser_download_url", null);
                            apkSize = asset.optLong("size", 0);
                            break;
                        }
                    }
                }

                activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                        .edit().putLong(KEY_LAST_CHECK_MS, System.currentTimeMillis()).apply();

                String currentVersion = BuildConfig.VERSION_NAME;
                if (apkUrl != null && isVersionNewer(cleanTag, currentVersion)) {
                    activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                            .putString(KEY_PENDING_TAG, rawTag)
                            .putString(KEY_PENDING_VERSION, cleanTag)
                            .putString(KEY_PENDING_TITLE, releaseTitle)
                            .putString(KEY_PENDING_LOG, changelog)
                            .putString(KEY_PENDING_URL, apkUrl)
                            .putLong(KEY_PENDING_SIZE, apkSize)
                            .apply();

                    ReleaseInfo info = new ReleaseInfo(rawTag, cleanTag, releaseTitle, changelog, apkUrl, apkSize);
                    if (callback != null) {
                        UI_HANDLER.post(() -> callback.onUpdateAvailable(info));
                    }
                } else {
                    activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
                            .remove(KEY_PENDING_TAG)
                            .remove(KEY_PENDING_VERSION)
                            .remove(KEY_PENDING_TITLE)
                            .remove(KEY_PENDING_LOG)
                            .remove(KEY_PENDING_URL)
                            .remove(KEY_PENDING_SIZE)
                            .apply();

                    if (callback != null && !silentIfCurrent) {
                        UI_HANDLER.post(() -> callback.onAlreadyLatest(currentVersion));
                    }
                }
            } catch (Exception e) {
                if (callback != null && !silentIfCurrent) {
                    UI_HANDLER.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Network error"));
                }
            }
        }).start();
    }

    public static boolean isVersionNewer(String remoteVer, String currentVer) {
        if (remoteVer == null || currentVer == null) return false;
        int[] rParts = parseVersionParts(remoteVer);
        int[] cParts = parseVersionParts(currentVer);

        int len = Math.max(rParts.length, cParts.length);
        for (int i = 0; i < len; i++) {
            int r = i < rParts.length ? rParts[i] : 0;
            int c = i < cParts.length ? cParts[i] : 0;
            if (r > c) return true;
            if (r < c) return false;
        }
        return false;
    }

    private static int[] parseVersionParts(String version) {
        if (version == null) return new int[0];
        String clean = version.replaceAll("^[^0-9]+", "");
        String[] tokens = clean.split("[^0-9]+");
        java.util.List<Integer> list = new java.util.ArrayList<>();
        for (String t : tokens) {
            if (!t.isEmpty()) {
                try {
                    list.add(Integer.parseInt(t));
                } catch (NumberFormatException ignored) {}
            }
        }
        int[] res = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            res[i] = list.get(i);
        }
        return res;
    }

    public static void downloadApk(Context context, String downloadUrl, DownloadCallback callback) {
        new Thread(() -> {
            File targetDir = context.getExternalFilesDir("updates");
            if (targetDir == null) {
                targetDir = new File(context.getFilesDir(), "updates");
            }
            if (!targetDir.exists()) targetDir.mkdirs();

            File apkFile = new File(targetDir, "sola-update.apk");
            if (apkFile.exists()) apkFile.delete();

            try {
                URL url = new URL(downloadUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "Sola-App/" + BuildConfig.VERSION_NAME);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307) {
                    String loc = conn.getHeaderField("Location");
                    if (loc != null) {
                        conn = (HttpURLConnection) new URL(loc).openConnection();
                        conn.setRequestProperty("User-Agent", "Sola-App/" + BuildConfig.VERSION_NAME);
                    }
                }

                long totalBytes = conn.getContentLength();
                InputStream is = conn.getInputStream();
                FileOutputStream fos = new FileOutputStream(apkFile);

                byte[] buffer = new byte[8192];
                long downloaded = 0;
                int len;
                int lastPercent = 0;

                while ((len = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, len);
                    downloaded += len;

                    if (totalBytes > 0) {
                        int percent = (int) ((downloaded * 100) / totalBytes);
                        if (percent != lastPercent) {
                            lastPercent = percent;
                            final long cur = downloaded;
                            UI_HANDLER.post(() -> callback.onProgress(percent, cur, totalBytes));
                        }
                    }
                }

                fos.flush();
                fos.close();
                is.close();

                UI_HANDLER.post(() -> callback.onCompleted(apkFile));
            } catch (Exception e) {
                if (apkFile.exists()) apkFile.delete();
                UI_HANDLER.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Download failed"));
            }
        }).start();
    }

    public static void installApk(Activity activity, File apkFile) {
        if (apkFile == null || !apkFile.exists() || activity == null || activity.isFinishing()) {
            return;
        }
        Uri contentUri = GenericFileProvider.getUriForApk(apkFile);
        Intent install = new Intent(Intent.ACTION_VIEW);
        install.setDataAndType(contentUri, "application/vnd.android.package-archive");
        install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            activity.startActivity(install);
        } catch (Exception e) {
            Toast.makeText(activity, "Unable to launch installer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static ReleaseInfo getSavedPendingUpdate(Context context) {
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String version = prefs.getString(KEY_PENDING_VERSION, null);
        if (version == null || !isVersionNewer(version, BuildConfig.VERSION_NAME)) {
            if (version != null) {
                prefs.edit()
                        .remove(KEY_PENDING_TAG)
                        .remove(KEY_PENDING_VERSION)
                        .remove(KEY_PENDING_TITLE)
                        .remove(KEY_PENDING_LOG)
                        .remove(KEY_PENDING_URL)
                        .remove(KEY_PENDING_SIZE)
                        .apply();
            }
            return null;
        }
        return new ReleaseInfo(
                prefs.getString(KEY_PENDING_TAG, "v" + version),
                version,
                prefs.getString(KEY_PENDING_TITLE, "v" + version),
                prefs.getString(KEY_PENDING_LOG, ""),
                prefs.getString(KEY_PENDING_URL, ""),
                prefs.getLong(KEY_PENDING_SIZE, 0)
        );
    }

    public static void resumePendingInstall(Activity activity) {
        // No-op: REQUEST_INSTALL_PACKAGES omitted to prevent Play Protect flags
    }

    public static void promptUpdate(Activity activity, ReleaseInfo info) {
        if (activity == null || activity.isFinishing() || isDialogShowing) {
            return;
        }

        isDialogShowing = true;
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        LinearLayout layout = Ui.column(activity);
        layout.setPadding(Ui.dp(activity, 20), Ui.dp(activity, 16), Ui.dp(activity, 20), Ui.dp(activity, 16));

        TextView tvTitle = Ui.text(activity, "Update Available: v" + info.version, 16, Ui.TEXT);
        tvTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        layout.addView(tvTitle);

        TextView tvCurrent = Ui.text(activity, "Installed: v" + BuildConfig.VERSION_NAME, 12, Ui.MUTED);
        tvCurrent.setPadding(0, Ui.dp(activity, 4), 0, Ui.dp(activity, 10));
        layout.addView(tvCurrent);

        TextView tvNotice = Ui.text(activity,
                "A newer release of Sola is available.\n\n" +
                "• If you already downloaded the update: open the file from your phone's notification bar or Downloads folder to install.\n\n" +
                "• Otherwise, tap Download Update below to get the latest APK.",
                12, Ui.TEXT);
        tvNotice.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
        tvNotice.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 8, 0));
        layout.addView(tvNotice);

        if (info.changelog != null && !info.changelog.isEmpty()) {
            TextView tvChangelogHeader = Ui.text(activity, "Release Notes", 11, Ui.MUTED);
            tvChangelogHeader.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            tvChangelogHeader.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 4));
            layout.addView(tvChangelogHeader);

            TextView tvLog = Ui.text(activity, info.changelog, 11, Ui.MUTED);
            tvLog.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 8), Ui.dp(activity, 10), Ui.dp(activity, 8));
            tvLog.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 6, 0));
            layout.addView(tvLog);
        }

        builder.setView(layout);
        builder.setNegativeButton("Later", null);
        builder.setPositiveButton("Download Update", (d, which) -> {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                Toast.makeText(activity, "Opening download in browser...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(activity, "Unable to open browser: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> isDialogShowing = false);
        dialog.show();
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }
}
