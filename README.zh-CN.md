# DSH × Termux 手机运维套件

[English](README.md) · **中文**

在一台**未 root 的安卓手机**上，把 DeepSeek Harness（DSH）做成「能自己启动、能自己恢复、坏了会自己说清楚」的东西。

全部基于 **Termux + 无障碍服务 + 官方 `RUN_COMMAND` 通道**，不需要 root、不需要电脑。

---

## 包含什么

| 目录 | 内容 |
|---|---|
| `apps/console/` | **DSH 控制台 App** 源码：12 个组件的图形界面 + 桌面小部件 |
| `apps/bridge/` | **DSH 桥 App** 源码：无障碍服务 + 回环接口，让 AI 能看屏、点按、滑动、输入 |
| `widgets/` | 12 个 Termux 桌面小组件 + 公共库 `common.sh` |
| `tools/` | 26 个命令行工具（启动、备份、装包、按文字点界面、Clash 自检…） |
| `plugins/` | 3 个 DSH 页面插件（手机任务面板 / AI 自看遥控 / 文件面板） |
| `ui/theme.json` | **两个 App 的唯一色板 + 主题源**：27 个颜色 → 两份 `Palette.java`、两份 `res/values/dsh_theme.xml`（窗口底色/状态栏/导航栏/滚动辉光/对话框）与控制台的形状 drawable。`tools/ui-controls gen` 生成、`tools/i18n-audit` 逐项核对（含「源码与 res 里不许有裸色值」），所以「滚到底露出一块灰」「两处一个深一个浅」这类问题在结构上不可能再出现 |
| `ui/controls.json` | **三处 UI 的唯一文案源**：分类、控件名、一句话后果、危险标记、出现在哪几处。`tools/ui-controls` 生成页面插件的控件块和两个 App 的 `UiControls.java`；`ui-controls check`（逐个产物比 md5）已进自检 |
| `tests/selftest.sh` | 自检套件：93 项（语法 → 预演 → 回归 → 真跑 → 沙箱冷启动 → 工具/UI 漂移） |
| `docs/` | 运维笔记（每次踩坑的证据与复盘）+ 架构与数据流 |
| `dist/` | 已构建的 APK + SHA256 |

---

## 安装

```bash
# 1) Termux（本仓库在 0.119.0-beta.3 上测过），装依赖
pkg install zstd imagemagick tesseract tesseract-lang   # tesseract 可选（本地 OCR）

# 2) 把脚本放到位
git clone <本仓库> dsh-termux-kit && cd dsh-termux-kit
install -m755 tools/*   ~/.local/bin/
mkdir -p ~/.shortcuts/tasks ~/.local/share/dsh-widgets
install -m755 widgets/*.sh ~/.shortcuts/tasks/
install -m755 tests/selftest.sh ~/.local/share/dsh-widgets/
install -m755 widgets/common.sh ~/.local/share/dsh-widgets/

# 3) Termux 里允许外部 App 调用命令（控制台 App 要用）
grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null \
  || echo 'allow-external-apps = true' >> ~/.termux/termux.properties
termux-reload-settings

# 4) 装两个 App（可直接用 dist/ 里的 APK，也可自己构建）
bash apps/console/build.sh && bash apps/bridge/build.sh
#   产物：apps/console/build/dsh-console.apk、apps/bridge/build/dsh-bridge.apk

# 5) 自检
bash tests/selftest.sh
```

装完还有两件事：
1. **桌面小组件**：装 Termux:Widget 后，长按桌面 → 小部件 → Termux:Widget，就能看到 `~/.shortcuts/tasks/` 里的 12 个组件。
2. **桥 App**：打开它，在系统设置里给「DSH 桥」开启**无障碍**，再把 App 里显示的 token 交给 DSH（或写进 `~/.dsh-bridge-token`）。

---

## 使用说明

### 三处 UI 的统一规范

桥 App / 控制台 App / 页面面板的**分类、控件名、说明文字完全一致**，因为它们出自同一份
`ui/controls.json`（`tools/ui-controls` 生成，`check` 比三处 md5 并进自检）：

- **七个分类**（＋桥的「关于本应用」）：启动·停止 / 通道（adb 与桥分开）/ 维护 / 安全 / 设置 / 紧急 / 状态·日志。
- **类型分清**：按钮＝点一次触发一次动作；开关＝持续状态（桥运行中、闲置自动软停、密码使用权）；状态灯＝只读。
- **每条都有说明**：控件名下面一行灰字写「做什么 + 代价」，**长按看全文**。危险分三级、靠**形状**区分：紧急类＝实心红底白字；**只有「关闭 DSH」**（不可逆的那一个）＝白底红字 + 红边框；其余危险项＝普通样式 + ⚠（用户 2026-09-27：三个红边框并排反而谁都不像危险动作）。
- **颜色语义固定**：绿=正常、黄=过渡、红=异常、灰=未启用/未安装；状态灯按 `indexOf('●')` 动态定位上色，不写死索引。
- **桥有四态**，由一次 ping 加状态记录判定（**不用计时**）：运行中 / 刚断（可唤醒）/ 长时间未响应 / 未安装。
- **跨 UI 同步**：密码使用权以磁盘上那个 600 文件为准 —— 任一处拨完立刻写、别处刷新即读到，两边都不存缓存。


### 桌面小组件（`~/.shortcuts/tasks/`，由 Termux:Widget 调用）

| 组件 | 作用 |
|---|---|
| `1_start-dsh` | 启动 DSH，并**等真正就绪**（日志出现 token 行 + 该 URL 返回 200 + 进程活着）才开浏览器 |
| `2_shutdown-dsh` | 停服务 + **关 DSH 窗口**（adb 优先；没 adb 就借无障碍桥按键关，不再假报成功）；默认**保留 Termux**（`--close-termux` 才连它一起关，因为控制台要靠 Termux 才能刷新）。`--keep-bridge` 可保留桥 |
| `3_backup-dsh` | 打包并校验归档（`zstd -t` + 条目数） |
| `4_soft-restart-dsh` / `6_hard-restart-dsh` | SIGTERM / -9 后重启（先取锁再动手，连点不会自杀） |
| `5_cleanup-dsh` | 清工具自己的产物：**我的截图默认全清**、状态包留 5 份、**完整快照留 1 份**；不碰你的文件（名字怪的我产出图写进 `图片/.dsh-images.list` 就会被认领） |
| `7_reconnect-ai` | adb 与桥一起恢复，失败会分别说清是哪条 |
| `8_enable-wireless-adb` | 半自动开「无线调试」并接上 adb（Wi-Fi 关着时打开设置页等你点一下，之后自动继续） |
| `9_revoke-pin` | 收回「AI 可用你的锁屏密码过系统验证」这个授权 |
| `0_emergency-stop` | 一键撤销 AI 对手机的全部控制（桥、token、adb 无线调试） |

所有组件都支持 `--dry-run`（只打印不执行）。

### DSH 控制台 App

- 顶部三盏灯：DSH / 桥 / adb 的状态，下面一行明细。灯的**颜色和文字同源**：桥那盏由 `status.json` 的 `bridge.state` 一处判定（绿=在听且真应答 / 黄=端口在听却不应答 / 灰=软停·真停·未安装），开关位置也读同一个值，不会再出现「绿灯 + 桥：未知」这种自相矛盾。
- 最近一次结果那条**固定在窗口底部**，不随分类滚动；没有结果时整条不显示。
- 按钮**按功能分类**：`启动·停止` / `通道（adb 与桥分开）` / `维护` / `安全`（密码使用权）/ `设置`（语言、项目主页、版本）/ `紧急` / `状态·日志`。「真停桥」不再和开关并排，移进通道里的子分类 `危险操作 · 只能你手动恢复`。
- **日志**独立一屏：发送、回传、退出码、原始输出都在里面；有未读时按钮带角标。
- 「密码使用权」是个**开关**（在「安全」类，不再和备份/清理并列）：打开＝AI 可用你的 6 位锁屏密码替你过系统身份验证；关掉＝立刻收回。副标题只写「当前：已授权/已收回」，文件路径、`mode 600`、写入时间这类技术细节**长按**才显示。
- 危险动作（重启/关闭/紧急停止/收回授权）需要二次确认。
- 超时按任务给窗口（备份 420s／重启 300s／查询 25s 且自动补发一次），超时文案如实说明「手机当时很忙」这类原因。
- 权限只有 1 个：`com.termux.permission.RUN_COMMAND`；没有存储、网络、无障碍、悬浮窗权限。

### DSH 页面插件（`plugins/`）

把目录放进 DSH profile 的 `local/` 下，并在 `package.json` 的 `dependencies` 与 `dsh.profile.bundles`
里都登记，然后 `pnpm install` + 刷新页面：

- `dsh-mobile-local`：页面右下角 ☰ → 与控制台**同一套五分类**的移动端面板（状态灯、带一句话说明的任务按钮、桥运行开关、密码使用权开关；日志面板可复制/清空、带未读角标、忙碌时也能看）。它跟随 `~/.dsh-lang`，一个语言设置同时管住小组件、面板和两个 App；
  后端是 `tools/dsh-tasksd`（`127.0.0.1:8787`，token + 白名单）。
- `dsh-selflook-local`：把页面渲成 PNG（供 AI 自看），并把点击/滑动/求值指令下发到页面。
- `dsh-filepanel-local`：文件面板的本地实现，同时给上面两个插件当 RPC 通道。

### 常用命令

```bash
dsh-status-pub --json --brief   # 采集状态（DSH/桥/adb/锁/任务）→ JSON
dsh-restart                     # 安全重启：先判"页面卡"还是"服务死"，只杀 bin.js web，等真就绪
dsh-bridge status|wake|stop     # 只操作桥（无障碍回环，不需要网络）
droid conn|shot|ui|tap|text     # 只操作 adb 那条线
dsh-uitap "刷新状态"            # 按文字点界面（自动滚动，比写死坐标可靠）
dsh-install-apk <apk> --verify  # 全自动装包，装完再开一次 APK 复核
clash-doctor                    # Clash 五项自检（含"节点域名被 fake-ip 吃掉"这个坑）
dsh-gh push|release|status      # 维护本仓库：推送 / 发发行版 / 看状态
```

---

## 撤销与安全

每条长期通道都配了「你自己能一键收回」的入口：

| 通道 | 给了什么 | 怎么收回 |
|---|---|---|
| 无障碍桥 | 看屏、点按、滑动、输入 | 组件 `0_emergency-stop`／`dsh-bridge stop`／关掉系统里的无障碍开关 |
| 回环 token | 调用桥的凭证 | 删掉 `~/.dsh-bridge-token` |
| adb 无线调试 | shell 级能力（最强） | `droid-panic`／关掉「无线调试」／重启手机 |
| 页面遥控 | 点你的 DSH 页面 | `~/.dsh-mobile-ui.json` 写 `{"enabled": false}` |
| 密码使用权 | 用你的锁屏密码过系统验证 | 控制台开关／组件 `9_revoke-pin`／`rm ~/.dsh-auth-pass` |

密码只存在 `~/.dsh-auth-pass`（`chmod 600`），只在替你过系统身份验证时读取，
不用于解锁手机翻内容、支付/免密或与当次任务无关的场景。

---

## 已知限制

- 不同 ROM 的安装器文案、无障碍行为、省电策略都不一样。本套件在 **vivo / Android 16** 上实测通过，
  换机器可能需要改坐标或判定文案。
- **adb 无线调试依赖可用的 Wi-Fi 网络**（不只是把开关拨开）；重启手机后需要人工再开一次「无线调试」。
- **无障碍桥会被系统回收**（前台切走约 10 秒可能掉线），属常态；靠带 token 的广播唤醒，冷启动可能等 20~40 秒。
- 首次安装 APK 时系统会要求指纹/锁屏密码确认——那是系统的安全验证，本套件只是让这个流程可自动化。

---

## 许可

MIT（见 `LICENSE`）。代码按「在某台真机实测可用」交付，不承诺在你的设备上同样可用。
