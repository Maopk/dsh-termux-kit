# DSH × Termux 手机运维套件

在一台**未 root 的安卓手机**上，把 DeepSeek Harness（DSH）做成「能自己启动、能自己恢复、坏了会自己说清楚」的东西。

全部基于 **Termux + 无障碍服务 + 官方 `RUN_COMMAND` 通道**，不需要 root、不需要电脑。

---

## 包含什么

| 目录 | 内容 |
|---|---|
| `apps/console/` | **DSH 控制台 App** 源码：9 个组件的图形界面 + 桌面小部件 |
| `apps/bridge/` | **DSH 桥 App** 源码：无障碍服务 + 回环接口，让 AI 能看屏、点按、滑动、输入 |
| `widgets/` | 9 个 Termux 桌面小组件 + 公共库 `common.sh` |
| `tools/` | 26 个命令行工具（启动、备份、装包、按文字点界面、Clash 自检…） |
| `plugins/` | 3 个 DSH 页面插件（手机任务面板 / AI 自看遥控 / 文件面板） |
| `tests/selftest.sh` | 自检套件：58 项（语法 → 预演 → 回归 → 真跑 → 沙箱冷启动） |
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
1. **桌面小组件**：装 Termux:Widget 后，长按桌面 → 小部件 → Termux:Widget，就能看到 `~/.shortcuts/tasks/` 里的 9 个组件。
2. **桥 App**：打开它，在系统设置里给「DSH 桥」开启**无障碍**，再把 App 里显示的 token 交给 DSH（或写进 `~/.dsh-bridge-token`）。

---

## 使用说明

### 桌面小组件（`~/.shortcuts/tasks/`，由 Termux:Widget 调用）

| 组件 | 作用 |
|---|---|
| `1_启动DSH` | 启动 DSH，并**等真正就绪**（日志出现 token 行 + 该 URL 返回 200 + 进程活着）才开浏览器 |
| `2_关闭DSH` | 停服务 + **关 DSH 窗口**（adb 优先；没 adb 就借无障碍桥按键关，不再假报成功）；默认**保留 Termux**（`--close-termux` 才连它一起关，因为控制台要靠 Termux 才能刷新）。`--keep-bridge` 可保留桥 |
| `3_备份DSH` | 打包并校验归档（`zstd -t` + 条目数） |
| `4_软重启DSH` / `6_硬重启DSH` | SIGTERM / -9 后重启（先取锁再动手，连点不会自杀） |
| `5_清理DSH` | 清工具自己的产物：**我的截图默认全清**、状态包留 5 份、**完整快照留 1 份**；不碰你的文件（名字怪的我产出图写进 `图片/.dsh-images.list` 就会被认领） |
| `7_重连AI通道` | adb 与桥一起恢复，失败会分别说清是哪条 |
| `8_自动开无线调试` | 半自动开「无线调试」并接上 adb（Wi-Fi 关着时打开设置页等你点一下，之后自动继续） |
| `9_撤销密码授权` | 收回「AI 可用你的锁屏密码过系统验证」这个授权 |
| `0_紧急停止` | 一键撤销 AI 对手机的全部控制（桥、token、adb 无线调试） |

所有组件都支持 `--dry-run`（只打印不执行）。

### DSH 控制台 App

- 顶部三盏灯：DSH / 桥 / adb 的状态，下面一行明细（都带时间）。
- 按钮**按功能分类**：`启动·停止` / `通道（adb 与桥分开）` / `维护` / `紧急`。
- **日志**独立一屏：发送、回传、退出码、原始输出都在里面；有未读时按钮带角标。
- 「密码使用权」是个**开关**：打开＝AI 可用你的 6 位锁屏密码替你过系统身份验证；关掉＝立刻收回。
- 危险动作（重启/关闭/紧急停止/收回授权）需要二次确认。
- 超时按任务给窗口（备份 420s／重启 300s／查询 25s 且自动补发一次），超时文案如实说明「手机当时很忙」这类原因。
- 权限只有 1 个：`com.termux.permission.RUN_COMMAND`；没有存储、网络、无障碍、悬浮窗权限。

### DSH 页面插件（`plugins/`）

把目录放进 DSH profile 的 `local/` 下，并在 `package.json` 的 `dependencies` 与 `dsh.profile.bundles`
里都登记，然后 `pnpm install` + 刷新页面：

- `dsh-mobile-local`：页面右下角 ☰ → 手机端任务面板（分类任务 + 状态灯 + 密码使用权开关）；
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
| 无障碍桥 | 看屏、点按、滑动、输入 | 组件 `0_紧急停止`／`dsh-bridge stop`／关掉系统里的无障碍开关 |
| 回环 token | 调用桥的凭证 | 删掉 `~/.dsh-bridge-token` |
| adb 无线调试 | shell 级能力（最强） | `droid-panic`／关掉「无线调试」／重启手机 |
| 页面遥控 | 点你的 DSH 页面 | `~/.dsh-mobile-ui.json` 写 `{"enabled": false}` |
| 密码使用权 | 用你的锁屏密码过系统验证 | 控制台开关／组件 `9_撤销密码授权`／`rm ~/.dsh-auth-pass` |

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
