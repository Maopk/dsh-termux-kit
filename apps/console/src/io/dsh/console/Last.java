package io.dsh.console;

import android.content.Context;
import android.content.SharedPreferences;

/** Local cache of the last result (survives the process being killed; the widget redraws straight from it). */
final class Last {
    private static final String P = "dsh-console";
    static void set(Context c, String text) {
        prefs(c).edit().putString("last", text).putLong("at", System.currentTimeMillis()).apply();
    }
    static void setStatus(Context c, String json) {
        prefs(c).edit().putString("status", json).putLong("statusAt", System.currentTimeMillis()).apply();
    }
    static String get(Context c) { return prefs(c).getString("last", ""); }
    static String status(Context c) { return prefs(c).getString("status", ""); }
    static long statusAt(Context c) { return prefs(c).getLong("statusAt", 0); }
    static void setPending(Context c, String label) {
        prefs(c).edit().putString("pending", label == null ? "" : label)
                .putLong("pendingAt", System.currentTimeMillis()).apply();
    }
    /** Returns the X in Lang.t("Running: X"); null when it is absent or expired (>3 minutes). */
    static String pending(Context c) {
        String p = prefs(c).getString("pending", "");
        long at = prefs(c).getLong("pendingAt", 0);
        if (p == null || p.isEmpty()) return null;
        if (System.currentTimeMillis() - at > 180000) return null;
        return p;
    }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(P, Context.MODE_PRIVATE); }
}
