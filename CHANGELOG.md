
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
