package io.dsh.bridge;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Tiny built-in two-language table for the bridge UI (and its notification).
 *
 * The bridge has no RUN_COMMAND permission, so unlike the Console app it cannot ask Termux for the
 * value of ~/.dsh-lang. Instead:
 *   · its own choice is persisted in SharedPreferences, and
 *   · the Termux side can push the current language into it over the loopback socket
 *     (`droid-sock lang <zh|en|auto>` / `dsh-lang set …` does it for you), so the app, the page panel
 *     and the widgets end up on the same language.
 * A key that is missing returns the English string unchanged (degrades to one English line).
 */
final class Lang {
    static final String[] MODES = {"auto", "zh", "en"};
    private static final Map<String, String> ZH = new HashMap<>();
    private static String mode = "auto";
    private static String cur = "en";

    static {
        ZH.put("DSH Bridge", "DSH 桥");
        ZH.put("Copy token", "复制 token");
        ZH.put("Token copied; you can paste it to DSH", "token 已复制，可以粘贴发给 DSH");
        ZH.put("① Open accessibility settings", "① 打开无障碍设置");
        ZH.put("② Refresh state / resume listening", "② 刷新状态 / 恢复监听");
        ZH.put("③ Open this app's notification settings (so notification-bar emergency stop works)",
                "③ 打开本应用的通知设置（让通知栏里的紧急停止可用）");
        ZH.put("Emergency stop (turn accessibility off now)", "紧急停止（立即关闭无障碍）");
        ZH.put("Emergency stop", "紧急停止");
        ZH.put("Accessibility: ", "无障碍：");
        ZH.put("Port: ", "端口：");
        ZH.put(" on", " 已开启");
        ZH.put(" off", " 已关闭");
        ZH.put("Not listening", "未监听");
        ZH.put("Listening on 127.0.0.1:", "正在监听 127.0.0.1:");
        ZH.put("Idle auto-stop: ", "空闲自动停止：");
        ZH.put("off (always listening)", "已关闭（一直监听）");
        ZH.put(" min idle → stop", " 分钟无指令则停止");
        ZH.put(" · tap to switch", " · 点此切换");
        ZH.put("Idle auto-stop is off", "已关闭空闲自动停止");
        ZH.put("Auto-stop after ", "空闲 ");
        ZH.put(" min idle", " 分钟自动停止");
        ZH.put("Language: ", "语言：");
        ZH.put("Runs as a foreground service so system/vendor battery savers cannot freeze it and lose contact.", "以前台服务方式常驻，系统/厂商省电策略无法冻结它、导致失联。");
        ZH.put("Emergency", "紧急");
        ZH.put("Behaviour", "行为");
        ZH.put("Setup", "设置");
        ZH.put("★ Emergency stop (any of the four):\\n", "★ 紧急停止（四种方式任选）：\n");
        ZH.put("Lets the DSH inside Termux operate the phone for you (read screen / tap / swipe / type).\\n", "让 Termux 里的 DSH 替你操作这台手机（读屏 / 点击 / 滑动 / 输入）。\n");
        ZH.put("   3. Emergency stop inside the DSH Bridge is running notification\\n", "   3. 「DSH 桥正在运行」通知里的紧急停止\n");
        ZH.put("   2. Hold volume + and volume − together for 3 seconds\\n", "   2. 音量＋ 和 音量－ 同时按住 3 秒\n");
        ZH.put("   1. The red button below\\n", "   1. 下面那个红色按钮\n");
        ZH.put("\nTap ① Open accessibility settings and switch DSH Bridge on in the list", "\n点 ① 打开无障碍设置，在列表里把「DSH 桥」打开");
        ZH.put("   4. The Termux home-screen widget item 0_emergency-stop", "   4. Termux 桌面组件里的 0_emergency-stop");
        ZH.put("The only permissions are accessibility and the local loopback port 127.0.0.1:8788; nothing is uploaded.", "唯一的权限是无障碍和本机回环端口 127.0.0.1:8788；任何内容都不上传。");
        ZH.put("(The volume-key gesture, the in-app red button and the Termux widget are unaffected)", "（音量键手势、App 里的红色按钮、Termux 组件都不受影响）");
        ZH.put("   Tap ③ Open this app's notification settings below to enable it.", "   点下面的 ③ 打开本应用的通知设置即可启用。");
        ZH.put("⚠️ Notification permission is off: the notification-bar emergency stop layer is currently dead.", "⚠️ 通知权限被关：通知栏里的那条紧急停止保险目前是失效的。");
        ZH.put("(new in v1.5: turns wireless debugging on automatically when online, used by widget 8)", "（v1.5 新增：联网时自动打开无线调试，组件 8 会用到）");
        ZH.put("v1.6 key change: switched to a **foreground service** to stay resident, so system/vendor battery savers cannot freeze it and lose contact.", "v1.6 关键变更：改为**前台服务**常驻，系统/厂商省电策略就无法冻结它、导致失联。");
        ZH.put("token: ", "token：");
        ZH.put("tap to switch", "点此切换");
        ZH.put("off ❌", "已关闭 ❌");
        ZH.put("on ✅", "已开启 ✅");
        ZH.put("System", "跟随系统");
        ZH.put("Chinese", "中文");
        ZH.put("English", "English");
        ZH.put("Tap to switch (auto → 中文 → English)", "点此切换（跟随系统 → 中文 → English）");
        ZH.put("DSH Bridge is running", "DSH 桥正在运行");
        ZH.put(" · hold volume +/- for 3s to stop", " · 音量+/- 同时按住 3 秒紧急停止");
        ZH.put("Token copied", "token 已复制");
        ZH.put("Failed to open settings: ", "打开设置失败：");
        ZH.put("Cannot open notification settings: ", "打不开通知设置：");
        ZH.put("Emergency stop done; the accessibility service is off", "已紧急停止，无障碍服务已关闭");
        ZH.put("The service is not running right now; to shut it down completely, turn DSH Bridge off in the system accessibility settings",
                "服务当前未运行；如需彻底关闭，请在系统无障碍设置里关掉「DSH 桥」");
        ZH.put("DSH Bridge started: hold volume +/- together for 3s for emergency stop",
                "DSH 桥已启动：音量+/- 同时按住 3 秒可紧急停止");
        ZH.put("DSH Bridge stays stopped (you turned it off earlier); tap widgets 1/7/8 to resume",
                "DSH 桥保持停止（你之前关过它）；点组件 1/7/8 可恢复");
        ZH.put("DSH Bridge emergency-stopped (", "DSH 桥已紧急停止（");
        ZH.put("DSH Bridge: ", "DSH 桥：");
        ZH.put("idle for ", "空闲 ");
        ZH.put(" min with no commands; listening stopped automatically", " 分钟无指令，已自动停止监听");
        ZH.put("accessibility turned off", "无障碍已关闭");
        ZH.put("service destroyed", "服务已销毁");
        ZH.put("Emergency stop: ", "紧急停止：");
        ZH.put("sleep command received: fully stopped (no self-recovery)", "收到 sleep 指令：已彻底停止（不会自恢复）");
        ZH.put("stop command received (remembered: no automatic recovery)", "收到 stop 指令（已记住：不会自动恢复）");
        ZH.put("DSH Bridge is no longer running", "DSH 桥已不在运行");
        ZH.put("(Also: this app does not autostart; it will not come up by itself after a phone restart)",
                "（另外：本应用不自动启动；手机重启后它不会自己起来）");
        ZH.put("Lets the DSH inside Termux operate the phone for you (read screen / tap / swipe / type).",
                "让 Termux 里的 DSH 替你操作这台手机（读屏 / 点击 / 滑动 / 输入）。");
    }

    private Lang() {}

    static String systemLang() {
        String l = Locale.getDefault().getLanguage();
        return l == null ? "en" : l;
    }

    /** auto follows the system language, otherwise the stored mode wins. */
    static void init(android.content.Context c) {
        try {
            String saved = c.getSharedPreferences("dsh", android.content.Context.MODE_PRIVATE).getString("lang", null);
            if (saved != null) mode = saved;
        } catch (Throwable ignore) {}
        resolve();
    }

    private static void resolve() {
        cur = mode.equals("auto") ? (systemLang().startsWith("zh") ? "zh" : "en") : mode;
    }

    static String mode() { return mode; }
    static boolean isZh() { return cur.equals("zh"); }

    static void setMode(android.content.Context c, String v) {
        if (v.equals("zh") || v.equals("en") || v.equals("auto")) {
            mode = v;
            resolve();
            try {
                c.getSharedPreferences("dsh", android.content.Context.MODE_PRIVATE)
                        .edit().putString("lang", v).apply();
            } catch (Throwable ignore) {}
        }
    }

    static String t(String en) {
        if (!cur.equals("zh") || en == null) return en;
        String z = ZH.get(en);
        return z == null ? en : z;
    }
}
