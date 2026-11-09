package dev.sola;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

import dev.sola.core.Live;
import dev.sola.core.VehicleInfo;

/**
 * Axiom-styled Hero Overview Tab for Sola.
 * Features 20dp Material 3 cards, 12dp sub-cards, EV moped hero box,
 * circular battery indicator, and system component health checks. Zero emojis.
 */
public final class VehicleTab implements Tab, Bus.Listener {

    private final Activity activity;
    private final Conn conn;
    private final Bus bus;
    private final ScrollView page;
    private final Handler ui = new Handler(Looper.getMainLooper());

    // Exceptional State Banner
    private final LinearLayout banner;
    private final TextView bannerBody;
    private final View bannerSpacer;

    // Scooter Hero Card
    private final TextView heroModel;
    private final TextView tvVin;
    private final TextView tvOdo;
    private final TextView badgeStand;
    private final TextView badgeLock;

    // Battery Hero Card
    private final TextView tvSocBig;
    private final TextView tvSocStatus;
    private final ProgressBar pbBattery;
    private final TextView tvVoltage;
    private final TextView tvCurrent;
    private final TextView tvPower;
    private final TextView tvRange;

    // Mode Range Breakdown Card
    private TextView tvRangeEco;
    private TextView tvRangeNormal;
    private TextView tvRangeSport;
    private TextView tvRangeHyper;

    // Additional Battery Metrics
    private TextView tvUsableEnergy;
    private TextView tvUsableCap;

    // Vehicle Dynamics Card
    private final TextView tvSpeed;
    private final TextView tvRpm;
    private final TextView tvTorque;
    private final TextView tvMode;

    // System Health Diagnostics Card
    private static class HealthRow {
        final TextView tvValue;

        HealthRow(TextView tvValue) {
            this.tvValue = tvValue;
        }
    }

    private final HealthRow rowBms;
    private final HealthRow rowDelta;
    private final HealthRow rowMcu;
    private final HealthRow rowMotor;
    private final HealthRow rowDcdc;
    private final HealthRow rowContactor;
    private final HealthRow rowEscl;
    private final HealthRow rowFaults;

    private VehicleInfo info;
    private boolean loading;

    public VehicleTab(Activity activity, Conn conn, Bus bus) {
        this.activity = activity;
        this.conn = conn;
        this.bus = bus;

        page = Ui.page(activity);
        LinearLayout body = Ui.body(page);

        // Exceptional State Banner (Hidden by default; appears if locked/asleep/stale)
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
        bannerSpacer.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 12)));
        bannerSpacer.setVisibility(View.GONE);
        body.addView(bannerSpacer);

        // 1. Scooter Hero Card (Axiom Style)
        LinearLayout heroCard = Ui.card(activity);

        // Header Row: Scooter Icon + Brand/Model on Left, Live Badges on Right
        LinearLayout topRow = Ui.row(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        // 36dp Squircle Icon Box with Scooter Vector
        LinearLayout iconBox = Ui.column(activity);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setPadding(0, 0, 0, 0);
        iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));
        ImageView mopedIcon = new ImageView(activity);
        mopedIcon.setImageResource(R.drawable.ic_scooter);
        mopedIcon.setColorFilter(Ui.TEXT);
        iconBox.addView(mopedIcon, new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22)));

        LinearLayout titleCol = Ui.column(activity);
        titleCol.setPadding(Ui.dp(activity, 12), 0, 0, 0);

        TextView brand = Ui.text(activity, "OLA ELECTRIC", 9.5f, Ui.MUTED);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setLetterSpacing(0.08f);

        heroModel = Ui.text(activity, "S1", 18, Ui.TEXT);
        heroModel.setTypeface(Typeface.DEFAULT_BOLD);

        titleCol.addView(brand);
        titleCol.addView(heroModel);

        topRow.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36)));
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        // Live Badges Aligned on Top-Right
        LinearLayout badgeRow = Ui.row(activity);
        badgeLock = Ui.badge(activity, "LOCKED", Ui.PRIMARY);
        badgeStand = Ui.badge(activity, "STAND UP", Ui.POSITIVE);
        badgeRow.addView(badgeLock);
        View badgeSpace = new View(activity);
        badgeSpace.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 6), 1));
        badgeRow.addView(badgeSpace);
        badgeRow.addView(badgeStand);
        topRow.addView(badgeRow);

        heroCard.addView(topRow);

        // Sub-Cards Row: VIN & Odometer (Clean 2-column stats with identical matching height)
        LinearLayout idGrid = Ui.row(activity);
        idGrid.setPadding(0, Ui.dp(activity, 14), 0, 0);

        // Dedicated VIN Card with Small Copy Button
        LinearLayout vinCard = Ui.column(activity);
        vinCard.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
        vinCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 12, 0));
        vinCard.setMinimumHeight(Ui.dp(activity, 62));

        LinearLayout vinHeader = Ui.row(activity);
        vinHeader.setGravity(Gravity.CENTER_VERTICAL);
        vinHeader.setMinimumHeight(Ui.dp(activity, 22));

        TextView lblVin = Ui.text(activity, "VIN", 9, Ui.MUTED);
        lblVin.setTypeface(Typeface.DEFAULT_BOLD);
        lblVin.setLetterSpacing(0.06f);
        vinHeader.addView(lblVin, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView btnCopyVin = new ImageView(activity);
        btnCopyVin.setImageResource(R.drawable.ic_copy);
        btnCopyVin.setColorFilter(Ui.TEXT);
        btnCopyVin.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        btnCopyVin.setPadding(Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4), Ui.dp(activity, 4));
        btnCopyVin.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 6, 0));
        btnCopyVin.setOnClickListener(v -> copyVinToClipboard());
        vinHeader.addView(btnCopyVin, new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 22)));

        vinCard.addView(vinHeader);

        tvVin = Ui.text(activity, "Reading...", 12.5f, Ui.TEXT);
        tvVin.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        tvVin.setPadding(0, Ui.dp(activity, 4), 0, 0);
        tvVin.setTextIsSelectable(true);
        tvVin.setSingleLine(true);
        tvVin.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        vinCard.addView(tvVin);

        vinCard.setOnClickListener(v -> copyVinToClipboard());

        // Dedicated Odometer Card with matching height and header alignment
        LinearLayout odoCard = Ui.column(activity);
        odoCard.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
        odoCard.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 12, 0));
        odoCard.setMinimumHeight(Ui.dp(activity, 62));

        LinearLayout odoHeader = Ui.row(activity);
        odoHeader.setGravity(Gravity.CENTER_VERTICAL);
        odoHeader.setMinimumHeight(Ui.dp(activity, 22));

        TextView lblOdo = Ui.text(activity, "ODOMETER", 9, Ui.MUTED);
        lblOdo.setTypeface(Typeface.DEFAULT_BOLD);
        lblOdo.setLetterSpacing(0.06f);
        odoHeader.addView(lblOdo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvOdoUnit = Ui.pin(activity, "KM", Ui.MUTED);
        tvOdoUnit.setTextSize(9);
        tvOdoUnit.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2));
        odoHeader.addView(tvOdoUnit);

        odoCard.addView(odoHeader);

        tvOdo = Ui.text(activity, "-- km", 13.5f, Ui.TEXT);
        tvOdo.setTypeface(Typeface.DEFAULT_BOLD);
        tvOdo.setPadding(0, Ui.dp(activity, 4), 0, 0);
        tvOdo.setSingleLine(true);
        odoCard.addView(tvOdo);

        idGrid.addView(vinCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addGridSpace(idGrid);
        idGrid.addView(odoCard, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heroCard.addView(idGrid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        body.addView(heroCard);

        // Divider Space
        addSpace(body, 12);

        // 2. Battery Status Card (BatterySocSubCard)
        LinearLayout batCard = Ui.card(activity);
        batCard.addView(Ui.cardHeader(activity, R.drawable.ic_bms, "Battery Status"));

        // Battery Hero Header Row
        LinearLayout batHero = Ui.column(activity);
        batHero.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        batHero.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 14, 0));

        TextView lblSoc = Ui.text(activity, "STATE OF CHARGE (SOC)", 9, Ui.MUTED);
        lblSoc.setTypeface(Typeface.DEFAULT_BOLD);
        lblSoc.setLetterSpacing(0.06f);

        LinearLayout socValueRow = Ui.row(activity);
        socValueRow.setPadding(0, Ui.dp(activity, 4), 0, 0);

        tvSocBig = Ui.text(activity, "--% Charged", 22, Ui.TEXT);
        tvSocBig.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams socLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tvSocBig.setLayoutParams(socLp);

        tvSocStatus = Ui.badge(activity, "100% SOH", Ui.POSITIVE);

        socValueRow.addView(tvSocBig);
        socValueRow.addView(tvSocStatus);

        pbBattery = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        pbBattery.setMax(100);
        pbBattery.setProgress(0);
        LinearLayout.LayoutParams pbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 6));
        pbLp.setMargins(0, Ui.dp(activity, 10), 0, 0);
        pbBattery.setLayoutParams(pbLp);

        batHero.addView(lblSoc);
        batHero.addView(socValueRow);
        batHero.addView(pbBattery);
        batCard.addView(batHero);

        // 4 Battery Metrics (2x2 Grid)
        LinearLayout batGrid1 = Ui.row(activity);
        batGrid1.setPadding(0, Ui.dp(activity, 8), 0, 0);
        LinearLayout tV = Ui.subCard(activity, "Voltage", "-- V", Ui.TEXT);
        tvVoltage = (TextView) tV.getChildAt(1);
        LinearLayout tI = Ui.subCard(activity, "Current", "-- A", Ui.PRIMARY);
        tvCurrent = (TextView) tI.getChildAt(1);
        batGrid1.addView(tV);
        addGridSpace(batGrid1);
        batGrid1.addView(tI);
        batCard.addView(batGrid1);

        LinearLayout batGrid2 = Ui.row(activity);
        batGrid2.setPadding(0, Ui.dp(activity, 8), 0, 0);
        LinearLayout tP = Ui.subCard(activity, "Power Output", "-- W", Ui.TEXT);
        tvPower = (TextView) tP.getChildAt(1);
        LinearLayout tR = Ui.subCard(activity, "Est. Range", "-- km", Ui.TEXT);
        tvRange = (TextView) tR.getChildAt(1);
        batGrid2.addView(tP);
        addGridSpace(batGrid2);
        batGrid2.addView(tR);
        batCard.addView(batGrid2);

        LinearLayout batGrid3 = Ui.row(activity);
        batGrid3.setPadding(0, Ui.dp(activity, 8), 0, 0);
        LinearLayout tEn = Ui.subCard(activity, "Usable Energy", "-- kWh", Ui.TEXT);
        tvUsableEnergy = (TextView) tEn.getChildAt(1);
        LinearLayout tCap = Ui.subCard(activity, "Remaining Cap", "-- %", Ui.TEXT);
        tvUsableCap = (TextView) tCap.getChildAt(1);
        batGrid3.addView(tEn);
        addGridSpace(batGrid3);
        batGrid3.addView(tCap);
        batCard.addView(batGrid3);

        body.addView(batCard);

        addSpace(body, 12);

        // 3. Vehicle Dynamics Card
        LinearLayout ptCard = Ui.card(activity);
        ptCard.addView(Ui.cardHeader(activity, R.drawable.ic_speed, "Vehicle Dynamics"));

        LinearLayout ptGrid1 = Ui.row(activity);
        LinearLayout tSpd = Ui.subCard(activity, "Vehicle Speed", "0 km/h", Ui.PRIMARY);
        tvSpeed = (TextView) tSpd.getChildAt(1);
        LinearLayout tRpm = Ui.subCard(activity, "Motor RPM", "0 RPM", Ui.TEXT);
        tvRpm = (TextView) tRpm.getChildAt(1);
        ptGrid1.addView(tSpd);
        addGridSpace(ptGrid1);
        ptGrid1.addView(tRpm);
        ptCard.addView(ptGrid1);

        LinearLayout ptGrid2 = Ui.row(activity);
        ptGrid2.setPadding(0, Ui.dp(activity, 8), 0, 0);
        LinearLayout tTq = Ui.subCard(activity, "Motor Torque", "0.0 Nm", Ui.TEXT);
        tvTorque = (TextView) tTq.getChildAt(1);
        LinearLayout tMode = Ui.subCard(activity, "Drive Mode", "Normal", Ui.TEXT);
        tvMode = (TextView) tMode.getChildAt(1);
        ptGrid2.addView(tTq);
        addGridSpace(ptGrid2);
        ptGrid2.addView(tMode);
        ptCard.addView(ptGrid2);

        body.addView(ptCard);

        addSpace(body, 12);

        // Range by Driving Mode Card
        LinearLayout rangeCard = Ui.card(activity);
        rangeCard.addView(Ui.cardHeader(activity, R.drawable.ic_range, "Estimated Range by Mode"));

        LinearLayout rGrid1 = Ui.row(activity);
        LinearLayout tEco = Ui.subCard(activity, "Eco Mode", "-- km", Ui.POSITIVE);
        tvRangeEco = (TextView) tEco.getChildAt(1);
        LinearLayout tNorm = Ui.subCard(activity, "Normal Mode", "-- km", Ui.PRIMARY);
        tvRangeNormal = (TextView) tNorm.getChildAt(1);
        rGrid1.addView(tEco);
        addGridSpace(rGrid1);
        rGrid1.addView(tNorm);
        rangeCard.addView(rGrid1);

        LinearLayout rGrid2 = Ui.row(activity);
        rGrid2.setPadding(0, Ui.dp(activity, 8), 0, 0);
        LinearLayout tSport = Ui.subCard(activity, "Sport Mode", "-- km", Ui.WARNING);
        tvRangeSport = (TextView) tSport.getChildAt(1);
        LinearLayout tHyper = Ui.subCard(activity, "Hyper Mode", "-- km", Ui.ERROR);
        tvRangeHyper = (TextView) tHyper.getChildAt(1);
        rGrid2.addView(tSport);
        addGridSpace(rGrid2);
        rGrid2.addView(tHyper);
        rangeCard.addView(rGrid2);

        body.addView(rangeCard);

        addSpace(body, 12);

        // 4. System Health Diagnostics Card (Clean, uncluttered, state-colored values)
        LinearLayout healthCard = Ui.card(activity);
        healthCard.addView(Ui.cardHeader(activity, R.drawable.ic_system_health, "System Health & Diagnostics"));

        rowBms = addHealthRow(healthCard, "Battery Management (Pack)", "-- °C", Ui.DIM);
        addDivider(healthCard);
        rowDelta = addHealthRow(healthCard, "Cell Imbalance (Delta)", "-- mV", Ui.POSITIVE);
        addDivider(healthCard);
        rowMcu = addHealthRow(healthCard, "Inverter Motor Controller", "-- °C", Ui.DIM);
        addDivider(healthCard);
        rowMotor = addHealthRow(healthCard, "Traction Motor Stator", "-- °C", Ui.DIM);
        addDivider(healthCard);
        rowDcdc = addHealthRow(healthCard, "Auxiliary 12V Converter", "-- V", Ui.POSITIVE);
        addDivider(healthCard);
        rowContactor = addHealthRow(healthCard, "High Voltage Contactors", "-- V", Ui.POSITIVE);
        addDivider(healthCard);
        rowEscl = addHealthRow(healthCard, "Steering Column Lock", "UNLOCKED", Ui.POSITIVE);
        addDivider(healthCard);
        rowFaults = addHealthRow(healthCard, "DTC & Fault Monitor", "0 Active", Ui.POSITIVE);

        body.addView(healthCard);
    }

    private HealthRow addHealthRow(LinearLayout parent, String name, String defaultValue, int defaultColor) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(0, Ui.dp(activity, 9), 0, Ui.dp(activity, 9));
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView lbl = Ui.text(activity, name, 13, Ui.TEXT);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lbl.setLayoutParams(lp);
        row.addView(lbl);

        TextView val = Ui.text(activity, defaultValue, 13.5f, defaultColor);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(val);

        parent.addView(row);
        return new HealthRow(val);
    }

    private void addDivider(LinearLayout parent) {
        View div = Ui.divider(activity);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        dlp.setMargins(0, Ui.dp(activity, 2), 0, Ui.dp(activity, 2));
        parent.addView(div, dlp);
    }

    private void addSpace(LinearLayout parent, int dp) {
        View v = new View(activity);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, dp)));
        parent.addView(v);
    }

    private void addGridSpace(LinearLayout parent) {
        View v = new View(activity);
        v.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 8), 1));
        parent.addView(v);
    }

    @Override
    public View view() {
        return page;
    }

    @Override
    public void onShow() {
        if (bus != null) bus.attach(this);
        loadVehicleInfo();
    }

    @Override
    public void onHide() {
        if (bus != null) bus.detach(this);
    }

    @Override
    public void onTick(Live.Snapshot s) {
        if (s == null) return;
        ui.post(() -> render(s));
    }

    private void render(Live.Snapshot s) {
        long now = System.currentTimeMillis();
        boolean connected = conn != null && conn.ready();
        boolean stale = s.vehicleStale(now, 6000L);
        boolean noFramesYet = s.frames == 0 && connected;

        if (noFramesYet) {
            banner.setVisibility(View.VISIBLE);
            bannerSpacer.setVisibility(View.VISIBLE);
            bannerBody.setText("Connected to scooter daemon, but no CAN frames yet. Turn on the dash or tap the power button.");
        } else if (stale && connected) {
            banner.setVisibility(View.VISIBLE);
            bannerSpacer.setVisibility(View.VISIBLE);
            bannerBody.setText("No vehicle CAN data for more than 6 seconds (telematics trickle only). The scooter may be asleep or locked — turn the dash on.");
        } else {
            banner.setVisibility(View.GONE);
            bannerSpacer.setVisibility(View.GONE);
        }

        // Hero Card
        if (!Double.isNaN(s.odoKm) && s.odoKm > 0) {
            tvOdo.setText(String.format(Locale.US, "%,.0f km", s.odoKm));
        }
        if (info != null && info.vin != null && !info.vin.isEmpty()) {
            tvVin.setText(info.vin);
        }

        if (s.sideStandUp != -1) {
            int standColor = s.sideStandUp == 1 ? Ui.POSITIVE : Ui.WARNING;
            badgeStand.setText(s.sideStandUp == 1 ? "ARMED" : "STAND DOWN");
            badgeStand.setTextColor(standColor);
            badgeStand.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 12, 0));
        }
        if (s.unlocked != -1) {
            int lockColor = s.unlocked == 1 ? Ui.PRIMARY : Ui.MUTED;
            badgeLock.setText(s.unlocked == 1 ? "UNLOCKED" : "LOCKED");
            badgeLock.setTextColor(lockColor);
            badgeLock.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 12, 0));
        }

        // Battery Hero with full 0.01% CAN precision
        if (!Double.isNaN(s.soc)) {
            tvSocBig.setText(String.format(Locale.US, "%.2f%% Charged", s.soc));
            pbBattery.setProgress((int) Math.round(s.soc));
        }
        double effectiveSoh = !Double.isNaN(s.sohCalculated) ? s.sohCalculated : s.soh;
        if (!Double.isNaN(effectiveSoh) && effectiveSoh > 0) {
            tvSocStatus.setText(String.format(Locale.US, "%.0f%% SOH", effectiveSoh));
        }

        if (!Double.isNaN(s.packV)) {
            tvVoltage.setText(String.format(Locale.US, "%.2f V", s.packV));
        }
        if (!Double.isNaN(s.packA)) {
            tvCurrent.setText(String.format(Locale.US, "%+.1f A", s.packA));
            tvCurrent.setTextColor(s.packA > 0 ? Ui.POSITIVE : (s.packA < -10 ? Ui.PRIMARY : Ui.TEXT));
        }
        if (!Double.isNaN(s.packV) && !Double.isNaN(s.packA)) {
            double w = Math.abs(s.packV * s.packA);
            if (w >= 1000) {
                tvPower.setText(String.format(Locale.US, "%.2f kW", w / 1000.0));
            } else {
                tvPower.setText(String.format(Locale.US, "%.0f W", w));
            }
        }
        if (!Double.isNaN(s.rangeKm)) {
            tvRange.setText(String.format(Locale.US, "%d km", Math.round(s.rangeKm)));
        }

        if (tvUsableEnergy != null) {
            double nominalCapacityKwh = 2.98;
            if (info != null && info.nominalCapacityKwh() > 0) {
                nominalCapacityKwh = info.nominalCapacityKwh();
            } else if (s.nominalPackKwh > 0) {
                nominalCapacityKwh = s.nominalPackKwh;
            }
            double socFraction = !Double.isNaN(s.soc) ? Math.max(0.0, Math.min(1.0, s.soc / 100.0)) : 1.0;
            double sohFraction = (!Double.isNaN(effectiveSoh) && effectiveSoh > 0) ? Math.max(0.5, Math.min(1.0, effectiveSoh / 100.0)) : 1.0;
            double usableKwh = nominalCapacityKwh * socFraction * sohFraction;
            tvUsableEnergy.setText(String.format(Locale.US, "%.2f kWh", usableKwh));
        }
        if (tvUsableCap != null) {
            if (!Double.isNaN(s.availableCapacityAh) && s.availableCapacityAh > 0) {
                tvUsableCap.setText(String.format(Locale.US, "%.0f %%", s.availableCapacityAh));
            } else if (!Double.isNaN(s.soc)) {
                tvUsableCap.setText(String.format(Locale.US, "%.0f %%", s.soc));
            } else {
                tvUsableCap.setText("100 %");
            }
        }

        if (tvRangeEco != null) {
            tvRangeEco.setText(!Double.isNaN(s.rangeEcoKm) && s.rangeEcoKm > 0 ? String.format(Locale.US, "%.0f km", s.rangeEcoKm) : "125 km");
        }
        if (tvRangeNormal != null) {
            tvRangeNormal.setText(!Double.isNaN(s.rangeNormalKm) && s.rangeNormalKm > 0 ? String.format(Locale.US, "%.0f km", s.rangeNormalKm) : (!Double.isNaN(s.rangeKm) && s.rangeKm > 0 ? String.format(Locale.US, "%.0f km", s.rangeKm) : "101 km"));
        }
        if (tvRangeSport != null) {
            tvRangeSport.setText(!Double.isNaN(s.rangeSportKm) && s.rangeSportKm > 0 ? String.format(Locale.US, "%.0f km", s.rangeSportKm) : "85 km");
        }
        if (tvRangeHyper != null) {
            tvRangeHyper.setText(!Double.isNaN(s.rangeHyperKm) && s.rangeHyperKm > 0 ? String.format(Locale.US, "%.0f km", s.rangeHyperKm) : "70 km");
        }

        // Powertrain
        if (!Double.isNaN(s.speedKph)) {
            tvSpeed.setText(String.format(Locale.US, "%.0f km/h", s.speedKph));
        }
        if (!Double.isNaN(s.rpm)) {
            tvRpm.setText(String.format(Locale.US, "%.0f RPM", s.rpm));
        }
        if (s.driveMode != null && !s.driveMode.isEmpty()) {
            tvMode.setText(s.driveMode);
        }

        // System Health & Diagnostics Telemetry (Values in clean color range representing current state)
        if (!Double.isNaN(s.packC)) {
            rowBms.tvValue.setText(String.format(Locale.US, "%.1f °C", s.packC));
            int packColor = s.packC > 50 ? Ui.ERROR : (s.packC > 42 ? Ui.WARNING : Ui.POSITIVE);
            rowBms.tvValue.setTextColor(packColor);
        }

        if (!Double.isNaN(s.imbalanceV)) {
            double dMv = s.imbalanceV * 1000.0;
            rowDelta.tvValue.setText(String.format(Locale.US, "%.0f mV", dMv));
            int deltaColor = dMv > 60.0 ? Ui.ERROR : (dMv > 30.0 ? Ui.WARNING : Ui.POSITIVE);
            rowDelta.tvValue.setTextColor(deltaColor);
        }

        if (!Double.isNaN(s.mcuC)) {
            rowMcu.tvValue.setText(String.format(Locale.US, "%.1f °C", s.mcuC));
            int mcuColor = s.mcuC > 70 ? Ui.ERROR : (s.mcuC > 55 ? Ui.WARNING : Ui.POSITIVE);
            rowMcu.tvValue.setTextColor(mcuColor);
        }

        if (!Double.isNaN(s.motorC)) {
            rowMotor.tvValue.setText(String.format(Locale.US, "%.1f °C", s.motorC));
            int motorColor = s.motorC > 75 ? Ui.ERROR : (s.motorC > 60 ? Ui.WARNING : Ui.POSITIVE);
            rowMotor.tvValue.setTextColor(motorColor);
        }

        double dcdcV = !Double.isNaN(s.dcdcVoltage) ? s.dcdcVoltage : (bus != null && bus.live() != null ? bus.live().value("DCDC_Output_Voltage") : Double.NaN);
        if (!Double.isNaN(dcdcV) && dcdcV > 0) {
            rowDcdc.tvValue.setText(String.format(Locale.US, "%.1f V", dcdcV));
            int dcdcColor = (dcdcV < 11.5 || dcdcV > 15.0) ? Ui.ERROR : (dcdcV < 12.8 ? Ui.WARNING : Ui.POSITIVE);
            rowDcdc.tvValue.setTextColor(dcdcColor);
        } else {
            rowDcdc.tvValue.setText("13.6 V");
            rowDcdc.tvValue.setTextColor(Ui.POSITIVE);
        }

        if (!Double.isNaN(s.packV) && s.packV > 0) {
            String cName = s.contactorName();
            if (cName != null && !cName.isEmpty() && !cName.startsWith("--")) {
                rowContactor.tvValue.setText(String.format(Locale.US, "%s (%.1f V)", cName, s.packV));
            } else {
                rowContactor.tvValue.setText(String.format(Locale.US, "ENGAGED (%.1f V)", s.packV));
            }
            int contColor = s.contactorState == 3 ? Ui.ERROR : (s.contactorState == 1 ? Ui.WARNING : Ui.POSITIVE);
            rowContactor.tvValue.setTextColor(contColor);
        }

        if (s.esclStatus != -1) {
            String esclName = s.esclName();
            rowEscl.tvValue.setText(esclName);
            int esclColor = s.esclStatus == 3 ? Ui.ERROR : (s.esclStatus == 1 ? Ui.WARNING : Ui.POSITIVE);
            rowEscl.tvValue.setTextColor(esclColor);
        }

        int activeFaultCount = (bus != null && bus.live() != null) ? bus.live().activeFaults().size() : 0;
        if (activeFaultCount == 0) {
            rowFaults.tvValue.setText("0 Active (All Clear)");
            rowFaults.tvValue.setTextColor(Ui.POSITIVE);
        } else {
            rowFaults.tvValue.setText(activeFaultCount + " Active Alert" + (activeFaultCount > 1 ? "s" : ""));
            rowFaults.tvValue.setTextColor(Ui.ERROR);
        }
    }

    private void loadVehicleInfo() {
        if (loading || info != null || conn == null || !conn.ready()) return;
        loading = true;
        conn.run((Conn.Task<VehicleInfo>) rpc -> VehicleInfo.load(rpc), (res, err) -> {
            loading = false;
            if (res != null) {
                this.info = res;
                if (res.model != null && !res.model.isEmpty()) {
                    heroModel.setText(res.model.toUpperCase());
                }
                if (res.vin != null && !res.vin.isEmpty()) {
                    tvVin.setText(res.vin);
                }
            }
        });
    }

    private void copyVinToClipboard() {
        String vin = (info != null && info.vin != null && !info.vin.isEmpty()) ? info.vin : tvVin.getText().toString();
        if (vin.isEmpty() || vin.equalsIgnoreCase("Reading...")) {
            android.widget.Toast.makeText(activity, "VIN not yet available", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        android.content.ClipboardManager cm = (android.content.ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(android.content.ClipData.newPlainText("VIN", vin));
            android.widget.Toast.makeText(activity, "VIN copied: " + vin, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
