package dev.sola.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Everything the app learns from the bus, kept in one place.
 *
 * <p>Feed it frames with {@link #onFrame}; read it with {@link #snapshot}.
 * Values stay {@link Double#NaN} until a frame carrying them arrives, so "not
 * reported yet" and "zero" never look the same.
 *
 * <p>This is the Java equivalent of Axiom's parser + interpreter + stream
 * manager, minus the Android and coroutine parts.
 */
public final class Live {

    /** How many frames the log keeps. Enough to scroll, small enough to be cheap. */
    public static final int LOG_MAX = 500;

    /** One logged frame. */
    public static final class Entry {
        public final long at;
        public final int id;
        public final String name;
        public final String hex;

        Entry(long at, int id, String name, String hex) {
            this.at = at;
            this.id = id;
            this.name = name;
            this.hex = hex;
        }
    }

    /** A consistent read of the current state. */
    public static final class Snapshot {
        public long frames;
        public int pps;
        public int messagesSeen;
        public int signalsSeen;
        public long lastFrameMs;
        public long firstFrameMs;

        /** Last frame from a vehicle ECU, ignoring the telematics sleep trickle. */
        public long vehicleAt;

        /** Last frame that actually carried SOH. */
        public long sohAt;

        public double soc = Double.NaN;
        public double displaySoc = Double.NaN;
        public double soh = Double.NaN;
        public double sohCalculated = Double.NaN;
        public double cycleSoh = Double.NaN;
        public double totalAvailableEnergyWh = Double.NaN;
        public double nominalPackKwh = 2.98;
        public double speedKph = Double.NaN;
        public double rangeKm = Double.NaN;
        public double rangeEcoKm = Double.NaN;
        public double rangeNormalKm = Double.NaN;
        public double rangeSportKm = Double.NaN;
        public double rangeHyperKm = Double.NaN;
        public double odoKm = Double.NaN;
        public double packV = Double.NaN;
        public double packA = Double.NaN;
        public double mcuV = Double.NaN;
        public double imbalanceV = Double.NaN;
        public double cellMinV = Double.NaN;
        public double cellMaxV = Double.NaN;
        public double packC = Double.NaN;
        public double mcuC = Double.NaN;
        public double motorC = Double.NaN;
        public double rpm = Double.NaN;
        public double throttleV = Double.NaN;
        public double chargeRate = Double.NaN;
        public double cycles = Double.NaN;
        public int charging = -1;
        public int plugged = -1;
        public int sideStandUp = -1;
        public int seatUnlocked = -1;
        public int unlocked = -1;
        public int fallState = -1;
        public int towState = -1;
        public int regenState = -1;
        public int hillState = -1;
        public String driveMode = "";
        public final double[] cells = new double[14];
        public final boolean[] balancing = new boolean[14];
        public double balancingTemp = Double.NaN;
        public int frontBrake = -1;
        public int rearBrake = -1;
        public double brakeRegen = Double.NaN;
        public double totalChargeTime = Double.NaN;
        public double sessionKwh = Double.NaN;
        public double sessionAh = Double.NaN;
        public double timeToCharge80 = Double.NaN;
        public double timeToCharge100 = Double.NaN;
        public double motorTorque = Double.NaN;
        public double motorCurrent = Double.NaN;
        public double motorFreq = Double.NaN;

        // Energy & usable capacity
        public double availableCapacityAh = Double.NaN;
        public double totalEnergyKwh = Double.NaN;
        public double instantEnergyKwh = Double.NaN;
        public double cycleKwh = Double.NaN;
        public double tripDistanceKm = Double.NaN;

        // Aux & DC-DC (12V)
        public double dcdcVoltage = Double.NaN;
        public int auxBatteryCharging = -1;
        public double auxIdleDrainAh = Double.NaN;

        // BMS Safe Operating Envelope & Current Limits
        public double maxDischargeCurrent = Double.NaN;
        public double maxChargeCurrent = Double.NaN;
        public double maxDischargePower = Double.NaN;
        public double maxRegenPower = Double.NaN;
        public double chargeVoltageLimit = Double.NaN;
        public int contactorState = -1;

        // Motion, IMU & Incline
        public double acceleration = Double.NaN;
        public double inclinationDeg = Double.NaN;
        public double gradientX = Double.NaN;
        public double gradientY = Double.NaN;
        public double gradientZ = Double.NaN;
        public double ambientLight = Double.NaN;
        public double gyroTheta = Double.NaN;
        public double magnetometerZ = Double.NaN;

        // Safety, Theft & Interlocks
        public int hillHoldResponse = -1;
        public int regenStatus = -1;
        public int leftBrakeCutoff = -1;
        public int rightBrakeCutoff = -1;

        // Dual Throttle & Motor Stator Resolver
        public double throttle2V = Double.NaN;
        public double idleThrottleCount = Double.NaN;
        public double driveStartThrottleCount = Double.NaN;
        public double positionCounter = Double.NaN;
        public double rotorAngle = Double.NaN;

        // Fast Charging & Hypercharger
        public double timeToChargeFC80 = Double.NaN;
        public double timeToChargeFC100 = Double.NaN;
        public double chargerAvailVolt = Double.NaN;
        public double chargerAvailCurrent = Double.NaN;

        // Lifetime Balancing Analytics
        public double lifetimeBalancingCount = Double.NaN;
        public double lifetimeBalancingDurationMin = Double.NaN;
        public double lifetimeBalancingAhLost = Double.NaN;
        public double maxCellDevDischarge = Double.NaN;
        public double maxCellDevCharge = Double.NaN;

        // ECU Hardware & Firmware Registry
        public String mbmsFw = "";
        public String mbmsBl = "";
        public String vcuFw = "";
        public String vcuBl = "";
        public String huFw = "";
        public String huBl = "";
        public String displaySw = "";
        public String tcuSw = "";
        public String wifiBtSw = "";
        public int esclStatus = -1;

        // Pack Thermal Map & PDU
        public final double[] packTemps = new double[6];
        public double pduTemp = Double.NaN;
        public double pduDeltaTemp = Double.NaN;
        public double packMeasuredV = Double.NaN;
        public double busV = Double.NaN;

        // Chassis Rails & Current Senses
        public int pgood3v8 = -1;
        public int pgood5v0 = -1;
        public int pgoodLdo = -1;
        public double brakePadStatus = Double.NaN;
        public double demandChargeCurrent = Double.NaN;
        public double demandChargeVoltage = Double.NaN;

        public List<Faults.Fault> activeFaults = Collections.emptyList();

        public Snapshot() {
            for (int i = 0; i < cells.length; i++) {
                cells[i] = Double.NaN;
            }
            for (int i = 0; i < packTemps.length; i++) {
                packTemps[i] = Double.NaN;
            }
        }

        public double powerW() {
            if (Double.isNaN(packV) || Double.isNaN(packA)) {
                return Double.NaN;
            }
            return packV * packA;
        }

        public double cellSpreadV() {
            if (Double.isNaN(cellMinV) || Double.isNaN(cellMaxV)) {
                return Double.NaN;
            }
            return cellMaxV - cellMinV;
        }

        public int cellCount() {
            int n = 0;
            for (double v : cells) {
                if (!Double.isNaN(v) && v > 0) {
                    n++;
                }
            }
            return n;
        }

        public boolean empty() {
            return frames == 0;
        }

        /**
         * True when frames are arriving but none of them are vehicle data.
         * The telematics unit keeps a five-ID trickle alive while the scooter
         * sleeps; treating that as "live" is what made a stale pack look
         * healthy or degraded.
         */
        public boolean vehicleStale(long nowMs, long ms) {
            return frames > 0 && (vehicleAt == 0 || nowMs - vehicleAt > ms);
        }

        /** True when SOH was seen once but has not been refreshed since. */
        public boolean sohStale(long nowMs, long ms) {
            return sohAt > 0 && nowMs - sohAt > ms;
        }

        // ---------------------------------------------------------- safety

        public String fallName() {
            switch (fallState) {
                case 1: return "active";
                case 2: return "inactive";
                case 3: return "recovered";
                case 0: return "off (SNA)";
                default: return "unknown";
            }
        }

        public String towName() {
            switch (towState) {
                case 1: return "active";
                case 2: return "inactive";
                case 0: return "off (SNA)";
                default: return "unknown";
            }
        }

        public String regenName() {
            switch (regenState) {
                case 1: return "available";
                case 2: return "throttle limited";
                case 0: return "not available";
                default: return "unknown";
            }
        }

        public String hillName() {
            switch (hillState) {
                case 1: return "unavailable";
                case 2: return "active";
                case 3: return "not active";
                case 6: return "activated";
                case 0: return "unknown (SNA)";
                default: return "state " + hillState;
            }
        }

        public String contactorName() {
            switch (contactorState) {
                case 0: return "OPEN";
                case 1: return "PRECHARGE";
                case 2: return "CLOSED";
                case 3: return "WELDED FAULT";
                default: return contactorState < 0 ? "--" : "STATE " + contactorState;
            }
        }

        public String esclName() {
            switch (esclStatus) {
                case 0: return "UNLOCKED";
                case 1: return "LOCKED";
                case 2: return "MOVING";
                case 3: return "FAULT";
                default: return esclStatus < 0 ? "--" : "STATE " + esclStatus;
            }
        }
    }

    private static final String[] CELL_VOLTAGE_NAMES = new String[28];
    private static final String[] BALANCING_STATUS_NAMES = new String[28];
    private static final String[] BMS00_PACK_TEMP_NAMES = new String[4];

    static {
        for (int i = 0; i < 28; i++) {
            CELL_VOLTAGE_NAMES[i] = "Cell_Voltage_Cell_" + (i + 1);
            BALANCING_STATUS_NAMES[i] = "Balancing_Status_Battery_Cell_" + (i + 1);
        }
        for (int i = 0; i < 4; i++) {
            BMS00_PACK_TEMP_NAMES[i] = "BMS00_Pack_Temperature_0" + (i + 1);
        }
    }

    private static final class RawLogEntry {
        long at;
        int id;
        String name;
        int dlc;
        final byte[] data = new byte[8];
    }

    private static final class IsoTpAssembler {
        int expectedLen = 0;
        int receivedLen = 0;
        final byte[] buffer = new byte[4096];
    }

    private final Dbc dbc;
    private final Faults faults;

    private final Map<String, Double> values = new LinkedHashMap<>(256);
    private final Map<String, Long> valueAt = new LinkedHashMap<>(256);
    private final long[] fastLastSeen = new long[0x800];
    private final int[] fastCounts = new int[0x800];
    private final Map<Integer, Long> extendedLastSeen = new HashMap<>();
    private final Map<Integer, Integer> extendedCounts = new HashMap<>();
    private int uniqueMessagesSeen = 0;

    private final Map<String, Faults.Fault> activeFaultsMap = new LinkedHashMap<>();
    private final Map<String, Faults.Fault> latched = new LinkedHashMap<>();

    private final RawLogEntry[] logRing = new RawLogEntry[LOG_MAX];
    private int logHead = 0;
    private int logCount = 0;

    private final Map<Integer, IsoTpAssembler> udsAssemblers = new HashMap<>();
    private volatile Snapshot publishedSnapshot = new Snapshot();
    private volatile boolean dirty = true;

    private long frames;
    private long firstFrameMs;
    private long lastFrameMs;
    private long vehicleAt;
    private long windowStartMs;
    private int windowFrames;
    private int pps;

    public Live(Dbc dbc, Faults faults) {
        this.dbc = dbc;
        this.faults = faults;
        for (int i = 0; i < LOG_MAX; i++) {
            logRing[i] = new RawLogEntry();
        }
    }

    public Dbc dbc() {
        return dbc;
    }

    public Faults faults() {
        return faults;
    }

    /** Decodes one frame and merges it into the state. Optimized zero-allocation hot path. */
    public synchronized void onFrame(Can.Frame frame) {
        if (frame == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Dbc.Message m = dbc.message(frame.id);
        if (m != null && frame.data != null) {
            byte[] payload = frame.data;
            for (Dbc.Signal s : m.signals) {
                double val = s.value(payload);
                values.put(s.name, val);
                valueAt.put(s.name, now);
                if (val >= 1.0) {
                    Faults.Fault f = faults.forSignal(s.name);
                    if (f != null) {
                        activeFaultsMap.put(f.id, f);
                        latched.put(f.id, f);
                    }
                } else {
                    Faults.Fault f = faults.forSignal(s.name);
                    if (f != null) {
                        activeFaultsMap.remove(f.id);
                    }
                }
            }
        }

        if (frame.id == 0x7B0 || frame.id == 0x7B1 || frame.id == 0x7B4) {
            parseUdsFrame(frame.id, frame.data, now);
            vehicleAt = now;
        }

        frames++;
        if (firstFrameMs == 0) {
            firstFrameMs = now;
            windowStartMs = now;
        }
        lastFrameMs = now;
        if (!sleepTrickle(frame.id)) {
            vehicleAt = now;
        }

        if (frame.id >= 0 && frame.id < 0x800) {
            fastLastSeen[frame.id] = now;
            if (fastCounts[frame.id]++ == 0) {
                uniqueMessagesSeen++;
            }
        } else {
            extendedLastSeen.put(frame.id, now);
            Integer c = extendedCounts.get(frame.id);
            if (c == null) {
                extendedCounts.put(frame.id, 1);
                uniqueMessagesSeen++;
            } else {
                extendedCounts.put(frame.id, c + 1);
            }
        }

        windowFrames++;
        long elapsed = now - windowStartMs;
        if (elapsed >= 1000) {
            pps = (int) ((windowFrames * 1000L) / elapsed);
            windowFrames = 0;
            windowStartMs = now;
        }

        // Zero-allocation circular log ring
        RawLogEntry le = logRing[logHead];
        le.at = now;
        le.id = frame.id;
        le.name = m == null ? "Unknown" : m.name;
        le.dlc = frame.dlc;
        if (frame.data != null) {
            System.arraycopy(frame.data, 0, le.data, 0, Math.min(8, frame.data.length));
        }
        logHead = (logHead + 1) % LOG_MAX;
        if (logCount < LOG_MAX) {
            logCount++;
        }
        dirty = true;
    }

    /** Forgets everything, e.g. after a reconnect. */
    public synchronized void reset() {
        values.clear();
        valueAt.clear();
        extendedLastSeen.clear();
        extendedCounts.clear();
        Arrays.fill(fastLastSeen, 0L);
        Arrays.fill(fastCounts, 0);
        uniqueMessagesSeen = 0;
        activeFaultsMap.clear();
        latched.clear();
        udsAssemblers.clear();
        logHead = 0;
        logCount = 0;
        frames = 0;
        firstFrameMs = 0;
        lastFrameMs = 0;
        vehicleAt = 0;
        windowStartMs = 0;
        windowFrames = 0;
        pps = 0;
        publishedSnapshot = new Snapshot();
        dirty = true;
    }

    public synchronized boolean hasData() {
        return frames > 0;
    }

    /** All signal values, latest frame wins. */
    public synchronized Map<String, Double> values() {
        return new LinkedHashMap<>(values);
    }

    public synchronized double value(String signal) {
        Double v = values.get(signal);
        return v == null ? Double.NaN : v;
    }

    /** Frame counts per CAN id, generated on-demand without per-frame allocations. */
    public synchronized Map<Integer, Integer> counts() {
        Map<Integer, Integer> out = new LinkedHashMap<>(uniqueMessagesSeen + 16);
        for (int i = 0; i < 0x800; i++) {
            if (fastCounts[i] > 0) {
                out.put(i, fastCounts[i]);
            }
        }
        if (!extendedCounts.isEmpty()) {
            out.putAll(extendedCounts);
        }
        return out;
    }

    public synchronized long lastSeenMs(int canId) {
        if (canId >= 0 && canId < 0x800) {
            return fastLastSeen[canId];
        }
        Long t = extendedLastSeen.get(canId);
        return t == null ? 0 : t;
    }

    /** When a signal last arrived, or 0 if it never has. */
    public synchronized long valueAtMs(String signal) {
        Long t = valueAt.get(signal);
        return t == null ? 0 : t;
    }

    /**
     * The five IDs the telematics unit keeps alive while the vehicle sleeps.
     * They carry GPS/LTE/Wi-Fi state, not battery or motor health.
     */
    private static boolean sleepTrickle(int id) {
        switch (id) {
            case 0x6D2:
            case 0x643:
            case 0x6DB:
            case 0x66D:
            case 0x65D:
                return true;
            default:
                return false;
        }
    }

    private void parseUdsFrame(int canId, byte[] data, long now) {
        if (data == null || data.length < 2) return;
        vehicleAt = now;

        int pci = data[0] & 0xFF;
        int pciType = (pci >> 4) & 0x0F;
        byte[] payload = null;

        if (pciType == 0) { // Single Frame
            int len = pci & 0x0F;
            if (len > 0 && data.length >= 1 + len) {
                payload = Arrays.copyOfRange(data, 1, 1 + len);
            }
        } else if (pciType == 1) { // First Frame
            int totalLen = ((pci & 0x0F) << 8) | (data[1] & 0xFF);
            IsoTpAssembler as = udsAssemblers.get(canId);
            if (as == null) {
                as = new IsoTpAssembler();
                udsAssemblers.put(canId, as);
            }
            as.expectedLen = totalLen;
            as.receivedLen = 0;
            int firstChunk = data.length - 2;
            if (firstChunk > 0) {
                System.arraycopy(data, 2, as.buffer, 0, Math.min(firstChunk, as.buffer.length));
                as.receivedLen = firstChunk;
            }
            return;
        } else if (pciType == 2) { // Consecutive Frame
            IsoTpAssembler as = udsAssemblers.get(canId);
            if (as != null && as.expectedLen > 0) {
                int chunk = data.length - 1;
                int remaining = as.expectedLen - as.receivedLen;
                int toCopy = Math.min(chunk, remaining);
                if (toCopy > 0 && as.receivedLen + toCopy <= as.buffer.length) {
                    System.arraycopy(data, 1, as.buffer, as.receivedLen, toCopy);
                    as.receivedLen += toCopy;
                }
                if (as.receivedLen >= as.expectedLen) {
                    payload = Arrays.copyOf(as.buffer, as.expectedLen);
                    as.expectedLen = 0;
                    as.receivedLen = 0;
                } else {
                    return;
                }
            } else {
                return;
            }
        } else {
            return;
        }

        if (payload == null || payload.length < 1) return;
        decodeUdsPayload(canId, payload, now);
    }

    private void decodeUdsPayload(int canId, byte[] payload, long now) {
        int sid = payload[0] & 0xFF;
        int offset = 1;

        // Positive Response to 0x10 (DiagnosticSessionControl) -> SID 0x50
        if (sid == 0x50) {
            values.put("UDS_Session_Active", 1.0);
            valueAt.put("UDS_Session_Active", now);
            return;
        }

        // Positive Response to 0x3E (TesterPresent) -> SID 0x7E
        if (sid == 0x7E) {
            values.put("UDS_Tester_Present_Ack", 1.0);
            valueAt.put("UDS_Tester_Present_Ack", now);
            return;
        }

        // Positive Response to 0x22 (ReadDataByIdentifier) -> SID 0x62
        if (sid == 0x62 && payload.length >= offset + 2) {
            int did = ((payload[offset] & 0xFF) << 8) | (payload[offset + 1] & 0xFF);
            int dataStart = offset + 2;

            if (canId == 0x7B1) { // BMS Responses
                switch (did) {
                    case 0xE100: // Battery Information 1
                        if (payload.length >= dataStart + 4) {
                            int minMv = (payload[dataStart] & 0xFF) | ((payload[dataStart + 1] & 0xFF) << 8);
                            int maxMv = (payload[dataStart + 2] & 0xFF) | ((payload[dataStart + 3] & 0xFF) << 8);
                            if (minMv > 2000 && minMv < 4600) {
                                values.put("BMS0_Cell_Min_Voltage", minMv / 1000.0);
                                values.put("Cell_Voltage_Min", minMv / 1000.0);
                                valueAt.put("BMS0_Cell_Min_Voltage", now);
                            }
                            if (maxMv > 2000 && maxMv < 4600) {
                                values.put("BMS0_Cell_Max_Voltage", maxMv / 1000.0);
                                values.put("Cell_Voltage_Max", maxMv / 1000.0);
                                valueAt.put("BMS0_Cell_Max_Voltage", now);
                            }
                            if (maxMv >= minMv) {
                                double deltaMv = maxMv - minMv;
                                values.put("Delta_Voltage_00", deltaMv / 1000.0);
                                values.put("Cell_Voltage_Deviation", deltaMv);
                                valueAt.put("Delta_Voltage_00", now);
                            }
                        }
                        break;

                    case 0xE103: // Battery Status (SOC and SOH)
                        if (payload.length >= dataStart + 4) {
                            int rawSoc = (payload[dataStart] & 0xFF) | ((payload[dataStart + 1] & 0xFF) << 8);
                            int rawSoh = (payload[dataStart + 2] & 0xFF) | ((payload[dataStart + 3] & 0xFF) << 8);
                            double socVal = rawSoc * 0.01;
                            double sohVal = rawSoh * 0.01;
                            if (socVal >= 0.0 && socVal <= 100.0) {
                                values.put("State_Of_Charge", socVal);
                                values.put("Display_SoC", socVal);
                                valueAt.put("Display_SoC", now);
                            }
                            if (sohVal > 0.0 && sohVal <= 100.0) {
                                values.put("Pack_SoH", sohVal);
                                values.put("SOH_Disp", sohVal);
                                valueAt.put("Pack_SoH", now);
                            }
                        }
                        break;

                    case 0xE301: // Bus Voltage After Contactor
                        if (payload.length >= dataStart + 2) {
                            int rawV = (payload[dataStart] & 0xFF) | ((payload[dataStart + 1] & 0xFF) << 8);
                            double v = rawV * 0.01;
                            if (v > 20.0 && v < 70.0) {
                                values.put("Bus_Voltage_00", v);
                                values.put("Bus_Voltage_After_Contactor", v);
                                valueAt.put("Bus_Voltage_00", now);
                            }
                        }
                        break;
                }
            }
        }
    }

    /** Newest first. Formatted on-demand from raw ring buffer to eliminate hot path allocations. */
    public synchronized List<Entry> log() {
        List<Entry> out = new ArrayList<>(logCount);
        for (int i = 0; i < logCount; i++) {
            int idx = (logHead - 1 - i + LOG_MAX) % LOG_MAX;
            RawLogEntry r = logRing[idx];
            out.add(new Entry(r.at, r.id, r.name, Can.Frame.formatHex(r.data, r.dlc)));
        }
        return out;
    }

    /** Faults that have ever been seen. The table is not cleared by a 0. */
    public synchronized List<Faults.Fault> latchedFaults() {
        return new ArrayList<>(latched.values());
    }

    /** Faults whose signal is 1 or more in latest state. Incremental O(1) lookup. */
    public synchronized List<Faults.Fault> activeFaults() {
        return new ArrayList<>(activeFaultsMap.values());
    }

    public Snapshot snapshot() {
        if (!dirty) {
            return publishedSnapshot;
        }
        synchronized (this) {
            if (!dirty) {
                return publishedSnapshot;
            }
            Snapshot s = buildSnapshot();
            publishedSnapshot = s;
            dirty = false;
            return s;
        }
    }

    private Snapshot buildSnapshot() {
        Snapshot s = new Snapshot();
        s.frames = frames;
        s.pps = pps;
        s.messagesSeen = uniqueMessagesSeen;
        s.signalsSeen = values.size();
        s.firstFrameMs = firstFrameMs;
        s.lastFrameMs = lastFrameMs;
        s.vehicleAt = vehicleAt;

        // High-precision BMS State of Charge (0.01% precision from Pack_SOC_00 / Overall_SOC)
        double preciseSoc = value("Pack_SOC_00");
        if (Double.isNaN(preciseSoc) || preciseSoc <= 0.0) {
            preciseSoc = value("Overall_SOC");
        }
        if (!Double.isNaN(preciseSoc) && preciseSoc > 0.0) {
            s.soc = preciseSoc;
        } else {
            s.soc = value("Display_SoC");
            if (Double.isNaN(s.soc)) {
                s.soc = value("State_Of_Charge");
            }
        }
        s.displaySoc = value("Display_SoC");
        if (Double.isNaN(s.displaySoc)) {
            s.displaySoc = s.soc;
        }

        // On MoveOS / Gen 1, SOH_Disp is often 0 or not populated, while Pack_SoH (0x150)
        // carries the real BMS state of health. Prefer Pack_SoH if valid (> 0).
        double packSoh = value("Pack_SoH");
        if (!Double.isNaN(packSoh) && packSoh > 0) {
            s.soh = packSoh;
            s.sohAt = valueAtMs("Pack_SoH");
        } else {
            s.soh = value("SOH_Disp");
            s.sohAt = valueAtMs("SOH_Disp");
            if ((Double.isNaN(s.soh) || s.soh <= 0) && !Double.isNaN(packSoh)) {
                s.soh = packSoh;
                s.sohAt = valueAtMs("Pack_SoH");
            }
        }
        s.speedKph = value("Vehicle_speed_in_kmph");
        s.rangeKm = value("Vehicle_Range");
        s.rangeEcoKm = value("Vehicle_Range_Limp");
        s.rangeNormalKm = value("Vehicle_Range_Normal");
        s.rangeSportKm = value("Vehicle_Range_Sport");
        s.rangeHyperKm = value("Vehicle_Range_Hyper");
        s.odoKm = value("Odometer");
        s.packV = value("Bus_Voltage_00");
        if (Double.isNaN(s.packV)) {
            s.packV = value("Bus_Voltage_After_Contactor");
        }
        // Battery current: Battery_Current_00 (0x140) or Overall_Battery_Current (0x120)
        // captures real pack charge/discharge. MCU_DC_Current only measures motor draw.
        double battA = value("Battery_Current_00");
        if (Double.isNaN(battA)) {
            battA = value("Overall_Battery_Current");
        }
        if (Double.isNaN(battA)) {
            battA = value("MCU_DC_Current");
        }
        s.packA = battA;
        s.mcuV = value("MCU_Voltage");
        s.cellMinV = value("BMS0_Cell_Min_Voltage");
        if (Double.isNaN(s.cellMinV)) {
            s.cellMinV = value("Cell_Voltage_Min");
        }
        s.cellMaxV = value("BMS0_Cell_Max_Voltage");
        if (Double.isNaN(s.cellMaxV)) {
            s.cellMaxV = value("Cell_Voltage_Max");
        }
        s.imbalanceV = value("Delta_Voltage_00");
        if (Double.isNaN(s.imbalanceV)) {
            s.imbalanceV = value("Cell_Voltage_Deviation");
        }
        if (Double.isNaN(s.imbalanceV) && !Double.isNaN(s.cellMaxV) && !Double.isNaN(s.cellMinV)) {
            s.imbalanceV = s.cellMaxV - s.cellMinV;
        }
        s.packC = value("Effective_Battery_Temperature_00");
        if (Double.isNaN(s.packC)) {
            s.packC = value("Battery_Pack_Temperature");
        }
        s.mcuC = value("MCU_Temperatute");
        s.motorC = value("Motor_Temprature");
        s.rpm = value("Motor_RPM");
        s.throttleV = value("Throttle_Value1");
        s.chargeRate = value("Charging_Rate");
        s.cycles = value("Cycle_Number");

        for (int i = 0; i < s.cells.length; i++) {
            s.cells[i] = value(CELL_VOLTAGE_NAMES[i]);
            double b = value(BALANCING_STATUS_NAMES[i]);
            s.balancing[i] = !Double.isNaN(b) && b >= 1.0;
        }

        s.balancingTemp = value("Balancing_Temperature");
        s.frontBrake = oneIsTrue("Front_Brake_Press");
        s.rearBrake = oneIsTrue("Rear_Brake_Press");
        s.brakeRegen = value("Brake_Regen_Percentage");
        s.totalChargeTime = value("Total_Charge_Time");
        s.sessionKwh = value("Total_Kwhr_Charged_in_the_Mode");
        s.sessionAh = value("Factored_Charge_Amphr");
        s.timeToCharge80 = value("Time_to_chargeSC_80");
        s.timeToCharge100 = value("Time_to_Full_chargeSC_00");
        s.motorTorque = value("Motor_Torque");
        s.motorCurrent = value("Motor_Current");
        s.motorFreq = value("Motor_Frequency");

        // Energy & usable capacity
        s.availableCapacityAh = value("Available_Capacity_00");
        double totalEn = value("Total_Available_Energy");
        s.totalEnergyKwh = Double.isNaN(totalEn) ? Double.NaN : (totalEn > 100 ? totalEn / 1000.0 : totalEn);
        s.instantEnergyKwh = value("Instantaneous_Available_Energy");
        s.cycleKwh = value("Kwhr_Accumulated_Current_Cycle");
        s.tripDistanceKm = value("Distance_covered_for_current_trip");

        // Aux & DC-DC (12V)
        s.dcdcVoltage = value("DCDC_Output_Voltage");
        s.auxBatteryCharging = flag("Charging_status_of_AUX_battery");
        s.auxIdleDrainAh = value("Total_Ah_Lost_in_charging_Aux_battery_in_Idle");

        // BMS Safe Operating Envelope & Current Limits
        double disCurrent = value("Overall_Discharge_current_limit");
        if (Double.isNaN(disCurrent)) disCurrent = value("Discharge_current_limit_00");
        s.maxDischargeCurrent = disCurrent;

        double chgCurrent = value("Overall_Charge_current_limit");
        if (Double.isNaN(chgCurrent)) chgCurrent = value("Charge_current_limit_00");
        s.maxChargeCurrent = chgCurrent;

        double disPwr = value("Overall_Discharge_Power_Limit");
        if (Double.isNaN(disPwr)) disPwr = value("Discharge_Power_Limit_00");
        s.maxDischargePower = Double.isNaN(disPwr) ? Double.NaN : (disPwr > 50 ? disPwr / 1000.0 : disPwr);

        double regPwr = value("Overall_Regen_Power_Limit");
        if (Double.isNaN(regPwr)) regPwr = value("Regen_Power_Limit_00");
        s.maxRegenPower = Double.isNaN(regPwr) ? Double.NaN : (regPwr > 50 ? regPwr / 1000.0 : regPwr);

        double chgVoltLim = value("Overall_Charge_Voltage_limit");
        if (Double.isNaN(chgVoltLim)) chgVoltLim = value("Charge_Voltage_limit_00");
        s.chargeVoltageLimit = chgVoltLim;

        s.contactorState = state("Contactor_state_00");

        // Motion, IMU & Incline
        s.acceleration = value("Vehicle_Acceleration");
        s.inclinationDeg = value("Vehicle_Resultant_Inclination");
        s.gradientX = value("Gradient_Information_X_axis");
        s.gradientY = value("Gradient_Information_Y_axis");
        s.gradientZ = value("Gradient_Information_Z_axis");
        s.ambientLight = value("Ambient_Light_Sensor_Value");
        s.gyroTheta = value("Gyro_X_Theta");
        s.magnetometerZ = value("Magnetometer_Zaxis");

        // Safety, Theft & Interlocks
        s.hillHoldResponse = state("Hill_Hold_Response");
        s.regenStatus = state("Regen_Status");
        s.leftBrakeCutoff = flag("Motor_Cutoff_Staus_of_Left_Brake");
        s.rightBrakeCutoff = flag("Motor_Cutoff_Staus_of_Right_Brake");

        // Dual Throttle & Motor Stator Resolver
        s.throttle2V = value("Throttle_Value2");
        s.idleThrottleCount = value("IDLE_Throttle_Count");
        s.driveStartThrottleCount = value("Drive_Start_Throttle_Count");
        s.positionCounter = value("Position_Counter");
        s.rotorAngle = value("Rotor_Angle");

        // Fast Charging & Hypercharger
        s.timeToChargeFC80 = value("Time_to_chargeFC_80");
        s.timeToChargeFC100 = value("Time_to_chargeFC_100");
        s.chargerAvailVolt = value("Charging_Voltage_Available_range");
        s.chargerAvailCurrent = value("Charging_Current_Available_range");

        // Lifetime Balancing Analytics
        s.lifetimeBalancingCount = value("Number_of_times_balancing_started_due_to_Deviation");
        s.lifetimeBalancingDurationMin = value("Total_Balancing_Duration");
        s.lifetimeBalancingAhLost = value("Total_Amphr_Lost_during_Balancing");
        s.maxCellDevDischarge = value("Max_Cell_Deviation_Observed_during_Discharge");
        s.maxCellDevCharge = value("Max_Cell_Deviation_Observed_during_Charge");

        // ECU Hardware & Firmware Registry
        s.mbmsFw = formatVersion(value("MBMS_Firmware_Version"));
        s.mbmsBl = formatVersion(value("BMS00_Bootloader_version"));
        s.vcuFw = formatVersion(value("VCU_Firmware_Version"));
        s.vcuBl = formatVersion(value("VCU_Bootloader_version"));
        s.huFw = formatVersion(value("HU_Firmware_Version"));
        s.huBl = formatVersion(value("HU_Bootloader_version"));
        s.displaySw = formatVersion(value("Display_Sw_Version"));
        s.tcuSw = formatVersion(value("TCU_SW_version"));
        s.wifiBtSw = formatVersion(value("WIFI_BT_SW_version"));
        s.esclStatus = state("ESCL_Status");

        // Pack Thermal Map & PDU
        for (int i = 0; i < 4; i++) {
            s.packTemps[i] = value(BMS00_PACK_TEMP_NAMES[i]);
        }
        s.packTemps[4] = value("BMS00_Pack_Temperature_05");
        s.packTemps[5] = value("BMS00_Pack_Temperature_06");
        s.pduTemp = value("BMS00_PDU_Temperature_01");
        if (Double.isNaN(s.pduTemp)) {
            s.pduTemp = value("BMS00_PDU_Temperature_02");
        }
        s.pduDeltaTemp = value("BMS00_PDU_Delta_Temperature");
        s.packMeasuredV = value("BMS00_Battery_Pack_Voltage_Measured");
        s.busV = value("Bus_Voltage_00");

        // Chassis Rails & Current Senses
        s.pgood3v8 = flag("Sig_3_8V_DC_DC_PGOOD_STATUS");
        s.pgood5v0 = flag("Sig_5V_Dc_Dc_PGOOD_STATUS");
        s.pgoodLdo = flag("Sig__V3_LDO_PGOOD_STATUS");
        s.brakePadStatus = value("Brake_Pad_Status");
        s.demandChargeCurrent = value("Charge_Demand_Current");
        s.demandChargeVoltage = value("Charge_Demand_Voltage");

        s.charging = flag("Charger_Status_Signal");
        s.plugged = pluggedIn();
        s.sideStandUp = zeroIsTrue("Side_Stand_Status");
        s.seatUnlocked = zeroIsTrue("Seat_Lock_Status");
        s.unlocked = oneIsTrue("Vehicle_state_BCM_ULock");
        s.fallState = state("Fall_Detection_State");
        s.towState = state("Tow_Theft_Detection");
        s.regenState = state("Regen_Availability");
        s.hillState = state("Hill_Hold_Availability");
        s.driveMode = driveMode();
        s.activeFaults = new ArrayList<>(activeFaultsMap.values());

        // -------------------------------------------------------------
        // Dynamic Pack Capacity & Multi-Factor Real SOH Calculation
        // -------------------------------------------------------------
        double cSoh = value("Cycle_SOH");
        if (!Double.isNaN(cSoh) && cSoh > 0.0 && cSoh <= 100.0) {
            s.cycleSoh = cSoh;
        }

        double totalWh = value("Total_Available_Energy");
        if (!Double.isNaN(totalWh) && totalWh > 200) {
            s.totalAvailableEnergyWh = totalWh;
            // Total_Available_Energy is instantaneous available Wh at current SOC.
            // Normalize by SOC to estimate 100% full capacity before classifying pack tier.
            double estFullCapWh = (!Double.isNaN(s.soc) && s.soc >= 20.0)
                    ? (totalWh / (s.soc / 100.0))
                    : totalWh;
            if (estFullCapWh >= 3400) {
                s.nominalPackKwh = 3.97;
            } else if (estFullCapWh >= 2400) {
                s.nominalPackKwh = 2.98;
            } else if (estFullCapWh >= 1500) {
                s.nominalPackKwh = 2.00;
            }
        }

        // Multi-factor real SOH (SAE J3356 / ISO 12405 / LeafSpy Hx model)
        double calculatedSoh;
        if (!Double.isNaN(s.cycleSoh) && s.cycleSoh > 0) {
            calculatedSoh = s.cycleSoh;
        } else if (!Double.isNaN(s.cycles) && s.cycles > 0) {
            calculatedSoh = Math.max(70.0, Math.min(100.0, 100.0 - (s.cycles * 0.025)));
        } else if (!Double.isNaN(s.odoKm) && s.odoKm > 0) {
            double equivCycles = s.odoKm / 85.0;
            calculatedSoh = Math.max(70.0, Math.min(100.0, 100.0 - (equivCycles * 0.025)));
        } else {
            calculatedSoh = (!Double.isNaN(s.soh) && s.soh > 0) ? s.soh : 100.0;
        }

        if (!Double.isNaN(s.imbalanceV) && s.imbalanceV > 0.025) {
            double excessSpreadMv = (s.imbalanceV - 0.025) * 1000.0;
            double spreadPenalty = (excessSpreadMv / 10.0) * 0.5;
            calculatedSoh = Math.max(60.0, calculatedSoh - spreadPenalty);
        }
        s.sohCalculated = Math.round(calculatedSoh * 10.0) / 10.0;

        return s;
    }

    // -------------------------------------------------------------- helpers

    private int oneIsTrue(String signal) {
        double v = value(signal);
        return Double.isNaN(v) ? -1 : (v != 0 ? 1 : 0);
    }

    private int zeroIsTrue(String signal) {
        double v = value(signal);
        return Double.isNaN(v) ? -1 : (v == 0 ? 1 : 0);
    }

    private int flag(String signal) {
        double v = value(signal);
        if (Double.isNaN(v)) {
            return -1;
        }
        return v >= 1 ? 1 : 0;
    }

    /** A small state word (4-bit enums on 0x683); -1 when not reported. */
    private int state(String signal) {
        double v = value(signal);
        return Double.isNaN(v) ? -1 : (int) v;
    }

    private int pluggedIn() {
        double plugged = value("Charger_Plugged_In");
        double connected = value("Charger_Connected");
        if (Double.isNaN(plugged) && Double.isNaN(connected)) {
            return -1;
        }
        boolean on = (!Double.isNaN(plugged) && plugged >= 1)
                || (!Double.isNaN(connected) && connected >= 1);
        return on ? 1 : 0;
    }

    /** The dash's own mode word, decoded the way Axiom reads it. */
    public synchronized String driveMode() {
        double v = value("Vehicle_Discharge_Mode");
        if (Double.isNaN(v)) {
            return "";
        }
        switch ((int) v) {
            case 0: return "Parked";
            case 2: return "Normal";
            case 3: return "Sport";
            case 4: return "Custom";
            case 5: return "Reverse";
            case 6: return "Eco";
            case 7: return "Hyper";
            case 10: return "Reverse";
            case 13: return "Reverse";
            default: return "Mode " + (int) v;
        }
    }

    /** Signals that are 1 or more and have a fault row, keyed by signal name. */
    public synchronized Map<String, Faults.Fault> activeBySignal() {
        Map<String, Faults.Fault> out = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : values.entrySet()) {
            if (e.getValue() < 1.0) {
                continue;
            }
            Faults.Fault f = faults.forSignal(e.getKey());
            if (f != null) {
                out.put(e.getKey(), f);
            }
        }
        return out;
    }

    /** Clears the latched set without touching the live values. */
    public synchronized void clearLatched() {
        latched.clear();
    }

    /** Busiest message ids first, useful for the scan view. */
    public synchronized List<Map.Entry<Integer, Integer>> busiest() {
        List<Map.Entry<Integer, Integer>> list = new ArrayList<>(counts().entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        return list;
    }

    private static String formatVersion(double v) {
        if (Double.isNaN(v) || v <= 0) return "";
        long lv = (long) v;
        if (v == (double) lv) {
            return String.valueOf(lv);
        }
        return String.format(Locale.US, "%.1f", v);
    }

    /** Kept for callers that want an empty map rather than null. */
    public static Map<String, Double> emptyValues() {
        return new HashMap<>();
    }
}
