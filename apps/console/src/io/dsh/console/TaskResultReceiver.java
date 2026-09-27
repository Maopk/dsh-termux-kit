package io.dsh.console;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** Receives command results sent back by Termux (stdout/stderr/exitCode), caches them and refreshes the widget. */
public class TaskResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String cmdId = intent.getStringExtra("cmdId");
        String label = intent.getStringExtra("label");
        // The result bundle key is the short name "result" (newer Termux; the long name is a legacy
        // leftover — try both)
        Bundle b = intent.getBundleExtra("result");
        if (b == null) b = intent.getBundleExtra(TermuxRunner.E_RESULT);
        String out = "";
        int code = -1;
        if (b != null) {
            out = str(b, "stdout");
            String err = str(b, "stderr");
            String errmsg = str(b, "errmsg");
            code = (int) num(b, "exitCode", -1);
            if (out.isEmpty() && !err.isEmpty()) out = err;
            else if (!err.isEmpty()) out = out + "\n" + err;
            // ⚠ Don't treat "err" as the message any more: per TermuxConstants, `err` in the bundle is an
            //   **int** error code (`errmsg` is the string). After v0.5 shipped, the log filled up with
            //   junk like "[errmsg] -1" — users just assume something broke. Only a **truly textual**
            //   errmsg counts now.
            if (errmsg.isEmpty()) errmsg = str(b, "err");
            if (!errmsg.isEmpty() && !errmsg.trim().matches("-?\\d+")) out = out + "\n[errmsg] " + errmsg.trim();
        } else {
            out = "(no result bundle received; Termux may not be authorized or allow-external-apps is off)\n" + intent.getExtras();
        }
        Last.setPending(ctx, null);
        out = out.trim();

        boolean isStatus = "status".equals(cmdId) || intent.getBooleanExtra("isStatus", false);
        // ⚠ Truncation rules differ: task output keeps the **tail** (the latest lines are the useful
        //   ones), while status is **one whole JSON**, and cutting it from the tail removes its head →
        //   parsing always fails. Measured: when the status payload grew to 6463 bytes, the headless
        //   fragment made the app display "Status parse failed: Value asks" of type java.lang.String
        //   cannot be converted to JSONObject".
        if (out.length() > (isStatus ? 200000 : 4000)) {
            out = isStatus ? out.substring(0, 200000) : out.substring(out.length() - 4000);
        }

        if (isStatus) {
            Last.setStatus(ctx, out);
        } else {
            Last.set(ctx, "[" + (label == null ? cmdId : label) + Lang.t("] exit=") + code + "\n" + out);
            // Refresh status once more after a task finishes (the script publishes its own when it
            // wraps up; this keeps the UI in sync)
            TermuxRunner.run(ctx, "status", Lang.t("Refresh status"), TermuxRunner.statusCmd(), true);
        }
        // Notify the widget to redraw
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, DshWidget.class));
            if (ids != null && ids.length > 0) {
                Intent u = new Intent(ctx, DshWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                ctx.sendBroadcast(u);
            }
        } catch (Throwable t) { /* no widget, never mind */ }
        // Notify the UI (if it is open): carry the result itself along so the UI can show "done / exit
        // code / output"
        try {
            Intent ui = new Intent("io.dsh.console.UI_REFRESH").setPackage(ctx.getPackageName())
                    .putExtra("cmdId", cmdId)
                    .putExtra("isStatus", isStatus)
                    .putExtra("exit", code)
                    .putExtra("output", out);
            ctx.sendBroadcast(ui);
        } catch (Throwable t) {}
    }

    private static String str(Bundle b, String k) {
        try { Object v = b.get(k); return v == null ? "" : String.valueOf(v); } catch (Throwable t) { return ""; }
    }
    private static double num(Bundle b, String k, double d) {
        try { Object v = b.get(k); if (v instanceof Number) return ((Number) v).doubleValue(); return Double.parseDouble(String.valueOf(v)); }
        catch (Throwable t) { return d; }
    }
}
