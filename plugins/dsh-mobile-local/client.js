window.__ModuleLoader__.load({
  id: 'dsh-mobile-local',
  factory: (require) => {
    var module = { exports: {} };
    var exports = module.exports;

    const React = require('react');
    const inject = ['slots', 'timer'];

    function apply(ctx) {
      const h = React.createElement;
      const useCallback = React.useCallback;
      const useEffect = React.useEffect;
      const useRef = React.useRef;
      const useState = React.useState;

      const TASKSD = 'http://127.0.0.1:8787';
      const HOME = '/data/data/com.termux/files/home';

      // ── generated:controls (ui/controls.json) — do not edit by hand; run tools/ui-controls gen ──
      const UI_VERSION = {"bridge": "2.21", "console": "1.15", "panel": "0.7"};
      const UI_CATS = [{"id": "startstop", "en": "Start / Stop", "zh": "启动 / 停止"}, {"id": "channels", "en": "Channels (adb and bridge separate)", "zh": "通道（adb 与桥分开）"}, {"id": "maintenance", "en": "Maintenance", "zh": "维护"}, {"id": "emergency", "en": "Emergency", "zh": "紧急"}, {"id": "statuslog", "en": "Status / Log", "zh": "状态 / 日志"}];
      const UI_GROUPS = [{"id": "adb", "cat": "channels", "en": "adb · wireless debugging, needs working Wi-Fi", "zh": "adb · 无线调试，需要可用 Wi-Fi"}, {"id": "bridge", "cat": "channels", "en": "bridge · accessibility loopback, no network", "zh": "桥 · 无障碍回环，不需要网络"}];
      const UI_CONTROLS = [
        {"id": "bridge_run", "kind": "switch", "cat": "channels", "group": "bridge", "icon": "🌉", "danger": false, "label": {"en": "Bridge running", "zh": "桥运行中"}, "hint": {"en": "On = DSH may drive the phone through this app (listening on 127.0.0.1:8788). Off = soft stop: the port closes, the process stays, and one broadcast brings it back.", "zh": "开 = 允许 DSH 通过本 App 操作手机（监听 127.0.0.1:8788）。关 = 软停：端口关闭、进程保留，一条广播就能拉回。"}, "surfaces": ["panel", "console", "bridge"]},
        {"id": "bridge_state_text", "kind": "text", "cat": "channels", "group": "bridge", "icon": "·", "danger": false, "label": {"en": "Bridge state", "zh": "桥状态"}, "hint": {"en": "Four states from one ping plus the state note (no timers): running / just dropped (wakeable, one broadcast brings it back) / long silent (the process went away on its own or is bound but not answering - waking may work, otherwise open DSH Bridge once) / not installed (this install has never been seen alive here).", "zh": "四态由**一次 ping 的结果 + 状态记录**判定（不看时间）：运行中 / 刚断（可唤醒，一条广播就能拉回）/ 长时间未响应（进程自己没了，或端口在听却不应答 —— 唤醒可能有效，无效就手动打开一次「DSH 桥」）/ 未安装（这台机器上从没见过它是活的）。"}, "surfaces": ["panel", "console", "bridge"]},
        {"id": "bridge_wake", "kind": "button", "cat": "channels", "group": "bridge", "icon": "🌉", "danger": false, "label": {"en": "Wake bridge", "zh": "唤醒桥"}, "hint": {"en": "Sends one token-carrying broadcast. Never takes your screen. Can take 20-40s if the process was reclaimed.", "zh": "发一条带 token 的广播把它唤回，绝不抢你的屏幕。进程被回收时要 20-40 秒。"}, "surfaces": ["panel", "console", "widget"]},
        {"id": "bridge_status", "kind": "button", "cat": "channels", "group": "bridge", "icon": "🔎", "danger": false, "label": {"en": "Bridge status", "zh": "桥状态查询"}, "hint": {"en": "Read-only: is the port listening, does it really answer, version, paused flag.", "zh": "只读：端口是否在听、是否真应答、版本、是否被暂停。"}, "surfaces": ["panel", "console"]},
        {"id": "7_reconnect-ai", "kind": "button", "cat": "channels", "group": "bridge", "icon": "🔗", "danger": false, "label": {"en": "Restore both channels", "zh": "恢复全部通道"}, "hint": {"en": "adb first, then the bridge. Use only when you need both; a failure names the channel it came from.", "zh": "先 adb 后桥。两条都要时才用；失败会说清是哪条通道。"}, "surfaces": ["panel", "console"]},
        {"id": "1_start-dsh", "kind": "button", "cat": "startstop", "group": "", "icon": "▶", "danger": false, "label": {"en": "Start DSH", "zh": "启动 DSH"}, "hint": {"en": "Starts DSH and opens the page; if it is already running it only opens the page.", "zh": "启动 DSH 并打开页面；已在运行则只打开页面。"}, "surfaces": ["panel", "console", "widget"]},
        {"id": "4_soft-restart-dsh", "kind": "button", "cat": "startstop", "group": "", "icon": "↻", "danger": true, "label": {"en": "Soft restart", "zh": "软重启"}, "hint": {"en": "Restarts the service with SIGTERM. Cost: drops the current web session.", "zh": "用 SIGTERM 重启服务。代价：会断开当前网页会话。"}, "surfaces": ["panel", "console"]},
        {"id": "6_hard-restart-dsh", "kind": "button", "cat": "startstop", "group": "", "icon": "⛔", "danger": true, "label": {"en": "Hard restart", "zh": "硬重启"}, "hint": {"en": "Restarts after a kill -9 and clears the orphan lock. Cost: drops the current web session.", "zh": "强杀后重启并清孤儿锁。代价：会断开当前网页会话。"}, "surfaces": ["panel", "console"]},
        {"id": "2_shutdown-dsh", "kind": "button", "cat": "startstop", "group": "", "icon": "■", "danger": true, "label": {"en": "Stop DSH", "zh": "关闭 DSH"}, "hint": {"en": "Stops DSH and closes the browser. The bridge is a separate channel and is left alone by default.", "zh": "关闭 DSH 并关浏览器。桥是另一条通道，默认不动它。"}, "surfaces": ["panel", "console"]},
        {"id": "adb_ensure", "kind": "button", "cat": "channels", "group": "adb", "icon": "🔌", "danger": false, "label": {"en": "Repair adb channel", "zh": "修复 adb 通道"}, "hint": {"en": "Wi-Fi state → Wireless debugging switch (written through the bridge) → port → connect → verify. Says which step failed.", "zh": "Wi-Fi 状态 → 无线调试开关（这一步借桥来写）→ 端口 → 连接 → 复核。卡在哪一步就说哪一步。"}, "surfaces": ["panel", "console"]},
        {"id": "8_enable-wireless-adb", "kind": "button", "cat": "channels", "group": "adb", "icon": "⚡", "danger": false, "label": {"en": "Connect adb", "zh": "连接 adb"}, "hint": {"en": "Wireless debugging only. Needs a real Wi-Fi network, not just the switch.", "zh": "只走无线调试。需要有真实可用的 Wi-Fi，不只是拨开关。"}, "surfaces": ["panel", "console", "widget"]},
        {"id": "adb_lamp", "kind": "lamp", "cat": "statuslog", "group": "", "icon": "●", "danger": false, "label": {"en": "adb", "zh": "adb"}, "hint": {"en": "Green = usable, yellow = connecting, red = failed, grey = not connected (check Wi-Fi).", "zh": "绿=可用，黄=连接中，红=失败，灰=未连接（看 Wi-Fi）。"}, "surfaces": ["panel", "console"]},
        {"id": "10_net-fix", "kind": "button", "cat": "maintenance", "group": "", "icon": "🩺", "danger": false, "label": {"en": "Network first aid", "zh": "网络急救"}, "hint": {"en": "When foreign sites die: decides whether the Clash core stopped or the config went bad, then repairs.", "zh": "外网全挂时用：先判 Clash 核心停了还是配置坏了，再修。"}, "surfaces": ["panel", "console"]},
        {"id": "11_update-apps", "kind": "button", "cat": "maintenance", "group": "", "icon": "⬆", "danger": false, "label": {"en": "Update the two apps", "zh": "更新两个 App"}, "hint": {"en": "Downloads the two APKs from GitHub Releases, verifies SHA256, then installs them.", "zh": "从 GitHub Releases 下载两个 APK，校验 SHA256 后安装。"}, "surfaces": ["panel", "console"]},
        {"id": "3_backup-dsh", "kind": "button", "cat": "maintenance", "group": "", "icon": "💾", "danger": false, "label": {"en": "Backup", "zh": "备份"}, "hint": {"en": "Packs DSH state and verifies the archive; the result goes to Download/dsh/.", "zh": "打包 DSH 状态并校验归档；产物落 Download/dsh/。"}, "surfaces": ["panel", "console"]},
        {"id": "5_cleanup-dsh", "kind": "button", "cat": "maintenance", "group": "", "icon": "🧹", "danger": false, "label": {"en": "Cleanup", "zh": "清理"}, "hint": {"en": "Deletes only this kit's own artifacts. Your files are not touched.", "zh": "只删本工具自己的产物，不碰你的文件。"}, "surfaces": ["panel", "console"]},
        {"id": "bridge_full_stop", "kind": "button", "cat": "channels", "group": "bridge", "icon": "⏻", "danger": true, "label": {"en": "Fully stop bridge", "zh": "真停桥"}, "hint": {"en": "Lets the App exit and unbinds accessibility. On this vivo it may not be wakeable again - you would have to open DSH Bridge by hand.", "zh": "让 App 退出并解绑无障碍。这台 vivo 上可能唤不回来 —— 只能你手动打开「DSH 桥」。"}, "surfaces": ["panel", "console", "bridge"]},
        {"id": "lang", "kind": "switch", "cat": "maintenance", "group": "", "icon": "🌐", "danger": false, "label": {"en": "Language", "zh": "语言"}, "hint": {"en": "System / Chinese / English. The widgets and the page panel follow this too.", "zh": "跟随系统 / 中文 / English。小组件和页面面板也跟着变。"}, "surfaces": ["panel", "console", "bridge"]},
        {"id": "project-page", "kind": "button", "cat": "maintenance", "group": "", "icon": "🔗", "danger": false, "label": {"en": "Project page", "zh": "项目主页"}, "hint": {"en": "Opens github.com/Maopk/dsh-termux-kit - source, releases and docs.", "zh": "打开 github.com/Maopk/dsh-termux-kit —— 源码、发行版和文档。"}, "surfaces": ["console", "bridge"]},
        {"id": "version-update", "kind": "text", "cat": "maintenance", "group": "", "icon": "·", "danger": false, "label": {"en": "Version", "zh": "版本"}, "hint": {"en": "Installed version and whether a newer release exists. If the check fails it says so instead of claiming it is up to date.", "zh": "已装版本 + 有没有新发行版。查不到就直说查不到，不谎报「已是最新」。"}, "surfaces": ["panel", "console", "bridge"]},
        {"id": "copy-token", "kind": "button", "cat": "maintenance", "group": "", "icon": "📋", "danger": true, "label": {"en": "Copy token", "zh": "复制 token"}, "hint": {"en": "Copies the bridge token, which the Termux side needs. Handing it out is handing out the key to this channel.", "zh": "复制桥的 token（Termux 侧要用）。把它交出去等于交出这条通道的钥匙。"}, "surfaces": ["bridge"]},
        {"id": "open-accessibility", "kind": "button", "cat": "maintenance", "group": "", "icon": "♿", "danger": false, "label": {"en": "Open accessibility settings", "zh": "打开无障碍设置"}, "hint": {"en": "For the first setup, or after the system unbound the service and it has to be re-enabled by hand.", "zh": "首次启用，或被系统解绑后需要手动重新打开时用。"}, "surfaces": ["bridge"]},
        {"id": "bridge_refresh", "kind": "button", "cat": "maintenance", "group": "", "icon": "🔄", "danger": false, "label": {"en": "Re-read state and resume listening", "zh": "重读状态并恢复监听"}, "hint": {"en": "Re-reads the accessibility state from the system and starts listening again if it was soft-stopped.", "zh": "从系统重新读无障碍状态；被软停时用它恢复监听。"}, "surfaces": ["bridge"]},
        {"id": "open-notification", "kind": "button", "cat": "maintenance", "group": "", "icon": "🔔", "danger": false, "label": {"en": "Notification settings", "zh": "通知设置"}, "hint": {"en": "Keeps the notification-bar emergency stop working; with notifications off you lose that rescue path.", "zh": "让通知栏的紧急停止可用；关掉通知就少一条自救路径。"}, "surfaces": ["bridge"]},
        {"id": "idle-auto-stop", "kind": "switch", "cat": "maintenance", "group": "", "icon": "⏱", "danger": false, "label": {"en": "Idle auto-stop", "zh": "闲置自动软停"}, "hint": {"en": "On = soft-stops itself after N idle minutes (less exposure, less battery). Off = always listening.", "zh": "开=闲置 N 分钟后自动软停（少暴露、省电）；关=一直监听。"}, "surfaces": ["bridge"]},
        {"id": "password-access", "kind": "switch", "cat": "maintenance", "group": "", "icon": "🔑", "danger": true, "label": {"en": "Password access", "zh": "密码使用权"}, "hint": {"en": "On = the AI may use your 6-digit lock-screen password to pass system verification for you (installing packages, removing settings restrictions). Off = revoked at once. It is never used to unlock the phone and read content, to pay, or for anything unrelated to the task at hand.", "zh": "开 = AI 可动用你的 6 位密码替你过系统验证（装包、解除应用设置限制）；关 = 立刻收回。绝不用于解锁手机翻看内容、支付/免密、或与当次任务无关的场景。"}, "surfaces": ["panel", "console"]},
        {"id": "0_emergency-stop", "kind": "button", "cat": "emergency", "group": "", "icon": "🛑", "danger": true, "label": {"en": "Emergency stop", "zh": "紧急停止"}, "hint": {"en": "Revokes the AI's control of the phone: soft-stops the bridge and revokes the token. adb is a separate channel and stays up.", "zh": "撤销 AI 对手机的控制：软停桥 + 吊销 token。adb 是另一条通道，不受影响。"}, "surfaces": ["panel", "console"]},
        {"id": "bridge_panic", "kind": "button", "cat": "emergency", "group": "", "icon": "🛑", "danger": true, "label": {"en": "Emergency stop", "zh": "紧急停止"}, "hint": {"en": "Turns accessibility off right now. The same button sits in the notification bar, which is why notifications must stay on.", "zh": "立刻关掉无障碍服务。通知栏里有一颗同样的按钮，所以别关通知。"}, "surfaces": ["bridge"]},
        {"id": "lamp_dsh", "kind": "lamp", "cat": "statuslog", "group": "", "icon": "●", "danger": false, "label": {"en": "DSH", "zh": "DSH"}, "hint": {"en": "Green = the web service answers, yellow = half-started, red = down, grey = not running.", "zh": "绿=网页服务正常应答，黄=半启动，红=挂了，灰=没在跑。"}, "surfaces": ["panel", "console"]},
        {"id": "lamp_bridge", "kind": "lamp", "cat": "statuslog", "group": "", "icon": "●", "danger": false, "label": {"en": "Bridge", "zh": "桥"}, "hint": {"en": "Green = listening and answering, grey = soft-stopped or not installed.", "zh": "绿=在听且真应答，灰=软停或未安装。"}, "surfaces": ["panel", "console"]},
        {"id": "status_refresh", "kind": "button", "cat": "statuslog", "group": "", "icon": "🔄", "danger": false, "label": {"en": "Refresh status", "zh": "刷新状态"}, "hint": {"en": "Reads the state once and updates the lamps. Read-only: it never wakes a channel.", "zh": "只读读一次状态并更新状态灯，**不会唤醒任何通道**。"}, "surfaces": ["panel", "console", "widget"]},
        {"id": "log", "kind": "button", "cat": "statuslog", "group": "", "icon": "📜", "danger": false, "label": {"en": "Log", "zh": "日志"}, "hint": {"en": "History: scrollable, copyable. Results you tapped and automatic refreshes are kept apart and never overwrite each other.", "zh": "历史记录：可滚动、可复制。你点出来的和自动刷新的分开记，互不覆盖。"}, "surfaces": ["panel", "console"]},
      ];
      // ── /generated:controls ──

      // ── language ──
      // The kit has exactly one language setting (~/.dsh-lang, written by dsh-lang / the console) and the
      // panel has to follow it like the widgets do — otherwise "语言：中文" would leave this one surface
      // English. 'auto' falls back to the browser's language, which on this phone is Chinese.
      function pickLang(mode) {
        const m = String(mode || '').trim();
        if (m === 'zh' || m === 'en') return m;
        const nav = (navigator.language || 'en').toLowerCase();
        return nav.indexOf('zh') === 0 ? 'zh' : 'en';
      }

      const CSS = `
.mb-fab { position: fixed; right: 12px; bottom: calc(132px + env(safe-area-inset-bottom, 0px)); z-index: 60;
  width: 50px; height: 50px; border-radius: 25px; border: 1px solid var(--dsw-alias-border-l2);
  background: var(--dsw-alias-bg-layer-2); color: var(--dsw-alias-label-primary);
  display: flex; align-items: center; justify-content: center; font-size: 20px; cursor: pointer;
  box-shadow: 0 4px 14px rgba(0,0,0,.28); -webkit-tap-highlight-color: transparent; }
.mb-fab:active { transform: scale(.95); }
.mb-mask { position: fixed; inset: 0; z-index: 61; background: rgba(0,0,0,.42); }
.mb-sheet { position: fixed; left: 0; right: 0; bottom: 0; z-index: 62;
  background: var(--dsw-alias-bg-layer-1); border-top: 1px solid var(--dsw-alias-border-l1);
  border-radius: 16px 16px 0 0; padding: 10px 12px calc(14px + env(safe-area-inset-bottom, 0px));
  max-height: 84vh; overflow-y: auto; animation: mb-up .16s ease-out; }
@keyframes mb-up { from { transform: translateY(24px); opacity: .6 } to { transform: none; opacity: 1 } }
.mb-head { display: flex; align-items: center; gap: 8px; margin: 2px 2px 8px; }
.mb-title { font-size: 14px; font-weight: 600; color: var(--dsw-alias-label-primary); flex: 1; }
.mb-ver { font-size: 11px; color: var(--dsw-alias-label-tertiary, #999); }
.mb-cat { margin: 14px 2px 6px; font-size: 13.5px; cursor: pointer; -webkit-user-select: none; user-select: none; font-weight: 700; letter-spacing: .3px;
  color: var(--dsw-alias-brand-primary, #4c8dff); }
.mb-sub { margin: 10px 2px 4px; font-size: 13px; font-weight: 600; color: var(--dsw-alias-label-secondary); }
.mb-list { display: flex; flex-direction: column; gap: 8px; }
.mb-task { display: flex; align-items: flex-start; gap: 9px; width: 100%; min-height: 54px; padding: 9px 11px;
  border: 1px solid var(--dsw-alias-border-l1); border-radius: 11px; background: var(--dsw-alias-bg-layer-2);
  color: var(--dsw-alias-label-primary); font-size: 13.5px; text-align: left; cursor: pointer;
  -webkit-tap-highlight-color: transparent; box-sizing: border-box; }
.mb-task:active { transform: scale(.99); }
.mb-task.danger { border-color: #e5484d; border-width: 1.5px; }
.mb-task.armed { background: #e5484d; border-color: #e5484d; color: #fff; }
/* Two danger levels: emergency = solid red (last resort); other dangerous = red outline */
.mb-task.solid { background: #e5484d; border-color: #e5484d; color: #fff; }
.mb-task.solid .mb-hint { color: rgba(255,255,255,.9); }
.mb-task[disabled] { opacity: .5; }
.mb-task[disabled] .mb-name { color: var(--dsw-alias-label-tertiary, #999); }
.mb-ic { width: 20px; text-align: center; font-size: 15px; line-height: 20px; }
.mb-txt { flex: 1; min-width: 0; }
.mb-name { display: block; font-weight: 600; }
.mb-hint { display: block; font-size: 13px; color: var(--dsw-alias-label-tertiary, #999); margin-top: 2px;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mb-hint.open { white-space: normal; }
.mb-task.armed .mb-hint { color: rgba(255,255,255,.9); }
.mb-close { min-width: 42px; min-height: 42px; border: 1px solid var(--dsw-alias-border-l1);
  border-radius: 10px; background: transparent; color: var(--dsw-alias-label-primary); font-size: 13px; cursor: pointer; }
.mb-close.badge { border-color: #4c8dff; color: #4c8dff; font-weight: 700; }
.mb-out { margin-top: 10px; padding: 9px 10px; border-radius: 10px; background: var(--dsw-alias-bg-layer-2);
  border: 1px solid var(--dsw-alias-border-l1); font-size: 11.5px; line-height: 1.5; white-space: pre-wrap;
  word-break: break-all; max-height: 32vh; overflow-y: auto; color: var(--dsw-alias-label-secondary);
  -webkit-user-select: text; user-select: text; }
.mb-lamps { display: flex; gap: 12px; flex-wrap: wrap; margin: 2px 2px 8px; font-size: 12px;
  color: var(--dsw-alias-label-secondary); }
.mb-lamp { display: inline-flex; align-items: center; gap: 5px; }
.mb-dot { width: 9px; height: 9px; border-radius: 5px; background: #8b949e; }
.mb-dot.green { background: #2ecc71; } .mb-dot.yellow { background: #e3b341; }
.mb-dot.red { background: #e5484d; } .mb-dot.grey { background: #8b949e; }
.mb-switch { display: flex; align-items: flex-start; gap: 9px; width: 100%; min-height: 54px; padding: 9px 11px;
  border: 1px solid var(--dsw-alias-border-l1); border-radius: 11px; background: var(--dsw-alias-bg-layer-2);
  box-sizing: border-box; }
.mb-switch.danger { border-color: #e5484d; border-width: 1.5px; }
.mb-switch input { width: 22px; height: 22px; margin-top: 1px; flex: none; }
.mb-switch[data-busy="1"] { opacity: .5; }
.mb-state { font-size: 13px; color: var(--dsw-alias-label-tertiary, #999); margin-top: 2px; display: block; }
.mb-text { font-size: 12px; color: var(--dsw-alias-label-secondary); padding: 2px 3px; }
.mb-ask { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--dsw-alias-border-l1);
  display: flex; flex-wrap: wrap; align-items: center; gap: 8px; font-size: 12px;
  color: var(--dsw-alias-label-secondary); }
.mb-pw { min-height: 40px; width: 96px; padding: 0 10px; border-radius: 10px;
  border: 1px solid var(--dsw-alias-border-l1); background: transparent; color: var(--dsw-alias-label-primary);
  font-size: 16px; letter-spacing: 2px; }
.mb-btn-inline { display: inline-flex; align-items: center; gap: 4px; min-height: 40px; padding: 0 12px;
  border: 1px solid var(--dsw-alias-border-l1); border-radius: 10px; background: transparent;
  color: var(--dsw-alias-label-primary); font-size: 12.5px; cursor: pointer; }
.mb-btn-inline[disabled] { opacity: .5; }
.mb-busy { display: flex; align-items: center; gap: 8px; margin: 4px 2px 2px; font-size: 12px;
  color: var(--dsw-alias-brand-primary, #4c8dff); }
.mb-spin { width: 14px; height: 14px; border-radius: 7px; border: 2px solid rgba(76,141,255,.3);
  border-top-color: #4c8dff; animation: mb-spin .8s linear infinite; }
@keyframes mb-spin { to { transform: rotate(360deg) } }
.mb-log { font-size: 11.5px; line-height: 1.5; white-space: pre-wrap; word-break: break-all; max-height: 50vh;
  overflow-y: auto; -webkit-user-select: text; user-select: text; padding: 8px 10px; border-radius: 10px;
  background: var(--dsw-alias-bg-layer-2); border: 1px solid var(--dsw-alias-border-l1); }
.mb-log .who { color: #4c8dff; } .mb-log .auto { color: #8b949e; }
/* ── mobile touch tweaks (add-only, reversible any time: set enabled to false in ~/.dsh-mobile-ui.json) ── */
@media (pointer: coarse) {
  header button, nav button, [class*="sidebarCol"] button { min-width: 42px; min-height: 42px; }
  [class*="composerSeat"] { padding-bottom: max(8px, env(safe-area-inset-bottom, 0px)) !important; }
  pre, table { overflow-x: auto; -webkit-overflow-scrolling: touch; }
  [class*="hoverOnly"], [class*="hover-only"] { opacity: 1 !important; visibility: visible !important; }
}
`;

      // ── style injection (reversible) ──
      const STYLE_ID = 'dsh-mobile-local';
      if (document.querySelector('style[data-plugin-css="' + STYLE_ID + '"]') === null) {
        const tag = document.createElement('style');
        tag.setAttribute('data-plugin-css', STYLE_ID);
        tag.textContent = CSS;
        document.head.appendChild(tag);
        if (ctx.effect) ctx.effect(() => () => tag.remove());
      }

      // ── RPC with filepanel (use it to read files instead of building a host route) ──
      // ⚠ Pitfall: filepanel's resolveWithin resolves the path on its own (**without joining root**),
      //   then validates with contains(root, target) → the path must be **absolute**; a relative one
      //   resolves outside the workspace and reports "path is not inside the workspace" (I hit this).
      async function rpc(method, args) {
        const resp = await fetch('/__dsh__/filepanel/rpc', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ method, args }),
        });
        const res = await resp.json();
        if (!res || res.ok === false) throw new Error((res && res.error) || 'RPC failed');
        return res;
      }

      let TOKEN_CACHE = null;
      async function token() {
        if (TOKEN_CACHE) return TOKEN_CACHE;
        const r = await rpc('panel.readText', { root: HOME, path: HOME + '/.dsh-tasks-token' });
        TOKEN_CACHE = String(r.content || '').trim();
        if (!TOKEN_CACHE) throw new Error('cannot read ~/.dsh-tasks-token');
        return TOKEN_CACHE;
      }

      async function api(path) {
        const t = await token();
        const url = TASKSD + path + (path.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(t);
        const resp = await fetch(url, { cache: 'no-store' });
        if (!resp.ok) {
          let msg = 'HTTP ' + resp.status;
          try { const j = await resp.json(); if (j && j.error) msg = j.error; } catch (e) {}
          throw new Error(msg);
        }
        return resp.json();
      }

      // ── log store ──
      // Kept in localStorage so it survives a page refresh (the console keeps its own 60 entries in
      // SharedPreferences). Two rules from the user: results the user tapped and automatic refreshes are
      // recorded **apart** and never overwrite each other's summary; and the log panel must not be
      // disabled while a task is running (you read the log *because* something is running).
      const LOG_KEY = 'dsh-mobile-log';
      const LOG_MAX = 60;
      function logRead() {
        try { return JSON.parse(localStorage.getItem(LOG_KEY) || '[]'); } catch (e) { return []; }
      }
      function logWrite(rows) {
        try { localStorage.setItem(LOG_KEY, JSON.stringify(rows.slice(-LOG_MAX))); } catch (e) {}
      }

      // ── helpers ──
      function lampOf(kind, status, extra) {
        // Colour semantics are fixed across all three UIs: green ok · yellow transitional · red broken · grey off/unknown
        if (kind === 'dsh') {
          if (!status) return 'grey';
          if (!(status.dsh && status.dsh.port)) return 'red';
          // 401 is the *normal* answer here: the page requires the login token, so an unauthenticated
          // curl to / gets 401 — that means DSH is up. 404 is the half-started window (port bound,
          // routes not mounted yet) and is exactly what "yellow / transitional" is for.
          const code = String((status.dsh && status.dsh.http) || '');
          if (code === '401' || code === '200' || code.charAt(0) === '3') return 'green';
          return 'yellow';
        }
        if (kind === 'bridge') {
          if (!status) return 'grey';
          const b = status.bridge || {};
          if (b.ok) return 'green';
          if (b.port) return 'yellow';
          return 'grey';
        }
        if (kind === 'adb') {
          if (!status) return 'grey';
          const d = (status.adb && status.adb.devices) || [];
          return d.length ? 'green' : 'grey';
        }
        return 'grey';
      }

      function Lamp(props) {
        return h('span', { className: 'mb-lamp', role: 'status', 'aria-label': props.aria },
          h('i', { className: 'mb-dot ' + props.color }),
          props.text)
      }

      // ── one control row ──
      function Control(props) {
        const { c, lang, busy, armed, expanded, status } = props;
        const t = (p) => (p && (lang === 'zh' ? p.zh : p.en)) || '';
        const isBusy = busy && busy.id === c.id;
        const isArmed = armed === c.id;
        const label = isArmed ? (lang === 'zh' ? '再点一次确认' : 'Tap again to confirm') : t(c.label);
        const hint = (c.danger ? '⚠ ' : '') + t(c.hint);
        const open = expanded === c.id;
        const st = (status && status.tasks && status.tasks[c.id]) || {};
        const tail = st.last === 'done' ? (lang === 'zh' ? '　上次 ✔' : '  last ✔')
          : (st.last ? (lang === 'zh' ? '　上次 ' + st.last : '  last ' + st.last) : '');
        return h('button', {
          className: 'mb-task' + (c.danger ? ' danger' : '') + (c.cat === 'emergency' ? ' solid' : '') + (isArmed ? ' armed' : ''),
          disabled: busy !== null && !isBusy,
          'aria-label': t(c.label) + (c.danger ? (lang === 'zh' ? '，危险操作' : ', dangerous') : ''),
          onClick: () => props.onFire(c),
          onContextMenu: (e) => { e.preventDefault(); props.onExpand(c.id); },
          onTouchStart: () => props.onPressStart(c.id),
          onTouchEnd: () => props.onPressEnd(),
          onTouchMove: () => props.onPressEnd(),
        },
          h('span', { className: 'mb-ic' }, c.icon),
          h('span', { className: 'mb-txt' },
            h('span', { className: 'mb-name' }, isBusy ? t(c.label) + ' …' : label),
            h('span', { className: 'mb-hint' + (open ? ' open' : '') }, hint + tail)))
      }

      function SwitchRow(props) {
        const { c, lang, on, busy, note, dot } = props;
        const t = (p) => (p && (lang === 'zh' ? p.zh : p.en)) || '';
        const press = useRef(0);
        return h('div', {
          className: 'mb-switch' + (c.danger ? ' danger' : ''),
          'data-busy': busy ? '1' : '0',
          // The long-press carries the on/off meaning; the visible subtitle stays the *current* state.
          onContextMenu: (e) => { e.preventDefault(); props.onExplain(c); },
          onTouchStart: () => { press.current = setTimeout(() => props.onExplain(c), 450); },
          onTouchEnd: () => clearTimeout(press.current),
          onTouchMove: () => clearTimeout(press.current),
        },
          h('input', {
            type: 'checkbox', checked: !!on, disabled: !!busy,
            'aria-label': t(c.label) + (lang === 'zh' ? '，当前' : ', currently ') + (on ? (lang === 'zh' ? '已开启' : 'on') : (lang === 'zh' ? '已关闭' : 'off')),
            onChange: (e) => props.onToggle(e.target.checked),
          }),
          h('span', { className: 'mb-txt' },
            h('span', { className: 'mb-name' }, t(c.label),
              h('i', { className: 'mb-dot ' + (dot || 'grey'), style: { marginLeft: '7px' } })),
            note ? h('span', { className: 'mb-state' }, note) : null))
      }

      /** Language: three explicit choices; a cycling control hides which option you will land on. */
      function LangRow(props) {
        const { lang, mode, onPick } = props;
        const items = [['auto', lang === 'zh' ? '跟随系统' : 'System'], ['zh', '中文'], ['en', 'English']];
        return h('div', { className: 'mb-switch' },
          h('span', { className: 'mb-txt' },
            h('span', { className: 'mb-name' }, lang === 'zh' ? '语言' : 'Language'),
            h('div', { style: { display: 'flex', gap: '6px', marginTop: '6px' } },
              items.map(([id, name]) => h('button', {
                key: id, className: 'mb-btn-inline',
                style: { borderColor: mode === id ? '#4c8dff' : undefined, color: mode === id ? '#4c8dff' : undefined, fontWeight: mode === id ? '700' : undefined },
                onClick: () => onPick(id),
              }, (mode === id ? '✓ ' : '') + name)))))
      }

      /** The full explanation as a dialog — for a switch (long-press) and for every dangerous button (tap). */
      function HintDialog(props) {
        const { c, lang, onClose, onConfirm, confirmLabel } = props;
        const t = (p) => (p && (lang === 'zh' ? p.zh : p.en)) || '';
        return h('div', null,
          h('div', { className: 'mb-mask', onClick: onClose }),
          h('div', { className: 'mb-sheet', onClick: (e) => e.stopPropagation() },
            h('div', { className: 'mb-head' },
              h('span', { className: 'mb-title' }, (c.danger ? '⚠ ' : '') + t(c.label)),
              h('button', { className: 'mb-close', onClick: onClose }, lang === 'zh' ? '关闭' : 'Close')),
            h('div', { className: 'mb-out', style: { maxHeight: '40vh' } }, t(c.hint)),
            onConfirm ? h('div', { className: 'mb-ask' },
              h('button', { className: 'mb-btn-inline', style: { borderColor: '#e5484d', color: '#e5484d' }, onClick: onConfirm },
                confirmLabel || (lang === 'zh' ? '确认执行' : 'Confirm')),
              h('button', { className: 'mb-btn-inline', onClick: onClose }, lang === 'zh' ? '取消' : 'Cancel')) : null))
      }

      function TaskSheet(props) {
        const lang = props.lang;
        const t = (p) => (p && (lang === 'zh' ? p.zh : p.en)) || '';
        const [status, setStatus] = useState(null);
        const [auth, setAuth] = useState(null);
        const [busy, setBusy] = useState(null);
        const [secs, setSecs] = useState(0);
        const [armed, setArmed] = useState('');
        const [expanded, setExpanded] = useState('');
        const [authArmed, setAuthArmed] = useState('');
        const [pw, setPw] = useState('');
        const [out, setOut] = useState('');
        const [err, setErr] = useState('');
        const [logOpen, setLogOpen] = useState(false);
        const [unread, setUnread] = useState(0);
        // Collapsible sections: only 启动/停止 open by default (user's call).
        const [collapsed, setCollapsed] = useState({ channels: true, maintenance: true, emergency: true, statuslog: true });
        const pressRef = useRef(0);

        const pushLog = useCallback((entry) => {
          const rows = logRead();
          rows.push(entry);
          logWrite(rows);
        }, []);

        const load = useCallback(async () => {
          setErr('');
          try {
            const rs = await Promise.all([api('/status'), api('/auth')]);
            setStatus(rs[0].status); setAuth(rs[1].auth);
          } catch (e) { setErr((lang === 'zh' ? '读状态失败：' : 'Failed to read status: ') + (e.message || e)); }
        }, [lang]);

        useEffect(() => { load(); }, [load]);
        // Cross-UI sync (user requirement D): the password switch is owned by the 600 file on disk, not by
        // either UI. Re-read it whenever the page becomes visible again, so flipping it in the console app
        // shows up here without a manual refresh — and never from a private cache on this side.
        useEffect(() => {
          const onVis = () => { if (!document.hidden) load(); };
          document.addEventListener('visibilitychange', onVis);
          return () => document.removeEventListener('visibilitychange', onVis);
        }, [load]);
        useEffect(() => {
          if (!busy) { setSecs(0); return; }
          const t0 = Date.now();
          const id = setInterval(() => setSecs(Math.round((Date.now() - t0) / 1000)), 1000);
          return () => clearInterval(id);
        }, [busy]);

        const apiPost = useCallback(async (path, body) => {
          const t0 = await token();
          const resp = await fetch(TASKSD + path, {
            method: 'POST', cache: 'no-store', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(Object.assign({ token: t0 }, body || {})),
          });
          const j = await resp.json().catch(() => ({}));
          if (!resp.ok || j.ok === false) throw new Error((j && j.error) || ('HTTP ' + resp.status));
          return j;
        }, []);

        // Which task id a control fires. Plain buttons carry their own id; a switch carries the id for
        // each direction, so a flip is the same kind of call as a press (one code path, one log format).
        const fireTask = useCallback(async (c, id) => {
          setBusy({ id: c.id }); setArmed(''); setOut(''); setErr('');
          const t0 = Date.now();
          try {
            const r = await api('/run?task=' + encodeURIComponent(id));
            const took = (r.seconds != null ? r.seconds : ((Date.now() - t0) / 1000)).toFixed(1);
            const head = '[' + t(c.label) + '] exit ' + r.exit + ' · ' + took + 's';
            setOut(head + '\n' + (r.output || (lang === 'zh' ? '（本次没有输出）' : '(no output this time)')));
            pushLog({ ts: Date.now(), who: 'user', id: id, head: head, body: (r.output || '').slice(0, 1500) });
            setUnread((n) => n + 1);
            await load();
          } catch (e) {
            const msg = (lang === 'zh' ? '失败：' : 'Failed: ') + (e.message || e);
            setErr(msg);
            pushLog({ ts: Date.now(), who: 'user', id: id, head: '[' + t(c.label) + '] ' + msg, body: '' });
            setUnread((n) => n + 1);
          }
          setBusy(null);
        }, [load, pushLog, lang]);

        // Danger: the explanation opens on a *tap* (a long-press fights the system back gesture), and the
        // confirm sits inside that dialog — one gesture, and the consequence is read before it happens.
        const onFire = useCallback((c) => {
          if (c.danger) { setAsk(c.id); return; }
          fireTask(c, c.id);
        }, [fireTask]);
        const [ask, setAsk] = useState('');        // dangerous control awaiting confirm
        const [hintId, setHintId] = useState('');  // pure explanation (switch long-press)
        const showHint = useCallback((c) => setHintId(c.id), []);

        const onToggle = useCallback((c, on) => {
          const id = on ? c.on_task : c.off_task;
          if (!id) return;
          if (!on && c.danger) {
            // revoking has its own confirm block for password-access; other switches fire directly
          }
          fireTask(c, id);
        }, [fireTask]);

        const refresh = useCallback(async () => {
          setBusy({ id: 'status_refresh' });
          const t0 = Date.now();
          try {
            const r = await api('/run?task=status_refresh');
            const head = '[' + t({ zh: '刷新状态', en: 'Refresh status' }) + '] exit ' + r.exit + ' · ' + (r.seconds != null ? r.seconds : ((Date.now() - t0) / 1000)).toFixed(1) + 's';
            // marked as an automatic refresh: it lands in the log but never overwrites the "last result"
            pushLog({ ts: Date.now(), who: 'auto', id: 'status_refresh', head: head, body: '' });
            await load();
          } catch (e) {
            setErr((lang === 'zh' ? '刷新失败：' : 'Refresh failed: ') + (e.message || e));
            pushLog({ ts: Date.now(), who: 'auto', id: 'status_refresh', head: 'status_refresh: ' + (e.message || e), body: '' });
          }
          setBusy(null);
        }, [load, pushLog, lang]);

        const doRevoke = useCallback(async () => {
          setBusy({ id: 'password-access' }); setErr(''); setOut('');
          try { const r = await apiPost('/auth/revoke'); setOut('[revoke] ' + (r.output || '')); setAuthArmed(''); await load(); }
          catch (e) { setErr((lang === 'zh' ? '撤销失败：' : 'Revoke failed: ') + (e.message || e)); }
          setBusy(null);
        }, [apiPost, load, lang]);

        const doAuthorize = useCallback(async () => {
          setBusy({ id: 'password-access' }); setErr(''); setOut('');
          try { const r = await apiPost('/auth/set', { pass: pw }); setOut('[authorize] ' + (r.output || '')); setAuthArmed(''); setPw(''); await load(); }
          catch (e) { setErr((lang === 'zh' ? '授权失败：' : 'Authorization failed: ') + (e.message || e)); }
          setBusy(null);
        }, [apiPost, load, pw, lang]);

        const pressStart = useCallback((id) => {
          pressRef.current = setTimeout(() => setExpanded((cur) => (cur === id ? '' : id)), 450);
        }, []);
        const pressEnd = useCallback(() => { clearTimeout(pressRef.current); }, []);

        const s = status || {};
        const byId = {};
        UI_CONTROLS.forEach((c) => { byId[c.id] = c; });
        const catName = (id) => {
          const c = UI_CATS.filter((x) => x.id === id)[0];
          return c ? (lang === 'zh' ? c.zh : c.en) : id;
        };
        const ctl = (id) => byId[id];

        function renderButton(id, extra) {
          const c = ctl(id);
          if (!c) return null;
          return h(Control, {
            key: id, c: c, lang: lang, busy: busy, armed: armed, expanded: expanded, status: s,
            onFire: onFire, onExpand: setExpanded, onPressStart: pressStart, onPressEnd: pressEnd,
          });
        }

        function section(catId, body) {
          return h('div', { key: catId },
            h('div', { className: 'mb-cat' }, '▍' + catName(catId)),
            body)
        }

        // ── 状态 / 日志 ──
        // Group name (the adb / bridge sub-headers inside Channels), per language, from the generated data.
        const g = (gid) => {
          const x = UI_GROUPS.filter((y) => y.id === gid)[0];
          return x ? (lang === 'zh' ? x.zh : x.en) : gid;
        };
        // The bridge's four states, from **one ping plus the state note** — never from a timer.
        const brState = (() => {
          const b = s.bridge || {};
          switch (b.state) {
            case 'running': return (lang === 'zh' ? '运行中' : 'running') + (b.ver ? ' · v' + b.ver : '');
            case 'soft': return lang === 'zh' ? '刚断（可唤醒）' : 'just dropped (wakeable)';
            case 'frozen': return lang === 'zh' ? '长时间未响应（端口在听却不应答）' : 'long silent (bound but not answering)';
            case 'silent': return lang === 'zh' ? '长时间未响应' : 'long silent';
            case 'never': return lang === 'zh' ? '未安装' : 'not installed';
            default: return lang === 'zh' ? '状态查询失败' : 'status check failed';
          }
        })();

        // ── Sections ──
        // Collapsible, and only the first one starts open: a phone panel that dumps 17 buttons on you
        // is the thing the user complained about. Lamps render **only** in 状态/日志 (no duplicates).
        const head = (catId) => h('div', {
          className: 'mb-cat',
          onClick: () => setCollapsed((cur) => { const n = Object.assign({}, cur); n[catId] = !n[catId]; return n; }),
          'aria-expanded': !collapsed[catId],
        }, (collapsed[catId] ? '▸ ' : '▾ ') + catName(catId) + (collapsed[catId] ? (lang === 'zh' ? '　' + countOf(catId) + ' 项' : '  ' + countOf(catId)) : ''));

        function countOf(catId) { return UI_CONTROLS.filter((c) => c.cat === catId).length; }

        const lampRow = () => h('div', { className: 'mb-lamps' },
          h(Lamp, { color: lampOf('dsh', status), aria: t(ctl('lamp_dsh').label),
            text: t(ctl('lamp_dsh').label) + ' ' + (s.dsh && s.dsh.port ? (lang === 'zh' ? '运行中' : 'running') : (lang === 'zh' ? '未运行' : 'stopped')) }),
          h(Lamp, { color: lampOf('bridge', status), aria: t(ctl('lamp_bridge').label),
            text: t(ctl('lamp_bridge').label) + ' ' + (s.bridge && s.bridge.ok ? (lang === 'zh' ? '运行中' : 'running') : (lang === 'zh' ? '未运行' : 'down')) }),
          h(Lamp, { color: lampOf('adb', status), aria: 'adb',
            text: 'adb ' + (((s.adb && s.adb.devices) || [])[0] || (lang === 'zh' ? '未连接' : 'not connected')) }));

        const sections = UI_CATS.map((cat) => {
          // Only what this surface declares: bridge-only controls (copy token, idle auto-stop, …) must
          // not leak into the page panel — the data says which surface shows what.
          const list = UI_CONTROLS.filter((c) => c.surfaces.indexOf('panel') >= 0 && c.cat === cat.id
            && (c.kind !== 'lamp' || cat.id === 'statuslog'));
          if (!list.length) return null;
          const body = [];
          if (!collapsed[cat.id]) {
            let openGroup = '';
            list.forEach((c) => {
              if (c.group && c.group !== openGroup) { openGroup = c.group; body.push(h('div', { key: 'g' + c.group, className: 'mb-sub' }, g(c.group))); }
              if (c.kind === 'lamp') { if (cat.id === 'statuslog' && !body.some((x) => x && x.key === 'lamps')) body.push(h('div', { key: 'lamps' }, lampRow())); return; }
              if (c.kind === 'text') {
                if (c.id === 'bridge_state_text') return;          // that state lives on the switch row
                if (c.id === 'version-update') {
                  body.push(h('div', { key: c.id, className: 'mb-text' }, '面板 v' + UI_VERSION.panel + ' · 控制台 v' + UI_VERSION.console + ' · 桥 v' + UI_VERSION.bridge));
                  return;
                }
                body.push(h('div', { key: c.id, className: 'mb-text' }, t(c.hint)));
                return;
              }
              if (c.kind === 'switch' && c.id === 'lang') {
                body.push(h(LangRow, { key: c.id, lang: lang, mode: props.langMode, onPick: props.onLang }));
                return;
              }
              if (c.kind === 'switch') {
                const isBridge = c.id === 'bridge_run';
                const authed = !!(auth && auth.authorized);
                const note = isBridge ? brState : (auth ? (authed ? (lang === 'zh' ? '已授权' : 'authorized') : (lang === 'zh' ? '未授权' : 'not authorized')) : (lang === 'zh' ? '读取中…' : 'loading…'));
                const dot = isBridge ? lampOf('bridge', status) : (authed ? 'green' : 'grey');
                body.push(h(SwitchRow, {
                  key: c.id, c: c, lang: lang, busy: !!busy, on: isBridge ? !!(s.bridge && s.bridge.ok) : authed,
                  note: note, dot: dot, onExplain: showHint,
                  onToggle: (on) => isBridge ? onToggle(c, on) : setAuthArmed(on ? 'set' : 'revoke'),
                }));
                return;
              }
              // button
              body.push(h(Control, {
                key: c.id, c: c, lang: lang, busy: busy, armed: armed, expanded: expanded, status: s,
                onFire: onFire, onExpand: setExpanded, onPressStart: pressStart, onPressEnd: pressEnd,
              }));
            });
          }
          return h('div', { key: cat.id }, head(cat.id), h('div', null, body));
        });

        const logSheet = logOpen ? h('div', null,
          h('div', { className: 'mb-mask', onClick: () => setLogOpen(false) }),
          h('div', { className: 'mb-sheet', onClick: (e) => e.stopPropagation() },
            h('div', { className: 'mb-head' },
              h('span', { className: 'mb-title' }, t(ctl('log').label) + ' · v' + UI_VERSION.panel),
              h('button', {
                className: 'mb-close', onClick: () => {
                  const rows = logRead();
                  const text = rows.map((r) => new Date(r.ts).toLocaleString() + '  ' + r.head + (r.body ? '\n' + r.body : '')).join('\n\n');
                  if (navigator.clipboard) navigator.clipboard.writeText(text);
                },
              }, lang === 'zh' ? '复制' : 'Copy'),
              h('button', { className: 'mb-close', onClick: () => { logWrite([]); setLogOpen(false); } }, lang === 'zh' ? '清空' : 'Clear'),
              h('button', { className: 'mb-close', onClick: () => setLogOpen(false) }, lang === 'zh' ? '关闭' : 'Close')),
            (() => {
              const rows = logRead();
              if (!rows.length) {
                return h('div', { className: 'mb-log' }, lang === 'zh'
                  ? '还没有记录。点「刷新状态」或上面任何一个按钮，结果就会出现在这里（你点的和自动刷新的会分开标注）。'
                  : 'No entries yet. Tap "Refresh status" or any button above and the result lands here (entries you tapped and automatic refreshes are labelled differently).');
              }
              return h('div', { className: 'mb-log' }, rows.slice().reverse().map((r, i) =>
                h('div', { key: i },
                  h('span', { className: r.who === 'auto' ? 'auto' : 'who' }, (r.who === 'auto' ? (lang === 'zh' ? '［自动］' : '[auto]') : (lang === 'zh' ? '［你点的］' : '[you]')) + ' '),
                  new Date(r.ts).toLocaleString() + '  ' + r.head + (r.body ? '\n' + r.body : ''))));
            })())) : null

        return h('div', null,
          h('div', { className: 'mb-mask', onClick: props.onClose }),
          h('div', { className: 'mb-sheet', onClick: (e) => e.stopPropagation() },
            h('div', { className: 'mb-head' },
              h('span', { className: 'mb-title' }, (lang === 'zh' ? 'DSH 手机面板' : 'DSH mobile panel')),
              h('span', { className: 'mb-ver' }, 'v' + UI_VERSION.panel),
              h('button', { className: 'mb-close' + (unread ? ' badge' : ''), onClick: () => { setLogOpen(true); setUnread(0); }, 'aria-label': t(ctl('log').label) }, t(ctl('log').label) + (unread ? ' ' + unread : '')),
              h('button', { className: 'mb-close', onClick: props.onClose }, lang === 'zh' ? '关闭' : 'Close')),
            busy ? h('div', { className: 'mb-busy' }, h('i', { className: 'mb-spin' }),
              (lang === 'zh' ? '正在跑：' : 'Running: ') + t((busy && ctl(busy.id) ? ctl(busy.id).label : { zh: '任务', en: 'task' })) + '　' + secs + 's') : null,
            sections,
            hintId && ctl(hintId) ? h(HintDialog, {
              c: ctl(hintId), lang: lang, onClose: () => setHintId(''),
            }) : null,
            ask && ctl(ask) ? h(HintDialog, {
              c: ctl(ask), lang: lang,
              onClose: () => setAsk(''),
              onConfirm: () => { const c = ctl(ask); setAsk(''); fireTask(c, c.id); },
            }) : null,
            err ? h('div', { className: 'mb-out' }, '⚠ ' + err) : null,
            out ? h('div', { className: 'mb-out' }, out) : null,
            h('div', { className: 'mb-lamps', style: { marginTop: '10px' } },
              h('span', { className: 'mb-hint' },
                lang === 'zh'
                  ? '危险操作（重启/关闭/真停/紧急停止）需要再点一次确认；长按副标题可展开全文。收回密码使用权有三个入口：这个开关 / 组件 9_撤销密码授权 / rm ~/.dsh-auth-pass。'
                  : 'Dangerous actions (restart / stop / full stop / emergency stop) need a second tap; long-press a subtitle to expand it. The password switch can also be revoked from widget 9 or by rm ~/.dsh-auth-pass.'))),
          logSheet)
      }

      function Fab() {
        const [open, setOpen] = useState(false);
        const [lang, setLang] = useState('en');
        const [langMode, setLangMode] = useState('auto');
        useEffect(() => {
          (async () => {
            try {
              const r = await rpc('panel.readText', { root: HOME, path: HOME + '/.dsh-lang' });
              const m = String(r.content || 'auto').trim();
              setLangMode(m || 'auto');
              setLang(pickLang(m));
            } catch (e) { setLang(pickLang('auto')); }
          })();
        }, []);
        return h(React.Fragment, null,
          h('button', { className: 'mb-fab', title: 'DSH', 'aria-label': 'DSH', onClick: () => setOpen(true) }, '☰'),
          open ? h(TaskSheet, { onClose: () => setOpen(false), lang: lang, langMode: langMode, onLang: async (m) => {
          setLangMode(m); setLang(pickLang(m));
          // Persist to the kit's one language file so the widgets and the two apps follow too — the
          // panel must not keep a private language of its own.
          try { await rpc('panel.writeText', { root: HOME, path: HOME + '/.dsh-lang', content: m + '\n' }); } catch (e) {}
        } }) : null)
      }

      // Each slot registration has its own try/catch: one missing slot must not affect the other, let alone crash the page
      function trySlot(slot, id, render) {
        try {
          ctx.slots.inject(slot, () => ctx.slots.register({ name: slot, id: id }, render))
        } catch (e) {
          console.warn('[mobileui] slot ' + slot + ' failed to register:', e && e.message)
        }
      }

      // ── master switch: {"enabled": false} in ~/.dsh-mobile-ui.json disables everything (takes effect on refresh) ──
      // On by default; stays on when the file cannot be read (works right after install). This is the user's "one-switch undo".
      let ENABLED = true
      const styleTag = document.querySelector('style[data-plugin-css="' + STYLE_ID + '"]')

      function mount() {
        trySlot('shell.overlay', 'mobile-tasks-fab', () => h(Fab, null))
        trySlot('conversation.input.left', 'mobile-tasks-btn', () => h(Fab, null))
      }
      function unmount() {
        if (styleTag) styleTag.remove()
      }

      if (ENABLED) mount()
      ;(async () => {
        try {
          const r = await rpc('panel.readText', { root: HOME, path: HOME + '/.dsh-mobile-ui.json' })
          const cfg = JSON.parse(String(r.content || '{}'))
          if (cfg && cfg.enabled === false) {
            ENABLED = false
            unmount()
            console.log('[mobileui] disabled by ~/.dsh-mobile-ui.json')
          }
        } catch (e) { /* no such file means it stays on by default */ }
      })()

      console.log('[mobileui] client ready')
    }

    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
