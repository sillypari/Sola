package dev.sola;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.sola.core.Faults;
import dev.sola.core.Live;

/**
 * Axiom-styled MoveOS Faults & DTC Diagnostic Monitor.
 * Organizes all 201 official factory diagnostic trouble codes (DTCs) based on
 * ECU subsystems (BMS, MCU, VCU) and functional categories (LIGHTING, THERMAL,
 * INVERTER, SENSORS, CRITICAL), exactly aligned with the factory diagnostic app.
 * Features 1-tap UDS DTC clearance, live CAN active/latched monitoring,
 * expandable technician repair guides, and zero emojis.
 */
public final class FaultsTab implements Tab, Bus.Listener {

    private static final String[] CATEGORIES = {
            "ALL", "BMS", "MCU", "VCU", "LIGHTING", "THERMAL", "INVERTER", "SENSORS", "CRITICAL"
    };

    private final Activity activity;
    private final Conn conn;
    private final Bus bus;
    private final ScrollView page;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Exceptional State Banner (Locked/Asleep/Stale)
    private final LinearLayout banner;
    private final TextView bannerBody;
    private final View bannerSpacer;

    // Status Hero Card Views
    private final LinearLayout headerIconBox;
    private final ImageView headerIcon;
    private final TextView tvHeaderTitle;
    private final TextView tvHeaderSubtitle;
    private final LinearLayout pinsRow;
    private Button btnTopClear;

    // Filter Chips
    private final TextView[] filterChips = new TextView[CATEGORIES.length];
    private String selectedCategory = "ALL";

    // Containers
    private final LinearLayout statusContainer;
    private final LinearLayout registryContainer;
    private final TextView tvRegistryCount;

    private boolean visible;
    private String currentQuery = "";
    private List<DtcCatalog.DtcItem> allDtcItems;

    public FaultsTab(Activity activity, Conn conn, Bus bus) {
        this.activity = activity;
        this.conn = conn;
        this.bus = bus;

        this.page = Ui.page(activity);
        LinearLayout body = Ui.body(page);

        // Preload DTC catalog
        allDtcItems = DtcCatalog.getItems(activity);

        // 0. Exceptional State Banner
        banner = Ui.column(activity);
        banner.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        banner.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x16), 14, Ui.alpha(Ui.WARNING, 0x55)));
        banner.setVisibility(View.GONE);

        LinearLayout bannerHeader = Ui.row(activity);
        ImageView warnIcon = new ImageView(activity);
        warnIcon.setImageResource(R.drawable.ic_warning);
        warnIcon.setColorFilter(Ui.WARNING);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18));
        wp.rightMargin = Ui.dp(activity, 8);
        bannerHeader.addView(warnIcon, wp);

        TextView bannerTitle = Ui.text(activity, "SCOOTER MIGHT BE LOCKED OR ASLEEP", 12, Ui.WARNING);
        bannerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        bannerTitle.setLetterSpacing(0.04f);
        bannerHeader.addView(bannerTitle);
        banner.addView(bannerHeader);

        bannerBody = Ui.text(activity, "", 12, Ui.TEXT);
        bannerBody.setPadding(Ui.dp(activity, 26), Ui.dp(activity, 4), 0, 0);
        banner.addView(bannerBody);

        body.addView(banner);

        bannerSpacer = new View(activity);
        bannerSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 10)));
        bannerSpacer.setVisibility(View.GONE);
        body.addView(bannerSpacer);

        // 1. Systems Operational / Faults Status Hero Card
        LinearLayout heroCard = Ui.card(activity);
        LinearLayout topRow = Ui.row(activity);

        headerIconBox = Ui.column(activity);
        headerIconBox.setGravity(Gravity.CENTER);
        int pad = Ui.dp(activity, 10);
        headerIconBox.setPadding(pad, pad, pad, pad);
        headerIconBox.setBackground(Ui.round(activity, Ui.alpha(Ui.POSITIVE, 0x1E), 16, 0));

        headerIcon = new ImageView(activity);
        headerIcon.setImageResource(R.drawable.ic_check);
        headerIcon.setColorFilter(Ui.POSITIVE);
        headerIconBox.addView(headerIcon, new LinearLayout.LayoutParams(Ui.dp(activity, 26), Ui.dp(activity, 26)));
        topRow.addView(headerIconBox);

        LinearLayout titleCol = Ui.column(activity);
        titleCol.setPadding(Ui.dp(activity, 14), 0, 0, 0);

        tvHeaderTitle = Ui.text(activity, "Systems Operational", 16, Ui.TEXT);
        tvHeaderTitle.setTypeface(Typeface.DEFAULT_BOLD);

        tvHeaderSubtitle = Ui.text(activity, "No Faults Detected", 12.5f, Ui.POSITIVE);
        tvHeaderSubtitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvHeaderSubtitle.setPadding(0, Ui.dp(activity, 2), 0, 0);

        titleCol.addView(tvHeaderTitle);
        titleCol.addView(tvHeaderSubtitle);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnTopClear = Ui.button(activity, "CLEAR DTCS", v -> executeUdsClearAll());
        btnTopClear.setTextSize(11);
        btnTopClear.setTextColor(Ui.PRIMARY);
        btnTopClear.setBackground(Ui.round(activity, Ui.alpha(Ui.PRIMARY, 0x22), 10, 0));
        btnTopClear.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 6), Ui.dp(activity, 12), Ui.dp(activity, 6));
        btnTopClear.setMinHeight(Ui.dp(activity, 34));
        btnTopClear.setMinimumHeight(Ui.dp(activity, 34));
        topRow.addView(btnTopClear);

        heroCard.addView(topRow);

        // Status Pins Row (0 ACTIVE - NORMAL - 201 MONITORED)
        pinsRow = Ui.row(activity);
        pinsRow.setPadding(0, Ui.dp(activity, 12), 0, 0);
        heroCard.addView(pinsRow);

        body.addView(heroCard);

        addSpace(body, 12);

        // 2. Active / Latched Diagnostic State Container
        statusContainer = Ui.column(activity);
        body.addView(statusContainer);

        addSpace(body, 14);

        // 3. DTC Registry Section Header
        LinearLayout regHeader = Ui.row(activity);
        regHeader.addView(Ui.heading(activity, "Diagnostic Trouble Codes"),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        tvRegistryCount = Ui.text(activity, "201 total", 11, Ui.MUTED);
        regHeader.addView(tvRegistryCount);
        body.addView(regHeader);

        // 4. Material 3 Search Bar
        LinearLayout searchContainer = Ui.searchBar(activity, "Search DTCs, components, symptoms...", query -> {
            currentQuery = query != null ? query.trim().toLowerCase(Locale.ROOT) : "";
            renderRegistry();
        });
        body.addView(searchContainer);

        addSpace(body, 10);

        // 5. Category Filter Chips (Horizontal Scroll)
        HorizontalScrollView hScroll = new HorizontalScrollView(activity);
        hScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipsRow = Ui.row(activity);
        chipsRow.setPadding(0, 0, 0, Ui.dp(activity, 4));

        for (int i = 0; i < CATEGORIES.length; i++) {
            final String cat = CATEGORIES[i];
            TextView chip = new TextView(activity);
            chip.setText(cat);
            chip.setTextSize(11);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false);
            chip.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);

            boolean isSelected = cat.equals(selectedCategory);
            chip.setTextColor(isSelected ? Color.BLACK : Ui.MUTED);
            chip.setBackground(Ui.round(activity, isSelected ? Ui.PRIMARY : Ui.SURFACE_HIGH, 12, 0));

            chip.setOnClickListener(v -> {
                selectedCategory = cat;
                updateFilterChipsUI();
                renderRegistry();
                page.post(() -> page.smoothScrollTo(0, 0));
            });

            filterChips[i] = chip;

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 32));
            if (i > 0) clp.leftMargin = Ui.dp(activity, 8);
            chipsRow.addView(chip, clp);
        }
        hScroll.addView(chipsRow);
        body.addView(hScroll);

        addSpace(body, 10);

        // 6. Registry Item List
        registryContainer = Ui.column(activity);
        body.addView(registryContainer);

        // Initial render
        updateHeader(new ArrayList<>(), new ArrayList<>());
        renderStatusSection(new ArrayList<>(), new ArrayList<>());
        renderRegistry();
    }

    private void updateFilterChipsUI() {
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean isSelected = CATEGORIES[i].equals(selectedCategory);
            filterChips[i].setTextColor(isSelected ? Color.BLACK : Ui.MUTED);
            filterChips[i].setBackground(Ui.round(activity, isSelected ? Ui.PRIMARY : Ui.SURFACE_HIGH, 12, 0));
        }
    }

    private void addSpace(LinearLayout parent, int dp) {
        View spacer = new View(activity);
        parent.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, dp)));
    }

    @Override
    public View view() {
        return page;
    }

    @Override
    public void onShow() {
        visible = true;
        if (bus != null) {
            bus.attach(this);
            onTick(bus.snapshot());
        }
    }

    @Override
    public void onHide() {
        visible = false;
        if (bus != null) {
            bus.detach(this);
        }
    }

    @Override
    public void onTick(Live.Snapshot snapshot) {
        if (!visible) return;
        ui.post(() -> {
            long now = System.currentTimeMillis();
            boolean connected = conn != null && conn.ready();
            boolean stale = snapshot != null && snapshot.vehicleStale(now, 6000L);
            boolean noFramesYet = snapshot != null && snapshot.frames == 0 && connected;

            if (noFramesYet) {
                banner.setVisibility(View.VISIBLE);
                bannerSpacer.setVisibility(View.VISIBLE);
                bannerBody.setText("Connected to scooter daemon, but no CAN frames yet. Turn on the dash or tap the power button.");
            } else if (stale && connected) {
                banner.setVisibility(View.VISIBLE);
                bannerSpacer.setVisibility(View.VISIBLE);
                bannerBody.setText("No vehicle CAN data for more than 6 seconds (telematics trickle only). The scooter may be asleep or locked - turn the dash on.");
            } else {
                banner.setVisibility(View.GONE);
                bannerSpacer.setVisibility(View.GONE);
            }

            Live live = bus != null ? bus.live() : null;
            List<Faults.Fault> active = snapshot == null ? new ArrayList<>() : snapshot.activeFaults;
            List<Faults.Fault> latched = live != null ? live.latchedFaults() : new ArrayList<>();

            updateHeader(active, latched);
            renderStatusSection(active, latchedExcept(latched, active));
        });
    }

    private List<Faults.Fault> latchedExcept(List<Faults.Fault> latched, List<Faults.Fault> active) {
        if (latched == null || latched.isEmpty()) return new ArrayList<>();
        if (active == null || active.isEmpty()) return latched;
        List<Faults.Fault> filtered = new ArrayList<>();
        for (Faults.Fault f : latched) {
            boolean inActive = false;
            for (Faults.Fault a : active) {
                if (a.id.equals(f.id)) {
                    inActive = true;
                    break;
                }
            }
            if (!inActive) filtered.add(f);
        }
        return filtered;
    }

    private void updateHeader(List<Faults.Fault> active, List<Faults.Fault> latched) {
        pinsRow.removeAllViews();
        int activeCount = active != null ? active.size() : 0;
        int latchedCount = latched != null ? latched.size() : 0;

        if (activeCount == 0 && latchedCount == 0) {
            headerIconBox.setBackground(Ui.round(activity, Ui.alpha(Ui.POSITIVE, 0x1E), 16, 0));
            headerIcon.setImageResource(R.drawable.ic_check);
            headerIcon.setColorFilter(Ui.POSITIVE);

            tvHeaderTitle.setText("Systems Operational");
            tvHeaderTitle.setTextColor(Ui.TEXT);
            tvHeaderSubtitle.setText("No Faults Detected");
            tvHeaderSubtitle.setTextColor(Ui.POSITIVE);

            TextView pinActive = Ui.pin(activity, "0 ACTIVE", Ui.POSITIVE);
            pinsRow.addView(pinActive);

            TextView pinStatus = Ui.pin(activity, "NORMAL", Ui.PRIMARY);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = Ui.dp(activity, 8);
            pinsRow.addView(pinStatus, lp);

            TextView pinTotal = Ui.pin(activity, "ALL CLEAR", Ui.MUTED);
            LinearLayout.LayoutParams lpTotal = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpTotal.leftMargin = Ui.dp(activity, 8);
            pinsRow.addView(pinTotal, lpTotal);

            if (btnTopClear != null) {
                btnTopClear.setText("CLEAR DTCS");
                btnTopClear.setTextColor(Ui.PRIMARY);
                btnTopClear.setBackground(Ui.round(activity, Ui.alpha(Ui.PRIMARY, 0x22), 10, 0));
            }
        } else {
            headerIconBox.setBackground(Ui.round(activity, Ui.alpha(Ui.ERROR, 0x1E), 16, 0));
            headerIcon.setImageResource(R.drawable.ic_warning);
            headerIcon.setColorFilter(Ui.ERROR);

            tvHeaderTitle.setText("Trouble Code Detected");
            tvHeaderTitle.setTextColor(Ui.TEXT);
            tvHeaderSubtitle.setText(activeCount + " Active - " + latchedCount + " Latched");
            tvHeaderSubtitle.setTextColor(Ui.ERROR);

            TextView pActive = Ui.pin(activity, activeCount + " ACTIVE", Ui.ERROR);
            pinsRow.addView(pActive);

            if (latchedCount > 0) {
                TextView pLatched = Ui.pin(activity, latchedCount + " LATCHED", Ui.WARNING);
                LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lpL.leftMargin = Ui.dp(activity, 8);
                pinsRow.addView(pLatched, lpL);
            }

            TextView pAttention = Ui.pin(activity, "ATTENTION", Ui.ERROR);
            LinearLayout.LayoutParams lpAtt = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpAtt.leftMargin = Ui.dp(activity, 8);
            pinsRow.addView(pAttention, lpAtt);

            if (btnTopClear != null) {
                btnTopClear.setText("CLEAR " + (activeCount + latchedCount) + " DTCS");
                btnTopClear.setTextColor(Ui.ERROR);
                btnTopClear.setBackground(Ui.round(activity, Ui.alpha(Ui.ERROR, 0x22), 10, 0));
            }
        }
    }

    private void renderStatusSection(List<Faults.Fault> active, List<Faults.Fault> latched) {
        statusContainer.removeAllViews();

        if ((active == null || active.isEmpty()) && (latched == null || latched.isEmpty())) {
            statusContainer.setVisibility(View.GONE);
            return;
        }
        statusContainer.setVisibility(View.VISIBLE);

        // Active Faults List
        if (active != null && !active.isEmpty()) {
            statusContainer.addView(Ui.heading(activity, "Active CAN Faults (" + active.size() + ")"));
            for (Faults.Fault f : active) {
                statusContainer.addView(buildLiveFaultCard(f, "ACTIVE", Ui.ERROR));
                addSpace(statusContainer, 8);
            }
        }

        // Latched Faults List
        if (latched != null && !latched.isEmpty()) {
            LinearLayout latchedHeader = Ui.row(activity);
            latchedHeader.addView(Ui.heading(activity, "Latched History (" + latched.size() + ")"),
                    new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView btnClear = Ui.text(activity, "CLEAR HISTORY", 10.5f, Ui.PRIMARY);
            btnClear.setTypeface(Typeface.DEFAULT_BOLD);
            btnClear.setLetterSpacing(0.04f);
            btnClear.setIncludeFontPadding(false);
            btnClear.setGravity(Gravity.CENTER);
            btnClear.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 4), Ui.dp(activity, 10), Ui.dp(activity, 4));
            btnClear.setBackground(Ui.round(activity, Ui.alpha(Ui.PRIMARY, 0x22), 10, 0));
            btnClear.setOnClickListener(v -> {
                if (bus != null) bus.clearLatched();
                Toast.makeText(activity, "Latched history cleared", Toast.LENGTH_SHORT).show();
                onTick(bus != null && bus.live() != null ? bus.live().snapshot() : null);
            });
            latchedHeader.addView(btnClear);
            statusContainer.addView(latchedHeader);

            for (Faults.Fault f : latched) {
                statusContainer.addView(buildLiveFaultCard(f, "LATCHED", Ui.WARNING));
                addSpace(statusContainer, 8);
            }
        }
    }

    private View buildLiveFaultCard(Faults.Fault f, String stateLabel, int stateColor) {
        LinearLayout card = Ui.card(activity, 14);

        LinearLayout chipRow = Ui.row(activity);
        chipRow.addView(Ui.componentPin(activity, f.component != null ? f.component : "SYSTEM"));

        TextView sevPin = Ui.severityPin(activity, f.severity != null ? f.severity : "INFO");
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp1.leftMargin = Ui.dp(activity, 6);
        chipRow.addView(sevPin, lp1);

        TextView stPin = Ui.pin(activity, stateLabel, stateColor);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp2.leftMargin = Ui.dp(activity, 6);
        chipRow.addView(stPin, lp2);
        card.addView(chipRow);

        TextView tvTitle = Ui.text(activity, f.title != null ? f.title : "Condition", 14.5f, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, Ui.dp(activity, 8), 0, 0);
        card.addView(tvTitle);

        if (f.description != null && !f.description.isEmpty()) {
            TextView tvDesc = Ui.text(activity, f.description, 12, Ui.MUTED);
            tvDesc.setPadding(0, Ui.dp(activity, 4), 0, 0);
            card.addView(tvDesc);
        }

        TextView tvId = Ui.text(activity, "Signal: " + (f.id != null ? f.id : "--"), 10.5f, Ui.DIM);
        tvId.setTypeface(Typeface.MONOSPACE);
        tvId.setPadding(0, Ui.dp(activity, 6), 0, 0);
        card.addView(tvId);

        return card;
    }

    private void renderRegistry() {
        registryContainer.removeAllViews();
        if (allDtcItems == null || allDtcItems.isEmpty()) {
            TextView tv = Ui.note(activity, "Loading MoveOS DTC definitions...");
            registryContainer.addView(tv);
            return;
        }

        int matched = 0;
        int maxDisplay = 30;

        for (DtcCatalog.DtcItem item : allDtcItems) {
            if (!categoryMatches(item, selectedCategory)) continue;
            if (!currentQuery.isEmpty() && !item.matches(currentQuery)) continue;

            matched++;
            if (matched <= maxDisplay) {
                registryContainer.addView(buildDtcItemCard(item));
                addSpace(registryContainer, 8);
            }
        }

        tvRegistryCount.setText(String.format(Locale.US, "%d matching", matched));

        if (matched > maxDisplay) {
            TextView more = Ui.note(activity, String.format(Locale.US, "+ %d more DTCs matching %s (use search bar above to narrow down)",
                    matched - maxDisplay, selectedCategory));
            more.setPadding(0, Ui.dp(activity, 8), 0, 0);
            registryContainer.addView(more);
        } else if (matched == 0) {
            TextView none = Ui.note(activity, "No DTC matching \"" + currentQuery + "\" under category " + selectedCategory + ".");
            none.setPadding(0, Ui.dp(activity, 14), 0, 0);
            registryContainer.addView(none);
        }
    }

    private boolean categoryMatches(DtcCatalog.DtcItem item, String cat) {
        if ("ALL".equalsIgnoreCase(cat)) return true;
        if ("CRITICAL".equalsIgnoreCase(cat)) return "CRITICAL".equalsIgnoreCase(item.severity);
        if ("BMS".equalsIgnoreCase(cat)) return "BMS".equalsIgnoreCase(item.ecu);
        if ("MCU".equalsIgnoreCase(cat)) return "MCU".equalsIgnoreCase(item.ecu);
        if ("VCU".equalsIgnoreCase(cat)) return "VCU".equalsIgnoreCase(item.ecu);
        return item.category.equalsIgnoreCase(cat);
    }

    private View buildDtcItemCard(DtcCatalog.DtcItem item) {
        LinearLayout card = Ui.column(activity);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackground(Ui.round(activity, Ui.SURFACE, 14, 0));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = Ui.dp(activity, 8);
        card.setLayoutParams(clp);

        // Header Row (Clickable anywhere to toggle expand/collapse)
        LinearLayout topRow = Ui.row(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = Ui.column(activity);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Sub-row: DTC Hex Code Badge + ECU Pill + Category Pill
        LinearLayout metaRow = Ui.row(activity);
        metaRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView codeBadge = Ui.text(activity, item.code, 12f, Ui.PRIMARY);
        codeBadge.setTypeface(Typeface.MONOSPACE);
        codeBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.PRIMARY, 0x1E), 6, 0));
        codeBadge.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2));
        metaRow.addView(codeBadge);

        TextView ecuPill = Ui.text(activity, item.ecu, 10f, Ui.TEXT);
        ecuPill.setTypeface(Typeface.DEFAULT_BOLD);
        ecuPill.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 6, 0));
        ecuPill.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2));
        LinearLayout.LayoutParams epLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        epLp.leftMargin = Ui.dp(activity, 6);
        metaRow.addView(ecuPill, epLp);

        TextView catPill = Ui.text(activity, item.category, 9.5f, Ui.MUTED);
        catPill.setTypeface(Typeface.DEFAULT_BOLD);
        catPill.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 6, 0));
        catPill.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2));
        LinearLayout.LayoutParams cpLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cpLp.leftMargin = Ui.dp(activity, 6);
        metaRow.addView(catPill, cpLp);

        textCol.addView(metaRow);

        // DTC Title / Description
        TextView title = Ui.text(activity, item.title, 13.5f, Ui.TEXT);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, Ui.dp(activity, 4), 0, 0);
        textCol.addView(title);

        topRow.addView(textCol);

        // Severity Pin (right side)
        TextView sPin = Ui.severityPin(activity, item.severity);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spLp.leftMargin = Ui.dp(activity, 8);
        topRow.addView(sPin, spLp);

        // Chevron icon
        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_expand_more);
        chevron.setColorFilter(Ui.MUTED);
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
        chLp.leftMargin = Ui.dp(activity, 6);
        topRow.addView(chevron, chLp);

        card.addView(topRow);

        // Expandable Detail Container (Hidden by default)
        LinearLayout details = Ui.column(activity);
        details.setVisibility(View.GONE);
        details.setPadding(0, Ui.dp(activity, 10), 0, 0);

        View div = Ui.divider(activity);
        details.addView(div);

        // 1. Technical Explanation
        TextView hExp = Ui.heading(activity, "Context");
        hExp.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 2));
        details.addView(hExp);

        TextView tvExp = Ui.text(activity, item.explanation, 11.5f, Ui.TEXT);
        tvExp.setLineSpacing(0, 1.25f);
        details.addView(tvExp);

        // 2. Possible Root Cause
        TextView hCause = Ui.heading(activity, "Root Cause");
        hCause.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 2));
        details.addView(hCause);

        TextView tvCause = Ui.text(activity, item.possibleCause, 11.5f, Ui.WARNING);
        tvCause.setLineSpacing(0, 1.25f);
        details.addView(tvCause);

        // 3. UDS Service 0x14 Clear Command
        TextView hCmd = Ui.heading(activity, "UDS Clear");
        hCmd.setPadding(0, Ui.dp(activity, 10), 0, Ui.dp(activity, 2));
        details.addView(hCmd);

        LinearLayout cmdBox = Ui.row(activity);
        cmdBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 8, 0));
        cmdBox.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 8), Ui.dp(activity, 10), Ui.dp(activity, 8));

        TextView tvCmd = Ui.text(activity, item.udsClearCmd, 10.5f, Ui.PRIMARY);
        tvCmd.setTypeface(Typeface.MONOSPACE);
        cmdBox.addView(tvCmd, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnClearSingle = Ui.text(activity, "CLEAR", 10.5f, Ui.TEXT);
        btnClearSingle.setTypeface(Typeface.DEFAULT_BOLD);
        btnClearSingle.setBackground(Ui.round(activity, Ui.alpha(Ui.PRIMARY, 0x22), 6, 0));
        btnClearSingle.setPadding(Ui.dp(activity, 8), Ui.dp(activity, 4), Ui.dp(activity, 8), Ui.dp(activity, 4));
        btnClearSingle.setOnClickListener(v -> executeUdsClear(item.udsClearCmd, item.ecu));
        cmdBox.addView(btnClearSingle);

        details.addView(cmdBox);

        card.addView(details);

        View.OnClickListener toggle = v -> {
            boolean expand = details.getVisibility() != View.VISIBLE;
            details.setVisibility(expand ? View.VISIBLE : View.GONE);
            chevron.animate().rotation(expand ? 180f : 0f).setDuration(180).start();
        };
        card.setOnClickListener(toggle);
        topRow.setOnClickListener(toggle);

        return card;
    }

    private void executeUdsClear(String command, String ecu) {
        if (conn == null || !conn.ready()) {
            Toast.makeText(activity, "Connect to scooter first to clear " + ecu + " DTCs", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(activity, "Sending UDS 0x14 Clear to " + ecu + "...", Toast.LENGTH_SHORT).show();
        conn.run(rpc -> rpc.shell(command, 4000), (value, error) -> {
            if (error != null) {
                Toast.makeText(activity, "Failed to clear " + ecu + ": " + error.getMessage(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, ecu + " Diagnostic Information Cleared", Toast.LENGTH_SHORT).show();
                if (bus != null) bus.clearLatched();
                onTick(bus != null && bus.live() != null ? bus.live().snapshot() : null);
            }
        });
    }

    private void executeUdsClearAll() {
        if (conn == null || !conn.ready()) {
            Toast.makeText(activity, "Connect to scooter first to clear vehicle DTCs", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(activity, "Clearing DTCs across VCU, BMS, and MCU...", Toast.LENGTH_SHORT).show();
        String cmd = "cansend vcan0 786#0414FFFFFF000000; cansend vcan0 78E#0414FFFFFF000000; cansend vcan0 7A6#0414FFFFFF000000";

        conn.run(rpc -> rpc.shell(cmd, 6000), (value, error) -> {
            if (error != null) {
                Toast.makeText(activity, "Clear DTC failed: " + error.getMessage(), Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(activity, "All Vehicle ECUs Cleared Successfully", Toast.LENGTH_SHORT).show();
                if (bus != null) bus.clearLatched();
                onTick(bus != null && bus.live() != null ? bus.live().snapshot() : null);
            }
        });
    }
}
