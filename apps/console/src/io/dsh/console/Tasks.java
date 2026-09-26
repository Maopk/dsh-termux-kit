package io.dsh.console;

/**
 * 任务清单：id 就是 Termux 侧 ~/.shortcuts/tasks 里的脚本名（白名单由那边决定，这里只是菜单）。
 *
 * v0.5 起每个任务带**功能分类** cat，界面按分类分段显示，不再把所有按钮堆成一列。
 * 分类是显示用的，小部件（DshWidget）只看 WIDGET_SAFE，不受影响。
 */
final class Tasks {
    /** 启动 / 停止：改变 DSH 自己的运行状态。 */
    static final String RUN = "启动 / 停止";
    /** 通道：桥（无障碍回环，不需要网络）与 adb（无线调试，必须有可用 Wi-Fi）是两条独立通道，永远分开列。 */
    static final String LINK = "通道（adb 与桥分开）";
    /** 维护：不改变运行状态，只动磁盘上的产物。 */
    static final String CARE = "维护";
    /** 紧急：撤销控制权，放最后但最显眼（红色）。 */
    static final String SOS = "紧急";
    /** 界面按这个顺序分段。 */
    static final String[] CATS = { RUN, LINK, CARE, SOS };

    static final class T {
        final String id, label, hint, cat;
        final boolean danger;
        /** 非空表示直接跑这条命令（不经过 ~/.shortcuts/tasks 里的组件脚本）。 */
        final String cmd;
        /**
         * 这条命令**通常最长**要等多久（秒）。为什么要它：以前所有任务共用一个 45 秒超时，
         * 于是"备份/清理/重启"这种本来就要 1~5 分钟的任务**每次都报超时**（假警报），
         * 而真正的失败又和它混在一起分不清。现在按任务给窗口，文案也说"这条通常 Ns 内回来"。
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
        // ── 启动 / 停止 ─────────────────────────────────────────────
        new T("1_启动DSH", RUN, "启动 DSH", "已在跑则直接开页面；含启动互斥与真就绪判定", false, null, 240),
        new T("open", RUN, "打开 Web UI", "用 ~/.dsh-url 里的地址开页面（优先桌面 PWA）", false, TermuxRunner.openUiCmd(), 60),
        new T("4_软重启DSH", RUN, "软重启", "SIGTERM 后重启，会断开当前网页会话", true, null, 300),
        new T("6_硬重启DSH", RUN, "硬重启", "-9 强杀后重启，会断开当前网页会话", true, null, 300),
        new T("2_关闭DSH", RUN, "关闭 DSH", "停 DSH 并关浏览器；桥是独立通道，默认不动它", true, null, 200),

        // ── 通道 ───────────────────────────────────────────────────
        new T("8_自动开无线调试", LINK, "连 adb", "只走无线调试：置开关 → 找端口 → adb connect（需要可用 Wi-Fi）", false, null, 220),
        // ⚠ 这两条必须写**绝对路径**：RUN_COMMAND 走 `bash -lc`，实测它的 PATH 只有
        //   /data/data/com.termux/files/usr/bin:. —— 没有 ~/.local/bin，
        //   所以以前写成裸 `dsh-bridge wake` 时这两条全是 exit=127（command not found）。
        new T("bridge_wake", LINK, "唤醒桥", "只走无障碍回环：带 token 的广播，不需要网络", false,
                TermuxRunner.HOME + "/.local/bin/dsh-bridge wake", 90),
        new T("bridge_status", LINK, "看桥状态", "只查桥：端口 / 是否真应答 / 版本 / paused", false,
                TermuxRunner.HOME + "/.local/bin/dsh-bridge status", 40),
        new T("7_重连AI通道", LINK, "全部恢复（adb + 桥）", "两条通道都要时用：先 adb，再桥；哪条失败会说哪条", false, null, 280),

        // ── 维护 ───────────────────────────────────────────────────
        // 注意：「密码使用权」（AI 能否动用用户的锁屏密码过身份验证）**不是按钮而是开关**，
        // 画在维护类下面（见 MainActivity.buildAuthRow）——它管的不只是装包。
        new T("3_备份DSH", CARE, "备份", "打包并校验归档（zstd -t + 条目数）", false, null, 420),
        new T("5_清理DSH", CARE, "清理", "只删我的产物，不碰配置与笔记", false, null, 420),

        // ── 紧急 ───────────────────────────────────────────────────
        new T("0_紧急停止", SOS, "紧急停止", "撤销 AI 对手机的控制（桥 stop + 撤 token）", true, null, 180),
    };

    /** 小部件上只放安全动作——桌面上误触代价太大。 */
    static final String[] WIDGET_SAFE = { "1_启动DSH", "8_自动开无线调试", "bridge_wake", "3_备份DSH" };
}
