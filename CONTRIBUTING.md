# 仓库操作守则 + 运维笔记习惯

> **动手前先读一遍这份文件。** 它管的是"怎么改这个仓库"，不是"这个仓库是什么"（那在 [README.zh-CN.md](README.zh-CN.md)）。
> 每次改动、每次跑命令、每次推仓库，都按这里写的做。

---

## 一、核心原则

1. **文档和代码同步**：改了功能就改文档，不许"下次一起改"。
2. **记录先于推送**：没写运维笔记、没更新 CHANGELOG 的改动，不许 push。
3. **可回退**：每次 push 前确保远端 commit 是干净的、可 `git revert` 的。
4. **单一数据源**：所有 UI 文案 / 分类 / 控件 id 从 `ui/controls.json` 生成，颜色与主题从 `ui/theme.json` 生成，
   翻译从 `i18n/zh.json` 生成 —— 不许在各处硬编码（这三个生成链都有门禁，见 [§十一](#十一本仓库的门禁现成的别重造)）。

---

## 二、每次开发 / 执行命令必须做的记录（最重要）

**每次改代码、跑诊断、修问题、动配置，都要在运维笔记里追加一条。**

格式：

```
YYYY-MM-DD HH:MM · <一句话标题>

做了什么：<动作>
为什么：<触发原因>
结果：<成功/失败 + 关键证据>
下一步：<如果没完，写清卡在哪>
```

**判定标准**：一件事只要动了文件、跑了命令、改了配置，就要有记录。
哪怕最后失败回滚了，也要记"试过 X，没用，回滚"。

**位置**（两份都要写，保持一字不差）：
- 仓库内：`docs/DSH运维笔记.md`
- 本地：`~/DSH运维笔记.md`
- 每次 push 时把本地新增段落合并进仓库版本

**副本**（**只在 push 时投递，平时改笔记不投递**）：push 时跑 `~/.local/bin/dsh-out ~/DSH运维笔记.md`，
覆盖交付目录里的同名文件 `Download/dsh/文档/DSH运维笔记.md` —— **不加日期后缀**。
`Download/dsh/文档/` 只放最新版；历史版本在 git 和备份里，不在那里堆。

**结构**：分类索引 + **最新在上**。新记录写在开头的「〇、最近改动记录」小节里，老的分节（一、二、三…）保留原样。

**踩过的坑必须写"根因"和"怎么避免"**，不要只写"试了 X 失败了"。
（例子见 `docs/DSH运维笔记.md` 里那条"一点开插件就消失"：根因是 helper 写在模块作用域却读组件变量，
避免办法是加了 `tools/panel-render-test` 渲染测试台并接进门禁。）

---

## 三、推仓库前的强制自检清单（每次 push 前逐条勾）

```
[ ] 1. git status 干净，没有未跟踪的临时文件
[ ] 2. 敏感信息扫描通过（见第七节）
[ ] 3. README / 项目介绍已同步本次改动
[ ] 4. CHANGELOG.md 已加新条目
[ ] 5. docs/ 里的相关文档已同步
[ ] 6. 运维笔记已追加本次记录（两份）
[ ] 7. 版本号已按规则 bump（见第六节）
[ ] 8. commit message 符合格式（见第五节）
[ ] 9. .gitignore 已覆盖本次产生的临时文件
[ ] 10. git pull --rebase 无冲突
[ ] 11. 自检套件全绿
[ ] 12. 面板插件改动只需 pnpm install + 刷新；APK 改动要重新编译
```

**任何一条打不了勾，停下，问用户。**

前 11 条里有 9 条已经脚本化，直接跑：

```bash
tools/pre-push-check          # 逐条打勾；标注哪些是"需要你手动/联网"的
tools/pre-push-check --strict # 把 ⚠（提醒类）也当失败，用于真的要推之前
```

---

## 四、README / 项目介绍的持续更新

README 是长期维护的唯一对外入口，**每次功能变化都要检查这几块**：

1. **一句话项目介绍**：是否还准确？功能范围变了要改。
2. **功能清单**：新增/删除的功能要同步（本仓库＝[README.zh-CN.md](README.zh-CN.md) 的「仓库结构」表 +「功能」一节）。
3. **截图**：UI 改了要换新图（旧图比没图更糟）。**本仓库目前没有截图，UI 未定稿前允许缺图** —— 这一条要求的是"改了 UI 就得换图"，不是"必须有图"；现在如实标"暂无截图"即可，别为了打勾去放半成品。
4. **快速开始**：安装步骤、依赖、首次运行命令（`README.zh-CN.md` 的「快速开始」一节）。
5. **目录结构**：新增顶层目录要补说明（`apps/ widgets/ tools/ plugins/ ui/ i18n/ tests/ docs/ dist/`）。
6. **FAQ / 已知问题**：踩过的坑、系统限制（vivo 后台冻结、无障碍被回收、无线调试依赖 Wi-Fi…）要写进来，
   别让后来者重复踩 —— 现在放在「环境要求与限制」一节；**「故障排查」是另一节**（逐条写现象 / 原因 / 解决方案），两节别混着改。
7. **更新日志入口**：两份 README 的「仓库结构」表里都要有一行指向 [CHANGELOG.md](CHANGELOG.md)（光在正文里提一句不算）。
8. **许可证与致谢**：引用了别人代码要注明（本仓库 MIT，见 [LICENSE](LICENSE)）。

**判断标准**：一个陌生人只看 README 能不能跑起来。跑不起来就是 README 没写够。
**不是"想起来才更新"——每次 push 前对照上面 8 条逐项过一遍。**

---

## 五、commit message 规范

```
<类型>: <一句话总结>

<可选正文：分点列出具体改动>

<可选 footer：关联 issue / 破坏性变更>
```

**类型**：`feat` 新功能 · `fix` 修 bug · `ui` 纯 UI 改动 · `docs` 只改文档 · `chore` 构建/依赖/杂项 ·
`refactor` 重构不改行为 · `perf` 性能 · `test` 测试 · `ci` 只改 CI / 工作流

示例：

```
ui: 统一三处面板分类与说明，修复 i18n 漏 key

· 清 i18n 遗留 key（channels ×3 / auto / Wi-Fi off…）
· 控制台与桥统一为主题（默认深色，同一份 ui/theme.json）
· 分类标题旁条目数改为动态计算，不再写死
· 密码使用权副标题删除技术细节
```

**禁忌**：`update`、`fix bug`、`改了一下` 这类无信息 commit。
`tools/pre-push-check` 会检查最近一条 commit 的类型前缀。

---

## 六、CHANGELOG + 版本号规则

### CHANGELOG

每次 push 前在 `CHANGELOG.md` 顶部追加（未发布的先放 `## [Unreleased]`）：

```
## [x.y.z] - YYYY-MM-DD

新增
· …

修复
· …

变更
· …

移除
· …
```

格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，用中文写。
> 2026-09-27 之前的条目是"日期 + 散文"的旧格式，作为历史保留，不再改写；**新条目一律按上面这个格式**。

### 版本号

- 三处（**控制台 / 桥 / 面板**）各自独立版本号，不要强行同步。
- 语义化 `x.y.z`：`x` 破坏性改动或大重构 · `y` 新功能 · `z` 修 bug / UI 微调。
- **每次 push 都至少 bump 一位 `z`**，除非是纯文档 commit。
- 版本号出现在三处并必须一致：
  1. `ui/controls.json` 的 `appVersions`（面板的 `UI_VERSION` 也从这里生成）；
  2. `apps/console/AndroidManifest.xml` / `apps/bridge/AndroidManifest.xml` 的 `versionName`（`versionCode` 同步 +1）；
  3. CHANGELOG 的标题。
- **一个版本号只能对应一份内容**：`tools/app-verify` 会检查 `dist/` 里有没有"同版本但内容不同"的包，
  有就报错。改了东西没顶版本号 = 别人不知道手机上装的是哪一份（2026-09-27 真踩过）。

---

## 七、敏感信息扫描（push 前必做）

```bash
tools/check-no-secrets.sh        # 本机真实凭据是否泄进仓库（桥 token / 面板 token / 6 位密码）
tools/pre-push-check             # 顺带扫改动文件里的 key/token/password/手机号/内网 IP
```

在改动文件里 grep：API Key / token / secret / password / 密码 / 手机号 / 真实姓名 / 设备 ID / IMEI /
内网 IP / 路径里的用户名 / 截图里的敏感信息（通知栏、聊天记录）。

**特查这几处**：截图文件、日志文件、`ui/controls.json`、`CHANGELOG.md`、commit message、运维笔记。

有命中就停下，报告给用户，等确认再继续。
（已知边界：`~/.dsh-auth-pass` 是**600 的 6 位数字**，脚本只做"内容是否出现"的全文比对；
删掉文件不等于擦除 —— 见运维笔记"撤销≠擦除"那条。）

---

## 八、不推的东西

`.gitignore` 必须覆盖：

- `node_modules/`
- `*.log`、`*.bak`、`*.tmp`
- `Download/`、`backup/`、`备份/`
- `~/.dsh/` 相关本地状态
- 截图产出的原始 PNG（要放 README 先压缩 + 检查敏感信息）
- `*.part`、`.DS_Store`、`Thumbs.db`
- 任何含明文凭据的文件

`tools/pre-push-check` 会逐条核对 `.gitignore` 里这些模式在不在。

---

## 九、push 流程

1. 走完第三节自检清单（`tools/pre-push-check --strict`）
2. `git pull --rebase` 同步远端
3. `git add <明确文件>`（**不要 `git add .`**，容易带进垃圾）
4. `git commit`（按第五节格式）
5. `git push`（本仓库用 `dsh-gh push`，token 在 `~/.dsh-gh-token`(600)，不写进 `.git/config`）
6. **推送后报告**：commit hash、push 结果、远端最新 commit、受影响文件清单
7. 在运维笔记追加一条"推送记录"：时间、commit、改动摘要

> 注意：本仓库的远端是 `Maopk/dsh-termux-kit`。**没有用户明确说"推"，就不要 push**；
> 本地提交与推送是两件事，报告时必须分清（本地文件 / 本地提交 / 已推送到远端）。

---

## 十、日常习惯总结（背下来）

1. **动手前**：先读一遍这份守则。
2. **动手时**：每做一件事，就在运维笔记里记一条（时间 + 动作 + 原因 + 结果 + 证据）。
3. **动手后**：跑自检；对照 README 八项、CHANGELOG、版本号、敏感信息扫描。
4. **push 前**：逐条勾第三节清单（`tools/pre-push-check --strict`）。
5. **push 后**：报告 + 追加推送记录。

**没有记录的工作 = 没做的工作。** 因为过三天你自己都不记得当时为什么那么改。

---

## 十一、本仓库的门禁（现成的，别重造）

一句话：**它们不是"建议"，是出包/提交前的硬门禁**，不过就不许出包。

| 门禁 | 管什么 | 跑法 |
|---|---|---|
| `tools/ui-controls check` | 三处 UI（控制台/桥/面板）与 `ui/controls.json`、`ui/theme.json` 一致（含色板、主题资源、形状 drawable） | 出包前自动跑（两个 `build.sh` 里） |
| `tools/i18n-table check` | 四个生成目标（bash 表 + 两个 `Lang.java` + 面板 `UI_TEXT`）与 `i18n/zh.json` 一致 | 同上 |
| `tools/i18n-audit` | 文案/主题/色板/状态口径 + **面板渲染测试台** + 单一数据源（条目数、灯色、开关、语言） | 同上，也可单独跑 |
| `tools/app-verify console\|bridge` | 打开**编好的 APK**核对：版本、主题、翻译表真的在包里；**版本号唯一性** | 出包后 |
| `tools/panel-render-test` | 真跑三遍面板渲染，抓"一点开就消失"这类运行时错误 | `i18n-audit` 里 |
| `tools/check-task-ids` | 任务 id 在四个地方（tasksd 白名单 / 控制台 / 面板 / 组件脚本）一致 | 自检 |
| `tools/check-counts` | **(h) 可数的量只许写一处**：小组件数的字面量与 `widgets/` 实际不符即报错；`--strict` = 除唯一源外不许出现字面量（**尚未达到**，是 (h) 的目标形态） | 自检（本地闸，**未接 CI**） |
| `tools/install-tools --check` | 仓库 `tools/` 与安装位 `~/.local/bin` 一致（防"我照着仓库推理、跑的是旧代码"） | 自检 |
| `tools/dsh-kit-update --check` | 仓库是否落后 `origin/master` **且** `~/.local/bin` 是否与仓库一致 | 手动（不带 `--check` 就会动手更新，顺序固定：快进合并 → 工具 → 小组件） |
| `tools/check-no-secrets.sh` | 本机凭据没泄进仓库 | push 前 |
| `tests/selftest.sh` | 70 项总自检（含沙箱冷启动；**会在手机上压负载**，忙的时候别整跑） | 手动 / CI |
| `tools/pre-push-check` | 第三节那份清单的脚本化版本 | push 前 |

> 仓库不一定住在 `$HOME/dsh-termux-kit`：`tools/` 里的脚本都认 `DSH_KIT_REPO`
> （例：`DSH_KIT_REPO=/path/to/dsh-termux-kit tools/ui-controls check`），不设时回退到 `$HOME/dsh-termux-kit`。
> 同理，读 App **真源码**的工具（`sync-apps` / `i18n-audit` / `i18n-table` / `ui-controls` / `app-verify`）认
> `DSH_CONSOLE_DIR` 与 `DSH_BRIDGE_DIR`，`tests/selftest.sh` 里读控制台源码的那几条断言只认 `DSH_CONSOLE_DIR`
> （都不设时回退 `$HOME/dsh-console`、`$HOME/droid-bridge`）—— 仓库 `apps/` 是只读镜像，不是编译源，改了不生效。
> 另外 `widgets/common.sh`、`widgets/[0-9]*.sh` 与 `tests/selftest.sh` 的家目录认 `DSH_HOME_DIR`
> （不设时是手机上那个 `/data/data/com.termux/files/home`）；`tools/install-tools` 的安装位跟 `$HOME` 走。

> **CI**（`.github/workflows/ci.yml`）跑的就是上面这一批里"不碰手机也能跑"的部分：
> `bash tools/ci-shellcheck.sh`（全量 `bash -n` + shellcheck）、`ruff check tools tests`、逐文件 `mypy`
> （目前只覆盖 `tools/*`；`tests/report_check.py` 与 `tests/unit` 本地已过、接进 CI 要改 workflow，见
> [`docs/test-layers.md`](docs/test-layers.md) 末节）、`bash tools/ci-gates.sh`（10 道门禁：9 道数据源 +
> `python -m pytest tests/unit -q`）、`bash tests/lib-tests.sh`（`widgets/common.sh` 与 `tests/lib/report.sh`
> 的 55 条单元断言）、`python -m pytest tests/unit -q`（报告策略的 19 个用例：14 个手写 + 5 个真实报告），
> 以及在容器里 `bash tests/ci-selftest.sh`（一次性 HOME + 手机同款目录布局；判定交给
> `tests/report_check.py`，结论写进 run summary）。
> 本地跑同一套：`python -m pip install -r requirements-dev.txt`，然后照抄上面这几行命令。
> 自检的每一项都带 `logic` / `simulable` / `device` 三类之一（表在 `tests/lib/categories.tsv`，每行都写了理由）：
> logic 与 simulable 的失败**必须为 0**；device 里没有放行行的失败算 unexpected，同样判红；放行的失败仍会逐条
> 列在日志里（`--verbose`），不会静默吞掉。规则、报告 schema 与"为什么这么分"见
> [`docs/test-layers.md`](docs/test-layers.md)。`tools/sync-apps` 会覆写 `apps/`，CI 里不跑。

**仓库里每个自检/工具在 CI 里的去向**（免得下次再问"这个跑了吗"）：

| 脚本 | CI 里在哪跑 |
|---|---|
| `tools/ci-shellcheck.sh` | `static` job：全量 `bash -n` + shellcheck（55 个脚本） |
| `tools/check-task-ids` · `tools/check-no-secrets.sh` · `tools/ui-controls check` · `tools/i18n-table check` · `tools/i18n-java-fix --check` ×2 · `tools/install-tools --check` · `tools/panel-render-test` · `tools/i18n-audit --quiet` | `gates` job（`tools/ci-gates.sh` 这 9 道） |
| `tests/lib-tests.sh` | `unit` job（`widgets/common.sh` 与 `tests/lib/report.sh` 的 55 条断言） |
| `tests/unit/`（pytest）· `tests/report_check.py` | `gates` job（`tools/ci-gates.sh` 的第 10 道，缺 pytest 时自己装同一个 pin）—— 报告策略的 19 个用例：14 个手写 + 5 个真实报告；检查器本身还给 `selftest` job 里容器那一跑下判定 |
| `tests/selftest.sh` | `selftest` job（容器 + 一次性 HOME，经 `tests/ci-selftest.sh`；每次跑都会写 `~/.smoke/selftest.json`，按 `tests/lib/categories.tsv` 分类） |
| `tools/pre-push-check` | `selftest` job 末尾，**只记录不判红**（容器里实测 12 通过 · 0 失败 · 0 提醒） |
| `tools/app-verify` · `apps/*/build.sh` | **不进**：要编好的 APK / Android SDK（aapt2、javac、d8、apksigner 与 `$DSH_AJ` 的 `android.jar`）；`build.sh` 在这里只做语法检查 |
| `tools/ui-bg-check` | **不进**：输入是一张真机截图 |
| `tools/sync-apps` | **不进**：它会覆写 `apps/`，而 CI 里 `apps/` 是只读镜像 |
| `tools/i18n-build-table` · `tools/verify-i18n-patch` | **不进**：翻译流程的助手，要人给的输入（git diff / 补丁 JSON）；它们生成的表由 `i18n-table check` 把关 |
| `tools/dsh-kit-update` | **不进**：运行期工具，在手机上把仓库 / `~/.local/bin` / 12 个小组件更新到最新（CI 里仓库是只读的，也没有"已安装副本"要刷新） |
| 其余 `tools/dsh-*` · `tools/droid*` · `tools/clash-*` · `tools/install-widgets`（不带 `--dry-run`） | **不进**：都是在手机上操作 DSH / 桥 / adb / 代理的**运行期**工具，不是测试；`install-widgets --dry-run` 那 12 条在 selftest 的 L2 里跑 |

改完东西的最短路径：

```bash
# 面板改动（只需 pnpm install + 刷新页面，不用重启 DSH）
tools/i18n-audit && cp plugins/dsh-mobile-local/client.js ~/.dsh/profiles/web/local/dsh-mobile-local/client.js
( cd ~/.dsh/profiles/web && pnpm install --prefer-offline && ~/.local/bin/dsh-relink-bundles --check )

# APK 改动（必须重新编译；build.sh 里已经内置前两个门禁）
# 编译前确认 ks.jks 还在原来那个目录：build.sh 按 DSH_KS → $SRC/ks.jks → ~/dsh-console/ks.jks 找，
# 全都没有才会新建一把；换了钥匙新包就装不上手机（安装器只报一句 "App not installed"，不解释原因）。
bash apps/console/build.sh && bash apps/bridge/build.sh
tools/app-verify console && tools/app-verify bridge
```

---

## 十二、新会话交接（下一个会话照着这个写）

交接文档**不算记录**（记录只认 §二的运维笔记），但它决定下一个会话照着什么干。写错一个路径，对方就会沿着错的
一路做下去 —— 2026-09-28 一天里连错两次（把笔记写成 `docs/运维笔记.md`、把数据源写成不存在的 `ui-controls.json`），
所以固定成下面这份骨架：

```markdown
# 新会话交接 · <主题>

## 一、项目速览
- 仓库：Maopk/dsh-termux-kit（本地 ~/dsh-termux-kit/）
- 本地运维笔记：~/DSH运维笔记.md
- 仓库运维笔记：docs/DSH运维笔记.md
- 交付目录（唯一）：Download/dsh/ —— 用 ~/.local/bin/dsh-out <文件> 按扩展名归类
- 三条生成链（"单一数据源"说的就是这三份，没有 ui-controls.json 这个文件）：
  ui/controls.json（文案 / 分类 / 控件 id / appVersions）· ui/theme.json（色板与主题）· i18n/zh.json（翻译）

## 二、三处 UI 与生效方式
- 控制台 APK：~/dsh-console/ · 桥 APK：~/droid-bridge/（仓库 apps/ 只是 sync-apps.sh 的镜像副本，改它不生效）
- 页面面板：plugins/dsh-mobile-local → 安装位 ~/.dsh/profiles/web/local/dsh-mobile-local/
- 面板：pnpm install + 刷新页面（不重启）· APK：重新编译，用户自己装 · host.js：改动要重启 DSH

## 三、这次要做什么（按顺序，一条一条来）
## 四、约束（不许做什么）
## 五、做完给我看什么（验收标准）
```

四条硬要求：

1. **路径先验证再写**：写进交接的路径，先在机器上 `ls` 确认它真的存在。仓库笔记是 `docs/DSH运维笔记.md`。
2. **数据源写全三条链，不许把 `ui-controls.json` 当文件名**（这个文件不存在）：面板版本号来自 `ui/controls.json` 的 `appVersions`。
3. **推不推要说清**：默认**不推**。"本地文件 / 本地提交 / 已推送到远端"三档必须分开写（§九）。
4. **APK 由用户自己装**；要 AI 代装就得写明原因，并且先过 §七的敏感信息扫描。

---

## 十三、术语（先查表，再写字）

**一个动作，全仓库只用一个词**：要么是本项目定义的（软停 / 强杀 / 桥 / 通道 …），要么是行业标准译名。
表在 [docs/术语表.md](docs/术语表.md) —— 改文档、写 UI 文案、起变量名之前先对一遍；要用的词不在表里，先加进表再用。

三类词不用：

- **A 类 · 修饰词**：优雅 / 无缝 / 丝滑 / 极致 / 强大 / 完美 / 完善 / 赋能 —— 直接删，或换成具体动作。
- **B 类 · 伪技术词**：健壮性（写"容错"）· 闭环（写清具体循环）· 抓手 / 拉通（删）· 对齐（只留排版和几何义，讲"两边一致"就写"一致 / 一一对应"）。
- **C 类 · 翻译腔**："这样做的好处是"（写"这样做的结果是"）· "值得注意的是"（直接写事）· "不得不说"（删）。

对照：

| 不要写 | 写成 |
|---|---|
| 只向服务发 SIGTERM 优雅退出 | 只向服务发 SIGTERM **软停** |
| 第三版闭环成功 | 第三版按「量一次 → 拖一次」**逐段收敛**成功 |
| 两份 README 结构对齐 | 两份 README 结构**一一对应** |
| 窗口在前台就直接按 Back（无缝） | 窗口在前台就直接按 Back（**前台不动**） |
