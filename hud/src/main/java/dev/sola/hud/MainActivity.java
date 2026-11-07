package dev.sola.hud;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Management and setup activity for Sola HUD.
 * Manages SYSTEM_ALERT_WINDOW permissions, auto-boot settings, and service start/stop.
 */
public final class MainActivity extends Activity {

    private static final int REQUEST_OVERLAY_CODE = 1001;
    private static final String PREFS_NAME = "sola_hud_prefs";

    private SharedPreferences prefs;
    private TextView tvPermStatus;
    private TextView tvServiceStatus;
    private Button btnGrantPerm;
    private Button btnToggleService;
    private Button btnToggleBoot;
    private Button btnToggleCap;

    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(HudUi.BG);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = HudUi.dp(this, 20);
        root.setPadding(pad, pad + HudUi.dp(this, 10), pad, pad);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // App Header
        TextView tvTitle = HudUi.text(this, "SOLA HUD", 24, HudUi.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(tvTitle);

        TextView tvSubtitle = HudUi.text(this, "Floating MoveOS Telemetry & EV Power Controls", 13, HudUi.MUTED);
        root.addView(tvSubtitle);

        root.addView(HudUi.space(this, 20));

        // Card 1: Overlay Permission Status
        root.addView(buildPermissionCard());

        root.addView(HudUi.space(this, 14));

        // Card 2: Service Controller
        root.addView(buildServiceControllerCard());

        root.addView(HudUi.space(this, 14));

        // Card 3: Automation Settings
        root.addView(buildSettingsCard());

        root.addView(HudUi.space(this, 14));

        // Card 4: Platform Diagnostics
        root.addView(buildPlatformCard());

        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUiState();
    }

    private View buildPermissionCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(HudUi.round(this, HudUi.SURFACE, 16, HudUi.LINE));
        int p = HudUi.dp(this, 16);
        card.setPadding(p, p, p, p);

        card.addView(HudUi.heading(this, "Floating Overlay Permission"));

        tvPermStatus = HudUi.text(this, "Checking permission...", 14, HudUi.TEXT);
        card.addView(tvPermStatus);

        card.addView(HudUi.space(this, 10));

        btnGrantPerm = HudUi.accentButton(this, "GRANT OVERLAY PERMISSION", v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY_CODE);
            }
        });
        card.addView(btnGrantPerm);

        return card;
    }

    private View buildServiceControllerCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(HudUi.round(this, HudUi.SURFACE, 16, HudUi.LINE));
        int p = HudUi.dp(this, 16);
        card.setPadding(p, p, p, p);

        card.addView(HudUi.heading(this, "HUD Service Status"));

        tvServiceStatus = HudUi.text(this, "STOPPED", 18, HudUi.MUTED);
        tvServiceStatus.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(tvServiceStatus);

        card.addView(HudUi.space(this, 12));

        btnToggleService = HudUi.button(this, "START HUD OVERLAY", v -> {
            if (!hasOverlayPermission()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                }
                return;
            }

            Intent serviceIntent = new Intent(this, HudOverlayService.class);
            if (isServiceRunning) {
                stopService(serviceIntent);
                isServiceRunning = false;
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                isServiceRunning = true;
            }
            updateUiState();
        });
        card.addView(btnToggleService);

        return card;
    }

    private View buildSettingsCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(HudUi.round(this, HudUi.SURFACE, 16, HudUi.LINE));
        int p = HudUi.dp(this, 16);
        card.setPadding(p, p, p, p);

        card.addView(HudUi.heading(this, "Automation Settings"));

        boolean cap = prefs.getBoolean("charge_cap_enabled", true);
        btnToggleCap = HudUi.button(this, cap ? "80% Battery Saver Cap: ENABLED" : "80% Battery Saver Cap: DISABLED", v -> {
            boolean next = !prefs.getBoolean("charge_cap_enabled", true);
            prefs.edit().putBoolean("charge_cap_enabled", next).apply();
            btnToggleCap.setText(next ? "80% Battery Saver Cap: ENABLED" : "80% Battery Saver Cap: DISABLED");
            btnToggleCap.setTextColor(next ? HudUi.POSITIVE : HudUi.MUTED);
        });
        btnToggleCap.setTextColor(cap ? HudUi.POSITIVE : HudUi.MUTED);
        card.addView(btnToggleCap);

        card.addView(HudUi.space(this, 8));

        boolean boot = prefs.getBoolean("start_on_boot", true);
        btnToggleBoot = HudUi.button(this, boot ? "Start On Scooter Boot: ENABLED" : "Start On Scooter Boot: DISABLED", v -> {
            boolean next = !prefs.getBoolean("start_on_boot", true);
            prefs.edit().putBoolean("start_on_boot", next).apply();
            btnToggleBoot.setText(next ? "Start On Scooter Boot: ENABLED" : "Start On Scooter Boot: DISABLED");
            btnToggleBoot.setTextColor(next ? HudUi.POSITIVE : HudUi.MUTED);
        });
        btnToggleBoot.setTextColor(boot ? HudUi.POSITIVE : HudUi.MUTED);
        card.addView(btnToggleBoot);

        return card;
    }

    private View buildPlatformCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(HudUi.round(this, HudUi.SURFACE, 16, HudUi.LINE));
        int p = HudUi.dp(this, 16);
        card.setPadding(p, p, p, p);

        card.addView(HudUi.heading(this, "Target Environment"));

        String osInfo = "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")";
        String devInfo = "Device: " + Build.MANUFACTURER + " " + Build.MODEL;
        String moveOsInfo = Build.VERSION.SDK_INT == 29 ? "MoveOS Target: Android 10 (Q) Compatible" : "General Android Target (API 26+)";

        card.addView(HudUi.text(this, osInfo, 13, HudUi.TEXT));
        card.addView(HudUi.space(this, 4));
        card.addView(HudUi.text(this, devInfo, 13, HudUi.MUTED));
        card.addView(HudUi.space(this, 4));
        card.addView(HudUi.text(this, moveOsInfo, 13, HudUi.POSITIVE));

        return card;
    }

    private boolean hasOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void updateUiState() {
        boolean perm = hasOverlayPermission();
        if (tvPermStatus != null) {
            if (perm) {
                tvPermStatus.setText("Overlay permission GRANTED. Ready to draw over MoveOS.");
                tvPermStatus.setTextColor(HudUi.POSITIVE);
                btnGrantPerm.setVisibility(View.GONE);
            } else {
                tvPermStatus.setText("Overlay permission REQUIRED. Tap below to allow drawing over apps.");
                tvPermStatus.setTextColor(HudUi.WARNING);
                btnGrantPerm.setVisibility(View.VISIBLE);
            }
        }

        if (tvServiceStatus != null) {
            tvServiceStatus.setText(isServiceRunning ? "ACTIVE (Capsule Visible)" : "STOPPED");
            tvServiceStatus.setTextColor(isServiceRunning ? HudUi.POSITIVE : HudUi.MUTED);
            btnToggleService.setText(isServiceRunning ? "STOP HUD OVERLAY" : "START HUD OVERLAY");
        }
    }
}
