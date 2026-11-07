package dev.sola.hud;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.sola.core.Live;

/**
 * Floating System HUD Overlay Service.
 * Draws an interactive, draggable telemetry capsule on top of MoveOS (Kanzi HMI),
 * expanding into a full EV power & cell health diagnostics drawer.
 */
public final class HudOverlayService extends Service {

    private static final String CHANNEL_ID = "sola_hud_channel";
    private static final int NOTIF_ID = 101;
    private static final String PREFS_NAME = "sola_hud_prefs";

    private WindowManager windowManager;
    private WindowManager.LayoutParams capsuleParams;
    private WindowManager.LayoutParams drawerParams;

    private View capsuleView;
    private View drawerView;
    private boolean isDrawerOpen = false;

    private CanReader canReader;
    private SharedPreferences prefs;

    // Capsule UI bindings
    private TextView tvCapsuleSoc;
    private TextView tvCapsuleDelta;
    private TextView tvCapsuleTemp;
    private TextView tvCapsuleBadge;

    // Drawer UI bindings
    private TextView tvDrawerSoc;
    private TextView tvDrawerVoltage;
    private TextView tvDrawerCurrent;
    private TextView tvDrawerPower;
    private TextView tvDrawerDelta;
    private TextView tvDrawerDeltaBadge;
    private TextView tvDrawerHighCell;
    private TextView tvDrawerLowCell;
    private TextView tvDrawerPackTemp;
    private TextView tvDrawerPduTemp;
    private TextView[] tvCellVoltages = new TextView[14];
    private Button btnChargeCapToggle;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildForegroundNotification());

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        canReader = new CanReader(this);
        canReader.setChargeCapEnabled(prefs.getBoolean("charge_cap_enabled", true));

        initCapsuleWindow();
        initDrawerWindow();

        canReader.setListener(this::onSnapshotReceived);
        canReader.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (canReader != null) canReader.stop();
        if (windowManager != null) {
            if (capsuleView != null && capsuleView.isAttachedToWindow()) {
                windowManager.removeView(capsuleView);
            }
            if (drawerView != null && drawerView.isAttachedToWindow()) {
                windowManager.removeView(drawerView);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void onSnapshotReceived(Live.Snapshot s, long pps) {
        double deltaV_mV = Double.isNaN(s.imbalanceV) ? 0.0 : (s.imbalanceV * 1000.0);
        double powerKw = (Double.isNaN(s.packV) || Double.isNaN(s.packA)) ? 0.0 : ((s.packV * s.packA) / 1000.0);

        // 1. Update Capsule
        if (tvCapsuleSoc != null) {
            tvCapsuleSoc.setText(Double.isNaN(s.soc) ? "--%" : String.format(java.util.Locale.US, "%.0f%%", s.soc));
        }
        if (tvCapsuleDelta != null) {
            tvCapsuleDelta.setText(String.format(java.util.Locale.US, "ΔV %.0f mV", deltaV_mV));
        }
        if (tvCapsuleTemp != null) {
            tvCapsuleTemp.setText(Double.isNaN(s.packC) ? "--°C" : String.format(java.util.Locale.US, "%.1f°C", s.packC));
        }
        if (tvCapsuleBadge != null) {
            tvCapsuleBadge.setVisibility(canReader.isChargeCapEnabled() ? View.VISIBLE : View.GONE);
        }

        // 2. Safety Interlock: if riding (speed > 0.5 km/h), auto-collapse drawer
        if (isDrawerOpen && !Double.isNaN(s.speedKph) && s.speedKph > 0.5) {
            collapseDrawer();
        }

        // 3. Update Drawer if visible
        if (isDrawerOpen) {
            if (tvDrawerSoc != null) {
                tvDrawerSoc.setText(Double.isNaN(s.soc) ? "--%" : String.format(java.util.Locale.US, "%.0f%%", s.soc));
            }
            if (tvDrawerVoltage != null) {
                tvDrawerVoltage.setText(Double.isNaN(s.packV) ? "-- V" : String.format(java.util.Locale.US, "%.1f V", s.packV));
            }
            if (tvDrawerCurrent != null) {
                tvDrawerCurrent.setText(Double.isNaN(s.packA) ? "-- A" : String.format(java.util.Locale.US, "%.1f A", s.packA));
            }
            if (tvDrawerPower != null) {
                tvDrawerPower.setText(String.format(java.util.Locale.US, "%.2f kW", powerKw));
            }
            if (tvDrawerDelta != null) {
                tvDrawerDelta.setText(String.format(java.util.Locale.US, "%.0f mV", deltaV_mV));
            }
            if (tvDrawerHighCell != null) {
                tvDrawerHighCell.setText(Double.isNaN(s.cellMaxV) ? "High: -- V" : String.format(java.util.Locale.US, "High: %.3f V", s.cellMaxV));
            }
            if (tvDrawerLowCell != null) {
                tvDrawerLowCell.setText(Double.isNaN(s.cellMinV) ? "Low: -- V" : String.format(java.util.Locale.US, "Low: %.3f V", s.cellMinV));
            }
            if (tvDrawerPackTemp != null) {
                tvDrawerPackTemp.setText(Double.isNaN(s.packC) ? "-- °C" : String.format(java.util.Locale.US, "%.1f °C", s.packC));
            }
            if (tvDrawerPduTemp != null) {
                tvDrawerPduTemp.setText(Double.isNaN(s.mcuC) ? "-- °C" : String.format(java.util.Locale.US, "%.1f °C", s.mcuC));
            }

            if (tvDrawerDeltaBadge != null) {
                if (deltaV_mV < 20.0) {
                    HudUi.pin(tvDrawerDeltaBadge, "OPTIMAL", HudUi.POSITIVE);
                } else if (deltaV_mV < 50.0) {
                    HudUi.pin(tvDrawerDeltaBadge, "IMBALANCE", HudUi.WARNING);
                } else {
                    HudUi.pin(tvDrawerDeltaBadge, "CRITICAL", HudUi.ERROR);
                }
            }

            // Cell voltages
            if (s.cells != null) {
                for (int i = 0; i < Math.min(14, s.cells.length); i++) {
                    if (tvCellVoltages[i] != null) {
                        double v = s.cells[i];
                        tvCellVoltages[i].setText(Double.isNaN(v) ? String.format(java.util.Locale.US, "C%02d: -- V", i + 1)
                                : String.format(java.util.Locale.US, "C%02d: %.3fV", i + 1, v));
                    }
                }
            }
        }
    }

    private void initCapsuleWindow() {
        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        capsuleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        capsuleParams.gravity = Gravity.TOP | Gravity.START;
        capsuleParams.x = prefs.getInt("capsule_x", 32);
        capsuleParams.y = prefs.getInt("capsule_y", 120);

        capsuleView = buildCapsuleLayout();
        windowManager.addView(capsuleView, capsuleParams);
    }

    private View buildCapsuleLayout() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER_VERTICAL);
        root.setBackground(HudUi.capsuleShape(this, HudUi.CAPSULE_BG, HudUi.LINE));
        int padH = HudUi.dp(this, 14);
        int padV = HudUi.dp(this, 8);
        root.setPadding(padH, padV, padH, padV);

        View dot = new View(this);
        dot.setBackground(HudUi.round(this, HudUi.POSITIVE, 4, 0));
        int dotSize = HudUi.dp(this, 7);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dotSize, dotSize);
        dotLp.rightMargin = HudUi.dp(this, 8);
        root.addView(dot, dotLp);

        tvCapsuleSoc = HudUi.text(this, "80%", 13, HudUi.TEXT);
        tvCapsuleSoc.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(tvCapsuleSoc);

        root.addView(createSeparator());

        tvCapsuleDelta = HudUi.text(this, "ΔV 11 mV", 12, HudUi.POSITIVE);
        tvCapsuleDelta.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(tvCapsuleDelta);

        root.addView(createSeparator());

        tvCapsuleTemp = HudUi.text(this, "28°C", 12, HudUi.MUTED);
        root.addView(tvCapsuleTemp);

        tvCapsuleBadge = HudUi.pin(this, "80% CAP", HudUi.PRIMARY);
        LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        badgeLp.leftMargin = HudUi.dp(this, 8);
        root.addView(tvCapsuleBadge, badgeLp);

        root.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDrag = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = capsuleParams.x;
                        initialY = capsuleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDrag = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isDrag = true;
                            capsuleParams.x = initialX + (int) dx;
                            capsuleParams.y = initialY + (int) dy;
                            windowManager.updateViewLayout(capsuleView, capsuleParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDrag) {
                            toggleDrawer();
                        } else {
                            prefs.edit()
                                    .putInt("capsule_x", capsuleParams.x)
                                    .putInt("capsule_y", capsuleParams.y)
                                    .apply();
                        }
                        return true;
                }
                return false;
            }
        });

        return root;
    }

    private View createSeparator() {
        TextView sep = HudUi.text(this, " · ", 12, HudUi.DIM);
        sep.setPadding(HudUi.dp(this, 4), 0, HudUi.dp(this, 4), 0);
        return sep;
    }

    private void initDrawerWindow() {
        int windowType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        drawerParams = new WindowManager.LayoutParams(
                HudUi.dp(this, 350),
                WindowManager.LayoutParams.WRAP_CONTENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        drawerParams.gravity = Gravity.CENTER;

        drawerView = buildDrawerLayout();
    }

    private View buildDrawerLayout() {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(HudUi.round(this, HudUi.SURFACE_TRANSLUCENT, 20, HudUi.LINE));
        int pad = HudUi.dp(this, 18);
        card.setPadding(pad, pad, pad, pad);
        scroll.addView(card, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = HudUi.text(this, "SOLA EV DASHBOARD", 15, HudUi.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);

        TextView tvSub = HudUi.text(this, "Real-Time MoveOS Telemetry & Quick Actions", 11, HudUi.MUTED);
        titleCol.addView(tvSub);
        header.addView(titleCol);

        Button btnClose = HudUi.button(this, "CLOSE", v -> collapseDrawer());
        header.addView(btnClose);
        card.addView(header);

        card.addView(HudUi.space(this, 14));
        card.addView(HudUi.divider(this));
        card.addView(HudUi.space(this, 14));

        card.addView(HudUi.heading(this, "Power & Battery Overview"));
        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);

        row1.addView(createStatCard("BATTERY SOC", tvDrawerSoc = HudUi.text(this, "80%", 18, HudUi.TEXT)));
        row1.addView(HudUi.space(this, 8));
        row1.addView(createStatCard("LIVE POWER", tvDrawerPower = HudUi.text(this, "0.42 kW", 18, HudUi.PRIMARY)));
        card.addView(row1);

        card.addView(HudUi.space(this, 8));
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(createStatCard("PACK VOLTS", tvDrawerVoltage = HudUi.text(this, "54.8 V", 15, HudUi.TEXT)));
        row2.addView(HudUi.space(this, 8));
        row2.addView(createStatCard("PACK AMPS", tvDrawerCurrent = HudUi.text(this, "7.8 A", 15, HudUi.TEXT)));
        card.addView(row2);

        card.addView(HudUi.space(this, 14));
        card.addView(HudUi.divider(this));
        card.addView(HudUi.space(this, 14));

        card.addView(HudUi.heading(this, "14-Series Cell Spread & Delta"));
        LinearLayout deltaRow = new LinearLayout(this);
        deltaRow.setOrientation(LinearLayout.HORIZONTAL);
        deltaRow.setGravity(Gravity.CENTER_VERTICAL);

        tvDrawerDelta = HudUi.text(this, "11 mV", 22, HudUi.POSITIVE);
        tvDrawerDelta.setTypeface(Typeface.DEFAULT_BOLD);
        deltaRow.addView(tvDrawerDelta);

        deltaRow.addView(HudUi.space(this, 10));
        tvDrawerDeltaBadge = HudUi.pin(this, "OPTIMAL", HudUi.POSITIVE);
        deltaRow.addView(tvDrawerDeltaBadge);

        View spring = new View(this);
        deltaRow.addView(spring, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout highLowCol = new LinearLayout(this);
        highLowCol.setOrientation(LinearLayout.VERTICAL);
        highLowCol.setGravity(Gravity.END);
        tvDrawerHighCell = HudUi.text(this, "High: 3.931 V", 11, HudUi.MUTED);
        tvDrawerLowCell = HudUi.text(this, "Low: 3.920 V", 11, HudUi.MUTED);
        highLowCol.addView(tvDrawerHighCell);
        highLowCol.addView(tvDrawerLowCell);
        deltaRow.addView(highLowCol);
        card.addView(deltaRow);

        card.addView(HudUi.space(this, 10));

        GridLayout cellGrid = new GridLayout(this);
        cellGrid.setColumnCount(2);
        for (int i = 0; i < 14; i++) {
            tvCellVoltages[i] = HudUi.text(this, String.format(java.util.Locale.US, "C%02d: 3.925V", i + 1), 11, HudUi.MUTED);
            tvCellVoltages[i].setBackground(HudUi.round(this, HudUi.SURFACE_HIGH, 6, 0));
            int p = HudUi.dp(this, 6);
            tvCellVoltages[i].setPadding(p, p / 2, p, p / 2);
            GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
            glp.width = 0;
            glp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            glp.setMargins(HudUi.dp(this, 2), HudUi.dp(this, 2), HudUi.dp(this, 2), HudUi.dp(this, 2));
            cellGrid.addView(tvCellVoltages[i], glp);
        }
        card.addView(cellGrid);

        card.addView(HudUi.space(this, 14));
        card.addView(HudUi.divider(this));
        card.addView(HudUi.space(this, 14));

        card.addView(HudUi.heading(this, "EV Smart Controls"));

        btnChargeCapToggle = HudUi.button(this,
                canReader.isChargeCapEnabled() ? "80% BATTERY SAVER: ON" : "80% BATTERY SAVER: OFF",
                v -> {
                    boolean next = !canReader.isChargeCapEnabled();
                    canReader.setChargeCapEnabled(next);
                    prefs.edit().putBoolean("charge_cap_enabled", next).apply();
                    btnChargeCapToggle.setText(next ? "80% BATTERY SAVER: ON" : "80% BATTERY SAVER: OFF");
                    btnChargeCapToggle.setTextColor(next ? HudUi.POSITIVE : HudUi.MUTED);
                    if (tvCapsuleBadge != null) tvCapsuleBadge.setVisibility(next ? View.VISIBLE : View.GONE);
                });
        btnChargeCapToggle.setTextColor(canReader.isChargeCapEnabled() ? HudUi.POSITIVE : HudUi.MUTED);
        card.addView(btnChargeCapToggle);

        card.addView(HudUi.space(this, 8));

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        Button btnTrunk = HudUi.button(this, "POP TRUNK", v -> canReader.popTrunk());
        btnTrunk.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionRow.addView(btnTrunk);

        actionRow.addView(HudUi.space(this, 8));

        Button btnHazard = HudUi.button(this, "HAZARDS", v -> canReader.toggleHazard());
        btnHazard.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        actionRow.addView(btnHazard);

        card.addView(actionRow);

        card.addView(HudUi.space(this, 8));

        Button btnStayOn = HudUi.button(this, "PREVENT DISPLAY SLEEP", v -> {
            try {
                Runtime.getRuntime().exec(new String[]{"sh", "-c", "svc power stayon true"});
            } catch (Exception ignored) {}
        });
        card.addView(btnStayOn);

        return scroll;
    }

    private View createStatCard(String label, TextView valueView) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(HudUi.round(this, HudUi.SURFACE_HIGH, 12, HudUi.LINE));
        int p = HudUi.dp(this, 12);
        c.setPadding(p, p, p, p);
        c.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView lbl = HudUi.text(this, label, 10, HudUi.MUTED);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setLetterSpacing(0.06f);
        c.addView(lbl);
        c.addView(HudUi.space(this, 4));
        c.addView(valueView);
        return c;
    }

    private void toggleDrawer() {
        if (isDrawerOpen) {
            collapseDrawer();
        } else {
            expandDrawer();
        }
    }

    private void expandDrawer() {
        if (isDrawerOpen) return;
        windowManager.addView(drawerView, drawerParams);
        isDrawerOpen = true;
    }

    private void collapseDrawer() {
        if (!isDrawerOpen) return;
        windowManager.removeView(drawerView);
        isDrawerOpen = false;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Sola HUD Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Maintains floating EV dashboard overlay and CAN telemetry link");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildForegroundNotification() {
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setContentTitle("Sola HUD Active")
                .setContentText("Monitoring live CAN telemetry and battery health")
                .setSmallIcon(R.drawable.ic_speed)
                .setOngoing(true)
                .build();
    }
}
