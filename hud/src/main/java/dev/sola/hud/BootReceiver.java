package dev.sola.hud;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

/**
 * Automatically launches the Sola HUD service when the scooter boots up.
 */
public final class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "SolaHud:BootReceiver";
    private static final String PREFS_NAME = "sola_hud_prefs";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean autoStart = prefs.getBoolean("start_on_boot", true);

        if (!autoStart) {
            Log.i(TAG, "Boot received, but start_on_boot is disabled.");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Boot received, but overlay permission is missing. Cannot start HUD.");
            return;
        }

        Log.i(TAG, "Boot completed. Starting Sola HUD Overlay Service...");
        Intent serviceIntent = new Intent(context, HudOverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
