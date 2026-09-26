package io.dsh.console;

import android.content.Context;
import android.content.SharedPreferences;

/** 上一次结果的本地缓存（进程被杀也不丢，小部件重绘时直接拿来用）。 */
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
    /** 返回"正在执行：X"里的 X；没有或已过期(>3分钟)则返回 null。 */
    static String pending(Context c) {
        String p = prefs(c).getString("pending", "");
        long at = prefs(c).getLong("pendingAt", 0);
        if (p == null || p.isEmpty()) return null;
        if (System.currentTimeMillis() - at > 180000) return null;
        return p;
    }
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences(P, Context.MODE_PRIVATE); }
}
