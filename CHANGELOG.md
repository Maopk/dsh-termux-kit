# 更新日志

> 格式：[Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/) · 版本号规则见 [CONTRIBUTING.md](CONTRIBUTING.md) §六。
> **2026-09-27 之前的条目是旧的"日期 + 散文"格式，作为历史保留，不再改写。**

## [Unreleased]

（暂无。）

## [v1.13] - 2026-10-03

控制台 **1.22** · 桥 **2.24** · 页面面板 **0.10.1** —— 三个产物都在发行版页面，并附 `SHA256SUMS.txt`。

### 新增

· **`tools/dsh-kit-update`：手机侧一条命令把整套更新到最新**（`# install: runtime`，会随 `install-tools` 装进 `~/.local/bin`）：
  先看本地改动（有就停下报告，不替你 stash）、`git fetch origin master`、**只接受快进合并**（本地领先或分叉就列出来让你决定），
  然后依次刷新 `~/.local/bin` 里的工具与 12 个桌面组件，最后核对「仓库与已装工具一致」。`--check` 只报告、不动手。
  为什么要有它：2026-10-03 发 v1.13 时，单跑 `git pull` 因为分支没设上游 **只更新了远端指针、一个文件都没合并**
  （退出状态看着还像成功），后面的工具刷新、控制台编译、面板打包**全在旧代码上跑** —— `install-tools` 报"一致 40 · 需同步 0"、
  控制台打出旧版本号 `1.21`、`npm pack` 打出旧版本 `0.10.0`，直到打包那步才露馅。顺序从此固定在这一个入口里。
· **控制台多了「自检」按钮**（🩺，维护区，只出现在控制台）：一键在 Termux 里跑完整的 `tests/selftest.sh`
  —— 70 项（语法、每个组件的 `--dry-run`、就绪回归、安全可逆的真跑、8099 端口上的一次性冷启动），
  `════ Result: … ════` 那行会直接落在控制台的历史里（结果回传保留尾部 4000 字符，这行正好在尾部）。
  为什么放控制台而不是桌面组件：它要跑几分钟、会压手机负载，放桌面一次误触代价太大；
  按钮**不带危险标记**——那套红框确认是给重启/停机的口径，而套件自己会把会话杀手类检查标成 SKIP。
  命令按套件的口径解析仓库位置（`DSH_KIT_REPO`，不设时用 `~/dsh-termux-kit`）；
  `tools/check-task-ids` 的例外表新增一条，写明它为什么不经过 tasksd 的白名单。

### 新增（CI）

· **CI（`.github/workflows/ci.yml`）**：5 个 job 跑这个仓库自己能证的离线部分 ——
  `bash -n` 全量 + shellcheck（`tools/ci-shellcheck.sh`）、`ruff` + 逐文件 `mypy` + 编译检查、
  9 道数据源门禁（`tools/ci-gates.sh`：任务 id / 密钥 / UI 与数据源 / i18n 表 / Java 转义 / 安装一致性 /
  面板渲染 / 文案审计）、`widgets/common.sh` 的单元测试（`tests/lib-tests.sh`），
  以及在容器里跑 `tests/selftest.sh`（`tests/ci-selftest.sh`，汇总写进 run summary 并上传产物）。
  钉版本的开发依赖在 `requirements-dev.txt`（shellcheck-py / ruff / mypy），配置在 `ruff.toml`、`mypy.ini`、`.shellcheckrc`。
  门禁读仓库里的 `apps/` 镜像与 `i18n/zh.json`，不联网、不碰手机，同一个 commit 永远同一结论；
  `tools/sync-apps` 会覆写 `apps/`，CI 里不跑。
  同一个 job 里还把 `tools/pre-push-check` 的整份输出录进产物（只记录、不判红）——容器里那份清单
  实测能跑到"通过 12 · 失败 0 · 提醒 0"（头两次跑出来的 ✘ 全是环境造成的：工具默认去 `$HOME/dsh-termux-kit`
  找仓库、git 不信任容器里的工作目录、python3 按 ASCII 输出撞上工具打的 ✔/✘、自己的工作目录被当成未跟踪文件）。
  `CONTRIBUTING.md` §十一 补了一张"每个脚本在 CI 里的去向"表：跑在哪、或者为什么不跑（要 APK/SDK、
  要真机截图、会覆写 `apps/`、是运行期工具），照着仓库里的脚本逐个过了一遍。
· **`tests/lib-tests.sh`**：把 `widgets/common.sh` 里能单独测的函数做成 35 条断言 —— `token_from_log`（含"最后一条生效"）、
  `port_open` / `wait_port_open` / `wait_port_free`、`url_code(200/000)` / `url_ready`、`boot_lock_acquire` /
  `owner` / `release` / `stale`（含"进程死了算过期"与"活着但锁太旧也算过期"）、`clear_orphan_cred_lock`（两个方向）、
  `rotate_log`（3MB 归档、小文件不动）、`run` 的 dry-run 与真跑两路、`dsh_log`、`parse_args --dry-run`。
· **`tests/ci-selftest.sh`**：给 `tests/selftest.sh` 造一个假手机的 `HOME`（widgets → `~/.shortcuts/tasks`、
  库 → `~/.local/share/dsh-widgets`、tools → `~/.local/bin`），把需要真机或联网才能过的检查列进 `OFF_PHONE`
  只记录不判红，其余任何失败都让 CI 变红；`--strict` 原样透传套件退出码。

### 修复

· **两个 App 的构建脚本不再"悄悄换一把签名钥匙"**（`apps/console/build.sh`、`apps/bridge/build.sh`）：
  原来只在 `$SRC/ks.jks` 不存在时 `keytool -genkeypair` 新生成一把 —— 而 `ks.jks` 在 `.gitignore` 里（`*.jks`）、
  只存在于**当初出包的那个目录**，所以换一个构建目录（真源码 `~/dsh-console` → 仓库里的 `apps/console`）就会生成新钥匙，
  签出来的包与手机上已装的那份签名不一致，安装器直接拒绝覆盖，**只报一句 "App not installed"**，不解释原因。
  2026-10-03 发 v1.13 时真踩了（新生成的 `8b2dee4e…` vs 手机上已装的 `da10e3f5…`），从 `~/dsh-console/ks.jks` 拷回来才签对。
  现在按 `DSH_KS` → `$SRC/ks.jks` → `$HOME/dsh-console/ks.jks`（桥是 `$HOME/droid-bridge/ks.jks`）→ `$HOME/.dsh-console/ks.jks`
  的顺序找一把**已经存在**的密钥库，全都没有才新建，并把用的是哪一个打印出来（`Keystore: …`）。
· **两个面板插件的 `npm pack` 不再把上一次的 tgz 打进包里**（`plugins/dsh-mobile-local`、`plugins/dsh-filepanel-local`）：
  加 `files` 白名单（`client.js` / `cordis.patch.yml` / `host.js` / `package.json`）。没有它时 `npm pack` 会收进目录里的一切 ——
  v1.13 第一次打出来的面板包是 39.5 kB / 5 个文件，因为里面还裹着上一次的 `dsh-mobile-local-0.10.0.tgz`（正常 19.4 kB / 4 个）。
· **三个生成器不再写出 CRLF**（`tools/ui-controls`、`tools/i18n-table`、`tools/i18n-build-table`）：写入时显式
  `newline='\n'`。不写这一条时 Python 会把 `\n` 翻译成宿主平台的行尾 —— 在 Windows 上重生成 `i18n/zh.json`、
  两份 `Lang.java`、`widgets/i18n.sh`、面板 `client.js` 和几个主题/控件文件，会把**每一行**都改掉（整文件 diff），
  而仓库是 LF-only。`widgets/i18n.sh` 尤其危险：CRLF 的 bash 库会让每条文案带一个尾随 `\r`。
  CI 在 Linux 上跑不出这个问题（那边本来就写 LF），所以是"只有在一台 Windows 机器上重生成才会踩到"的坑。
· **两个 App 的构建脚本不再写死路径**（`apps/console/build.sh` / `apps/bridge/build.sh`）：改为从脚本自身所在目录
  取 `SRC`、从 `$SRC/../..` 取仓库根目录，仓库 clone 到任意位置都能编译；`android.jar` 可用 `DSH_AJ` 覆盖
  （默认仍是 `$HOME/.smoke/android.jar`），缺它时先打印它是什么、从哪取、怎么指过来，而不是编到一半报一句
  命令不存在；编译前先检查 `aapt2 / javac / d8 / zip / keytool / apksigner` 是否在 PATH 上。
· **README 第 4 步写明编译前提**（中英两份）：先备好 `android.jar`，再跑两个 `build.sh`。
· **术语修正**：`README.zh-CN.md` 的「工程日志」改成「运维笔记」（见 `docs/术语表.md` 第 44 行）。
· **`widgets/i18n.sh` 恢复可执行位**：上一批它被按 100644 发布，`./widgets/i18n.sh` 这类直接调用会失败；
  本批按文件带上 mode 重新发布（脚本保持 100755），顺带说明"脚本别用一律 100644 的方式发布"。
  （本批不改三个产物的内容，不 bump 版本号，见 §六。）
· **8 个工具与自检脚本不再假设仓库住在 `$HOME/dsh-termux-kit`**：`tools/app-verify`、`tools/check-task-ids`、
  `tools/dsh-gh`、`tools/i18n-audit`、`tools/i18n-table`、`tools/ui-controls`、`tools/sync-apps`、
  `tools/panel-render-test` 与 `tests/selftest.sh` 统一认 `DSH_KIT_REPO`（`pre-push-check`、`install-tools`
  本来就认它），不设时仍回退到 `$HOME/dsh-termux-kit`；规矩写进 `CONTRIBUTING.md` §八 的工具表下面。
· **`tools/pre-push-check` 的第 8 条补上 `ci` 类型**（`CONTRIBUTING.md` §五 同步）：CI 那几笔 commit 用
  `ci:` 开头，原来会被自己判成"不符合规范"。`.gitignore` 也补上 CI 的工作目录 `ci-selftest-work/`，
  否则自检会把 CI 自己刚生成的产物同时记成"未跟踪文件"和"疑似明文凭据"。
· **读 App 真源码的 5 个工具不再假设源码住在 `$HOME`**：`tools/app-verify`、`tools/i18n-audit`、
  `tools/i18n-table`、`tools/ui-controls`、`tools/sync-apps` 统一认 `DSH_CONSOLE_DIR` 与 `DSH_BRIDGE_DIR`
  （不设时回退 `~/dsh-console`、`~/droid-bridge`）；规矩与 `DSH_KIT_REPO` 并列写在同一处。
· **静态检查抓出来的真问题**：`tools/dsh-tasksd` 的 `last` 字段取的是**编译后的正则**的下标（`re.compile` 的结果不能下标，
  异常又被外层 `except Exception: pass` 吞掉），于是它永远是 null —— 改成取刚算出的状态列表的最后一项；
  `tools/install-widgets` 数语言条数用的 `ls | grep -c` 换成 glob 计数（文件名里有空格也不会数错）；
  `widgets/2_shutdown-dsh.sh:123` 与 `widgets/8_enable-wireless-adb.sh:166` 的提示语双引号套双引号（字符串会提前闭合）改用单引号；
  7 处 `cd` 补上失败出口（SC2164）：`tools/dsh-restart`、`widgets/0_emergency-stop.sh`、`widgets/1_start-dsh.sh`、
  `widgets/2_shutdown-dsh.sh`、`widgets/4_soft-restart-dsh.sh`、`widgets/6_hard-restart-dsh.sh`、`widgets/7_reconnect-ai.sh`；
  `tools/clash-override.py` 13 处 `%` 格式化改 f-string（输出逐字不变）、两处 `subprocess.run` 显式写 `check=False`、
  `tools/check-task-ids` 与 `tools/ui-controls` 删掉没用到的 import、`tools/i18n-audit` 补类型标注（mypy）、
  `tools/app-verify` 保留"读 theme.json"这一句并注明原因（删了会让缺文件时不再报错）。
· **12 个小部件脚本的 `HOME_DIR` 不再写死手机路径**：`widgets/[0-9]*.sh` 统一改成
  `HOME_DIR="${DSH_HOME_DIR:-/data/data/com.termux/files/home}"`（`widgets/common.sh` 上一批已改），手机上行为不变。
  这是自检套件在容器里跑不起来的真正原因：原来它们 source 不到库，12 个 `--dry-run` 全报 `command not found`（rc=127）。
· **`tools/install-tools --check` 不再顺手建目录**：安装位不存在时只打印一行说明就返回 0，不再 `mkdir -p ~/.local/bin`
  （检查动作不该改机器）。
· **`tests/selftest.sh` 三处裸 `$TMPDIR` 改成 `${TMPDIR:-${PREFIX:-/tmp}}`**（`:225`、`:383`、`:421`）：
  原来 `set -u` 下没设 `TMPDIR` 时套件会在 L3 中途断掉，连 `Result:` 汇总行都拿不到。
  （本批不改三个产物的内容，不 bump 版本号，见 §六。）
· **`tools/panel-render-test` 的兜底更稳**：没有 `HOME` 时回退到 `os.homedir()`（Windows 上很常见），
  不再落到相对路径 `./dsh-termux-kit` 而在别的目录报 `ENOENT`。
· **`tests/selftest.sh` 里读控制台源码的断言也不再写死 `$HOME`**：9 处 `$HOME_DIR/dsh-console/...` 改成
  `$CONSOLE_DIR`（`${DSH_CONSOLE_DIR:-$HOME_DIR/dsh-console}`），和 `tools/` 同一套规矩。
  以前在仓库副本里跑这些自检，它们会去读一个不存在的 `$HOME/dsh-termux-kit` 然后报错 ——
  实测（在副本里设 `DSH_KIT_REPO`）：`ui-controls check`、`i18n-table check`、`i18n-audit --quiet`、
  `check-task-ids` 四个自检全绿，`panel-render-test` 三遍渲染通过。
  仍然按设计去 `$HOME/dsh-console`、`$HOME/droid-bridge` 读 App 真源码（仓库 `apps/` 是只读镜像）。
  （同上：本批不 bump 版本号，见 §六。）
· **收掉上一次发布误带进仓库的 `tools/__pycache__/*.pyc`**：编译检查会在工具旁边留下 Python 编译缓存，
  而发布脚本是遍历文件系统、不读 `.gitignore`，于是把 5 个 `.pyc` 一起提交了（`f08b5b76`）。
  已从仓库删除（`feb4580a`），并让发布脚本跳过 `__pycache__` 目录与 `*.pyc`，避免再犯。
  仓库历史里那次提交仍留着这几个文件 —— 没有改写历史，代价是 clone 时会多几 KB。

### 变更

· **README 中英两份重写**（`README.md` / `README.zh-CN.md`）：叙述结构重排 —— 删掉「包含什么 / 安装 / 使用说明（三处 UI 规范、小组件、控制台 App、页面插件）」，
  换成「为什么写这个 / 跑起来（五步）/ 它能干什么 / 12 个小组件 / 常用命令 / 三处界面的约定 / 已知的坑（9 条）/ 依赖和限制 / 怎么收回控制权 / 文件结构 / 用到的别人的东西」；
  新增「已知的坑」9 条，每条写现象 → 根因 → 绕法。两份都是 204 行、27 个标题，结构一一对应。
· 同步修 `docs/` 与守则的指路：`CONTRIBUTING.md` §四 里指向 README 的三处引用改成新标题（「包含什么」→「文件结构」+「它能干什么」、
  「安装」→「跑起来」、「已知限制」→「依赖和限制」），§四.3 写明 UI 未定稿前允许缺截图；README 的「文件结构」表里 `ui/` 那行补上 `ui/theme.json`。
· 全仓库扫一遍"AI 味"：去掉装饰性的 ⚠️（`CONTRIBUTING.md` §九、`docs/architecture.md`、`docs/operations.md`、`docs/i18n.md`、`docs/架构与数据流.md`），
  英文文档里的 "we / our" 改成 "I"，运维笔记里的"我们"改成"我"；两份 README 的「文件结构」表补上 `CHANGELOG.md` 入口，
  `ui/` 那行补回色板生成链（27 个颜色 → 两份 `Palette.java`、两份 `res/values/dsh_theme.xml`）。
  历史存档（已发版条目、运维笔记的旧条目）里的符号与用词按原样保留，没有回改。
· §四.7 的措辞改成"两份 README 的「文件结构」表里都要有一行指向 CHANGELOG.md"。
· **术语标准化**：新建 `docs/术语表.md`（5 类 25 条），`CONTRIBUTING.md` §十三 定"一个动作一个词"与 A/B/C 三类禁用词；
  文档里 12 处"优雅退出 / 优雅停 / 无缝 / 闭环 / 对齐（黑话义）"改成 软停 / 前台不动 / 逐段收敛 / 一致·一一对应；
  `i18n/zh.json` 中文侧 6 条"优雅退出 / 优雅停止"→"软停"（英文侧保留行业标准词 `graceful`），产物 `widgets/i18n.sh` 重生成；
  两份 README 的「文件结构」表加术语表入口，「一条龙」→「adb 全流程」，「一键撤销」去「一键」。
  （本批是纯文档，不 bump 版本号，见 §六。）
· **README 中英两份术语标准化重写**（`README.md` / `README.zh-CN.md`）：章节标题与措辞统一，技术事实未变 ——
  中文侧「为什么写这个 / 跑起来 / 它能干什么 / 已知的坑 / 依赖和限制 / 怎么收回控制权 / 文件结构 / 用到的别人的东西」
  改成「概览 / 快速开始 / 功能 / 故障排查 / 环境要求与限制 / 安全与权限 / 仓库结构 / 第三方组件」（「许可」不变）；
  英文侧同步改成 Overview / Quick start / Features / Troubleshooting / Requirements and limits /
  Security and permissions / Repository layout / Third-party components（License 不变）。
  「12 个小组件 / 常用命令 / 三处界面的约定」与英文侧三个三级标题保留，现在挂在「功能 / Features」下，两份仍逐节一一对应。
  顺带把 `CONTRIBUTING.md` §四里指向 README 的引用换成新标题（「文件结构」→「仓库结构」、「它能干什么」→「功能」、「跑起来」→「快速开始」、
  「依赖和限制」→「环境要求与限制」、「已知的坑」→「故障排查」），§四.6 的「现象 / 根因 / 绕法」改成「现象 / 原因 / 解决方案」
  ——只换引用，不改规矩、流程与门禁条目。（本批是纯文档，不 bump 版本号，见 §六。）
· **修正文档里的旧数字与旧路径**：`docs/operations.md` §9 的「26 tools」改成不带数字的写法（工具数会漂移，README 这次也已去掉这类数字）、
  「the 9 home-screen widgets」→「the 12 home-screen widgets」、`~/.local/share/dsh-widgets/` 的内容由「common.sh + selftest.sh」
  改成实际安装的「common.sh + i18n.sh」；`docs/i18n.md` 的「the 10 widgets」→「the 12 widgets」。
  数字以 `widgets/` 的 12 个任务脚本与 `tools/install-widgets` 的映射表为准。历史存档（`docs/DSH运维笔记.md` 的旧条目与已发版条目）
  按原样保留，没有回改。（本批是纯文档，不 bump 版本号，见 §六。）

## [v1.12] - 2026-09-28

> 本版三个产物：控制台 **1.21**（versionCode 34）· 桥 **2.24**（versionCode 34）· 页面面板 **0.10.0**。
> 资产名一律 ASCII，SHA256 见 `dist/SHA256SUMS.txt` 与发行版页面的 `SHA256SUMS.txt`。

### 新增

· `CONTRIBUTING.md`：仓库操作守则（记录习惯、push 前 12 条清单、commit/CHANGELOG/版本号规则、敏感信息扫描、本仓库门禁一览）。
· `tools/pre-push-check`：那张清单的脚本化版本（9 条自动判 + 3 条如实标"手动/联网"）。
· `tools/panel-render-test`：面板渲染测试台 —— 用最小 React 垫片真跑三遍渲染（默认折叠 / 全部展开 / 二次确认弹层），
  抓"一点开插件就消失"这类**只有真执行才看得见**的运行时错误；已接进 `tools/i18n-audit` 门禁。
· `tools/i18n-audit` / `tools/app-verify` / `tools/ui-bg-check` / `tools/sync-apps`：文案·主题·色板·状态口径审计、
  打开 APK 核对、底部底色客观测量、App 源码镜像进仓库。
· `ui/theme.json`：两个 App + 面板的**唯一色板源**（生成两份 `Palette.java`、两份 `res/values/dsh_theme.xml`、
  控制台的形状 drawable、面板的 `UI_THEME`）。
· 面板接进 i18n 表：`i18n/zh.json` → `client.js` 的 `generated:i18n` 块（`UI_TEXT`，第四个生成目标）。
· `安全` 与 `设置` 两个分类；通道里的子分类「危险操作 · 只能你手动恢复」（真停桥移入）。
· 控制台拿到认证 URL 后多一行可点的「▶ Open the DSH page / ▶ 打开 DSH 页面」（固定在底部结果条上方，
  没拿到地址就整行隐藏，不留空白占位）。

### 修复

· **版本行把「发行版」说成了「仓库」**（用户 2026-09-28 拿截图质问「你这个推流有问题啊」）：那一行读的是
  GitHub **Releases**（能下载安装的 APK），文案却写「仓库」—— 用户刚看着源码 1.20 推上 GitHub，界面却说
  「仓库只有 v1.14」，一句话里混了两个不同的事实。现在**两个事实各说各的、各有来源**：
  `dsh-update check --json` 除发行版版本（Releases API）外，再报一个 `repo_app`（master 的 `ui/controls.json`，
  只读前 4KB，读不到就留空 —— 界面必须能区分「没有」和「不知道」）；界面按情况说成
  「版本：v1.21 · 发行版还是 v1.14 · 仓库源码 v1.21（源码已推上去，只差发版）」。
  两个 App 的版本行、`dsh-update` 自己的输出、`11_update-apps.sh` 的措辞一并改口（仓库 → 发行版）。
  `tools/i18n-audit` 的「版本行」样例也跟着改成三段式，`" → the repo has "` 这类会把两个概念搅在一起的 key 已删除。

· **`tools/pre-push-check` 的第 7 / 12 条在 commit 之后集体失明**：它们判断「本次改了什么」只看 `git status --porcelain`，而 push 前的常规状态恰恰是「已经 commit、工作区干净」→ 于是同一批改动，commit 前报「改了 App，版本号顶了吗」，commit 后变成「本次没改 App，无需变动」。
  现在「本次改动」＝工作区改动 ∪ **将要推送的 commit** 里改过的文件（`@{u}...HEAD`，无上游时退 `origin/<分支>` → `origin/HEAD`），并加 `core.quotepath=false` 让中文路径也能做前缀匹配。

· **控制台的页面日志说过一句与事实相反的话**（页面其实是打开的）—— 并更正一条被推翻的结论。
  第一版判断"Termux 在后台发 `am start` 一定会被系统静默拦掉"，于是让脚本 `--no-open`、由控制台自己开，
  控制台不在前台时还打印"我没假装已打开"。2026-09-28 08:05 的实测把这条判断推翻了：
  ① **`dsh web` 自己就会开浏览器**（`dsh-web-app/lib/index.js`: `opening the default browser; pass --no-open to disable`），
  页面就是它开的、也确实出现了；
  ② 同一个上午，后台的 Termux 还靠 `am start` 把 WebAPK 窗口拉到前台（用户正是被那一下拽过去的）。
  → **"后台一律发不出 am start" 太绝对，已更正**。现在不猜谁开得成：脚本把
  `DSH_BOOT_OPEN=server|widget|none` 当**证据**回传（启动日志里那句 / 脚本调过 opener / 都没开），
  控制台只念事实 —— 有人开过就说"是它开的"，没人开过才自己开，且只说"已请求系统打开"，不说"已经打开"。
  启动类命令也不再带 `--no-open`：页面在**就绪那一刻**就被打开，而不是等 `restore_channels`（最长 180 秒）之后。
  协议行带 token，进日志前会被剔掉并留一句说明。
· **关闭 DSH 不再把你拽到 WebAPK 界面**：`dsh-close-window` 以前"为了按 Back 先把窗口切到前台"，
  那一下就是跳转的来源。现在：窗口本来就在前台 → 直接按 Back（前台不动）；不在前台 → **什么都不做**，
  返回退出码 3，`2_shutdown-dsh.sh` 照实说「窗口没关（关它就得把你拽走，你说过不要）——
  自己上划掉，或把 Wi-Fi + 无线调试打开，有 adb 时是无跳转的 `force-stop`」。想要旧行为可显式 `--allow-jump`。
· **`11_update-apps` 会把更旧的 APK 盖到本机更新的版本上**（日志里真发生过：桥 v2.20 装了 v2.21 的机器）：
  脚本原来只会"下载仓库最新发行版 → 装"，**从不比较两边版本**。现在先定版本再下载 ——
  仓库更新才装 / 一样新就跳过 / 本机领先**不降级** / 版本读不到就跳过并说明（要装得显式 `--force`）；
  控制台会把自己的版本号（`--console-ver`）传给脚本，不再靠"版本读不到就照装"。
· **控制台「语言」只有标题、没有选项**：`choices` 容器把三个按钮塞进去之后**从没 addView 到布局**，
  界面上就只剩标题和副标题（用户 2026-09-28 截图报的）。现在选项单独一行显示；
  `tools/i18n-audit` 新增「建好却没挂上去的 View」检查（对旧版本能复现抓到 `choices`）。
· **面板一点开就消失**：`T()` 写在模块作用域却读组件里的 `lang` → `ReferenceError` → React 卸掉整棵子树。
  现在每个用到它的组件各定义一份（5 处），并加了渲染测试台复现/回归。
· **i18n 漏 key**：桥的分类标题拿"▸ + 英文名"整串查表（永远查不到）→ 改成先翻译再加前缀；
  控制台 `groupEn()` 返回分类 id 导致「通道」里出现 3 次英文 `channels`；`auto` 打头的语言副标题、`up to date`、
  `Wi-Fi off, or Wireless debugging not on` 等一批未翻译串。
· **主题不一致**：桥的 `<application>` 从没声明 `android:theme` → 走系统默认浅色（白底 + 浅色 ActionBar + 标题重复）。
· **状态自相矛盾**：绿灯 + 「桥：未知」（灯按 port/ok 算、文字按 state 算，而 producer 不写 state）；
  DSH 灯按 `ok` 算、文字按 `port` 算（404 窗口期"黄灯 + 运行中"）；桥 App 的 `isListening()` 一次渲染里算了两遍。
· **版本行谎报「已是最新」**：本地 1.16 > 仓库 1.14 时照旧显示"已是最新（仓库最新 v1.14）"。
· **分类条目数对不上**：面板用 `UI_CONTROLS.filter(cat).length` 另算一套（通道报 8 项、展开只有 7 条）。
· **密码使用权副标题**带上文件路径 / `mode 600` / 写入时间 / 位数。
· **滚到底露出一块灰**：窗口/状态栏/导航栏/边缘辉光/对话框都不属于任何 View，走的是框架灰；
  现在全部钉在色板的 BG 上。
· **⚠ 没有红色提示**：去掉红边框后 ⚠ 是危险项唯一的视觉信号，却跟正文同色 → 三处都画成红色。
· **版本号一个号盖多份内容**：六批改动都塞在 1.16 / 2.22 里；现在 `app-verify` 会拒绝"同版本不同内容"。
· `droid-sock` 的坐标参数用 `int()` 解析：调用方（算出来的坐标）传 `600.0` 这种小数就 ValueError
  崩在 stderr、stdout 为空 —— 调用方会把"工具崩了"误判成"手势失败"（2026-09-28 画爱心时真踩）。
  现在 `iv()` 容错，`600` 与 `600.0` 都能用。
· 5 个工具的 shebang 是 `#!/usr/bin/env python3`，而 Termux 没有 `/usr/bin/env` → 直接跑会 bad interpreter。
· 桥「关于本应用」的折叠状态写回时会冲掉其它分类的记忆（单独造了一个集合再写共享 prefs）。

### 变更

· 分类从五个扩到七个（＋桥的「关于本应用」）：启动·停止 / 通道 / 维护 / **安全** / **设置** / 紧急 / 状态·日志。
· 危险分三级、靠形状区分：紧急＝实心红底白字；**只有「关闭 DSH」**保留红边框；其余危险项＝普通样式 + 红色 ⚠。
· 分类标题 13sp → 14.5sp，分类之间加分割线；最近一次结果条**固定在窗口底部**（不随分类滚动）。
· 列表按钮显式左对齐；token 打码带长度（`tok·······（长度 12）`）。
· 面板语言切换改走 `dsh-lang set`（原来自己写 `~/.dsh-lang`，桥不会跟着变）；
  桥自己的语言行写明"只改本 App"。
· 版本号：控制台 **1.18** / 桥 **2.23** / 面板 **0.10**（各自独立，一次交付一个号）。

### 移除

· 面板里 45 处散落的 `lang === 'zh' ? '中文' : 'English'` 字面量（进 i18n 表）；
  面板的第三套颜色（`#e5484d` / `#4c8dff` / `#2ecc71` / `#e3b341`）改用统一色板；
  已没人调用的 `lampOf()` dsh/bridge 分支、`section()`、`countOf()`。

---

## 2026-09-27 — P1（控制台 1.16 / 桥 2.22 同版追加）：分类归位、真停桥独立子分类、布局与危险分级

> P0 那一版**没有装到手机上**，所以 P1 直接编进同一个版本号（1.16 / 2.22）：装一次就是全部。

**分类与位置（5–7）**
- 新增两个分类：**安全**（密码使用权，从「维护」移出——它跟备份/清理并列语义完全不对）与
  **设置**（语言、项目主页、版本，从「维护」移出——语言是全局设置，不属于维护）。
  `ui/controls.json` 的分类顺序：启动·停止 / 通道 / 维护 / 安全 / 设置 / 紧急 / 状态·日志（＋桥的「关于本应用」）。
- 「真停桥」不再是开关旁边的一个按钮：移进通道里的子分类 **危险操作 · 只能你手动恢复**（琥珀色小标题）。
  为什么不做成"长按开关出三态菜单"：全项目的长按＝看说明，让长按去执行一个单向门动作容易误触。
- 桥的 `layoutAll` 改成**按 `UiControls.CATS` 跑**（以前手写顺序，于是"语言/项目主页/版本"被塞在「维护」里，
  跟控制台对不上）；桥首次打开只展开「通道」，其余（含关于本应用）默认收起。

**布局与视觉（8–13）**
- 分类之间加分割线，标题上边距 14→11dp，收起后不再是一片空白。
- 分类标题 13sp → **14.5sp**（保持加粗），收起状态下也能一眼看到。
- 危险动作改三级、靠**形状**分：紧急＝实心红底白字；**只有「关闭 DSH」**保留白底红字+红边框
  （`ui/controls.json` 新字段 `redBorder`，生成器带进两个 App）；软/硬重启等回到普通样式 + ⚠。
  三个红边框并排反而谁都不像危险动作（用户 2026-09-27）。
- 桥的列表按钮显式左对齐（新增 `listBtn()`，不再依赖深色 pass 顺手设置的 gravity）。
- 桥的浅色 / 顶部标题重复两条随 P0 的主题统一一并消失。

**状态条（14）**
- 最近一次结果那条从根布局末尾**挪到窗口底部固定**（ScrollView 之外）：它本来是全局的，却跟着分类滚，
  看着像"每展开一类就出现一次"，滚到别处还看不到点下去的结果。没有结果时整条仍然不显示。

**P2**
- token 打码带上长度：`token：tok·······（长度 12）`（以前固定 6 个点，看不出长度；真正的值仍然只有点「复制 token」才进剪贴板）。
- 「关于本应用」默认收起 —— 而且**顺带修了一个真 bug**：桥以前给 about 单独造了一个折叠集合再写回
  SharedPreferences，于是"点一下关于本应用"会把其它分类的记忆一起冲掉；现在所有分类共用同一个集合。
- 折叠记忆：两个 App 都把 `collapsed` 存在各自 SharedPreferences 里（本就在，装机后即可确认）。
  首次运行的默认折叠改成**按 `UiControls.CATS` 推导**（控制台只展开「启动 / 停止」，桥只展开「通道」），
  不再手写分类名 —— 手写那份在新增「安全 / 设置」时会漏掉，新分类会默认展开。

**顺手修的（不在清单里，但会咬人）**
- 5 个工具（`check-task-ids` / `i18n-table` / `i18n-java-fix` / `verify-i18n-patch` / `dsh_common.py`）
  的 shebang 是 `#!/usr/bin/env python3`，而 Termux **没有** `/usr/bin/env` → README 里写的
  `tools/i18n-table gen` 直接跑会 "bad interpreter"。已统一成 Termux 的 python3 绝对路径。
- 面板 `client.js` 重新部署（v0.8，八个分类与数据源一致）+ `pnpm install` + `dsh-relink-bundles --check`
  通过；刷一下页面即可看到新分类。

## 2026-09-27 — 修"一点开插件就消失"（面板 0.10）

**症状**：点右下角 ☰ → 面板刚出来就整块没了（插件从页面上消失）。

**根因**（我上一批引入的）：把面板文案搬进 i18n 表时，helper 写在了**模块作用域**：
```js
const T = (en) => (lang === 'zh' ? (UI_TEXT[en] || en) : en);   // ← lang 是组件里的变量
```
`node --check` 语法全过，静态审计也全过（因为 `lang` 这个**名字**在文件里确实存在，只是在另一个作用域）。
于是插件照常加载、☰ 也在，**一点开** TaskSheet 调 `T()` → `ReferenceError: lang is not defined` →
React 把整棵子树卸掉。用户看到的就是"点一下它就消失了"。

**修法**：`UI_TEXT`（纯数据）留在模块作用域，`T` 改成**每个用到它的组件各定义一份**（那里才有 `lang`）：
Control / SwitchRow / LangRow / HintDialog / TaskSheet 五处，11 个调用点全部落在有定义的函数里（已逐点核对）。

**新增 `tools/panel-render-test`（渲染测试台）——这类错误以后过不了门禁**
用最小 React 垫片把组件**真的调用起来**（createElement/useState/useEffect/useCallback/useMemo/useRef
+ document/window/fetch/localStorage 垫片），跑三遍：
① 默认折叠态；② 全部展开（清空"默认折叠"、把 false/null/'' 的 state 顶成能进分支的值）→ 覆盖
Control/SwitchRow/LangRow/Lamp/TaskSheet；③ 二次确认弹层（armed/hintId 指向真实控件 id）→ 覆盖 HintDialog。
任何一遍抛错就非零退出。当前：三遍共渲染 66 个组件（Fab×2 · TaskSheet×2 · Control×34 · SwitchRow×4 ·
LangRow×2 · Lamp×18 · HintDialog×4）✅；拿**上一版（有 bug 的那个提交）**跑同一个测试台会当场失败：
`ReferenceError: lang is not defined at T (...:74) at TaskSheet (...:573)` —— 回归验证过。

已接进 `tools/i18n-audit`（每次出包/自检都会跑），面板这种"点开就崩"不会再悄悄发出去。

## 2026-09-27 — 版本号与 ⚠ 提示（面板 0.9 / 控制台 1.17 / 桥 2.23）

用户指出两件事，都成立：

**① 版本号一直没动** —— P0/P1/P2/背景/计数/单一数据源 六批改动全塞在 1.16 / 2.22 里，dist/ 里出现过多份
同版本、不同字节的包，谁也不知道手机装的是哪一份。现在**一次交付一个版本号**：控制台 1.17（code 30）、
桥 2.23（code 33）、面板 0.9。并且加了门禁：`app-verify` 会检查 dist/ 里有没有**同版本但内容不同**的包，
有就直接报错（"顶一个版本号再出包"），不会再出现这种情况。

**② ⚠ 没有红色提示** —— P1-10 把红边框只留给「关闭 DSH」之后，⚠ 成了其余危险项**唯一**的视觉信号，
而它当时跟正文同一个颜色，等于没有提示。现在三处都把 ⚠ 本身画成红色：
- 控制台：控件按钮副标题的 ⚠、长按详情标题的 ⚠、文本行的 ⚠（用 `warnMark()`，取色板的 BAD #F85149 ——
  比 DANGER #B3261E 在深色底上清楚得多）；
- 桥：控件说明行的 ⚠；
- 面板：`.mb-warn`，同样取色板。

**顺带统一了面板的色板**：面板原来有自己的第三套颜色（危险红 #e5484d、强调蓝 #4c8dff、绿 #2ecc71、
黄 #e3b341、灰 #8b949e），跟两个 App（#B3261E / #58A6FF / #3FB950 / #D29922 / #8B949E）不是一个产品。
现在 `ui/theme.json` 生成 `client.js` 的 `generated:theme` 块（`UI_THEME`），面板 CSS 全部改成插值取色，
裸色值检查也扩到了面板（生成块之外不许再出现任何 `#RRGGBB`）。

## 2026-09-27 — 通用约束"单一数据源"落地（面板 0.8 / 控制台 1.16）

用户把这条升成**通用约束**（任何 UI 上的数字、状态、文案只能有一个来源）。逐条结果：

| 约束 | 做法 | 门禁 |
|---|---|---|
| 分类条目数：从实际渲染列表 count | 控制台边渲染边计数、桥按类型认（GroupLabel/HintLine 不算）、面板 `rowsOf(body)` | `audit_counts` + 禁止 `UI_CONTROLS.filter(...).length` |
| 状态灯色：从状态数据源直接取 | 三处（python/java/js）**同一张** state→灯色表；控制台不再读 `lamps.*`，adb 灯与文字同读 `devices` | `audit_state_maps` 三处逐项对拍 |
| 版本对比：两半同一次查询 | 控制台本地版本改取 `dsh-update check` 的**回显** `current`（以前一半用界面字段、一半用返回） | `app-verify` 核对；三态逻辑 |
| 开关状态：取实际查询结果 | 桥运行中 ← `bridge.state`；密码使用权 ← 每次进前台跑 `dsh-auth-pass status`；闲置软停 ← 服务与界面读同一份 prefs | 见上（开关与文字同源检查） |
| 语言文案：从 i18n 表取 | **面板也进表了**：45 处散落的 `lang === 'zh' ? '中文' : 'x'` 全部改成 `T('English')`，新增第四个生成目标（`i18n/zh.json` → `client.js` 的 `generated:i18n` 块） | `audit_panel_i18n`（面板里除生成块外不许出现中文）+ `node` 求值检查 |

**顺手纠正的漂移**：抽取面板文案时，`add` 顺手覆盖了 4 条共享表里已有的用词（`Running: ` / `Confirm` /
`(no output this time)` / `stopped`）。共享表**以已有说法为准**，面板改用共享说法（"正在执行："而不是
"正在跑："），已回滚覆盖 —— 同一个产品不能因为界面不同就换一套词。

面板改动只需 `pnpm install` + 刷新页面；控制台因为改了版本行取值，编了新的 v1.16 包。

## 2026-09-27 — 面板插件的"两套数据"排查（面板 0.8 / 桥 2.22）

用户把第 20–22 条明确落到**面板插件**上。逐项结果：

**① 分类 (N)（第 20 条）** —— 上一轮已修：面板原来用 `UI_CONTROLS.filter(c => c.cat === id).length`
另算一遍（不按 surfaces 过滤、3 条灯算 3 条、`bridge_state_text` 也没算吸收），通道报 8 项实际 7 条。
现在数的是**真正要渲染的 body**（`rowsOf(body)`），并删掉没人调用的 `section()`。

**② 三个灯（第 21 条）** —— 查出**两处不同源**：
- DSH：`lampOf('dsh')` 按 `port` + `http` 算色，文字只按 `port` → 404 窗口期"黄灯 + 运行中"。现在灯色与文字
  都读 `dsh.state`（`DSH_LAMP` 表）。
- 桥：灯色读 `b.ok` / `b.port`，文字读 `b.state` → 现在都读 `state`（`LAMP_OF_STATE` 表，5 态文字照旧）。
- adb：本来就同源（`devices`）✓；顺手把已经没人调用的 `lampOf()` 里 dsh/bridge 两个分支删掉（改名
  `adbLamp`）—— 留着就是第二个真相。

**③ 「桥运行中」开关（第 21 条）** —— 开关读 `s.bridge.ok`、下面那行文字读 `brState`（state）→ **两套数据**。
现在开关位置、圆点颜色、状态文字三者都读 `brStateKey`。另外桥 App 里 `isListening()` 原来在同一个
`updateState()` 里算了两次（端口行一次、开关与状态文字一次），服务状态一变就会出现"端口：正在监听"
配"开关：关"—— 现在只算一次，三处共用（审计会盯住：只允许出现一次）。

**④ 语言开关（第 21 条）** —— 扫描全文件找"不跟语言走的中文"，查出一处：版本行
`'面板 v0.8 · 控制台 v1.16 · 桥 v2.22'` 里三个名字写死中文，英文界面下照样显示中文 → 改成 `t({zh,en})`。
其余中文都在 `lang === 'zh' ?` 或 `t({zh,en})` 分支里 ✓。

**⑤ 版本号 /「是否最新」（第 21 条）** —— 面板**不做**更新检查（它没有后端查询），只显示三个版本号，
来源是生成块里的 `UI_VERSION`（一个源）；"是否最新"只存在于两个 App，各自一次查询。所以面板这一项
不存在"两个数据源"，也不会谎报"已是最新"。

**门禁（都是这次新加的）**
- `i18n-audit` 新增 `audit_state_maps()`：把**三处**（dsh-status-pub 的 python、控制台的 java、面板的 js）
  的"state→灯色"表**逐项对拍**；并禁止面板再出现 `lampOf('bridge'|'dsh')` / `.bridge.ok ?` 这类另算；
  桥 App 的 `isListening()` 只允许出现一次（注释不算）。
- `i18n-audit` 新增 `audit_panel_i18n()`：扫描面板里**所有中文**，必须落在 `lang === 'zh'` 或
  `t({zh, en})` 里 —— "写死中文、英文界面不跟着变"会当场报错。

面板改动只需 `pnpm install` + 刷新页面（已同步进 profile，`dsh-relink-bundles --check` 通过）；
桥 App 因为改了 `updateState()`，编了新的 v2.22 包。

## 2026-09-27 — 计数与"两套数据"排查（控制台 1.16 / 桥 2.22 同版追加）

**分类标题上的 (N) 对不上（第 20 条）**
- 真凶在**页面面板**：`countOf(catId) = UI_CONTROLS.filter(c => c.cat === catId).length` —— 这是**第二套数据**：
  不按 `surfaces` 过滤（面板不显示的「项目主页」也被算进去）、把状态/日志里 3 条灯算成 3 条（实际只画
  1 行灯 + 2 个按钮）、也不管 `bridge_state_text` 被开关行吸收。于是通道写 8 项、展开只有 7 条。
  现在面板数的是**真正要渲染的那个 body**（`rowsOf(body)`，分组小标题不算），并删掉了没人调用的
  `section()`（留着就是第二个真相）。
- 控制台与桥**也加上同一个数字**，口径统一为「一个控件 = 一条」：控制台在渲染循环里边加边计数
  （灯那一整块算 1 条、分组小标题不算）；桥用类型区分（`GroupLabel` 小标题、`HintLine` 说明行都不算条目，
  说明行跟着它上面的控件走）。两处的数字都**只来自实际渲染**，展开/折叠都显示，方便对着数。
- 新门禁 `i18n-audit`：① 拦住"按控件表另算一遍"的写法（`UI_CONTROLS.filter(...).length`）；
  ② 打印三个界面每个分类的条目数（console 通道 8 / bridge 通道 3 / panel 通道 7 —— 差异是各界面
  真正显示的东西不同，不是漂移）。

**同类隐患（第 21 条）**
- **DSH 那盏灯**：灯色按 `ok`（端口 + HTTP 码）算，文字却按 `port` 算 —— 端口在听但页面还没挂路由
  （404）时会出现"黄灯 + 运行中"。现在生产者写 `dsh.state`（running/half/down），灯色由 state 推、
  文字读同一个 state；`i18n-audit` 把 python 与 java 两张表**逐项对拍**（和桥一样）。
- **adb 那盏灯**：控制台原来灯色读 `lamps.adb`、文字读 `devices`；现在两者都读 `devices`（一个事实）。
- **桥的状态文字**：服务根本没起来时以前也写「刚断（可唤醒）」—— 那是句不准的话；现在三种情况分开说
  （运行中 / 刚断可唤醒 / 未运行，并告诉你去哪儿把它叫起来），开关位置仍读同一个 `listening`。
- **版本行**：复核确认是同一次 `dsh-update check --json` 的返回 + 本地版本号，没有第二个数据源。
- **语言切换**：查出一条真的不同步 —— 面板以前**自己写** `~/.dsh-lang`（小组件、控制台会跟上，但**桥不会**，
  因为桥没有 RUN_COMMAND，只能被推）。现在面板改走 tasksd 的新虚拟任务 `lang_auto|lang_zh|lang_en`
  → `dsh-lang set` → 写文件 **+ 把值推给桥**。桥自己的那一行加了一句实话：
  「这一行只改本 App；三处一起变请用控制台里的「语言」」。

## 2026-09-27 — 背景一致性（控制台 1.16 / 桥 2.22 同版追加）

用户报：**控制台滚到底露出一大块灰褐色**，桥底部露灰；并点名「同类隐患」（折叠过渡区、对话框圆角外露、
日志面板底色）。根因不是某个 View 忘了刷色，而是**那几层根本不属于任何 View**：

- 平台主题的 `windowBackground` 是框架自己的灰（#303030 那一类）—— 内容比屏幕短、或过度滚动时露出的就是它；
- 状态栏 / 导航栏 / 滚动到头的边缘辉光同理，都走框架默认值；
- 对话框底色是 Material 的 #424242 灰，跟页面卡片色不是一个。

**做法：把这些层也纳入唯一色板源。**
- `ui/theme.json` 新增 `androidTheme / androidThemeParent / androidDialogTheme / androidDialogParent`，
  生成器据此产出 **`res/values/dsh_theme.xml`**（两个 App 逐字节相同）：`windowBackground`、`colorBackground`、
  `statusBarColor`、`navigationBarColor`、`colorEdgeEffect` 全部钉在 `BG(#0D1117)`；`colorAccent` /
  `colorControlActivated` 钉在强调蓝（开关与进度条不再是平台青色）；`alertDialogTheme` 指向 `DshDialog`
  （底色＝卡片色 #161B22）。**不动** dialog 的 `windowBackground` —— 圆角与内边距仍由平台那份背景提供。
- 两份 manifest 改用 `@style/DshTheme`（不再是平台主题）；控制台的 ScrollView 也显式刷底色 + `fillViewport`：
  **窗口 → page → ScrollView → root 四层同色**，怎么滚都不会断层。
- 控件形状的颜色（`box/btn/btn_danger` 里 10 个手写色值）也搬进 `ui/theme.json` 生成，值不变，
  但不再有「第二套卡片色」（box 曾是 #0F141A，而 Palette.CARD 是 #161B22）。
- 桌面小组件布局 `res/layout/widget.xml` 的 6 处手写色值改用 `@color/dsh_*`。

**门禁（本次新加）**
- `i18n-audit`：核对两份 `dsh_theme.xml` 逐字节相同、六个关键项都在、`dsh_bg` 等于 `ui/theme.json` 的 BG；
  裸色值检查扩展到 **res/ 下的 XML**（只有生成的主题/形状文件与图标 artwork 允许带色值）。
- `app-verify`：从 APK 里解出 `style/DshTheme`，核对 manifest 指向的就是它、parent 正确、`color/dsh_bg`
  的实际值、以及**两个 App 的主题逐项相同**（比 item 文本而不比资源 id —— 主题现在是各 App 自己的资源，
  id 天然不同，比 id 会得出假结论）。
- 新工具 `tools/ui-bg-check`：截图 → 沿屏幕中轴取一列像素 → 报出「页底色 / 屏幕最底一行 / 是否一致」，
  把「看着有块灰」变成可复跑的数字。

**顺手**：`Palette` 支持 8 位 ARGB（小组件的半透明底 #F21B1F27）—— 生成器第一版拼出
`0xFFF21B1F27` 直接编译失败，已修。

## 2026-09-27 — P0 返工（面板 0.8 / 控制台 1.16 / 桥 2.22）

**为什么会有一版"返工"**（根因写清楚，免得再犯）
- 上一轮改完文案**没有重新出包**：`tools/ui-controls`、`i18n/zh.json` 与两份 `Lang.java` 是 15:48
  才重写的，而 APK 是 15:38 编的 → 手机上跑的是旧翻译表（`channels` ×3、`auto 跟随系统语言`、
  `up to date`、`Wi-Fi off, or Wireless debugging not on` 全是英文）。新工具 `tools/app-verify`
  专治这件事：它打开 APK 核对"当前翻译表的每一条中文是否真的在包里"，旧包当场不通过。
- 桥的浅色是**真没做**：桥的 `<application>` 从来没声明 `android:theme`，走系统默认浅色主题；
  代码里的深色 pass 只重绘了它够得到的 View，窗口/滚动区仍是白的，标题还被 keepColor 保成黑字。
- `channels` ×3 还有第二层原因：生成器 `groupEn()` 返回的是**分类 id**（id 没进翻译表）。

**P0-1 i18n**
- 桥：分类标题改成"**先翻译，再加 ▸/▾ 前缀**"——原来是拿 `"▸ Channels (adb and bridge separate)"`
  整串去查表，永远查不到，所以三个分类标题全英文而周围都是中文。
- `UiControls.groupEn/groupZh` 修好；`bridge_full_stop` 移回桥分组，通道里不再重复出现分组头。
- 新工具 `tools/i18n-audit`（两个 build.sh 编译前强制跑，不过不许出包）：
  ① `Lang.t()` 的参数必须是字面量 —— "拼好的串再查表"一律报错；
  ② 每一处字面量都要有非空中文；③ 控件表/分类/分组与翻译表逐条一致；
  ④ 两份 AndroidManifest 的主题必须相同且等于 ui/theme.json；⑤ 两份 Palette.java 必须一致、源码不许有裸色值；
  ⑥ 桥的 state→灯色 在 python 与 java 两侧必须是同一张表。
- 顺带清掉"先传英文 key、再在被调函数里查表"的写法（BridgeService / PanicReceiver / MainActivity 的 stop/panic 原因）。

**P0-2 主题统一**
- 新增 `ui/theme.json` 作**唯一色板源**，生成两份 `Palette.java`（各 16 色）；两个 App 的
  MainActivity / DshWidget 里不再有一处裸色值。
- 桥的 manifest 补上 `android:theme="@android:style/Theme.Material.NoActionBar"`（与控制台同一个
  资源 id 0x0103022e，app-verify 核对两边一致）；ScrollView 刷成页底色 + fillViewport，内容短时
  屏幕底部不再留白；标题显式用 FG，不再被 keepColor 保成黑字。

**P0-3 状态同一数据源**
- `tools/dsh-status-pub` 新增 `bridge.state`（running / frozen / soft / silent / never）与 `bridge.note`，
  `lamps.bridge` 由 state 推出；控制台里**灯色、那行文字、开关位置**全部读 `bridgeStateKey()` 一个判定。
  （"绿灯 + 桥：未知"的根因：producer 不写 state，灯色按 port/ok 算、文字按 state 算，两处各算各的。）
- 版本行三态：相等 →「已是最新」；本地落后 →「→ 仓库最新 vX · 点这一行更新」（点了就跑 `11_update-apps`）；
  本地领先 →「本地比仓库新（仓库只有 vX）」，**不再谎报已是最新**。桥 App 同样三态。

**P0-4 密码使用权副标题**
- 副标题只留「当前：已授权（AI 可代你过系统身份验证）」/「当前：已收回」/「当前：读不到…」；
  文件路径、`mode 600`、写入时间、位数全部移出 —— 长按这一行看完整技术细节，只有状态**发生变化**时才记一条日志。

**验证**：`i18n-audit` ✅ · `ui-controls check` ✅ · `i18n-table check` ✅ ·
`app-verify console|bridge` ✅（1.16 / 2.22，主题同源，191 / 107 条中文全部在 dex 里）；
对旧包（15:38 的 1.15 / 2.21）跑 app-verify **不通过** —— 那正是手机当时装的那一份。

## 2026-09-27 — UI 统一（面板 0.7 / 控制台 1.15 / 桥 2.21）

**共性**
- 清 i18n 遗留 key：控制台里裸 id 直接进 `Lang.t()` 的那处（界面会显示 `bridge_wake`）
- 统一主题：控制台与桥改用同一套深色色板（`#0D1117` 底 / `#E6EDF3` 字 / `#8B949E` 次要 / `#58A6FF` 强调）
- 危险分两级：**紧急 = 实心红底白字**；其余危险 = **白底红字 + 红边框**（真停桥归「通道-桥」）
- 分类可折叠，默认只展开「启动/停止」，记住上次状态（App 存 SharedPreferences，面板存 localStorage）
- 面板：按 `surfaces` 过滤（桥专属控件不再漏进面板）；语言改三选一；修崩溃（补回被误删的 `g()`/`brState()`）

**控制台**
- 顶部状态行重做：去掉时间戳与 `HTTP 401`，改成「DSH 运行中 · 桥 v2.21 · adb 未连接（Wi-Fi 未连或无线调试未开）」
- adb 状态带原因；桥运行开关显示四态（运行中/刚断（可唤醒）/长时间未响应/未安装）
- 密码使用权移出「紧急」→「维护」，副标题只留人话，技术细节移入长按
- 删除底部「命令经 Termux 执行…」自述

**桥**
- 按钮标题瘦身（去掉 ①②③ 圈数字）、全部左对齐
- 语言选择器：未选灰底、选中蓝框 + ✓（不再是三块纯色）
- token 默认打码（`tok••••••`），点「复制 token」才取，复制后 Toast
- 「关于本应用」默认折叠
- 版本行：「版本 2.21 · 已是最新」/「版本 2.21 → 仓库最新 2.22」
- 项目地址改成链接样式（蓝色 + 下划线）

**验证**：`check-task-ids` ✅ · `ui-controls check` ✅ · 面板渲染测试台 5/5 ✅（逐个点开折叠头无异常、无控件泄漏）
