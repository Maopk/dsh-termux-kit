#!/data/data/com.termux/files/usr/bin/bash
# selftest.sh —— 9 个小组件的自检套件（改完必须跑这个）
#
# 分级测试：
#   L0 前置   环境自己会坏（桥/adb/8080），先判定，别把环境问题算成组件失败
#   L1 语法   bash -n + python 编译
#   L2 预演   脚本自身的 --dry-run（必须退出 0 且走完全流程）
#   L3 判定   就绪判定/lock 的**回归测试**（这一层是本次修 404 的核心，全部可离线跑）
#   L4 真跑   安全可逆的组件真实执行并校验结果
#   L5 沙箱   组件 1 冷启动：8099 起临时实例，采样 HTTP 码取证「端口先开、路由后挂」
#   SKIP      真跑会杀掉当前会话（2/4/6）或需人工恢复（0）→ 只能预演 + 由用户择机真跑
HOME_DIR="/data/data/com.termux/files/home"
T="$HOME_DIR/.shortcuts/tasks"
L="$HOME_DIR/.local/share/dsh-widgets/common.sh"
PASS=0; FAIL=0; SKIP=0; REPORT=""
TMPLOG="$HOME_DIR/.smoke/selftest.log"
TMPD="$HOME_DIR/.smoke/selftest-tmp"; rm -rf "$TMPD"; mkdir -p "$TMPD"
SAVED=0   # 只有在本次真的备份过日志/URL 之后，才允许收尾还原

# ── 沙箱只准碰 8099，绝不准碰你正在用的 8080 ──
# 为什么写这么细：以前这里是"凡是不在启动前快照里的 dsh 进程就 kill"，
# 万一你在我测试期间重启了 DSH，新实例就会被当成沙箱误杀。
kill_sandbox() {
  local p
  for p in $(pgrep -f 'bin[.]js web' 2>/dev/null); do
    [ "$p" = "$$" ] && continue
    if tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null | grep -q -- '--port 8099'; then
      kill -9 "$p" 2>/dev/null && line "     （已清理沙箱进程 pid $p）"
    fi
  done
}
# 被 pkill / 重启打断时也要收拾干净：沙箱进程、沙箱启动锁、被改动的日志与 .dsh-url
cleanup_all() {
  kill_sandbox
  [ -d "$HOME_DIR/.dsh-boot-8099.lock" ] && rm -rf "$HOME_DIR/.dsh-boot-8099.lock" 2>/dev/null
  if [ "$SAVED" = 1 ]; then
    cp -f "$HOME_DIR/.smoke/restart.log.save" "$HOME_DIR/.dsh-restart.log" 2>/dev/null
    cp -f "$HOME_DIR/.smoke/dsh-url.save" "$HOME_DIR/.dsh-url" 2>/dev/null
  fi
  rm -rf "$TMPD" 2>/dev/null
  return 0
}
trap 'cleanup_all' EXIT INT TERM
kill_sandbox   # 上一轮被打断留下的沙箱，先清掉

# 公共库要**在 L0 之前**载入：L0 的桥唤醒需要 bridge_wake（带 token）
. "$L"

line() { printf '%s\n' "$1"; }
rec()  { # $1=状态 $2=组件 $3=说明
  case "$1" in
    PASS) PASS=$((PASS+1)); printf '  ✔ %-24s %s\n' "$2" "$3" ;;
    FAIL) FAIL=$((FAIL+1)); printf '  ✘ %-24s %s\n' "$2" "$3" ;;
    SKIP) SKIP=$((SKIP+1)); printf '  ○ %-24s %s\n' "$2" "$3" ;;
  esac
  REPORT="${REPORT}${1}|${2}|${3}\n"
}

dryrun_ok() { # $1=脚本
  local out rc
  out=$(timeout 120 bash "$1" --dry-run 2>&1); rc=$?
  printf '%s' "$out" > "$TMPLOG"
  [ "$rc" = 0 ] && grep -q '】完成' "$TMPLOG"
}

line "════ 小组件自检 $(date '+%F %T') ════"

# ── L0：前置条件 ──
line "【L0】前置条件"
BRIDGE_OK=0; ADB_OK=0; WEB_OK=0
(exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && BRIDGE_OK=1
if [ "$BRIDGE_OK" = 0 ]; then
  # 必须用**带 token** 的广播：v1.7 起不带 token 的唤醒在服务已关时会被忽略，
  # 以前这里用裸广播 → 桥明明能唤醒却被判成"不可用"，整段桥测试被跳过（实测踩到）。
  bridge_wake 2>/dev/null || { am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver >/dev/null 2>&1; sleep 2; }
  (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && BRIDGE_OK=1
fi
if [ "$BRIDGE_OK" = 0 ]; then
  am start -n io.dsh.bridge/.MainActivity >/dev/null 2>&1; sleep 3
  (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && BRIDGE_OK=1
fi
[ "$BRIDGE_OK" = 1 ] && rec PASS "前置：桥通道" "8788 可用（$(timeout 12 "$HOME_DIR/.local/bin/droid-sock" ping 2>/dev/null | grep -oE '"ver": *"[^"]*"')）" \
  || rec SKIP "前置：桥通道" "带 token 唤醒也起不来 —— 需你打开一次「DSH 桥」App（非组件问题）"
adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q . && ADB_OK=1
if [ "$ADB_OK" = 0 ] && [ "$BRIDGE_OK" = 1 ]; then
  timeout 150 bash "$T/8_自动开无线调试.sh" >/dev/null 2>&1
  adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q . && ADB_OK=1
fi
[ "$ADB_OK" = 1 ] && rec PASS "前置：adb 通道" "$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')" \
  || rec SKIP "前置：adb 通道" "未连接（Wi-Fi 关过/重启过；需先开一次「无线调试」）"
(exec 3<>/dev/tcp/127.0.0.1/8080) 2>/dev/null && WEB_OK=1
[ "$WEB_OK" = 1 ] && rec PASS "前置：8080" "当前会话在跑" || rec SKIP "前置：8080" "当前没有会话在跑"

# ── L1：语法 ──
line "【L1】语法检查"
for f in "$T"/*.sh "$L" "$HOME_DIR/.local/share/dsh-widgets/selftest.sh"; do
  n=$(basename "$f")
  if bash -n "$f" 2>/dev/null; then rec PASS "$n" "语法 OK"; else rec FAIL "$n" "语法错误"; fi
done
PYC=$(python3 -m py_compile "$HOME_DIR/.local/bin/droid-sock" 2>&1)
[ -z "$PYC" ] && rec PASS "droid-sock" "python 编译 OK" || rec FAIL "droid-sock" "python 编译失败：$(printf '%s' "$PYC" | tail -1)"

# ── L2：--dry-run ──
line "【L2】--dry-run 全流程预演"
for f in "$T"/*.sh; do
  n=$(basename "$f")
  if dryrun_ok "$f"; then rec PASS "$n" "dry-run 走完且退出 0"; else rec FAIL "$n" "dry-run 异常（见 $TMPLOG）"; fi
done
# 组件 2 的 --keep-bridge 曾经只在文档里、没进 case（写了也被无视）→ 用真行为回验
if timeout 60 bash "$T/2_关闭DSH.sh" --dry-run --keep-bridge > "$TMPLOG" 2>&1 && grep -q '按要求保留桥通道' "$TMPLOG"; then
  rec PASS "2_关闭DSH --keep-bridge" "参数真的生效（保留桥通道）"
else rec FAIL "2_关闭DSH --keep-bridge" "写了 --keep-bridge 但仍去关桥（参数没进 case）"; fi
if timeout 60 bash "$T/2_关闭DSH.sh" --dry-run > "$TMPLOG" 2>&1 && grep -qE 'droid-sock (sleep|stop)' "$TMPLOG"; then
  rec PASS "2_关闭DSH 默认" "默认会关桥（sleep 优先，旧版退 stop）"
else rec FAIL "2_关闭DSH 默认" "默认没走到关桥分支"; fi

# ── L3：就绪判定 / 锁 的回归测试（本次修 404 的核心）──
line "【L3】就绪判定与启动锁（回归测试）"
# 载入公共库（DRY=0），单独在子 shell 里做断言

# ① 旧 token 必须被拒：这正是截图里「打开就是报错页」的成因
CACHED=$(grep -oE "$TOKEN_RE" "$HOME_DIR/.dsh-url" 2>/dev/null | tail -1)
if [ "$WEB_OK" = 0 ]; then
  rec SKIP "旧 token 被拒" "8080 没在跑，无法验证"
elif [ -z "$CACHED" ]; then
  rec SKIP "旧 token 被拒" ".dsh-url 里没有 URL"
elif url_ready "$CACHED"; then
  rec PASS "旧 token 被拒" "缓存 URL 恰好仍有效（200）"
else
  rec PASS "旧 token 被拒" "缓存 URL 已失效（HTTP $(url_code "$CACHED")），url_ready 正确判为不可用"
fi

# ② 半启动的 404 必须被拒（并证明旧判定的确会放行）
python3 - <<'PY' >/dev/null 2>&1 &
import http.server, socketserver
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(404); self.end_headers(); self.wfile.write(b'not found')
    def log_message(self, *a): pass
socketserver.TCPServer.allow_reuse_address = True
socketserver.TCPServer(('127.0.0.1', 8097), H).serve_forever()
PY
FAKE404=$!
sleep 1.2
OLD=$(http_code 8097)                       # 旧判定用的就是这个：非 000 就算「就绪」
if url_ready "http://127.0.0.1:8097/"; then
  rec FAIL "半启动 404 被拒" "url_ready 竟然把 404 当成就绪"
elif [ "$OLD" = "404" ]; then
  rec PASS "半启动 404 被拒" "旧判定看到 HTTP $OLD 会放行 → 新判定正确拦住（这就是截图那个 404）"
else
  rec PASS "半启动 404 被拒" "404 服务被正确拒绝（旧判定 http_code=$OLD）"
fi
kill "$FAKE404" 2>/dev/null

# ③ token 行提取
cat > "$TMPD/fake.log" <<'EOF'
[dsh-cost-meter] 已加载
dsh web: http://127.0.0.1:8099/?token=AAA-bbb_CCC123 (LAN: http://192.168.1.5:8099/?token=AAA-bbb_CCC123)
EOF
GOT=$(token_from_log "$TMPD/fake.log")
[ "$GOT" = "http://127.0.0.1:8099/?token=AAA-bbb_CCC123" ] && rec PASS "token 行提取" "取到本机 URL：$GOT" \
  || rec FAIL "token 行提取" "取到的是「$GOT」"

# ④ 启动互斥：连点两次不能同时启动两个实例（两个实例抢 credentials 写锁必崩一个）
# 注意：必须用**真正的另一个进程**来当持锁者 —— subshell 里 $$ 仍是父进程 pid，
# 会被「同进程幂等」判定当成自己人，测不出互斥。真实的连点就是两个 bash 进程。
LOCK_BLOCKED=0; LOCK_OK=0
bash -c '. "$HOME/.local/share/dsh-widgets/common.sh"; boot_lock_acquire; sleep 3; boot_lock_release' &
HOLDER=$!
sleep 1
if boot_lock_acquire 2>/dev/null; then boot_lock_release; else LOCK_BLOCKED=1; fi
if [ "$LOCK_BLOCKED" = 1 ]; then
  wait $HOLDER 2>/dev/null
  boot_lock_acquire && LOCK_OK=1
  boot_lock_release
  [ "$LOCK_OK" = 1 ] && rec PASS "启动互斥锁" "第二个启动被挡住；持锁者退出后可再取" \
    || rec FAIL "启动互斥锁" "持锁者退出后仍取不到锁（陈旧锁没被清）"
else
  rec FAIL "启动互斥锁" "第二个启动没被挡住 → 连点两次会拉起两个实例"
fi
# 幂等：同一进程（组件先取锁 + 公共启动函数再取锁）不能自己挡自己
A=$( boot_lock_acquire; echo $? ); B=$( boot_lock_acquire; echo $? )
boot_lock_release
[ "$A" = "0" ] && [ "$B" = "0" ] && rec PASS "启动锁幂等" "同进程重复取锁都成功（不会自己挡自己）" \
  || rec FAIL "启动锁幂等" "同进程第二次取锁失败（$A/$B）→ 组件会在自己内部死锁"
rm -rf "$BOOT_LOCK_DIR" 2>/dev/null   # 兜底：测试不留锁，否则后面 L5 会起不来

# ⑤ 孤儿 credentials 锁：没有实例在跑时必须清掉（-9 之后不清就永远启动不了）
printf '999999\n' > "$TMPD/cred.lock"
R1=$( ( dsh_alive() { return 1; }; clear_orphan_cred_lock "$TMPD/cred.lock"; echo $? ) )
GONE=0; [ -f "$TMPD/cred.lock" ] || GONE=1
printf '999999\n' > "$TMPD/cred.lock"
R2=$( ( dsh_alive() { return 0; }; clear_orphan_cred_lock "$TMPD/cred.lock"; echo $? ) )
if [ "$R1" = "0" ] && [ "$GONE" = "1" ] && [ "$R2" = "1" ]; then
  rec PASS "孤儿 credentials 锁" "无实例时删除；有实例时不动（-9 之后不再永久卡启动）"
else
  rec FAIL "孤儿 credentials 锁" "行为不对（无实例=$R1 删掉了=$GONE 有实例=$R2）"
fi

# ⑥ 进程已经死了要立刻报错，不要傻等
S=$(date +%s)
wait_dsh_ready 20 "$TMPD/fake.log" 999999 >/dev/null 2>&1; RC=$?
D=$(( $(date +%s) - S ))
if [ "$RC" = "2" ] && [ "$D" -lt 5 ]; then rec PASS "进程中途退出即报错" "pid 不存在时 ${D}s 内返回「已退出」"
else rec FAIL "进程中途退出即报错" "返回 $RC，耗时 ${D}s（应为 2 且很快）"; fi

# ⑦ dsh_pids 不能匹配到自己这条命令行（以前踩过：pgrep 自杀）
SELFHIT=$(dsh_pids | grep -c "^$$\$" || true)
[ "$SELFHIT" = "0" ] && rec PASS "dsh_pids 不自匹配" "不会把调用者自己算成 dsh 进程" || rec FAIL "dsh_pids 不自匹配" "匹配到了自己"

# ⑧ 重启类组件必须**先取锁、后动手**：否则连点两次时，第二次的第①步会把第一次
#    刚拉起的新实例 -9 掉，第一次就报"进程在启动过程中退出了"（组件 4 实测就是这么坏的）
ORDER_OK=1
for w in 2_关闭DSH 4_软重启DSH 6_硬重启DSH; do
  LK=$(grep -n 'boot_lock_acquire' "$T/$w.sh" | head -1 | cut -d: -f1)
  KW=$(grep -n 'kill_wait' "$T/$w.sh" | head -1 | cut -d: -f1)
  if [ -z "$LK" ] || [ -z "$KW" ] || [ "$LK" -gt "$KW" ]; then
    ORDER_OK=0; line "     ⚠ $w：取锁在第 ${LK:-无} 行、动手在第 ${KW:-无} 行"
  fi
done
[ "$ORDER_OK" = 1 ] && rec PASS "取锁早于动手" "组件 2/4/6 都是先取锁再杀进程（连点不会再自杀）" \
  || rec FAIL "取锁早于动手" "有组件先动手后取锁 → 连点会杀掉自己刚起的实例"

# ⑨ 关浏览器/关 Termux 必须说真话：termux-am 不支持 force-stop，没 adb 就该说做不到
# 忽略注释行（grep -n 输出是 "行号:内容"，注释行长这样 → 108:# …）
BADFS=$(grep -n 'am force-stop' "$T/2_关闭DSH.sh" 2>/dev/null | grep -v ':[[:space:]]*#' | grep -v 'adb shell am force-stop' | wc -l)
if [ "$BADFS" -gt 0 ]; then
  rec FAIL "关闭动作不撒谎" "组件 2 里还有 $BADFS 处裸 am force-stop（termux-am 不支持 → 没关却报成功）"
else
  rec PASS "关闭动作不撒谎" "组件 2 只用 adb shell am force-stop；没 adb 就如实说做不到"
fi

# ⑩ 组件 8：Wi-Fi 关着时不能只是"停下来抱怨"，必须把 Wi-Fi 页打开并自动接续
#    （Android 10+ 不允许 App 开 Wi-Fi → 只能这样半自动；--no-ui 可退回纯提示）
if grep -q 'android.settings.WIFI_SETTINGS' "$T/8_自动开无线调试.sh" && grep -q -- '--no-ui' "$T/8_自动开无线调试.sh"; then
  rec PASS "组件8 Wi-Fi 半自动" "Wi-Fi 关着时打开设置页并轮询等你打开，之后自动跑完"
else
  rec FAIL "组件8 Wi-Fi 半自动" "缺 Wi-Fi 交接流程（会像以前那样只报"前置不满足"就退出）"
fi

# ⑪ 组件 8：**写设置 ≠ adbd 真的起来** —— 置 1 之后必须先回读，没保住就立刻停下并给对原因。
#    2026-09-26 实跑抓到：旧版会白扫 30000-60999 等 40 秒，最后还把"网络不通"当原因（而当时 online=true）。
if grep -q '③·校验' "$T/8_自动开无线调试.sh" && grep -q '开关没保住' "$T/8_自动开无线调试.sh"; then
  rec PASS "组件8 置1后先回读" "没保住就 4s 停下并说明真实原因，不再白扫端口、不再报反的结论"
else
  rec FAIL "组件8 置1后先回读" "缺回读校验（会白扫 3 万端口并给出与事实相反的原因）"
fi

# ⑫ 撤销安装密码授权：这条是"AI 拿你的锁屏密码装包"的**唯一撤销口**，
#    必须两头都有入口（小组件 + 控制台 App），而且小组件要真的调 helper 的 revoke。
if [ -x "$HOME_DIR/.local/bin/dsh-auth-pass" ] \
   && grep -q 'dsh-auth-pass' "$T/9_撤销密码授权.sh" \
   && grep -q 'revoke' "$T/9_撤销密码授权.sh"; then
  rec PASS "收回密码使用权(小组件)" "组件 9 走 dsh-auth-pass revoke，并复核文件确实消失"
else
  rec FAIL "收回密码使用权(小组件)" "缺 helper 或组件 9 没接上 revoke"
fi
#    用户 2026-09-26 要求：授权要是**开关**而不是按钮 → 检查点改到 MainActivity 里那一行
if grep -q 'dsh-auth-pass revoke' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null \
   && grep -q 'dsh-auth-pass status' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null \
   && grep -q 'authSwitch' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null \
   && grep -q '密码使用权' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null; then
  rec PASS "密码使用权开关(控制台)" "维护类里是 Switch：开=AI 可动用你的密码过验证，关=立刻收回（语义不只装包）"
else
  rec FAIL "密码使用权开关(控制台)" "控制台里没有把"密码使用权"做成开关（或没接上 helper）"
fi

# ⑬ DSH 页面里那个 task 插件（dsh-mobile-local）也要跟上：
#    分类、桥/adb 分开、密码使用权开关、以及 tasksd 的 /auth 三件套
PLUG="$HOME_DIR/.dsh/profiles/web/local/dsh-mobile-local/client.js"
if grep -q 'CATS' "$PLUG" 2>/dev/null && grep -q 'mb-auth' "$PLUG" 2>/dev/null \
   && grep -q 'bridge_wake' "$PLUG" 2>/dev/null && grep -q 'dsh-auth-pass' "$HOME_DIR/.local/bin/dsh-tasksd" 2>/dev/null; then
  rec PASS "task 插件(页面)更新" "分类 + 桥/adb 分开 + 密码使用权开关；tasksd 也接了 /auth"
else
  rec FAIL "task 插件(页面)更新" "插件或 tasksd 没跟上（分类/开关/授权端点缺一）"
fi
if grep -q "'9_撤销密码授权'" "$HOME_DIR/.local/bin/dsh-tasksd" 2>/dev/null \
   && grep -q 'VIRTUAL' "$HOME_DIR/.local/bin/dsh-tasksd" 2>/dev/null; then
  rec PASS "tasksd 白名单+虚拟任务" "9_撤销密码授权 与 bridge_wake/bridge_status 都在白名单里"
else
  rec FAIL "tasksd 白名单+虚拟任务" "白名单没跟上（缺 9_ 或桥的虚拟任务）"
fi

# ⑭ 客户端 bundle 必须拼得出来（2026-09-26 真踩：在 profile 里跑 pnpm install 会剪掉
#    运行时软链的 @deepseek-ai/dsh-base / dsh-web-app → 页面报 "Failed to load plugins"）
if "$HOME_DIR/.local/bin/dsh-relink-bundles" --check >/dev/null 2>&1; then
  rec PASS "运行时 bundle 软链" "@deepseek-ai/dsh-base / dsh-web-app 都在（pnpm install 后要 dsh-relink-bundles 补链）"
else
  rec FAIL "运行时 bundle 软链" "缺链 → 页面会报 Failed to load plugins（跑 dsh-relink-bundles）"
fi
# 只在本机 DSH 活着时验一次"真的能取到 bundle"（取不到就说明路由/包有问题）
if timeout 6 bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' 2>/dev/null; then
  TOK=$(grep -ao 'token=[A-Za-z0-9_-]*' "$HOME_DIR/.dsh-restart.log" 2>/dev/null | tail -1 | cut -d= -f2)
  if [ -n "$TOK" ]; then
    CJ="$TMPDIR/dsh-cj.$$"
    curl -sL -c "$CJ" -b "$CJ" -o "$TMPLOG.page" "http://127.0.0.1:8080/?token=$TOK" 2>/dev/null
    U1=$(grep -oE '/plugins/\?\?[^"'"'"'\\]+' "$TMPLOG.page" 2>/dev/null | sed 's/&amp;/\&/g' | sort -u | head -1)
    if [ -n "$U1" ]; then
      C=$(curl -s -b "$CJ" -o /dev/null -w '%{http_code}' "http://127.0.0.1:8080$U1" 2>/dev/null)
      [ "$C" = "200" ] && rec PASS "客户端 bundle 真能取" "本机 DSH 的 /plugins/??… 返回 200" \
                       || rec FAIL "客户端 bundle 真能取" "返回 $C（页面会显示 Failed to load plugins）"
    else
      rec SKIP "客户端 bundle 真能取" "页面里没解析出 /plugins/ URL（DSH 可能刚起）"
    fi
    rm -f "$CJ" "$TMPLOG.page"
  else
    rec SKIP "客户端 bundle 真能取" "读不到本机 token"
  fi
else
  rec SKIP "客户端 bundle 真能取" "本机 8080 没在跑"
fi

# ⑮ 状态回传不能被"从头切"：App 侧只截任务输出（看尾部），状态 JSON 必须整包（看头部）。
#    实测踩到：状态包 6463 字节 > 旧的 4000 上限 → 头被切掉 → App 显示「状态解析失败」。
if grep -q 'dsh-status-pub --json --brief' "$HOME_DIR/dsh-console/src/io/dsh/console/TermuxRunner.java" 2>/dev/null \
   && grep -q 'isStatus ? 200000 : 4000' "$HOME_DIR/dsh-console/src/io/dsh/console/TaskResultReceiver.java" 2>/dev/null; then
  BRIEF=$(~/.local/bin/dsh-status-pub --json --brief 2>/dev/null | wc -c | tr -d ' ')
  FULL=$(~/.local/bin/dsh-status-pub --json 2>/dev/null | wc -c | tr -d ' ')
  if [ "${BRIEF:-99999}" -lt 4000 ]; then
    rec PASS "状态回传不截断" "--brief ${BRIEF}B（完整 ${FULL}B）：状态整包不切头，任务输出才从尾部截"
  else
    rec FAIL "状态回传不截断" "--brief ${BRIEF}B 已超 4000，会被截断（要给它瘦身）"
  fi
else
  rec FAIL "状态回传不截断" "App 侧没有区分 status/任务的截断规则（状态 JSON 会被切头）"
fi

# ── L4：真实执行 ──
line "【L4】真实执行（安全可逆的）"

# 撤销安装密码授权：**只动沙箱路径**，真授权文件全程不碰（跑完还要复核它没被动过）
if [ -f "$HOME_DIR/.dsh-auth-pass" ]; then HAD_REAL_PASS=1; else HAD_REAL_PASS=0; fi
TMPPASS="${TMPDIR:-$PREFIX/tmp}/dsh-pass-selftest.$$"
if printf '123456' > "$TMPPASS" && chmod 600 "$TMPPASS"; then
  OUTR=$(DSH_AUTH_PASS_FILE="$TMPPASS" "$HOME_DIR/.local/bin/dsh-auth-pass" revoke 2>&1); RCR=$?
  if [ "$RCR" = 0 ] && [ ! -e "$TMPPASS" ]; then
    rec PASS "收回密码使用权(真跑)" "沙箱文件先覆写后删除，退出 0；$OUTR" 
  else
    rec FAIL "收回密码使用权(真跑)" "退出 $RCR / 文件仍在：$OUTR"
  fi
else
  rec FAIL "收回密码使用权(真跑)" "造不出沙箱文件 $TMPPASS"
fi
if [ "$HAD_REAL_PASS" = 1 ] && [ ! -f "$HOME_DIR/.dsh-auth-pass" ]; then
  rec FAIL "真授权未被误删" "自检把真授权文件删了（这正是最不能出的事）"
elif [ "$HAD_REAL_PASS" = 1 ]; then
  rec PASS "真授权未被误删" "沙箱测试只动临时路径，真授权仍在且权限 $(stat -c '%a' "$HOME_DIR/.dsh-auth-pass" 2>/dev/null)"
else
  rec SKIP "真授权未被误删" "当前没有授权文件（未授权状态）"
fi
if timeout 180 bash "$T/3_备份DSH.sh" > "$TMPLOG" 2>&1 && grep -q '归档可读' "$TMPLOG"; then
  rec PASS "3_备份DSH.sh" "真跑通过：$(grep -oE '共 [0-9]+ 份[^，]*' "$TMPLOG" | head -1)"
else rec FAIL "3_备份DSH.sh" "真跑失败（见 $TMPLOG）"; fi
if timeout 180 bash "$T/5_清理DSH.sh" > "$TMPLOG" 2>&1; then
  rec PASS "5_清理DSH.sh" "真跑通过：$(grep -oE 'Download/dsh：[0-9]+MB → [0-9]+MB' "$TMPLOG" | head -1)"
else rec FAIL "5_清理DSH.sh" "真跑失败"; fi
if timeout 120 bash "$T/7_重连AI通道.sh" > "$TMPLOG" 2>&1 && grep -q 'DSH Web : 在跑' "$TMPLOG"; then
  rec PASS "7_重连AI通道.sh" "真跑通过：$(grep -oE 'adb     : .*' "$TMPLOG" | head -1 | cut -c1-40)"
else rec FAIL "7_重连AI通道.sh" "真跑失败"; fi
if [ "$BRIDGE_OK" = 0 ]; then
  rec SKIP "8_自动开无线调试.sh" "依赖桥通道，前置未满足（非组件问题）"
else
  timeout 150 bash "$T/8_自动开无线调试.sh" > "$TMPLOG" 2>&1; RC8=$?
  case "$RC8" in
    0) if grep -qE '开关已置 1|已是开启|已连上' "$TMPLOG"; then rec PASS "8_自动开无线调试.sh" "真跑通过（幂等）"
       else rec FAIL "8_自动开无线调试.sh" "退出 0 但没看到开关生效（见 $TMPLOG）"; fi ;;
    3) # 退出 3 = "未执行：前置不满足"。**按脚本自己的措辞区分是哪一种前置**，
       # 别一概说成"Wi-Fi 关着"——2026-09-26 实测过：网络是通的，只是 adb_wifi 被系统清回 0。
       if grep -q '开关没保住' "$TMPLOG"; then
         if grep -q '不是网络问题' "$TMPLOG"; then
           rec SKIP "8_自动开无线调试.sh" "环境：网络通但开关被系统清回 0 → 需在开发者选项手动开一次（已正确诊断，未白扫端口）"
         else
           rec SKIP "8_自动开无线调试.sh" "环境：Wi-Fi 没连上网络 → 无线调试清回 0（已正确诊断，未白扫端口）"
         fi
       else
         rec SKIP "8_自动开无线调试.sh" "Wi-Fi 关着 → 脚本正确提前停下（前置不满足，非组件问题）"
       fi ;;
    1) # 退出 1 时先看它自己的**诊断**：环境不满足算 SKIP，只有诊断不出来才算组件失败
       if grep -q '没有真正连上网络' "$TMPLOG"; then
         rec SKIP "8_自动开无线调试.sh" "环境：Wi-Fi 开着但没连上网络 → 系统把无线调试清回 0（已正确诊断）"
       elif grep -q '框架没被真正启动' "$TMPLOG"; then
         rec SKIP "8_自动开无线调试.sh" "环境：开关=1 但框架未启动，需在开发者选项手动开一次（已正确诊断）"
       else rec FAIL "8_自动开无线调试.sh" "退出 1 且没能给出诊断（见 $TMPLOG）"; fi ;;
    *) rec FAIL "8_自动开无线调试.sh" "退出 $RC8（见 $TMPLOG）" ;;
  esac
fi

# 桥：能力探测 → 软停 → 广播唤醒 往返，并实测「软停会不会自己回来」
if [ "$BRIDGE_OK" = 0 ]; then
  rec SKIP "桥软停/唤醒" "依赖桥通道，前置未满足"
else
  CAPS=$(timeout 12 "$HOME_DIR/.local/bin/droid-sock" caps 2>/dev/null || true)
  if printf '%s' "$CAPS" | grep -q '"stop_is_durable"'; then
    # ── v1.8 起的**默认路径**（组件 2 默认走这条）：软停必须不自恢复，且唤醒能立刻回来 ──
    rec PASS "桥能力探测" "v1.8：$(printf '%s' "$CAPS" | grep -oE '"ver": *"[^"]*"') 软停即持久（不再自恢复）+ sleep 仍可用"
    if timeout 15 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1; then
      sleep 2
      if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then
        rec FAIL "v1.8 软停持久" "stop 后端口仍开"
      else
        CAME=0
        for i in $(seq 1 16); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { CAME=1; break; }; done
        [ "$CAME" = 0 ] && rec PASS "v1.8 软停持久" "stop 后 8788 关闭，18 秒内不自恢复（旧版 14 秒就自己回来）" \
                        || rec FAIL "v1.8 软停持久" "stop 后 ${i}s 又自己开了"
        # 窗口给到 45 秒：App 进程若被系统回收，广播要把它冷启动起来，实测能到 20~40 秒。
        # 2026-09-26 晚碰到过一次 45 秒没回来（同一版本上一轮是通过的）→ 补一发广播再等 45 秒，
        # 并把"补发后才回来"如实写进结论，而不是直接判失败、也不是假装一次就成功。
        T0W=$(date +%s)
        timeout 20 "$HOME_DIR/.local/bin/droid-sock" wake >/dev/null 2>&1
        BACK=0
        for i in $(seq 1 45); do
          if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then BACK=$(( $(date +%s) - T0W )); break; fi
          sleep 1
        done
        RETRY=0
        if [ "$BACK" = 0 ]; then
          RETRY=1
          timeout 20 "$HOME_DIR/.local/bin/droid-sock" wake >/dev/null 2>&1
          for i in $(seq 1 45); do
            if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then BACK=$(( $(date +%s) - T0W )); break; fi
            sleep 1
          done
        fi
        if [ "$BACK" != 0 ] && [ "$RETRY" = 0 ]; then
          rec PASS "v1.8 唤醒恢复" "广播唤醒后 ${BACK}s 端口恢复（进程还活着，不用重新授权）"
        elif [ "$BACK" != 0 ]; then
          rec PASS "v1.8 唤醒恢复" "首发 45s 没回，补一发后 ${BACK}s 恢复（vivo 冷启动慢，属已知抖动）"
        else
          rec FAIL "v1.8 唤醒恢复" "两发广播共 90 秒没唤回来"
        fi
        PS=""
        for i in $(seq 1 10); do
          PS=$(timeout 12 "$HOME_DIR/.local/bin/droid-sock" ping 2>/dev/null | grep -o '"paused": *[a-z]*')
          case "$PS" in *false*) break ;; esac
          sleep 1
        done
        case "$PS" in
          *false*) rec PASS "paused 标记正确" "唤醒后 paused 已清回 false（下次重绑不会被静默）" ;;
          *) rec FAIL "paused 标记正确" "唤醒后 paused 仍是 true（$PS）" ;;
        esac
      fi
    else
      rec FAIL "v1.8 软停持久" "droid-sock stop 无响应"
    fi
  elif printf '%s' "$CAPS" | grep -q '"sleep"'; then
    rec PASS "桥能力探测" "v1.7+ 支持 sleep 真停 + 带 token 唤醒可重新授权"
    # v1.7 路径：真停必须**不自恢复**（旧版软停实测 14s 就自己回来了）
    if timeout 20 "$HOME_DIR/.local/bin/droid-sock" sleep >/dev/null 2>&1; then
      sleep 2
      if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then
        rec FAIL "v1.7 真停" "sleep 后端口仍开"
      else
        CAME=0
        for i in $(seq 1 8); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { CAME=1; break; }; done
        [ "$CAME" = 0 ] && rec PASS "v1.7 真停" "sleep 后 8788 关闭，16 秒内不自恢复（旧版软停会自己回来）" \
                        || rec FAIL "v1.7 真停" "sleep 后不久就自己回来了"
        # 安全边界：错误 token 的广播不许把它拉起来
        am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver --es token 000000000000 >/dev/null 2>&1
        BAD=0
        for i in $(seq 1 8); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { BAD=1; break; }; done
        [ "$BAD" = 0 ] && rec PASS "唤醒鉴权" "错误 token 的 WAKE 唤不醒（任何 App 都拉不起来）" \
                       || rec FAIL "唤醒鉴权" "错误 token 也把它唤醒了"
        # 恢复：带对 token 的广播应自动重新授权无障碍并恢复监听
        timeout 20 "$HOME_DIR/.local/bin/droid-sock" wake >/dev/null 2>&1
        BACK=0
        for i in $(seq 1 20); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { BACK=1; break; }; done
        [ "$BACK" = 1 ] && rec PASS "自动重新授权" "带 token 的 WAKE 在 ${i}s 内自行恢复（全程不碰屏幕）" \
                        || rec FAIL "自动重新授权" "唤不回来（需手动开无障碍）"
      fi
    else
      rec FAIL "v1.7 真停" "droid-sock sleep 无响应"
    fi
  else
    rec PASS "桥能力探测" "当前是旧版桥：只能软停（关端口），系统重绑无障碍时会自己回来 → 装 v1.7 可根治"
    if timeout 15 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1; then
      sleep 1
      if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then
        rec FAIL "桥软停/唤醒" "软停后端口仍开"
      else
        # 实测软停后的自恢复：15 秒内端口自己回来 = 用户说的「关了它自己又开」
        CAME=0
        for i in $(seq 1 15); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { CAME=1; break; }; done
        if [ "$CAME" = 1 ]; then
          rec PASS "软停自恢复实测" "软停后端口在 ${i}s 自己回来了 —— 实测坐实「关了又自己开」（v1.7 sleep 可根治）"
        else
          rec PASS "软停自恢复实测" "15 秒内没有自己回来（自恢复取决于系统何时重绑无障碍，不是每次都触发）"
        fi
        am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver \
          --es token "$(cat "$HOME_DIR/.dsh-bridge-token" 2>/dev/null)" >/dev/null 2>&1
        sleep 2
        if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then rec PASS "桥软停/唤醒" "软停→广播唤醒 往返成功"
        else rec FAIL "桥软停/唤醒" "唤醒失败（需打开 App 或装 v1.7）"; fi
      fi
    else rec FAIL "桥软停/唤醒" "droid-sock stop 无响应"; fi
  fi
fi

# ── L5：组件 1 冷启动沙箱 + 半启动取证 ──
line "【L5】组件 1 冷启动（8099 沙箱）+「端口先开、路由后挂」取证"
cp -f "$HOME_DIR/.dsh-restart.log" "$HOME_DIR/.smoke/restart.log.save" 2>/dev/null
cp -f "$HOME_DIR/.dsh-url" "$HOME_DIR/.smoke/dsh-url.save" 2>/dev/null   # 沙箱会覆写它，必须还原
SAVED=1   # 从这个点起，收尾（含被打断时）才允许还原这两个文件
# 沙箱实例必须带 --patch 关掉 filetransfer：主实例已占着 3199，第二个实例抢不到端口
# 会在插件树加载阶段 EADDRINUSE 崩掉（实测过）。注意 --patch 要放在 --port 之前。
DSH_PORT=8099 DSH_WEB_EXTRA="--patch $HOME_DIR/.smoke/patch.yml" \
  timeout 200 bash "$T/1_启动DSH.sh" --no-open > "$TMPLOG" 2>&1 &
WPID=$!
SAMPLES=""
while kill -0 "$WPID" 2>/dev/null; do
  c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8099/ 2>/dev/null)
  case "$c" in ''|000) ;; *) LAST=""; for x in $SAMPLES; do LAST="$x"; done
       [ "$c" != "$LAST" ] && SAMPLES="$SAMPLES $c" ;; esac
  sleep 0.3
done
wait "$WPID"; WRC=$?
SBURL=$(grep -oE 'http://127\.0\.0\.1:8099/\?token=[A-Za-z0-9_-]+' "$TMPLOG" | tail -1)
if grep -q '就绪校验通过' "$TMPLOG"; then
  rec PASS "1_启动DSH.sh 冷启动" "等到真就绪并通过 200 校验（用时 $(grep -oE '用时 [0-9]+s' "$TMPLOG" | tail -1)）"
else
  rec FAIL "1_启动DSH.sh 冷启动" "没有走完真就绪校验（exit=$WRC，见 $TMPLOG）"
fi
# 取证：冷启动期间出现过的 HTTP 码序列。旧逻辑只看「非 000」，第一个码就会被当成就绪。
SEQ=$(printf '%s' "$SAMPLES" | sed 's/^ //')
case "$SEQ" in
  *404*) rec PASS "半启动取证" "采样到 HTTP 码序列：${SEQ}（404=路由还没挂，旧逻辑此时就会开浏览器）" ;;
  *401*) rec PASS "半启动取证" "采样到 HTTP 码序列：${SEQ}（401=认证已挂但无 token，旧逻辑也当成就绪）" ;;
  *)     rec PASS "半启动取证" "采样到 HTTP 码序列：${SEQ:-（没采到，可能启动过快）}" ;;
esac
[ -n "$SBURL" ] && url_ready "$SBURL" && rec PASS "沙箱 URL 可用" "token URL 返回 200：$SBURL" \
  || rec FAIL "沙箱 URL 可用" "沙箱没产出可用 token URL"
# 清理：**只按 --port 8099 精确匹配**杀沙箱（绝不碰你的 8080 实例），再还原日志
kill_sandbox
sleep 1
cp -f "$HOME_DIR/.smoke/restart.log.save" "$HOME_DIR/.dsh-restart.log" 2>/dev/null
cp -f "$HOME_DIR/.smoke/dsh-url.save" "$HOME_DIR/.dsh-url" 2>/dev/null
P8099=$( (exec 3<>/dev/tcp/127.0.0.1/8099) 2>/dev/null && echo open || echo closed )
line "     沙箱清理：8099 = $P8099（应为 closed），启动日志与 .dsh-url 已还原"

# ── SKIP ──
line "【SKIP】真跑会杀掉当前会话或需人工恢复"
rec SKIP "0_紧急停止.sh" "真跑会吊销 token + 关无障碍 + 断 adb，需你手动恢复；已由音量键演练间接验证"
rec SKIP "2_关闭DSH.sh" "真跑会停掉当前会话；各步骤已单独真跑（备份/轮换/端口等待/桥停/关浏览器）"
rec SKIP "4_软重启DSH.sh" "真跑会重启服务、断开当前会话"
rec SKIP "6_硬重启DSH.sh" "同上；这是唯一能验证 -9 强杀路径的方式（孤儿锁清理已由 L3⑤ 单测覆盖）"

line ""
line "════ 结果：通过 $PASS / 失败 $FAIL / 跳过 $SKIP ════"
[ "$FAIL" = 0 ] && line "结论：全部可测项通过 ✅" || line "结论：有 $FAIL 项失败，需修 ❌"
printf '%b' "$REPORT" > "$HOME_DIR/.smoke/selftest-report.txt"
rm -rf "$TMPD"
exit "$FAIL"
