package io.dsh.console;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** Home-screen widget: three lamps + 4 safe buttons + refresh/open. */
public class DshWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (intent != null && AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(intent.getAction())) {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new android.content.ComponentName(ctx, DshWidget.class));
            if (ids != null) for (int id : ids) m.updateAppWidget(id, build(ctx));
        }
    }

    static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        // Lamp and button ids are kept fully separate so a tap cannot bind to the wrong view
        v.setOnClickPendingIntent(R.id.w_btn_refresh, pi(ctx, "status"));
        v.setOnClickPendingIntent(R.id.w_btn_start, pi(ctx, "1_start-dsh"));
        v.setOnClickPendingIntent(R.id.w_btn_adb, pi(ctx, "8_enable-wireless-adb"));   // adb only
        v.setOnClickPendingIntent(R.id.w_btn_bridge, pi(ctx, "bridge_wake"));     // wake the bridge only
        v.setOnClickPendingIntent(R.id.w_title, pi(ctx, "open"));

        String json = Last.status(ctx);
        String dsh = "grey", bridge = "grey", adb = "grey", line = "Tap Refresh to read status";
        if (json != null && json.length() > 0) {
            try {
                JSONObject o = new JSONObject(json);
                JSONObject lamps = o.optJSONObject("lamps");
                if (lamps != null) {
                    dsh = lamps.optString("dsh", "grey");
                    bridge = lamps.optString("bridge", "grey");
                    adb = lamps.optString("adb", "grey");
                }
                JSONObject d = o.optJSONObject("dsh");
                JSONObject b = o.optJSONObject("bridge");
                JSONObject a = o.optJSONObject("adb");
                line = o.optString("ts", "");
                if (d != null) line += "  DSH:" + (d.optBoolean("ok") ? Lang.t("running") : Lang.t("stopped"));
                if (b != null) line += "  Bridge:" + (b.optString("ver", "?").isEmpty() ? "none" : "v" + b.optString("ver"));
                if (a != null) {
                    org.json.JSONArray ds = a.optJSONArray("devices");
                    line += "  adb:" + (ds != null && ds.length() > 0 ? ds.optString(0) : Lang.t("not connected"));
                }
            } catch (Throwable t) {
                line = Lang.t("Status parse failed: ") + t.getMessage();
            }
        }
        v.setTextViewText(R.id.w_lamp_dsh, Lang.t("● DSH"));
        v.setTextColor(R.id.w_lamp_dsh, color(dsh));
        v.setTextViewText(R.id.w_lamp_bridge, Lang.t("● Bridge"));
        v.setTextColor(R.id.w_lamp_bridge, color(bridge));
        v.setTextViewText(R.id.w_lamp_adb, Lang.t("● adb"));
        v.setTextColor(R.id.w_lamp_adb, color(adb));
        String pend = Last.pending(ctx);
        if (pend != null) line = "⏳ Running: " + pend + " …　" + line;
        v.setTextViewText(R.id.w_status, line);
        return v;
    }

    private static int color(String lamp) {
        if ("green".equals(lamp)) return Color.parseColor("#3FB950");
        if ("yellow".equals(lamp)) return Color.parseColor("#D29922");
        if ("red".equals(lamp)) return Color.parseColor("#F85149");
        return Color.parseColor("#8B949E");
    }

    private static PendingIntent pi(Context ctx, String what) {
        Intent i = new Intent(ctx, WidgetActionReceiver.class).putExtra("what", what);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (android.os.Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, what.hashCode(), i, flags);
    }
}
