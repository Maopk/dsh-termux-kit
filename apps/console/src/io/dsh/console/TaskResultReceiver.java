package io.dsh.console;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** 接收 Termux 回传的命令结果（stdout/stderr/exitCode），缓存并刷新小部件。 */
public class TaskResultReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String cmdId = intent.getStringExtra("cmdId");
        String label = intent.getStringExtra("label");
        // 结果 bundle 的键是短名 "result"（新版 Termux；长名是旧版遗留，两个都试）
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
            // ⚠ 别再拿 "err" 当消息：按 TermuxConstants，bundle 里 `err` 是 **int** 错误码
            //   （`errmsg` 才是字符串）。v0.5 实机上线后日志里就出现了 "[errmsg] -1" 这种鬼东西
            //   —— 用户只会以为出了错。现在只认**真的有文字**的 errmsg。
            if (errmsg.isEmpty()) errmsg = str(b, "err");
            if (!errmsg.isEmpty() && !errmsg.trim().matches("-?\\d+")) out = out + "\n[errmsg] " + errmsg.trim();
        } else {
            out = "(没有收到结果 bundle；可能是 Termux 未授权或 allow-external-apps 未开)\n" + intent.getExtras();
        }
        Last.setPending(ctx, null);
        out = out.trim();

        boolean isStatus = "status".equals(cmdId) || intent.getBooleanExtra("isStatus", false);
        // ⚠ 截断规则不一样：任务输出看**尾部**（最近的几行最有用），
        //   而状态是**一整个 JSON**，从尾部切会把头切掉 → 解析必失败。
        //   实测踩到：状态包长到 6463 字节时，被切掉头部的片段让 App 显示「状态解析失败：
        //   Value asks" of type java.lang.String cannot be converted to JSONObject」。
        if (out.length() > (isStatus ? 200000 : 4000)) {
            out = isStatus ? out.substring(0, 200000) : out.substring(out.length() - 4000);
        }

        if (isStatus) {
            Last.setStatus(ctx, out);
        } else {
            Last.set(ctx, "【" + (label == null ? cmdId : label) + "】exit=" + code + "\n" + out);
            // 任务跑完后顺手再刷一次状态（脚本收尾时自己也发布过，这里保证界面同步）
            TermuxRunner.run(ctx, "status", "刷新状态", TermuxRunner.statusCmd(), true);
        }
        // 通知小部件重绘
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(ctx);
            int[] ids = m.getAppWidgetIds(new ComponentName(ctx, DshWidget.class));
            if (ids != null && ids.length > 0) {
                Intent u = new Intent(ctx, DshWidget.class).setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                ctx.sendBroadcast(u);
            }
        } catch (Throwable t) { /* 没有部件就算了 */ }
        // 通知界面（如果正开着）：把结果本身也带过去，界面才能显示"完成/退出码/输出"
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
