package io.dsh.bridge;

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
    static final String VERSION = "2.20";

    static final class C {
        final String id, kind, cat, group, icon, labelEn, hintEn;
        final boolean danger;
        C(String id, String kind, String cat, String group, String icon, String labelEn, String hintEn, boolean danger) {
            this.id = id; this.kind = kind; this.cat = cat; this.group = group;
            this.icon = icon; this.labelEn = labelEn; this.hintEn = hintEn; this.danger = danger;
        }
    }

    /** Category order on screen, and the two column names (id, en, zh). */
    static final String[][] CATS = { {"startstop", "Start / Stop", "启动 / 停止"}, {"channels", "Channels (adb and bridge separate)", "通道（adb 与桥分开）"}, {"maintenance", "Maintenance", "维护"}, {"emergency", "Emergency", "紧急"}, {"statuslog", "Status / Log", "状态 / 日志"} };
    static final String[][] GROUPS = { {"adb", "channels", "adb · wireless debugging, needs working Wi-Fi", "adb · 无线调试，需要可用 Wi-Fi"}, {"bridge", "channels", "bridge · accessibility loopback, no network", "桥 · 无障碍回环，不需要网络"} };

    static final C[] ALL = {
        new C("bridge_run", "switch", "channels", "bridge", "🌉", "Bridge running", "On = listening on 8788. Off = soft stop: the port closes but the process stays, so one broadcast brings it back.", false),
        new C("bridge_state_text", "text", "channels", "bridge", "·", "Bridge state", "Four states from one ping plus the state note (no timers): running / just dropped (wakeable, one broadcast brings it back) / long silent (the process went away on its own or is bound but not answering - waking may work, otherwise open DSH Bridge once) / not installed (this install has never been seen alive here).", false),
        new C("bridge_full_stop", "button", "maintenance", "", "⏻", "Fully stop bridge", "Lets the App exit and unbinds accessibility. On this vivo it may not be wakeable again - you would have to open DSH Bridge by hand.", true),
        new C("lang", "switch", "maintenance", "", "🌐", "Language", "System / Chinese / English. The widgets and the page panel follow this too.", false),
        new C("project-page", "button", "maintenance", "", "🔗", "Project page", "Opens github.com/Maopk/dsh-termux-kit - source, releases and docs.", false),
        new C("version-update", "text", "maintenance", "", "·", "Version", "Installed version and whether a newer release exists. If the check fails it says so instead of claiming it is up to date.", false),
        new C("copy-token", "button", "maintenance", "", "📋", "Copy token", "Copies the bridge token, which the Termux side needs. Handing it out is handing out the key to this channel.", true),
        new C("open-accessibility", "button", "maintenance", "", "♿", "Open accessibility settings", "For the first setup, or after the system unbound the service and it has to be re-enabled by hand.", false),
        new C("bridge_refresh", "button", "maintenance", "", "🔄", "Re-read state and resume listening", "Re-reads the accessibility state from the system and starts listening again if it was soft-stopped.", false),
        new C("open-notification", "button", "maintenance", "", "🔔", "Notification settings", "Keeps the notification-bar emergency stop working; with notifications off you lose that rescue path.", false),
        new C("idle-auto-stop", "switch", "maintenance", "", "⏱", "Idle auto-stop", "On = soft-stops itself after N idle minutes (less exposure, less battery). Off = always listening.", false),
        new C("bridge_panic", "button", "emergency", "", "🛑", "Emergency stop", "Turns accessibility off right now. The same button sits in the notification bar, which is why notifications must stay on.", true),
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
        for (String[] g : GROUPS) if (g[0].equals(id)) return g[1];
        return id;
    }

    static String groupZh(String id) {
        for (String[] g : GROUPS) if (g[0].equals(id)) return g[2];
        return id;
    }
}
