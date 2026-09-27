package io.dsh.console;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Widget button → send one Termux command. */
public class WidgetActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String what = intent == null ? null : intent.getStringExtra("what");
        if (what == null) return;
        Last.setPending(ctx, "status".equals(what) ? Lang.t("Refresh status") : ("open".equals(what) ? "Open UI" : labelOf(what)));
        refreshWidget(ctx);
        if ("status".equals(what)) {
            TermuxRunner.run(ctx, "status", Lang.t("Refresh status"), TermuxRunner.statusCmd(), true);
        } else if ("open".equals(what)) {
            TermuxRunner.run(ctx, "open", "Open UI", TermuxRunner.openUiCmd(), false);
        } else {
            String label = what, command = TermuxRunner.taskCmd(what);
            for (Tasks.T t : Tasks.ALL) if (t.id.equals(what)) { label = t.label; if (t.cmd != null) command = t.cmd; }
            TermuxRunner.run(ctx, what, label, command, false);
        }
    }

    private static String labelOf(String id) {
        for (Tasks.T t : Tasks.ALL) if (t.id.equals(id)) return t.label;
        return id;
    }

    static void refreshWidget(Context ctx) {
        try {
            android.appwidget.AppWidgetManager m = android.appwidget.AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new android.content.ComponentName(ctx, DshWidget.class));
            if (ids != null) for (int id : ids) m.updateAppWidget(id, DshWidget.build(ctx));
        } catch (Throwable t) { /* no widget, never mind */ }
    }
}
