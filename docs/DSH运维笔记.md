# DSH 手机端运维笔记

> 由 DSH 助手维护。记忆类插件（灵枢/Hindsight/Mnemon）已于 2026-09-25 卸载后又装回，重要环境信息记录在此。

## 一、桌面小组件（Termux:Widget → `tasks/`，数字前缀决定显示顺序）

| 条目 | 作用 |
|---|---|
| `1_启动DSH.sh` | 后台启动 DSH Web（8080）。已在运行则直接打开浏览器（优先用 `~/.dsh-restart.log` 里的 token URL）。输出 tee 到 `~/.dsh-restart.log`；**启动失败会把退出状态和第一条错误行写进日志** |
| `2_关闭DSH.sh` | 停服务 → **自动备份**（时间戳，留最近 5 份）→ 日志轮换为 `.bak` → 清 8080 端口 → 关浏览器/DSH PWA → 关闭 Termux |
| `3_备份DSH.sh` | **主动备份**：不关服务，立刻做一次时间戳备份；结果写 `备份/最近备份.txt` 与 `备份日志.txt` |
| `4_软重启DSH.sh` | **软重启**（不杀进程）：只向服务发 SIGTERM 优雅退出（**不用 -9**）→ 备份 → 日志轮换 → 等端口释放 → 启动新服务；**不关浏览器/PWA、不关 Termux、不动其它进程**。若服务 15 秒内没优雅退出则放弃并提示改用硬重启。自检：`bash 4_软重启DSH.sh --dry-run` |
| `6_硬重启DSH.sh` | **硬重启**（最后一项，最彻底）：优雅停 → **`-9` 强杀** dsh 服务 / 灵枢桥 `md_cg` / `dsh-termux-runtime` 残留 → 备份 → 日志轮换 → 等端口释放 → **关掉旧浏览器与 PWA 窗口**（保证新页面加载最新插件模块）→ 启动新服务。**插件改动后必须用硬重启**。自检：`bash 6_硬重启DSH.sh --dry-run` |
| `5_清理DSH.sh` | **手动清理**：把 `备份/` 与 `备份/系统备份/` 里的**所有归档一起按修改时间排序**，只留最新 5 份，其余**直接删除**（不询问）。自检：`bash 5_清理DSH.sh --dry-run`（只列出将删的文件）；`DSH_CLEAN_PROTECT_SYSTEM=1` 可保护系统备份里最新的 1 个 |

六个脚本都会 `cd ~/storage/shared` 并 `source ~/.bashrc`（因此 `.bashrc` 里的环境变量对启动生效）。

## 二、环境变量（`~/.bashrc`）

```bash
export PATH="$HOME/.local/bin:$PATH"
export DEEPSEEK_API_KEY=...
export DSH_HOME="/data/data/com.termux/files/home/.dsh"
export NODE_COMPILE_CACHE="/data/data/com.termux/files/usr/tmp/v8-cache"
export BROWSER="$HOME/.local/bin/dsh-browser-open"
export DSH_VAULT_PASSWORD="随便设个复杂密码"   # ⚠️ 占位符，见下
```

- ✅ **`dsh-vault` 已于 2026-09-25 卸载**（当时确认库内无任何数据），`DSH_VAULT_PASSWORD` 一行也已从 `.bashrc` 删除。
  - 原因：该插件缺 `DSH_VAULT_PASSWORD` 时会让**整个插件树启动失败**（19:19 崩溃即此原因），稳定性风险高且从未真正使用。
  - 如需重新启用：`dsh plugin --profile web add dsh-vault`，并在 `.bashrc` 里用**真实强密码**设置 `DSH_VAULT_PASSWORD`（密码一旦设定且写入数据后不可再改，否则旧数据无法解密）。
- 浏览器打开器 `~/.local/bin/dsh-browser-open`：先发无包名 VIEW 意图让系统解析（命中桌面安装的 DSH PWA 复用窗口），再退到 Chrome，最后默认浏览器。

## 三、插件（profile: `~/.dsh/profiles/web`）

**当前已装全部 15 个（2026-09-25 晚，`dsh plugin --profile web list` 实测）：**

| 插件 | 版本 | 说明 |
|---|---|---|
| `@furongjun1999/dsh-memory` | 0.5.0 | 灵枢记忆（拉起 python MCP `md_cg`） |
| `@vectorize-io/hindsight-coding-agents` | 0.7.0 | 🧠 Hindsight 仓库知识库 |
| `dsh-mnemon` | 0.5.14 | Mnemon 记忆体系 |
| `dsh-filetransfer` | 0.2.5 | 文件上传/交付卡片（监听 127.0.0.1:3199） |
| `dsh-filepanel-local` | file:local/… | **本地包**：右侧文件面板（源码来自 Lings01/dsh-filepanel-plugin，已改造） |
| `dsh-filepanel-plugin` | 1.0.0 | 上游原包（仅作依赖保留，实际生效的是上面的本地包） |
| `dsh-better-sidebar` | 0.21.1 | 侧边栏增强 |
| `dsh-context` | 0.56.2 | 上下文概览 |
| `dsh-cost-meter` | 1.7.37 | 用量/成本统计 |
| `dsh-find-plugin` | 0.4.0 | 插件搜索 |
| `dsh-vision-router` | 2.2.3 | 视觉工具路由 |
| `@liustack/modsearch` | 5.10.5 | 网络搜索桥 |
| `dsh-univer-office` | 0.3.5 | Univer 文档/表格/演示能力 |
| `@nanmicoder/dsh-agent-teams` | 0.1.21 | Agent Teams 多智能体协作 |
| `dshmarket` | ^1.65.1 | 插件市场 |

- 未装 `@liustack/modlens`（与 `dsh-vision-router` 能力重叠，避免冲突）
- 已卸载 `dsh-vault`（2026-09-25，稳定性考虑，详见第二节）
- 记忆工具与注入在新会话/重启后生效；`2_关闭DSH.sh` 会一并清掉 `md_cg` 子进程
- 每次退出 DSH 时，插件清单快照会随状态备份一起归档，并另存一份最新的到 `Download/dsh/文档/插件清单.txt`（随时可查，不用解包）
- **文件面板**：`~/.dsh/profiles/web/local/dsh-filepanel-local`（profile 内**本地包**，`file:` 依赖）
  - 来源：Lings01/dsh-filepanel-plugin 的 host/client 源码，已按 bundle 规范改造：
    - host：`export const inject` + `apply.inject = inject` + `export default apply`，RPC 走 webServer 路由 `/__dsh__/filepanel/rpc`（bundle 环境没有动态沙箱的 `harness`）
    - client：`window.__ModuleLoader__.load({id, factory: (require) => ...})`，`exports.apply/inject`，CSS 用 `<style data-plugin-css>` 注入，RPC 用 fetch
  - profile `cordis.patch.yml` 里覆写了 filetransfer 的配置（workspaceRoot=HOME、allowedOrigins=8080 等）
- ⚠️ **教训**：不要直接改 `node_modules` 里的 git 依赖（`dsh plugin add`/市场安装会重新解包冲掉）；本地包才抗覆盖。

### 退出自动备份（2026-09-25 起）

- 触发：`2_关闭DSH.sh` 的第 2 步调用 `~/.local/bin/dsh-backup`
- 产物：`Download/dsh/备份/dsh-state-YYYYMMDD-HHMMSS.tar.gz`（**时间戳编号**）
- 大小/耗时：约 3.8M / 0.5 秒（排除 `node_modules`、`cache`、`profiles.bak-*`、`.credentials.yaml`）
- 内容：`~/.dsh` 核心状态（settings、profiles 配置与本地包、sessions、storages、attachments）、`~/.shortcuts`、`~/.plugin-src`、运维笔记、`_meta/插件清单.txt`、`_meta/bashrc.sanitized`（**API Key 已脱敏**）
- 保留策略：**两套并存**
  - 自动（每次备份时执行）：**保留最近 30 天** + **200 份硬上限**，按**修改时间**判断（不按文件名，避免时钟跳变误删）
  - 手动（第 5 项 `5_清理DSH.sh`）：点一下立刻裁剪到**只留最近 5 份**，超出直接删除
  - 单份约 4M：按每天关闭 3 次算，30 天 ≈ 90 份 ≈ 360M，1TB 空间无压力
  - 可临时覆盖参数：`DSH_BACKUP_KEEP_DAYS=7 DSH_BACKUP_MAX_FILES=50 dsh-backup`
  - 测试用沙盒：`DSH_BACKUP_DEST=/path/to/test dsh-backup`（不碰真实备份）
- 目录结构：`备份/` 根目录放自动状态备份（约 4M/份）；`备份/系统备份/` 预留给**完整运行时备份**（含 node_modules，约 190M/份），**目前为空**（2026-09-25 已按"只留最新 5 份"规则清理，释放约 380M）
- **需要完整运行时备份时**（例如大改动前）手动生成，用完可自行删除或移到 `Download/dsh/归档/`（那里的文件不参与备份轮换）：
```bash
tar czf ~/storage/downloads/dsh/归档/dsh-full-$(date +%Y%m%d-%H%M%S).tar.gz \
  -C /data/data/com.termux/files/home .local/opt/dsh-termux-runtime
# 恢复：cd /data/data/com.termux/files/home && tar xzf <备份文件>
```
- 日志：`Download/dsh/备份/备份日志.txt`
- 手工执行：`~/.local/bin/dsh-backup`

## 四、改配置后的标准流程（避免再次崩溃）

1. 改完先做**第二实例冒烟测试**（不碰正在跑的服务）：
```bash
source ~/.bashrc
dsh --patch ~/.smoke/patch.yml --profile web --port 0 --no-open > ~/.smoke/boot.log 2>&1 &
# 等 20-40 秒，检查日志里出现 "dsh web: http" 即启动成功；之后 kill 掉该进程
grep "dsh web: http" ~/.smoke/boot.log && echo OK
```
   `~/.smoke/patch.yml` 里临时禁用 filetransfer（避免 3199 端口与在跑实例冲突）：
```yaml
- id: filetransfer
  disabled: true
```
2. 冒烟通过后再让用户重启：小组件 `2_关闭DSH` → `1_启动DSH`
3. **注意**：profile 里任何一行插件 `apply()` 抛错都会导致整个 DSH 启动失败（不是只坏那一行），所以冒烟测试必不可少。

## 五、回滚手段

- **🚑 救援 profile（打不开时的兜底）**：`~/.dsh/profiles/rescue` 已创建（shipped 模板，几乎无第三方插件）。
  若主 profile 启动失败，在 Termux 里执行：
```bash
dsh --profile rescue web --port 8080
```
  即可用最干净的配置打开 DSH，进去后再排查/禁用出问题的插件行。
- 启动失败不再静默：`1_启动DSH.sh` 已改为在服务退出时把退出状态和**第一条错误行**写进 `~/.dsh-restart.log`（装了 Termux:API 的话还会弹通知）。所以「打不开」时先看：
```bash
tail -30 ~/.dsh-restart.log
```
- 禁用某行插件：在 `~/.dsh/profiles/web/cordis.patch.yml` 追加
```yaml
- id: filepanel
  disabled: true
```
- 查看最终组合树：`dsh --profile web --dump-config`
- 启动日志：`~/.dsh-restart.log`（上一轮为 `.bak`）

## 六、其他

- **📁 产物投递规则（重要，长期生效）**：DSH 生成的任何文件都统一投递到
  `~/storage/downloads/dsh/`（即 **Download/dsh/**），并按类型自动归类：
  文档 / 表格 / 演示 / 脚本 / 配置 / 归档 / 图片 / **备份** / 其他。
  - 投递命令：`~/.local/bin/dsh-out <文件路径>`（按扩展名自动选目录）
  - 用户无法通过 DSH 界面保存文件，所以这个文件夹是主要交付通道（手机文件管理器直接打开）
  - **备份统一放 `Download/dsh/备份/`**：2026-09-25 已把 Download 根目录散落的 3 个归档归位
    （termux-home-backup-2026-09-25.tar.gz 7.2K、dsh-full-backup-2026-09-26.tar.gz 196M、dsh-clean-2026-09-26.tar.gz 184M，共约 380M）
- 手机存储：`~/storage/shared → /storage/emulated/0`，读写已通
- htop 已装；zip/unzip 已装
- 配套：`~/.shortcuts/tasks/` 两个脚本、`~/.plugin-src/` 存有 filepanel 原始动态源码备份

## 七、视觉/看图能力（2026-09-26 定案）

**根因**：`dsh-vision-router` 的 429 限流**不在插件**，而在它的内置匿名后端（全部托管 OVH，限 **2 次/分钟/IP/模型**）。换插件无效——同类插件要么接同一批免费端点，要么依赖本地 Claude Code/Codex 登录。

**已落地方案（本地 OCR 优先，零成本零限流）**：
- 已装 `tesseract` 5.5.3 + 中文包 `chi_sim`（2.4M，从官方 tessdata_fast 拉取；Termux 仓库无中文包）
- `~/.dsh/settings.yaml` 里设 `vision-router.ocrEngine: tesseract` → **文字类截图 OCR 完全本地化**
- 实战验证：`vision_ocr` 返回 `engine: tesseract`，成功识别中文截图（文件管理器界面）
- 命令行等价用法：`tesseract 图片 stdout -l chi_sim+eng`

**真读图（版面/颜色/细节）的升级路径**（按方便程度排序）：
1. **不加 key**：Web UI → Settings → **Vision Router** → 视觉后端行可选**任何已配置的可调用模型**（本机 `deepseek-vision`/`deepseek-flash` 即是默认模型）→ 视觉链优先用它，OVH 只作兜底
2. **加永久免费 key（质量最稳）**：智谱 bigmodel.cn 的 `glm-4.6v-flash`（永久免费、大陆直连、不限 token）
   - 注册拿 key → 写入 `~/.bashrc`：`export ZAI_API_KEY="你的key"`
   - 把 `<profile>/node_modules/dsh-vision-router/presets/zhipu.yaml` 的 provider 段并入 profile 的 `cordis.patch.yml`
   - 冒烟测试后重启生效
   - 备选：DashScope（`DASHSCOPE_API_KEY`，新用户 100 万 token/系列）、SiliconFlow（`SILICONFLOW_API_KEY`，¥14 赠金）
3. OVH 官方 access key：从 2 次/分钟提到 400 次/分钟

## 八、插件安装记录（滚动）

| 时间 | 插件 | 版本 | 安装方式 | 备注 |
|---|---|---|---|---|
| 2026-09-26 | `dsh-whale-widget`（余额小鲸鱼挂件，MeteorNOX） | 0.3.15 | `dsh plugin --profile web add github:MeteorNOX/DeepSeek-Balance-Whale-Widget` | 标准 bundle；显示 DeepSeek 余额/今日消耗/峰谷定价，右下角挂件；冒烟测试通过，**重启后生效** |
| 2026-09-26 | `dsh-gomoku`（五子棋，omdsh-dev，npm `@yejiming/dsh-gomoku`） | 0.0.1 | `dsh plugin --profile web add @yejiming/dsh-gomoku` | 15×15 无禁手；人机/双 AI 对弈，AI 走子真调 LLM（能力标注 host-runtime + llm）；棋局状态在浏览器端，服务端只仲裁 AI 落子；侧边栏棋盘弹窗（⟳新开局 / ⏸暂停 / ▶继续）；冒烟测试 0 错误，**重启后生效** |

**插件市场怎么搜**（比点 UI 快）：目录源 `https://awesome-dsh-plugin.com/plugins.json`（500 万字节、4353 个插件，含 `npm`/`install`/`stars`/`downloads`/`capabilities` 字段）。本地副本 `~/.smoke/plugins.json`，搜法：
```bash
python3 - <<'PY'
import json,re
items=json.load(open('/data/data/com.termux/files/home/.smoke/plugins.json',encoding='utf-8'))
rx=re.compile(r'五子棋|gomoku|小游戏|minigame',re.I)
for it in items:
    de=it.get('description'); de=' '.join(str(v) for v in de.values()) if isinstance(de,dict) else str(de)
    if rx.search(str(it.get('name'))+' '+de):
        print(it.get('name'),'|',str(de)[:70],'|',it.get('install'))
PY
```
（`find_dsh_plugin` 工具搜的是 GitHub `dsh-plugin` topic，覆盖不全——搜五子棋时它没结果，而市场目录里有。）


| 2026-09-26 | ~~`dsh-gomoku`（五子棋）~~ **已卸载** | 0.0.1 | `dsh plugin --profile web remove @yejiming/dsh-gomoku` | 试玩后按要求移除；deps 已删、bundles 19→18、`--dump-config` 无 gomoku 层、冒烟测试 0 错误；另手删了 `node_modules/@yejiming` 空目录残留。**插件移除需重启 DSH 生效**（五子棋标签页届时消失） |

> ⚠️ **教训（2026-09-26）**：`pnpm store prune` **不能当默认清理项**。我把它放进 `5_清理` 默认执行，结果下一次 `dsh plugin remove` 时 pnpm **重新下载了 405 个包、耗时 4 分 43 秒**（日志：`reused 0`）。
> 现在 `5_清理` **默认不动 pnpm 缓存**，需要时显式加 `--prune-store`。磁盘 vs 时间，在手机上时间更贵。

> ⚠️ 安装经验：`dsh plugin add` 会做**供应链策略校验**，耗时可能超过单次命令 60 秒超时——**必须在后台任务里跑**（`run_in_background`），否则进程会被超时杀掉、插件装到一半。

## 九、自看能力（AI 自己看界面，2026-09-26）

**目标**：AI 需要看界面效果时自己截图，不必让用户代劳。

**走过的路**：
- ❌ Termux 无可用浏览器引擎（`chromium`/`firefox` 都是虚拟包，无 x11 仓库）→ 无头截图不可行
- ❌ vision-router 的「桌面截图识图」(`vision_screenshot`) 只实现 macOS/Windows，Android 无代码路径
- ✅ **最终方案：让界面自己截图**（bundle 本地包 `dsh-selflook-local`）

**实现**：
- 位置：`~/.dsh/profiles/web/local/dsh-selflook-local`（host.js + client.js + cordis.patch.yml + package.json）
- 触发：`~/.local/bin/dsh-selflook`（内部 `touch ~/.dsh-look-request`）
- 流程：客户端每 2s 轮询宿主 `/__dsh__/selflook/rpc` → 有请求就把 DOM+CSS 渲染进 `foreignObject` → canvas → PNG → 回传 → 宿主写入 `Download/dsh/图片/self-look-<时间戳>.png`，路径记在 `~/.dsh-look-last.txt`
- 前提：**页面需在浏览器中打开**（客户端插件只存在于打开的页面里）
- bundle 插件**无需批准**（不同于动态客户端插件）
- **截图兜底**（`capture()`）：① 整页+CSS → ② 视口+CSS → ③ 整页无CSS → ④ **文字快照**（可见文字按 `[标签 @x,y 宽x高] 文字` 结构化上报，经 `log` 通道分片写入，必定成功）。全部失败信息回传 `~/.dsh-look-client.log`
- **遥控通道（2026-09-26 新增）**：客户端每 2s 读 `~/.dsh-look-cmd.json`（借 filepanel 的 `panel.readText`/`panel.writeText` RPC，**无需重启**），执行 `probe | click | capture | eval`，结果写回 `~/.dsh-look-cmd-result.json` 并清空命令文件。命令行工具：`~/.local/bin/dsh-control`（`probe <关键词>` / `click text=…|selector=…|x,y` / `capture` / `eval '<js>'`）与 `~/.local/bin/dsh-eval`（JS 从文件或 stdin 读，避免引号地狱，函数体里 `return` 结果）
- 客户端资源 URL：`/plugins/??dsh-selflook-local/client.js&rev=<派生哈希>`；响应头 `Cache-Control: public, max-age=31536000, immutable`
- 改动同步：`local/` 改完必须 `pnpm install`（node_modules 是**拷贝**不是软链，两侧 md5 要对得上）；**rev 随内容变化 → 刷新页面即生效，无需重启**（实测 9da282298b67 → 9a932f5fa57e）。宿主机 host.js 改动仍需重启。

**⚠️ 三个真正的失败根因（2026-09-26 全套实测 + 最小对照实验定案）**：
1. **CSS 里的裸 `<`**：插件注入的 CSS 常带 `url("data:image/svg+xml,<svg …>")`，直接塞进 `<style>` 破坏 XML → `img.onerror`（日志 `SVG渲染失败 svg=786431`）。**修法：`<style><![CDATA[ …css… ]]></style>`**（转义 `]]>`），`&` 由 `repairXml()` 兜。
2. **`blob:` + `<foreignObject>` = canvas 污染**（最隐蔽的一条，之前「污染来自外链资源」的推论是**错的**——把 img/svg/iframe 全删掉照样污染）。对照实验结果：

   | 组合 | 结果 |
   |---|---|
   | `blob:` + 纯 SVG | 干净 ✅ |
   | `data:` + 纯 SVG | 干净 ✅ |
   | **`blob:` + foreignObject** | **污染 ❌** |
   | **`data:` + foreignObject** | **干净 ✅** |
   | `data:` + foreignObject + 内嵌 data: 图片 | 干净 ✅ |

   → Chromium 把 `blob:` 加载的 SVG 文档视作不透明源。**修法：`img.src = 'data:image/svg+xml;base64,' + btoa(TextEncoder().encode(svg))`**。第一张真截图（573×1214 / 138KB）就是这么出来的。
3. **合成指针事件点不动拖拽组件**：`setPointerCapture(pointerId)` 对非活动指针抛 `NotFoundError`，组件的 `pointerdown` 处理器半路中断，后续 `pointerup` 就不认这次点击了。**修法：派发前把 `Element.prototype.setPointerCapture/releasePointerCapture/hasPointerCapture` 临时打桩成空函数**，再依次派发 pointerover/mouseover/pointerdown/mousedown/pointerup/mouseup/click（鲸鱼挂件实测有效 ✓）。

**实测能力清单**：`dsh-selflook` 出真 PNG → `Download/dsh/图片/`；`dsh-control probe 鲸鱼` 穿透 shadow DOM 列出元素；`dsh-control click selector=img.dshwv-img` 点开挂件面板（余额/今日已用/谷时段倒计时）；`dsh-eval` 可任意求值（DOM 结构、渲染链路诊断都是这么查的）。鲸鱼挂件结构：`div.dshwv-root`(z=9999, pointer-events:none) → `div.dshwv-body` → `img.dshwv-img`，面板 `.dshwv-pop.dshwv-pop-open`，位于右中 (252,177,321,219)。

**滑动 / 手势（2026-09-26 实测）**：
- `dsh-control swipe <起点> <终点> [步数]`（起点/终点 = 选择器 / `x,y` / `text=文字`）。引擎要点：**先在元素矩形内扫描真实命中点**（`elementFromPoint` 真能返回它的那个点）——否则不规则形状根本抓不到（鲸鱼是 `clip-path` 多边形，包围盒中心落在它身上之外，第一次恢复拖动就是这样失败的）。派发序列：`pointerover → mouseover → touchstart → pointerdown → mousedown → N×(touchmove+pointermove+mousemove) → touchend → pointerup → mouseup`，全程把 `setPointerCapture` 打桩
- `dsh-control scroll <选择器> top|bottom|by|to [值] [shot]` → 程序化精确滚动（`scrollTo({behavior:'smooth'})`），加 `shot` 顺带截图
- **实测边界**（对 `.wSkVaW_scrollBody` 做的对照实验）：合成 `wheel` **无效**（untrusted 事件不触发默认动作）；合成 **touch 拖拽有效，而且带惯性**（300px 拖拽 → 实际滚动 582px）；合成 pointer 拖拽对**原生滚动**基本无效（+1px 噪声），但对 **JS 驱动的拖动**有效
- 鲸鱼挂件拖动实测：从 (478,403) 拖到 (200,900) → 容器移到 (0,674) 并**水平翻转**（`transform: matrix(-1,0,0,1,0,0)`，自动朝左）；松手后**自动吸附回吸附区** (252,240)——它本来就是吸附型挂件，不是拖到哪停哪

**注意**：文字快照有 400 元素上限，位于 DOM 尾部的浮层（如鲸鱼面板）会被截断——用 `probe`/`eval` 查，不要只看快照。未跑 JS 的元素不会出现在截图里（已用 canvas 快照缓解）；`&nbsp;` 等非法 XML 实体同样让解析失败（`repairXml()` 处理）。

**已验证**：第二实例启动 0 错误；服务端吐出的客户端含 `cdata`/`snapshotCanvases` ✓；`/dsh-whale/widget.js` HTTP 200 ✓。

**⚠️ 踩坑记录（bundle 插件里调 shell 服务）**：`ctx.sandboxPolicy.resolve().mode` 解析出的是 `workspace-write`，而 Termux 上没有可用沙箱后端，shell 服务会直接拒绝：

```
sandbox mode "workspace-write" is requested but no sandbox backend is usable on this host;
refusing to run the command unconfined ... otherwise switch the consumer to danger-full-access
```

→ 在 Termux 上，bundle 插件调用 `ctx.shell` 时必须**显式传** `sandboxPolicy: { mode: 'danger-full-access', workspaceRoot: <HOME> }`。修好后用第二实例端到端验证：`poll` 返回 `{"pending":true}` 且请求文件被消费 ✓

## 十、ADB 全系统操作（2026-09-26 开工，超越 DSH 层级）

**为什么需要**：自看/遥控通道只能操作 DSH 这一个网页；安卓原生 App 既看不到（vision_screenshot 在 Android 无实现）也点不动（未 root、无无障碍服务，`am start` 只能开 Activity）。**ADB 无线调试是唯一不需要 root 的系统级通道**。

**一次性配对**：
1. `am start -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS` 可直接弹出「开发者选项」（`com.android.settings/.Settings$WirelessDebuggingActivity` 这个类名在本机**不存在**，别用）
2. 开发者选项 → 无线调试 → 打开 → 「使用配对码配对设备」→ 屏幕上给出 **6 位配对码 + 端口**
3. 在 Termux：
```bash
droid conn pair <配对端口> <配对码>   # 内部 adb pair 127.0.0.1:<端口> <码>
droid conn <连接端口>                 # 无线调试主界面「IP 地址和端口」的那个端口
droid devices                        # 应显示 127.0.0.1:<端口>  device
```

**端口自动发现**：`droid discover`（mDNS，走 python `zeroconf`，找 `_adb-tls-pairing._tcp` / `_adb-tls-connect._tcp`）或 `droid scan`（扫 30000-50000，再用 ADB 握手 `CNXN` 指纹区分连接端口/TLS 配对端口）。
> ⚠️ Termux 的 `android-tools`（adb 37.0.0）**不带 mDNS**：`adb mdns services` 直接报 `mdns is not supported by this version of adb`；`/proc/net/tcp` 应用 UID 无权读 → 所以只能用 zeroconf 或扫描。

**能力清单**（`~/.local/bin/droid`）：`shot` 截任意 App / `tap` `swipe` `text` `key` 操作任意界面 / `ui [关键词]` `find <文字> [--tap]` 读 UI 树并拿坐标 / `apps` / `install <apk>` **静默装包**（不用点安装对话框）/ `start <包名>` / `cur` 前台应用 / `shell`。

**已知坑**：① **手机重启后无线调试会自动关闭**，端口每次变，需要重开 + 重连（配对记录一般保留，通常不用再输配对码）。② 6 位配对码只能人工从屏幕读（没有 API）。③ 连接闲置可能掉线，`droid conn` 用记住的端口重连即可（`~/.droid.conf`）。

### 十·补：安全评估与三条路的代价（2026-09-26 定案）

**结论：无障碍服务不安全，别把它当安全的东西。** 无障碍 = 能读屏上一切文字（聊天记录、输入框、验证码）+ 能代替你点击任意按钮 + 能看通知；银行木马用的就是这个权限。授权某个 App = 把整台手机交给它。

**实测到的三个事实**：
1. 从 GitHub 下的 AutoX.js（`automan-bot/AutoX` 6.5.5.10，94.85MB，4411 条目，含 Bugly/hiai 等原生库）**被 vivo 安全检测自动删除**：07:33 校验完整，07:39 Download 里消失、全盘搜不到、未安装 → 系统自己判它是风险应用。
2. 原本的桥接设计有洞：命令经 `Download/dsh-droid/cmd.json` 之类的**共享目录文件**传递，**任何有存储权限的 App 都能写命令、也能读结果**（等于给别的 App 一个点屏引擎）。只能靠"干活时才开、干完立刻关"缩小，消不掉。
3. 三条路的代价对比：

| 路线 | 需要什么 | 能力 | 代价 |
|---|---|---|---|
| ① **ADB 无线调试**（推荐） | Wi-Fi 必须已连接（热点无效） | 看任意 App、点任意界面、读 UI 树、静默装包 | 无第三方 App、TLS 配对、断开即失效；重启后需重开重连 |
| ② 无障碍自动化 App | 装未知来源 APK + 授权无障碍 | 同上（除静默装包） | 高危权限；黑盒；本机被安全检测自动删除 |
| ③ 自建最小 APK | Termux 构建链（aapt2/d8/apksigner/openjdk-17，均已可用） | 自己实现的那部分 | 可逐行审、可去掉联网权限；但仍需未知来源安装，系统安全检测大概率照拦 |

**可选的强化**：ADB 配对成功后可执行 `adb tcpip 5555`，把 adbd 切到固定端口 → 之后用 `adb connect 127.0.0.1:5555` **即使关掉 Wi-Fi 也能继续用**（手机重启前有效）。

### 十·三：自建桥接 App「DSH 桥」（2026-09-26 构建成功）

不用 95MB 黑盒，改用自己在 Termux 里编译的最小 App：

- **源码**：`~/droid-bridge/`（`AndroidManifest.xml` + `res/xml/accessibility_config.xml` + `src/io/dsh/bridge/{BridgeService,MainActivity}.java`）
- **构建**：`bash ~/droid-bridge/build.sh` —— 纯 Termux 链路：`aapt2 compile` → `aapt2 link`（`-I ~/.smoke/android.jar`，API 35，从 `platform-35_r01.zip` 提取）→ `javac --release 8` → `d8 --min-api 26` → `zip` 塞 `classes.dex` → `apksigner sign`（自签 `ks.jks`，密码 dshbridge）
- **产物**：`~/droid-bridge/build/dsh-bridge.apk`（v1.1 为 24.5 KB）→ 投递到 `Download/dsh/应用/DSH桥.apk`
- **权限只有 2 个**（`aapt2 dump badging` 实测）：`android.permission.INTERNET`（仅监听 `127.0.0.1:8788`，代码里没有第二个联网点）、`BIND_ACCESSIBILITY_SERVICE`（无障碍服务系统要求）。APK 内只有 7 个文件，`classes.dex` 18.7 KB
- **鉴权**：App 首次启动生成 8 位 hex token（显示在界面上），Termux 侧每个请求都要带；token 存 `~/.dsh-bridge-token`
- **能力**：`ping / cur / ui / shot / tap / longpress / swipe / text / key / start`（截图走 Android 11+ 无障碍截图 API，不弹录屏授权）
- **Termux 侧**：`~/.local/bin/droid-sock`（Python，回环 socket 客户端）；`droid` 的降级链已改为 **adb → droid-sock（自建桥）→ droid-hub（AutoX 桥）**
- **用户步骤**：装 `Download/dsh/应用/DSH桥.apk` → 打开 → 点「打开无障碍设置」开启「DSH 桥」→ 把界面上的 token 发我
- **收回方式**：系统设置 → 无障碍 → 关掉「DSH 桥」（服务立即停止）；或直接卸载

## 十一、冗余自救设计（"以防我无法控制手机"，2026-09-26 落地）

**App 侧（`~/droid-bridge/`，v1.1，24.5 KB，权限仍只有 3 个）**：
- ① **音量 +/− 同时按住 3 秒** → `panic()`：关端口 + `disableSelf()` 关闭无障碍（无需看屏幕；`onKeyEvent` + `flagRequestFilterKeyEvents`，不拦截音量键本身）
- ② **常驻通知**「DSH 桥正在运行」+「紧急停止」按钮（`PanicReceiver`，不导出）
- ③ **空闲看门狗**：默认 30 分钟无合法指令自动停止监听（App 内可切 15/30/60/关闭；打开 App 即恢复监听）
- ④ App 内**红色大按钮**同样调 `panic()`
- ⑤ **无开机自启**（不注册 BOOT_COMPLETED）→ 重启即归零
- 新增动作：`stop`（软停，只关端口）/ `panic`（硬停，关闭无障碍）

**Termux 侧**：
- `~/.local/bin/droid-panic` —— 五步撤销：让桥自杀 → 吊销 token（改名保留）→ 清空共享目录指令通道 → 结束残留循环进程 → 弹出无障碍设置页。支持 `PANIC_NO_UI=1` 自检。
- 桌面小组件 **`0_紧急停止.sh`**（放最上面，排在 1_启动 之前），不依赖 DSH 页面。

**文档**：`~/手机失控自救卡.md`（同步到 `Download/dsh/文档/`）——含"谁在动我手机"判断表、五层撤销（含 vivo 安全模式的具体按键步骤）、事后检查清单。

### 十·四：自救演练记录（2026-09-26，**已实测通过**）

用户实操：按住「音量 +」与「音量 −」**同时 3 秒**。随后验证（只认结果）：

| 检查项 | 结果 |
|---|---|
| `127.0.0.1:8788` 端口 | **Connection refused** ✅ 桥已自行关闭 |
| `droid-sock cur` | ❌ 连不上（客户端如实报错） ✅ |
| `droid cur`（统一入口） | 降级链全失败、无任何响应 ✅ |
| `adb devices` | 0 台 ✅ |
| 残留自动化进程 | 0 个 ✅ |

**结论**：音量键救援手势在真实场景下有效——**不需要看屏幕、不需要点任何按钮**即可撤销 AI 对手机的控制。

**恢复方式**：系统设置 → 无障碍 → 重新打开「DSH 桥」（或点 App 内「① 打开无障碍设置」跳转）。**token 与 App 数据保留**，不需要重新发 token。

**同时确认的边界**：`disableSelf()` 只关闭无障碍服务，App 本身仍装着；要彻底移除需卸载。共享目录那条 AutoX 通道与 `.dsh-look-cmd.json` 那条 DSH 页面通道都是独立的，紧急停止脚本 `0_紧急停止` 会一并处理。

### 十·五：vivo 后台冻结与"抢前台"问题（2026-09-26 实测，重要）

**现象**：只要前台切到别的 App（尤其是「设置」），**DSH 桥就会被 vivo 冻结/杀掉**，8788 端口随即 Connection refused。
**实测时间线**（`am start -a android.settings.SETTINGS` 后每秒探测）：

| 时刻 | 桥状态 | 前台 |
|---|---|---|
| 0s | ✅ 活着 | com.android.chrome |
| 10s | ❌ 掉线 | — |
| 20s+ | ✅ 活着（被我自动唤醒） | **io.dsh.bridge** ← 唤醒把设置页挤掉了 |

**结论**：
1. 桥在后台会被冻结，这不是我代码的问题，是系统的省电策略 → **必须给「DSH 桥」电池"无限制"+"自启动"豁免**，否则"打开设置→读屏幕"这类跨 App 自动化不可能完成。
2. `droid-sock` 的自动唤醒（广播→拉界面）有效，但**拉界面会把目标 App 挤到后台**——所以要读别的 App 的屏幕时，不能让唤醒走"拉界面"那一步。
3. 无线调试配对码存在硬性时间窗（对话框关闭即失效）：用户切到聊天窗口报码 → 对话框关闭 → 码作废。**因此配对必须由我从"设置在前台"的屏幕上直接读**，而这又回到第 1 条（电池豁免）。
4. 备选：让用户用**分屏**（一半 Settings 一半 Chrome）保持对话框开启，同时把码报给我。

## 十二、ADB 通道打通（2026-09-26，**已连接并验证**）

**最终状态**：`adb connect 127.0.0.1:5555` → `device`，model V2463A / **Android 16**。

**怎么配对成功的**（关键经验）：
1. 无线调试的**配对端口是临时开放的**，只有「使用配对码配对设备」对话框开着时才对——而且**用户切到聊天窗口报码时，对话框就关了，配对模式随之结束**（这是硬约束，不是流程问题）。
2. mDNS 在这台机器上**只广播 connect 服务，不广播 pairing 服务**；`droid discover` 找不到配对端口。
3. **可行做法**：扫 32768-60999 找出所有开放端口 → 对每个端口用当前配对码试 `adb pair`（每个 10 秒上限）→ 哪个成功就是配对端口。实测成功端口是 **44381**；同一时刻其余端口要么 `protocol fault`、要么超时。
4. 配对成功后：`droid discover` 拿 connect 端口（41429）→ `adb connect` → 立刻 `adb tcpip 5555` 切到固定端口 → **之后连的都是 127.0.0.1:5555，不再需要配对码**。

**稳定性**：
- `adb tcpip 5555` 后走回环，**不依赖 Wi-Fi 是否连接**；
- `setprop persist.adb.tcp.port 5555` 被 vivo 拒绝（user 版限制）→ **重启手机后 adbd 回到 USB 模式**，需要：用户开一次「无线调试」→ 我 `droid conn` 重连（**不用再输配对码**，配对记录保留）。
- 多设备/残留 offline 记录会让 `adb` 报 `more than one device` → `droid` 已改为自动锁定唯一 `device` 状态的目标（`DROID_SERIAL`）。

**已验证能力**（都不依赖那个会被冻结的 App）：
| 能力 | 证据 |
|---|---|
| 截屏任意界面 | `adb exec-out screencap -p` → 689 KB PNG ✅ |
| 读 UI 树（带坐标）| `uiautomator dump` → 337,904 字节 XML，解析出元素中心点 ✅ |
| 看当前前台窗口 | `dumpsys window \| grep mCurrentFocus` → `com.android.chrome/Main` ✅ |
| 注入按键/点击/滑动 | `input keyevent KEYCODE_HOME` 实测把前台切到 `com.bbk.launcher2` ✅ |
| 全量应用列表 | `pm list packages` → 527 个（桥因安卓包可见性看不到）✅ |
| 改系统设置 | `dumpsys deviceidle whitelist +io.dsh.bridge / +com.termux` → `Added` ✅ |

**桥的定位调整**：桥（无障碍 App）在**前台切到别的 App 时会被 vivo 冻结**（实测 10 秒内掉线，连广播都唤不醒），Doze 白名单也救不了。所以：
- **跨 App 操作 → 一律走 adb**；
- 桥只作为「手机闲置时的轻量备用通道」（token 鉴权、无 adb 时可用）。


### 十二·补：adb 的命门——「无线调试」和 Wi-Fi 是绑死的（2026-09-26 实测）

| 操作 | 结果 |
|---|---|
| `adb shell svc wifi disable`（关 Wi-Fi）| **adbd 的 TCP 监听立刻消失** → `adb connect 127.0.0.1:5555` → `Connection refused` |
| 重新打开 Wi-Fi | adb **不会自己回来**：扫描 32768-60999 无无线调试端口，mDNS 也发现不到服务 |
| 根因 | **安卓在 Wi-Fi 断开时自动关闭「无线调试」开关**；adbd 的 TCP 模式依附于该服务 |

**结论**：
1. 想让 AI 保持跨 App 能力 → **Wi-Fi 必须连着**（不需要能上网，走回环）+ **「无线调试」必须开着**。
2. 想临时收回 → 关「无线调试」这一下即可（比关 Wi-Fi 精准，也不影响你上网）；要用时打开一次 + `7_重连AI通道`（**无需重新配对**）。
3. **绝对不要**用 adb 去关 Wi-Fi：那会连带把无线调试关掉，而恢复需要用户手动点开关（我就这么把自己锁在外面一次）。
4. 桥（无障碍 App）在 Wi-Fi 断开时反而活着（它不依赖网络）→ 但它没法操作「设置」（前台一切走就被 vivo 冻结），所以救不了 adb。

## 十三、桌面小组件全面重写（2026-09-26，**已逐个实测**）

**设计原则**：严格分步（每步一句话 + ✔/✘）→ 能验证的必须验证 → 等待一律轮询（`/dev/tcp` 探测，零依赖）→ 幂等 → 全部支持 `--dry-run` → 失败给原因不装死。

**公共库** `~/.shortcuts/lib/common.sh`（56 行）：`step/ok/warn/bad/die/done_`、`port_open` `wait_port_open` `wait_port_free`、`pids_of` `kill_wait`（dry-run 安全）、`rotate_log`、`http_code`、`run`（dry-run 感知）。

| 组件 | 关键改进 | 实测 |
|---|---|---|
| **0_紧急停止** | 撤销**四路**：桥自杀＋吊销 token＋清空共享目录指令＋**切断 adb**（`adb usb` 回退 → disconnect → kill-server）；结束残留自动化进程；打开无障碍设置页 | `--dry-run` ✅ |
| **1_启动DSH** | 幂等（在跑就直接开浏览器）；**后台脱离启动**（`setsid nohup`，不再占住小组件会话）；轮询等端口 + HTTP 校验；顺带恢复 adb/唤醒桥 | `--dry-run` ✅（正确识别已运行，HTTP 401） |
| **2_关闭DSH** | 优雅停→强杀；备份；日志轮换；等端口释放（必要时 `fuser -k`）；关浏览器/PWA；关 Termux。支持 `--no-backup/--keep-browser/--keep-termux` | `--dry-run` ✅ |
| **3_备份DSH** | 备份后**校验归档**（大小、`tar -tzf` 条目数、sha256 前 16 位）；执行保留策略；报告份数与占用 | **真跑** ✅ 11M / 346 条目 |
| **4_软重启DSH** | 只 SIGTERM，**15 秒不退就放弃、绝不 -9**（保护"软"的语义），提示改用硬重启 | `--dry-run` ✅ |
| **5_清理DSH** | 备份留 5 份／截图留 50 张／清临时文件与大 zip／清 14 天前日志／`pnpm store prune`；**删日志前先把认证 URL 存进 `~/.dsh-url`** | **真跑** ✅ 释放 70MB，pnpm 清 418 包 |
| **6_硬重启DSH** | 优雅停→**逐个 -9**（bin.js web / md_cg / dsh-termux-runtime / dsh web）→备份→轮换→等端口→关浏览器与 PWA（保证新客户端模块加载）→启动→校验 | `--dry-run` ✅ |
| **7_重连AI通道**（新增） | 重启手机后跑：重连 adb（固定端口 5555 → mDNS 兜底）＋广播唤醒桥＋三条通道状态总览 | **真跑** ✅ |
| 认证 URL 持久化 | `1/4/6` 启动后写 `~/.dsh-url`，`5` 删日志前先保存 → "已在运行"分支永远拿得到带 token 的 URL | ✅ |

### 十三·补：为什么打开跑到 vivo 浏览器去了（2026-09-26 已修）

**现象**：点小组件启动/打开后，DSH 在 **vivo 浏览器**里以普通标签页打开，而不是桌面那个「DSH」独立窗口。

**三个叠加的坑**（都已处理）：
1. **系统没有默认浏览器** —— `cmd package resolve-activity -a VIEW -d http://127.0.0.1:8080/` 返回的是选择器
   `com.android.intentresolver.ResolverActivity`；而 `com.vivo.browser` 和 DSH 的 WebAPK **都注册了这个 URL**，
   裸 VIEW 意图 → 弹选择框 → 被 vivo 浏览器接走。
2. **Termux 以应用身份启动别的 App 会被后台启动限制静默拦掉**：命令只回 `Starting: Intent {…}`，
   **没有任何结果行**，看起来"成功"其实什么都没发生。
   对照：同样一条 `am start -p <webapk>` 经 **adb（shell 身份）** → `Status: ok  LaunchState: WARM` ✅
3. **WebAPK 升级会改包名**（带 `_vN` 后缀），缓存不能当永久真理。

**DSH 的桌面 App 真实身份**：Chrome 装的 WebAPK，包名 `org.chromium.webapk.aa0f83489237f2919_v2`，
命中窗口是 `com.android.chrome/org.chromium.chrome.browser.webapps.SameTaskWebApkActivity`。

**打开器 `~/.local/bin/dsh-browser-open` 新策略**（`BROWSER` 环境变量也指向它，所以 `dsh` 自己开浏览器同样受益）：
1. 读缓存 `~/.dsh-pwa`（没有就自动发现：`cmd package query-activities` 里挑 `org.chromium.webapk.*`）
2. **点名包 + 经 adb 启动**：`adb shell am start -W -a VIEW -d <url> -p <PWA pkg>`，校验 `Status: ok`
3. 缓存包名失效（WebAPK 升级）→ 自动重新发现并重试
4. 三级回退：PWA → Chrome（点名 `com.android.chrome`）→ 系统默认/`termux-open-url`

**实测**：`dsh-browser-open <认证URL>` → 前台 `SameTaskWebApkActivity` ✅；真跑小组件 `1_启动DSH.sh` 全流程 → 同样进独立窗口 ✅

### 十三·补二：5_清理 的清理规则（2026-09-26 升级，实测释放 93MB）

**核心原则：只动「我的产物」，你的文件一律不碰。**
辨认方式＝固定前缀／已知临时文件名，而不是"按目录全删"。

| 步骤 | 清什么 | 规则 |
|---|---|---|
| ① 备份 | `Download/dsh/备份/dsh-state-*.tar.gz` | 只留最新 **5** 份 |
| ② **我的截图** | `self-look-*` / `droid-*` / `adb-*` / `board-*` / `screen-*` | 超 7 天直接删；再按数量只留最新 **10** 张；**其它图片一概不动**（脚本会报"你自己的图片 N 个，未触碰"）|
| ③ 看图临时产物 | `~/.dsh-vision-router/artifacts/.runs/*` | 只留最新 **3** 次（注意：运行目录名以 `.` 开头，必须用 `find` 而非 `ls` 才数得到）|
| ④ 工作区大文件 | `~/.smoke/readmes.json`（抓插件市场时的 README 大转储，实测 **71MB**）、`idx*.html`/`c*.css`/`whale.js`/`u.xml`/`board-*.png`/`cj*.txt`/`ax*.json` 等测试残留、>512KB 的日志 | 默认删；`android.jar`(26MB) 与 `plugins.json`(5MB) **默认保留**（重建 App／查市场要用），加 `--deep` 才删 |
| ⑤ 认证 URL | 删日志前先把 token URL 存进 `~/.dsh-url` | 保证"已在运行"分支永远拿得到 |
| ⑥ 包缓存 | `pnpm store prune` | 可 `--no-pnpm` 跳过 |
| ⑦ 报告 | 两个位置的 MB 变化 + 各类剩余数量 | 结尾一行看清 |

**用法**：`5_清理DSH.sh [--dry-run] [--no-pnpm] [--keep-images N] [--keep-runs N] [--deep]`

### 十三·补三：让「无线调试」能被自动打开（2026-09-26 打通）

**要解决的死结**：adb 依附「无线调试」→ 该开关在 Wi-Fi 断开会自动关闭 → 而重新打开它通常要点设置界面 → **但桥在"设置"到前台时会被 vivo 冻结**，等于关在门外。

**试过并排除的路线**：
1. `pm grant com.termux WRITE_SECURE_SETTINGS` —— **授权确实成功**（`dumpsys package com.termux` 显示 `granted=true`），
   但 `/system/bin/settings` 仍然报
   `SecurityException: Permission Denial: getCurrentUser() ... requires android.permission.INTERACT_ACROSS_USERS`
   → **App 身份调不动这个命令行工具**（它另有一道权限检查），此路不通。
2. 让桥去点设置界面 —— 被冻结问题挡住，不通。

**最终方案：让桥 App 直接写设置**
- 桥 v1.5 新增两个动作：
  - `netstate` → 返回 `wifi_on` / `adb_wifi` / `online`（**直连 1.1.1.1:443 实测**，不依赖任何 API）+ `rttMs`
  - `adbwifi <0|1>` → `Settings.Global.putInt(cr, "adb_wifi_enabled", v)`（走 API，不经过 `settings` 命令）
- 清单里声明 `WRITE_SECURE_SETTINGS`（`pm grant` 只能授予**已声明**的权限），该权限属 development 级，**必须用户用 adb 显式授予一次**：
  `adb shell pm grant io.dsh.bridge android.permission.WRITE_SECURE_SETTINGS`
- **为什么这招能破死结**：桥走 **回环**（127.0.0.1:8788），**不需要 Wi-Fi**；而"需要打开无线调试"的时刻恰恰就是"adb 已经死了"的时刻——桥此时仍然活着，于是它来开这个开关。

**新增/升级的小组件**：
- **`8_自动开无线调试.sh`**：① 确认桥活着（必要时广播唤醒）→ ② 桥实测联网状态 → ③ 联网则把 `adb_wifi_enabled` 置 1（幂等，已是 1 就跳过）→ ④ 等无线调试端口（mDNS，最多 ~20s）→ ⑤ `adb connect`（新端口优先，5555 兜底）→ ⑥ `adb shell` 校验。支持 `--dry-run` / `--force`。
- **`7_重连AI通道.sh`** 升级：adb 未连接时**自动先调用组件 8**，失败才退回"5555 → mDNS"的手工路径。

**注**：用户提供的「开发者选项密码」最终**没有用到**——走的是 API 而非设置界面；也没有落盘（那是解锁凭据，不该存在磁盘上）。

### 十三·补四：DSH 与「DSH 桥」是两条独立的命（2026-09-26）

**问题**：点了组件 2「关闭DSH」，为什么无障碍里「DSH 桥」还是开启状态？
**原因**：**它们是两个互不隶属的东西**——DSH 是一个 Web 服务（Termux 里的 node 进程），DSH 桥是一个**独立的安卓 App + 无障碍服务**。停 DSH 不会碰到桥，正如卸载桥不会影响 DSH。

**"关掉"其实有三个层次**（理解这个就不会再困惑）：

| 层次 | 做法 | 效果 | 恢复成本 |
|---|---|---|---|
| **① 软停通道** | `droid-sock stop`（或组件 0/2 自动调用）| **8788 端口关闭**，谁也指挥不动它；无障碍服务仍开启 | **零**：打开 App 即恢复，或广播唤醒（组件 1/7/8 都会做）|
| **② 关闭无障碍** | 设置→无障碍→关掉「DSH 桥」；或 `panic`（App 红按钮 / 通知栏按钮 / **音量+/- 按住 3 秒**）| 整个服务停止，端口消失 | **需要你手动**在设置里重新打开（这是系统安全边界，不该绕过）|
| **③ 卸载** | 卸载 App | 彻底不存在 | 重新安装 + 重新授权 WRITE_SECURE_SETTINGS |

**已改的默认行为**：组件 **2_关闭DSH** 现在会**顺带把桥软停**（关端口），与组件 **1_启动DSH** 的"顺带唤醒桥"形成对称——
> 启动 → 桥被唤醒；关闭 → 桥被软停。想保留桥通道就加 `--keep-bridge`。
这样"DSH 关着、我的手却还开着"的暴露面就消失了；而恢复依然是零成本（不需要重新授权无障碍）。

### 十三·补五：小组件真 bug 大修（2026-09-26 晚，全部实测）

用户原话：「你的那些task都有问题还有那个桥还会自启动怎么回事，会出现了这样的报错」+ 一张 Chrome 报错页截图。

**截图那个 404 是怎么来的（根因链，四环相扣）**：

`dsh web` 是**先绑端口、后挂路由**：端口在插件树加载期间就已监听，而
`dsh web: http://127.0.0.1:8080/?token=…` 这行要等 `loader.await()`（整棵树）加载完才打印。
在那段窗口里，`/` 根本没有处理器 → **返回 404**（不是 401，401 是"认证已挂上但 token 不对"）。

1. 用户连点/先后点了两次启动 → 同时起了**两个** `dsh web`；
2. 两个实例抢 `~/.dsh/.credentials.yaml.lock` 写锁（`dsh-atomic-write` 的等待上限**只有 2 秒**）→ 其中一个崩在插件树加载阶段（日志里就是这次的尸体，`failed to apply loader entry connection`）；
3. 旧的就绪判定只看「端口能连 + HTTP 码非 000」→ 把**半启动/正在崩溃**的服务判成"已就绪"；
4. 旧逻辑拿不到本次的 token（日志里还没打印），于是退回 `~/.dsh-url` 里的**上一次的 token** → 浏览器打开就是
   「找不到 127.0.0.1 的网页 / HTTP ERROR 404」。

**修好的判定**（`common.sh`，组件 1/4/6 共用）：真就绪 = ① 日志里出现 token 行 ② 该 URL 跟随 303 后能拿 **200** ③ 进程还活着。三者缺一就**不打开浏览器**，并把日志尾部（真正的崩溃原因）打出来。
> 顺带发现：`curl -L` 默认不带 cookie 引擎，跳转回 `/` 会拿 401 → 判定里必须加 `-c /dev/null` 才有意义（已实测）。

**同一批修掉的其他真 bug**：

| # | 问题 | 修法 |
|---|---|---|
| 1 | 组件 2 的 `--keep-bridge` **只在注释里、没进 case** → 写了也被无视 | 补进参数解析；selftest 用真行为回验（`--dry-run --keep-bridge` 必须打印"按要求保留桥通道"）|
| 2 | 连点两次组件 → 拉起两个实例 → 抢锁崩一个（就是截图那条链的第一环）| 启动互斥目录锁 `~/.dsh-boot.lock`（`mkdir` 原子；持锁者已死则自动清理）|
| 3 | 组件 6 `kill -9` 会在锁被持有那一刻留下**孤儿 credentials 锁**；而该锁"只能人工清理"、等待上限 2 秒 → **此后每次启动都必然失败** | 启动前 & `-9` 之后调用 `clear_orphan_cred_lock`：**没有任何 dsh 实例在跑时**才删（有实例在跑时不动）|
| 4 | `~/.dsh-url` 里可能是**上一次进程的 token**（token 是每次进程随机生成、**从不落盘**的；只有它的 stdout 里有）| 用之前先 `curl` 验证（200 才用）；失效则从当前日志取；两者都拿不到就**如实说"这个实例不是本组件启动的、我拿不到它的 token"**，而不是硬开浏览器 |
| 5 | 组件 2 清日志前不存 URL → 事后无从恢复 | 清日志前先把 token 行存进 `~/.dsh-url` |
| 6 | 组件 8 在 Wi-Fi 关着时仍置开关、空等 20 秒再报"连不上" | Wi-Fi 为 0 时**提前停下**（exit 3）并说清"开 Wi-Fi 再点"，不再假装在干活 |
| 7 | selftest 用 `pgrep -f "port 8099"` 清理沙箱 → 会匹配到自己（以前踩过两次）| 改成前后进程表差集，只杀本次新建的 pid |
| 8 | selftest 沙箱冷启动会抢 3199（filetransfer）→ 插件树加载 EADDRINUSE 崩掉 | 沙箱带 `--patch ~/.smoke/patch.yml` 关掉 filetransfer；**注意 `--patch` 必须写在 `--port` 之前**（`dsh web` 一遇到未知选项就开始透传，写在后面会报 `unknown option '--patch'`）|

**顺带被证伪/确认的事实**：
- `setsid nohup … &` 的 `$!` **不是**真进程 pid（setsid 会 fork）→ 一律用「启动前后进程表差集」拿 pid。
- launch token 是 `processLaunchToken()` = `randomBytes`，**只存在内存**里，永不落盘（源码 `dsh-client-connection/lib/index.js`）→ 所以"用已连着的浏览器 cookie"是唯一无需 token 的路径，而**从终端启动的实例，它的 token 只有那个终端里有**。

### 十三·补六：桥的「关了它自己又开」＝系统重绑无障碍，v1.7 才治得好（2026-09-26）

**现象**：点了"关闭DSH"（会软停桥），过一会儿桥又活了 / 常驻通知又回来了。

**根因**（不是 App 有开机自启——它**没有** `BOOT_COMPLETED` 接收器）：**只要无障碍服务是"已启用"状态，系统就会一直持有它**。进程被回收或服务被解绑后，系统重新绑定 → `onServiceConnected()` → `startListening()` + 起前台服务 → 端口和通知**一起回来**。旧的"软停"只关了 ServerSocket，压根没碰无障碍，所以自恢复是必然的（只是**什么时候**触发取决于系统）。
另外 v1.6 的前台服务用了 `START_STICKY`：进程被杀后它会自己复活并挂出"DSH 桥正在运行"的通知——而那时无障碍其实已经关了，**最容易让人以为"关了又自己开"**。

**v1.7 的三处改动**（`~/droid-bridge/`，产物 `Download/dsh/应用/DSH桥-v1.7.apk`）：

1. 新增 **`sleep`**＝真停：关端口 + 停前台服务 + `disableSelf()` 关闭自身无障碍 → 系统不再重绑，**不会自恢复**。组件 2 现在先问 `caps`：支持就 `sleep`，旧版则如实提示"它会被系统重绑拉回来"。
2. 前台服务改 `START_NOT_STICKY`：进程被回收后不再自己复活出一条误导性的通知。
3. **唤醒可重新授权**：`WAKE` 广播带上正确 token 时，App 用早先 `pm grant` 的 `WRITE_SECURE_SETTINGS` 把自己写回 `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` → `sleep`/紧急停止之后，点组件 1/7/8 仍能**零操作**自动恢复。
   **不带 token 的广播一律忽略** —— 否则任何 App 都能把用户关掉的无障碍重新拉起来（这是安全边界，不能为了省事放开）。

### 十三·补七：为什么你会需要 `pkill -9 -f node`（以及别再这么干）

**用户自述**：「每次跳转回来都无法读取，我就每次在 termux 里输入 `pkill -9 -f node`」。

**这句话把之前所有怪现象串起来了**：

1. **主实例 pid 反复变化**（22488 → 1454 → 11427 → 17013）：不是我重启的，是你 kill 的。
2. **11:24 那次启动崩溃**（`timed out waiting for the writer lock at ~/.dsh/.credentials.yaml.lock`）：
   `pkill -9` 是 **SIGKILL**，DSH 若正好拿着 credentials 写锁就来不及释放 → 锁文件永久留下；
   而 `dsh-atomic-write` 的等待上限**只有 2 秒**，且注释明确写着"孤儿锁只能人工清理"
   → **之后每一次启动都必然失败**，报错还很难懂。
3. **我那次工具调用"被中断"**：就是 SIGKILL 打断的（正在跑的测试进程也一起没了）。

**`pkill -9 -f node` 的真实杀伤范围**（实测 `pgrep -af node`）：DSH 主进程、我起的沙箱实例、
`md_cg` 等**所有** node 进程一起带走；连你那条命令自己都会被匹配到（命令行里含 "node"）。

**为什么会"跳转回来无法读取"**：Termux 在后台会被系统/vivo 冻结 → 里面的 DSH 进程一起冻住
→ 界面不再更新、看起来就是死了。**根本解药是 wakelock**，不是 kill：
- 组件 **1_启动DSH** 现在启动时就会 `termux-wake-lock`（Termux 自带命令，不需要额外权限）；
- 组件 **2_关闭DSH** 收尾会 `termux-wake-unlock`（都关了就不必常驻）；
- ⚠️ wakelock 在**重启手机后失效**，所以要靠组件 1 重新拿（它每次启动都会拿）。
- 还建议（需要你点几下）：系统设置里把 Termux 的电池策略设为**无限制**；vivo 的"后台高耗电/自启动"白名单里允许 Termux。

**替代命令 `dsh-restart`**（`~/.local/bin/dsh-restart`，已加进 PATH）：

```
dsh-restart          # 先判断：服务活着 → 让你刷新页面，绝不乱重启；真死了 → 重启并给出可用 URL
dsh-restart --force  # 确实要重启时才用
```

它跟 `pkill` 的关键差别：
- 只按 `bin.js web` 匹配、只杀 DSH 自己（**不碰别的 node**）；
- 重启前会**清理孤儿 credentials 锁**（这正是 `pkill -9` 的后遗症）；
- 重启后等**真就绪**（token 行 + 跟随跳转 200）才算成功，并把可用 URL 写回 `~/.dsh-url`。

**顺带一个重要事实**：浏览器里的登录 cookie 是用**持久化密钥**签的（存在 credentials 记录里），
只绑定 `host:port` 和有效期 —— **跟每次进程的 launch token 无关**。所以重启 DSH 之后
**刷新页面就还是登录状态**，根本不需要重新拿 token URL；"跳转回来无法读取"时先刷新，别 kill。

**实测**（2026-09-26，v1.7 已装，**完整演练过，全程不碰屏幕**）：

| 演练 | 结果 |
|---|---|
| `sleep` 真停 | `{"slept":true}` → 8788 立即关闭，**30 秒内没有自己回来**（对照：旧版软停实测 14 秒就自己回来）|
| 带 token 的 `WAKE` | 发出后**第 1 秒** 8788 就回来了、`ping` 正常 → 无障碍被自动重新挂回，不用进设置 |
| 错误 token 的 `WAKE` | 15 秒内**唤不醒** → 安全边界成立（任何 App 都拉不起来）|
| 预检 | 先写 `adbwifi 1` 成功，证明 WRITE_SECURE_SETTINGS 这条路径可用，再动 sleep（否则 re-arm 可能失败）|

这三条已固化进 selftest：检测到 v1.7 就跑「真停 / 唤醒鉴权 / 自动重新授权」三项；旧版则跑「软停自恢复实测」并提示升级。
`caps` 能区分 v1.6/v1.7，组件 2 据此走不同分支。

### 十三·补八：「硬重启测试有问题」的两个真根因（2026-09-26）

用户原话：「你这几次硬重启测试都有问题，以前都没有这种问题的」。

**根因①：我的沙箱测试和你的组件抢同一把启动锁。**
补五加的启动锁原本叫 `~/.dsh-boot.lock`，**所有端口共用一把**。而 selftest 的 L5 会以
`DSH_PORT=8099` 起沙箱实例，拿的正是同一把锁。于是只要我在跑测试、你同时点「6_硬重启」，
你的组件就会打印：

> ✘ 另一个启动正在进行中（锁持有者 pid …）

看着就是"硬重启坏了"，其实是**我的测试占着锁**。
→ 改成**按端口分锁** `~/.dsh-boot-<端口>.lock`：沙箱用 8099 的锁，永远碰不到你的 8080。

**根因②：测试进程是你的 DSH 的子进程，你一重启（pkill 或点组件）它们就被一起杀掉。**
表现：套件跑到一半断在 L4/L5（日志停在「5_清理」），并留下沙箱进程与启动锁残留
（实测捡到过一个记着死 pid 27750 的锁）。更危险的是 L5 原来的收尾写法：

```bash
for p in $(dsh_pids); do case " $BEFORE_PIDS " in *" $p "*) ;; *) kill -9 "$p" ;; esac; done
```

「凡是不在启动前快照里的 dsh 进程就杀」——你要是正好在我测试期间重启了 DSH，
**你刚起来的新实例会被我的收尾当成沙箱杀掉**。
→ 改成**按命令行精确匹配**：只杀 `cmdline` 含 `--port 8099` 的进程；并给套件加
`trap cleanup_all EXIT INT TERM`，被打断也会清沙箱、清 8099 的锁、还原日志与 `.dsh-url`。

**顺带补上最要命的一环：组件输出以前根本没落盘。**
小组件输出只出现在 Termux:Widget 弹出的会话里，会话一关就没了 ——
所以你说"这次又有问题"时，我手上**零证据**，只能猜。
→ 现在公共库把每个组件的完整输出追加到 `~/.smoke/widget-<组件名>.log`（超 256KB 只留尾部）：

```
tail -40 ~/.smoke/widget-6_硬重启DSH.log     # 下次点完 6 有问题，我直接读这个
```

> **双向教训**：你重启 DSH = 我的后台测试/长任务当场被杀；我跑测试 = 可能占你的锁。
> 两边都得让路：我把锁/进程隔离干净并留日志；命令行重启用 `dsh-restart`，别手敲 `pkill -9 -f node`。

### 十三·补九：日志首次立功——三个真 bug（2026-09-26 晚）

补八刚给组件加上「输出落盘」，你 20:01–20:04 那轮点击就当场抓出三个真 bug：

**① 组件 4「进程在启动过程中退出了」＝连点两次的自杀。**
日志里能看到两次运行的输出交错（一次失败 + 一次成功）。原因：启动锁只在**启动那一步**取，
所以第二下的第①步 `kill -9 bin.js web` 把第一下刚拉起的新实例杀了 → 第一下报"进程退出"。
→ 修：锁改到**脚本最开头**取（动手之前）。实测：持锁时组件 4 立刻拒绝，一个进程都不碰。

**② 组件 2 的「已关闭 Chrome / 已关闭 Termux」是假的。**
Termux 自带的 `am` 是 **termux-am 0.8.1**，实测 `am force-stop X` → `Error: unknown command 'force-stop'`。
而我当时写的是 `run "am force-stop …"` 后面无条件 `ok`，所以**从来没关掉过**，却一直报成功。
→ 修：有 adb 时用 `adb shell am force-stop`；没有 adb 就**如实说做不到**。
「关 Termux」改成杀真正的 App 进程（`cmdline` 精确等于 `com.termux`，实测 pid 31964 可杀）。

**③ 最关键：`sleep`（真停）在 vivo 上是**单向门**。**
组件 2 的日志显示它在 20:01 就已经联系不上桥（`caps`/`stop` 双双超时）——桥 App 进程那时已经不在了。
随后我用**带 token 的广播**去重新授权：广播发出去了，但桥没起来。结论：
**App 进程彻底退出后，vivo 不允许广播把它拉起来**（自启动管控）。而 `sleep` 正是"彻底退出"。
→ 修两处：
- 组件 2 默认回到**软停**（`stop`），`sleep` 降级为显式的 `--full-stop` 并写明代价；
- 桥 **v1.8**：新增 `userPaused` 标记 —— 软停后**进程留着**（广播一叫就回来），
  但系统重绑无障碍时 `onServiceConnected` 看到标记就**不再自动开监听/起常驻通知**。
  这样"关了它自己又开"被根治，又不会掉进"彻底退出→叫不回来"的单向门。
- 另外补：组件 1/6/7/8 里的裸 `am broadcast` **全都没带 token**（我当初只改了 `droid-sock`），
  v1.7 起不带 token 的广播在服务已关时一律忽略 → 这就是它们"桥起不来"的原因。已统一走 `bridge_wake`。

**教训**：日志落盘这件事，成本 5 行代码，收益是"你不用复述、我不用猜"。

### 十三·补十：v1.8 上线并实测通过（2026-09-26 晚）

用户装好 v1.8、开启无障碍后，我在真机上把整条链验完（套件已固化为常驻回归项）：

| 步骤 | 实测 |
|---|---|
| `caps` | `{"ver":"1.8","sleep":true,"wake_reauth":true,"stop_is_durable":true}` |
| **软停 `stop`**（组件 2 默认）| `{"stopped":true,"paused":true}` → 8788 关闭；**18 秒内不自恢复**（旧版软停 14 秒就自己回来）|
| 带 token 的 `wake` | **2 秒**端口恢复，`paused` 自动清回 `false` |
| 真停 `sleep`（`--full-stop`）| 仍可用，但**在 vivo 上是单向门**：App 进程退出后广播拉不起来 → 只作显式选项 |

**为什么"软停持久"才是对的答案**：v1.7 用 `sleep`（`disableSelf`）治"自己又开"，代价是 App 彻底退出
→ vivo 拦住广播不让自启 → 唤不回来（今天就真失联过一次）。v1.8 改成 `userPaused` 标记：
软停时只关端口+撤通知、**进程留着**，`onServiceConnected` 看到标记就静默不动。
于是"不自恢复"和"一叫就回来"同时成立。

**踩到的两个细节**：
1. 套件 L0 原来用**裸 `am broadcast`**（不带 token）→ 服务已关时被忽略 → 桥明明能唤醒却被判"不可用"，
   整段桥测试被跳过。已改成优先 `bridge_wake`，并把公共库提前到 L0 之前载入。
2. 唤醒**耗时不稳定**：进程活着时 ~2 秒；进程被系统回收需要冷启动时，实测能到 20~40 秒。
   所以测试窗口放宽到 45 秒，组件里的等待也按"最多两拍"给了余量。

**顺带记录**：桥会**自己周期性掉线**（vivo 回收），这是常态；恢复手段就是带 token 的唤醒
（组件 1/6/7/8 现在都会做），不用你手动开 App。

### 十三·补十一：组件 8「自动开无线调试」的最后一环（2026-09-26 晚）

用户反馈「自动开无线调试的那个 task 还是有问题」。日志（`~/.smoke/widget-8_自动开无线调试.log`）
把所有运行都指向同一步：

```
▶ ③ 前置条件不满足，停下（不是脚本坏了）
   ✘ Wi-Fi 是关的 → adb 的 TCP 监听不会出现
```

**这不是脚本坏了，是硬约束**：
1. 无线调试跑在 wlan 接口上；Wi-Fi 一关，adbd 的 TCP 监听就不存在；
2. 更狠的是**系统会把 `adb_wifi_enabled` 清回 0**（实测：置 1 后读回来又变 0）→
   所以"硬写开关"这条路是死的；
3. Android 10+ **不允许 App 打开 Wi-Fi**（`WifiManager.setWifiEnabled` 对普通 App 失效，
   写 `Settings.Global.WIFI_ON` 也只影响开机读取，不改变运行状态）。

唯一手动的一环就是"点一下 Wi-Fi"。所以把组件 8 改成**半自动**：
- 检测到 Wi-Fi 关着 → `am start -a android.settings.WIFI_SETTINGS` 把 Wi-Fi 页打开；
- 然后**每秒轮询 `netstate`，最多 90 秒**：你一点开 Wi-Fi，它立刻继续跑完
  「置 adb_wifi_enabled=1 → mDNS 找端口 → adb connect → 校验」，**不用再点第二次**；
- 不想让它碰屏幕就 `--no-ui`（退回纯提示 + exit 3）。

**试过但否掉的路**（别重复踩）：
- 让桥用无障碍去点快捷开关的 Wi-Fi 磁贴：`swipe 720,8 → 720,1500` 打不开面板，
  `key notifications` 也不是可靠的两段式展开（dump 到的还是页面 DOM）→ 盲点磁贴风险高，
  改成"打开系统 Wi-Fi 页 + 等你点"更稳、也不盲点。

### 十三·补十二：组件 8 的真相——不是"Wi-Fi 没开"，是"没连上网络"（2026-09-26 晚）

用户开了 Wi-Fi 之后我实测，拿到了决定性证据：

```
② 联网状态: {"wifi_on": 1, "adb_wifi": 0, "online": false, "rttMs": -1}
③ 置开关:   {"adb_wifi": 1}          ← 写入成功
… 几分钟后再读: adb_wifi 又变回 0    ← 系统自己清的
```

**结论（三层，缺一不可）**：
1. 无线调试依附的是**"可用的 Wi-Fi 网络"**，不只是开关状态；网络不可用时，Android 会把
   `adb_wifi_enabled` 自动清回 0 —— 所以"硬写开关"必然反复失效（这不是脚本 bug）；
2. 没有可用网络时，**mDNS 什么都发现不了**（组播没接口用）→ 旧版就卡在"没发现端口"；
3. 而 `adb connect` 必须有一个**真的在监听的 adbd**，上面两条不满足时它压根不存在。

**实测补充**：本地扫 30000-60999 能稳定扫到几个开放端口（31961/39531/41293，7 秒扫完），
但逐个 `adb connect` 全是 offline —— 它们是我自己这边的服务（vision/filepanel 等），**不是 adbd**。
注意：连错端口会在 adb 的设备表里留下 offline 假记录，脚本里已经顺手 `adb disconnect` 清掉。

**组件 8 据此改成三件事**：
- Wi-Fi 没开 → 打开 Wi-Fi 页并轮询，你一点开它就**自动继续**（`--no-ui` 可关闭）；
- 端口发现 = **mDNS 优先 → 失败就本地扫端口**（不再单点依赖 mDNS）；
- 连不上时**给准确诊断**，分三种状态：
  - `wifi_on=1 且 adb_wifi 被清回 0` → "Wi-Fi 没真正连上网络"（本次就是这种）
  - `wifi_on=1 且 adb_wifi=1 但无监听` → "框架没启动，请在开发者选项里手动开一次无线调试"
  - 其他 → 打印三个原始值

**顺带修掉一个自己挖的坑**：组件日志开关用了 `export`，会传给孩子进程（套件→组件），
导致被套件调用的组件以为自己已经记过日志而**跳过自己的日志**——那次"退出 1"的输出就是这么丢的。
已改成不 export。

### 十三·补十三：adb 固定到 5555 + 电池白名单（2026-09-26 晚，已落地）

**① 端口固定**：`adb tcpip 5555` → `adb connect 127.0.0.1:5555`。
切换后 **mDNS 广播消失**（adbd 从"无线调试模式"变成"TCP 模式"），所以组件 8 的端口发现顺序
补了第 0 步：**先看 5555 是否在听** → 在就直接用。
> 不补这一步的代价：5555 **不在** 30000-60999 的扫描区间里，组件会先白等 3×12s 的 mDNS
> 再加 7s 扫描，才轮到 5555 兜底（≈45s）。补完之后实测 **3 秒**跑完。

切换时别忘了 `adb disconnect <旧端口>`：旧条目会以 `offline` 留在设备表里，
之后每条 `adb shell` 都报 `more than one device/emulator`（本次实测踩到）。

**② 重启后会回到无线调试模式**（TCP 模式不持久，`persist.adb.tcp.port` 被 vivo 拒），
需要你开一次「无线调试」；组件 8 两种模式都能处理。

**③ 电池白名单**（一直挂在"待手动"里，这次用 adb 做掉了）：
```
adb shell dumpsys deviceidle whitelist +com.termux
adb shell dumpsys deviceidle whitelist +io.dsh.bridge
```
确认两条都在（`user,com.termux` / `user,io.dsh.bridge`）。这是"跳转回来无法读取"（后台被冻结）
的根因对策之一，配合组件 1 自动拿的 wakelock 一起生效。

**④ 顺带用 adb 核实的事实**：
- `settings get secure enabled_accessibility_services` → 桥在列表里；
- `settings get global adb_wifi_enabled` → 1；
- `dumpsys package io.dsh.bridge` → `versionName=1.8`、`stopped=false`；
- 无障碍列表里**还有 AutoJs（org.autojs.autoxjs.v6）与 vivo 一个 SDK 服务**也处于启用状态——
  组件 0 只撤我这条链，管不到它们（已如实告知用户）。

### 十三·补十四：「此主机没有可用的桌面，无法打开文件或文件夹」（2026-09-26 晚）

**现象**：点交付文件卡片上的「打开」，弹出这句（截图 OCR 出来的），看起来像"打不开了"。

**根因（一行源码）**：`dsh-native-command` 判定"有没有桌面"的方式是——

```js
if (platform === "darwin" || platform === "win32") return true;
return isWsl(internals) || present(env.DISPLAY) || present(env.WAYLAND_DISPLAY);
```

Termux 既不是 WSL，也没有 `DISPLAY`/`WAYLAND_DISPLAY` → 判定"无桌面" → 客户端就拒绝执行打开动作。
而真正干活的那一步是 `xdg-open <path>`。

**关键发现**：**Termux 自带的 `/data/data/com.termux/files/usr/bin/xdg-open` 就是 `termux-open`**
（脚本开头 `SCRIPTNAME=termux-open`），实测 `xdg-open 某文件` → **安卓的文本查看器（vivo PlainTextReader）正常弹出**。
也就是说这条链只差"让 DSH 以为有桌面"。

**已做**：启动 DSH 时带上 `DISPLAY=:0`（写在 `common.sh` 的 `dsh_start` 里，
可用 `DSH_DISPLAY=""` 关掉）。**需要重启一次 DSH 才生效**（组件 4/6 或 `dsh-restart`）。
生效后「打开」会调用 `xdg-open` → `termux-open` → 交给安卓打开。

**不重启也能用的路**：交付卡片上的「在侧边栏预览」——那条路是纯浏览器侧的，不需要桌面。

**注**：这里给的是"善意的假 DISPLAY"（Termux 上并没有 X 服务器），只为通过那一处可用性判定；
真正执行的是 xdg-open。想彻底不撒这个谎，就 `DSH_DISPLAY=""` 并改用"侧边栏预览"。

### 十三·补十五：**停放项**——「无线调试」与 TCP 模式互斥（2026-09-26 晚，用户说以后再说）

**起因**：为了端口稳定（用户同意）我跑了 `adb tcpip 5555`。副作用：adbd 从"无线调试模式"切成
"TCP 模式"，两者互斥 → 开发者选项里的「无线调试」虽然 `settings get global adb_wifi_enabled`
仍读作 **1**，但那套模式实际不再生效（用户的感觉就是"无线调试打不开了"）。

**已验证**：把 `adb_wifi_enabled` 关掉再打开 **不能** 把 adbd 拉回无线模式（5555 仍在监听）。
`adb tcpip` 是粘的，只能靠 `adb usb` 退出。

**以后再做的恢复步骤**（挑有空的时候，因为中途 adb 会全断）：
1. `adb usb`（adbd 回 USB 模式 → 5555 的无线监听消失；没有数据线时 adb 会彻底断）
2. 在开发者选项里重新打开「无线调试」
3. 点组件 8 → 它会 mDNS/扫端口自己找回来并接上

**当前可用状态**（停放期间）：adb 走 `127.0.0.1:5555`（device）；组件 8 优先用 5555，2~3 秒完成；
桥 v1.8 正常；DSH 正常。**也就是说不恢复也不影响使用，只是那个开关的状态不好看。**

### 十三·补十六：用桥给 Clash Meta 做「覆写」优化（2026-09-26 晚，已落地并验证持久化）

**入口**：Clash Meta → 设置 → **覆写**（DNS/常规表单）与 设置 → **Meta 特性**（统一延迟/TCP 并发/嗅探）。
这两处是**全局覆写**，所以订阅更新也不会把它们冲掉 —— 这正是 09-25 那份 YAML 想解决的问题。

**已应用的设置**（退出重进后复查，全部保留）：

| 项 | 位置 | 值 |
|---|---|---|
| Name Server | 覆写 → DNS | `https://doh.pub/dns-query`、`https://dns.alidns.com/dns-query` |
| Fallback Name Server | 覆写 → DNS | `https://1.1.1.1/dns-query`、`https://dns.google/dns-query` |
| Default Name Server | 覆写 → DNS | `223.5.5.5`、`119.29.29.29` |
| 增强模式 | 覆写 → DNS | **Fake-IP 至 域名映射** |
| FakeIP 过滤器 | 覆写 → DNS | 8 条：`*.lan` `*.local` `*.localhost` `*.home.arpa` `time.*.apple.com` `ntp.*.com` `+.pool.ntp.org` `stun.*.*` |
| 统一延迟 | Meta 特性 | 已启用 |
| TCP 并发 | Meta 特性 | 已启用（本来就有） |

**表单做不到的**（如实记下）：
- 删订阅里 proxy-groups 的假节点（`server: '1'`）——覆写里没有代理组/节点项；
- 删坏规则 `smartdnsFantasy Cloud.com`——没有规则项；
- `keep-alive-interval`——没有对应字段（搜过 keep/存活/间隔）。
  想要这三样只能走"自己生成完整优化配置 → 配置页导入成本地文件 profile"，代价是订阅更新要手动重做。

**为这件事新建的两个工具**（都可复用）：
- `~/.local/bin/dsh-uitap <文字>` —— 用桥在任意 App 里按文字点控件，**自动滚动**找人，
  支持 `--list`（看当前控件）/`--contains`/`--nth`/`--set`；同时匹配 text 与 desc（图标按钮靠 desc）。
  踩过的两个坑都写进注释了：① 无障碍树里**同时存在屏幕外的旧副本**（坐标可能为负或很靠下）→
  必须"按离视口中心最近"挑，否则会朝错误方向滚；② 可视区要收紧到内容区（y 420~2500），
  否则会挑到压在标题栏上的副本。
- `~/.local/share/dsh-widgets/clash-override.py` —— 专门填 Clash 覆写表单。
  **关键：这个表单是"列表套对话框"，输入框的确认在 y≈1271、列表的确认在 y≈2931**，
  按文字找「确认」会撞车（我因此把 Fallback 误设成"置空"过一次）→ 脚本一律**按坐标点**，每步都 dump 校验。
  用法：`clash-override.py audit | list "<字段>" v1 v2 … | enum "<字段>" <选项> | open "<字段>"`。

**生效时机**：覆写在 Clash 重新加载配置/启动时生效。当前服务仍是「已停止」，没动它。

### 附录：如果将来要迁到 DSHA（2026-09-26 评估，**暂不迁**）

发现的项目：[DSH-APP/DSHA](https://github.com/DSH-APP/DSHA) —— 把「Ubuntu 24.04 + Node 24 + DSH」
打进一个 APK，免 Termux、免 root；内置 ADB 无线配对、看门狗、23 项自检、数据放 `Documents/dshdata`。
**用户决定暂不迁移**（它在 rc/alpha 阶段，且自述线上包用的是 debug keystore 签名；本项目环境已调好，先并行观察）。

将来真要迁，按这个顺序（每一步都可回退）：
1. **只读侦察**：装 APK → 跑通首次解压 → 记录它**真实**的目录布局（`Documents/dshdata`、DSH_HOME、profile 路径）；
2. **只迁 DSH 数据**：停 Termux 侧 DSH → 拷 sessions / settings.yaml / 插件清单 + `local/` 源码
   （**node_modules 必须重装**，不能拷）→ 在它的 Ubuntu 里 `pnpm install` → 验证会话可开、插件树 0 错误；
3. **补回运维**：它的看门狗/自检替代大部分组件职责；需要重做的是「紧急停止」与（可选的）**桥**
   —— 桥是独立 Android 应用，**与 Termux 无关，可以继续用**，`droid-sock` 用它的 Ubuntu 里的 python3 即可；
4. **并行对比一段时间**再决定退役哪边；**Termux 环境全程别删**（那是回退路径）。

迁不过去 / 要重写的：Termux:Widget 那 9 个桌面入口（DSHA 无此机制，改用通知/悬浮条）、
`~/.bashrc` 那套环境变量与 `dsh-browser-open`、`droid*` 系列脚本（逻辑可移植，路径要改）。
会丢的：DSHA 的设备能力走 ADB，**没有我们那条"离线回环 + 音量键自救"的备用通道**（除非把桥一起带过去）。

### 十三·补十七：手机端增强插件上线 + 一个必踩的坑（2026-09-26 晚）

**做了什么**：`dsh-tasksd`（回环任务执行器，白名单+token+CORS）+ 本地插件 `dsh-mobile-local`
（右下角 ☰ 浮动按钮 → 任务面板：3 状态灯 + 9 个组件按钮 + 危险动作二次确认 + 输出显示；
另有输入框左侧第二入口；附带保守的触屏 CSS：按钮撑到 42px、输入区加底部安全区、代码块横向滚动）。
总开关：`~/.dsh-mobile-ui.json` 里 `enabled:false` + 刷新即整体停用。

**坑（我踩了，实测报错「路径不在工作区内」）**：
filepanel 的 `resolveWithin(root, path)` 里，`path` 是**单独** `ctx.fs.resolve()` 的，
**不会拼上 root**，然后再用 `ctx.fs.contains(rootTarget, target)` 校验。所以：

> **调 `panel.readText` / `panel.writeText` 时，`path` 必须传绝对路径。**
> 传相对路径（哪怕 root 传对了）会被解析到工作区外 → 报 `路径不在工作区内`。

正确写法（照抄 selflook）：`call('panel.readText', { root: HOME, path: HOME + '/.dsh-look-cmd.json' })`。

**另一个必须记住的部署细节**：新增插件包光加 `dependencies` **不够**，还要加进
`package.json` 的 **`dsh.profile.bundles`**（组合树是按这个列表逐个 patch 出来的）；
而且**新增组合行必须重启 DSH** 才生效（改已有插件的 client.js 只需 `pnpm install` + 刷新）。

**验收**：在页面里用 eval 实测「读 token（32 字节）→ 调 `/status` → HTTP 200 → 拿到 DSH/桥/adb 状态」全绿。

### 十三·补十八：C（完整快照）落地 + A 第一步（状态发布器）2026-09-26 晚

**C · 完整快照**：新工具 `~/.local/bin/dsh-snapshot`
- 打什么：Termux 前缀(2.6G) + DSH 运行时(495M) + `~/.dsh` + 我写的运维层(775K)；**不含** `storage/`、`~/.smoke`、
  `~/.npm`、`~/.cache`、`~/.dsh/profiles.bak-*`；`--with-store` 可把 pnpm store(674M) 也打进去（还原后完全离线）
- 压缩：**zstd -T0 -3**（Termux 里在 `$PREFIX/glibc/bin/zstd`）；源 3.9GB → **1.1GB**
- 实测产物：`Download/dsh/备份/dsh-full-20260926-141729.tar.zst`（154,553 条目，`zstd -t` 完整性退出 0）
- **还原演练已做**：从包里抽出组件脚本/笔记/common.sh，与现役文件 **`cmp` 逐字节一致**
- 关键设计：`--owner=0 --group=0 --numeric-owner`（Termux 的 UID 是安装时分配的，记 0 才能在换机后由当前 UID 落地）；
  边写 `.part`、成功才改名；只留最近 3 份
- 踩到的两个坑：① **tar 的 `--exclude` 对命令行成员同样生效** —— 我把要打进包的 `META/` 也排掉了（下版已修，
  且改为把 `说明.md`/`.sha256` 放在包旁边）；② **tar 退出码 1 = "有文件在读取时被改动"（正在写会话），不是失败**，
  ≥2 才致命；判定归档好坏要看 `zstd -t`。

**A · 第一步**：状态发布器 `~/.local/bin/dsh-status-pub`
- 写 `Download/dsh/状态/status.json`（机器可读）+ `status.txt`（人可读）——因为未来的控制台 APK 与 Termux
  **不同 UID**，读不到私有文件，只能靠这个共享文件看状态。
- 内容：三个灯(dsh/bridge/adb) + 各自详情 + 9 个组件上次结果与时间 + 锁残留
- **已接进所有组件**：`done_`/`die` 都会刷新（实测：跑一次备份后 `ts` 与结果立刻更新）；
  组件 2 特殊——它在最后会杀掉 Termux，所以**在杀之前**发布。

### 十三·补十九：A 第二步 —— 控制台 APK v0.1（2026-09-26 晚）

**工程**：`~/dsh-console/`（纯 Java + 原生 UI，无第三方 SDK；复用桥那套 build.sh/签名流程）
**产物**：`Download/dsh/应用/DSH控制台-v0.1.apk`（**25 KB**，包名 `io.dsh.console`）

**权限只有一个**：`com.termux.permission.RUN_COMMAND`（Termux 定义的）。
没有：存储访问、网络、无障碍、悬浮窗、读应用列表、开机自启。
→ 因此它**读不到** Termux 的私有文件，状态与执行**全部走 RUN_COMMAND 通道**（Termux 把结果回传）。

**做了什么**：
- 主界面：三个状态灯（DSH/桥/adb）+ 9 个任务按钮（危险动作二次确认）+ 输出区 + 「打开 Web UI」
- 桌面小部件：三灯 + 启动/连adb/备份/刷新（**桌面上只放安全动作**，误触代价太大）+ 点标题打开界面
- 「打开 Web UI」复用 `dsh-browser-open "$(cat ~/.dsh-url)"`（所以仍能命中你桌面那个 PWA）

**通信协议（已对源码逐字核对，别凭印象写）**：
- 服务 `com.termux.app.RunCommandService`，action `com.termux.RUN_COMMAND`
- extras：`com.termux.RUN_COMMAND_PATH` / `_ARGUMENTS`(String[]) / `_WORKDIR` / `_BACKGROUND`(true→APP_SHELL 跑，不开终端会话)
  / `_COMMAND_LABEL` / `_PENDING_INTENT`
- **结果 bundle 的键是短名 `"result"`**（不是 `com.termux.RUN_COMMAND_RESULT_BUNDLE`），里面是
  `stdout` / `stderr` / `exitCode` / `errmsg` —— 我第一版就写错了这个，靠拉 `TermuxConstants.java` 才发现。
- 权限名 `com.termux.permission.RUN_COMMAND`；服务类名字面量在 constants 里也确认过。

**配套（A 第一步已完成）**：`dsh-status-pub`（写 `Download/dsh/状态/status.json`）+ 已接进所有组件（跑完自动刷新）。
APK 里「刷新状态」其实是跑 `dsh-status-pub --json` 拿 stdout —— 所以状态数据来源只有一条，不会两边不一致。

**待实测（装上才能验）**：① RUN_COMMAND 往返；② 小部件按钮在"后台启动服务"限制下能否发得出去
（App 内点击没这个问题，小部件点击可能被 Android 8+ 的后台限制拦住 → 若没反应就改成前台服务派发）。

### 十三·补二十：控制台 v0.2 —— 按用户反馈重做交互（2026-09-26 晚）

用户原话：「没有输出的时候是空白的，我还以为有bug」「按钮要做反馈啊，交互没反馈我还以为你做的是个壳子」。

**问题确认**（截图 OCR 显示其实功能是通的：状态灯读到了 `DSH 在跑(HTTP 401)`，点启动后也出现了"正在执行"）——
**是交互设计偷懒**：没占位、没有忙碌态、等待期间界面一动不动，看起来就像壳子。

**v0.2 改了什么**：
- **忙碌态**：点按钮 → Toast + 顶部转圈 + **每秒刷新「已等待 Ns」** + 所有按钮变灰不可点；结果到了自动恢复
- **超时提示**：45 秒没回传 → 明确列出可能原因（未授权 / allow-external-apps 关了 / 系统拦了后台启动）
- **输出区永不留白**：没内容时显示占位说明（怎么用）；有内容时按时间戳逐条追加，保留最近 4 条
- **结果包含**：退出码 + 耗时（`[22:41:03] 备份 完成（exit=0，用时 12.3s）`）
- **按压反馈**：按钮改用 StateListDrawable（按下变亮、禁用变暗）——之前用纯色背景把系统水波纹盖掉了
- **自动读状态**：进前台时若缓存超过 20 秒自动刷新一次（不再满屏灰灯让人以为坏了）
- **小部件也有反馈**：点按钮立刻把状态行改成「⏳ 正在执行：X …」，结果回来再刷新

### 十三·补二十一：控制台 v0.3 —— 靠"看图看颜色"抓出的三个 bug（2026-09-26 晚）

用户发两张截图并提醒「最好能看到颜色的那种」。视觉模型 + 本地取色一读，三个问题立刻现形：

1. **灯的圆点根本没上色**：v0.2 用 `SpannableString` 上色时把索引写死成 0/4/8，而
   `"● DSH　● 桥　● adb"` 里三个圆点实际在 **0 / 6 / 10** → 颜色涂到了字母 `H` 和汉字「桥」上，
   于是出现「文字写着 桥 v1.8、灯却是灰的」这种自相矛盾。
   **修**：按 `indexOf('●')` 动态定位；顺手让灯后面的名字也跟色。
2. **输出区同一条「已发送：刷新状态」重复三遍**：进前台时 `onCreate` 与 `onResume` 都会触发自动刷新，
   而那时缓存仍是旧的 → 各发一次。
   **修**：加 `autoStatus()` —— 有在飞的不发、缓存 <20s 不发、15s 内不重复发。
3. **忙碌时其他按钮"看不出变灰"**：给 Button 设的是**纯色文字**，完全忽略禁用状态。
   **修**：文字改用 `ColorStateList`（启用/禁用两态），禁用色明显更暗。

**方法论收获**：这三个里有两个（灯色、变灰）是**纯逻辑读代码不容易发现、截图一眼就看出来**的
——"能看颜色"确实不是锦上添花。取色用 `vision_colors`（本地 sharp 量化，不走限流后端）就能拿到主色板。

### 十三·补二十二：桥与 adb 必须分开（用户指出的概念错误）2026-09-26 晚

用户原话：「adb 是 adb，桥是桥，为什么要合并在一个选项里面呢，要分开的」。

**他说得对，这是我概念上就合并错了**：桥（无障碍 App + 回环 8788 + token，不需要网络）与
adb（无线调试 + wlan 接口，必须有可用 Wi-Fi）是**两条独立的命**。而「7_重连AI通道」把两者揉在一起
（adb 失败就顺带喊一下桥），结果"桥没起来"和"adb 没连上"分不清是谁的问题。

**改动**：
- 新工具 `~/.local/bin/dsh-bridge`：`status` / `wake` / `stop` / `off` —— **只**管桥，与 adb 无关
- 控制台 App：`连 adb`（组件 8）/ `唤醒桥`（dsh-bridge wake）/ `看桥状态` / `全部恢复（adb+桥）`（组件 7，诚实标注）
- 桌面小部件：`启动` / `连 adb` / `唤醒桥` / `刷新`（原来是 `连adb`+`备份`）
- `Tasks.T` 增加 `cmd` 字段：条目既可以是组件脚本，也可以是一条直接命令（桥那两条就是直接命令）

**顺带抓到一个真隐患**：小部件布局里「连 adb」按钮和 adb 指示灯 **id 撞了**（都叫 `w_adb`），
`RemoteViews.setOnClickPendingIntent` 会挂到同 id 的另一个控件上——也就是**那个按钮的点击本来就不可靠**。
现在灯用 `w_lamp_*`、按钮用 `w_btn_*`，并在布局里写了注释说明为什么绝不能重名。

### 十三·补二十三：控制台 v0.5 —— 输出框改「日志」按钮 + 按钮按功能分类（用户要求）2026-09-26 晚

用户原话：「底下的那个输出端你可以单独做一个日志按钮，然后再放里面」+「还有就是那个按钮按功能分类」。

**① 为什么原来的输出框该撤**：它是常驻在页面底部的一块 `TextView`，没有记录时就是一片空白 ——
用户的原话是「没有输出的时候是空白的啊，我还以为有bug」。**空白区域没有语义，用户只能猜它坏了**。
现在：
- 右上角一个「日志」按钮 → 点开**独立一屏**（AlertDialog + ScrollView），里面是完整记录：
  发送 / 回传 / exit 码 / 耗时 / 原始输出；三个按钮 `复制全部` `清空` `关闭`；可长按选中文本。
- 有未看记录时按钮带角标 `日志 (2)`，点开清零；**日志按钮不参与忙碌禁用**（等结果时也能翻记录）。
- 底部只留**一行**「最近一次结果」摘要（✅ 绿 / ⚠ 红 + exit + 耗时 + 时间），点它也能开日志；
  **一条记录都没有时这一行不显示** —— 不留空白，也不占地方。
- 日志内容永远不为空：没记录时写明"点哪里才会产生记录"。

**② 按钮分类**：`Tasks.T` 增加 `cat` 字段，界面按 `Tasks.CATS` 顺序分段渲染，段标题是蓝色小字「▍分类名」：
| 分类 | 内容 |
|---|---|
| 启动 / 停止 | 启动 DSH · 打开 Web UI · 软重启 · 硬重启 · 关闭 DSH |
| 通道（adb 与桥分开） | 连 adb · 唤醒桥 · 看桥状态 · 全部恢复（adb + 桥） |
| 维护 | 备份 · 清理 |
| 紧急 | 紧急停止 |
分类名直接写进 `cat` 常量（含"adb 与桥分开"），**分类本身就是给补二十二那条约定留的界面证据**。

**③ 顺手改**：对话框/日志面板改用深色主题（`@android:style/Theme.Material.NoActionBar`），
之前系统默认亮色对话框在深色页面上白一块；标题右侧加版本号 `v0.5`（省得又装到旧包还看不出来）。

### 十三·补二十四：组件 8 的"假诊断"——写设置 ≠ adbd 真起来（实跑抓到）2026-09-26 晚

拿组件 8 去接 adb 时实跑了一遍，抓到两个真问题：

1. **结论与事实相反**：脚本末尾的诊断分支是 `if wifi_on=1 && adb_wifi=0 → 判定"没真正连上网络"`，
   而当时桥实测 `online=true`（真能出网）。于是它一边打印 `online=true`，一边说"没有真正连上网络"。
   **这就是"撒谎的提示"**，比不报还坏。
   **修**：拆成三支 —— `adb_wifi=0 && online!=true`（网络/AP 问题）、
   `adb_wifi=0 && online=true`（**不是网络问题**，这一版系统只认手动打开那次）、
   `adb_wifi=1` 但没监听（框架没起）。
2. **白扫 3 万个端口**：桥把 `adb_wifi_enabled` 置 1 之后**会被系统清回 0**
   （实测：置 1 → 回读 0，只用 4 秒），adbd 根本不会起。原脚本不回读，照样去 mDNS + 扫 30000-60999，
   等 30~45 秒才报错。
   **修**：新增「③·校验」——置 1 后回读 3 次（间隔 1s），没保住就直接给出正确原因并 `exit 3`。
   实测新分支 **4 秒**给出正确结论（旧路径要 40 秒且结论是错的）。
3. 顺带记录：本机 `adb mdns services` 直接报 `mdns is not supported by this version of adb`
   → mDNS 那条路在本机永远是"没发现"，真正干活的是后面的本地端口扫描；
   而 30000-60999 里扫出来的 8 个候选端口（31961/35232/38458/39531/41293/51830/53046/53830）
   全是别的服务，**没有一个 adbd** → 说明"扫到候选"和"adb 能用"是两件事。

**结论（写进运维常识）**：`adb_wifi_enabled=1` 只是"期望状态"，不是"adbd 已在监听"。
重启手机之后，**必须有人在 开发者选项 → 无线调试 里手动打开一次**，adbd 才会真的在随机端口上监听；
此后组件 8 才能自动维持。想静默装包/截图（`pm install` / `screencap`）就得先过这一步。

### 十三·补二十五：没有 adb 也能把 APK 装上并自检（2026-09-26 晚，全程走桥）

用户当时连不上 Wi-Fi（`adb_wifi` 也被系统清回 0）→ adb 完全不可用。**只用桥 + Termux 就完成了
"装包 → 启动 → 截图 → 读界面 → 点按钮 → 读日志" 的全套自检**，路子记下来备用：

| 需求 | 用什么 |
|---|---|
| 打开安装包 | Termux 里 `am start -a android.intent.action.VIEW -d file:///…apk -t application/vnd.android.package-archive` |
| 读界面（带坐标） | `droid-sock ui`（无障碍树，坐标与整屏截图**同一坐标系**） |
| 截图 | `droid-sock shot` |
| 点击 | `droid-sock tap X Y` |
| 启动指定 Activity | Termux 的 `am start -n 包名/.Activity` |

**坑 1：桥的 `start <包名>` 基本没用** —— 受 Android 11 包可见性限制，`com.android.chrome`、
`io.dsh.console` 都回 `❌ 找不到应用`。要拉别的 App 一律用 Termux 的 `am start -n`。

**坑 2：vivo 安装器底部的按钮不在无障碍树里**
流程是：`am start` → vivo 弹「"Termux"请求使用安装权限」（三个按钮：本次允许使用权限 / 以后都允许 / 取消，
在树里能按 desc 找到）→ 选**本次允许**（一次性，不留下长期授权）→ 超级守护扫描页（"未经安全检测…
当前无法链接到网络"）→ 底部 `取消` + `安装`。
第二页的树里**只有**一个 `desc='取消安装'` 的节点（坐标 [720,2976]，其实是整条底部栏），
真正分左右的两个按钮读不到 → 改用 **tesseract TSV 在整屏截图上定位**：
`取消 x=628..712`、`安装 x=740..809`、`y=2951..2993` → 点 **(775, 2972)**，两次安装都命中同一坐标。

**坑 3：`vision_colors` 不能用来验证小面积配色**
它是对缩略图做量化（一次只统计一两千像素），灯上的圆点、蓝色小标题、按钮文字这种**小面积强调色
根本统计不到**，结果会让你误判"界面没有颜色"。正确做法是**自己裁剪区域再做直方图**：
`magick 图.png -crop 1360x90+40+270 +repage -colors 8 -format "%c" histogram:info:-`
实测（v0.5 主界面）：灯行 1795px 绿 `#61B25B` + 752px 红 `#E35D50`（adb 那盏是红的，正确）；
分类标题行 1776px 蓝 `#69A1F4`；日志按钮文字 53px 浅蓝 `#88BCF8` → **颜色确实上了**。

**实机自检又抓到两个"只有跑起来才看得见"的 bug**（已在 v0.5.1 修，见 `Download/dsh/应用/DSH控制台-说明.txt`）：
1. 日志里出现 `[errmsg] -1`：`TermuxConstants.EXTRA_PLUGIN_RESULT_BUNDLE_ERR = "err"` 是 **int**，
   `…_ERRMSG = "errmsg"` 才是 String。代码里 `if (errmsg.isEmpty()) errmsg = str(b,"err")` →
   把整数 -1 当消息打了出来。现在只认有文字的 errmsg。
2. 任务失败后摘要被覆盖成 `✅ 刷新状态`：任何任务跑完 Termux 侧都会自动补一次状态刷新，
   那次回传把刚失败的 `⚠ 连 adb exit=3` 刷成 `✅ 刷新状态 exit=0`。现在只有**用户点的那次**才更新摘要。

**把页面还给用户**：桥 `start com.android.chrome` 不管用 → 用 `dsh-browser-open "$(cat ~/.dsh-url)"`，
命中桌面 PWA 独立窗口（`org.chromium.webapk.aa0f83489237f2919_v2`），前台回到 Chrome。

**补充（用户告知，2026-09-26 晚）：vivo 这版安装器还有两步，我没处理**
用户原话：「安装的时候你要点击『可继续为您安装』那行小句子，然后还要通过我的指纹认证，
不过你也可以用我给你的密码」。也就是说点完「安装」之后：
① 还要点那行小字提示（"可继续为您安装"）才会真的往下走；② 然后要过**身份验证**（指纹 / 密码）。
→ **这次 v0.5 与 v0.5.1 能装上，那一步不是自动化完成的**（用户按的，或这次系统没拦）。
   以后要装包：要么先把界面停在那行小字/验证框上等你按一下指纹，要么用密码。
**更省事的路**：adb 可用时 `pm install` 是**静默安装、不需要身份验证**——这是"把无线调试打开一次"
最实际的好处；而无线调试重启后又得手动开一次（补二十四）。
**解锁密码怎么处理（我的建议：不落盘）**：那是整台手机的解锁凭据，写进文件就意味着
"任何能读到该文件的东西（包括以后读文件的 AI）都能解锁手机"。默认做法：
① 需要验证时我把界面停在验证框并告诉你，你按一下指纹（约 5 秒）；
② 你明确要我用密码时我就用，但**不写进笔记、不写进记忆、不落盘**；
③ 如果你确实要我长期持有，就单独放一个 `chmod 600` 文件，并在说明里写清"删掉它即失效"。

**演练结果（2026-09-26 23:18，用户同意后实跑，同一个 v0.5.1 覆盖安装）**
vivo 安装器的分支比想象的细，实测三条路：

| 情形 | 界面 | 要不要小字/身份验证 |
|---|---|---|
| 首次装某 App | 「超级守护」扫描页 → 底部 `取消`/`安装` | **要**（用户告知：点完「安装」还要点那行小字"可继续为您安装"，再过指纹/密码） |
| 已装**相同版本** | `已安装相同版本"DSH 控制台（0.5.1）"` → `直接打开` / `重新安装` / `取消` | 实测**不要**：点「重新安装」后 9 秒内直接装完，没有任何验证框 |
| 装了更高版本号 | 等同首次（走扫描页） | 预计要（未实测） |

**所以我自己的 App 以后升级（同签名 + 更高 versionCode）大概不需要你按指纹；只有"装一个全新 App"
才会撞上小字 + 身份验证那一步。** 到那一步我会把界面停在验证框上、告诉你去按指纹。

**坐标备忘**（1440×2976 屏，桥的 tap 与整屏截图同一坐标系）：
- 「本次允许使用权限」 `desc` 可直接在无障碍树里找到 → `(719, 2438)`
- 「以后都允许」`(719, 2641)`、「取消」`(719, 2844)`
- 同版本页：「直接打开」`(719,2438)`、「重新安装」`(719,2641)`
- 扫描页底部：`取消` x=628..712、`安装` x=740..809、y=2951..2993 → 点 **(775, 2972)**

**⚠️ 更正（2026-09-26 23:2x，v0.6 演练打脸）：上一段"同版本覆盖安装不需要指纹"是错的；
"v0.5 / v0.5.1 是自动化装上的"也是错的。** 实测事实：
① 从这个来源（Termux）装这个包，**每一次**都会走「超级守护」扫描页 → 必须点那行蓝字 → 必须过身份验证；
② 我此前点 (775,2972) 那一下，其实打在底部那个大按钮上，而它的标签是 **「取消安装」**
   （a11y 里只有一个 desc='取消安装' 的节点，中心 (719.5,2976)，尺寸约 1056×173）→ **那是取消，不是安装**；
③ 真正能点的就是那行小字：整句是「…若信任该应用，您**可授权本次安装**」，
   蓝色部分 x≈141..400、y≈1135..1180；但**只有后半段响应**：
   点 (277,1151)（"授权"上）**毫无反应**，点 **(338,1151)**（"安装"两字上）**才**弹出验证框；
④ 验证框由 **com.android.systemui** 承载：标题「安全验证」，正文
   "该应用来源于非官方应用商店，未经官方检测，需要您指纹验证后安装。"，
   「锁屏密码验证」按钮在 (368,1503)，指纹区 ImageView 在 (720,1584)；
⑤ **这个框约 50 秒无操作会自己超时关闭**（两次实测 50s / 54s），超时后回到扫描页、**安装不会发生**
   —— 我误判"装上了"就是因为我只看到"前台变了"就以为过了；
⑥ 判断"到底装上没有"的可靠办法：**再打开一次同一个 APK**：安装器说「已安装相同版本（x.y）」= 装上了；
   又走「超级守护」扫描页 = 没装上。**App 界面上的版本号不可靠**（更新后旧进程还活着，看到的还是旧版）。
⑦ 结论：**这台机器上要装 APK，人手（指纹/密码）跑不掉**；想真正无人值守只能用 adb 的 `pm install`（不需验证）。

**「锁屏密码验证」那条路的界面（2026-09-26 深夜实测，已走到输入框）**
点 (368,1503) 的「锁屏密码验证」后，界面**跳到 `com.android.settings`**，内容是：
- 标题「请输入锁屏密码」；无障碍里密码框的 desc 明说是 **「密码输入框，6位数字密码」** → **是 6 位纯数字**
- 自带数字键盘（全在无障碍树里，可直接点，不用输入法）：
  `1(364,1956) 2(720,1956) 3(1076,1956)`
  `4(364,2228) 5(720,2228) 6(1076,2228)`
  `7(364,2500) 8(720,2500) 9(1076,2500)`
  `0(720,2772)　删除(1076,2772)`
- 「关闭」在 (1200,1440)；**这个框同样有超时**（不操作会关掉 → 安装取消）
→ 所以走密码这条路的动作序列是：点蓝字 → 点「锁屏密码验证」→ 依次点 6 个数字。
**密码不在我手上**（此前刻意没落盘），要么用户当场敲，要么他发我一次。

**v0.6.1 的两个 bug（2026-09-26 深夜，装 v0.6 后我实机点出来的；这就是"做完要自己跑一遍"的价值）**
1. **`唤醒桥` / `看桥状态` 从来就没成功过**：`Tasks.T.cmd` 写的是裸命令名 `dsh-bridge wake|status`，
   而 RUN_COMMAND 的 shell 环境（`bash -lc`）PATH 只有 `/data/data/com.termux/files/usr/bin:.`，
   **不含 `~/.local/bin`** → 两条都是 `exit=127`。复现命令：
   `env -i /data/data/com.termux/files/usr/bin/bash -lc 'command -v dsh-bridge'` → NOT FOUND。
   → 改成绝对路径（`TermuxRunner.HOME + "/.local/bin/dsh-bridge ..."`）。修完实测：两条都 exit=0，
   看桥状态回传「桥：在监听且应答　版本 v1.8　paused=false」。
   **教训**：`~/.local/bin` 只对交互式 shell 在 PATH 里；任何"被别的程序拉起来的 bash -lc"
   都必须写绝对路径（组件脚本里本来就是绝对路径，所以只有 App 这两条中招）。
2. **状态刷新的摘要行从来没显示**：v0.6 的 isStatus 分支把 `pending` 先清再读 `wasPending`
   → 恒为 false。**改**：先读后清。实测点「刷新状态」→ 摘要行 `✅ 刷新状态 exit=0 0.171s`。

**密码那条路（已验证可用，全程无人值守）**：蓝字 (338,1151) →「锁屏密码验证」(368,1503)
→ 数字键盘 6 位（坐标见上）→ 完成页「打开/完成」→ 点完成 → **冷启动**才看得到新版本号。
再次强调：**密码没写进任何文件**（笔记/记忆/产物里都没有），只在当次点击时用到。

### 十三·补二十六：装包密码的"授权存续"与冗余自救（2026-09-26 深夜，用户要求）

用户原话：「你就把密码写进去吧，然后在 task 和那个控制台里面加入取消用户密码授权权限的项目，
项目内容你应该都知道吧，这也是冗余设计」。

**落地的东西（单一真源 + 两个入口 + 一个工具）**
| 组件 | 作用 |
|---|---|
| `~/.dsh-install-pass`（**600**，6 字节） | 唯一存密码的地方。**撤销 = 删掉它** |
| `~/.local/bin/dsh-install-pass` | 唯一真源：`status`（只报存在/权限/时间，**绝不回显密码**）、`set`（stdin 读，非 6 位拒绝）、`revoke`（先覆写后删+复核）、`digits`（只给自动化）、`path` |
| 小组件 `9_撤销安装密码授权.sh` | Termux 桌面入口（`--dry-run` 可用，走 helper 的 revoke 并复核） |
| 控制台 App | 「紧急」类『撤销安装密码授权』（红色+二次确认）、「维护」类『看安装授权状态』 |
| `~/.local/bin/dsh-install-apk <apk> [--verify]` | 一条命令装包：开安装器→本次允许→**按颜色找那行蓝字**→锁屏密码验证→6 位→完成→（可选）复核 |
| `~/.local/bin/dsh-find-blue [y起] [y止]` | 用像素颜色找"蓝色可点文字"的位置（那行字不在无障碍树里，且系统文案会变） |

**为什么需要 `dsh-find-blue`**：同一台机器实测到**两种版式**——"未经vivo人工亲测…"（段落在 y≈1135）
与"存在未核准（备案）问题…"（段落在 y≈1255），链接位置随文案变 → 固定坐标会失效。
做法：先从无障碍树取含"本次安装"的段落 y，再在该 y±(170/230) 的带里按颜色找蓝字 → 实测命中 (330,1271)。

**演练结果（用户要求的"不接受只写在说明里的保证"）**
1. 控制台「撤销安装密码授权」→ `✅ exit=0 0.104s` → Termux 侧 `ls` 确认文件消失 → `status` 返回未授权(1)；
2. 撤销后立刻用 `dsh-install-apk` 装包 → **被拦住：`✘ 未授权`，退出码 3**（"AI 立刻装不了包"是真的）；
3. 小组件 9 真跑 → exit 0、复核"文件必须真的没了"通过；
4. 之后重新授权 → `status` 又显示已授权（权限 600）。
**撤销只影响"装包用密码"，桥 / adb / DSH 各有自己的撤销入口，互不牵连。**

### 十三·补二十七：状态回传被"切头"导致的假故障（App v0.7.1 修）

现象：控制台明细行变成 `状态解析失败：Value asks" of type java.lang.String cannot be converted to JSONObject`。
根因（一步步挖出来的）：
1. `dsh-status-pub --json` 的包**长到了 6463 字节**（其中 `tasks.*.tail` 占 3992）；
2. `TaskResultReceiver` 对所有回传统一 `out.substring(out.length()-4000)` —— 任务输出看尾部没错，
   **但状态是一整个 JSON，从尾部切就把头切掉了**；
3. 被切后的片段开头是 `ad/dsh/备份/…`，`JSONTokener.readLiteral()` 把它读成字符串 `asks"`（停在 `:`），
   `JSONObject` 构造器于是抛 typeMismatch —— 这就是那句看起来很莫名其妙的报错。
**修**：① `dsh-status-pub` 加 `--brief`（不要每个任务的 tail）：**6463 → 1560 字节**；
② App 侧分开截断规则：状态整包（上限 200000，从头部保），任务输出才从尾部截 4000；
③ 解析失败时的文案改成「状态解析失败（回传可能被截断）」——别再让用户猜。
**教训**：**"从尾部截断"对结构化数据是破坏性的**；任何跨进程回传的 JSON，要么整包、要么显式报错，
不能悄悄切。

**v0.8：授权做成开关（用户 2026-09-26 深夜要求：「我觉得你的那个授权应该做一个开关，而不是一个按钮」）**
- 控制台「维护」类下面是一个 `android.widget.Switch` + 一行状态文字；开=已授权（绿），关=未授权（灰）。
- **关**＝撤销：二次确认 → `dsh-install-pass revoke`（实测 0.115s，文件消失，随后 `dsh-install-apk` 退出码 3 拒绝）。
- **开**（未授权时）＝弹 6 位密码输入框 → `printf '%s' <6位> | dsh-install-pass set` → 写回 600 文件。
- 开关状态**不是本地缓存**：进前台/回前台各静默查一次 `dsh-install-pass status`（新增 `run(...,quiet)`，
  查询不进日志、不动忙碌态、不写摘要——为此 `TaskResultReceiver` 的回传里加了 `cmdId` extra，
  界面才分得清"这条是查询回的"还是"任务回的"）。
- **怎么验证开关真的生效**（我做的演练）：拨关 → 确认 → 状态行变未授权 + `ls` 确认文件没了 + 装包工具被拦；
  拨开 → 输入 6 位 → 状态行变已授权 + `ls` 确认 600 文件回来了；外观上也对比过像素：
  开时该区域有 3741px 强调色 `#8FC5C0`，关时只有灰（`#2F3135/#3C3E41/#494B4F`）。
- **真机上的一次小插曲**：用桥往 App 的输入框打字要先点中 EditText（`droid-sock text` 走无障碍 SET_TEXT），
  而且软键盘弹出后对话框会上移（授权按钮从 y=1870 挪到 1257）→ 要**重新取坐标**再点，别用旧坐标。

**补二十九（续）：语义纠正 —— 这是「密码使用权」，不是「安装授权」（用户 2026-09-26 深夜）**
用户原话：「我觉得那个密码不单单只是安装，因为你也可以用这个密码给你自己授权，
这个开关是授权你能否使用这个密码」。**他是对的**：那 6 位是**系统身份验证的通用凭据**
（装包＝「安全验证」、解除应用"设置限制"、开发者选项里的敏感确认…都是同一个密码框），
所以开关的语义必须是「**AI 能不能动用你的密码**」，而不是「能不能装包」。

改名与迁移（旧的自动搬过去，不需要你操作）：
| 旧 | 新 |
|---|---|
| `~/.dsh-install-pass` | **`~/.dsh-auth-pass`**（600，helper 启动时自动 `mv`） |
| `dsh-install-pass` | **`dsh-auth-pass`**（status/set/revoke/digits/path；也认旧环境变量） |
| `9_撤销安装密码授权.sh` | **`9_撤销密码授权.sh`** |
| 控制台「安装密码授权」 | **「密码使用权」**（开关，语义写清"装包、解除设置限制等"） |

**写进文档的使用政策（我可以被检查）**：这个密码**只**在"替你过系统身份验证"时读取
（装包、解除设置限制这类你交办的事）；**绝不**用于解锁手机翻看内容、支付/免密、
或与当次任务无关的任何场景。收回入口三个：控制台开关 / 小组件 9 / `rm ~/.dsh-auth-pass`；
收回后 `dsh-install-apk` 会在前置检查处以退出码 3 拒绝。

### 十三·补三十：DSH 页面里的 task 插件同步更新 + 敏感值善后（2026-09-26 深夜）

**① 插件（`~/.dsh/profiles/web/local/dsh-mobile-local/client.js`）**
用户要求「那个 dsh task 插件也给更新一下」→ 与控制台保持同一套语义与分组：
- **按功能分类**：`▍启动 / 停止`｜`▍通道（adb 与桥分开）`｜`▍维护`｜`▍紧急`（原来是一整块 grid，方向感很差）。
- **桥与 adb 彻底分开**：新增 `唤醒桥` / `看桥状态`；`连 adb` 只做无线调试；`全部恢复（adb + 桥）` 保留但明确标注两条都要时才用。
- **「密码使用权」开关**：与 App 里那个完全同语义（开＝AI 可动用你的密码过身份验证；关＝收回）。
  开关状态来自 `GET /auth`；收回走 `POST /auth/revoke`；**重新授权**在页面里输入 6 位后 `POST /auth/set`
  （只写进 600 文件，tasksd 的日志只记一行 `auth set rc=…`，**不含值**）。
- **后端 tasksd 同步升级**：白名单加 `9_撤销密码授权`；新增 `VIRTUAL` 虚拟任务
  （`bridge_wake` / `bridge_status` → 绝对路径调 `dsh-bridge`，与控制台 App 同一套 id）；
  新增 `/auth`、`/auth/revoke`、`/auth/set` 三个端点（都要 token；`/auth/set` 只收 6 位数字）。
- **为什么改 tasksd 而不是改插件 host**：页面插件是**客户端**代码，改完 `pnpm install` + 刷新页面即可生效；
  host.js 改动要重启 DSH。把新能力放 tasksd（独立的 python 服务）就完全绕开了重启。
- **验证方式**：用自看通道 `dsh-control click selector=.mb-fab` 打开面板，再用 probe 读结构 ——
  实测面板里出现了四段分类、`唤醒桥/看桥状态`、以及「密码使用权　已授权」那一行。

**② 敏感值善后（`dsh-scrub-secret`）**
- 用户撤销后我审计发现：**撤销 ≠ 擦除** —— 删掉 `~/.dsh-auth-pass` 后工具全拒（装包退出码 3），
  但那 6 位明文仍在：DSH 会话存档 3 个 json + 灵枢情境记忆 2~3 个 md（因为用户是在**对话里**打出来的）。
- 走官方 `lingshu_cg forget` 只是**软删除**（进 `trash/`，明文还在）→ 又用新工具**就地抹掉**：
  `printf '%s' '<值>' | dsh-scrub-secret`（默认扫 `~/.dsh/.dsh-memory`；`--all` 连会话存档一起扫）。
  实测 3 个文件 4 处 → 全部替换为 `«已清除»`，`grep -rl` 在记忆库里 0 命中。
- **结构性结论（写进工具注释与这里）**：只要值进过对话，**当前会话存档无法根治**（每轮都会被重新写回）
  → 想彻底清：① 结束/删除该会话；② 再跑 `dsh-scrub-secret --all`。
  真正的预防是**别把凭据打进对话**：用控制台/页面那个开关的输入框自己输。

### 十三·补三十一：我踩的坑 —— 在 profile 里跑 `pnpm install` 会把 DSH 客户端 bundle 剪断（2026-09-26 深夜）

**现象**：页面顶部弹「Failed to load plugins … failed to import loader entry 0fe19c20
(@deepseek-ai/dsh-typert-registry): client-modules: bundle script /plugins/??…&rev=… failed to load」。

**根因**：为了让页面插件（`dsh-mobile-local`）的改动生效，我在 `~/.dsh/profiles/web` 里跑了
`pnpm install`（输出 `Packages: +3 -19`）。pnpm 把 **19 个"多余的包"剪掉了**，其中两个是要命的：
`@deepseek-ai/dsh-base`、`@deepseek-ai/dsh-web-app`。
这两个 **不是 pnpm 依赖**（profile 的 `package.json` / `pnpm-lock.yaml` 里根本没有它们），
它们是**指向 DSH 运行时的软链**（`~/.local/opt/dsh-termux-runtime/work/node_modules/@deepseek-ai/…`）。
它们提供那 60 个内置客户端 UI 模块 → 少了它们，服务端拼不出客户端 bundle，
`/plugins/??…` 直接 404 → 浏览器报"加载插件失败"。

**修法（1 分钟）**：把软链补回来 —— 现在有工具了：
`~/.local/bin/dsh-relink-bundles`（`--check` 只检查；幂等，跑几次都行）。
补链后**刷新页面**即可，不必重启 DSH（bundle 是按请求现拼的）。实测：补链前该 URL 404、补链后 200（15.8MB）。

**顺带的两个教训**
1. **诊断时我自己也踩了一脚**：页面 HTML 里 URL 是 `&amp;` 转义的，我拿转义后的字符串去 curl → 一路 404，
   差点把"服务端坏了"当成结论。**先把 404 的 URL 原样验证一遍**，再怀疑服务端。
2. **以后别在 profile 目录里裸跑 `pnpm install`**：要装插件用 `dsh plugin --profile web add …`；
   万一跑了，立刻 `dsh-relink-bundles`。自检套件已加两条护栏：
   「运行时 bundle 软链存在」+「本机 `/plugins/??…` 真的返回 200」（后者只在 8080 活着时跑）。

### 十三·补三十二：App 超时假警报的根因 + 按任务给窗口（2026-09-27 00:1x）

用户贴来控制台日志：`00:10:34 自动刷新` → 45 秒无回传 → 弹"可能原因：未授权/allow-external-apps/后台启动被拦"。
**取证结果**：那一刻我在跑 58 项自检（1GB 备份 + 沙箱 DSH 启动），`uptime` 显示 **load average 7.11**；
`Download/dsh/状态/status.json` 的 mtime 停在 16:10:07（UTC），之后没有新写入
→ **那两次请求根本没被执行**（不是权限、不是 allow-external-apps）。同一命令正常耗时 **0.131s**。
**结论：Windows/手机端的"忙"会让 App 发的 RUN_COMMAND 静默失败一次，而旧文案把用户往错误方向引。**

控制台 v1.0 的修法：
1. `Tasks.T` 增加 `waitS`（每条任务自己的等待窗口）：启动 240 / 软硬重启 300 / 关闭 200 / 连 adb 220 /
   全部恢复 280 / 备份·清理 420 / 紧急停止 180 / 看桥 40 / 查询 25。忙碌行显示「（这条通常 ≤Ns）」。
2. 查询类超时**自动补发一次**，两次都不回音才报错；报错文案第一条是"手机当时很忙"，权限类降到后面。
3. 自动刷新（进前台那次）走 `silentLog`：**不再写"已发送"日志**（以前会把真结果淹没），
   手动刷新照常记录。
4. `dsh-install-apk` 的完成判定放宽：向导**自己关掉**也算完成（之前只认"完成"节点 → 明明装上了却报失败），
   最终以前面的 `--verify`（"已安装相同版本"）为准。实测 v1.0 就是这样装上的。

**我自己的两个操作教训（写下来别再犯）**
- 控制台 App 的列表**会滚动**，点按钮必须用 `dsh-uitap <文字>`（按无障碍文字找+自动滚），
  **不能写死坐标** —— 我因此误触了「关闭 DSH」的确认框（立刻点了取消，DSH 没受影响，但很险）。
- 页面 HTML 里的 URL 是 `&amp;` 转义的：拿它去 curl 会一路 404，**先反转义再验证**（补三十一）。

**补三十二（续）：状态查询没写完成行 ≠ 没回传（2026-09-27 00:2x，用户追问"所以说回传呢"）**
- 用户日志：4 条"已发送：刷新状态"，0 条完成行。**回传其实是好的** —— 摘要行一直是
  `✅ 刷新状态 exit=0 0.17xs`、`✅ 自动刷新状态 exit=0 0.233s`。
- 根因是**我的设计**：状态类结果只更新灯+摘要，不写日志。日志本该回答"回传了吗" → 设计错了。
- **v1.1 修**：状态查询也写完成行（`✅ 刷新状态 完成（exit=0，用时 0.176s）—— 结果已更新到顶上的灯和下面那行摘要`），
  自动刷新仍静默（`pendingSilent` 控制），免得刷屏。实测：点一次刷新 → 日志角标从 1 变 2（发送+完成）。
- **我又被自己的判据骗了一次（重要）**：我用 `status.json` 的 mtime 断定"00:17 那几次没执行"，
  但 `dsh-status-pub` 的 `--json` 分支**打印完直接 return，不写文件**（第 150~152 行）；
  写文件只发生在不带 `--json` 的调用（小组件/tasksd）。→ **判据要选"这条路径真的会产出的证据"**，
  别拿旁边的副产物当铁证。（同一天第二次栽在这类"证据错配"上：第一次是页面上 `&amp;` 转义的 URL。）

### 十三·补三十三：Clash「连 GitHub 都打不开」的真凶 —— 节点域名被自己的 fake-ip 吃了（2026-09-27 00:5x）

**症状**：Clash 显示"运行中"、国内直连（百度）200、但**所有走节点的请求全 502**，GitHub 打不开。

**排查过程（都是实测证据，不是猜）**
1. `curl -x 7890 https://github.com` → CONNECT 200 后 **TLS 握手卡死**；`gstatic/generate_204` → **502**；
   国内直连 200 ✓ → 说明"核心在、规则在、DNS 劫持在，只有上游节点这条腿断了"。
2. **决定性实验**：拿一个**不需要解析的裸 IP 目标**（`http://1.1.1.1/`）走代理 —— 仍然 502，
   日志写 `dial Fantasy Cloud (match Match/) --> 1.1.1.1:80 error: connect error: dns resolve failed: couldn't find ip`。
   目标不需要 DNS 却报 DNS 失败 → **失败的是"节点自己那个域名"的解析**。
3. 打开 Clash 的「Clash 日志捕捉工具」（info 级）复现，拿到原文：
   `dial Fantasy Cloud (match DomainSuffix/gstatic.com) ... error: connect error: dns resolve failed: couldn't find ip`
4. 对照解析：机场节点域 `a-ec01.kfchaochi.com`：
   · DoH（阿里）→ CNAME `gtm-*.moeya.cc` → **16.162.47.51**（真实）
   · **Clash 自己的 DNS → 198.18.0.50（FakeIP！）**
   → mihomo **拒绝拿 FakeIP 去连代理服务器** → 全部节点"连不上"。而订阅本身是好的
     （流量 8.41/99 GiB、到期 2027-05-23、2 小时前刚更新过）。

**修法（两步，缺一不可 —— 我只做了这两步，没动别的）**
1. 把**整个机场域名**加进 `设置 → 覆写 → DNS → FakeIP 过滤器`：
   `+.kfchaochi.com` 与 `+.moeya.cc`（工具 `~/.local/share/dsh-widgets/clash-override.py list "FakeIP 过滤器" …`）。
2. **让配置重新生成 + 核心重启**：只点「更新」不生效（实测 DNS 仍发 FakeIP）；
   把 App 彻底退出重进（或停/开一次核心）之后才生效 —— 生效后立刻：
   `gstatic204=204`、`github=200`、`google/youtube=200`、系统路径（浏览器走的那条）也 200 ✅

**留下的自检工具**：`~/.local/bin/clash-doctor` —— 一条命令跑完五项
（核心在不在 / 直连 / 代理 / **节点域名有没有被 fake-ip 吃** / 结论 + 取证指引），退出码 0/1。

**⚠ 会复发**：机场换域名（比如从 `kfchaochi.com` 换成别的）时，同样的坑会再来一次；
`clash-doctor` 第④步就是为了让人一眼看出来（它会给出现成的修法）。

### 十三·补三十四：桥"唤醒不了"的真凶 —— 广播叫不醒死掉的 App 进程（2026-09-27 01:0x）
排查 Clash 时发现 DSH 桥掉了，`dsh-bridge wake` 报"token 被吊销 或 无障碍被关"，
但**手动 `am start -n io.dsh.bridge/.MainActivity` 立刻就活了**（token 也没问题）。
看代码发现：`droid-sock` 的阶梯唤醒里，**stage 2（拉界面）被 `DSH_BRIDGE_WAKE_UI=1` 默认挡着**
→ 广播受 Android 后台启动限制、叫不醒已死的 App 进程 → 桥一死就再也回不来。
**修**：stage 2 改为**默认启用**（代价：桥界面会被拉到前台一次），要关掉兜底就设
`DSH_BRIDGE_NO_UI=1`。实测语法通过、桥正常应答。

### 十三·补三十五：把套件发到 GitHub（2026-09-27 01:2x，全程我操作）

**结果**：仓库 **`Maopk/dsh-termux-kit`**（**私有**），默认分支 `master`，**87 个文件**已推送
（`apps/ widgets/ tools/ plugins/ tests/ docs/ dist/` + README/LICENSE/.gitignore）。
验证方式：GitHub API 读 `repos/.../git/trees/master?recursive=1` 数到 87 个 blob ✓。

**走通的路（含三次踩坑）**
1. 你说"我在 Chrome 里已登录，你自己去操作" → 我用桥驱动 Chrome：确认 `id=dashboard`（已登录）→
   打开 `/new?name=…&visibility=private`，**用 URL 参数预填表单**（比点表单可靠得多）。
2. **坑 1：分屏**。Chrome 和 DSH 的 PWA 窗口是分屏的，无障碍树只读**当前聚焦窗口** → 我按坐标点
   全点在了另一半上（还误读了一堆 OCR）。**解法：先回桌面再按 Activity 名拉起主 Chrome**
   （`am start -n com.android.chrome/com.google.android.apps.chrome.Main`），分屏自动拆掉。
3. **坑 2：账号 2FA（sudo 模式）**。生成 token 的页面直接弹 "Confirm access"（passkey/验证器/邮件码）——
   **这一步只能用户本人做**，我无法代过。
4. **坑 3：设备码用错了**。`gh auth login --web` 的进程被我误杀后，我用 curl 自己发起设备码流程，
   但浏览器沿用会话里**上一个未完成的设备码** → 用户授权的是旧码，我的轮询永远 `pending`
   （还因为轮询太密被 GitHub 判 `slow_down`）。**教训：设备码流程里，"页面上显示的码"必须和你
   手里那个 device_code 是同一次申请的**；重新申请后要让页面回到**输入码的那一步**再填。
5. 最终：重新申请设备码 `26B7-03A7` → 浏览器里 Continue as Maopk → 填 8 格 → Continue →
   **因为 sudo 模式还在，没有再要 2FA** → Authorize → curl 轮询拿到 token。

**token 与凭据**
- token 存在 `~/.dsh-gh-token`（**600**，内容从不打印）；用它建了仓库并推送一次。
- **撤销入口**：GitHub → Settings → Applications → Authorized OAuth Apps → "GitHub CLI" → Revoke
  （或在 Termux 里 `rm ~/.dsh-gh-token`）。`.git/config` 里**没有** token（推送时用一次性 URL）。

**可复用的经验（写给未来的我）**
- GitHub 的**表单字段不在无障碍树里**（React 受控输入），但**只读文本/按钮/代码框在** →
  验证码类输入可以从树里逐格读回、精确填写（比 OCR 稳）。
- 生成 token / 创建 token 需要 **sudo 模式**（2FA）；**OAuth 设备授权**在 sudo 有效期内**可以不再要 2FA** ——
  所以"刚做完一次 2FA 之后"是趁热打铁的最好时机。
- 一次 `slow_down` 之后要把轮询间隔拉长（GitHub 要求 +5s 以上），别硬轮询。

### 十三·补三十六：公开仓库 + 发行版（2026-09-27 01:4x）

用户要求：「我还是想公开，你把产品介绍做精简一些，不要花哨繁杂，还有把现有的产品公布出来以及加上README使用说明」。

**做的四件事**
1. **README 精简重写**（原来 200 行、偏"散文"）：现在是一句话定位 + 一张"包含什么"表 +
   **安装 5 步**（含 `allow-external-apps`、自检）+ **使用说明**（小组件表 / App / 插件 / 常用命令）+
   撤销与安全表 + 已知限制。事故复盘那类内容全部留在 `docs/`，不占首页。
2. **成品作为发行版发出**：`v1.1 — 控制台 1.1 + 桥 1.8`，资产三个：
   `dsh-console-v1.1.apk`(36.8KB) / `dsh-bridge-v1.8.apk`(28.5KB) / `SHA256SUMS.txt`。
   实测匿名下载：桥 APK `http=200`、29167 字节、sha256 与本地一致 ✓
3. **仓库转公开**：`PATCH /repos/... {"private": false}` → 之后匿名验证：
   `api/repos` / 仓库主页 / raw README / 发行版页 **全部 200** ✓
4. **公开前的复查**：`tools/check-no-secrets.sh` ✓；**全 git 历史**也扫了一遍
   （`gho_`/`ghp_`/`github_pat_`/锁屏密码/机场域名/设备序列号/`token=` 长串）→ **0 命中**。

**踩到的一个小坑**：GitHub Release 的资产上传接口对**非 ASCII 文件名**不友好——
我按 URL 编码传 `DSH控制台-v1.1.apk`，结果名字被吞成 `DSH.-v1.1.apk`。
**改法：资产名一律用 ASCII**（`dsh-console-v1.1.apk`），中文名只留在仓库内的 `dist/` 里。

### 十三·补三十七：GitHub token 的去留（2026-09-27 01:5x，用户决定）

用户问「这个 token 对这个项目有什么作用」，我先把**真实权限**查清楚再说结论（响应头就是证据）：
```
x-oauth-scopes: repo                              ← classic token：仓库级完全控制
x-oauth-client-id: 178c6fc778ccc68e1d6a           ← GitHub CLI 的 OAuth 应用
实测可访问仓库: 2 → Maopk/dsh-termux-kit、Maopk/Skyhook
```
**所以它不是"本项目的钥匙"，而是"名下所有仓库的钥匙"**（含以后新建的私有仓库），
OAuth token 又**没有自动过期**；而且关键一点：**光 `rm ~/.dsh-gh-token` 不会让它失效** ——
GitHub 那边授权还在，必须去 Settings → Applications → Authorized OAuth Apps → **Revoke**。

我把三个选项摆出来（彻底撤销 / 换 fine-grained 窄 token / 留着），**用户选择「留着方便后续维护」**。
于是做了两件事，让"留着"是有护栏的：
1. `~/.local/bin/dsh-gh`（已进仓库 tools/）：`status` / `push "说明"` / `release <tag> <标题> <文件…>` / `revoke-hint`。
   - `push` 用一次性 URL（**token 不写进 `.git/config`**），实测提交+推送成功；
   - `release` 会**提醒资产名别用非 ASCII**（中文名会被 GitHub 吞成 `DSH.-x.apk`）；
   - `revoke-hint` 把撤销步骤写成一条命令输出，免得以后再想。
2. 笔记与热记忆都记下：token 位置/scope/**撤销的正确做法**（删文件 ≠ 撤销）。

**给未来的自己**：要动这个 token 前先想清楚它是"全仓库钥匙"；不确定就先 `dsh-gh revoke-hint` 看一眼。

### 十三·补三十八：为什么我点了三次「没点到」鲸鱼娘 —— 自看插件的截图会把 `<img>` 整块剥掉（2026-09-27 02:0x，已实证修复）

**症状**：用户让点三下鲸鱼娘挂件，我用桥发了可信触摸，页面毫无反应；我自己插的调试 div 报的坐标也和"看起来"的位置对不上。用户一眼看穿：`实际上你没点到）`。

**根因（两层，缺一不可）**：
1. **自看插件的截图看不见图片**。`dsh-selflook-local` 的 capture 走的是"DOM → 内联 SVG → data-url 重绘"这条路，而它在重绘前把
   `<svg|img|picture|canvas|image>` 全部**剥掉**（`client.js` 的清理函数）。于是鲸鱼娘在那个 573×1214 的 PNG 里**从来没有像素**——
   我把那张图裁来裁去、画 ASCII 亮度图，全是在分析一张"没有鲸鱼的图"。（顺带解释了当时那张图 66 万像素纯白：那是 DSH 页面**浅色**渲染，和真机截图里 #151517 的深色底完全不是一回事。）
2. **命中判定在鲸鱼的 alpha 蒙版上**，不在矩形上。挂件 v757 起给 `img` 加了 `pointer-events:auto` + 用命中图（610×610 蒙版）的**凸包 clip-path** 裁掉透明边距；
   点透明处会穿透。所以"按 DOM 矩形中心点"必然落空——那里正是透明区。

**正解（可复用套路）**：
- 定位图像元素：**只用真机截图**（`droid-sock shot`），别用自看插件的 PNG。
- 从真机图里找目标：按它的特征色做 `-fuzz 7% -opaque <色>` + `-connected-components 8`，输出连通块的**像素包围盒**（比 fuzz 直接 trim 靠谱：trim 会被满屏文字抗锯齿的暖色带偏，实测假包围盒 1375×2915）。
  这次得到鲸鱼娘 = 设备 `(1106–1438 × 1305–1653)`，脸（#FCEEE7）`160×99+1194+1492` → 点心 (1274,1541)。
- CSS ↔ 设备 换算（1440×3168 屏、网页视口 573 CSS 宽）：**设备 = CSS × 2.5131 + (0, 119)**（119 = 状态栏）。用 `img.dshwv-img` 的 CSS 矩形 [430,468,143,143] 反算得 1295–1655，与像素实测 1305–1653 吻合 ✅
- 回读页面数据：`dsh-control eval` 的返回值**不可靠**（`value` 时有时无、结果文件里会被丢）。**用 `document.title` 当信道**：eval 里写 `document.title = JSON.stringify({...})`，再 `dsh-control capture`，从文字快照的 `VIEWPORT ... title=` 那行读回来，稳。
- 验证"真的点到了"：别读 `display/opacity`（气泡容器常驻 block/1，跟开合无关）。两个铁证：
  ① `MutationObserver` 盯 `.dshwv-root`（subtree+class+style）记流水；
  ② 数签名——`.dshwv-body` 的 **style 变化次数 = 2 × 点击次数**（pressDown 压扁 + pressUp 回弹），`dshwv-dragging` 随之增删。
  这次三下点完 = **6 次** body style 变化、气泡类 `dshwv-pop dshwv-pop-open`，一一对应。
- 语义澄清：`whaleClick()` 的效果就是**弹气泡**（`dshwv-pop-open`，BUBBLE_MS=5000 后自动收）；`bubbleTapAdvance` 开着时再点是"推进气泡队列"。

**给未来的自己**：这个仓库里"看得见"的东西分三种——DOM 能查的、真机截图能看的、页面自己重绘出来的。**页面自己重绘的那张图会骗人**（丢图片、换主题），凡是跟像素/位置有关的判断，一律回真机截图 + 连通域，再用 DOM 矩形交叉验证。
