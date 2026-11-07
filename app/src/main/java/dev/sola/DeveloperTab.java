package dev.sola;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
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

    private static final String PREF_NAME = "sola_dev_expand";
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

        // 1. Vehicle Operation Mode (Closed by default, remembers user preference)
        View modeCard = createExpandableSection(
                KEY_MODE,
                R.drawable.ic_build,
                "Vehicle Operation Mode",
                "Switch Customer, Service, or Factory mode",
                "MODE",
                Ui.POSITIVE,
                modeTab.view()
        );
        body.addView(modeCard);

        addSpace(body, 12);

        // 2. Root ADB Terminal (Closed by default, remembers user preference)
        View adbCard = createExpandableSection(
                KEY_ADB,
                R.drawable.ic_code,
                "Root ADB Terminal",
                "Direct root shell over port 50005 (getprop, sqlite3, su)",
                "ROOT",
                Ui.WARNING,
                adbTab.view()
        );
        body.addView(adbCard);

        addSpace(body, 12);

        // 3. MoveOS System Settings (Closed by default, remembers user preference)
        View setCard = createExpandableSection(
                KEY_SETTINGS,
                R.drawable.ic_settings,
                "MoveOS System Settings",
                "Bluetooth, Wi-Fi, Display, and Android settings launcher",
                "SYSTEM",
                Ui.MUTED,
                settingsTab.view()
        );
        body.addView(setCard);

        addSpace(body, 12);

        // 4. CAN Session Recorder (Closed by default, remembers user preference)
        View recCard = createExpandableSection(
                KEY_RECORDER,
                R.drawable.ic_code,
                "CAN Session Recorder",
                "Raw frame log, candump recorder & CSV export",
                "LOGGER",
                Ui.PRIMARY,
                logTab.view()
        );
        body.addView(recCard);

        addSpace(body, 16);
    }

    private View createExpandableSection(String prefKey, int iconRes, String title, String subtitle,
                                         String pinText, int pinColor, View content) {
        // Read persisted preference; default to closed (false)
        boolean isExpanded = prefs.getBoolean(prefKey, false);

        LinearLayout card = Ui.card(activity);

        // Header Row (Clickable)
        LinearLayout header = Ui.row(activity);
        header.setPadding(0, 0, 0, isExpanded ? Ui.dp(activity, 12) : 0);

        if (iconRes != 0) {
            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconRes);
            icon.setColorFilter(Ui.PRIMARY);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
            ip.rightMargin = Ui.dp(activity, 10);
            header.addView(icon, ip);
        }

        LinearLayout titleCol = Ui.column(activity);
        TextView tvTitle = Ui.text(activity, title, 16, Ui.TEXT);
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
        }

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_expand_more);
        chevron.setColorFilter(Ui.MUTED);
        chevron.setRotation(isExpanded ? 180f : 0f);
        LinearLayout.LayoutParams chp = new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22));
        chp.leftMargin = Ui.dp(activity, 8);
        header.addView(chevron, chp);

        card.addView(header);

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
            chevron.animate().rotation(willExpand ? 180f : 0f).setDuration(200).start();
            header.setPadding(0, 0, 0, willExpand ? Ui.dp(activity, 12) : 0);
            prefs.edit().putBoolean(prefKey, willExpand).apply();
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
