
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
