# DSH × Termux 手机运维套件

> 在一台**未 root 的安卓手机**上，把 DeepSeek Harness（DSH）养成「自己能开、自己能救、自己会报告」的东西。
> 全部用 Termux + 无障碍服务 + 官方 `RUN_COMMAND` 通道搭成，**不需要 root**。

这套东西是**从零长出来的**：一开始只是"点一下就能启动 DSH"，最后变成
9 个桌面小组件 + 一个控制台 App + 一个无障碍桥 + 一套自检套件 + 三个 DSH 页面插件。
每一处设计背后都有一次真实的踩坑，证据与复盘都在 `docs/DSH运维笔记.md`（1100+ 行）。

---

## 它解决什么问题

| 痛点 | 这里的东西 |
|---|---|
| 手机上敲命令太累、DSH 冷启动 18 秒 | 桌面小组件一键启动，且**只在真就绪时才开浏览器**（不会开出 `HTTP ERROR 404`） |
| 连点两次会起两个实例、互相抢锁把 DSH 搞崩 | 启动互斥锁 + 孤儿锁自动清理 |
| 后台被系统冻结，"跳转回来就无法读取" | 启动即拿 `termux-wake-lock` + 电池白名单 |
| 忘了关，进程留在后台 | 一键关闭/软重启/硬重启，且**不会撒谎**（做不到就明说做不到） |
| AI 想控制手机却没有通道 | 两条独立通道：**无障碍桥**（回环，不要网络）与 **adb 无线调试**（要 Wi-Fi），界面上严格分开 |
| 通道一旦给出去就收不回 | 每条通道都有**自救撤销口**（见下文"安全模型"） |
| 装 APK 要过系统身份验证 | `dsh-install-apk`：按颜色找到那行蓝字 → 6 位密码 → 完成 → **再开一次 APK 复核** |
| 手机端 UI 不适合手指 | 控制台 App + DSH 页面插件（分类按钮、日志独立一屏、开关式授权） |

---

## 组件地图

```
手机桌面                          手机内部
┌─────────────────────┐          ┌──────────────────────────────────────────────┐
│ 小组件 ×9            │  ──────▶ │ widgets/*.sh   （每个都是独立可跑、可 --dry-run）│
│ （Termux:Widget）    │          │   ↑ common.sh：启动互斥/真就绪判定/桥唤醒/状态发布│
├─────────────────────┤          ├──────────────────────────────────────────────┤
│ DSH 控制台 App       │  ──────▶ │ com.termux.RUN_COMMAND（官方通道，无存储/网络权限）│
│ （本仓库 apps/console）│         ├──────────────────────────────────────────────┤
├─────────────────────┤          │ tools/  命令行工具（dsh-* 与 droid-*）           │
│ DSH 页面里的 ☰ 面板  │  ──────▶ │ tools/dsh-tasksd（127.0.0.1:8787，token 白名单） │
│ （plugins/dsh-mobile）│         ├──────────────────────────────────────────────┤
└─────────────────────┘          │ DSH 桥 App（apps/bridge）：无障碍 + 回环 8788     │
                                 │ droid-sock ↔ 桥：截图/点按/滑动/输入/UI 树        │
                                 └──────────────────────────────────────────────┘
```

### `widgets/` —— 9 个小组件（Termux:Widget 用）

| 组件 | 干什么 | 关键点 |
|---|---|---|
| `1_启动DSH` | 启动 web 并**等真就绪** | 真就绪＝①日志有 token 行 ②该 URL `curl` 得 200 ③进程活着 |
| `2_关闭DSH` | 停服务、关浏览器 | 有 adb 才敢说"关掉了 Chrome"，否则**如实说做不到** |
| `3_备份DSH` | 打包 + 校验 | `zstd -t` + 条目数复核，tar 退出码 1 不算失败 |
| `4_软重启` / `6_硬重启` | SIGTERM / -9 后重启 | 取锁在动手之前（连点不会自杀） |
| `5_清理DSH` | 只删我的产物 | 绝不碰你的文件 |
| `7_重连AI通道` | adb + 桥一起恢复 | 失败会分别说清是哪条 |
| `8_自动开无线调试` | 半自动开无线调试 | Wi-Fi 关着时打开设置页等你点一下，之后**自动继续** |
| `9_撤销密码授权` | 收回"AI 能用你的锁屏密码" | 删 `~/.dsh-auth-pass` 并复核确实没了 |
| `0_紧急停止` | 一键撤销 AI 的全部控制 | 四条路径全断（见安全模型） |

### `tools/` —— 命令行工具（放进 `$PREFIX/../home/.local/bin`）

| 工具 | 作用 |
|---|---|
| `dsh-status-pub` | 采集状态（DSH/桥/adb/锁/任务）→ JSON+文本，给 App 与页面用；`--brief` 给 App 瘦身 |
| `dsh-restart` | 安全重启（先判"页面卡"还是"服务死"，只杀 `bin.js web`，清孤儿锁，等真就绪） |
| `dsh-bridge` / `droid-sock` | 桥的开关与调用（`status/wake/stop/off`） |
| `droid` / `droid-hub` / `droid-pair` / `droid-panic` | adb 无线调试那条线：连接、配对、批量控制、紧急切断 |
| `dsh-uitap` | **按文字**点界面（自动滚动、text/desc 双匹配）——写死坐标会误触，用它 |
| `dsh-snapshot` / `dsh-backup` | 快照与备份（原子写 + 校验 + 保留 N 份） |
| `dsh-install-apk` | 全自动装包：开安装器 → 那行蓝字 → 6 位密码 → 完成 → **再开一次复核** |
| `dsh-auth-pass` | 「密码使用权」的唯一真源：`status/set/revoke/digits/path`（**从不回显密码**） |
| `dsh-find-blue` | 按**像素颜色**找那行蓝字（它不在无障碍树里，且系统文案会变） |
| `dsh-scrub-secret` | 把一个敏感值从记忆/笔记里就地抹掉（`--all` 连会话存档一起扫） |
| `dsh-relink-bundles` | 修"页面报 Failed to load plugins"（见事故记录） |
| `dsh-selflook` / `dsh-control` / `dsh-eval` | AI 自看/遥控 DSH 页面（截图、点按、求值） |
| `dsh-tasksd` | 页面面板的后端：`127.0.0.1:8787`，token + 白名单，只有它能跑任务 |
| `clash-override.py` | 在 Clash Meta 里按坐标+校验写覆写（它的表单是"列表套对话框"，按文字找会撞车） |

### `apps/` —— 两个自己写的安卓 App（纯 Termux 构建，无 Android Studio）

- **`apps/console`（DSH 控制台）**：9 个组件的图形界面 + 桌面小部件。
  权限只有**一个** `com.termux.permission.RUN_COMMAND`；没有存储/网络/无障碍。
  按功能分类（启动·停止 / 通道 / 维护 / 紧急）、日志独立一屏、"密码使用权"是**开关**。
- **`apps/bridge`（DSH 桥）**：无障碍服务 + 回环服务，让 AI 能看屏、点按、滑动、输入。
  软停（`stop`）是**持久**的（系统重绑也不会自己回来），带 token 的广播才能唤醒。

构建：`bash apps/console/build.sh`（aapt2 → javac → d8 → apksigner，全在 Termux 里跑；
密钥库不进仓库，脚本会在缺失时自动生成一个自签的）。

### `plugins/` —— 三个 DSH 页面插件

| 插件 | 作用 |
|---|---|
| `dsh-mobile-local` | 手机端任务面板：☰ 按钮 + 分类任务 + 状态灯 + **密码使用权开关** |
| `dsh-selflook-local` | AI 自看/遥控页面的客户端一半（另一半是 `tools/dsh-control`） |
| `dsh-filepanel-local` | 文件面板的本地实现（给自看插件当 RPC 通道） |

### `tests/selftest.sh` —— 58 项自检

```bash
bash tests/selftest.sh
```
分五层：语法 → `--dry-run` 全流程预演 → 启动锁/就绪判定回归 → **真实执行**（备份/清理/桥的停与唤）
→ 沙箱冷启动取证（8099 端口，不碰正在跑的实例）。结论长这样：

```
✔ 半启动取证          采样到 HTTP 码序列：404 401（404=路由还没挂，旧逻辑此时就会开浏览器）
✔ 撤销密码授权(真跑)  沙箱文件先覆写后删除，退出 0
✔ 真授权未被误删      沙箱测试只动临时路径，真授权仍在且权限 600
════ 结果：通过 58 / 失败 0 / 跳过 6 ════
```

> 跳过项是真跑会杀掉当前会话或需要人工恢复的（关闭/重启/紧急停止），它们的步骤已由单测覆盖。

---

## 安全模型（重要）

这套东西给 AI 的能力不小，所以**每一条通道都配了"你自己能一键收回"的口子**，而且都**实际演练过**
（不接受"写在说明里的保证"）：

| 通道 | 给了什么 | 怎么收回 | 演练结果 |
|---|---|---|---|
| 无障碍桥 | 看屏、点按、滑动、输入 | 组件 `0_紧急停止`／`dsh-bridge stop`／桥的通知里停 | `stop` 后 18 秒不自恢复；带 token 的广播 2 秒唤醒 |
| 回环 token | 调用桥的凭证 | 删/改名 `~/.dsh-bridge-token` | 唤醒被拒（token 不对广播被忽略） |
| adb 无线调试 | shell 级能力（最强） | `droid-panic`／关掉"无线调试"／重启手机 | 关开关即失效；重启后必须人工再开一次 |
| 页面遥控通道 | 点你的 DSH 页面 | 删 `~/.dsh-look-cmd.json`／改 `~/.dsh-mobile-ui.json` 为 `{"enabled":false}` | 面板立即消失 |
| **密码使用权** | 用你的 6 位锁屏密码替你过系统身份验证 | 控制台「维护」里的**开关**／组件 `9_撤销密码授权`／`rm ~/.dsh-auth-pass` | 收回后 `dsh-install-apk` 前置检查即拒（退出码 3） |

**关于那 6 位密码（写下来供检查）**
- 它存放在 `~/.dsh-auth-pass`（`chmod 600`），**只**在你交办的"过系统身份验证"场景被读取
  （装 APK、解除应用"设置限制"等）。
- **绝不**用于解锁手机翻看内容、支付/免密，或与当次任务无关的任何场景。
- 撤销 = 删掉那个文件，一步到位；**撤销 ≠ 擦除**——如果你在对话里打过它，明文还会留在
  DSH 会话存档与记忆库里，要清得用 `tools/dsh-scrub-secret`，且在会话结束后再扫一次
  （这条是实测出来的，详见笔记）。

---

## 事故与复盘（节选，全量在 `docs/DSH运维笔记.md`）

这套东西的可信度主要来自这些"被现实打脸"的记录：

1. **`HTTP ERROR 404` 页面**：`dsh web` 先绑端口、后挂路由，窗口期 `/` 返回 404；
   旧的就绪判定"端口通 + HTTP 非 000"把半启动当就绪 → 现在**必须拿到 200**。
2. **桥"关了它自己又开"**：无障碍被系统重绑 → 加 `userPaused`，软停变持久（实测 18 秒不回来）。
3. **组件 8 的假诊断**：它把"开关没保住"一律说成"没真正连上网络"，而同一次输出里
   `online=true` → 改成先回读、4 秒停下并给对原因（旧路径白扫 3 万个端口 40 秒）。
4. **状态回传被"切头"**：App 把所有回传统一从尾部截 4000 字节，而状态是一整个 JSON
   → 头被切掉、解析失败（`Value asks" of type java.lang.String…`）→ 现在状态整包、任务才截尾，
   并给状态加了 `--brief`（6463 → 1560 字节）。
5. **在 profile 里跑 `pnpm install` 会剪断页面 bundle**：两个运行时软链包被当"多余"删掉
   → 页面报 `Failed to load plugins` → `dsh-relink-bundles` 修，自检加了护栏。
6. **"日志里只有已发送、没有完成行"**：状态类结果当初不进日志 → 看着像没回传
   → 现在成对出现，自动刷新仍静默。

还有两次**我自己的诊断错误**也写进去了（拿 `&amp;` 转义的 URL 去 curl；拿 `--json` 模式
根本不写的 `status.json` 当判据）——**判据要选"这条路径真的会产出的证据"**。

---

## 快速开始（在 Termux 里）

```bash
# 1) 把工具装到位
install -m755 tools/* ~/.local/bin/
mkdir -p ~/.shortcuts/tasks && install -m755 widgets/*.sh ~/.shortcuts/tasks/
install -m644 widgets/common.sh ~/.local/share/dsh-widgets/ 2>/dev/null || {
  mkdir -p ~/.local/share/dsh-widgets && install -m755 widgets/common.sh ~/.local/share/dsh-widgets/; }

# 2) 装 Termux:Widget（桌面小组件要用它），并把 ~/.termux/termux.properties 里
#    allow-external-apps 设为 true（控制台 App 要用官方的 RUN_COMMAND 通道）

# 3) 自检（先看这套东西在你机器上是什么状态）
bash tests/selftest.sh

# 4) 想装两个 App：直接装 dist/ 里的 APK，或用源码自己构建
bash apps/console/build.sh && bash apps/bridge/build.sh
```

依赖：Termux（本文实测 0.119.0-beta.3）、`zstd`、`tesseract`（本地 OCR，可选）、
`ImageMagick`（找蓝字/取色用）、`adb`（无线调试那条线，可选）、Node 版 DSH。

---

## 目录结构

```
apps/     两个自研 App 的源码（console=控制台，bridge=无障碍桥）
widgets/  9 个 Termux 小组件 + common.sh
tools/    所有命令行工具
plugins/  三个 DSH 页面插件（丢进 profile 的 local/ 即可）
tests/    selftest.sh（58 项）
docs/     运维笔记（1100+ 行，含每次踩坑的证据）+ 控制台说明
dist/     已构建的 APK + SHA256SUMS
```

## 许可

MIT（见 `LICENSE`）。代码按"我在这台机器上实测可用"交付，**不承诺**在你机器上同样可用——
不同 ROM 的安装器文案、无障碍行为、省电策略都会不一样，笔记里那些坑就是证据。
