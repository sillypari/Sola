package dev.sola;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Axiom-styled MoveOS System Settings & Dash Intent Launcher.
 * Features clean sub-cards, uniform 34dp buttons with 10dp radius,
 * zero button shadows, and hierarchical typography.
 * Zero emojis.
 */
public final class SettingsTab implements Tab {

    private static final String[][] INTENTS = {
            {"Settings", "android.settings.SETTINGS"},
            {"Wi-Fi", "android.settings.WIFI_SETTINGS"},
            {"Bluetooth", "android.settings.BLUETOOTH_SETTINGS"},
            {"Apps", "android.settings.APPLICATION_SETTINGS"},
            {"Display", "android.settings.DISPLAY_SETTINGS"},
            {"Sound", "android.settings.SOUND_SETTINGS"},
            {"Battery", "android.settings.BATTERY_SAVER_SETTINGS"},
            {"Storage", "android.settings.INTERNAL_STORAGE_SETTINGS"},
            {"About Device", "android.settings.DEVICE_INFO_SETTINGS"},
            {"Notifications", "android.settings.NOTIFICATION_SETTINGS"},
            {"Location", "android.settings.LOCATION_SOURCE_SETTINGS"},
            {"Date & Time", "android.settings.DATE_SETTINGS"},
    };

    private static final String HMI = "com.olaelectric.hmiclient/.MainActivity";
    private static final String DIAG = "com.ola.mfgapp/.MainActivity";

    private final MainActivity activity;
    private final Conn conn;
    private final LinearLayout body;
    private final TextView result;

    public SettingsTab(MainActivity activity, Conn conn) {
        this.activity = activity;
        this.conn = conn;
        this.body = Ui.column(activity);

        // 1. Dash Lifecycle Sub-card
        LinearLayout dashCard = Ui.column(activity);
        dashCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        dashCard.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));

        TextView tvDashTitle = Ui.text(activity, "Dash Display & Diagnostics", 13.5f, Ui.TEXT);
        tvDashTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dashCard.addView(tvDashTitle);

        TextView tvDashSub = Ui.text(activity,
                "Send HOME keyevent, force-restart HMI client, or launch native service APK.", 11.5f, Ui.MUTED);
        tvDashSub.setLineSpacing(0, 1.25f);
        tvDashSub.setPadding(0, Ui.dp(activity, 2), 0, Ui.dp(activity, 10));
        dashCard.addView(tvDashSub);

        // Row 1: Back to Dash + Restart Dash
        LinearLayout r1 = Ui.row(activity);
        Button btnHome = Ui.button(activity, "Back to Dash", v -> run(
                "input keyevent 3", "HOME sent. Dash is active."));
        btnHome.setMinHeight(Ui.dp(activity, 34));
        btnHome.setMinimumHeight(Ui.dp(activity, 34));
        btnHome.setTextSize(10.5f);
        btnHome.setTypeface(Typeface.DEFAULT_BOLD);

        Button btnRestart = Ui.button(activity, "Restart Dash", v -> confirmRestart());
        btnRestart.setMinHeight(Ui.dp(activity, 34));
        btnRestart.setMinimumHeight(Ui.dp(activity, 34));
        btnRestart.setTextSize(10.5f);
        btnRestart.setTypeface(Typeface.DEFAULT_BOLD);

        r1.addView(btnHome, new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f));
        LinearLayout.LayoutParams g1 = new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f);
        g1.leftMargin = Ui.dp(activity, 8);
        r1.addView(btnRestart, g1);
        dashCard.addView(r1);

        // Row 2: Launch Diagnostic App
        Button btnDiag = Ui.accent(activity, "Launch Factory Diagnostic App", v -> run(
                "am start -n " + DIAG, "Opened MoveOS service app."));
        btnDiag.setMinHeight(Ui.dp(activity, 34));
        btnDiag.setMinimumHeight(Ui.dp(activity, 34));
        btnDiag.setTextSize(11);
        btnDiag.setTypeface(Typeface.DEFAULT_BOLD);

        LinearLayout.LayoutParams dLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 34));
        dLp.topMargin = Ui.dp(activity, 8);
        dashCard.addView(btnDiag, dLp);

        body.addView(dashCard);

        addSpace(body, 10);

        // 2. Android Settings Sub-card
        LinearLayout setCard = Ui.column(activity);
        setCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        setCard.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));

        TextView tvSetTitle = Ui.text(activity, "MoveOS Android Intent Launcher", 13.5f, Ui.TEXT);
        tvSetTitle.setTypeface(Typeface.DEFAULT_BOLD);
        setCard.addView(tvSetTitle);

        TextView tvSetSub = Ui.text(activity,
                "Launches system settings activities directly on the scooter dash display.", 11.5f, Ui.MUTED);
        tvSetSub.setLineSpacing(0, 1.25f);
        tvSetSub.setPadding(0, Ui.dp(activity, 2), 0, Ui.dp(activity, 10));
        setCard.addView(tvSetSub);

        for (int i = 0; i < INTENTS.length; i += 2) {
            LinearLayout row = Ui.row(activity);
            row.addView(intentButton(INTENTS[i][0], INTENTS[i][1]), new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f));
            if (i + 1 < INTENTS.length) {
                LinearLayout.LayoutParams gap2 = new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f);
                gap2.leftMargin = Ui.dp(activity, 8);
                row.addView(intentButton(INTENTS[i + 1][0], INTENTS[i + 1][1]), gap2);
            } else {
                row.addView(new View(activity), new LinearLayout.LayoutParams(0, Ui.dp(activity, 34), 1f));
            }
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 34));
            if (i > 0) rLp.topMargin = Ui.dp(activity, 8);
            setCard.addView(row, rLp);
        }

        body.addView(setCard);

        // Feedback / Result Console
        result = Ui.text(activity, "", 11, Ui.DIM);
        result.setTypeface(Typeface.MONOSPACE);
        result.setPadding(Ui.dp(activity, 2), Ui.dp(activity, 8), Ui.dp(activity, 2), 0);
        body.addView(result);
    }

    private void addSpace(LinearLayout parent, int dp) {
        View spacer = new View(activity);
        parent.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, dp)));
    }

    private Button intentButton(String label, String action) {
        Button b = Ui.button(activity, label, v -> run("am start -a " + action, "Opened " + label + "."));
        b.setMinHeight(Ui.dp(activity, 34));
        b.setMinimumHeight(Ui.dp(activity, 34));
        b.setTextSize(10.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        return b;
    }

    private void confirmRestart() {
        new AlertDialog.Builder(activity)
                .setTitle("Restart Dash Client?")
                .setMessage("This force-stops com.olaelectric.hmiclient and relaunches it. "
                        + "The screen goes blank for a few seconds. Do not execute while riding.")
                .setPositiveButton("Restart", (d, w) -> restartDash())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void restartDash() {
        result.setText("Restarting the dash ...");
        result.setTextColor(Ui.DIM);
        conn.run(rpc -> {
            rpc.exec("am force-stop com.olaelectric.hmiclient", 20_000);
            Thread.sleep(1200);
            rpc.exec("am start -n " + HMI, 20_000);
            Thread.sleep(2000);
            rpc.exec("input keyevent 224", 10_000);
            return "Dash restarted and brought to front.";
        }, (value, error) -> {
            if (error != null) {
                result.setText("Failed: " + error.getMessage());
                result.setTextColor(Ui.BAD);
                return;
            }
            result.setText(value);
            result.setTextColor(Ui.ACCENT);
        });
    }

    private void run(String command, String successMessage) {
        result.setText("Executing: " + command);
        result.setTextColor(Ui.DIM);
        conn.run(rpc -> {
            rpc.exec(command, 10_000);
            return successMessage;
        }, (value, error) -> {
            if (error != null) {
                result.setText("Failed: " + error.getMessage());
                result.setTextColor(Ui.BAD);
                return;
            }
            result.setText(value);
            result.setTextColor(Ui.ACCENT);
        });
    }

    @Override
    public View view() {
        return body;
    }

    @Override
    public void onShow() {
    }

    @Override
    public void onHide() {
    }
}
