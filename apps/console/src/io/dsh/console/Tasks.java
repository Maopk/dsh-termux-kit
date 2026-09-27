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
     * Only safe actions go on the home-screen widget: a mis-tap there costs too much.
     * Must stay in step with the `widget` surface in ui/controls.json — check-task-ids verifies that.
     */
    static final String[] WIDGET_SAFE = { "1_start-dsh", "8_enable-wireless-adb", "bridge_wake", "status_refresh" };
}
