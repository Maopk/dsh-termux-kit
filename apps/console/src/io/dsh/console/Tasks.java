package io.dsh.console;

/**
 * Task list: the id is the script name under ~/.shortcuts/tasks on the Termux side (that side owns the
 * allowlist; this is only the menu).
 *
 * Since v0.5 every task carries a **function category** cat, and the UI shows them in sections instead
 * of stacking every button in one column. The category is display-only; the widget (DshWidget) looks
 * only at WIDGET_SAFE and is unaffected.
 */
final class Tasks {
    /** Start / stop: changes DSH's own running state. */
    static final String RUN = "Start / Stop";
    /** Channels: the bridge (accessibility loopback, no network) and adb (wireless debugging, needs working Wi-Fi) are two independent channels, always listed separately. */
    static final String LINK = "Channels (adb and bridge separate)";
    /** Maintenance: does not change run state, only touches artifacts on disk. */
    static final String CARE = "Maintenance";
    /** Emergency: gives up control; listed last but kept most prominent (red). */
    static final String SOS = "Emergency";
    /** The UI uses this order for its sections. */
    static final String[] CATS = { RUN, LINK, CARE, SOS };

    static final class T {
        final String id, label, hint, cat;
        final boolean danger;
        /** Non-null means run this command directly (without the wrapper script in ~/.shortcuts/tasks). */
        final String cmd;
        /**
         * How long this command **usually takes at most** (seconds). Why it exists: every task used to
         * share one 45-second timeout, so tasks that inherently need 1-5 minutes (backup/cleanup/restart)
         * **timed out every single time** (false alarms) while real failures were indistinguishable from
         * them. Each task now gets its own window, and the text says "this one usually answers within Ns".
         */
        final int waitS;
        T(String id, String cat, String label, String hint, boolean danger) {
            this(id, cat, label, hint, danger, null, 90);
        }
        T(String id, String cat, String label, String hint, boolean danger, String cmd) {
            this(id, cat, label, hint, danger, cmd, 90);
        }
        T(String id, String cat, String label, String hint, boolean danger, String cmd, int waitS) {
            this.id = id; this.cat = cat; this.label = label; this.hint = hint;
            this.danger = danger; this.cmd = cmd; this.waitS = waitS;
        }
    }

    static final T[] ALL = new T[] {
        // ── Start / Stop ─────────────────────────────────────────────
        new T("1_start-dsh", RUN, "Start DSH", "Opens the page directly if it is already running; includes the start mutex and a real readiness check", false, null, 240),
        new T("open", RUN, "Open Web UI", "Opens the page at the address in ~/.dsh-url (prefers the desktop PWA)", false, TermuxRunner.openUiCmd(), 60),
        new T("4_soft-restart-dsh", RUN, "Soft restart", "Restarts after SIGTERM; drops the current web session", true, null, 300),
        new T("6_hard-restart-dsh", RUN, "Hard restart", "Restarts after a -9 kill; drops the current web session", true, null, 300),
        new T("2_shutdown-dsh", RUN, "Shut down DSH", "Stops DSH and closes the browser; the bridge is a separate channel and is left alone by default", true, null, 200),

        // ── Channels ───────────────────────────────────────────────────
        new T("8_enable-wireless-adb", LINK, "Connect adb", "Wireless debugging only: set the switch → find the port → adb connect (needs working Wi-Fi)", false, null, 220),
        // ⚠ These two must use **absolute paths**: RUN_COMMAND goes through `bash -lc`, and its measured
        //   PATH is only /data/data/com.termux/files/usr/bin:. — no ~/.local/bin, so when they were
        //   written as a bare `dsh-bridge wake` both returned exit=127 (command not found).
        new T("bridge_wake", LINK, "Wake bridge", "Accessibility loopback only: a broadcast carrying the token, no network needed", false,
                TermuxRunner.HOME + "/.local/bin/dsh-bridge wake", 90),
        new T("bridge_status", LINK, "Check bridge status", "Bridge only: port / whether it really answers / version / paused", false,
                TermuxRunner.HOME + "/.local/bin/dsh-bridge status", 40),
        new T("7_reconnect-ai", LINK, "Restore everything (adb + bridge)", "For when you need both channels: adb first, then the bridge; a failure names the channel it came from", false, null, 280),

        // ── Maintenance ───────────────────────────────────────────────────
        // Note: "password access" (whether the AI may use the user's lock-screen password to pass
        // identity checks) is **a switch, not a button**, drawn under the maintenance section (see
        // MainActivity.buildAuthRow) — it governs more than package installs.
        new T("3_backup-dsh", CARE, "Backup", "Packs and verifies the archive (zstd -t + entry count)", false, null, 420),
        new T("5_cleanup-dsh", CARE, "Cleanup", "Deletes only my own artifacts; never touches config or notes", false, null, 420),

        // ── Emergency ───────────────────────────────────────────────────
        new T("0_emergency-stop", SOS, "Emergency stop", "Revokes the AI's control of the phone (bridge stop + revoke token)", true, null, 180),
    };

    /** Only safe actions go on the widget — a mis-tap from the home screen costs too much. */
    static final String[] WIDGET_SAFE = { "1_start-dsh", "8_enable-wireless-adb", "bridge_wake", "3_backup-dsh" };
}
