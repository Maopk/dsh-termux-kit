# 测试分层（test layers）

这份文档是「哪一类断言该由谁回答、谁有权改这个判断」的单一出处。CI 的策略检查
`tests/report_check.py` 判的就是它背后那张表：`tests/lib/categories.tsv`。

## 三类断言

| 类别 | 谁来回答 | 在 CI 里的地位 |
| --- | --- | --- |
| `logic` | 任何一台机器：解析日志、判 HTTP code 序列、锁的语义、路径处理 | **必须 0 失败**，失败就是回归 |
| `simulable` | 本机 + 临时 HOME/localhost 就能模拟（起假服务器、桩掉 `dsh_alive`） | 同上，必须 0 失败 |
| `device` | 只有这台手机能回答：adb、DSH 桥服务、已装的 DSH 插件、Termux 解释器路径 | 记录并计数；表里**放行**的失败不算红，未放行的算 unexpected |

层与类别的默认对应（`tests/lib/categories.tsv` 只需要写例外）：

| 层 | 含义 | 默认类别 |
| --- | --- | --- |
| L0 | 预检（桥/adb 通道、8080） | device |
| L1 | 语法（每个脚本 `bash -n`） | logic |
| L2 | 预演（每个组件的 `--dry-run`） | simulable |
| L3 | 离线裁决（就绪、锁、URL、状态口径） | logic |
| L4 | 真跑（安全的、可逆的那些） | device |
| L5 | 冷启动沙箱（8099） | device |
| 尾部 SKIP 段 | 真跑会杀掉当前会话 | device |

## 一个断言的类别是怎么定的

`tests/selftest.sh` 里每次 `rec PASS|FAIL|SKIP "<id>" "<detail>"` 都会变成报告里的一项：
`{status, tier, id, category, ci_allowed, detail}`。类别由 `report_category <tier> <id>` 决定：
先查 `<tier>:<id>`（同一个 id 跨层时用，例如 `L2:3_backup-dsh.sh` 与 `L4:3_backup-dsh.sh` 不是
一回事），再查裸 `<id>`，都没有就取上表的层级默认。**未知层 → 空 = 未分类**：报告会点名，
CI 变红 —— 这是「新增断言必须有归属」的兜底。

## 报告与判定

* 套件每次运行都写 `$HOME_DIR/.smoke/selftest.json`（schema `dsh-selftest/1`，`--json [路径]`
  可改路径）。控制台「自检」按钮跑的就是带 `--json` 的那条命令，所以手机上的一次运行可以原样
  交给 `python3 tests/report_check.py <json>` 复核。
* `summary` 按类别×状态计数，并给出 `total`、`unexpected_failures`、`unclassified`；报告自称的
  数字与 items 重算不一致，检查器按「报告不可用」处理（退出码 2），不会把坏报告当绿灯。
* `tests/ci-selftest.sh` 用一次性 HOME 跑套件，然后把退出码交给 `tests/report_check.py`：
  logic/simulable 失败、或 device 里没有放行的失败 → 红（1）；报告本身坏了 → 2；否则绿（0）。
  `--strict` 仍然表示「用套件自己的退出码」（一条失败都不许有）。

## 怎么维护

* **新增断言**：写完 `rec …` 就完事，层默认就够。只有当它属于别的类别、或它在 CI 里本来就答
  不了时，才去 `tests/lib/categories.tsv` 加一行，并在第 4 列写清 why。
* **放行一行（`ci allow`）** = 公开声明「这台机器答不了它，留给手机」。检查器会用 `--verbose`
  把这类失败逐条列出来（`⚠` 之外的第二段），不会静默吞掉；手机上的那一跑仍然要真过。
* **想收紧 CI**：删掉一行 `allow`，看 CI 是否真能过 —— 过不去就说明那条断言确实只能手机答，
  把它加回去（或把断言改成 `SKIP` 而不是 `FAIL`）。
* 表的键不能陈旧：`tests/lib-tests.sh` 会检查每一行都还能在套件/组件里找到对应物。

## 与「老师方案」的三处偏差（以及为什么）

1. **类别按层默认、例外单独列**，而不是 130+ 个 `rec` 调用点逐条分类：逐条会得到一张很快过期
   的大表；按层默认 + 二十几行例外足以表达「谁回答得了」，而「忘了分类」由层默认兜住，
   「层都认不出来」才变红。
2. **断言函数留在 shell，没有重写成 Python**：设备侧必须在 Termux 上跑，Python 不是这个仓库对
   手机的前提；仓库已有 CI 强制的 shell 单测通道（`tests/lib-tests.sh`，job `unit`）。
   报告里那半部分用 Python/pytest：`tests/report_check.py` 是纯函数 + 12 个用例。
3. **device 的「预期缺席」从正则升级为被审阅的表**：原来藏在 `tests/ci-selftest.sh` 的
   `OFF_PHONE` 正则里（按消息匹配，顺带会吞掉同类的别的失败）；现在是一条条 id + 类别 + 理由，
   改动要过 review，且 CI 里那份「记录了但不算红」的清单会打在日志里。

## 还没做的

* device 报告还没有「上传/链接」那一步：CI 只能校验交给它的报告。手机跑完把
  `~/.smoke/selftest.json` 拿上来，用同一条命令复核即可（合并策略见
  `.github/workflows/ci.yml` 的 `selftest` job）。
* `apps/*/build.sh`（aapt2/javac/d8）与 `tools/sync-apps` 仍然只能在真机上跑，见
  `CONTRIBUTING.md` §三 的手工清单。
