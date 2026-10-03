package io.dsh.console;

import java.util.ArrayList;
import java.util.List;

/**
 * Execution table for the console's task buttons: **id → how to run it**, nothing else.
 *
 * The labels, hints, categories and the danger flag used to live here too, which meant the same control
 * was described in this file, in the page panel and in the bridge app — three descriptions that drift.
 * Since 2026-09-27 all of that comes from the generated {@link UiControls} (source: ui/controls.json),
 * and this class keeps only what is genuinely console-side: the command to run and how long it usually
 * needs before the app should stop waiting.
 *
 * Why per-task waitS: every task once shared one 45-second timeout, so backup/cleanup/restart — which
 * inherently need 1-5 minutes — timed out every single time (false alarms), and a real failure looked
 * exactly the same. Each entry now carries its own window, and the UI says so while waiting.
 */
final class Tasks {

    static final class T {
        final String id;
        /** Non-null means run this command directly instead of the widget script of the same id. */
        final String cmd;
        final int waitS;
        T(String id, String cmd, int waitS) { this.id = id; this.cmd = cmd; this.waitS = waitS; }
        T(String id, int waitS) { this(id, null, waitS); }
    }

    static final T[] ALL = new T[] {
        // ── Start / stop ──────────────────────────────────────────────
        new T("1_start-dsh", 240),
        new T("4_soft-restart-dsh", 300),
        new T("6_hard-restart-dsh", 300),
        new T("2_shutdown-dsh", 200),
        // "Open Web UI" carries its own command and never goes through tasksd's allowlist.
        new T("open", TermuxRunner.openUiCmd(), 60),
        // ── Channels: adb (wireless debugging, needs Wi-Fi) ───────────
        new T("adb_ensure", 220),
        new T("8_enable-wireless-adb", 220),
        // ── Channels: bridge (accessibility loopback, no network) ────
        new T("bridge_wake", TermuxRunner.HOME + "/.local/bin/dsh-bridge wake", 90),
        new T("bridge_status", TermuxRunner.HOME + "/.local/bin/dsh-bridge status", 40),
        // The bridge run switch needs both directions as real tasks (on = wake, off = soft stop).
        new T("bridge_stop", TermuxRunner.HOME + "/.local/bin/dsh-bridge stop", 60),
        new T("7_reconnect-ai", 280),
        new T("10_net-fix", 300),
        // ── Maintenance ──────────────────────────────────────────────
        new T("11_update-apps", 420),
        new T("3_backup-dsh", 420),
        // The self-test suite (tests/selftest.sh) checks this phone: widget syntax, every widget's
        // --dry-run, the readiness regressions, real runs that are safe and reversible, then a
        // throwaway cold start on port 8099. It takes minutes and does load the phone, so it is a
        // console-only button and the wait window is generous — that window is only a UI guard, the
        // suite keeps running in Termux even after the dialog gives up. The kit path follows the same
        // default as the rest of the suite (DSH_KIT_REPO, else ~/dsh-termux-kit).
        // --json leaves ~/.smoke/selftest.json behind: the same run, in the shape CI judges
        // (tests/report_check.py) — a phone run can be handed to the policy check unchanged.
        new T("selftest", "bash \"${DSH_KIT_REPO:-$HOME/dsh-termux-kit}/tests/selftest.sh\" --json", 900),
        new T("5_cleanup-dsh", 300),
        // Full stop is a one-way door on this ROM; it is dangerous but belongs to Maintenance (user's call).
        new T("bridge_full_stop", TermuxRunner.HOME + "/.local/bin/dsh-bridge off", 60),
        // ── Emergency ────────────────────────────────────────────────
        new T("0_emergency-stop", 180),
        // ── Status / log ─────────────────────────────────────────────
        new T("status_refresh", TermuxRunner.statusCmd(), 25),
    };

    static T get(String id) {
        for (T t : ALL) if (t.id.equals(id)) return t;
        return null;
    }

    /** Wait window for an id, with a sane default for anything the table does not know. */
    static int waitOf(String id) {
        T t = get(id);
        return t == null ? 90 : t.waitS;
    }

    /** Command override for an id (absolute path), or null to let tasksd run the widget script. */
    static String cmdOf(String id) {
        T t = get(id);
        return t == null ? null : t.cmd;
    }

    static List<T> all() { return new ArrayList<T>(java.util.Arrays.asList(ALL)); }

    /**
     * 这两个动作跑完后，控制台要去读脚本回传的页面协议行（DSH_AUTH_URL= / DSH_PWA_PKG= / DSH_BOOT_OPEN=）。
     *
     * ⚠ 这里**不**代表"页面由控制台打开"。第一版是这么以为的：当时判断 Termux 在后台发 `am start`
     * 一定会被系统静默拦掉，于是让脚本 `--no-open`、控制台自己开。2026-09-28 08:05 的日志把这条判断
     * 推翻了 —— 那次 `--no-open` 照样有页面出现，因为 **`dsh web` 自己就会开浏览器**
     * （`dsh-web-app/lib/index.js`: "dsh web: opening the default browser; pass --no-open to disable"）；
     * 同一个上午，后台的 Termux 还靠 `am start` 把 WebAPK 窗口拉到了前台（用户就是被那一下拽过去的）。
     * 所以现在**不猜谁开得成**：脚本把"有没有开、谁开的"当事实回传，控制台只在没人开的时候才自己开。
     */
    static boolean consoleOpensPage(String id) {
        return "1_start-dsh".equals(id) || "open".equals(id);
    }

    /**
     * 控制台这条路上真正要跑的命令。与 {@link #ALL} 的差别只有一处，说清如下：
     * · 更新类：把控制台**自己的版本号**带给脚本 —— 本 App 最清楚自己装的是哪一版，
     *   脚本靠它才能判断"仓库是不是比本机旧"（本机领先就绝不降级；没有这个值它只能读 adb 或跳过）。
     * · 开页面类（`open`）：不跑 `dsh-browser-open`，只取地址，由控制台自己开。
     * · 启动类：**与 ALL 完全一致**（不加 `--no-open`）—— 让脚本/服务在就绪的那一刻就把页面打开，
     *   这是最早的时刻；等控制台收到回传再开，要排在 restore_channels（最长 180 秒）后面了。
     */
    static String consoleCmd(String id, String ver) {
        if ("open".equals(id)) return TermuxRunner.pageInfoCmd();
        if ("11_update-apps".equals(id)) {
            String v = ver == null ? "" : ver.trim().replaceFirst("^v", "");
            return TermuxRunner.taskCmd(id) + (v.isEmpty() ? "" : " --console-ver " + v);
        }
        T t = get(id);
        return t != null && t.cmd != null ? t.cmd : TermuxRunner.taskCmd(id);
    }

    /**
     * Only safe actions go on the home-screen widget: a mis-tap there costs too much.
     * Must stay in step with the `widget` surface in ui/controls.json — check-task-ids verifies that.
     */
    static final String[] WIDGET_SAFE = { "1_start-dsh", "8_enable-wireless-adb", "bridge_wake", "status_refresh" };
}
