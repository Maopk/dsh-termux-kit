package io.dsh.console;

import java.util.ArrayList;
import java.util.List;

/**
 * GENERATED FILE — do not edit. Source: ui/controls.json, generator: tools/ui-controls.
 *
 * Why generated: the same controls must read identically in the bridge app, the console app and
 * the page panel, and three hand-kept copies drift apart (that is exactly what happened before).
 * Label/hint are **English keys**: render them with Lang.t() so the runtime language switch
 * still works; the Chinese side lives in i18n/zh.json, the kit's single translation source.
 */
final class UiControls {
    static final String VERSION = "1.22";

    static final class C {
        final String id, kind, cat, group, icon, labelEn, hintEn;
        final boolean danger;
        /** true = draw the red outline. Only for the one action that cannot be undone
         *  (closing DSH). The user asked for this on 2026-09-27: three red-outlined
         *  buttons side by side made none of them feel dangerous. */
        final boolean redBorder;
        C(String id, String kind, String cat, String group, String icon, String labelEn, String hintEn, boolean danger, boolean redBorder) {
            this.id = id; this.kind = kind; this.cat = cat; this.group = group;
            this.icon = icon; this.labelEn = labelEn; this.hintEn = hintEn; this.danger = danger;
            this.redBorder = redBorder;
        }
    }

    /** Category order on screen, and the two column names (id, en, zh). */
    static final String[][] CATS = { {"startstop", "Start / Stop", "启动 / 停止"}, {"channels", "Channels (adb and bridge separate)", "通道（adb 与桥分开）"}, {"maintenance", "Maintenance", "维护"}, {"security", "Security", "安全"}, {"settings", "Settings", "设置"}, {"emergency", "Emergency", "紧急"}, {"statuslog", "Status / Log", "状态 / 日志"}, {"about", "About this app", "关于本应用"} };
    static final String[][] GROUPS = { {"adb", "channels", "adb · wireless debugging, needs working Wi-Fi", "adb · 无线调试，需要可用 Wi-Fi"}, {"bridge", "channels", "bridge · accessibility loopback, no network", "桥 · 无障碍回环，不需要网络"}, {"danger", "channels", "Danger zone · only you can undo it by hand", "危险操作 · 只能你手动恢复"} };

    static final C[] ALL = {
        new C("bridge_run", "switch", "channels", "bridge", "🌉", "Bridge running", "On = DSH may drive the phone through this app (listening on 127.0.0.1:8788). Off = soft stop: the port closes, the process stays, and one broadcast brings it back.", false, false),
        new C("bridge_state_text", "text", "channels", "bridge", "·", "Bridge state", "Four states from one ping plus the state note (no timers): running / just dropped (wakeable, one broadcast brings it back) / long silent (the process went away on its own or is bound but not answering - waking may work, otherwise open DSH Bridge once) / not installed (this install has never been seen alive here).", false, false),
        new C("bridge_wake", "button", "channels", "bridge", "🌉", "Wake bridge", "Sends one token-carrying broadcast. Never takes your screen. Can take 20-40s if the process was reclaimed.", false, false),
        new C("bridge_status", "button", "channels", "bridge", "🔎", "Bridge status", "Read-only: is the port listening, does it really answer, version, paused flag.", false, false),
        new C("7_reconnect-ai", "button", "channels", "bridge", "🔗", "Restore both channels", "adb first, then the bridge. Use only when you need both; a failure names the channel it came from.", false, false),
        new C("1_start-dsh", "button", "startstop", "", "▶", "Start DSH", "Starts DSH and opens the page; if it is already running it only opens the page.", false, false),
        new C("4_soft-restart-dsh", "button", "startstop", "", "↻", "Soft restart", "Restarts the service with SIGTERM. Cost: drops the current web session.", true, false),
        new C("6_hard-restart-dsh", "button", "startstop", "", "⛔", "Hard restart", "Restarts after a kill -9 and clears the orphan lock. Cost: drops the current web session.", true, false),
        new C("2_shutdown-dsh", "button", "startstop", "", "■", "Stop DSH", "Stops DSH and closes the browser. The bridge is a separate channel and is left alone by default.", true, true),
        new C("adb_ensure", "button", "channels", "adb", "🔌", "Repair adb channel", "Wi-Fi state → Wireless debugging switch (written through the bridge) → port → connect → verify. Says which step failed.", false, false),
        new C("8_enable-wireless-adb", "button", "channels", "adb", "⚡", "Connect adb", "Wireless debugging only. Needs a real Wi-Fi network, not just the switch.", false, false),
        new C("bridge_full_stop", "button", "channels", "danger", "⏻", "Fully stop bridge", "Lets the App exit and unbinds accessibility. On this vivo it may not be wakeable again - you would have to open DSH Bridge by hand.", true, false),
        new C("adb_lamp", "lamp", "statuslog", "", "●", "adb", "Green = usable, yellow = connecting, red = failed, grey = not connected (check Wi-Fi).", false, false),
        new C("10_net-fix", "button", "maintenance", "", "🩺", "Network first aid", "When foreign sites die: decides whether the Clash core stopped or the config went bad, then repairs.", false, false),
        new C("11_update-apps", "button", "maintenance", "", "⬆", "Update the two apps", "Downloads the two APKs from GitHub Releases, verifies SHA256, then installs them.", false, false),
        new C("3_backup-dsh", "button", "maintenance", "", "💾", "Backup", "Packs DSH state and verifies the archive; the result goes to Download/dsh/.", false, false),
        new C("selftest", "button", "maintenance", "", "🩺", "Self-check", "Runs the whole self-test suite in Termux (70 checks: syntax, dry-runs, regression, real runs, a cold-start sandbox); the last line is the verdict. It takes minutes and loads the phone — pick a quiet moment. Script: ~/dsh-termux-kit/tests/selftest.sh", false, false),
        new C("5_cleanup-dsh", "button", "maintenance", "", "🧹", "Cleanup", "Deletes only this kit's own artifacts. Your files are not touched.", false, false),
        new C("lang", "switch", "settings", "", "🌐", "Language", "System / Chinese / English. The widgets and the page panel follow this too.", false, false),
        new C("project-page", "button", "settings", "", "🔗", "Project page", "Opens github.com/Maopk/dsh-termux-kit - source, releases and docs.", false, false),
        new C("version-update", "text", "settings", "", "·", "Version", "Installed version and whether a newer release exists. If the check fails it says so instead of claiming it is up to date.", false, false),
        new C("password-access", "switch", "security", "", "🔑", "Password access", "On = the AI may use your 6-digit lock-screen password to pass system verification for you (installing packages, removing settings restrictions). Off = revoked at once. It is never used to unlock the phone and read content, to pay, or for anything unrelated to the task at hand.", true, false),
        new C("0_emergency-stop", "button", "emergency", "", "🛑", "Emergency stop", "Revokes the AI's control of the phone: soft-stops the bridge and revokes the token. adb is a separate channel and stays up.", true, false),
        new C("lamp_dsh", "lamp", "statuslog", "", "●", "DSH", "Green = the web service answers, yellow = half-started, red = down, grey = not running.", false, false),
        new C("lamp_bridge", "lamp", "statuslog", "", "●", "Bridge", "Green = listening and answering, grey = soft-stopped or not installed.", false, false),
        new C("status_refresh", "button", "statuslog", "", "🔄", "Refresh status", "Reads the state once and updates the lamps. Read-only: it never wakes a channel.", false, false),
        new C("log", "button", "statuslog", "", "📜", "Log", "History: scrollable, copyable. Results you tapped and automatic refreshes are kept apart and never overwrite each other.", false, false),
    };

    static C get(String id) {
        for (C c : ALL) if (c.id.equals(id)) return c;
        return null;
    }

    static List<C> ofCat(String cat) {
        List<C> out = new ArrayList<C>();
        for (C c : ALL) if (c.cat.equals(cat)) out.add(c);
        return out;
    }

    static List<C> ofGroup(String group) {
        List<C> out = new ArrayList<C>();
        for (C c : ALL) if (c.group.equals(group)) out.add(c);
        return out;
    }

    /** Category/group names by id, in both languages (fall back to the id). */
    static String catEn(String id) {
        for (String[] c : CATS) if (c[0].equals(id)) return c[1];
        return id;
    }

    static String catZh(String id) {
        for (String[] c : CATS) if (c[0].equals(id)) return c[2];
        return id;
    }

    static String groupEn(String id) {
        for (String[] g : GROUPS) if (g[0].equals(id)) return g[2];
        return id;
    }

    static String groupZh(String id) {
        for (String[] g : GROUPS) if (g[0].equals(id)) return g[3];
        return id;
    }
}
