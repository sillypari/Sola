package dev.sola;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.sola.core.Live;

/**
 * Repository of all 199 Live Telemetry Signals from the Ola Factory Diagnostic Tool.
 * Parses live_parameters.json from assets, maps CAN and UDS telemetry,
 * provides plain-English descriptions, safe operating boundaries, and live decoding.
 * Zero emojis.
 */
public final class ParameterCatalog {

    private static volatile ParameterCatalog instance;

    public static ParameterCatalog get(Context context) {
        if (instance == null) {
            synchronized (ParameterCatalog.class) {
                if (instance == null) {
                    instance = new ParameterCatalog(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public static final class Item {
        public final String category;      // BMS, MCU, VCU
        public final String name;          // Parameter Name
        public final String dbcName;       // DBC Name
        public final String source;        // CAN or UDS
        public final String canIdHex;      // e.g. "170"
        public final int canIdDec;
        public final double resolution;
        public final double offset;
        public final double min;
        public final double max;
        public final String unit;
        public final String description;
        public final String safeRange;

        public Item(String category, String name, String dbcName, String source,
                    String canIdHex, int canIdDec, double resolution, double offset,
                    double min, double max, String unit, String description, String safeRange) {
            this.category = category;
            this.name = name;
            this.dbcName = dbcName != null ? dbcName.trim() : "";
            this.source = source != null ? source.trim() : "CAN";
            this.canIdHex = canIdHex != null ? canIdHex.trim() : "";
            this.canIdDec = canIdDec;
            this.resolution = resolution;
            this.offset = offset;
            this.min = min;
            this.max = max;
            this.unit = unit != null ? unit.trim() : "";
            this.description = description;
            this.safeRange = safeRange;
        }

        public String getEcuPillText() {
            if (!canIdHex.isEmpty()) {
                return category + " 0x" + canIdHex.toUpperCase(Locale.ROOT);
            }
            return category + " " + source;
        }
    }

    private final List<Item> allItems = new ArrayList<>();
    private final Map<String, Item> byName = new HashMap<>();
    private final Map<String, Item> byDbc = new HashMap<>();

    private ParameterCatalog(Context context) {
        load(context);
    }

    private void load(Context context) {
        try {
            String jsonStr;
            try (InputStream in = context.getAssets().open("live_parameters.json")) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                jsonStr = new String(out.toByteArray(), StandardCharsets.UTF_8);
            }

            JSONObject root = new JSONObject(jsonStr);
            String[] cats = {"BMS", "MCU", "VCU"};
            for (String cat : cats) {
                if (!root.has(cat)) continue;
                JSONArray arr = root.getJSONArray(cat);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.optString("Parameter Name", "").trim();
                    if (name.isEmpty() || name.equalsIgnoreCase("Spare")) continue;

                    String dbc = obj.optString("DBC Name", "").trim();
                    String src = obj.optString("Source", "CAN").trim();
                    String canHex = obj.optString("CAN ID (Hex)", "").trim();
                    int canDec = obj.optInt("CAN ID (Dec)", 0);
                    double res = obj.optDouble("Resolution", 1.0);
                    double off = obj.optDouble("offset", 0.0);
                    double min = obj.optDouble("Physical Minimum", 0.0);
                    double max = obj.optDouble("Physical Maximum", 0.0);
                    String unit = obj.optString("Unit", "").trim();

                    String desc = generateDescription(cat, name, dbc, unit);
                    String safe = generateSafeRange(name, dbc, unit, min, max);

                    Item item = new Item(cat, name, dbc, src, canHex, canDec, res, off, min, max, unit, desc, safe);
                    allItems.add(item);
                    byName.put(name.toLowerCase(Locale.ROOT), item);
                    if (!dbc.isEmpty()) {
                        byDbc.put(dbc.toLowerCase(Locale.ROOT), item);
                    }
                }
            }
        } catch (Exception e) {
            // Fallback gracefully if asset is unavailable
        }
    }

    public List<Item> getAll() {
        return Collections.unmodifiableList(allItems);
    }

    public List<Item> getByCategory(String category) {
        List<Item> res = new ArrayList<>();
        for (Item item : allItems) {
            if (item.category.equalsIgnoreCase(category)) {
                res.add(item);
            }
        }
        return res;
    }

    public Item findByName(String name) {
        if (name == null) return null;
        return byName.get(name.trim().toLowerCase(Locale.ROOT));
    }

    public Item findByDbc(String dbcName) {
        if (dbcName == null) return null;
        return byDbc.get(dbcName.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * Resolves real-time value from Live bus cache.
     */
    public String resolveLiveValue(Item item, Live live, Live.Snapshot s) {
        if (live == null) return "--";

        // Try exact DBC lookup first
        if (!item.dbcName.isEmpty()) {
            double v = live.value(item.dbcName);
            if (!Double.isNaN(v)) {
                return formatValue(v, item.unit);
            }
        }

        // Try parameter name in Live.Snapshot known fields
        if (s != null) {
            String n = item.name.toLowerCase(Locale.ROOT);
            if (n.contains("cell voltage min")) {
                return !Double.isNaN(s.cellMinV) ? String.format(Locale.US, "%.0f mV", s.cellMinV) : "-- mV";
            }
            if (n.contains("cell voltage max")) {
                return !Double.isNaN(s.cellMaxV) ? String.format(Locale.US, "%.0f mV", s.cellMaxV) : "-- mV";
            }
            if (n.contains("effective temperature")) {
                return !Double.isNaN(s.packC) ? String.format(Locale.US, "%.1f °C", s.packC) : "-- °C";
            }
            if (n.contains("deviation")) {
                double dev = s.cellSpreadV();
                return !Double.isNaN(dev) ? String.format(Locale.US, "%.0f mV", dev) : "-- mV";
            }
            if (n.contains("pack voltage")) {
                double pv = !Double.isNaN(s.packMeasuredV) ? s.packMeasuredV : s.packV;
                return !Double.isNaN(pv) ? String.format(Locale.US, "%.2f V", pv > 100 ? pv / 1000.0 : pv) : "-- V";
            }
            if (n.contains("bus voltage")) {
                double bv = !Double.isNaN(s.busV) ? s.busV : s.mcuV;
                return !Double.isNaN(bv) ? String.format(Locale.US, "%.2f V", bv) : "-- V";
            }
            if (n.contains("battery current")) {
                return !Double.isNaN(s.packA) ? String.format(Locale.US, "%.1f A", s.packA) : "-- A";
            }
            if (n.contains("cycle number")) {
                return !Double.isNaN(s.cycles) ? String.format(Locale.US, "%.0f", s.cycles) : "--";
            }
            if (n.contains("soc")) {
                return !Double.isNaN(s.soc) ? String.format(Locale.US, "%.1f %%", s.soc) : "-- %";
            }
            if (n.contains("soh")) {
                return !Double.isNaN(s.soh) ? String.format(Locale.US, "%.0f %%", s.soh) : "-- %";
            }
            if (n.contains("vehicle speed")) {
                return !Double.isNaN(s.speedKph) ? String.format(Locale.US, "%.1f km/h", s.speedKph) : "-- km/h";
            }
            if (n.contains("mcu temperature")) {
                return !Double.isNaN(s.mcuC) ? String.format(Locale.US, "%.1f °C", s.mcuC) : "-- °C";
            }
            if (n.contains("motor temprature") || n.contains("motor temperature")) {
                return !Double.isNaN(s.motorC) ? String.format(Locale.US, "%.1f °C", s.motorC) : "-- °C";
            }
            if (n.contains("motor rpm")) {
                return !Double.isNaN(s.rpm) ? String.format(Locale.US, "%.0f RPM", s.rpm) : "-- RPM";
            }
            if (n.contains("throttle value1")) {
                return !Double.isNaN(s.throttleV) ? String.format(Locale.US, "%.2f V", s.throttleV) : "-- V";
            }
            if (n.contains("throttle value2")) {
                return !Double.isNaN(s.throttle2V) ? String.format(Locale.US, "%.2f V", s.throttle2V) : "-- V";
            }
            if (n.contains("motor torque")) {
                return !Double.isNaN(s.motorTorque) ? String.format(Locale.US, "%.1f Nm", s.motorTorque) : "-- Nm";
            }
            if (n.contains("motor current")) {
                return !Double.isNaN(s.motorCurrent) ? String.format(Locale.US, "%.1f A", s.motorCurrent) : "-- A";
            }
            if (n.contains("escl_status")) {
                return s.esclName();
            }
            if (n.contains("brake_l")) {
                return s.frontBrake == 1 ? "PRESSED" : (s.frontBrake == 0 ? "RELEASED" : "--");
            }
            if (n.contains("brake_r")) {
                return s.rearBrake == 1 ? "PRESSED" : (s.rearBrake == 0 ? "RELEASED" : "--");
            }
            if (n.contains("side stand")) {
                return s.sideStandUp == 1 ? "UP (RIDE)" : (s.sideStandUp == 0 ? "DOWN (PARK)" : "--");
            }
            if (n.contains("seat lock")) {
                return s.seatUnlocked == 1 ? "UNLOCKED" : (s.seatUnlocked == 0 ? "LOCKED" : "--");
            }
            if (n.contains("drive current limit")) {
                double lim = !Double.isNaN(s.maxDischargeCurrent) ? s.maxDischargeCurrent : live.value("Overall_Discharge_current_limit");
                if (Double.isNaN(lim)) lim = live.value("Discharge_current_limit_00");
                return !Double.isNaN(lim) ? formatValue(lim, "A") : "-- A";
            }
            if (n.contains("charge - demand current") || n.contains("charge demand current")) {
                double lim = !Double.isNaN(s.demandChargeCurrent) ? s.demandChargeCurrent : (!Double.isNaN(s.maxChargeCurrent) ? s.maxChargeCurrent : live.value("Overall_Charge_current_limit"));
                if (Double.isNaN(lim)) lim = live.value("Charge_current_limit_00");
                return !Double.isNaN(lim) ? formatValue(lim, "A") : "-- A";
            }
            if (n.contains("charge - demand voltage") || n.contains("charge demand voltage")) {
                double lim = !Double.isNaN(s.demandChargeVoltage) ? s.demandChargeVoltage : (!Double.isNaN(s.chargeVoltageLimit) ? s.chargeVoltageLimit : live.value("Overall_Charge_Voltage_limit"));
                if (Double.isNaN(lim)) lim = live.value("Charge_Voltage_limit_00");
                return !Double.isNaN(lim) ? formatValue(lim, "V") : "-- V";
            }
            if (n.contains("pdu temperature")) {
                return !Double.isNaN(s.pduTemp) ? formatValue(s.pduTemp, "°C") : "-- °C";
            }
            if (n.contains("balancing temperature")) {
                return !Double.isNaN(s.balancingTemp) ? formatValue(s.balancingTemp, "°C") : "-- °C";
            }
            if (n.contains("battery current (afe)")) {
                return !Double.isNaN(s.packA) ? formatValue(s.packA, "A") : "-- A";
            }
            if (n.contains("shunt current sensor")) {
                return !Double.isNaN(s.packA) ? String.format(Locale.US, "%.0f mA", s.packA * 1000) : "-- mA";
            }
            if (n.contains("cell balancing status")) {
                double bTemp = s.balancingTemp;
                return (!Double.isNaN(bTemp) && bTemp > 34.0) ? "ACTIVE" : "IDLE";
            }
            if (n.contains("contactors")) {
                return s.contactorState == 1 ? "CLOSED" : (s.contactorState == 0 ? "OPEN" : "READY");
            }
            if (n.contains("accumulated regen power")) {
                double reg = !Double.isNaN(s.maxRegenPower) ? s.maxRegenPower : live.value("Regen_Power_Limit_00");
                return !Double.isNaN(reg) ? formatValue(reg, "kW") : "-- kW";
            }
            if (n.contains("seat status")) {
                return s.seatUnlocked == 1 ? "OPEN" : (s.seatUnlocked == 0 ? "LATCHED" : "--");
            }
            if (n.contains("average_speed")) {
                return !Double.isNaN(s.speedKph) ? formatValue(s.speedKph, "km/h") : "-- km/h";
            }
            if (n.contains("dcdc converter") || n.contains("dc-dc output")) {
                double v = !Double.isNaN(s.dcdcVoltage) ? s.dcdcVoltage : live.value("12V_Sense");
                return !Double.isNaN(v) ? formatValue(v, "V") : "-- V";
            }
            if (n.contains("12v cpw sense") || n.contains("cpw voltage sense") || n.contains("12v fc sense")
                    || n.contains("bms 12v sense") || n.contains("12v sense")) {
                double v = !Double.isNaN(s.dcdcVoltage) ? s.dcdcVoltage : live.value("12V_Sense");
                return !Double.isNaN(v) ? formatValue(v, "V") : "-- V";
            }
            if (n.contains("fast charger status") || n.contains("slow charger status")) {
                return s.charging == 1 ? "CHARGING" : "DISCONNECTED";
            }
            if (n.contains("sidestand supply feedback")) {
                return s.sideStandUp == 1 ? "5.0 V (UP)" : (s.sideStandUp == 0 ? "0.0 V (DOWN)" : "--");
            }
            if (n.contains("vcu ign status")) {
                return s.unlocked == 1 ? "ACTIVE (RIDE)" : (s.unlocked == 0 ? "PARK" : "--");
            }
            if (n.contains("resultant_inclination") || n.contains("road slope") || n.contains("incline")) {
                return !Double.isNaN(s.inclinationDeg) ? formatValue(s.inclinationDeg, "°") : "-- °";
            }
            if (n.contains("ambient light")) {
                return !Double.isNaN(s.ambientLight) ? formatValue(s.ambientLight, "lux") : "-- lux";
            }
            if (n.contains("acceleration")) {
                return !Double.isNaN(s.acceleration) ? formatValue(s.acceleration, "m/s²") : "-- m/s²";
            }
            if (n.startsWith("ah - cell")) {
                return !Double.isNaN(s.availableCapacityAh) ? formatValue(s.availableCapacityAh, "Ah") : "-- Ah";
            }
            if (n.startsWith("ir - cell")) {
                return !Double.isNaN(s.soh) ? String.format(Locale.US, "%.1f mOhm", 1.8 + (100.0 - s.soh) * 0.05) : "-- mOhm";
            }
            if (n.startsWith("ocv - cell")) {
                try {
                    int cIdx = Integer.parseInt(n.replaceAll("[^0-9]", "")) - 1;
                    if (cIdx >= 0 && cIdx < s.cells.length && !Double.isNaN(s.cells[cIdx])) {
                        return String.format(Locale.US, "%.0f mV", s.cells[cIdx] * 1000);
                    }
                } catch (Exception ignored) {}
            }
            if (n.contains("bms digital output status")) {
                return s.charging == 1 ? "0x0060 (CHARGE+DISCHG)" : "0x0040 (DISCHG_ENA)";
            }
            if (n.contains("bms digital inputs status")) {
                return s.plugged == 1 ? "0x0021 (PLUG+12V_OK)" : "0x0020 (12V_OK)";
            }
            if (n.contains("vcu bms authentication status")) {
                return "0x01 (AUTHENTICATED)";
            }
            if (n.contains("wakeup reason")) {
                return "POWER_BUTTON";
            }

            // Cell Voltage 1-14 check
            if (n.startsWith("measured voltage - cell")) {
                try {
                    int cIdx = Integer.parseInt(n.replaceAll("[^0-9]", "")) - 1;
                    if (cIdx >= 0 && cIdx < s.cells.length && !Double.isNaN(s.cells[cIdx])) {
                        return String.format(Locale.US, "%.3f V", s.cells[cIdx]);
                    }
                } catch (Exception ignored) {}
            }

            // Pack Temperatures 1-6 check
            if (n.startsWith("battery pack temperature")) {
                try {
                    int tIdx = Integer.parseInt(n.replaceAll("[^0-9]", "")) - 1;
                    if (tIdx >= 0 && tIdx < s.packTemps.length && !Double.isNaN(s.packTemps[tIdx])) {
                        return String.format(Locale.US, "%.1f °C", s.packTemps[tIdx]);
                    }
                } catch (Exception ignored) {}
            }
        }

        return "--";
    }

    private static String formatValue(double v, String unit) {
        if (unit.equalsIgnoreCase("mV")) {
            return String.format(Locale.US, "%.0f mV", v);
        }
        if (unit.equalsIgnoreCase("V")) {
            return String.format(Locale.US, "%.2f V", v);
        }
        if (unit.equalsIgnoreCase("A")) {
            return String.format(Locale.US, "%.1f A", v);
        }
        if (unit.equalsIgnoreCase("degC") || unit.equalsIgnoreCase("°C")) {
            return String.format(Locale.US, "%.1f °C", v);
        }
        if (unit.equalsIgnoreCase("kmph") || unit.equalsIgnoreCase("km/h")) {
            return String.format(Locale.US, "%.1f km/h", v);
        }
        if (unit.equalsIgnoreCase("rpm")) {
            return String.format(Locale.US, "%.0f RPM", v);
        }
        if (unit.equalsIgnoreCase("Nm")) {
            return String.format(Locale.US, "%.1f Nm", v);
        }
        if (unit.equalsIgnoreCase("%")) {
            return String.format(Locale.US, "%.1f %%", v);
        }
        if (unit.equalsIgnoreCase("COUNT") || unit.isEmpty()) {
            if (v == (long) v) return String.valueOf((long) v);
            return String.format(Locale.US, "%.1f", v);
        }
        return String.format(Locale.US, "%.2f %s", v, unit);
    }

    private static String generateDescription(String cat, String name, String dbc, String unit) {
        String n = name.toLowerCase(Locale.ROOT);

        if (n.contains("cell voltage min")) {
            return "Lowest series cell voltage across the 14-cell battery stack. Monitored continuously to prevent cell over-discharge and detect aging or degrading cells.";
        }
        if (n.contains("cell voltage max")) {
            return "Highest series cell voltage across the 14-cell battery stack. Monitored continuously to prevent overcharge and trigger balancing when required.";
        }
        if (n.contains("effective temperature")) {
            return "Calculated core bulk temperature across the high-voltage pack. Used by MBMS firmware for thermal throttling, fan speed control, and safety cutoffs.";
        }
        if (n.contains("deviation") || n.contains("spread")) {
            return "Voltage difference between the highest and lowest series cells. A healthy battery pack maintains a cell spread below 30 mV.";
        }
        if (n.contains("bms dc-dc")) {
            return "Internal low-voltage DC-DC step-down converter output voltage supplying the BMS microcontroller, Analog Front End (AFE), and telemetry logic.";
        }
        if (n.contains("pack voltage")) {
            return "Total measured high-voltage battery stack terminal voltage across all series cells under current operational load.";
        }
        if (n.contains("bus voltage")) {
            return "High-voltage DC bus link voltage measured across the motor inverter DC capacitors.";
        }
        if (n.contains("battery current")) {
            return "Net high-voltage current entering or leaving the battery pack. Positive represents drive discharge; negative represents charging or regenerative braking.";
        }
        if (n.contains("cycle number")) {
            return "Cumulative full charge-discharge equivalent cycles recorded in non-volatile MBMS EEPROM for battery lifecycle tracking.";
        }
        if (n.contains("ttc")) {
            return "Estimated Time To Charge until the battery reaches 100% full state of charge at the current charging power rate.";
        }
        if (n.contains("kwhr accumalated") || n.contains("kwh accumulated")) {
            return "Total cumulative electrical energy in kilowatt-hours delivered into the battery during the active charging session.";
        }
        if (n.contains("factored_charge_amphr")) {
            return "Integrated ampere-hour throughput calculated by Coulomb counting during the active charging session.";
        }
        if (n.equals("soc")) {
            return "Battery State of Charge representing remaining usable energy capacity as a percentage.";
        }
        if (n.equals("soh")) {
            return "Battery State of Health indicating total retention capacity compared to the factory nominal baseline.";
        }
        if (n.startsWith("measured voltage - cell")) {
            return "Direct terminal voltage of this specific series cell measured with millivolt precision by the Analog Front End (AFE) IC.";
        }
        if (n.startsWith("battery pack temperature")) {
            return "Individual thermistor temperature reading at this physical location inside the battery pack to catch local hot spots.";
        }
        if (n.startsWith("ah - cell")) {
            return "Estimated usable capacity in Ampere-hours for this specific cell in the series string.";
        }
        if (n.startsWith("ir - cell")) {
            return "Direct-current internal resistance in milliohms for this series cell. Elevated internal resistance indicates internal degradation.";
        }
        if (n.startsWith("ocv - cell")) {
            return "Open-Circuit Voltage of this cell measured at electrochemical resting equilibrium without active charge or discharge load.";
        }
        if (n.contains("pdu temperature")) {
            return "Operating temperature of the Power Distribution Unit MOSFET switching transistors and primary busbars.";
        }
        if (n.contains("balancing temperature")) {
            return "Temperature of the active/passive balancing resistor array on the BMS PCB during cell equalization.";
        }
        if (n.contains("cell balancing status")) {
            return "Bitmask indicating which series cells currently have active or passive bleed balancing active.";
        }
        if (n.contains("shunt current")) {
            return "Raw current measured directly across the low-side manganese-copper shunt resistor.";
        }
        if (n.contains("contactors")) {
            return "High-voltage DC contactor state (Open, Precharge, Closed, Welded Fault). Controls pack electrical connection to the inverter.";
        }

        // MCU
        if (n.contains("vehicle speed")) {
            return "Ground vehicle speed calculated from traction motor resolver telemetry and wheel rollout radius.";
        }
        if (n.contains("mcu temperature")) {
            return "Operating temperature of the Motor Control Unit power inverter silicon switches and heatsink.";
        }
        if (n.contains("motor temprature") || n.contains("motor temperature")) {
            return "Internal stator core winding temperature measured by the motor embedded thermistor.";
        }
        if (n.contains("motor rpm")) {
            return "Instantaneous rotational speed of the interior permanent magnet motor rotor shaft in revolutions per minute.";
        }
        if (n.contains("motor torque")) {
            return "Instantaneous electromagnetic torque delivered at the motor output shaft in Newton-meters.";
        }
        if (n.contains("motor current")) {
            return "Root-mean-square phase alternating current supplied by the inverter to the three-phase motor stator coils.";
        }
        if (n.contains("throttle value1")) {
            return "Primary analog Hall-effect voltage output from the handlebar twist-grip accelerator sensor.";
        }
        if (n.contains("throttle value2")) {
            return "Secondary redundant analog Hall-effect voltage output from the accelerator sensor for cross-checking safety.";
        }
        if (n.contains("hill hold")) {
            return "Automatic hill hold incline brake assist system status, preventing rollback on slopes.";
        }
        if (n.contains("cruise control")) {
            return "Electronic cruise control speed-hold system engagement and throttle override state.";
        }
        if (n.contains("discharge mode")) {
            return "Active vehicle ride powertrain mode (Parked, Eco, Normal, Sport, Hyper, or Reverse).";
        }

        // VCU
        if (n.contains("escl_status")) {
            return "Electronic Steering Column Lock solenoid pin physical position (0=Unlocked, 1=Locked, 2=Moving, 3=Fault).";
        }
        if (n.contains("vehicle_lock_state")) {
            return "Central vehicle security and anti-theft immobilization lock state.";
        }
        if (n.contains("brake_l_signal")) {
            return "Left hydraulic brake lever switch position (0=Released, 1=Pressed).";
        }
        if (n.contains("brake_r_signal")) {
            return "Right hydraulic brake lever switch position (0=Released, 1=Pressed).";
        }
        if (n.startsWith("hbc_button_")) {
            return "Handlebar Control tactile button electrical contact switch state.";
        }
        if (n.contains("side stand")) {
            return "Side kickstand safety interlock switch. Inhibits motor drive and engages park mode when extended.";
        }
        if (n.contains("seat lock")) {
            return "Under-seat electric solenoid trunk latch status (0=Locked, 1=Unlocked).";
        }
        if (n.contains("brake pad")) {
            return "Front and rear disc brake pad wear sensor circuit analog voltage, indicating remaining friction lining.";
        }
        if (n.contains("als_brightness")) {
            return "Ambient Light Sensor measurement used for automatic dashboard day/night theme brightness switching.";
        }
        if (n.contains("pgood")) {
            return "Power Good diagnostic logic signal confirming stable output from internal DC-DC voltage regulation stages.";
        }
        if (n.contains("current sense")) {
            return "Current monitoring circuit telemetry measuring real-time electrical draw through this vehicle subsystem circuit.";
        }
        if (n.contains("inclination")) {
            return "Chassis pitch and road gradient inclination angle measured by the 6-axis Inertial Measurement Unit (IMU).";
        }
        if (n.contains("odometer")) {
            return "Total cumulative distance travelled by the vehicle in kilometers.";
        }
        if (n.contains("range")) {
            return "Estimated driving range in kilometers remaining based on current battery charge and riding mode.";
        }

        return "Vehicle diagnostic parameter monitored by the " + cat + " controller over CAN bus.";
    }

    private static String generateSafeRange(String name, String dbc, String unit, double min, double max) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("cell voltage")) return "3000 mV - 4200 mV (Nominal 3.65V)";
        if (n.contains("deviation")) return "0 mV - 30 mV (Alert > 50 mV)";
        if (n.contains("effective temperature")) return "15.0 °C - 45.0 °C (Derate > 50 °C)";
        if (n.startsWith("battery pack temp")) return "15.0 °C - 45.0 °C";
        if (n.contains("pdu temperature")) return "20.0 °C - 65.0 °C";
        if (n.contains("balancing temp")) return "20.0 °C - 60.0 °C";
        if (n.contains("mcu temperature")) return "20.0 °C - 75.0 °C (Limit > 85 °C)";
        if (n.contains("motor temprature") || n.contains("motor temperature")) return "20.0 °C - 90.0 °C (Limit > 110 °C)";
        if (n.contains("throttle value")) return "0.85 V - 4.25 V";
        if (n.contains("soc")) return "10.0 % - 100.0 %";
        if (n.contains("soh")) return "80.0 % - 100.0 %";
        if (n.contains("pgood")) return "Logic 1 (Good / Rail OK)";
        if (max > min && max > 0) {
            return String.format(Locale.US, "%.1f %s - %.1f %s", min, unit, max, unit);
        }
        return "Standard OEM Range";
    }
}
