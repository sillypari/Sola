package dev.sola;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Master catalog of 201 official factory MoveOS Diagnostic Trouble Codes (DTCs),
 * loaded directly from the factory diagnostic database (dtc_ecu_list.json).
 * Organizes fault codes by ECU (BMS, MCU, VCU), Functional Subsystem
 * (LIGHTING, THERMAL, VOLTAGE, INVERTER, SENSORS, LOCKS, COMMUNICATION, CHARGER),
 * and Severity (CRITICAL, WARNING, INFO).
 * Zero emojis.
 */
public final class DtcCatalog {

    public static final class DtcItem {
        public final String code;          // e.g. "0x920711"
        public final String rawCode;       // e.g. "920711"
        public final String ecu;           // "BMS", "MCU", "VCU"
        public final String title;         // "Head lamp low Short"
        public final String category;      // "LIGHTING", "THERMAL", "VOLTAGE", etc.
        public final String severity;      // "CRITICAL", "WARNING", "INFO"
        public final String explanation;   // Functional technical description
        public final String possibleCause; // Likely root cause in field
        public final String remedy;        // Recommended ASC repair procedure
        public final String udsClearCmd;   // Specific UDS Service 0x14 clear command

        public DtcItem(String code, String rawCode, String ecu, String title,
                       String category, String severity, String explanation,
                       String possibleCause, String remedy, String udsClearCmd) {
            this.code = code;
            this.rawCode = rawCode;
            this.ecu = ecu;
            this.title = title;
            this.category = category;
            this.severity = severity;
            this.explanation = explanation;
            this.possibleCause = possibleCause;
            this.remedy = remedy;
            this.udsClearCmd = udsClearCmd;
        }

        public boolean matches(String query) {
            if (query == null || query.isEmpty()) return true;
            String q = query.toLowerCase(Locale.ROOT);
            return rawCode.toLowerCase(Locale.ROOT).contains(q)
                    || code.toLowerCase(Locale.ROOT).contains(q)
                    || title.toLowerCase(Locale.ROOT).contains(q)
                    || ecu.toLowerCase(Locale.ROOT).contains(q)
                    || category.toLowerCase(Locale.ROOT).contains(q)
                    || explanation.toLowerCase(Locale.ROOT).contains(q);
        }
    }

    private static volatile List<DtcItem> cachedItems = null;

    public static synchronized List<DtcItem> getItems(Context context) {
        if (cachedItems != null) {
            return cachedItems;
        }

        List<DtcItem> list = new ArrayList<>();
        try (InputStream in = context.getAssets().open("dtc_ecu_list.json")) {
            byte[] bytes = new byte[in.available()];
            in.read(bytes);
            String jsonStr = new String(bytes, StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(jsonStr);

            // 1. BMS DTCs
            if (root.has("BMS")) {
                JSONArray bmsArr = root.getJSONArray("BMS");
                for (int i = 0; i < bmsArr.length(); i++) {
                    JSONObject obj = bmsArr.getJSONObject(i);
                    String raw = obj.optString("DTC Code", "");
                    String desc = obj.optString("Description", "");
                    list.add(createDtcItem("BMS", raw, desc));
                }
            }

            // 2. MCU DTCs
            if (root.has("MCU")) {
                JSONArray mcuArr = root.getJSONArray("MCU");
                for (int i = 0; i < mcuArr.length(); i++) {
                    JSONObject obj = mcuArr.getJSONObject(i);
                    String raw = obj.optString("DTC Code", "");
                    String desc = obj.optString("Description", "");
                    list.add(createDtcItem("MCU", raw, desc));
                }
            }

            // 3. VCU DTCs
            if (root.has("VCU")) {
                JSONArray vcuArr = root.getJSONArray("VCU");
                for (int i = 0; i < vcuArr.length(); i++) {
                    JSONObject obj = vcuArr.getJSONObject(i);
                    String raw = obj.optString("DTC Code", "");
                    String desc = obj.optString("Description", "");
                    list.add(createDtcItem("VCU", raw, desc));
                }
            }

            cachedItems = Collections.unmodifiableList(list);
        } catch (Exception e) {
            cachedItems = Collections.emptyList();
        }

        return cachedItems;
    }

    private static DtcItem createDtcItem(String ecu, String rawCode, String desc) {
        String hexCode = "0x" + rawCode.toUpperCase(Locale.ROOT);
        String category;
        String severity;
        String explanation;
        String cause;
        String remedy;
        String udsClear;

        String dLower = desc.toLowerCase(Locale.ROOT);

        if ("BMS".equals(ecu)) {
            udsClear = "cansend vcan0 78E#0414FFFFFF000000";
            if (dLower.contains("cell") || dLower.contains("voltage")) {
                category = "VOLTAGE";
                explanation = "Monitors cell series voltage group balance and Analog Front End (AFE) sensing lines.";
                cause = "Cell voltage tap ribbon connection loose, high contact resistance, or cell group capacity deviation.";
                remedy = "Verify live cell delta in Diagnostics tab. Perform Active Cell Balancing routine or inspect AFE ribbon.";
            } else if (dLower.contains("temp") || dLower.contains("pdu")) {
                category = "THERMAL";
                explanation = "Traction pack thermistor or Power Distribution Unit (PDU) temperature monitor.";
                cause = "High ambient heat, intensive regenerative braking, thermistor NTC sensor disconnect, or PDU MOSFET thermal rise.";
                remedy = "Check 8-sensor heatmap in Diagnostics tab. Allow pack to cool before DC fast charging.";
            } else if (dLower.contains("precharge")) {
                category = "CHARGER";
                explanation = "High-voltage precharge circuit protecting DC bus capacitors before main contactor closes.";
                cause = "Precharge resistor open, precharge FET gate driver failure, or DC bus short.";
                remedy = "Inspect BMS precharge circuit and motor controller DC bus input capacitance.";
            } else if (dLower.contains("current") || dLower.contains("mosfet") || dLower.contains("short")) {
                category = "CURRENT & FETS";
                explanation = "Battery pack overcurrent protection and solid-state high-voltage MOSFET switches.";
                cause = "Current transducer out of calibration, short circuit downstream, or MOSFET gate punch-through.";
                remedy = "Clear overload latch using BMS Overload Clear routine after verifying inverter DC bus.";
            } else {
                category = "COMMUNICATION";
                explanation = "Internal BMS logic, AFE communications, or VCU-to-BMS CAN bus frame reception.";
                cause = "CAN bus noise, low 12V auxiliary supply, or micro-controller watchdog reset.";
                remedy = "Verify 12V auxiliary rail voltage and CAN terminating resistance.";
            }
        } else if ("MCU".equals(ecu)) {
            udsClear = "cansend vcan0 7A6#0414FFFFFF000000";
            if (dLower.contains("hall") || dLower.contains("resolver") || dLower.contains("encoder")) {
                category = "SENSORS";
                explanation = "Motor rotor position tracking and 3-phase Hall effect feedback loop.";
                cause = "Resolver connector loose, Hall sensor harness pinched at swingarm pivot, or phase misalignment.";
                remedy = "Inspect motor signal harness. Run Motor FOC Auto-Tune routine to re-calibrate resolver offset.";
            } else if (dLower.contains("temp")) {
                category = "THERMAL";
                explanation = "Motor stator winding thermistor and inverter power module junction temperature.";
                cause = "Sustained high torque output, hill climb, or stator thermistor signal open circuit.";
                remedy = "Inspect stator thermistor resistance (~10k Ohm at 25C). Check motor cooling airflow.";
            } else if (dLower.contains("throttle") || dLower.contains("wiper")) {
                category = "SENSORS";
                explanation = "Dual-channel handlebar electronic throttle potentiometer / hall sensor monitor.";
                cause = "Throttle wiper signal mismatch, voltage outside 0.5V-4.5V range, or water ingress in handlebar pod.";
                remedy = "Verify Throttle A/B voltage spread in Testing tab. Inspect handlebar connector.";
            } else {
                category = "INVERTER";
                explanation = "Field-Oriented Control (FOC) motor inverter stage, gate drivers, and phase current sensors.";
                cause = "Software overcurrent from abrupt torque transient, motor stall, or low DC bus rail.";
                remedy = "Inspect phase U/V/W terminal torque. Verify DC bus capacitor voltage in Diagnostics tab.";
            }
        } else {
            // VCU
            udsClear = "cansend vcan0 786#0414FFFFFF000000";
            if (dLower.contains("lamp") || dLower.contains("light") || dLower.contains("indicator")) {
                category = "LIGHTING";
                explanation = "Body high-side 12V solid-state driver switch for vehicle external lighting.";
                cause = "LED harness wiring pinched against chassis, moisture in lamp assembly, or LED driver overcurrent.";
                remedy = "Inspect lamp wiring and ground return. Test circuit actuation in Testing tab.";
            } else if (dLower.contains("lock") || dLower.contains("escl")) {
                category = "LOCKS";
                explanation = "Electronic steering lock (ESCL) or motorized seat latch solenoid driver.";
                cause = "Mechanical latch binding, solenoid plunger obstruction, or harness short.";
                remedy = "Check mechanical alignment. Test latch release pulse in Testing tab.";
            } else if (dLower.contains("horn")) {
                category = "AUDIO";
                explanation = "Electromagnetic horn 12V relay and power driver circuit.";
                cause = "Horn terminal corrosion, loose ground eyelet, or driver FET overload.";
                remedy = "Inspect horn bracket grounding and test with 300ms pulse in Testing tab.";
            } else if (dLower.contains("sidestand") || dLower.contains("brake_pad") || dLower.contains("hbc") || dLower.contains("usb")) {
                category = "SENSORS";
                explanation = "Chassis switch excitation rails, brake pad wear sensors, and handlebar controller pod.";
                cause = "Side stand hall sensor gap out of tolerance, worn brake pads, or LIN bus wire fault.";
                remedy = "Verify sensor state in Testing tab. Check brake pad friction material thickness.";
            } else {
                category = "COMMUNICATION";
                explanation = "Inter-ECU communication gateway between VCU, Smart Module (SOM), and display cluster.";
                cause = "CAN/LIN bus frame drops, Android Smart Module sleep delay, or loose diagnostic port.";
                remedy = "Execute Bus loopback diagnostics in Testing tab and clear fault history.";
            }
        }

        // Determine Severity
        if (dLower.contains("permanent fault") || dLower.contains("failure") || dLower.contains("short")
                || dLower.contains("stall") || dLower.contains("overcurrent") || dLower.contains("grade 3")) {
            severity = "CRITICAL";
        } else if (dLower.contains("warning") || dLower.contains("out of range") || dLower.contains("open")
                || dLower.contains("abnormal") || dLower.contains("grade 2")) {
            severity = "WARNING";
        } else {
            severity = "INFO";
        }

        return new DtcItem(hexCode, rawCode, ecu, desc, category, severity,
                explanation, cause, remedy, udsClear);
    }
}
