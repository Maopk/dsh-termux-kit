package io.dsh.bridge;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wake receiver: the Termux side uses
 *   am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver --es token <token>
 * to make the bridge start listening again (sidestepping Android 10+'s background-activity-start restriction).
 *
 * Two cases:
 *   ① The service is still alive (only the port was soft-stopped) → resumeListening directly; any
 *      broadcast can wake it (old behaviour unchanged).
 *   ② The service has been shut down (unbound by the system after a soft stop / sleep / panic) → **the
 *      right token is required** before it may re-authorize: write this service back into the system's
 *      enabled-accessibility-services list so the system rebinds it.
 *      A wake without the token is always ignored — otherwise any app could pull up an accessibility
 *      service the user had switched off.
 *
 * Note: re-authorizing depends on WRITE_SECURE_SETTINGS (the user must have run an explicit pm grant
 * via adb once). Without it case ② fails and only the user can re-enable it in system settings —
 * exactly what we want to avoid, so a missing permission is stated explicitly in the log.
 */
public class WakeReceiver extends BroadcastReceiver {
    public static final String ACTION_WAKE = "io.dsh.bridge.WAKE";
    private static final String TAG = "DSHBridge";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (!ACTION_WAKE.equals(intent.getAction())) return;

        // ① The service is still there: the lightest path
        BridgeService svc = BridgeService.INSTANCE;
        if (svc != null) {
            svc.resumeListening();
            return;
        }

        // ② The service has been shut down: re-authorizing requires the token
        String given = intent.getStringExtra("token");
        String real = context.getSharedPreferences(BridgeService.PREFS, Context.MODE_PRIVATE)
                .getString("token", "");
        if (real == null || real.isEmpty() || given == null || !real.equals(given.trim())) {
            Log.w(TAG, "WAKE ignored: the correct token was not supplied (re-authorizing accessibility requires the token)");
            return;
        }
        reenable(context);
    }

    /** Writes this service back into enabled accessibility services so the system rebinds it (needs WRITE_SECURE_SETTINGS) */
    private void reenable(Context ctx) {
        try {
            ComponentName me = new ComponentName(ctx, BridgeService.class);
            String longForm = me.flattenToString();          // pkg/full class name
            String shortForm = me.flattenToShortString();    // pkg/.class name

            String cur = Settings.Secure.getString(ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            Set<String> set = new LinkedHashSet<>();
            if (cur != null) {
                for (String s : cur.split(":")) if (!s.isEmpty()) set.add(s);
            }
            boolean has = false;
            for (String s : set) {
                if (s.equalsIgnoreCase(longForm) || s.equalsIgnoreCase(shortForm)) { has = true; break; }
            }
            if (!has) {
                set.add(longForm);
                Settings.Secure.putString(ctx.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, TextUtils.join(":", set));
            }
            Settings.Secure.putInt(ctx.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            Log.i(TAG, "WAKE: accessibility re-authorized" + (has ? " (it was already in the list)" : " (written back to the list)"));
        } catch (Throwable t) {
            // Without WRITE_SECURE_SETTINGS a SecurityException lands here
            Log.w(TAG, "WAKE: re-authorization failed (most likely missing WRITE_SECURE_SETTINGS): " + t);
        }
    }
}
