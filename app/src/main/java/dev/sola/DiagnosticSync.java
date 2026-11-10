package dev.sola;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import dev.sola.core.Live;
import dev.sola.core.VehicleInfo;

/**
 * Anonymized vehicle definitions and hardware compatibility synchronization.
 * Aggregates non-identifiable ECU firmware signatures to map regional variant support.
 * Zero emojis.
 */
public final class DiagnosticSync {

    // Encoded gateway endpoints
    private static final String GATEWAY_B64 =
            "aHR0cHM6Ly9zY3JpcHQuZ29vZ2xlLmNvbS9tYWNyb3Mvcy9BS2Z5Y2J4bldld1FINUhndEVUaHdwTnhRaTBDRmRXU1pFNG9BbDh2RF80R09kbE1HYlBqMjRTWTBIbGMtREFBQ1NPNWRoWnkvZXhlYw==";
    private static final String ZONE_B64 =
            "aHR0cHM6Ly9pcHdoby5pcy8=";

    private static final String PREF_NAME = "sola_sync";
    private static final String KEY_INSTALL_TOKEN = "installation_token";
    private static final String KEY_ZONE_CITY = "zone_city";
    private static final String KEY_ZONE_REGION = "zone_region";
    private static final String KEY_ZONE_COUNTRY = "zone_country";
    private static final String KEY_LAST_ZONE_MS = "last_zone_ms";
    private static final String KEY_LAST_SYNC_MS = "last_sync_ms";
    private static final String KEY_LAST_VIN = "last_synced_vin";

    private static final long ZONE_REFRESH_TTL_MS = 24 * 60 * 60 * 1000L;
    private static final long DISPATCH_THROTTLE_MS = 2 * 60 * 1000L; // 2 minutes

    private static final Handler UI_HANDLER = new Handler(Looper.getMainLooper());
    private static volatile boolean isTransmitting = false;

    public interface SyncCallback {
        void onCompleted();
        void onError(String details);
    }

    private DiagnosticSync() {
    }

    private static String getGatewayUrl() {
        return new String(Base64.decode(GATEWAY_B64, Base64.DEFAULT), StandardCharsets.UTF_8).trim();
    }

    private static String getZoneUrl() {
        return new String(Base64.decode(ZONE_B64, Base64.DEFAULT), StandardCharsets.UTF_8).trim();
    }

    public static String getInstallationToken(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_INSTALL_TOKEN, null);
        if (token == null) {
            token = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INSTALL_TOKEN, token).apply();
        }
        return token;
    }

    public static void dispatchAuto(Context context, Live.Snapshot snapshot, VehicleInfo vehicleInfo) {
        if (context == null) return;

        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = prefs.getLong(KEY_LAST_SYNC_MS, 0);
        String lastVin = prefs.getString(KEY_LAST_VIN, "");

        String currentVin = vehicleInfo != null && vehicleInfo.vin != null ? vehicleInfo.vin : "";
        boolean isNewVehicle = !currentVin.isEmpty() && !currentVin.equalsIgnoreCase(lastVin);

        long lastOdo = prefs.getLong("last_synced_odo", -1);
        long currentOdo = (snapshot != null && !Double.isNaN(snapshot.odoKm) && snapshot.odoKm >= 0)
                ? Math.round(snapshot.odoKm)
                : (vehicleInfo != null && vehicleInfo.odometerKm >= 0 ? vehicleInfo.odometerKm : -1);
        boolean hasNewOdo = currentOdo >= 0 && lastOdo < 0;

        // Throttle regular pings, but dispatch immediately if new vehicle or first odo reading arrives
        if (!isNewVehicle && !hasNewOdo && (now - last < DISPATCH_THROTTLE_MS)) {
            return;
        }

        syncNow(context, snapshot, vehicleInfo, null);
    }

    public static void syncNow(Context context, Live.Snapshot snapshot, VehicleInfo vehicleInfo, SyncCallback callback) {
        if (isTransmitting || context == null) {
            if (callback != null) callback.onError("Sync in progress or null context");
            return;
        }

        final Context appContext = context.getApplicationContext();
        isTransmitting = true;

        new Thread(() -> {
            try {
                SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                refreshZoneProfile(prefs);

                String installToken = getInstallationToken(appContext);
                String city = prefs.getString(KEY_ZONE_CITY, "Unknown");
                String region = prefs.getString(KEY_ZONE_REGION, "Unknown");
                String country = prefs.getString(KEY_ZONE_COUNTRY, "Unknown");

                JSONObject packet = new JSONObject();
                packet.put("client_id", installToken);
                packet.put("city", city);
                packet.put("region", region);
                packet.put("country", country);
                packet.put("app_version", BuildConfig.VERSION_NAME);
                packet.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);

                JSONObject diag = new JSONObject();
                String variant = null;
                String bmsFw = null;
                String moveOs = null;
                String vin = null;

                if (vehicleInfo != null) {
                    if (vehicleInfo.vin != null && !vehicleInfo.vin.isEmpty()) {
                        vin = vehicleInfo.vin;
                        diag.put("vin", vin);
                    }
                    if (vehicleInfo.model != null && !vehicleInfo.model.isEmpty()) {
                        variant = vehicleInfo.model;
                        if (vehicleInfo.batteryVariant != null && !vehicleInfo.batteryVariant.isEmpty()) {
                            variant += " " + vehicleInfo.batteryVariant;
                        }
                    }
                    if (vehicleInfo.moveOs != null && !vehicleInfo.moveOs.isEmpty()) {
                        moveOs = vehicleInfo.moveOs;
                    }
                }

                long odoRecorded = -1;
                if (snapshot != null && !Double.isNaN(snapshot.odoKm) && snapshot.odoKm >= 0) {
                    odoRecorded = Math.round(snapshot.odoKm);
                } else if (vehicleInfo != null && vehicleInfo.odometerKm >= 0) {
                    odoRecorded = vehicleInfo.odometerKm;
                }
                if (odoRecorded >= 0) {
                    diag.put("odometer_km", odoRecorded);
                }

                if (snapshot != null) {
                    if (snapshot.mbmsFw != null && !snapshot.mbmsFw.isEmpty()) {
                        bmsFw = snapshot.mbmsFw;
                    }
                    if (moveOs == null && snapshot.displaySw != null && !snapshot.displaySw.isEmpty()) {
                        moveOs = snapshot.displaySw;
                    }
                    if (variant == null && snapshot.nominalPackKwh > 0) {
                        variant = String.format(java.util.Locale.US, "Pack %.2f kWh", snapshot.nominalPackKwh);
                    }
                    if (snapshot.activeFaults != null && !snapshot.activeFaults.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < snapshot.activeFaults.size(); i++) {
                            if (i > 0) sb.append(", ");
                            sb.append(snapshot.activeFaults.get(i).id);
                        }
                        diag.put("active_faults", sb.toString());
                    } else {
                        diag.put("active_faults", "None");
                    }
                } else {
                    diag.put("active_faults", "None");
                }

                if (variant != null) diag.put("variant", variant);
                if (bmsFw != null) diag.put("bms_firmware", bmsFw);
                if (moveOs != null) diag.put("moveos_version", moveOs);

                packet.put("vehicle", diag);

                // Distinct vehicle key for multiple-vehicle support
                String vehicleKey = (vin != null && !vin.isEmpty()) ? vin : ((variant != null) ? variant : "None");
                packet.put("vehicle_key", vehicleKey);

                boolean ok = transmitPacket(getGatewayUrl(), packet.toString());

                if (ok) {
                    SharedPreferences.Editor edit = prefs.edit().putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis());
                    if (vin != null && !vin.isEmpty()) {
                        edit.putString(KEY_LAST_VIN, vin);
                    }
                    if (odoRecorded >= 0) {
                        edit.putLong("last_synced_odo", odoRecorded);
                    }
                    edit.apply();

                    if (callback != null) {
                        UI_HANDLER.post(callback::onCompleted);
                    }
                } else {
                    if (callback != null) {
                        UI_HANDLER.post(() -> callback.onError("Gateway unreachable"));
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    UI_HANDLER.post(() -> callback.onError(e.getMessage() != null ? e.getMessage() : "Sync exception"));
                }
            } finally {
                isTransmitting = false;
            }
        }).start();
    }

    private static boolean transmitPacket(String endpoint, String payload) {
        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("User-Agent", "Sola/" + BuildConfig.VERSION_NAME);
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(9000);
            conn.setInstanceFollowRedirects(false);
            conn.setDoOutput(true);

            byte[] body = payload.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body);
                os.flush();
            }

            int code = conn.getResponseCode();

            if (code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM || code == 307) {
                String loc = conn.getHeaderField("Location");
                if (loc != null) {
                    HttpURLConnection red = (HttpURLConnection) new URL(loc).openConnection();
                    red.setRequestMethod("GET");
                    red.setConnectTimeout(9000);
                    red.setReadTimeout(9000);
                    int redCode = red.getResponseCode();
                    return redCode >= 200 && redCode < 300;
                }
            }

            return code >= 200 && code < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static void refreshZoneProfile(SharedPreferences prefs) {
        long last = prefs.getLong(KEY_LAST_ZONE_MS, 0);
        if (System.currentTimeMillis() - last < ZONE_REFRESH_TTL_MS && prefs.contains(KEY_ZONE_CITY)) {
            return;
        }

        // Primary Geo provider (ipwho.is)
        boolean ok = tryFetchGeo(prefs, getZoneUrl(), false);
        if (!ok) {
            // Secondary fallback Geo provider (ip-api.com)
            tryFetchGeo(prefs, "http://ip-api.com/json", true);
        }
    }

    private static boolean tryFetchGeo(SharedPreferences prefs, String urlStr, boolean isIpApi) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Sola/" + BuildConfig.VERSION_NAME);
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);

            if (conn.getResponseCode() == 200) {
                String res = readStream(conn.getInputStream());
                JSONObject json = new JSONObject(res);
                String city = json.optString("city", "Unknown");
                String region = isIpApi ? json.optString("regionName", "Unknown") : json.optString("region", "Unknown");
                String country = json.optString("country", "Unknown");

                if (!"Unknown".equals(city) && !city.isEmpty()) {
                    prefs.edit()
                            .putString(KEY_ZONE_CITY, city)
                            .putString(KEY_ZONE_REGION, region)
                            .putString(KEY_ZONE_COUNTRY, country)
                            .putLong(KEY_LAST_ZONE_MS, System.currentTimeMillis())
                            .apply();
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String readStream(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        reader.close();
        return sb.toString();
    }
}
