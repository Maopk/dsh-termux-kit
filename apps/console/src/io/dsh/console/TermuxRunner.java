package io.dsh.console;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/**
 * 与 Termux 的唯一通道：com.termux.RUN_COMMAND。
 *
 * 为什么用这条：不需要存储权限（读不到 Termux 私有文件，但可以让它把结果**回传**给我们）、
 * 不需要网络、不需要无障碍。前提是 Termux 侧 allow-external-apps=true（本机已开）。
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

    /** 跑一条 shell 命令；结果通过 PendingIntent 回传给 TaskResultReceiver。 */
    static boolean run(Context ctx, String cmdId, String label, String command, boolean isStatus) {
        try {
            Intent r = new Intent(ctx, TaskResultReceiver.class);
            r.setAction("io.dsh.console.RESULT." + cmdId + "." + System.currentTimeMillis());
            r.putExtra("cmdId", cmdId).putExtra("label", label).putExtra("isStatus", isStatus);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_ONE_SHOT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE; // Termux 要往里塞结果
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
            Last.set(ctx, "启动 Termux 命令失败：" + t);
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

    // ---- 常用命令 ----
    static String statusCmd() {
        // --brief：不要每个任务的 tail。实测完整包 6463 字节、--brief 只有 1560，
        // 而 App 侧的回传有长度上限（见 TaskResultReceiver）——包太大就会被截断、JSON 解析失败。
        return HOME + "/.local/bin/dsh-status-pub --json --brief";
    }
    static String taskCmd(String id) {
        return HOME + "/.shortcuts/tasks/" + id + ".sh";
    }
    static String openUiCmd() {
        return HOME + "/.local/bin/dsh-browser-open \"$(cat " + HOME + "/.dsh-url)\"";
    }
}
