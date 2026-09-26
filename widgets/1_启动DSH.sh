#!/data/data/com.termux/files/usr/bin/bash
# 1_启动DSH —— 启动 DSH Web 并打开浏览器；已在跑则直接打开（幂等）
# 2026-09-26 修正三处真问题：
#   ① 就绪判定不再只看端口。dsh web 是「先绑端口、后挂路由」：端口在插件树加载期间
#      就已监听，token 行要等整棵树加载完（15~30s）才打印，此前的请求会被回 404。
#      旧逻辑把半启动当成已就绪 → 浏览器打开就是「找不到 127.0.0.1 的网页 HTTP ERROR 404」。
#      现在：等 token 行出现 + 该 URL 跟随跳转返回 200，才认为真的可用。
#   ② 启动前取互斥锁。连点两次会同时拉起两个 dsh web，两个实例抢 .credentials.yaml
#      写锁（等待上限只有 2 秒）→ 必有一个崩在插件树加载阶段。
#   ③ 启动前清理孤儿 credentials 锁。进程被 -9 后锁文件会留下，之后每次启动都失败。
# 用法：1_启动DSH.sh [--dry-run] [--no-open]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; OPEN=1
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --no-open) OPEN=0 ;; esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1

LOG="$(dsh_log)"
OPENER="$HOME_DIR/.local/bin/dsh-browser-open"
URLFILE="$HOME_DIR/.dsh-url"

open_browser() {   # $1=url
  [ "$OPEN" = 1 ] || { warn "按要求不打开浏览器"; return 0; }
  if [ -x "$OPENER" ]; then run "'$OPENER' '$1' >/dev/null 2>&1"; else run "termux-open-url '$1'"; fi
}

# 顺带恢复 AI 通道（失败不影响启动本身）
restore_channels() {
  step "恢复 AI 控制通道（失败不影响启动）"
if [ "$DRY" = 1 ]; then printf '   · [dry] 起任务执行器 dsh-tasksd（给网页按钮用）\n'; ok "（dry-run）"
elif tasksd_ensure; then ok "任务执行器在 127.0.0.1:$TASKSD_PORT（网页右下角按钮可用）"
else warn "任务执行器没起来（网页按钮会显示读不到 token）"; fi
  if [ -x "$HOME_DIR/.local/bin/droid" ]; then
    if [ "$DRY" = 1 ]; then printf '   · [dry] droid conn\n'; ok "（dry-run）"
    elif timeout 25 "$HOME_DIR/.local/bin/droid" conn >/dev/null 2>&1; then ok "adb 已连接"
    else warn "adb 未连接（Wi-Fi 关过或重启过 → 需要先打开「无线调试」）"; fi
  fi
  if [ "$DRY" = 1 ]; then printf '   · [dry] 唤醒桥（带 token）并 ping\n'; ok "（dry-run）"
  elif bridge_alive; then ok "DSH 桥在监听"
  elif bridge_ensure; then ok "DSH 桥已唤醒（带 token 的广播可让 v1.7 重新授权无障碍）"
  else warn "DSH 桥没响应：token 被吊销 或 无障碍被关（设置→无障碍→DSH 桥）"; fi
}

step "① 拿 wakelock（防 Termux 后台被冻结）"
# Termux 在后台会被系统/vivo 冻结 → DSH 进程一起冻住，表现就是"跳转回来无法读取"。
# 拿住 wakelock 是这个问题最直接的解药（Termux 自带命令，不需要额外权限，重启手机后要重新拿）。
if need termux-wake-lock; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] termux-wake-lock\n'; ok "（dry-run）"
  elif termux-wake-lock 2>/dev/null; then ok "已拿住 wakelock"
  else warn "wakelock 没拿到"; fi
else warn "没有 termux-wake-lock 命令"; fi

step "② 检查 $DSH_PORT 是否已有服务"
if port_open "$DSH_PORT"; then
  ok "已有服务在 $DSH_PORT"
  printf '   · 如果页面「无法读取」：先下拉刷新（浏览器登录 cookie 在重启后依然有效，不必重拿 URL）\n'

  step "③ 取一个**真正可用**的认证 URL（旧 token 会 401，半启动会 404，都不能直接开）"
  U=""
  C=$(grep -oE "$TOKEN_RE" "$URLFILE" 2>/dev/null | tail -1)
  if [ -n "$C" ] && url_ready "$C"; then
    U="$C"; ok "缓存的认证 URL 仍然有效"
  elif [ -n "$C" ]; then
    warn "缓存的认证 URL 已失效（旧实例留下的 token，HTTP $(url_code "$C")）"
  fi
  if [ -z "$U" ]; then
    C=$(token_from_log "$LOG")
    if [ -n "$C" ] && url_ready "$C"; then
      U="$C"; printf '%s\n' "$U" > "$URLFILE"; ok "从启动日志取到当前实例的认证 URL，已写回 .dsh-url"
    fi
  fi
  if [ -n "$U" ]; then
    step "④ 打开浏览器"
    open_browser "$U"
  else
    warn "这个实例不是本组件启动的：它的 token 只打印在启动它的那个终端里，我拿不到"
    warn "要一个可用的认证 URL，请点「4_软重启DSH」或「6_硬重启DSH」重新取一次"
  fi
  restore_channels
  done_; exit 0
fi

step "③ 启动互斥（防连点两次拉起两个实例互相抢锁）"
if [ "$DRY" = 1 ]; then
  printf '   · [dry] mkdir %s\n' "$BOOT_LOCK_DIR"; ok "（dry-run）"
elif boot_lock_acquire; then
  ok "已取得启动锁（$BOOT_LOCK_DIR）"
  trap 'boot_lock_release' EXIT
else
  bad "另一个启动正在进行中（锁持有者 pid $(boot_lock_owner)）"
  printf '   等它跑完再点本组件；若确认没有启动在跑，删掉 %s\n' "$BOOT_LOCK_DIR"
  die "已有启动在跑"
fi

step "④ 清理孤儿 credentials 写锁"
clear_orphan_cred_lock; rc=$?
case "$rc" in
  0) ok "已清理（-9 之后留下的孤儿锁会让之后每次启动都失败）" ;;
  1) warn "锁被占用（有实例在跑），未动它" ;;
  2) warn "锁存在但删不掉，启动可能失败" ;;
  3) ok "没有残留锁" ;;
esac

step "⑤ 重置启动日志（token 只从这次的日志里取）"
rotate_log "$LOG" 2
dsh_log_reset "$LOG"
ok "启动日志已重置"

step "⑥ 后台启动 dsh web（脱离小组件会话）并等**真就绪**"
if [ "$DRY" = 1 ]; then
  printf '   · [dry] setsid nohup dsh web --port %s >> %s 2>&1 &\n' "$DSH_PORT" "$LOG"
  printf '   · [dry] 等 token 行 + curl 跟随跳转 200（最多 120 秒）\n'
  ok "（dry-run）"
elif dsh_start_and_wait "$LOG" 120; then
  ok "启动完成"
else
  rc=$?
  [ "$rc" = 3 ] && die "已有另一个启动在进行中（不是失败，是防连点）——等它跑完再点"
  need termux-notification && termux-notification --title "DSH 启动失败" \
    --content "没拿到可用认证 URL，见 $LOG" --priority high 2>/dev/null
  die "启动失败（日志尾部已打印在上面）"
fi

step "⑦ 打开浏览器"
U=$(grep -oE "$TOKEN_RE" "$URLFILE" 2>/dev/null | tail -1)
[ -n "$U" ] && open_browser "$U" || warn "没有可用认证 URL，不打开浏览器（避免开到报错页）"

restore_channels
done_
