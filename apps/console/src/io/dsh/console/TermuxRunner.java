package io.dsh.console;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * The only channel to Termux: com.termux.RUN_COMMAND.
 *
 * Why this one: no storage permission needed (Termux's private files stay unreadable, but it can
 * **send results back** to us), no network, no accessibility. The prerequisite is
 * allow-external-apps=true on the Termux side (already enabled on this device).
 */
final class TermuxRunner {
    static final String PKG = "com.termux";
    static final String SERVICE = "com.termux.app.RunCommandService";
    static final String ACTION = "com.termux.RUN_COMMAND";
    static final String E_PATH = "com.termux.RUN_COMMAND_PATH";
    static final String E_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS";
    static final String E_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR";
    static final String E_BG = "com.termux.RUN_COMMAND_BACKGROUND";
    static final String E_PI = "com.termux.RUN_COMMAND_PENDING_INTENT";
    static final String E_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL";
    static final String E_RESULT = "com.termux.RUN_COMMAND_RESULT_BUNDLE";

    static final String HOME = "/data/data/com.termux/files/home";
    static final String BASH = "/data/data/com.termux/files/usr/bin/bash";
    static final String PERM = "com.termux.permission.RUN_COMMAND";

    /** Runs one shell command; the result comes back to TaskResultReceiver through a PendingIntent. */
    static boolean run(Context ctx, String cmdId, String label, String command, boolean isStatus) {
        try {
            Intent r = new Intent(ctx, TaskResultReceiver.class);
            r.setAction("io.dsh.console.RESULT." + cmdId + "." + System.currentTimeMillis());
            r.putExtra("cmdId", cmdId).putExtra("label", label).putExtra("isStatus", isStatus);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE; // Termux needs to put its result inside
            PendingIntent pi = PendingIntent.getBroadcast(ctx, (int) (System.currentTimeMillis() & 0x7fffffff), r, flags);

            Intent i = new Intent();
            i.setClassName(PKG, SERVICE);
            i.setAction(ACTION);
            i.putExtra(E_PATH, BASH);
            i.putExtra(E_ARGS, new String[] { "-lc", command });
            i.putExtra(E_WORKDIR, HOME);
            i.putExtra(E_BG, true);
            i.putExtra(E_LABEL, label);
            i.putExtra(E_PI, pi);
            ctx.startService(i);
            return true;
        } catch (Throwable t) {
            Last.set(ctx, "Failed to start the Termux command: " + t);
            return false;
        }
    }

    static boolean hasPermission(Context ctx) {
        return ctx.checkSelfPermission(PERM) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    static boolean installed(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(PKG, 0);
            return true;
        } catch (Throwable t) { return false; }
    }

    // ---- Common commands ----
    static String statusCmd() {
        // --brief: skip the per-task tail. Measured: the full payload is 6463 bytes, --brief only 1560,
        // while callbacks on the app side have a length cap (see TaskResultReceiver) — anything too big
        // gets truncated and JSON parsing fails.
        return HOME + "/.local/bin/dsh-status-pub --json --brief";
    }
    static String taskCmd(String id) {
        return HOME + "/.shortcuts/tasks/" + id + ".sh";
    }
    static String openUiCmd() {
        return HOME + "/.local/bin/dsh-browser-open \"$(cat " + HOME + "/.dsh-url)\"";
    }
}
