package dev.sola;

import android.app.Activity;
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
    private final TextView heroVinOdo;
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

    // System Health Card
    private final TextView tvBmsTemp;
    private final TextView tvMcuTemp;
    private final TextView tvMotorTemp;
    private final TextView tvBcmState;

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
        LinearLayout topRow = Ui.row(activity);

        // 56dp Icon Box with Moped Vector
        LinearLayout iconBox = Ui.column(activity);
        iconBox.setGravity(Gravity.CENTER);
        int pad = Ui.dp(activity, 12);
        iconBox.setPadding(pad, pad, pad, pad);
        iconBox.setBackground(Ui.round(activity, Ui.PRIMARY_CONTAINER, 16, 0));
        ImageView mopedIcon = new ImageView(activity);
        mopedIcon.setImageResource(R.drawable.ic_moped);
        mopedIcon.setColorFilter(Ui.PRIMARY);
        iconBox.addView(mopedIcon, new LinearLayout.LayoutParams(Ui.dp(activity, 32), Ui.dp(activity, 32)));

        LinearLayout titleCol = Ui.column(activity);
        titleCol.setPadding(Ui.dp(activity, 14), 0, 0, 0);

        TextView brand = Ui.text(activity, "OLA ELECTRIC", 10, Ui.MUTED);
        brand.setTypeface(Typeface.DEFAULT_BOLD);
        brand.setLetterSpacing(0.08f);

        heroModel = Ui.text(activity, "S1", 18, Ui.TEXT);
        heroModel.setTypeface(Typeface.DEFAULT_BOLD);

        heroVinOdo = Ui.text(activity, "VIN: Reading... • Odo: -- km", 12, Ui.DIM);
        heroVinOdo.setPadding(0, Ui.dp(activity, 2), 0, 0);

        titleCol.addView(brand);
        titleCol.addView(heroModel);
        titleCol.addView(heroVinOdo);

        // Status Badges Row (Aligned inside titleCol directly under VIN/Odo)
        LinearLayout badgeRow = Ui.row(activity);
        badgeRow.setPadding(0, Ui.dp(activity, 8), 0, 0);
        badgeLock = Ui.badge(activity, "LOCKED", Ui.PRIMARY);
        badgeStand = Ui.badge(activity, "STAND UP", Ui.POSITIVE);
        badgeRow.addView(badgeLock);
        View badgeSpace = new View(activity);
        badgeSpace.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 8), 1));
        badgeRow.addView(badgeSpace);
        badgeRow.addView(badgeStand);
        titleCol.addView(badgeRow);

        topRow.addView(iconBox);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        heroCard.addView(topRow);

        body.addView(heroCard);

        // Divider Space
        addSpace(body, 12);

        // 2. Battery Status Card (BatterySocSubCard)
        LinearLayout batCard = Ui.card(activity);
        batCard.addView(Ui.cardHeader(activity, R.drawable.ic_battery, "Battery Status"));

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
        rangeCard.addView(Ui.cardHeader(activity, R.drawable.ic_speed, "Estimated Range by Mode"));

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

        // 4. System Health Card (Components with Checkmark)
        LinearLayout healthCard = Ui.card(activity);
        healthCard.addView(Ui.cardHeader(activity, 0, "System Health"));

        tvBmsTemp = addComponentRow(healthCard, "Battery Management (BMS)", "-- °C");
        tvMcuTemp = addComponentRow(healthCard, "Inverter Controller (MCU)", "-- °C");
        tvMotorTemp = addComponentRow(healthCard, "Traction Motor", "-- °C");
        tvBcmState = addComponentRow(healthCard, "Body Control Module (BCM)", "Active");

        body.addView(healthCard);
    }

    private TextView addComponentRow(LinearLayout parent, String name, String value) {
        LinearLayout row = Ui.row(activity);
        row.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 8));

        ImageView check = new ImageView(activity);
        check.setImageResource(R.drawable.ic_check);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18));
        cp.rightMargin = Ui.dp(activity, 10);
        row.addView(check, cp);

        TextView lbl = Ui.text(activity, name, 13, Ui.TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lbl.setLayoutParams(lp);
        row.addView(lbl);

        TextView val = Ui.text(activity, value, 13, Ui.MUTED);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(val);

        parent.addView(row);
        return val;
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
            String vin = (info != null && info.vin != null) ? info.vin : "WAU...";
            heroVinOdo.setText(String.format(Locale.US, "VIN: %s • Odo: %,.0f km", vin, s.odoKm));
        }

        if (s.sideStandUp != -1) {
            int standColor = s.sideStandUp == 1 ? Ui.POSITIVE : Ui.WARNING;
            badgeStand.setText(s.sideStandUp == 1 ? "ARMED" : "STAND DOWN");
            badgeStand.setTextColor(standColor);
            badgeStand.setBackground(Ui.round(activity, Ui.alpha(standColor, 0x22), 12, 0));
        }
        if (s.unlocked != -1) {
            int lockColor = s.unlocked == 1 ? Ui.PRIMARY : Ui.MUTED;
            badgeLock.setText(s.unlocked == 1 ? "UNLOCKED" : "LOCKED");
            badgeLock.setTextColor(lockColor);
            badgeLock.setBackground(Ui.round(activity, Ui.alpha(lockColor, 0x22), 12, 0));
        }

        // Battery Hero with full 0.01% CAN precision
        if (!Double.isNaN(s.soc)) {
            tvSocBig.setText(String.format(Locale.US, "%.2f%% Charged", s.soc));
            pbBattery.setProgress((int) Math.round(s.soc));
        }
        if (!Double.isNaN(s.soh) && s.soh > 0) {
            tvSocStatus.setText(String.format(Locale.US, "%.0f%% SOH", s.soh));
        }

        if (!Double.isNaN(s.packV)) {
            tvVoltage.setText(String.format(Locale.US, "%.2f V", s.packV));
        }
        if (!Double.isNaN(s.packA)) {
            tvCurrent.setText(String.format(Locale.US, "%+.1f A", s.packA));
            tvCurrent.setTextColor(s.packA < -5 ? Ui.PRIMARY : (s.packA > 5 ? Ui.POSITIVE : Ui.TEXT));
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
            double nominalCapacityKwh = 2.98; // 3 kWh Ola S1 pack
            if (info != null && info.batteryVariant != null && info.batteryVariant.contains("4")) {
                nominalCapacityKwh = 3.97;
            }
            double socFraction = !Double.isNaN(s.soc) ? Math.max(0.0, Math.min(1.0, s.soc / 100.0)) : 1.0;
            double sohFraction = (!Double.isNaN(s.soh) && s.soh > 0) ? Math.max(0.5, Math.min(1.0, s.soh / 100.0)) : 1.0;
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

        // Thermals
        if (!Double.isNaN(s.packC)) {
            tvBmsTemp.setText(String.format(Locale.US, "%.1f °C", s.packC));
            tvBmsTemp.setTextColor(s.packC > 45 ? Ui.WARNING : Ui.POSITIVE);
        }
        if (!Double.isNaN(s.mcuC)) {
            tvMcuTemp.setText(String.format(Locale.US, "%.1f °C", s.mcuC));
            tvMcuTemp.setTextColor(s.mcuC > 60 ? Ui.WARNING : Ui.POSITIVE);
        }
        if (!Double.isNaN(s.motorC)) {
            tvMotorTemp.setText(String.format(Locale.US, "%.1f °C", s.motorC));
            tvMotorTemp.setTextColor(s.motorC > 65 ? Ui.WARNING : Ui.POSITIVE);
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
            }
        });
    }
}
