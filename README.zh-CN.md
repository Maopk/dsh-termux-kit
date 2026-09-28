# DSH × Termux 手机运维套件

[English](README.md) · **中文**

在一台没 root 的安卓手机上跑 DeepSeek Harness（DSH），而且不用一直盯着它。

在手机上跑 DSH 和电脑上不是一回事：Termux 随时可能被系统冻住，浏览器窗口说没就没，出了问题你在手机上也看不到日志。这个仓库就是补这些的——12 个 Termux 小组件管启停和恢复，一个控制台 App 管点按钮，一个无障碍 App 让 AI 能看屏幕、点按、输入。用到的只有 Termux、Android 的无障碍服务、Termux 官方的 `RUN_COMMAND`，不需要 root，也不需要电脑。

## 为什么写这个

我自己在手机上用 DSH。手机是 vivo，后台冻结很凶：桥（无障碍服务）会周期性被系统回收，adb 无线调试关一次 Wi-Fi 就断，浏览器窗口偶尔被系统直接丢掉。这些事每次手动救一遍太烦，就都写成了脚本和 App。

顺手记了一本账：`docs/DSH运维笔记.md` 里是每次踩坑的证据和复盘。下面「已知的坑」大部分是从那里挑出来的，想看细节可以去翻。

## 跑起来

最省事是直接用发行版里的 APK。最新版本 **[v1.12](https://github.com/Maopk/dsh-termux-kit/releases/tag/v1.12)**：控制台 1.21、桥 2.24、页面面板 0.10.0，三个产物都在发行版页面，附 `SHA256SUMS.txt`。

`master` 上的源码可能比发行版新——推了源码但还没发版时就是这样，所以 App 的版本行会把「你装的 / 发行版最新的 / 仓库源码里的」三个数分开写。

要自己从源码跑：

```bash
# 1) Termux 里装依赖。tesseract 可选，只有本地 OCR 用得到
pkg install zstd imagemagick tesseract tesseract-lang

# 2) 把脚本放到位
git clone https://github.com/Maopk/dsh-termux-kit && cd dsh-termux-kit
install -m755 tools/* ~/.local/bin/
bash tools/install-widgets      # 装 12 个小组件，同时装它们的中文名

# 3) 允许外部 App 调用 Termux 命令（控制台 App 要靠这个）
grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null \
  || echo 'allow-external-apps = true' >> ~/.termux/termux.properties
termux-reload-settings

# 4) 编两个 App（产物在 apps/console/build/ 和 apps/bridge/build/）
bash apps/console/build.sh && bash apps/bridge/build.sh

# 5) 自检：会把 12 个组件走一遍，破坏性的那几步默认只预演
bash tests/selftest.sh
```

装完还有两件事：

1. 装 Termux:Widget，长按桌面 → 小部件 → Termux:Widget，就能看到那 12 个组件。中文名是转发用的，可以在桌面直接认中文。
2. 打开桥 App，在系统设置里给它开无障碍，然后把 App 里显示的 token 交给 DSH（或写进 `~/.dsh-bridge-token`）。

## 它能干什么

- 启动 DSH，并且等**真正就绪**才开浏览器：日志里出现 token 行、那个 URL 返回 200、进程还活着。DSH 是先绑端口后挂路由的，只看端口会给你开出一个 404 页。
- 关闭、软重启、硬重启、备份、清理、紧急停止，各是一个小组件。备份会校验归档（`zstd -t` 和条目数），不是打完就算。
- adb 一条龙：开「无线调试」、找端口、连上、验证；断了能自己修，修不动会说清卡在哪一步。
- 状态一眼看：DSH、桥、adb 三条通道各自的灯和文字。桥和 adb 是两条独立的东西，坏了要能分清是谁坏了，所以它们从不合并成一个开关。
- 让 AI 看屏幕、点按、滑动、输入。无障碍桥走回环 8788，不需要网络；adb 走 shell，能力更强但依赖 Wi-Fi。
- 全自动装 APK：走 vivo 的安装页、点那行蓝色的授权字样、输 6 位锁屏密码。密码只存在 `~/.dsh-auth-pass`（600）。
- 更新两个 App：从发行版下载、校验 SHA256、安装。只升不降。
- 网络急救：判断是 Clash 核心停了还是生成的配置坏了，然后修。
- 三处界面同一套文案：控制台 App、桥 App、网页右下角的面板。改一处，三处一起变。

### 12 个小组件

| 组件 | 干什么 |
|---|---|
| `1_start-dsh` | 启动 DSH，等真就绪再开浏览器 |
| `2_shutdown-dsh` | 停服务并关掉 DSH 窗口；默认保留 Termux（`--close-termux` 才连它一起关） |
| `3_backup-dsh` | 打包并校验归档 |
| `4_soft-restart-dsh` | SIGTERM 后重启 |
| `5_cleanup-dsh` | 清工具自己产生的截图和状态包，不动你的文件 |
| `6_hard-restart-dsh` | 强杀后重启 |
| `7_reconnect-ai` | 恢复 adb 和桥，失败会说清是哪一条 |
| `8_enable-wireless-adb` | 半自动开「无线调试」并接上 adb |
| `9_revoke-pin` | 收回「AI 可用你的锁屏密码过系统验证」这个授权 |
| `10_net-fix` | 修网络：判断 Clash 核心停了还是配置坏了 |
| `11_update-apps` | 把控制台和桥更新到发行版，只升不降 |
| `0_emergency-stop` | 一键撤销 AI 对手机的全部控制：桥、token、adb 无线调试 |

每个组件都支持 `--dry-run`，只打印不执行。

### 常用命令

```bash
dsh-status-pub --json --brief   # 采集状态（DSH、桥、adb、锁、任务）成 JSON
dsh-restart                     # 安全重启：先判页面卡还是服务死了，只杀 bin.js web，等真就绪
dsh-bridge status|wake|stop     # 只操作桥（走回环，不需要网络）
droid conn|shot|ui|tap|text     # 只操作 adb
dsh-uitap "刷新状态"            # 按文字点界面，会自动滚动，比写死坐标可靠得多
dsh-install-apk <apk> --verify  # 全自动装包，装完再开一次 APK 复核
clash-doctor                    # Clash 五项自检，含「节点域名被 fake-ip 吃掉」这个坑
dsh-gh push|release|status      # 维护这个仓库
```

### 三处界面的约定

控制台 App、桥 App、网页面板用的是同一套分类和文案，因为它们都从 `ui/controls.json` 生成。控制台和面板显示 7 类，桥 App 显示其中 4 类（底部折叠着「关于本应用」），小组件覆盖 3 类。

- 按钮点一次触发一次动作，开关表示持续状态（桥运行中、密码使用权），状态灯只读。
- 每个控件名下面有一行灰字，写清它做什么、代价是什么；长按看全文。
- 颜色的含义处处一致：绿是正常，黄是过渡，红是异常，灰是未启用或未安装，灯也按这一套上色。
- 危险用形状区分，不再单独分一类：紧急类动作是实心红底白字；只有「关闭 DSH」（唯一不可逆的那个）是白底红字加红边框；其余危险项是普通样式加一个红色警示记号。
- 语言只有一个开关（`~/.dsh-lang`），小组件、面板和两个 App 都听它的。

## 已知的坑

每条都是真机上遇到的，写清现象、根因、怎么绕。

### 点了「启动 DSH」，页面半天不出来
现象：控制台显示启动完成（exit=0），浏览器或桌面上那个 DSH 窗口没动静。
根因：`dsh web` 启动时自己会去开浏览器，启动脚本也会让 Termux 去开；而 Termux 在后台发 `am start` 有时会被系统丢掉（我实测过：持着 `termux-wake-lock` 时能成功，所以不是"一定不行"）。
绕法：先等一两秒，多数情况是 `dsh web` 自己开的。还没出现就点底部那行「打开 DSH 页面」——那是你自己点的，属于前台操作，一定能开。

### 桥老是掉线
现象：状态里桥变灰，或者点唤醒没反应。
根因：无障碍服务会被系统回收，vivo 上前台切走约 10 秒就可能掉。
绕法：点一下组件 `1`、`7` 或 `8` 就会带 token 把它唤醒；进程被回收过的话冷启动要 20 到 40 秒，别急着判它死了。这是常态，不是坏了。

### adb 接不上，或者一关 Wi-Fi 就断
现象：`adb devices` 是空的；`droid conn` 说连不上。
根因：无线调试要的是**可用的 Wi-Fi 网络**，不只是把开关拨开；Wi-Fi 一断，Android 会顺手把「无线调试」清掉，端口也就没了。
绕法：跑 `droid-ensure`，它会自己走一遍：看 Wi-Fi 状态、借桥把「无线调试」打开、扫端口、连上、验证，哪一步失败说哪一步。想让它长期可用就别关 Wi-Fi 和「无线调试」；想收回就关掉「无线调试」。

### 装 APK 卡住，或者安装页自己消失了
现象：`dsh-install-apk` 停在"等授权页"，然后超时。
根因：vivo 的安装页有个约 50 秒的超时，不操作就自己退；那行「您可授权本次安装」的蓝色后半段不在无障碍树里。
绕法：再跑一次，通常第二次就成了（第一次常常只是把页面拉起来）。确认状态可以看安装器的原话：如果回「已安装相同版本」，说明已经在目标版本上了。

### 用 `pkill -9 -f node` 重启之后，DSH 再也起不来
现象：启动脚本报「另一个启动在进行中」，或者一直失败。
根因：`-9` 会留下一个孤儿写锁（`~/.dsh/.credentials.yaml.lock`），而 DSH 等锁的上限只有 2 秒。
绕法：别用 `pkill -9 -f node`（它还会顺手杀掉别的 node 进程）。用 `dsh-restart`：它会先判断是"页面卡"还是"服务死了"，只杀 `bin.js web`，重启前清掉孤儿锁，等真就绪再把新的认证 URL 写回 `~/.dsh-url`。

### 页面显示「读取不到」，但服务是活的
现象：浏览器打开 DSH 页，提示读不到内容。
根因：多数是 Termux 被系统冻住了，或者你打开的是半启动状态（端口在听、路由还没挂完）。
绕法：先下拉刷新（浏览器里的登录 cookie 是持久密钥签的，重启 DSH 后不用重新拿 URL）。还不行就点组件 `4` 或 `6` 重启一次。

### 冷启动要等 18 秒左右，不是卡住了
根因：瓶颈在 `node_modules` 的加载量（我这份现在是 700MB 上下），跟插件多少关系不大。
绕法：没有捷径，等。启动脚本等的是真就绪，所以到点会自己开页面。

### 关 DSH 时，浏览器窗口没被关掉
现象：脚本说「窗口没关」。
根因：这条路线在没有 adb 时只能用无障碍按键去关，而按键要求那个窗口在前台；把它切到前台会把你从正在做的事里拽走。
绕法：这是故意的取舍——宁可留着窗口也不切你的前台。自己上划掉即可；想要静默关闭就把 adb 接上（有 adb 时用 `force-stop`，完全不影响前台）。

### 版本号那行有三个数，看着像打架
现象：控制台里写着「版本：v1.21 · 发行版还是 v1.14 · 仓库源码 v1.21」。
根因：这三个是不同的事实。你装的那份是本地版本，发行版是 GitHub Releases 里能下载的那个，仓库源码是 `master` 上的版本号。推了源码还没发版，三者就会不一致。
绕法：不用绕，这是如实显示。想让它变成「已是最新」，得发一个发行版。

## 依赖和限制

- 在 Termux 0.119.0-beta.3 上测过。Termux 是 GitHub 预发布版，配套的 addon 要和它同源。
- 在 vivo V2463A / Android 16 上实测通过。换机型可能要改坐标和匹配的文案，安装器文案、省电策略、后台冻结的力度各家都不一样。
- 不需要 root，也没有用任何需要 root 的手段。
- 桥被系统回收是常态，不是故障。
- adb 那条线依赖可用的 Wi-Fi；桥那条线不需要网络。
- 装 APK 时需要你的 6 位锁屏密码过一次系统验证。密码只落在 `~/.dsh-auth-pass`（600），只在替你过系统验证时读。
- 冷启动 18 秒左右是正常的。

## 怎么收回控制权

每条长期通道都有你自己就能收回的入口，不用经过这套工具：

| 通道 | 给了什么 | 怎么收回 |
|---|---|---|
| 无障碍桥 | 看屏、点按、滑动、输入 | 组件 `0_emergency-stop`、`dsh-bridge stop`，或在系统设置里关掉无障碍 |
| 回环 token | 调用桥的凭证 | 删掉 `~/.dsh-bridge-token` |
| adb 无线调试 | shell 级能力，三者里最强的 | `droid-panic`、关掉「无线调试」，或重启手机 |
| 页面遥控 | 点你的 DSH 页面 | 往 `~/.dsh-mobile-ui.json` 写 `{"enabled": false}` |
| 密码使用权 | 用你的锁屏密码过系统验证 | 控制台里的开关、组件 `9_revoke-pin`，或 `rm ~/.dsh-auth-pass` |

密码只在替你过系统身份验证时读，不用来解锁手机翻内容，不用于支付，也不用于与当次任务无关的事。

## 文件结构

| 目录 | 是什么 |
|---|---|
| `apps/console/` | 控制台 App：点按钮的那个 |
| `apps/bridge/` | 无障碍桥 App：让 AI 看屏、点按、输入 |
| `widgets/` | 12 个 Termux 小组件，加公共库 `common.sh` |
| `tools/` | 命令行工具：启动、备份、装包、按文字点界面、Clash 自检等 |
| `plugins/` | 3 个 DSH 页面插件：手机面板、AI 自看遥控、文件面板 |
| `ui/` | 三处界面的文案与配色的源文件：`ui/controls.json`（分类、控件名、一句话说明、危险标记、`appVersions`）与 `ui/theme.json`（27 个颜色 → 两份 `Palette.java`、两份 `res/values/dsh_theme.xml` 和控制台的形状 drawable）。两者都由 `tools/ui-controls gen` 写出，再由 `tools/i18n-audit` 逐项核对 |
| `i18n/zh.json` | 唯一的翻译源，三处界面的中文都从它生成 |
| `tests/selftest.sh` | 自检套件 |
| `docs/` | [运维笔记](docs/DSH运维笔记.md)、[排障](docs/operations.md)、[架构](docs/architecture.md)、[语言](docs/i18n.md) |
| `dist/` | 打好的 APK 和 SHA256 |
| `CONTRIBUTING.md` | 给自己定的规矩：每次改动怎么记录、push 前要过哪些门禁 |
| `CHANGELOG.md` | 每个版本的改动记录，最新在上，格式按 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) |

## 用到的别人的东西

仓库里没有别人的代码。跑起来依赖这些外面装的东西：

- Termux（GPLv3）以及它的 `RUN_COMMAND` 接口、Termux:Widget 插件。整套东西的地基。
- DeepSeek Harness（DSH）本身是 DeepSeek 的东西。这个仓库不是官方项目，跟 DeepSeek 没有关系。
- 构建用 Termux 里的 `aapt2`、`d8`、`apksigner`（Android SDK 构建工具）。
- 打包用 zstd，图片处理用 imagemagick，本地 OCR 用 tesseract（可选）。

## 许可

MIT，见 `LICENSE`。

代码是「在我这台真机上跑通了」交付的，不承诺在你的设备上表现一样。
