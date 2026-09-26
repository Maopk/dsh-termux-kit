package io.dsh.console;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.widget.RemoteViews;

import org.json.JSONObject;

/** 桌面小部件：三灯 + 4 个安全按钮 + 刷新/打开。 */
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
        // 灯与按钮的 id 完全分开，避免点击挂错控件
        v.setOnClickPendingIntent(R.id.w_btn_refresh, pi(ctx, "status"));
        v.setOnClickPendingIntent(R.id.w_btn_start, pi(ctx, "1_启动DSH"));
        v.setOnClickPendingIntent(R.id.w_btn_adb, pi(ctx, "8_自动开无线调试"));   // 只连 adb
        v.setOnClickPendingIntent(R.id.w_btn_bridge, pi(ctx, "bridge_wake"));     // 只唤醒桥
        v.setOnClickPendingIntent(R.id.w_title, pi(ctx, "open"));

        String json = Last.status(ctx);
        String dsh = "grey", bridge = "grey", adb = "grey", line = "点「刷新」读取状态";
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
                if (d != null) line += "  DSH:" + (d.optBoolean("ok") ? "在跑" : "停");
                if (b != null) line += "  桥:" + (b.optString("ver", "?").isEmpty() ? "无" : "v" + b.optString("ver"));
                if (a != null) {
                    org.json.JSONArray ds = a.optJSONArray("devices");
                    line += "  adb:" + (ds != null && ds.length() > 0 ? ds.optString(0) : "未连");
                }
            } catch (Throwable t) {
                line = "状态解析失败：" + t.getMessage();
            }
        }
        v.setTextViewText(R.id.w_lamp_dsh, "● DSH");
        v.setTextColor(R.id.w_lamp_dsh, color(dsh));
        v.setTextViewText(R.id.w_lamp_bridge, "● 桥");
        v.setTextColor(R.id.w_lamp_bridge, color(bridge));
        v.setTextViewText(R.id.w_lamp_adb, "● adb");
        v.setTextColor(R.id.w_lamp_adb, color(adb));
        String pend = Last.pending(ctx);
        if (pend != null) line = "⏳ 正在执行：" + pend + " …　" + line;
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
