package dev.sola;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import dev.sola.core.Addr;
import dev.sola.core.HotspotScanner;
import dev.sola.core.Live;

/**
 * Sola - Next-generation Ola S1 Telemetry & Diagnostics Tool.
 * Features authentic Material 3 Obsidian dark UI/UX with Axiom vector icons,
 * Material 3 bottom navigation bar, live throughput pins, and 1-tap mobile hotspot auto-discovery.
 * Zero emojis.
 */
public final class MainActivity extends Activity implements Bus.Listener {

    private static final int REQ_LOCATION = 41;
    private static final String PREFS = "sola";
    private static final String KEY_ADDRESS = "address";

    // 5 Axiom Primary Destinations
    private static final String[] TAB_TITLES = {
            "Overview", "Diagnostics", "Testing", "Faults", "Developer"
    };

    private static final int[] TAB_ICONS = {
            R.drawable.ic_scooter,
            R.drawable.ic_diagnostics,
            R.drawable.ic_testing,
            R.drawable.ic_warning,
            R.drawable.ic_terminal
    };

    private static final class RetainedState {
        final Conn conn;
        final Bus bus;
        final int activeTab;

        RetainedState(Conn conn, Bus bus, int activeTab) {
            this.conn = conn;
            this.bus = bus;
            this.activeTab = activeTab;
        }
    }

    private Conn conn;
    private final Tab[] tabs = new Tab[5];
    private final LinearLayout[] navItemContainers = new LinearLayout[5];
    private final LinearLayout[] navPillContainers = new LinearLayout[5];
    private final ImageView[] navIcons = new ImageView[5];
    private final TextView[] navLabels = new TextView[5];
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Bus bus;
    private FrameLayout contentFrame;
    private TextView liveBadge;
    private ImageView btnHotspot;
    private Button btnConnect;
    private int currentTab = -1;
    private String currentAddress = "192.168.1.96";
    private String pendingAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String themeMode = prefs.getString("app_theme", "dark");
        boolean isSystemNight = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Ui.applyTheme(themeMode, isSystemNight);

        // Configure Status Bar & Navigation Bar appearance for Light / Dark themes
        Window window = getWindow();
        window.setStatusBarColor(Ui.BG);
        window.setNavigationBarColor(Ui.SURFACE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (!Ui.isDarkTheme()) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        }

        RetainedState retained = (RetainedState) getLastNonConfigurationInstance();
        if (retained != null && retained.conn != null) {
            conn = retained.conn;
            bus = retained.bus;
        } else {
            conn = new Conn();
            bus = new Bus(this, conn);
            bus.start();
        }

        // Core REA features consolidated into Axiom's 5 destinations
        tabs[0] = new VehicleTab(this, conn, bus);
        tabs[1] = new DiagnosticsTab(this, conn, bus);
        tabs[2] = new TestingTab(this, conn, bus);
        tabs[3] = new FaultsTab(this, conn, bus);
        tabs[4] = new DeveloperTab(this, conn, bus);

        currentAddress = prefs.getString(KEY_ADDRESS, "192.168.1.96");

        setContentView(buildRoot());
        int initialTab = retained != null ? retained.activeTab : (savedInstanceState != null ? savedInstanceState.getInt("active_tab", 0) : 0);
        selectTab(initialTab);

        bus.attach(this);
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (bus != null) {
            bus.detach(this);
        }
        return new RetainedState(conn, bus, currentTab);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("active_tab", currentTab);
    }

    @Override
    protected void onDestroy() {
        if (bus != null) {
            bus.detach(this);
        }
        if (!isChangingConfigurations()) {
            if (bus != null) {
                bus.stop();
            }
            if (conn != null) {
                conn.close();
            }
        }
        super.onDestroy();
    }

    @Override
    public void onTick(Live.Snapshot s) {
        ui.post(() -> {
            if (conn.ready()) {
                if (s != null && s.pps > 0) {
                    liveBadge.setText(String.format(Locale.US, "● %d PPS", s.pps));
                    liveBadge.setTextColor(Ui.POSITIVE);
                    liveBadge.setBackground(Ui.round(this, Ui.SURFACE_HIGH, 12, 0));
                } else {
                    liveBadge.setText("● CONNECTED");
                    liveBadge.setTextColor(Ui.PRIMARY);
                    liveBadge.setBackground(Ui.round(this, Ui.SURFACE_HIGH, 12, 0));
                }
            } else {
                liveBadge.setText("OFFLINE");
                liveBadge.setTextColor(Ui.DIM);
                liveBadge.setBackground(Ui.round(this, Ui.SURFACE_HIGH, 12, 0));
            }
        });
    }

    // ---------------------------------------------------------------- UI Layout

    private View buildRoot() {
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);

        // 1. Axiom Top App Bar
        LinearLayout topBar = Ui.row(this);
        topBar.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        topBar.setBackgroundColor(Ui.BG);

        // Left: Clean Brand Logo & Wordmark
        LinearLayout brandCol = Ui.row(this);
        brandCol.setGravity(Gravity.CENTER_VERTICAL);
        brandCol.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ImageView brandLogo = new ImageView(this);
        brandLogo.setImageResource(Ui.isDarkTheme() ? R.drawable.ic_sola_brand : R.drawable.ic_sola_brand_light);
        brandLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brandLogo.setAdjustViewBounds(true);
        LinearLayout.LayoutParams brandLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 22));
        brandCol.addView(brandLogo, brandLp);

        topBar.addView(brandCol);

        // Spacer to push right actions cleanly to the right
        View spacer = new View(this);
        topBar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        // Right Actions: Live PPS Pin + Hotspot Button + Connect Button (Uniform 32dp height)
        LinearLayout rightActions = Ui.row(this);

        liveBadge = Ui.text(this, conn != null && conn.ready() ? "LIVE" : "OFFLINE", 10, conn != null && conn.ready() ? Ui.POSITIVE : Ui.DIM);
        liveBadge.setTypeface(Typeface.DEFAULT_BOLD);
        liveBadge.setLetterSpacing(0.06f);
        liveBadge.setIncludeFontPadding(false);
        liveBadge.setGravity(Gravity.CENTER);
        liveBadge.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        liveBadge.setBackground(Ui.round(this, Ui.SURFACE_HIGH, 12, 0));
        LinearLayout.LayoutParams lbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34));
        rightActions.addView(liveBadge, lbLp);

        btnHotspot = new ImageView(this);
        btnHotspot.setImageResource(R.drawable.ic_wifi_tethering);
        btnHotspot.setColorFilter(Ui.TEXT);
        btnHotspot.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnHotspot.setPadding(Ui.dp(this, 8), Ui.dp(this, 7), Ui.dp(this, 8), Ui.dp(this, 7));
        btnHotspot.setBackground(Ui.round(this, Ui.SURFACE_HIGH, 11, 0));
        btnHotspot.setOnClickListener(v -> startHotspotDiscovery());
        LinearLayout.LayoutParams hpLp = new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 34));
        hpLp.leftMargin = Ui.dp(this, 8);
        rightActions.addView(btnHotspot, hpLp);

        btnConnect = new Button(this);
        btnConnect.setText(conn.ready() ? "CONNECTED" : "CONNECT");
        btnConnect.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        btnConnect.setTypeface(Typeface.DEFAULT_BOLD);
        btnConnect.setLetterSpacing(0.06f);
        btnConnect.setTextColor(Ui.ACCENT_TEXT);
        btnConnect.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 12), 0);
        btnConnect.setMinHeight(Ui.dp(this, 34));
        btnConnect.setMinimumHeight(Ui.dp(this, 34));
        btnConnect.setBackground(Ui.round(this, Ui.PRIMARY, 12, 0));
        btnConnect.setOnClickListener(v -> showConnectionDialog());
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34));
        cpLp.leftMargin = Ui.dp(this, 8);
        rightActions.addView(btnConnect, cpLp);

        topBar.addView(rightActions);
        root.addView(topBar);

        // Divider
        View topDivider = new View(this);
        topDivider.setBackgroundColor(Ui.LINE);
        root.addView(topDivider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        // 2. Main Content Frame (Hosts current active tab)
        contentFrame = new FrameLayout(this);
        root.addView(contentFrame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 3. Axiom Material 3 Bottom Navigation Bar
        View botDivider = new View(this);
        botDivider.setBackgroundColor(Ui.LINE);
        root.addView(botDivider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));

        LinearLayout bottomNav = Ui.row(this);
        bottomNav.setBackgroundColor(Ui.SURFACE);
        // Added 20dp bottom padding for Android gesture navigation insets
        bottomNav.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 20));
        bottomNav.setMinimumHeight(Ui.dp(this, 76));

        for (int i = 0; i < TAB_TITLES.length; i++) {
            final int index = i;
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 2));

            // Indicator Pill (pin) container for icon
            LinearLayout pill = Ui.column(this);
            pill.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams pillLp = new LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 30));
            pill.setLayoutParams(pillLp);

            ImageView icon = new ImageView(this);
            icon.setImageResource(TAB_ICONS[i]);
            LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22));
            pill.addView(icon, iconLp);

            TextView label = Ui.text(this, TAB_TITLES[i], 10.5f, Ui.MUTED);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setSingleLine(true);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, Ui.dp(this, 3), 0, 0);

            item.addView(pill);
            item.addView(label);

            item.setOnClickListener(v -> selectTab(index));

            navItemContainers[i] = item;
            navPillContainers[i] = pill;
            navIcons[i] = icon;
            navLabels[i] = label;

            LinearLayout.LayoutParams itemLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            bottomNav.addView(item, itemLp);
        }

        root.addView(bottomNav);

        return root;
    }

    private void selectTab(int index) {
        if (index == currentTab) return;
        if (currentTab >= 0 && currentTab < tabs.length) {
            tabs[currentTab].onHide();
        }

        currentTab = index;
        contentFrame.removeAllViews();
        contentFrame.addView(tabs[index].view(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Update Material 3 Navigation Items & Active Indicator Pins
        for (int i = 0; i < TAB_TITLES.length; i++) {
            if (i == index) {
                // Active: Electric Cyan pill with dark navy/teal icon (Image 2) & Electric Cyan label
                navPillContainers[i].setBackground(Ui.round(this, Ui.PRIMARY, 16, 0));
                navIcons[i].setColorFilter(Ui.ACCENT_TEXT);
                navLabels[i].setTextColor(Ui.PRIMARY);
            } else {
                // Inactive: Transparent container, muted icon & label
                navPillContainers[i].setBackground(null);
                navIcons[i].setColorFilter(Ui.MUTED);
                navLabels[i].setTextColor(Ui.MUTED);
            }
        }

        tabs[index].onShow();
    }

    // ------------------------------------------------------------ Connection Dialog

    private void showConnectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout dialogView = Ui.card(this, 20);
        dialogView.setBackground(Ui.round(this, Ui.SURFACE, 24, 0));

        TextView dTitle = Ui.text(this, "Connect to Scooter", 18, Ui.TEXT);
        dTitle.setTypeface(Typeface.DEFAULT_BOLD);
        dialogView.addView(dTitle);

        TextView dDesc = Ui.text(this, "Enter scooter IP address or use 1-tap mobile hotspot auto-discovery.", 12, Ui.MUTED);
        dDesc.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 16));
        dialogView.addView(dDesc);

        // Material 3 Input Bar with Search Icon
        LinearLayout inputBar = Ui.row(this);
        inputBar.setPadding(Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4));
        inputBar.setBackground(Ui.round(this, Ui.SURFACE_HIGH, 16, 0));

        ImageView wifiIcon = new ImageView(this);
        wifiIcon.setImageResource(R.drawable.ic_wifi_tethering);
        wifiIcon.setColorFilter(Ui.MUTED);
        LinearLayout.LayoutParams wip = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
        wip.rightMargin = Ui.dp(this, 10);
        inputBar.addView(wifiIcon, wip);

        EditText etIp = new EditText(this);
        etIp.setText(currentAddress);
        etIp.setHint("scooter IP (e.g. 192.168.1.96)");
        etIp.setHintTextColor(Ui.MUTED);
        etIp.setTextColor(Ui.TEXT);
        etIp.setTextSize(14);
        etIp.setSingleLine(true);
        etIp.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        etIp.setBackground(null);
        inputBar.addView(etIp, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        dialogView.addView(inputBar);

        // Action Buttons Row
        LinearLayout actions = Ui.row(this);
        actions.setPadding(0, Ui.dp(this, 20), 0, 0);

        Button btnScan = Ui.button(this, "Scan Hotspot", null);
        btnScan.setTextColor(Ui.PRIMARY);
        btnScan.setBackground(Ui.round(this, Ui.alpha(Ui.PRIMARY, 0x22), 12, 0));

        Button btnDoConnect = Ui.accent(this, "Connect", null);

        actions.addView(btnScan, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 10), 1));
        actions.addView(space);
        actions.addView(btnDoConnect, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        dialogView.addView(actions);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        btnScan.setOnClickListener(v -> {
            dialog.dismiss();
            startHotspotDiscovery();
        });

        btnDoConnect.setOnClickListener(v -> {
            String typed = etIp.getText().toString().trim();
            if (!typed.isEmpty()) {
                currentAddress = typed;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putString(KEY_ADDRESS, typed).apply();
                dialog.dismiss();
                initiateConnection(typed);
            }
        });

        dialog.show();
    }

    // ------------------------------------------------------------ Method 1: Hotspot Discovery

    private void startHotspotDiscovery() {
        btnHotspot.setEnabled(false);
        Toast.makeText(this, "Scanning mobile hotspot on port 50005...", Toast.LENGTH_SHORT).show();

        HotspotScanner.discoverScooter(new HotspotScanner.ScanCallback() {
            @Override
            public void onProgress(int scanned, int total) {}

            @Override
            public void onFound(String ipAddress) {
                ui.post(() -> {
                    currentAddress = ipAddress;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_ADDRESS, ipAddress).apply();
                    Toast.makeText(MainActivity.this, "Found Scooter: " + ipAddress + "! Connecting...", Toast.LENGTH_LONG).show();
                    initiateConnection(ipAddress);
                });
            }

            @Override
            public void onComplete(boolean found) {
                ui.post(() -> {
                    btnHotspot.setEnabled(true);
                    if (!found) {
                        Toast.makeText(MainActivity.this, "Scooter not detected on hotspot subnet.", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------ Connection Execution

    private void initiateConnection(String ip) {
        if (!Addr.isIpv4(ip) && !ip.contains(":")) {
            Toast.makeText(this, "Invalid IPv4 address format", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            pendingAddress = ip;
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQ_LOCATION);
            return;
        }

        doConnect(ip);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_LOCATION && pendingAddress != null) {
            String ip = pendingAddress;
            pendingAddress = null;
            doConnect(ip);
        }
    }

    private void doConnect(String target) {
        btnConnect.setEnabled(false);
        btnConnect.setText("CONNECTING...");

        new Thread(() -> {
            try {
                Net.Result result = Net.connect(getApplicationContext(), target, 4000);
                conn.install(result.rpc, result.target, result.ipv6, result.note);
                bus.start();
                runOnUiThread(() -> {
                    btnConnect.setEnabled(true);
                    btnConnect.setText("CONNECTED");
                    Toast.makeText(MainActivity.this, "Connected: " + result.target, Toast.LENGTH_SHORT).show();
                    if (currentTab >= 0) {
                        tabs[currentTab].onHide();
                        tabs[currentTab].onShow();
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    btnConnect.setEnabled(true);
                    btnConnect.setText("CONNECT");
                    Toast.makeText(MainActivity.this, "Connection failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "sola-connect").start();
    }

    void report(String text, int color) {
        ui.post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onBackPressed() {
        if (currentTab > 0) {
            selectTab(0);
        } else {
            moveTaskToBack(true);
        }
    }
}
