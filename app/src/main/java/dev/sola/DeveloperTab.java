package dev.sola;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import dev.sola.core.Live;

/**
 * Axiom-styled Developer & Power Tools Tab.
 * Consolidates CAN Session Recorder, Root ADB Shell, Mode Switcher, and MoveOS Settings.
 * Features expandable/collapsible solid Material 3 cards, closed by default, with persistent user selection.
 * Zero emojis.
 */
public final class DeveloperTab implements Tab, Bus.Listener {

    private static final String PREF_NAME = "sola";
    private static final String KEY_RECORDER = "expand_recorder";
    private static final String KEY_MODE = "expand_mode";
    private static final String KEY_SETTINGS = "expand_settings";
    private static final String KEY_ADB = "expand_adb";

    private final Activity activity;
    private final Conn conn;
    private final Bus bus;
    private final ScrollView page;
    private final SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Live.Snapshot latestSnapshot;

    private final LogTab logTab;
    private final AdbTab adbTab;
    private final ModeTab modeTab;
    private final SettingsTab settingsTab;

    public DeveloperTab(MainActivity activity, Conn conn, Bus bus) {
        this.activity = activity;
        this.conn = conn;
        this.bus = bus;
        this.prefs = activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        this.logTab = new LogTab(activity, conn, bus);
        this.adbTab = new AdbTab(activity, conn);
        this.modeTab = new ModeTab(activity, conn);
        this.settingsTab = new SettingsTab(activity, conn);

        this.page = Ui.page(activity);
        LinearLayout body = Ui.body(page);

        // 0. Theme Appearance Switcher (Instant Dark / Light / System Mode)
        View themeCard = createThemeCard();
        body.addView(themeCard);

        addSpace(body, 12);

        // 1. CAN Diagnostic Protocol Mode (Normal Passive Sniffing vs Extended UDS)
        View canModeCard = createDiagnosticModeCard();
        body.addView(canModeCard);

        addSpace(body, 12);

        // 2. Vehicle Operation Mode (Closed by default, remembers user preference)
        final TextView[] tvModePinHolder = new TextView[1];
        View modeCard = createExpandableSection(
                KEY_MODE,
                R.drawable.ic_operation_mode,
                null,
                "Vehicle Operation Mode",
                null,
                "MODE",
                Ui.POSITIVE,
                modeTab.view(),
                pin -> tvModePinHolder[0] = pin
        );
        body.addView(modeCard);

        modeTab.setModeListener((netMode, vehMode) -> {
            TextView pin = tvModePinHolder[0];
            if (pin == null) return;
            if ("cust".equalsIgnoreCase(netMode)) {
                pin.setText("CUSTOMER");
                pin.setTextColor(Ui.POSITIVE);
                pin.setBackground(Ui.round(activity, Ui.alpha(Ui.POSITIVE, 0x22), 12, 0));
            } else if ("serv".equalsIgnoreCase(netMode)) {
                pin.setText("SERVICE");
                pin.setTextColor(Ui.WARNING);
                pin.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x22), 12, 0));
            }
        });

        addSpace(body, 12);

        // 3. Root ADB Terminal (Closed by default, remembers user preference)
        View adbCard = createExpandableSection(
                KEY_ADB,
                R.drawable.ic_terminal,
                null,
                "Root ADB Terminal",
                null,
                "ROOT",
                Ui.WARNING,
                adbTab.view()
        );
        body.addView(adbCard);

        addSpace(body, 12);

        // 4. MoveOS System Settings (Closed by default, remembers user preference)
        View setCard = createExpandableSection(
                KEY_SETTINGS,
                R.drawable.ic_settings,
                null,
                "MoveOS System Settings",
                null,
                "SYSTEM",
                Ui.MUTED,
                settingsTab.view()
        );
        body.addView(setCard);

        addSpace(body, 12);

        // 5. CAN Session Recorder (Closed by default, remembers user preference)
        View recCard = createExpandableSection(
                KEY_RECORDER,
                R.drawable.ic_live_signal,
                null,
                "CAN Session Recorder",
                null,
                "LOGGER",
                Ui.PRIMARY,
                logTab.view()
        );
        body.addView(recCard);

        addSpace(body, 12);

        // 6. Software Updates
        View updateCard = createSoftwareUpdatesCard();
        body.addView(updateCard);

        addSpace(body, 12);

        // 7. Project & Developer Credit Card
        View creditCard = createCreditCard();
        body.addView(creditCard);

        addSpace(body, 20);
    }

    private View createSoftwareUpdatesCard() {
        LinearLayout card = Ui.card(activity);

        // Header Row
        LinearLayout row = Ui.row(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout iconBox = Ui.column(activity);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_system_update);
        icon.setColorFilter(Ui.TEXT);
        iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36));
        ip.rightMargin = Ui.dp(activity, 12);
        row.addView(iconBox, ip);

        LinearLayout textCol = Ui.column(activity);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = Ui.text(activity, "Software Updates", 15, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = Ui.text(activity, "v" + BuildConfig.VERSION_NAME + " • MoveOS Diagnostic Platform", 11, Ui.MUTED);
        tvSub.setPadding(0, Ui.dp(activity, 2), 0, 0);
        textCol.addView(tvSub);

        row.addView(textCol);
        card.addView(row);

        // Divider
        View div = Ui.divider(activity);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        dlp.setMargins(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 10));
        card.addView(div, dlp);

        // Updates Button
        android.widget.Button btnCheckUpdate = Ui.button(activity, "Check for Updates", null);
        btnCheckUpdate.setTextSize(11);
        btnCheckUpdate.setTypeface(Typeface.DEFAULT_BOLD);
        btnCheckUpdate.setMinHeight(Ui.dp(activity, 34));
        btnCheckUpdate.setMinimumHeight(Ui.dp(activity, 34));

        applyUpdateStatus(tvSub, btnCheckUpdate, UpdateManager.getSavedPendingUpdate(activity));

        card.addView(btnCheckUpdate, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 34)));

        return card;
    }

    private void applyUpdateStatus(TextView tvSub, android.widget.Button btn, UpdateManager.ReleaseInfo pending) {
        if (pending != null) {
            tvSub.setText("v" + BuildConfig.VERSION_NAME + " • Update Available (v" + pending.version + ")");
            tvSub.setTextColor(Ui.WARNING);
            btn.setText("Get Update (v" + pending.version + ")");
            btn.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x33), 10, Ui.WARNING));
            btn.setTextColor(Ui.WARNING);
            btn.setOnClickListener(v -> UpdateManager.promptUpdate(activity, pending));
        } else {
            tvSub.setText("v" + BuildConfig.VERSION_NAME + " • MoveOS Diagnostic Platform");
            tvSub.setTextColor(Ui.MUTED);
            btn.setText("Check for Updates");
            btn.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
            btn.setTextColor(Ui.TEXT);
            btn.setOnClickListener(v -> {
                btn.setEnabled(false);
                btn.setText("Checking...");
                UpdateManager.checkForUpdates(activity, false, new UpdateManager.CheckCallback() {
                    @Override
                    public void onUpdateAvailable(UpdateManager.ReleaseInfo info) {
                        btn.setEnabled(true);
                        applyUpdateStatus(tvSub, btn, info);
                        UpdateManager.promptUpdate(activity, info);
                    }

                    @Override
                    public void onAlreadyLatest(String currentVersion) {
                        btn.setEnabled(true);
                        applyUpdateStatus(tvSub, btn, null);
                        android.widget.Toast.makeText(activity, "Sola is up to date (v" + currentVersion + ")", android.widget.Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        btn.setEnabled(true);
                        btn.setText("Check for Updates");
                        android.widget.Toast.makeText(activity, "Update check failed: " + message, android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }
    }

    private View createCreditCard() {
        LinearLayout card = Ui.card(activity);

        LinearLayout row = Ui.row(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout iconBox = Ui.column(activity);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_link);
        icon.setColorFilter(Ui.TEXT);
        iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));

        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36));
        ip.rightMargin = Ui.dp(activity, 12);
        row.addView(iconBox, ip);

        LinearLayout textCol = Ui.column(activity);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvTitle = Ui.text(activity, "Sola Diagnostics", 15, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        textCol.addView(tvTitle);

        TextView tvSub = Ui.text(activity, "Developed by @sillypari • MoveOS CAN Suite", 11, Ui.MUTED);
        tvSub.setPadding(0, Ui.dp(activity, 2), 0, 0);
        textCol.addView(tvSub);

        row.addView(textCol);
        card.addView(row);

        // Divider
        View div = Ui.divider(activity);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        dlp.setMargins(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 10));
        card.addView(div, dlp);

        // Action Buttons Row: [GitHub Profile] [Project Repo]
        LinearLayout btnRow = Ui.row(activity);

        android.widget.Button btnProfile = Ui.button(activity, "GitHub Profile", v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/sillypari"));
            activity.startActivity(intent);
        });
        btnProfile.setTextSize(11);
        btnProfile.setTypeface(Typeface.DEFAULT_BOLD);
        btnProfile.setTextColor(Ui.TEXT);
        btnProfile.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        btnProfile.setMinHeight(Ui.dp(activity, 36));
        btnProfile.setMinimumHeight(Ui.dp(activity, 36));

        android.widget.Button btnRepo = Ui.accent(activity, "Project Repository", v -> {
            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/sillypari/Sola"));
            activity.startActivity(intent);
        });
        btnRepo.setTextSize(11);
        btnRepo.setTypeface(Typeface.DEFAULT_BOLD);
        btnRepo.setMinHeight(Ui.dp(activity, 36));
        btnRepo.setMinimumHeight(Ui.dp(activity, 36));

        btnRow.addView(btnProfile, new LinearLayout.LayoutParams(0, Ui.dp(activity, 36), 1f));
        View space = new View(activity);
        space.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 8), 1));
        btnRow.addView(space);
        btnRow.addView(btnRepo, new LinearLayout.LayoutParams(0, Ui.dp(activity, 36), 1f));

        card.addView(btnRow);
        return card;
    }

    private View createExpandableSection(String prefKey, int iconRes, String eyebrowText, String title, String subtitle,
                                         String pinText, int pinColor, View content) {
        return createExpandableSection(prefKey, iconRes, eyebrowText, title, subtitle, pinText, pinColor, content, null);
    }

    private View createExpandableSection(String prefKey, int iconRes, String eyebrowText, String title, String subtitle,
                                         String pinText, int pinColor, View content, java.util.function.Consumer<TextView> pinConsumer) {
        // Read persisted preference; default to closed (false)
        boolean isExpanded = prefs.getBoolean(prefKey, false);

        LinearLayout card = Ui.card(activity);

        // Header Row (Clickable)
        LinearLayout header = Ui.row(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, isExpanded ? Ui.dp(activity, 10) : 0);

        if (iconRes != 0) {
            LinearLayout iconBox = Ui.column(activity);
            iconBox.setGravity(Gravity.CENTER);
            iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));
            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconRes);
            icon.setColorFilter(Ui.TEXT);
            iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36));
            ip.rightMargin = Ui.dp(activity, 12);
            header.addView(iconBox, ip);
        }

        LinearLayout titleCol = Ui.column(activity);
        if (eyebrowText != null && !eyebrowText.isEmpty()) {
            TextView eyebrow = Ui.text(activity, eyebrowText, 9.5f, Ui.MUTED);
            eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
            eyebrow.setLetterSpacing(0.08f);
            titleCol.addView(eyebrow);
        }

        TextView tvTitle = Ui.text(activity, title, 15, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);

        if (subtitle != null && !subtitle.isEmpty()) {
            TextView tvSub = Ui.text(activity, subtitle, 11, Ui.MUTED);
            tvSub.setPadding(0, Ui.dp(activity, 2), 0, 0);
            titleCol.addView(tvSub);
        }

        header.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (pinText != null && !pinText.isEmpty()) {
            TextView pin = Ui.pin(activity, pinText, pinColor);
            header.addView(pin);
            if (pinConsumer != null) {
                pinConsumer.accept(pin);
            }
        }

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_expand_more);
        chevron.setColorFilter(Ui.MUTED);
        chevron.setRotation(isExpanded ? 180f : 0f);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22));
        chp.leftMargin = Ui.dp(activity, 8);
        header.addView(chevron, chp);

        card.addView(header);

        // Divider visible when expanded
        View div = Ui.divider(activity);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        dlp.setMargins(0, 0, 0, Ui.dp(activity, 10));
        div.setLayoutParams(dlp);
        div.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        card.addView(div);

        // Content Container
        LinearLayout contentBox = Ui.column(activity);
        if (content.getParent() != null) {
            ((ViewGroup) content.getParent()).removeView(content);
        }
        contentBox.addView(content);
        contentBox.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        card.addView(contentBox);

        // Smooth toggle on click + Persist customer choice immediately
        header.setOnClickListener(v -> {
            boolean willExpand = contentBox.getVisibility() == View.GONE;
            contentBox.setVisibility(willExpand ? View.VISIBLE : View.GONE);
            div.setVisibility(willExpand ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(willExpand ? 180f : 0f).setDuration(200).start();
            header.setPadding(0, 0, 0, willExpand ? Ui.dp(activity, 10) : 0);
            prefs.edit().putBoolean(prefKey, willExpand).apply();
        });

        return card;
    }

    private View createDiagnosticModeCard() {
        final boolean isExpanded = prefs.getBoolean("expand_can_mode", false);
        LinearLayout card = Ui.card(activity);

        LinearLayout topRow = Ui.row(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, isExpanded ? Ui.dp(activity, 10) : 0);

        LinearLayout iconBox = Ui.column(activity);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_uds_mode);
        icon.setColorFilter(Ui.TEXT);
        iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36));
        ip.rightMargin = Ui.dp(activity, 12);
        topRow.addView(iconBox, ip);

        LinearLayout titleCol = Ui.column(activity);
        TextView tvTitle = Ui.text(activity, "CAN Diagnostic Mode", 15, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final SharedPreferences modePrefs = activity.getSharedPreferences("sola_can_mode", Context.MODE_PRIVATE);
        boolean isUds = "uds".equalsIgnoreCase(modePrefs.getString("can_diagnostic_mode", "normal"));

        TextView modeBadge = Ui.pin(activity, isUds ? "EXTENDED UDS" : "PASSIVE NORMAL", isUds ? Ui.WARNING : Ui.POSITIVE);
        topRow.addView(modeBadge);

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_expand_more);
        chevron.setColorFilter(Ui.MUTED);
        chevron.setRotation(isExpanded ? 180f : 0f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
        cp.leftMargin = Ui.dp(activity, 8);
        topRow.addView(chevron, cp);

        card.addView(topRow);

        View div = Ui.divider(activity);
        div.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        card.addView(div);

        LinearLayout contentBox = Ui.column(activity);
        contentBox.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        contentBox.setPadding(0, Ui.dp(activity, 10), 0, 0);

        // 2 Mode Selector Chips
        LinearLayout chipRow = Ui.row(activity);

        TextView chipNormal = new TextView(activity);
        chipNormal.setText("NORMAL (PASSIVE)");
        chipNormal.setTextSize(11);
        chipNormal.setTypeface(Typeface.DEFAULT_BOLD);
        chipNormal.setGravity(Gravity.CENTER);
        chipNormal.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
        chipNormal.setTextColor(!isUds ? Ui.ACCENT_TEXT : Ui.MUTED);
        chipNormal.setBackground(Ui.round(activity, !isUds ? Ui.PRIMARY : Ui.SURFACE_HIGH, 16, 0));

        TextView chipUds = new TextView(activity);
        chipUds.setText("EXTENDED UDS");
        chipUds.setTextSize(11);
        chipUds.setTypeface(Typeface.DEFAULT_BOLD);
        chipUds.setGravity(Gravity.CENTER);
        chipUds.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 8), Ui.dp(activity, 12), Ui.dp(activity, 8));
        chipUds.setTextColor(isUds ? Color.WHITE : Ui.MUTED);
        chipUds.setBackground(Ui.round(activity, isUds ? Ui.WARNING : Ui.SURFACE_HIGH, 16, 0));

        LinearLayout.LayoutParams cNormalLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        chipNormal.setLayoutParams(cNormalLp);

        LinearLayout.LayoutParams cUdsLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        cUdsLp.leftMargin = Ui.dp(activity, 8);
        chipUds.setLayoutParams(cUdsLp);

        chipRow.addView(chipNormal);
        chipRow.addView(chipUds);
        contentBox.addView(chipRow);

        TextView tvExpl = Ui.text(activity, isUds
                ? "Active UDS sessions (0x10 0x03) enabled for actuator sweeps & DTC clear. MoveOS halts AC charging while active."
                : "Passive CAN sniffing active. Charging and riding are completely safe and will never be interrupted.", 11, Ui.MUTED);
        tvExpl.setPadding(0, Ui.dp(activity, 8), 0, 0);
        contentBox.addView(tvExpl);
        card.addView(contentBox);

        topRow.setOnClickListener(v -> {
            boolean expand = contentBox.getVisibility() != View.VISIBLE;
            contentBox.setVisibility(expand ? View.VISIBLE : View.GONE);
            div.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(expand ? 180f : 0f).setDuration(180).start();
            topRow.setPadding(0, 0, 0, expand ? Ui.dp(activity, 10) : 0);
            prefs.edit().putBoolean("expand_can_mode", expand).apply();
        });

        chipNormal.setOnClickListener(v -> {
            modePrefs.edit().putString("can_diagnostic_mode", "normal").apply();
            if (bus != null) bus.setExtendedUdsMode(false);
            chipNormal.setTextColor(Ui.ACCENT_TEXT);
            chipNormal.setBackground(Ui.round(activity, Ui.PRIMARY, 16, 0));
            chipUds.setTextColor(Ui.MUTED);
            chipUds.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 16, 0));
            modeBadge.setText("PASSIVE NORMAL");
            modeBadge.setTextColor(Ui.POSITIVE);
            modeBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.POSITIVE, 0x22), 12, 0));
            tvExpl.setText("Passive CAN sniffing active. Charging and riding are completely safe and will never be interrupted.");
        });

        chipUds.setOnClickListener(v -> {
            modePrefs.edit().putString("can_diagnostic_mode", "uds").apply();
            if (bus != null) bus.setExtendedUdsMode(true);
            chipUds.setTextColor(Color.WHITE);
            chipUds.setBackground(Ui.round(activity, Ui.WARNING, 16, 0));
            chipNormal.setTextColor(Ui.MUTED);
            chipNormal.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 16, 0));
            modeBadge.setText("EXTENDED UDS");
            modeBadge.setTextColor(Ui.WARNING);
            modeBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x22), 12, 0));
            tvExpl.setText("Active UDS sessions (0x10 0x03) enabled for actuator sweeps & DTC clear. MoveOS halts AC charging while active.");
        });

        return card;
    }

    private View createThemeCard() {
        final boolean isExpanded = prefs.getBoolean("expand_theme", false);
        LinearLayout card = Ui.card(activity);

        LinearLayout topRow = Ui.row(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.setPadding(0, 0, 0, isExpanded ? Ui.dp(activity, 10) : 0);

        LinearLayout iconBox = Ui.column(activity);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.drawable.ic_theme);
        icon.setColorFilter(Ui.TEXT);
        iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36));
        ip.rightMargin = Ui.dp(activity, 12);
        topRow.addView(iconBox, ip);

        LinearLayout titleCol = Ui.column(activity);
        TextView tvTitle = Ui.text(activity, "Theme Appearance", 15, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        titleCol.addView(tvTitle);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        String currentMode = prefs.getString("app_theme", "dark");
        TextView themeBadge = Ui.pin(activity, currentMode.toUpperCase(), Ui.PRIMARY);
        topRow.addView(themeBadge);

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_expand_more);
        chevron.setColorFilter(Ui.MUTED);
        chevron.setRotation(isExpanded ? 180f : 0f);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
        cp.leftMargin = Ui.dp(activity, 8);
        topRow.addView(chevron, cp);

        card.addView(topRow);

        View div = Ui.divider(activity);
        div.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        card.addView(div);

        LinearLayout contentBox = Ui.column(activity);
        contentBox.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        contentBox.setPadding(0, Ui.dp(activity, 10), 0, 0);

        // 3 Theme Chips: DARK, LIGHT, SYSTEM
        LinearLayout chipRow = Ui.row(activity);

        String[] modes = {"dark", "light", "system"};
        String[] labels = {"DARK", "LIGHT", "SYSTEM"};

        for (int i = 0; i < modes.length; i++) {
            final String m = modes[i];
            boolean isSel = currentMode.equalsIgnoreCase(m);

            TextView chip = new TextView(activity);
            chip.setText(labels[i]);
            chip.setTextSize(11);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 8), Ui.dp(activity, 16), Ui.dp(activity, 8));
            chip.setTextColor(isSel ? Ui.ACCENT_TEXT : Ui.MUTED);
            chip.setBackground(Ui.round(activity, isSel ? Ui.PRIMARY : Ui.SURFACE_HIGH, 16, 0));

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            if (i > 0) clp.leftMargin = Ui.dp(activity, 8);
            chip.setLayoutParams(clp);

            chip.setOnClickListener(v -> {
                prefs.edit().putString("app_theme", m).apply();
                boolean isSysNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
                Ui.applyTheme(m, isSysNight);
                activity.recreate();
            });

            chipRow.addView(chip);
        }

        contentBox.addView(chipRow);
        card.addView(contentBox);

        topRow.setOnClickListener(v -> {
            boolean expand = contentBox.getVisibility() != View.VISIBLE;
            contentBox.setVisibility(expand ? View.VISIBLE : View.GONE);
            div.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(expand ? 180f : 0f).setDuration(180).start();
            topRow.setPadding(0, 0, 0, expand ? Ui.dp(activity, 10) : 0);
            prefs.edit().putBoolean("expand_theme", expand).apply();
        });

        return card;
    }

    private void addSpace(LinearLayout parent, int dp) {
        View v = new View(activity);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, dp)));
        parent.addView(v);
    }

    @Override
    public View view() {
        return page;
    }

    @Override
    public void onShow() {
        if (bus != null) bus.attach(this);
        logTab.onShow();
        modeTab.onShow();
        adbTab.onShow();
    }

    @Override
    public void onHide() {
        if (bus != null) bus.detach(this);
        logTab.onHide();
        modeTab.onHide();
        adbTab.onHide();
    }

    @Override
    public void onTick(Live.Snapshot s) {
        // Handled by sub-tabs
    }
}
