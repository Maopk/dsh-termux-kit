# DSH × Termux 手机运维套件

[English](README.md) · **中文**

在一台未 root 的安卓手机上运行 DeepSeek Harness（DSH），无需持续人工监控。

在手机上运行 DSH 与在电脑上运行存在差异：Termux 会被系统冻结，浏览器窗口会被丢弃，出现故障时在设备上也无法读取日志。本仓库用于弥补这些缺口：12 个 Termux 小组件负责启动、停止与恢复，一个控制台 App 提供按钮，一个无障碍 App 让 AI 能够读取屏幕、点按和输入。全部依赖 Termux、Android 的无障碍服务和 Termux 官方的 `RUN_COMMAND`，不需要 root，也不需要电脑。

## 概览

DSH 运行在一台 vivo 手机上，该 ROM 对后台应用的冻结较为激进：无障碍桥会周期性被系统回收，adb 无线调试在 Wi-Fi 断开时立即中断，浏览器窗口偶尔会被系统丢弃。逐一手动恢复的成本过高，因此实现为脚本和 App。

同时维护一份工程日志：`docs/DSH运维笔记.md`（中文）记录了每次遇到问题的证据与复盘。下文「故障排查」中的大部分条目取自该日志。

## 快速开始

最简路径是直接使用最新发行版中的 APK。当前发行版：**[v1.12](https://github.com/Maopk/dsh-termux-kit/releases/tag/v1.12)** —— 控制台 1.21、桥 2.24、页面面板 0.10.0，三个产物均在发行版页面，并附 `SHA256SUMS.txt`。

`master` 上的源码可能比发行版更新——即「已推送源码但尚未发版」的状态。两个 App 的版本行会把「本机安装的 / 发行版最新的 / 仓库源码中的」三项版本号有意分开显示。

要从源码构建：

```bash
# 1) Termux 里装依赖。tesseract 可选，只有本地 OCR 用得到
pkg install zstd imagemagick tesseract tesseract-lang

# 2) 把脚本放到位
git clone https://github.com/Maopk/dsh-termux-kit && cd dsh-termux-kit
install -m755 tools/* ~/.local/bin/
bash tools/install-widgets      # 装 12 个小组件，同时装它们的中文名

# 3) 允许外部 App 调用 Termux 命令（控制台 App 依赖此项）
grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null \
  || echo 'allow-external-apps = true' >> ~/.termux/termux.properties
termux-reload-settings

# 4) 编译两个 App（产物在 apps/console/build/ 和 apps/bridge/build/）
bash apps/console/build.sh && bash apps/bridge/build.sh

# 5) 自检：会把 12 个小组件走一遍，破坏性的那几步默认只预演
bash tests/selftest.sh
```

完成后还有两项操作：

1. 安装 Termux:Widget，长按桌面 → 小部件 → Termux:Widget，即可看到这 12 个小组件。中文名是转发别名，可在桌面直接识别中文。
2. 打开桥 App，在系统设置中为其启用无障碍，然后把 App 中显示的 token 交给 DSH（或写入 `~/.dsh-bridge-token`）。

## 功能

- 启动 DSH，并等待**真正就绪**后才打开浏览器：日志中出现 token 行、该 URL 返回 200、进程仍然存活。DSH 先绑定端口、后挂载路由，因此只检查端口的实现会打开一个 404 页面。
- 关闭、软重启、硬重启、备份、清理、紧急停止各对应一个小组件。备份会校验归档（`zstd -t` 与条目数），而非仅生成文件。
- adb 全流程：开启「无线调试」、查找端口、连接、验证；连接中断后可自动恢复，失败时明确输出失败步骤。
- 状态概览：DSH、桥、adb 三条通道各有状态指示与文字行。桥与 adb 是两条独立通道——出现故障时必须能区分是哪一条——因此从不合并为单个开关。
- 让 AI 能够读取屏幕、点按、滑动和输入。桥使用回环 8788，不需要网络；adb 使用 shell，能力更强，但依赖可用的 Wi-Fi。
- 全自动安装 APK：走 vivo 的安装页、点击蓝色的授权文字、输入 6 位锁屏密码。密码仅保存在 `~/.dsh-auth-pass`（600）。
- 从发行版更新两个 App：下载、校验 SHA256、安装。只升不降。
- 网络修复：判断 Clash 核心已停止还是生成的配置无效，然后修复。
- 三处界面共用一套文案：控制台 App、桥 App 与网页右下角的面板。改一处，三处同时生效。

### 12 个小组件

| 小组件 | 功能 |
|---|---|
| `1_start-dsh` | 启动 DSH，等待真正就绪后打开浏览器 |
| `2_shutdown-dsh` | 停止服务并关闭 DSH 窗口；默认保留 Termux（传入 `--close-termux` 才一并关闭） |
| `3_backup-dsh` | 打包并校验归档 |
| `4_soft-restart-dsh` | 软停（SIGTERM）后重启 |
| `5_cleanup-dsh` | 清理本套件自身产生的截图和状态包，不影响用户文件 |
| `6_hard-restart-dsh` | 强杀（SIGKILL）后重启 |
| `7_reconnect-ai` | 恢复 adb 与桥，失败时指明是哪一条 |
| `8_enable-wireless-adb` | 半自动开启「无线调试」并连接 adb |
| `9_revoke-pin` | 收回「AI 可用你的锁屏密码过系统验证」这一授权 |
| `10_net-fix` | 修复网络：Clash 核心已停止，或生成的配置无效 |
| `11_update-apps` | 将控制台与桥更新至发行版，只升不降 |
| `0_emergency-stop` | 紧急停止：撤销 AI 对手机的控制（桥、token、adb 无线调试） |

每个小组件都支持 `--dry-run`，只打印不执行。

### 常用命令

```bash
dsh-status-pub --json --brief   # 采集状态（DSH、桥、adb、锁、任务）为 JSON
dsh-restart                     # 安全重启：先判定页面无响应还是服务已终止，只结束 bin.js web，等真正就绪
dsh-bridge status|wake|stop     # 只操作桥（走回环，不需要网络）
droid conn|shot|ui|tap|text     # 只操作 adb
dsh-uitap "刷新状态"            # 按文字点击界面，会自动滚动，比写死坐标可靠得多
dsh-install-apk <apk> --verify  # 全自动安装，装完再打开一次 APK 复核
clash-doctor                    # Clash 五项自检，含「节点域名被 fake-ip 吃掉」这一问题
dsh-gh push|release|status      # 维护这个仓库
```

### 三处界面的约定

控制台 App、桥 App 与网页面板使用同一套分类与文案，因为三者均从 `ui/controls.json` 生成。控制台与面板显示 7 类，桥 App 显示其中 4 类（底部折叠「关于本应用」），小组件覆盖 3 类。

- 按钮点击一次触发一次动作；开关表示持续状态（桥运行中、密码使用权）；状态灯只读。
- 每个控件名下方有一行辅助说明，写清它做什么、代价是什么；长按查看全文。
- 颜色的含义处处一致：绿为正常，黄为过渡，红为异常，灰为未启用或未安装；状态灯也按同一规则上色。
- 危险程度用形状区分，不单独分类：紧急类动作为实心红底白字；仅「关闭 DSH」（唯一不可逆的动作）为白底红字加红色边框；其余危险控件使用普通样式加一个红色警示标记。
- 语言只有一项设置（`~/.dsh-lang`），小组件、面板与两个 App 均遵循它。

## 故障排查

每条均在真机上实际发生。每条写清现象、原因与解决方案。

### 点击「启动 DSH」后页面不出现
现象：控制台显示启动完成（exit=0），但没有出现浏览器或 DSH 窗口。
原因：`dsh web` 自身会打开浏览器，启动脚本也会请求 Termux 打开；而 Termux 在后台发出的 `am start` 有时会被系统丢弃。实测：Termux 持有 `termux-wake-lock` 时可以成功，因此并非必然失败。
解决方案：先等待一到两秒，多数情况下是 `dsh web` 自行打开的。若仍未出现，点击控制台底部「打开 DSH 页面」那一行——该点击来自前台，必定生效。

### 无障碍桥连接中断
现象：状态中桥的状态灯变灰，或点击唤醒没有反应。
原因：无障碍服务会被系统回收；在这台 vivo 上，前台切换到其他 App 约 10 秒后即可能中断。
解决方案：点击小组件 `1`、`7` 或 `8` 会带 token 将其唤醒；若进程已被回收，冷启动需要 20 到 40 秒，请勿过早判定进程已终止。属预期行为，非故障。

### adb 无法连接，或 Wi-Fi 断开时连接中断
现象：`adb devices` 为空；`droid conn` 报无法连接。
原因：「无线调试」要求**可用的 Wi-Fi 网络**，而不只是开关已打开；Wi-Fi 一断，Android 会同时清除「无线调试」，端口也随之消失。
解决方案：运行 `droid-ensure`。它会走完整流程——读取 Wi-Fi 状态、借用桥开启「无线调试」、扫描端口、连接、验证——并指明失败的步骤。需要长期可用就不要关闭 Wi-Fi 与「无线调试」；需要收回就关闭「无线调试」。

### APK 安装卡住，或安装页自行消失
现象：`dsh-install-apk` 停在等待授权页，然后超时。
原因：vivo 的安装页在约 50 秒无操作后自行退出；「您可授权本次安装」那行的蓝色后半段不在无障碍树中。
解决方案：再次运行，通常第二次即可成功（第一次往往只是把页面拉起来）。确认状态可读取安装器的原文：若返回「已安装相同版本」，说明已处于目标版本。

### 执行 `pkill -9 -f node` 之后，DSH 无法再次启动
现象：启动脚本报「另一个启动正在进行中」，或持续失败。
原因：`-9` 会留下一个孤儿写锁（`~/.dsh/.credentials.yaml.lock`），而 DSH 等待该锁的上限只有 2 秒。
解决方案：不要使用 `pkill -9 -f node`（它还会终止其他无关的 node 进程）。改用 `dsh-restart`：它会先判定页面无响应还是服务已终止，只结束 `bin.js web`，重启前清除孤儿锁，等待真正就绪，并把新的认证 URL 写回 `~/.dsh-url`。

### 页面提示「读取不到」，但服务仍在运行
现象：浏览器打开 DSH 页，提示读取不到内容。
原因：多数情况是 Termux 被系统冻结，或打开的是半启动实例（端口在监听、路由尚未挂载完成）。
解决方案：先下拉刷新（浏览器中的登录 cookie 由持久密钥签名，重启 DSH 后仍然有效，无需重新获取 URL）。仍无效则用小组件 `4` 或 `6` 重启一次。

### 冷启动约需 18 秒，并非卡住
原因：瓶颈在于需要加载的 `node_modules` 体积（本机当前约 700MB），与插件数量关系不大。
解决方案：没有捷径，等待即可。启动脚本等待的是真正就绪，因此就绪后会自行打开页面。

### 关闭 DSH 后浏览器窗口未关闭
现象：脚本提示「窗口没关」。
原因：在没有 adb 的情况下，关闭其他 App 窗口的唯一途径是通过无障碍桥发送返回键，而这要求该窗口处于前台；将其切到前台会打断用户当前的操作。
解决方案：这是有意为之的取舍——保留窗口优先于抢占前台。手动上划关闭即可；需要静默关闭就接入 adb（有 adb 时使用 `force-stop`，不影响前台）。

### 三项版本号含义不同
现象：控制台中显示「版本：v1.21 · 发行版还是 v1.14 · 仓库源码 v1.21」。
原因：这是三项不同的事实：本机安装的版本、GitHub Releases 中提供的最新版本、`master` 中的版本号。推送源码而未发布发行版时，三者就会不一致。
解决方案：无需处理，这是如实显示。要让它显示「已是最新」，需要发布一个发行版。

## 环境要求与限制

- 在 Termux 0.119.0-beta.3 上测试通过。Termux 是 GitHub 预发布版，配套的 addon 必须与其同源。
- 在 vivo V2463A / Android 16 上实测通过。更换机型可能需要调整坐标与匹配文案：安装器文案、省电策略、后台冻结力度因 ROM 而异。
- 不需要 root，也未使用任何需要 root 的手段。
- 桥被系统回收属预期行为，非故障。
- adb 通道依赖可用的 Wi-Fi；桥通道不需要网络。
- 安装 APK 时需要 6 位锁屏密码过一次系统验证。密码仅保存在 `~/.dsh-auth-pass`（600），仅在代为通过系统验证时读取。
- 冷启动约 18 秒属于正常情况。

## 安全与权限

每条长期通道均可由用户自行回收，无需经过本套件：

| 通道 | 授予的能力 | 回收方式 |
|---|---|---|
| 无障碍桥 | 读取屏幕、点按、滑动、输入 | 小组件 `0_emergency-stop`、`dsh-bridge stop`，或在系统设置中关闭无障碍 |
| 回环 token | 调用桥的凭证 | 删除 `~/.dsh-bridge-token` |
| adb 无线调试 | shell 级能力，三者中最强 | `droid-panic`、关闭「无线调试」，或重启手机 |
| 页面遥控 | 点击 DSH 页面 | 向 `~/.dsh-mobile-ui.json` 写入 `{"enabled": false}` |
| 密码使用权 | 使用锁屏密码通过系统验证 | 控制台开关、小组件 `9_revoke-pin`，或 `rm ~/.dsh-auth-pass` |

密码仅在代为通过系统验证时读取，不用于解锁手机翻看内容，不用于支付，也不用于与当次任务无关的事项。

## 仓库结构

| 目录 | 内容 |
|---|---|
| `apps/console/` | 控制台 App：提供按钮的界面 |
| `apps/bridge/` | 无障碍桥 App：读取屏幕、点按、输入 |
| `widgets/` | 12 个 Termux 小组件，以及公共库 `common.sh` |
| `tools/` | 命令行工具：启动、备份、安装 APK、按文字点击界面、Clash 自检等 |
| `plugins/` | 3 个 DSH 页面插件：手机面板、AI 自看遥控、文件面板 |
| `ui/` | 三处界面的文案与配色源文件：`ui/controls.json`（分类、控件名、一句话说明、危险标记、`appVersions`）与 `ui/theme.json`（27 个颜色 → 两份 `Palette.java`、两份 `res/values/dsh_theme.xml` 和控制台的形状 drawable）。两者均由 `tools/ui-controls gen` 写出，再由 `tools/i18n-audit` 逐项核对 |
| `i18n/zh.json` | 唯一的翻译源，三处界面的中文均从它生成 |
| `tests/selftest.sh` | 自检套件 |
| `docs/` | [运维笔记](docs/DSH运维笔记.md)、[排障](docs/operations.md)、[架构](docs/architecture.md)、[语言](docs/i18n.md)、[术语表](docs/术语表.md) |
| `dist/` | 构建出的 APK 及其 SHA256 |
| `CONTRIBUTING.md` | 本仓库遵循的规则：每次改动如何记录、push 前需要过哪些门禁 |
| `CHANGELOG.md` | 每个版本的改动记录，最新在上，格式按 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) |

## 第三方组件

仓库中不含第三方代码。运行依赖以下外部安装的组件：

- Termux（GPLv3）及其 `RUN_COMMAND` 接口、Termux:Widget 插件。整套工具的基础。
- DeepSeek Harness（DSH）本身属于 DeepSeek。本仓库不是官方项目，与 DeepSeek 无关。
- 构建使用 Termux 中的 `aapt2`、`d8`、`apksigner`（Android SDK 构建工具）。
- 打包使用 zstd，图片处理使用 imagemagick，本地 OCR 使用 tesseract（可选）。

## 许可

MIT，见 `LICENSE`。

代码按「在本机真机上验证通过」交付，不保证在其他设备上具有相同表现。
