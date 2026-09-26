#!/data/data/com.termux/files/usr/bin/bash
# ============ DSH 小组件公共库（所有 task 脚本共用）============
# 设计原则：每步一句话 + ✔/✘；能验证的必须验证；等待一律轮询而非死睡；幂等、可 --dry-run
set -u

DSH_PORT="${DSH_PORT:-8080}"
HOME_DIR="/data/data/com.termux/files/home"
SHARED="$HOME_DIR/storage/shared"
DL="$SHARED/Download"
DSH_DIR="$DL/dsh"
LOGS="$HOME_DIR/.smoke"
DRY=0
T0=$(date +%s)

SELF="$(basename "$0")"
log()  { printf '%s %s\n' "$(date '+%H:%M:%S')" "$*"; }
step() { printf '\n▶ %s\n' "$*"; }
ok()   { printf '   ✔ %s\n' "$*"; }
warn() { printf '   ⚠ %s\n' "$*"; }
bad()  { printf '   ✘ %s\n' "$*" >&2; }
die()  { bad "$*"; publish_status; printf '\n【%s】失败（用时 %ss）\n' "$SELF" "$(( $(date +%s) - T0 ))"; exit 1; }
done_() { publish_status; printf '\n【%s】完成，用时 %ss\n' "$SELF" "$(( $(date +%s) - T0 ))"; }
need()  { command -v "$1" >/dev/null 2>&1; }
run()   { if [ "$DRY" = 1 ]; then printf '   · [dry] %s\n' "$*"; else eval "$@"; fi; }

parse_args() { for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; esac; done; [ "$DRY" = 1 ] && log "== dry-run 模式：只打印不执行 =="; }

# ---- 网络/进程探测（bash 内建 /dev/tcp，零依赖）----
port_open() { (exec 3<>/dev/tcp/127.0.0.1/"$1") 2>/dev/null && exec 3<&- 3>&- && return 0 || return 1; }
wait_port_open() { local p=$1 t=${2:-40} i=0; while [ $i -lt $((t*4)) ]; do port_open "$p" && return 0; sleep 0.25; i=$((i+1)); done; return 1; }
wait_port_free() { local p=$1 t=${2:-20} i=0; while [ $i -lt $((t*4)) ]; do port_open "$p" || return 0; sleep 0.25; i=$((i+1)); done; return 1; }
http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$1/" 2>/dev/null; }

# ---- 进程 ----
pids_of()  { pgrep -f "$1" 2>/dev/null | tr '\n' ' '; }
kill_wait() {   # $1=模式 $2=秒 $3=信号
  local pat="$1" t="${2:-10}" sig="${3:-TERM}" i=0
  if [ "${DRY:-0}" = 1 ]; then printf '   · [dry] kill -%s 匹配 %s 的进程\n' "$sig" "$pat"; return 0; fi
  local ps; ps=$(pids_of "$pat")
  [ -z "$ps" ] && return 0
  for p in $ps; do kill -"$sig" "$p" 2>/dev/null; done
  while [ $i -lt $((t*4)) ]; do ps=$(pids_of "$pat"); [ -z "$ps" ] && return 0; sleep 0.25; i=$((i+1)); done
  return 1
}

# ============ DSH 启动 / 真就绪判定（2026-09-26 修正「404 半启动」误判）============
# 为什么不能只看端口：dsh web 是「先绑端口、后挂路由」——
#   端口在插件树加载期间就已监听，而 `dsh web: http://…/?token=…` 这行
#   要等整棵树加载完（15~30s）才打印；此前任何请求都还没挂上处理器 → 返回 404。
#   端口一开就开浏览器，看到的就是 404（拿旧 token 则是 401）。
# 因此「就绪」= ① 日志里出现 token 行 ② 该 URL 跟随 303 后能拿 200 ③ 进程还活着。
CRED_LOCK="$HOME_DIR/.dsh/.credentials.yaml.lock"
# 启动锁**按端口区分**：沙箱测试跑在 8099，绝不能和你的 8080 抢同一把锁。
# （之前共用一把 → 我的测试正拿着锁时你点「6_硬重启」，就会看到"已有另一个启动在进行中"）
BOOT_LOCK_DIR="$HOME_DIR/.dsh-boot-${DSH_PORT}.lock"
BOOT_LOCK_HELD=0
TOKEN_RE='http://127\.0\.0\.1:[0-9]+/\?token=[A-Za-z0-9_-]+'

dsh_pids()  { pgrep -f 'bin[.]js web' 2>/dev/null; }   # 中括号：避免匹配到本条命令行自己
dsh_alive() { [ -n "$(dsh_pids)" ]; }
dsh_log_reset() { if [ "${DRY:-0}" = 1 ]; then printf '   · [dry] 重置启动日志\n'; else : > "$1"; fi; }
token_from_log() { local f="${1:-$(dsh_log)}"; [ -f "$f" ] && grep -oE "$TOKEN_RE" "$f" | tail -1; }

# 认证 URL 真能用才算数：无 token → 401；旧 token → 401；半启动 → 404；只有 200 才是通的
url_code()  { curl -sL -c /dev/null -o /dev/null -w '%{http_code}' --max-time 8 "$1" 2>/dev/null; }
url_ready() { [ "$(url_code "$1")" = "200" ]; }

# 启动互斥：防止连点两次小组件同时拉起两个 dsh web（两个实例抢 credentials 写锁 → 必崩一个）
# 幂等：同一进程重复调用直接成功（否则「组件先取锁 + 公共启动函数再取锁」会自己挡自己）
# 陈旧判定有两条，缺一不可：
#   ① 记的 pid 已死；② 锁本身超过 5 分钟（pid 会被系统复用，光看 pid 可能永远"活着"）
boot_lock_stale() {
  local p; p=$(cat "$BOOT_LOCK_DIR/pid" 2>/dev/null)
  if [ -z "$p" ] || ! kill -0 "$p" 2>/dev/null; then return 0; fi
  [ -n "$(find "$BOOT_LOCK_DIR" -maxdepth 0 -mmin +5 2>/dev/null)" ] && return 0
  return 1
}
boot_lock_acquire() {
  local p; p=$(cat "$BOOT_LOCK_DIR/pid" 2>/dev/null)
  if [ -d "$BOOT_LOCK_DIR" ] && [ "$p" = "$$" ]; then BOOT_LOCK_HELD=1; return 0; fi
  if mkdir "$BOOT_LOCK_DIR" 2>/dev/null; then printf '%s' "$$" > "$BOOT_LOCK_DIR/pid"; BOOT_LOCK_HELD=1; return 0; fi
  if boot_lock_stale; then
    rm -rf "$BOOT_LOCK_DIR" 2>/dev/null
    mkdir "$BOOT_LOCK_DIR" 2>/dev/null || return 1
    printf '%s' "$$" > "$BOOT_LOCK_DIR/pid"; BOOT_LOCK_HELD=1; return 0
  fi
  return 1
}
boot_lock_release() { [ "$BOOT_LOCK_HELD" = 1 ] && rm -rf "$BOOT_LOCK_DIR" 2>/dev/null; BOOT_LOCK_HELD=0; return 0; }
boot_lock_owner() { cat "$BOOT_LOCK_DIR/pid" 2>/dev/null; }

# 陈旧 credentials 写锁：dsh-atomic-write 的等待上限只有 2 秒，且「孤儿锁只能人工清理」。
# 进程被 -9 时（组件 6 就是这么干的）锁文件会永久留下 → 之后每次启动都必然失败。据此规则：
# 没有任何 dsh 实例在跑时它就是孤儿，可以删。返回 0=已清 1=有实例在跑没动 2=删不掉 3=本来没有
clear_orphan_cred_lock() {   # $1=锁路径（默认真实路径，便于单测）
  local lk="${1:-$CRED_LOCK}"
  [ -f "$lk" ] || return 3
  dsh_alive && return 1
  rm -f "$lk" 2>/dev/null && return 0 || return 2
}

# 启动 dsh web，回显新进程 pid（setsid 会 fork，$! 不是真 pid → 用前后进程表差集）
# DSH_WEB_EXTRA 可传额外参数（沙箱测试用 "--patch ~/.smoke/patch.yml" 关掉 filetransfer，
# 否则第二个实例会抢 3199 端口，插件树加载直接失败）。
# 注意顺序：dsh web 的参数解析是「遇到第一个未知选项就开始透传」，所以 --patch 这类
# 子命令自己的选项必须放在 --port **之前**，写成 `dsh web --port 8099 --patch F` 会报
# unknown option '--patch'。
dsh_start() {   # $1=日志文件
  local f="$1" before after newp x i=0
  before=$(dsh_pids | tr '\n' ' ')
  # DISPLAY 是给 `dsh-host-open-in-app` 看的：Linux 分支下它用
  #   `present(env.DISPLAY) || present(env.WAYLAND_DISPLAY)` 判定"有没有桌面"，
  # 没有就拒绝执行打开动作（deliverables 里那句"此主机没有可用的桌面…"）。
  # Termux 上真正干活的是自带的 xdg-open（它就是 termux-open → 交给安卓的查看器），
  # 所以我们只是把"有桌面"这个开关打开，好让 DSH 肯调用 xdg-open。
  # 不想要就设 DSH_DISPLAY="" 再启动。
  DISPLAY="${DSH_DISPLAY-:0}" setsid nohup dsh web ${DSH_WEB_EXTRA:-} --port "$DSH_PORT" >>"$f" 2>&1 </dev/null &
  while [ $i -lt 40 ]; do
    after=$(dsh_pids | tr '\n' ' ')
    newp=""
    for x in $after; do case " $before " in *" $x "*) ;; *) newp="$x"; break ;; esac; done
    [ -n "$newp" ] && { printf '%s' "$newp"; return 0; }
    sleep 0.25; i=$((i+1))
  done
  return 1
}

# 等真就绪：0=成功（回显 URL）1=超时 2=进程中途退出
wait_dsh_ready() {   # $1=超时秒 $2=日志 $3=pid（可空）
  local t="${1:-90}" f="$2" pid="${3:-}" i=0 u
  while [ $i -lt $((t*2)) ]; do
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then return 2; fi
    u=$(token_from_log "$f")
    [ -n "$u" ] && url_ready "$u" && { printf '%s\n' "$u"; return 0; }
    sleep 0.5; i=$((i+1))
  done
  return 1
}

# 起服务 + 等真就绪 + 写回 ~/.dsh-url；失败时打印日志尾巴（含崩溃原因）
dsh_start_and_wait() {   # $1=日志 $2=超时秒
  local f="$1" t="${2:-90}" pid="" U="" rc=0
  # 启动互斥下沉到这里：所有调用方（组件 1/4/6、dsh-restart）都自动受保护，
  # 免得以后新增入口忘了取锁，又出现"连点两次 → 两个实例抢写锁 → 崩一个"。
  if ! boot_lock_acquire; then
    bad "另一个启动正在进行中（锁持有者 pid $(boot_lock_owner)）——等它跑完再试，别连点"
    return 3
  fi
  clear_orphan_cred_lock; rc=$?
  [ "$rc" = 0 ] && ok "清掉了陈旧的 credentials 写锁（不清则启动必然失败）"
  [ "$rc" = 1 ] && warn "credentials 写锁被占用（有实例在跑），未动它"
  pid=$(dsh_start "$f") || { bad "dsh web 进程没起来"; tail -6 "$f" 2>/dev/null | sed 's/^/     /'; return 1; }
  ok "进程已起（pid $pid）"
  U=$(wait_dsh_ready "$t" "$f" "$pid"); rc=$?
  case "$rc" in
    0) printf '%s\n' "$U" > "$HOME_DIR/.dsh-url"
       ok "就绪校验通过：认证 URL 跟随跳转返回 200"
       printf '   · %s\n' "$U"; return 0 ;;
    2) bad "进程在启动过程中退出了（多半是插件树加载失败）"
       tail -10 "$f" 2>/dev/null | sed 's/^/     /'; return 2 ;;
    *) bad "${t} 秒内没等到可用的认证 URL"
       tail -10 "$f" 2>/dev/null | sed 's/^/     /'; return 1 ;;
  esac
}

# ---- 唤醒/确认 DSH 桥（2026-09-26 修正：广播必须带 token）----
# 为什么必须带 token：桥 v1.7 起，服务已被关掉（sleep/panic/被系统解绑）时，
# 只有**带对 token** 的 WAKE 才允许把自己写回系统无障碍列表——这是安全边界。
# 我当初只改了 droid-sock，忘了组件里那几处裸 `am broadcast` → 于是组件 2 用了 sleep 之后，
# 组件 1/6/7/8 全都唤不醒桥（实测踩到：组件 8 日志里"桥起不来"）。
bridge_token() { [ -f "$HOME_DIR/.dsh-bridge-token" ] && cat "$HOME_DIR/.dsh-bridge-token" 2>/dev/null; }

bridge_wake() {   # 发一次唤醒广播（带 token；没有 token 只能做弱唤醒）
  local tok; tok=$(bridge_token)
  if [ -n "$tok" ]; then
    am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver --es token "$tok" >/dev/null 2>&1
    sleep 2; return 0
  fi
  warn "没有 ~/.dsh-bridge-token（被吊销过？）→ 只能弱唤醒：服务已关时唤不醒"
  am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver >/dev/null 2>&1
  sleep 2; return 0
}

bridge_alive() {   # 端口在听 ≠ 能用：被冻结时端口在听却不答应，所以真 ping 一次
  [ -x "$HOME_DIR/.local/bin/droid-sock" ] || return 1
  timeout 12 "$HOME_DIR/.local/bin/droid-sock" ping >/dev/null 2>&1
}

bridge_ensure() {  # 能用=0；不能就唤醒重试（最多两拍，系统绑无障碍有时要两拍）
  bridge_alive && return 0
  bridge_wake; bridge_alive && return 0
  bridge_wake; bridge_alive
}


# ---- 任务执行器（给网页界面的快捷按钮用）----
# 只监听回环、白名单 + token；随 DSH 一起起，随 DSH 一起收。
TASKSD_PORT=8787
tasksd_pids() { pgrep -f '[d]sh-tasksd' 2>/dev/null; }
tasksd_alive() { port_open "$TASKSD_PORT"; }
tasksd_ensure() {
  tasksd_alive && return 0
  [ -x "$HOME_DIR/.local/bin/dsh-tasksd" ] || return 1
  setsid nohup "$HOME_DIR/.local/bin/dsh-tasksd" >>"$HOME_DIR/.smoke/tasksd.log" 2>&1 </dev/null &
  local i=0; while [ $i -lt 20 ]; do tasksd_alive && return 0; sleep 0.25; i=$((i+1)); done
  return 1
}
tasksd_stop() {
  local ps; ps=$(tasksd_pids); [ -z "$ps" ] && return 0
  for x in $ps; do kill "$x" 2>/dev/null; done
  return 0
}


# ---- 状态发布（给外部 App / 你自己看）----
# 把状态写进共享存储 Download/dsh/状态/status.json，控制台 APK 靠它显示状态灯。
# 组件跑完/失败时都刷新一次；失败不影响组件本身。
publish_status() {
  [ -x "$HOME_DIR/.local/bin/dsh-status-pub" ] || return 0
  if [ "${DRY:-0}" = 1 ]; then printf '   · [dry] 发布状态到 Download/dsh/状态/\n'; return 0; fi
  timeout 40 "$HOME_DIR/.local/bin/dsh-status-pub" --quiet >/dev/null 2>&1 || true
  return 0
}

# ---- 日志 ----
rotate_log() {  # $1=文件 $2=保留MB（默认2）
  local f="$1" mb="${2:-2}"
  [ -f "$f" ] || return 0
  local sz=$(( $(stat -c%s "$f" 2>/dev/null || echo 0) / 1048576 ))
  [ "$sz" -lt "$mb" ] && return 0
  run "mv -f '$f' '$f.bak' && : > '$f'"
  ok "日志轮换: $(basename "$f") (${sz}MB → .bak)"
}

# ---- 常用路径 ----
dsh_log() { echo "$HOME_DIR/.dsh-restart.log"; }

# ============ 组件输出同时落盘（2026-09-26 加）============
# 为什么需要：小组件的输出只出现在 Termux:Widget 弹出的那个会话里，会话一关就没了
# → 你说"这次又有问题"的时候，我手上没有任何证据，只能猜。
# 现在每个组件都会把自己这轮的完整输出追加到 ~/.smoke/widget-<名字>.log，
# 出问题我自己去读，不用你复述。设 DSH_WIDGET_LOG=0 可关闭（测试里用得上）。
if [ "${DSH_WIDGET_LOG:-1}" = 1 ] && [ -z "${DSH_WIDGET_LOG_DONE:-}" ]; then
  case "$SELF" in
  *.sh)   # 只对真正的小组件脚本落盘；被 bash -c 之类 source 时不生成 widget-bash.log 这种垃圾
    DSH_WIDGET_LOG_DONE=1   # 故意**不 export**：导出了会传给孩子进程（套件→组件、组件→组件），
                            # 子组件就会以为自己已经记过日志而跳过自己的日志（实测踩到）
    WLOG="$LOGS/widget-${SELF%.sh}.log"
    mkdir -p "$LOGS" 2>/dev/null
    # 只留最近 ~256KB，免得越滚越大
    if [ -f "$WLOG" ] && [ "$(stat -c%s "$WLOG" 2>/dev/null || echo 0)" -gt 262144 ]; then
      tail -c 32768 "$WLOG" > "$WLOG.tmp" 2>/dev/null && mv -f "$WLOG.tmp" "$WLOG" 2>/dev/null
    fi
    printf '\n===== %s  %s 开始（pid %s）=====\n' "$(date '+%F %T')" "$SELF" "$$" >> "$WLOG" 2>/dev/null
    exec > >(tee -a "$WLOG") 2>&1
    ;;
  esac
fi
