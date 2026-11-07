package dev.sola.hud;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import dev.sola.core.Can;
import dev.sola.core.Dbc;
import dev.sola.core.Faults;
import dev.sola.core.Live;

/**
 * High-speed CAN reader for Sola HUD.
 * Reads frames directly from Linux SocketCAN (vcan0) on MoveOS / SOM,
 * decodes via Dbc into Live.Snapshot, and falls back to a smooth simulation
 * when running off-vehicle.
 */
public final class CanReader {

    private static final String TAG = "SolaHud:CanReader";

    public interface Listener {
        void onSnapshot(Live.Snapshot snapshot, long pps);
    }

    private final Context context;
    private final Live live;
    private final Handler mainHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Listener listener;
    private Process candumpProcess;
    private boolean chargeCapEnabled = true;
    private long frameCount = 0;
    private long lastPpsTime = 0;
    private long currentPps = 0;

    public CanReader(Context context) {
        this.context = context.getApplicationContext();
        Dbc dbc = loadDbc(context);
        Faults faults = loadFaults(context);
        this.live = new Live(dbc, faults);
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setChargeCapEnabled(boolean enabled) {
        this.chargeCapEnabled = enabled;
    }

    public boolean isChargeCapEnabled() {
        return chargeCapEnabled;
    }

    public void start() {
        if (running.getAndSet(true)) return;
        executor.submit(this::runLoop);
    }

    public void stop() {
        running.set(false);
        if (candumpProcess != null) {
            try {
                candumpProcess.destroy();
            } catch (Exception ignored) {}
        }
    }

    private void runLoop() {
        lastPpsTime = System.currentTimeMillis();
        boolean socketCanAvailable = tryStartCandump();

        if (socketCanAvailable) {
            readCandumpStream();
        } else {
            Log.i(TAG, "SocketCAN vcan0 not reachable; starting mock telemetry stream for demonstration.");
            runSimulationLoop();
        }
    }

    private boolean tryStartCandump() {
        try {
            candumpProcess = new ProcessBuilder("candump", "vcan0").redirectErrorStream(true).start();
            Thread.sleep(150);
            try {
                candumpProcess.exitValue();
                return false;
            } catch (IllegalThreadStateException e) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
    }

    private void readCandumpStream() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(candumpProcess.getInputStream()))) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                parseAndFeedFrame(line);
                tickPps();
            }
        } catch (Exception e) {
            Log.w(TAG, "candump stream error: " + e.getMessage());
        }
    }

    private void parseAndFeedFrame(String line) {
        try {
            line = line.trim();
            if (line.isEmpty()) return;

            int canId;
            byte[] payload;

            if (line.contains("#")) {
                int hashIdx = line.indexOf('#');
                int spaceIdx = line.lastIndexOf(' ', hashIdx);
                String idStr = (spaceIdx >= 0) ? line.substring(spaceIdx + 1, hashIdx) : line.substring(0, hashIdx);
                canId = Integer.parseInt(idStr.trim(), 16);
                String dataStr = line.substring(hashIdx + 1).trim();
                payload = hexToBytes(dataStr);
            } else {
                String[] parts = line.split("\\s+");
                if (parts.length < 3) return;
                canId = Integer.parseInt(parts[1], 16);
                int start = line.indexOf(']');
                if (start < 0) return;
                String hexBytes = line.substring(start + 1).trim().replace(" ", "");
                payload = hexToBytes(hexBytes);
            }

            live.onFrame(new Can.Frame(canId, payload.length, payload));
            checkChargeCap(live.snapshot());
        } catch (Exception ignored) {}
    }

    private void tickPps() {
        frameCount++;
        long now = System.currentTimeMillis();
        if (now - lastPpsTime >= 1000) {
            currentPps = frameCount;
            frameCount = 0;
            lastPpsTime = now;
            postSnapshot();
        }
    }

    private void postSnapshot() {
        final Live.Snapshot snap = live.snapshot();
        final long pps = currentPps;
        mainHandler.post(() -> {
            if (listener != null) listener.onSnapshot(snap, pps);
        });
    }

    private void checkChargeCap(Live.Snapshot s) {
        if (!chargeCapEnabled) return;
        if (s.charging == 1 && s.soc >= 80.0) {
            sendFrame(0x6BC, "0D00000000000000"); // 0x0D = Stop_Charge
            Log.i(TAG, "80% Battery Saver Cap triggered Stop_Charge frame on 0x6BC");
        }
    }

    public void sendFrame(int canId, String hexData) {
        executor.submit(() -> {
            try {
                String idHex = Integer.toHexString(canId).toUpperCase();
                String cmd = "cansend vcan0 " + idHex + "#" + hexData;
                Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
                Log.i(TAG, "Executed: " + cmd);
            } catch (Exception e) {
                Log.w(TAG, "Failed to send frame: " + e.getMessage());
            }
        });
    }

    public void popTrunk() {
        sendFrame(0x6BC, "0300000000000000"); // 0x03 = Seat_Unlock_Cmd
    }

    public void lockVehicle() {
        sendFrame(0x6BC, "5400000000000000"); // 0x54 = Lock_Now
    }

    public void unlockVehicle() {
        executor.submit(() -> {
            for (int i = 0; i < 30; i++) {
                sendFrame(0x6BC, "1900000000000000");
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {}
            }
        });
    }

    public void toggleHazard() {
        sendFrame(0x6BC, "4A00000000000000"); // 0x4A = Hazard_Light_Turn_ON
    }

    private void runSimulationLoop() {
        double simSoc = 79.4;
        double simSpeed = 0.0;
        double deltaV = 11.5;
        double simTemp = 28.4;
        double simPower = 0.42;

        while (running.get()) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                break;
            }

            simTemp += (Math.random() - 0.48) * 0.05;
            double cellBase = 3.92 + (simSoc / 100.0) * 0.26;

            Live.Snapshot s = new Live.Snapshot();
            s.soc = simSoc;
            s.displaySoc = simSoc;
            s.packV = cellBase * 14.0;
            s.packA = (simPower * 1000.0) / s.packV;
            s.speedKph = simSpeed;
            s.unlocked = 1;
            s.plugged = 1;
            s.charging = 1;
            s.packC = simTemp;
            s.mcuC = simTemp + 3.2;
            s.balancingTemp = simTemp + 1.1;
            s.imbalanceV = deltaV / 1000.0;
            s.cellMinV = cellBase;
            s.cellMaxV = cellBase + s.imbalanceV;
            for (int i = 0; i < 14; i++) {
                s.cells[i] = cellBase + (i * s.imbalanceV / 13.0);
            }
            s.lastFrameMs = System.currentTimeMillis();

            if (chargeCapEnabled && s.charging == 1 && s.soc >= 80.0) {
                Log.i(TAG, "SIM: 80% Charge Cap reached, simulated charge stop.");
            }

            final Live.Snapshot snap = s;
            mainHandler.post(() -> {
                if (listener != null) listener.onSnapshot(snap, 1024);
            });
        }
    }

    private byte[] hexToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    private Dbc loadDbc(Context c) {
        try (InputStream is = c.getAssets().open("can_database.json")) {
            byte[] bytes = new byte[is.available()];
            int read = is.read(bytes);
            return Dbc.parse(new String(bytes, 0, read, StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "Could not load can_database.json: " + e.getMessage());
            return Dbc.parse("{}");
        }
    }

    private Faults loadFaults(Context c) {
        try (InputStream is = c.getAssets().open("faults.json")) {
            byte[] bytes = new byte[is.available()];
            int read = is.read(bytes);
            return Faults.parse(new String(bytes, 0, read, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return Faults.parse("{}");
        }
    }
}
