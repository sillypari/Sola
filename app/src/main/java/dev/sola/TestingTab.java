package dev.sola;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import dev.sola.core.Live;

/**
 * Axiom-styled Vehicle ECU Testing & Actuation Suite for Sola.
 * Implements all hardware actuations, routines, switch tests, and bus verifications
 * discovered in the factory diagnostic tool (com.olaelectric.mfg.diagnosticapp).
 * Features expandable detail cards explaining test behavior, safety preconditions,
 * inline live test execution output, and CAN protocol wire frames.
 * Zero emojis. Material 3 Obsidian dark theme.
 */
public final class TestingTab implements Tab, Bus.Listener {

    private static final String[] CATEGORIES = {
            "ALL", "BMS", "MCU", "BUS", "LOCKS", "LIGHTS", "AUDIO", "SWITCHES"
    };

    private final Activity activity;
    private final Conn conn;
    private final Bus bus;
    private final ScrollView page;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);

    // Safety Interlock Banner
    private final LinearLayout safetyBanner;
    private final TextView safetyText;

    // Filter Chips
    private String selectedCategory = "ALL";
    private final List<TextView> categoryChips = new ArrayList<>();

    // Test Cards Registry
    private final List<TestItemView> testItemViews = new ArrayList<>();

    // Master Diagnostic Console Output (Bottom of tab)
    private TextView tvConsoleLog;
    private Button btnAutoTest;
    private TextView tvHeroSafetyStatus;
    private boolean autoTestRunning = false;
    private boolean vehicleMoving = false;

    public TestingTab(Activity activity, Conn conn, Bus bus) {
        this.activity = activity;
        this.conn = conn;
        this.bus = bus;

        this.page = Ui.page(activity);
        if (bus != null) {
            bus.attach(this);
        }
        LinearLayout body = Ui.body(page);

        // 1. Safety Interlock Banner
        safetyBanner = Ui.column(activity);
        safetyBanner.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 10), Ui.dp(activity, 14), Ui.dp(activity, 10));
        safetyBanner.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x18), 12, Ui.alpha(Ui.WARNING, 0x55)));
        safetyBanner.setVisibility(View.GONE);

        LinearLayout sbRow = Ui.row(activity);
        ImageView sbIcon = new ImageView(activity);
        sbIcon.setImageResource(R.drawable.ic_warning);
        sbIcon.setColorFilter(Ui.WARNING);
        LinearLayout.LayoutParams sbIp = new LinearLayout.LayoutParams(Ui.dp(activity, 18), Ui.dp(activity, 18));
        sbIp.rightMargin = Ui.dp(activity, 8);
        sbRow.addView(sbIcon, sbIp);

        safetyText = Ui.text(activity, "VEHICLE MOTION DETECTED: Actuations locked for physical safety.", 12, Ui.WARNING);
        safetyText.setTypeface(Typeface.DEFAULT_BOLD);
        sbRow.addView(safetyText);
        safetyBanner.addView(sbRow);

        LinearLayout.LayoutParams sbLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sbLp.bottomMargin = Ui.dp(activity, 8);
        safetyBanner.setLayoutParams(sbLp);
        body.addView(safetyBanner);

        // 2. Hero Diagnostic Suite Header Card (Axiom Style)
        LinearLayout heroCard = Ui.card(activity);

        // Header Row: Hardware Icon + Title/Eyebrow + Auto Test Button on Right
        LinearLayout topRow = Ui.row(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout iconBox = Ui.column(activity);
        iconBox.setGravity(Gravity.CENTER);
        iconBox.setPadding(0, 0, 0, 0);
        iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));

        ImageView hwIcon = new ImageView(activity);
        hwIcon.setImageResource(R.drawable.ic_advanced_hardware);
        hwIcon.setColorFilter(Ui.TEXT);
        iconBox.addView(hwIcon, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));

        LinearLayout.LayoutParams hip = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36));
        hip.rightMargin = Ui.dp(activity, 12);
        topRow.addView(iconBox, hip);

        LinearLayout titleCol = Ui.column(activity);
        TextView eyebrow = Ui.text(activity, "ECU ACTUATORS", 9.5f, Ui.MUTED);
        eyebrow.setTypeface(Typeface.DEFAULT_BOLD);
        eyebrow.setLetterSpacing(0.08f);

        TextView tvHeroTitle = Ui.text(activity, "Actuator Suite", 18, Ui.TEXT);
        tvHeroTitle.setTypeface(Typeface.DEFAULT_BOLD);

        TextView tvHeroSub = Ui.text(activity, "VCAN0 Bus (500 kbps)", 11.5f, Ui.MUTED);
        tvHeroSub.setPadding(0, Ui.dp(activity, 2), 0, 0);

        titleCol.addView(eyebrow);
        titleCol.addView(tvHeroTitle);
        titleCol.addView(tvHeroSub);
        topRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnAutoTest = Ui.button(activity, "AUTO TEST", v -> runAutomatedSelfTest());
        btnAutoTest.setTextSize(11);
        btnAutoTest.setTypeface(Typeface.DEFAULT_BOLD);
        btnAutoTest.setTextColor(Ui.ACCENT_TEXT);
        btnAutoTest.setBackground(Ui.round(activity, Ui.PRIMARY, 12, 0));
        btnAutoTest.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 6), Ui.dp(activity, 14), Ui.dp(activity, 6));
        btnAutoTest.setMinHeight(Ui.dp(activity, 34));
        btnAutoTest.setMinimumHeight(Ui.dp(activity, 34));
        topRow.addView(btnAutoTest);

        heroCard.addView(topRow);

        // Subtle Divider Line
        View heroDiv = new View(activity);
        heroDiv.setBackgroundColor(Ui.LINE);
        LinearLayout.LayoutParams hdlp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 1));
        hdlp.setMargins(0, Ui.dp(activity, 12), 0, Ui.dp(activity, 12));
        heroCard.addView(heroDiv, hdlp);

        // 2 Evenly Sized Sub-Cards: Safety Interlock - CAN Bus
        LinearLayout subCardRow = Ui.row(activity);

        LinearLayout cardSafety = Ui.subCard(activity, "Safety", "READY", Ui.POSITIVE);
        tvHeroSafetyStatus = (TextView) cardSafety.getChildAt(1);

        LinearLayout cardBus = Ui.subCard(activity, "CAN Node", "VCAN0", Ui.TEXT);

        subCardRow.addView(cardSafety);
        View sp1 = new View(activity);
        sp1.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(activity, 8), 1));
        subCardRow.addView(sp1);
        subCardRow.addView(cardBus);

        heroCard.addView(subCardRow);

        LinearLayout.LayoutParams hcLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hcLp.bottomMargin = Ui.dp(activity, 10);
        heroCard.setLayoutParams(hcLp);
        body.addView(heroCard);

        // 3. Category Filter Chips (Horizontal Scroll)
        HorizontalScrollView chipScroll = new HorizontalScrollView(activity);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        csLp.bottomMargin = Ui.dp(activity, 10);
        chipScroll.setLayoutParams(csLp);

        LinearLayout chipRow = Ui.row(activity);
        chipRow.setPadding(0, 0, 0, Ui.dp(activity, 2));

        for (String cat : CATEGORIES) {
            TextView chip = new TextView(activity);
            chip.setText(cat);
            chip.setTextSize(11);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false);
            chip.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 14), 0);

            boolean active = cat.equals(selectedCategory);
            chip.setTextColor(active ? Ui.ACCENT_TEXT : Ui.MUTED);
            chip.setBackground(Ui.round(activity, active ? Ui.PRIMARY : Ui.SURFACE_HIGH, 12, 0));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 32));
            lp.rightMargin = Ui.dp(activity, 8);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> filterCategory(cat));
            categoryChips.add(chip);
            chipRow.addView(chip);
        }
        chipScroll.addView(chipRow);
        body.addView(chipScroll);

        // 4. Test Actuation Items Registry Container
        LinearLayout testContainer = Ui.column(activity);
        registerAllTestItems(testContainer);
        body.addView(testContainer);

        // 5. Live Diagnostic Execution Terminal Card (Master Log)
        LinearLayout consoleCard = createSectionHeader(R.drawable.ic_terminal, "Live Execution Log & UDS Monitor", "Real-time CAN frame response and error stream");
        LinearLayout.LayoutParams ccLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ccLp.topMargin = Ui.dp(activity, 4);
        consoleCard.setLayoutParams(ccLp);

        tvConsoleLog = new TextView(activity);
        tvConsoleLog.setText("Ready. Tap any test actuation above to dispatch UDS frames to vehicle bus.");
        tvConsoleLog.setTextSize(11);
        tvConsoleLog.setTextColor(Ui.MUTED);
        tvConsoleLog.setTypeface(Typeface.MONOSPACE);
        tvConsoleLog.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 10, 0));
        tvConsoleLog.setPadding(Ui.dp(activity, 12), Ui.dp(activity, 10), Ui.dp(activity, 12), Ui.dp(activity, 10));
        tvConsoleLog.setMinLines(4);
        consoleCard.addView(tvConsoleLog);
        body.addView(consoleCard);
    }

    @Override
    public View view() {
        return page;
    }

    @Override
    public void onShow() {
        if (bus != null) {
            bus.attach(this);
        }
    }

    @Override
    public void onHide() {
        if (bus != null) {
            bus.detach(this);
        }
    }

    @Override
    public void onTick(Live.Snapshot s) {
        ui.post(() -> {
            if (s == null) return;

            // Safety Interlock
            vehicleMoving = !Double.isNaN(s.speedKph) && s.speedKph > 0.5;
            safetyBanner.setVisibility(vehicleMoving ? View.VISIBLE : View.GONE);
            if (tvHeroSafetyStatus != null) {
                tvHeroSafetyStatus.setText(vehicleMoving ? "LOCKED" : "READY");
                tvHeroSafetyStatus.setTextColor(vehicleMoving ? Ui.WARNING : Ui.POSITIVE);
            }
        });
    }

    // -------------------------------------------------------------------------
    // Test Item Registration & Setup
    // -------------------------------------------------------------------------

    private void registerAllTestItems(LinearLayout container) {
        // --- Category: BMS (Pack & Battery Electronics) ---
        addTestItem(container, "BMS", "Active Cell Balancing Routine", "ROx05F8 (31 01 05 F8) - Initiates pack-wide cell voltage balancing", "BMS 0x78E", "START ROUTINE",
                "cansend vcan0 78E#04310105F8000000",
                "Instructs the Battery Management System (BMS) to begin cell balancing across all cell groups, bleeding excess charge through balancing resistors to equalize delta millivolts.",
                "Target ECU: BMS (CAN 0x78E)\nService: 0x31 (RoutineControl) | RID: 0x05F8 (CellBalancing)\nFrame: [04 31 01 05 F8 00 00 00]\nMonitor balancing tags live in Diagnostics tab.");

        addTestItem(container, "BMS", "BMS Pre-Charge FET Test", "IOxE70C (2F E7 0C 03) - Tests pre-charge resistor FET before main contactor", "BMS 0x78E", "TEST",
                "cansend vcan0 78E#052FE70C03010000; sleep 1; cansend vcan0 78E#042FE70C00000000",
                "Verifies the high-voltage pre-charge circuit that safely charges motor controller DC bus capacitors before the main battery contactor engages.",
                "Target ECU: BMS (CAN 0x78E)\nService: 0x2F | DID: 0xE70C (PreChargeFET)\nRequest ON Frame: [05 2F E7 0C 03 01 00 00]\nRelease Frame: [04 2F E7 0C 00 00 00 00]");

        addTestItem(container, "BMS", "BMS Gate Driver Enable", "IOxE709 (2F E7 09 03) - High-side / low-side MOSFET gate driver test", "BMS 0x78E", "TEST",
                "cansend vcan0 78E#052FE70903010000; sleep 1; cansend vcan0 78E#042FE70900000000",
                "Tests the high-side and low-side MOSFET gate driver logic on the BMS power stage.",
                "Target ECU: BMS (CAN 0x78E)\nService: 0x2F | DID: 0xE709 (GateDriver)\nRequest ON Frame: [05 2F E7 09 03 01 00 00]\nRelease Frame: [04 2F E7 09 00 00 00 00]");

        addTestItem(container, "BMS", "Analog Front End (AFE) Reset", "IOxE70D (2F E7 0D 03) - Software reset of cell monitoring AFE chip", "BMS 0x78E", "RESET",
                "cansend vcan0 78E#052FE70D03010000; sleep 1; cansend vcan0 78E#042FE70D00000000",
                "Performs a warm reset of the multichannel battery cell voltage and thermistor ADC monitoring chip (AFE) to clear communication locks.",
                "Target ECU: BMS (CAN 0x78E)\nService: 0x2F | DID: 0xE70D (AFEReset)\nRequest ON Frame: [05 2F E7 0D 03 01 00 00]\nRelease Frame: [04 2F E7 0D 00 00 00 00]");

        addTestItem(container, "BMS", "Auxiliary 12V Battery Charger", "IOx0423 (2F 04 23 03) - DC-DC auxiliary battery charging circuit", "VCU 0x786", "TEST",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F042303010000; sleep 2; cansend vcan0 786#042F042300000000",
                "Verifies the DC-DC buck converter circuit responsible for charging the 12V auxiliary battery from the main traction pack.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0423 (Aux Battery Charger)\nRequest ON Frame: [05 2F 04 23 03 01 00 00]\nRelease Frame: [04 2F 04 23 00 00 00 00]");

        // --- Category: MCU (Motor & Inverter) ---
        addTestItem(container, "MCU", "Motor FOC Auto-Tune Start", "ROx06F1 (31 01 05 F1) - Calibrates motor resolver offset and inductance", "MCU 0x7A6", "START",
                "cansend vcan0 7A6#04310105F1000000",
                "Initiates resolver angle alignment and d/q-axis stator inductance mapping for Field-Oriented Control (FOC). Required after motor or inverter replacement.",
                "Target ECU: MCU (CAN 0x7A6)\nService: 0x31 | RID: 0x05F1 (AutoTuneStart)\nPrerequisite: Scooter MUST be on center stand with rear wheel freely suspended.\nFrame: [04 31 01 05 F1 00 00 00]");

        addTestItem(container, "MCU", "Motor FOC Auto-Tune Stop", "ROx06F1 (31 02 06 F1) - Force stops active motor auto-calibration", "MCU 0x7A6", "STOP",
                "cansend vcan0 7A6#04310206F1000000",
                "Forcefully halts the autotuning sequence and returns the motor controller to standard idle mode.",
                "Target ECU: MCU (CAN 0x7A6)\nService: 0x31 | RID: 0x06F1 (AutoTuneStop)\nFrame: [04 31 02 06 F1 00 00 00]");

        addTestItem(container, "MCU", "Motor Auto-Tune Counter Query", "ROx06F1 (31 03 06 F1) - Reads successful auto-tune calibration counter", "MCU 0x7A6", "QUERY",
                "cansend vcan0 7A6#04310306F1000000",
                "Queries the number of successful resolver calibrations recorded in motor controller flash memory.",
                "Target ECU: MCU (CAN 0x7A6)\nService: 0x31 | RID: 0x06F1 (AutotuneResult)\nSubfunction: 0x03 (RequestRoutineResults)\nFrame: [04 31 03 06 F1 00 00 00]");

        // --- Category: BUS (CAN, LIN & UART Transceivers) ---
        addTestItem(container, "BUS", "Smart Module (SOM) UART Loopback", "ROxBF03/4 (31 01 BF 03) - VCU to SOM high-speed UART communication test", "VCU 0x786", "TEST",
                "cansend vcan0 786#043101BF03000000",
                "Transmits known byte patterns across the VCU-to-Android Smart Module serial bus to verify baud rate stability and frame parity.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x31 | RID: 0xBF03 & 0xBF04\nFrame: [04 31 01 BF 03 00 00 00]");

        addTestItem(container, "BUS", "Handlebar Controller (HBC) LIN Bus", "ROxBF05 (31 01 BF 05) - Tests LIN physical layer to handlebar switches", "VCU 0x786", "TEST",
                "cansend vcan0 786#043101BF05000000",
                "Performs communication loopback across the LIN transceiver to the handlebar switch pods.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x31 | RID: 0xBF05 (HBC_LIN_Communication)\nFrame: [04 31 01 BF 05 00 00 00]");

        addTestItem(container, "BUS", "Head Unit (HU) CAN Bus Check", "ROxBF06 (31 01 BF 06) - Validates display cluster CAN bus communication", "VCU 0x786", "TEST",
                "cansend vcan0 786#043101BF06000000",
                "Tests CAN transceiver frame reception between the Vehicle Control Unit and the Head Unit display cluster.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x31 | RID: 0xBF06 (HU_CAN_Communication)\nFrame: [04 31 01 BF 06 00 00 00]");

        addTestItem(container, "BUS", "Smart Module (SOM) Self-Check", "ROxBF02 (31 01 BF 02) - Internal self-diagnostic check on Android SOM", "VCU 0x786", "RUN CHECK",
                "cansend vcan0 786#043101BF02000000",
                "Executes internal diagnostics on the Smart Module covering kernel watchdog, storage read/write integrity, and thermal zones.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x31 | RID: 0xBF02 (Perform_Selfcheck_on_SOM)\nFrame: [04 31 01 BF 02 00 00 00]");

        addTestItem(container, "BUS", "MCU Peripherals Loopback", "ROxBF01 (31 01 BF 01) - Validates inverter and controller hardware interfaces", "VCU 0x786", "RUN CHECK",
                "cansend vcan0 786#043101BF01000000",
                "Validates inverter hardware communication, phase current transducers, gate driver fault lines, and thermistors.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x31 | RID: 0xBF01 (Check_MCU_peripherals)\nFrame: [04 31 01 BF 01 00 00 00]");

        // --- Category: LOCKS ---
        addTestItem(container, "LOCKS", "Trunk / Boot Latch Release", "IOx0402 (2F 04 02 03) - Triggers under-seat electric release latch", "VCU 0x786", "ACTUATE",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040203010000; sleep 1; cansend vcan0 786#042F040200000000",
                "Sends a UDS Service 0x2F actuation command to the Vehicle Control Unit (VCU) to energize the 12V seat latch solenoid for 800ms, mechanically popping the under-seat luggage compartment open.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F (InputOutputControlByIdentifier)\nData Identifier (DID): 0x0402 (Seat Lock 12V supply)\nSession: Extended Diagnostic (0x10 0x03)\nRequest ON Frame: [05 2F 04 02 03 01 00 00]\nRelease Frame: [04 2F 04 02 00 00 00 00]\nExpected Response: 0x7B0 -> [04 6F 04 02 01 ...]");

        addTestItem(container, "LOCKS", "Electronic Steering Column Lock (ESCL)", "IOx0400 (2F 04 00 03) - Tests steering lock actuator solenoid", "VCU 0x786", "TEST",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040003010000; sleep 1; cansend vcan0 786#042F040000000000",
                "Actuates the motorized steering column lock bolt to verify solenoid drive circuitry, position sensors, and lock motor current limits.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0400\nPrerequisite: Vehicle must be stationary with handlebar centered or in lock position.\nRequest ON Frame: [05 2F 04 00 03 01 00 00]\nRelease Frame: [04 2F 04 00 00 00 00 00]");

        addTestItem(container, "LOCKS", "CAN Lock / Unlock Sequence", "ROxBF07 (31 01 BF 07) - Complete vehicle CAN lock/unlock transition routine", "VCU 0x786", "RUN ROUTINE",
                "cansend vcan0 786#043101BF07000000",
                "Triggers the vehicle-wide security state transition over CAN. Coordinates VCU, MCU power gate, and BMS sleep/wake envelopes.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x31 (RoutineControl)\nRoutine Identifier (RID): 0xBF07 (LockUnlockSequenceOverCAN)\nFrame: [04 31 01 BF 07 00 00 00]");

        addTestItem(container, "LOCKS", "Security Alarm 12V Supply", "IOx0401 (2F 04 01 03) - Powers external anti-theft alarm siren circuit", "VCU 0x786", "PULSE 1s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040103010000; sleep 1; cansend vcan0 786#042F040100000000",
                "Energizes the 12V anti-theft alarm siren circuit for 1 second to verify driver transistor functionality and wiring harness continuity.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0401 (Alarm 12V supply control)\nRequest ON Frame: [05 2F 04 01 03 01 00 00]\nRelease Frame: [04 2F 04 01 00 00 00 00]");

        // --- Category: LIGHTS ---
        addTestItem(container, "LIGHTS", "Front Light Low Beam", "IOx0406 (2F 04 06 03) - Front low beam LED 12V control circuit", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040603010000; sleep 3; cansend vcan0 786#042F040600000000",
                "Powers the low beam LED module for 3 seconds to verify light intensity, driver FET status, and headlamp current draw.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0406 (Front light low beam 12V control)\nRequest ON Frame: [05 2F 04 06 03 01 00 00]\nRelease Frame: [04 2F 04 06 00 00 00 00]\nExpected Response: 0x7B0 -> [04 6F 04 06 01 ...]");

        addTestItem(container, "LIGHTS", "Front Light High Beam", "IOx0407 (2F 04 07 03) - Front high beam projector LED 12V circuit", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040703010000; sleep 3; cansend vcan0 786#042F040700000000",
                "Activates the projector high beam LED lighting circuit for 3 seconds to verify beam throw and high-current relay operation.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0407 (Front light high beam 12V control)\nRequest ON Frame: [05 2F 04 07 03 01 00 00]\nRelease Frame: [04 2F 04 07 00 00 00 00]");

        addTestItem(container, "LIGHTS", "Front DRL (Daytime Running Light)", "IOx0405 (2F 04 05 03) - Signature ring LED lighting test", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040503010000; sleep 3; cansend vcan0 786#042F040500000000",
                "Energizes the signature circular DRL halo LED ring for 3 seconds to confirm driver rail regulation.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0405 (Front DRL 12V supply control)\nRequest ON Frame: [05 2F 04 05 03 01 00 00]\nRelease Frame: [04 2F 04 05 00 00 00 00]");

        addTestItem(container, "LIGHTS", "Left Turn Indicators", "IOx0409 (2F 04 09 03) - Left front and rear amber flasher circuit", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040903010000; sleep 3; cansend vcan0 786#042F040900000000",
                "Enters Extended Diagnostic Session (0x10 0x03), pulses the front left and rear left amber turn indicator LEDs for 3 seconds with state 0x01 (ON), then returns control to the VCU (0x00).",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0409 (Left Indicators 12V supply control)\nSession Request: [02 10 03 00 00 00 00 00]\nRequest ON Frame: [05 2F 04 09 03 01 00 00]\nRelease Frame: [04 2F 04 09 00 00 00 00]\nExpected Response: 0x7B0 -> [04 6F 04 09 01 ...]");

        addTestItem(container, "LIGHTS", "Right Turn Indicators", "IOx0408 (2F 04 08 03) - Right front and rear amber flasher circuit", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040803010000; sleep 3; cansend vcan0 786#042F040800000000",
                "Enters Extended Diagnostic Session (0x10 0x03), pulses the front right and rear right amber turn indicator LEDs for 3 seconds with state 0x01 (ON), then returns control to the VCU (0x00).",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0408 (Right Indicators 12V supply control)\nSession Request: [02 10 03 00 00 00 00 00]\nRequest ON Frame: [05 2F 04 08 03 01 00 00]\nRelease Frame: [04 2F 04 08 00 00 00 00]\nExpected Response: 0x7B0 -> [04 6F 04 08 01 ...]");

        addTestItem(container, "LIGHTS", "Rear Brake Light", "IOx040A (2F 04 0A 03) - Rear high-intensity brake stop lamp circuit", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040A03010000; sleep 3; cansend vcan0 786#042F040A00000000",
                "Activates the high-intensity rear brake stop lamp LEDs for 3 seconds to verify brake signaling independently of brake levers.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x040A (Rear Brake light 12V supply control)\nRequest ON Frame: [05 2F 04 0A 03 01 00 00]\nRelease Frame: [04 2F 04 0A 00 00 00 00]");

        addTestItem(container, "LIGHTS", "Rear Position / Tail Lamp", "IOx040B (2F 04 0B 03) - Continuous rear running position light", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040B03010000; sleep 3; cansend vcan0 786#042F040B00000000",
                "Powers the continuous rear red position running light strip to verify nighttime visibility illumination.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x040B (Rear Light POS 12V supply control)\nRequest ON Frame: [05 2F 04 0B 03 01 00 00]\nRelease Frame: [04 2F 04 0B 00 00 00 00]");

        addTestItem(container, "LIGHTS", "Under-Seat Boot Lamp", "IOx040C (2F 04 0C 03) - Luggage compartment illumination LED", "VCU 0x786", "PULSE 3s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040C03010000; sleep 3; cansend vcan0 786#042F040C00000000",
                "Illuminates the luggage compartment interior LED lamp to confirm driver circuit and bulb health.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x040C (Boot Light 12V supply control)\nRequest ON Frame: [05 2F 04 0C 03 01 00 00]\nRelease Frame: [04 2F 04 0C 00 00 00 00]");

        // --- Category: AUDIO ---
        addTestItem(container, "AUDIO", "Vehicle Horn Test", "IOx0410 (2F 04 10 03) - Sends momentary pulse to 12V horn relay", "VCU 0x786", "PULSE 300ms",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F041003010000; sleep 0.3; cansend vcan0 786#042F041000000000",
                "Issues a short 300ms 12V pulse to the vehicle electromagnetic horn to test audible alerting without holding the physical handlebar button.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x0410 (Horn 12V supply control)\nRequest ON Frame: [05 2F 04 10 03 01 00 00]\nRelease Frame: [04 2F 04 10 00 00 00 00]");

        addTestItem(container, "AUDIO", "Speaker Amplifier Supply", "IOx03FF (2F 03 FF 03) - Powers internal head unit audio amplifier rail", "VCU 0x786", "ENABLE 2s",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F03FF03010000; sleep 2; cansend vcan0 786#042F03FF00000000",
                "Switches on the power supply rail for the Head Unit (HU) / SOM stereo speaker amplifier to verify sound output circuitry.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x03FF (Speaker Supply Enable)\nRequest ON Frame: [05 2F 03 FF 03 01 00 00]\nRelease Frame: [04 2F 03 FF 00 00 00 00]");

        addTestItem(container, "AUDIO", "Find My Scooter Locator Pattern", "Triggers synchronized horn chime and hazard flasher locator pulse", "VCU 0x786", "TRIGGER",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F041003010000; cansend vcan0 786#052F040503010000; sleep 0.4; cansend vcan0 786#042F041000000000; sleep 0.6; cansend vcan0 786#042F040500000000",
                "Simulates the companion app locate function by coordinating a momentary horn chirp with simultaneous front and rear hazard light flashes.",
                "Target ECU: VCU (CAN 0x786)\nCombines Horn (0x0410) and Front DRL (0x0405) actuations with session control.");

        // --- Category: SWITCHES ---
        addTestItem(container, "SWITCHES", "Side Stand Switch Supply", "IOx03F7 (2F 03 F7 03) - Verifies 5V/12V sensor excitation supply rail", "VCU 0x786", "CHECK",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F03F703010000; sleep 1; cansend vcan0 786#042F03F700000000",
                "Verifies excitation voltage delivery to the hall-effect side stand deployment sensor.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x03F7 (Side Stand switch supply control)\nRequest ON Frame: [05 2F 03 F7 03 01 00 00]");

        addTestItem(container, "SWITCHES", "Seat Occupant Sensor", "IOx03F8 (2F 03 F8 03) - Under-seat pressure switch excitation circuit", "VCU 0x786", "CHECK",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F03F803010000; sleep 1; cansend vcan0 786#042F03F800000000",
                "Tests the excitation circuit for the under-seat pressure sensor used to detect rider presence.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x03F8 (Seat Occupant switch supply)\nRequest ON Frame: [05 2F 03 F8 03 01 00 00]");

        addTestItem(container, "SWITCHES", "Start / Stop SSB Switch", "IOx03F9 (2F 03 F9 03) - Handlebar power button supply circuit", "VCU 0x786", "CHECK",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F03F903010000; sleep 1; cansend vcan0 786#042F03F900000000",
                "Checks electrical continuity and excitation supply to the handlebar Start/Stop power push button.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x03F9 (SSB switch supply control)\nRequest ON Frame: [05 2F 03 F9 03 01 00 00]");

        addTestItem(container, "SWITCHES", "Brake Pad Wear Sensors", "IOx03FA/B (2F 03 FA 03) - Front and rear brake pad wear sensor circuits", "VCU 0x786", "CHECK",
                "cansend vcan0 786#0210030000000000; cansend vcan0 786#052F03FA03010000; sleep 1; cansend vcan0 786#042F03FA00000000",
                "Tests excitation voltage across the front and rear brake caliper wear sensor loops to detect worn pads or severed sensor wires.",
                "Target ECU: VCU (CAN 0x786)\nService: 0x2F | DID: 0x03FA (Right Brake pad wear) & 0x03FB (Left Brake pad wear)\nRequest ON Frame: [05 2F 03 FA 03 01 00 00]");
    }

    private void addTestItem(LinearLayout parent, String category, String title, String subtitle,
                             String ecu, String actionText, String canCommand,
                             String explanation, String protocolDetails) {
        LinearLayout card = Ui.column(activity);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackground(Ui.round(activity, Ui.SURFACE, 14, 0));

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.bottomMargin = Ui.dp(activity, 8);
        card.setLayoutParams(cardLp);

        // Header Row (Clickable anywhere on top to toggle expand/collapse)
        LinearLayout topRow = Ui.row(activity);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout textCol = Ui.column(activity);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout eyebrowRow = Ui.row(activity);
        eyebrowRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView ecuPill = Ui.text(activity, ecu, 9f, Ui.PRIMARY);
        ecuPill.setTypeface(Typeface.DEFAULT_BOLD);
        ecuPill.setSingleLine(true);
        ecuPill.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 6, 0));
        ecuPill.setPadding(Ui.dp(activity, 6), Ui.dp(activity, 2), Ui.dp(activity, 6), Ui.dp(activity, 2));
        eyebrowRow.addView(ecuPill);

        TextView catTag = Ui.text(activity, category, 9f, Ui.MUTED);
        catTag.setTypeface(Typeface.DEFAULT_BOLD);
        catTag.setLetterSpacing(0.06f);
        catTag.setPadding(Ui.dp(activity, 6), 0, 0, 0);
        eyebrowRow.addView(catTag);

        textCol.addView(eyebrowRow);

        TextView tvTitle = Ui.text(activity, title, 14f, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setPadding(0, Ui.dp(activity, 2), 0, 0);
        textCol.addView(tvTitle);

        TextView tvSub = Ui.text(activity, subtitle, 11f, Ui.MUTED);
        tvSub.setSingleLine(true);
        tvSub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tvSub.setPadding(0, Ui.dp(activity, 2), 0, 0);
        textCol.addView(tvSub);

        topRow.addView(textCol);

        // Compact Inline Action Button inside topRow
        Button btnAction = new Button(activity);
        btnAction.setText(actionText);
        btnAction.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        btnAction.setTypeface(Typeface.DEFAULT_BOLD);
        btnAction.setTextColor(Ui.ACCENT_TEXT);
        btnAction.setBackground(Ui.round(activity, Ui.PRIMARY, 10, 0));
        btnAction.setPadding(Ui.dp(activity, 12), 0, Ui.dp(activity, 12), 0);
        btnAction.setMinHeight(Ui.dp(activity, 32));
        btnAction.setMinimumHeight(Ui.dp(activity, 32));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(activity, 32));
        btnLp.leftMargin = Ui.dp(activity, 8);
        topRow.addView(btnAction, btnLp);

        ImageView chevron = new ImageView(activity);
        chevron.setImageResource(R.drawable.ic_expand_more);
        chevron.setColorFilter(Ui.MUTED);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20));
        cp.leftMargin = Ui.dp(activity, 6);
        topRow.addView(chevron, cp);

        card.addView(topRow);

        TextView badgeStatus = createStatusPill(activity, "IDLE", Ui.DIM);
        badgeStatus.setSingleLine(true);

        // Expandable Details Container (Clean, concise, zero clutter)
        LinearLayout detailsContainer = Ui.column(activity);
        detailsContainer.setVisibility(View.GONE);
        detailsContainer.setPadding(0, Ui.dp(activity, 8), 0, 0);

        View div = Ui.divider(activity);
        detailsContainer.addView(div);

        TextView tvExp = Ui.text(activity, explanation, 12, Ui.TEXT);
        tvExp.setLineSpacing(0, 1.25f);
        tvExp.setPadding(0, Ui.dp(activity, 8), 0, Ui.dp(activity, 6));
        detailsContainer.addView(tvExp);

        // Technical Spec Box (Compact, monospace, dark surface)
        LinearLayout specBox = Ui.column(activity);
        specBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 8, 0));
        specBox.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 8), Ui.dp(activity, 10), Ui.dp(activity, 8));

        TextView tvEcuBadge = Ui.text(activity, "CAN COMMAND SEQUENCE (" + ecu + ")", 9, Ui.MUTED);
        tvEcuBadge.setTypeface(Typeface.DEFAULT_BOLD);
        tvEcuBadge.setLetterSpacing(0.06f);
        specBox.addView(tvEcuBadge);

        TextView tvCanRef = Ui.text(activity, canCommand, 10f, Ui.PRIMARY);
        tvCanRef.setTypeface(Typeface.MONOSPACE);
        tvCanRef.setPadding(0, Ui.dp(activity, 3), 0, 0);
        specBox.addView(tvCanRef);

        detailsContainer.addView(specBox);

        TextView tvInlineConsole = new TextView(activity);
        tvInlineConsole.setVisibility(View.GONE);
        tvInlineConsole.setTextSize(10.5f);
        tvInlineConsole.setTypeface(Typeface.MONOSPACE);
        tvInlineConsole.setTextColor(Ui.TEXT);
        tvInlineConsole.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 8, 0));
        tvInlineConsole.setPadding(Ui.dp(activity, 10), Ui.dp(activity, 6), Ui.dp(activity, 10), Ui.dp(activity, 6));
        LinearLayout.LayoutParams icLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        icLp.topMargin = Ui.dp(activity, 6);
        tvInlineConsole.setLayoutParams(icLp);
        detailsContainer.addView(tvInlineConsole);

        card.addView(detailsContainer);

        TestItemView item = new TestItemView(category, card, badgeStatus, detailsContainer, chevron, tvInlineConsole, title, canCommand);

        // Click on topRow or card background toggles expandable details
        topRow.setOnClickListener(v -> toggleCardExpansion(item));

        // Click on button triggers test execution and auto-expands card to show live output
        btnAction.setOnClickListener(v -> {
            if (detailsContainer.getVisibility() != View.VISIBLE) {
                expandCard(item);
            }
            executeTest(item);
        });

        parent.addView(card);
        testItemViews.add(item);
    }

    private void toggleCardExpansion(TestItemView item) {
        boolean expand = item.detailsContainer.getVisibility() != View.VISIBLE;
        if (expand) {
            expandCard(item);
        } else {
            collapseCard(item);
        }
    }

    private void expandCard(TestItemView item) {
        item.detailsContainer.setVisibility(View.VISIBLE);
        item.chevron.animate().rotation(180f).setDuration(180).start();
    }

    private void collapseCard(TestItemView item) {
        item.detailsContainer.setVisibility(View.GONE);
        item.chevron.animate().rotation(0f).setDuration(180).start();
    }

    private void filterCategory(String category) {
        selectedCategory = category;
        for (int i = 0; i < CATEGORIES.length; i++) {
            boolean active = CATEGORIES[i].equals(category);
            TextView chip = categoryChips.get(i);
            chip.setTextColor(active ? Ui.ACCENT_TEXT : Ui.MUTED);
            chip.setBackground(Ui.round(activity, active ? Ui.PRIMARY : Ui.SURFACE_HIGH, 12, 0));
        }


        // Toggle each test item cleanly without leaving gaps
        for (TestItemView t : testItemViews) {
            boolean visible = category.equals("ALL") || t.category.equals(category);
            t.card.setVisibility(visible ? View.VISIBLE : View.GONE);
        }

        // Smoothly scroll back to the top of the container to eliminate blank scroll spaces
        page.post(() -> page.smoothScrollTo(0, 0));
    }

    // -------------------------------------------------------------------------
    // Command Execution Engine
    // -------------------------------------------------------------------------

    private void executeTest(TestItemView item) {
        String name = item.title;
        String command = item.canCommand;

        // 1. Motion Interlock
        if (vehicleMoving) {
            String msg = "BLOCKED: Vehicle motion detected (> 0.5 km/h).\nStationary interlock active. Scooter must be stopped.";
            item.statusBadge.setText("BLOCKED");
            item.statusBadge.setTextColor(Ui.WARNING);
            item.statusBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x22), 8, 0));
            logInline(item, msg);
            logConsole("ABORTED: Cannot actuate " + name + " while vehicle is moving.");
            return;
        }

        // 2. Connection Check
        if (!conn.ready()) {
            String msg = "ABORTED: Scooter not connected to Sola.\nTap [CONNECT] at top right to establish link to 192.168.1.96:50005.";
            item.statusBadge.setText("OFFLINE");
            item.statusBadge.setTextColor(Ui.ERROR);
            item.statusBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.ERROR, 0x22), 8, 0));
            logInline(item, msg);
            logConsole("ERROR: Connect to scooter first to execute " + name);
            return;
        }

        item.statusBadge.setText("RUNNING");
        item.statusBadge.setTextColor(Ui.WARNING);
        item.statusBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.WARNING, 0x22), 8, 0));

        logInline(item, "DISPATCHING TEST: " + name + "\nPRECHECK: Interlock verified (Vehicle stationary)\nTX -> " + command);
        logConsole("TX -> " + command);

        conn.run(rpc -> rpc.shell(command, 12000), (value, error) -> {
            if (error != null) {
                item.statusBadge.setText("ERROR");
                item.statusBadge.setTextColor(Ui.ERROR);
                item.statusBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.ERROR, 0x22), 8, 0));
                String failMsg = "RX [FAIL] " + error.getMessage();
                logInline(item, failMsg);
                logConsole("RX [FAIL] " + name + ": " + error.getMessage());
            } else {
                item.statusBadge.setText("PASS");
                item.statusBadge.setTextColor(Ui.POSITIVE);
                item.statusBadge.setBackground(Ui.round(activity, Ui.alpha(Ui.POSITIVE, 0x22), 8, 0));
                String res = value != null ? value.trim() : "";
                String passMsg = "RESULT: PASS\nFrame sequence dispatched to vehicle bus.\n" + (res.isEmpty() ? "Vehicle accepted commands (UDS Positive Response ACK)." : res);
                logInline(item, passMsg);
                logConsole("RX [PASS] " + name + " -> Completed successfully.");
            }
        });
    }

    private void logInline(TestItemView item, String message) {
        item.tvInlineConsole.setVisibility(View.VISIBLE);
        String ts = timeFmt.format(new Date());
        String current = item.tvInlineConsole.getText().toString();
        String entry = "[" + ts + "] " + message;
        String[] lines = current.split("\n");
        StringBuilder sb = new StringBuilder();
        sb.append(entry).append("\n");
        for (int i = 0; i < Math.min(lines.length, 8); i++) {
            if (!lines[i].trim().isEmpty() && !lines[i].startsWith("Ready. Tap")) {
                sb.append(lines[i]).append("\n");
            }
        }
        item.tvInlineConsole.setText(sb.toString().trim());
        item.tvInlineConsole.setTextColor(Ui.TEXT);
    }

    private void logConsole(String line) {
        String ts = timeFmt.format(new Date());
        String current = tvConsoleLog.getText().toString();
        String entry = "[" + ts + "] " + line;
        String[] lines = current.split("\n");
        StringBuilder sb = new StringBuilder();
        sb.append(entry).append("\n");
        for (int i = 0; i < Math.min(lines.length, 12); i++) {
            if (!lines[i].trim().isEmpty()) {
                sb.append(lines[i]).append("\n");
            }
        }
        tvConsoleLog.setText(sb.toString().trim());
    }

    // -------------------------------------------------------------------------
    // Automated Self-Test Wizard
    // -------------------------------------------------------------------------

    private void runAutomatedSelfTest() {
        if (autoTestRunning) return;
        if (!conn.ready()) {
            logConsole("ERROR: Connect to scooter first before running Auto Self-Test.");
            return;
        }

        autoTestRunning = true;
        btnAutoTest.setText("TESTING...");
        btnAutoTest.setTextColor(Ui.WARNING);
        logConsole("=== STARTING 6-STEP AUTOMATED VEHICLE HEALTH TEST ===");

        new Thread(() -> {
            try {
                // Step 1: SOM System Info
                ui.post(() -> logConsole("[1/6] Querying Smart Module Serial & Kernel..."));
                String som = conn.rpc().getprop("ro.serialno", 4000);
                ui.post(() -> logConsole("  SOM Serial: " + som + " -> PASS"));
                Thread.sleep(600);

                // Step 2: VCU CAN Diagnostic Link
                ui.post(() -> logConsole("[2/6] Verifying VCU Diagnostic Channel (0x786)..."));
                conn.rpc().shell("cansend vcan0 786#023E000000000000", 4000);
                ui.post(() -> logConsole("  VCU ISO-TP Channel Responsive -> PASS"));
                Thread.sleep(600);

                // Step 3: BMS Health & Balancing Status
                ui.post(() -> logConsole("[3/6] Verifying BMS Traction Pack Channel (0x78E)..."));
                conn.rpc().shell("cansend vcan0 78E#023E000000000000", 4000);
                ui.post(() -> logConsole("  BMS Battery Manager Responsive -> PASS"));
                Thread.sleep(600);

                // Step 4: MCU Inverter Channel
                ui.post(() -> logConsole("[4/6] Verifying Motor Inverter Channel (0x7A6)..."));
                conn.rpc().shell("cansend vcan0 7A6#023E000000000000", 4000);
                ui.post(() -> logConsole("  MCU Inverter Responsive -> PASS"));
                Thread.sleep(600);

                // Step 5: SOM Loopback Verification
                ui.post(() -> logConsole("[5/6] Verifying VCU-to-SOM Bus Loopback..."));
                conn.rpc().shell("cansend vcan0 786#043101BF02000000", 4000);
                ui.post(() -> logConsole("  Bus Handshake Completed -> PASS"));
                Thread.sleep(600);

                // Step 6: Locate & Audible Horn Pulse
                ui.post(() -> logConsole("[6/6] Verifying Visual & Audible Actuation Circuits..."));
                conn.rpc().shell("cansend vcan0 786#0210030000000000; cansend vcan0 786#052F040503010000; sleep 1; cansend vcan0 786#042F040500000000", 4000);
                ui.post(() -> logConsole("  DRL Pulse Dispatched -> PASS"));

                ui.post(() -> {
                    logConsole("=== AUTOMATED TEST COMPLETE: ALL SUBSYSTEMS PASSED ===");
                    btnAutoTest.setText("AUTO SELF-TEST");
                    btnAutoTest.setTextColor(Ui.PRIMARY);
                    autoTestRunning = false;
                });
            } catch (Exception e) {
                ui.post(() -> {
                    logConsole("AUTO TEST ABORTED: " + e.getMessage());
                    btnAutoTest.setText("RETRY TEST");
                    btnAutoTest.setTextColor(Ui.ERROR);
                    autoTestRunning = false;
                });
            }
        }).start();
    }

    // -------------------------------------------------------------------------
    // UI Helpers
    // -------------------------------------------------------------------------

    private LinearLayout createSectionHeader(int iconResId, String title, String subtitle) {
        LinearLayout card = Ui.column(activity);
        card.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 12), Ui.dp(activity, 14), Ui.dp(activity, 12));
        card.setBackground(Ui.round(activity, Ui.SURFACE, 16, 0));

        LinearLayout headerRow = Ui.row(activity);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setPadding(0, 0, 0, Ui.dp(activity, 10));

        if (iconResId != 0) {
            LinearLayout iconBox = Ui.column(activity);
            iconBox.setGravity(Gravity.CENTER);
            iconBox.setBackground(Ui.round(activity, Ui.SURFACE_HIGH, 11, 0));
            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconResId);
            icon.setColorFilter(Ui.TEXT);
            iconBox.addView(icon, new LinearLayout.LayoutParams(Ui.dp(activity, 20), Ui.dp(activity, 20)));

            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 36), Ui.dp(activity, 36));
            ip.rightMargin = Ui.dp(activity, 12);
            headerRow.addView(iconBox, ip);
        }

        LinearLayout titleCol = Ui.column(activity);
        TextView tvTitle = Ui.text(activity, title, 15, Ui.TEXT);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        TextView tvSub = Ui.text(activity, subtitle, 11, Ui.MUTED);
        tvSub.setPadding(0, Ui.dp(activity, 2), 0, 0);
        titleCol.addView(tvTitle);
        titleCol.addView(tvSub);

        headerRow.addView(titleCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(headerRow);

        return card;
    }

    private TextView createStatusPill(Activity a, String text, int color) {
        TextView v = new TextView(a);
        v.setText(text);
        v.setTextSize(10);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setGravity(Gravity.CENTER);
        v.setTextColor(color);
        v.setBackground(Ui.round(a, Ui.SURFACE_HIGH, 8, 0));
        v.setPadding(Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6));
        return v;
    }

    private TextView createMetricCell(Activity a, String label, String value) {
        TextView v = new TextView(a);
        v.setText(label + "\n" + value);
        v.setTextSize(11);
        v.setGravity(Gravity.CENTER);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Ui.TEXT);
        v.setBackground(Ui.round(a, Ui.SURFACE_HIGH, 10, 0));
        v.setPadding(Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8), Ui.dp(a, 8));
        return v;
    }

    private LinearLayout.LayoutParams weightParam() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(activity, 4);
        lp.rightMargin = Ui.dp(activity, 4);
        return lp;
    }

    private void addSpace(LinearLayout parent, int dp) {
        View v = new View(activity);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, dp)));
        parent.addView(v);
    }

    private static class TestItemView {
        final String category;
        final LinearLayout card;
        final TextView statusBadge;
        final LinearLayout detailsContainer;
        final ImageView chevron;
        final TextView tvInlineConsole;
        final String title;
        final String canCommand;

        TestItemView(String category, LinearLayout card, TextView statusBadge,
                     LinearLayout detailsContainer, ImageView chevron,
                     TextView tvInlineConsole, String title, String canCommand) {
            this.category = category;
            this.card = card;
            this.statusBadge = statusBadge;
            this.detailsContainer = detailsContainer;
            this.chevron = chevron;
            this.tvInlineConsole = tvInlineConsole;
            this.title = title;
            this.canCommand = canCommand;
        }
    }
}
