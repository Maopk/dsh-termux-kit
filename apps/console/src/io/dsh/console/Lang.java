package io.dsh.console;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny built-in two-language table (named Lang, not L: MainActivity has a local JSONObject called L).
 *
 * Why not the Android way: this app is built with plain javac + android.jar, so there is no
 * AndroidX and therefore no AppCompatDelegate.setApplicationLocales(). A small table plus a
 * preference (and recreate()) gives the same result with zero dependencies.
 *
 * The switch writes the SAME file the shell scripts read - ~/.dsh-lang - so the app, the DSH page
 * panel and the 10 widgets never disagree about the current language. That file lives in Termux's
 * private directory, which this app cannot touch directly, so it is written by running
 * "dsh-lang set <v>" through the RUN_COMMAND channel instead.
 *
 * A key that is missing returns the English string unchanged, so a gap shows one English line
 * rather than an empty label.
 */
final class Lang {
    static final String LANG_FILE = "/data/data/com.termux/files/home/.dsh-lang";
    static final String[] MODES = {"auto", "zh", "en"};
    private static final Map<String, String> ZH = new HashMap<>();
    private static String mode = "auto";
    private static String cur = "en";

    static {
        ZH.put("(no entries yet)\n\n", "（还没有记录）\n\n");
        ZH.put("(no result bundle received; Termux may not be authorized or allow-external-apps is off)\n", "(没有收到结果 bundle；可能是 Termux 未授权或 allow-external-apps 未开)\n");
        ZH.put("6 digits", "6 位数字");
        ZH.put("Authorize", "授权");
        ZH.put("Authorize the AI to use your password", "授权 AI 使用你的密码");
        ZH.put("Authorized", "已授权");
        ZH.put("Backup", "备份");
        ZH.put("Bridge offline", "桥离线");
        ZH.put("Bridge online", "桥在线");
        ZH.put("Bridge status", "桥状态");
        ZH.put("Cancel", "取消");
        ZH.put("Channels (adb and bridge separate)", "通道（adb 与桥分开）");
        ZH.put("Chinese", "中文");
        ZH.put("Cleanup", "清理");
        ZH.put("Clear", "清空");
        ZH.put("Close", "关闭");
        ZH.put("Commands run through Termux (RUN_COMMAND channel); this app has no storage, network, or accessibility permission.\n", "命令经 Termux 执行（RUN_COMMAND 通道）；本 App 无存储/网络/无障碍权限。\n");
        ZH.put("Confirm", "确认");
        ZH.put("Confirm revoke", "确认收回");
        ZH.put("Connect", "连接");
        ZH.put("Connect adb", "连 adb");
        ZH.put("Copy all", "复制全部");
        ZH.put("DSH Console", "DSH 控制台");
        ZH.put("Emergency", "紧急");
        ZH.put("Emergency stop", "紧急停止");
        ZH.put("Every run is recorded in the log: sent, callback, exit code, raw output.", "所有执行记录都在「日志」里：发送、回传、退出码、原始输出。");
        ZH.put("Failed to start the Termux command: ", "启动 Termux 命令失败：");
        ZH.put("For when you need both channels: adb first, then the bridge; a failure names the channel it came from", "两条通道都要时用：先 adb，再桥；哪条失败会说哪条");
        ZH.put("Hard restart", "硬重启");
        ZH.put("Language", "语言");
        ZH.put("Log", "日志");
        ZH.put("Log cleared", "日志已清空");
        ZH.put("Maintenance", "维护");
        ZH.put("No reply the first time, resending: ", "第一次没回音，正在补发：");
        ZH.put("No status yet — tap Refresh status", "还没有状态 —— 点「刷新状态」");
        ZH.put("Not authorized", "未授权");
        ZH.put("Now: authorized (the AI can pass identity checks for you)", "当前：已授权（AI 可代你过身份验证）");
        ZH.put("Now: not authorized (any password-protected check needs you in person)", "当前：未授权（任何需要密码的验证都得你本人来）");
        ZH.put("OK", "确定");
        ZH.put("Open Web UI", "打开 Web UI");
        ZH.put("Password access", "密码使用权");
        ZH.put("Reading authorization state…", "正在读取授权状态…");
        ZH.put("Reading status…", "正在读取状态…");
        ZH.put("Reconnect AI", "重连 AI 通道");
        ZH.put("Refresh", "刷新");
        ZH.put("Refresh status", "刷新状态");
        ZH.put("Restore everything (adb + bridge)", "全部恢复（adb + 桥）");
        ZH.put("Revokes the AI's control of the phone (bridge stop + revoke token)", "撤销 AI 对手机的控制（桥 stop + 撤 token）");
        ZH.put("Run", "执行");
        ZH.put("Run ", "确认执行「");
        ZH.put("Running", "正在运行");
        ZH.put("Running: ", "正在执行：");
        ZH.put("Running: X", "正在执行：X");
        ZH.put("Sent: ", "已发送：");
        ZH.put("Shut down DSH", "关闭 DSH");
        ZH.put("Soft restart", "软重启");
        ZH.put("Start", "启动");
        ZH.put("Start / Stop", "启动 / 停止");
        ZH.put("Start DSH", "启动 DSH");
        ZH.put("State: unreadable (no callback from Termux; tap Refresh status to retry)", "状态：读不到（Termux 没回传，点「刷新状态」重试）");
        ZH.put("Status parse failed (callback may be truncated): ", "状态解析失败（回传可能被截断）：");
        ZH.put("Status parse failed: ", "状态解析失败：");
        ZH.put("Stopped", "已停止");
        ZH.put("System", "跟随系统");
        ZH.put("Tap again to confirm", "再点一次确认");
        ZH.put("Task", "任务");
        ZH.put("Tasks", "任务");
        ZH.put("Turn the switch off at any time to revoke.", "随时把开关关掉即可收回。");
        ZH.put("Type that 6-digit lock-screen password → it is written to ~/.dsh-auth-pass (600).\n", "输入那 6 位锁屏密码 → 写进 ~/.dsh-auth-pass（600）。\n");
        ZH.put("Used only to pass system identity checks for you (installing packages, lifting settings restrictions, etc.).\n", "用途仅限：替你过系统身份验证（装包、解除设置限制等）。\n");
        ZH.put("Wake", "唤醒");
        ZH.put("Wake bridge", "唤醒桥");
        ZH.put("\n   · RUN_COMMAND permission not granted, or allow-external-apps is disabled in Termux", "\n   · 未授予 RUN_COMMAND 权限、或 Termux 里 allow-external-apps 被关掉");
        ZH.put("\n   · The phone was busy at the time (a backup / self-check running in the background) — most common, just tap Refresh status and retry", "\n   · 手机当时很忙（后台在跑备份/自检之类）—— 最常见，点「刷新状态」再试即可");
        ZH.put("\n   · The system blocked starting a service from the background (most common on widget taps)", "\n   · 系统拦了「从后台启动服务」（小部件点击最容易遇到）");
        ZH.put("\n   · This is a long task (backups and restarts are slow anyway) — give it more time", "\n   · 这次是长任务（备份/重启本来就慢），可以再等等看");
        ZH.put("\n(no output this time)", "\n（这次没有输出）");
        ZH.put("\n\n⚠ Confirmation required: restart/shutdown drops the current web session; ", "\n\n⚠ 需要二次确认：重启/关闭会断开当前网页会话；");
        ZH.put("adb connected", "adb 已连接");
        ZH.put("adb not connected", "adb 未连接");
        ZH.put("being briefly busy", "忙一下");
        ZH.put("done", "完成");
        ZH.put("failed", "失败");
        ZH.put("felt like a shell", "像壳子");
        ZH.put("greying out", "变灰");
        ZH.put("last result", "最近一次结果");
        ZH.put("not connected", "未连接");
        ZH.put("query", "查询类");
        ZH.put("revoke-style actions do not come back on their own (re-authorize to restore).", "撤销类动作不会自动恢复（要恢复得重新授权）。");
        ZH.put("running", "在跑");
        ZH.put("s (this one usually answers within ", " 秒没收到 Termux 回传（这条通常 ");
        ZH.put("s). Likely causes, most common first:", " 秒内回来）。按可能性排：");
        ZH.put("sent silently", "静默发出");
        ZH.put("skipped", "未执行");
        ZH.put("stopped", "停了");
        ZH.put("· Empty output still gets a (no output this time) line, so nothing is left blank\n", "· 输出为空也会写明「这次没有输出」，不会留白\n");
        ZH.put("· Tap Refresh status to read the three lamps\n", "· 点「刷新状态」读三个灯\n");
        ZH.put("· Tap any task button to run it: sent, callback, exit code and raw output are all recorded here\n", "· 点任意任务按钮执行：发送、回传、退出码、原始输出都记在这里\n");
        ZH.put("● Bridge", "● 桥");
        ZH.put("● DSH", "● DSH");
        ZH.put("● DSH　● Bridge　● adb", "● DSH　● 桥　● adb");
        ZH.put("● adb", "● adb");
        ZH.put("⚠ Failed to start the Termux command.", "⚠ 启动 Termux 命令失败。");
        ZH.put("⚠ No callback from Termux after ", "⚠ 等了 ");
        ZH.put("✅ Refresh status exit=0", "✅ 刷新状态 exit=0");
        ZH.put("　(tap here for the log)", "　（点这里看日志）");
        ZH.put("　waited 0s", "　已等待 0s");
    }

    private Lang() {}

    static String systemLang() {
        String l = Locale.getDefault().getLanguage();
        return l == null ? "en" : l;
    }

    static String readMode() {
        try {
            File f = new File(LANG_FILE);
            if (f.canRead()) {
                BufferedReader r = new BufferedReader(new FileReader(f));
                String v = r.readLine();
                r.close();
                if (v != null) {
                    v = v.trim();
                    if (v.equals("zh") || v.equals("en") || v.equals("auto")) return v;
                }
            }
        } catch (Throwable ignored) {}
        return mode;
    }

    static void init() {
        mode = readMode();
        cur = mode.equals("auto") ? (systemLang().startsWith("zh") ? "zh" : "en") : mode;
    }

    static String mode() { return mode; }
    static boolean isZh() { return cur.equals("zh"); }

    static void setMode(String v) {
        if (v.equals("zh") || v.equals("en") || v.equals("auto")) {
            mode = v;
            cur = v.equals("auto") ? (systemLang().startsWith("zh") ? "zh" : "en") : v;
        }
    }

    static String t(String en) {
        if (!cur.equals("zh") || en == null) return en;
        String z = ZH.get(en);
        return z == null ? en : z;
    }
}
