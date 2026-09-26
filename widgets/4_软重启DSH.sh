#!/data/data/com.termux/files/usr/bin/bash
# 4_软重启DSH —— 不杀进程：仅 SIGTERM 优雅停服务 → 备份 → 轮换日志 → 等端口 → 启动 → 校验
# 15 秒不退就放弃（绝不 -9），并提示改用「6_硬重启DSH」
# 用法：4_软重启DSH.sh [--dry-run] [--no-backup]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_BAK=1
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --no-backup) DO_BAK=0 ;; esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1
LOG="$(dsh_log)"

# ── 启动互斥：**在最开头就取锁**（2026-09-26 修）──
# 为什么必须在"杀进程之前"取：以前锁只在启动那一刻取，于是连点两次时，
# 第二次的第①步会把第一次刚拉起来的新实例 -9 掉 → 第一次报"进程在启动过程中退出了"。
# 实测就是这么坏的（组件 4 日志：一次失败 + 一次成功、输出还交错在一起）。
if [ "$DRY" != 1 ]; then
  if boot_lock_acquire; then
    trap 'boot_lock_release' EXIT
  else
    bad "另一个启动/重启正在进行中（锁持有者 pid $(boot_lock_owner)）"
    printf '   等它跑完再点；确认没有在跑的话删掉 %s\n' "$BOOT_LOCK_DIR"
    die "已有启动在跑（防连点）"
  fi
fi

step "① 优雅停止（SIGTERM，最多 15 秒；不升级强杀）"
if [ -z "$(pids_of 'bin.js web')" ]; then
  ok "本来就没在跑，直接进入启动"
else
  T=$(date +%s)
  kill_wait "bin.js web" 15 TERM && ok "已优雅退出（$(( $(date +%s) - T ))s）" || {
    bad "15 秒仍未退出——**软重启放弃**，未做任何强杀"
    printf '   请改用小组件「6_硬重启DSH」（它会 -9 强杀）\n'
    printf '   当前进程：%s\n' "$(pids_of 'bin.js web')"
    exit 2
  }
fi

step "② 状态备份"
if [ "$DO_BAK" = 1 ] && [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  [ "$DRY" = 1 ] && printf '   · [dry] dsh-backup\n' || { OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -1); ok "${OUT:-已备份}"; }
else warn "跳过备份"; fi

step "③ 日志轮换"
rotate_log "$LOG" 2; run ": > '$LOG'"; ok "启动日志已重置"

step "④ 等端口 $DSH_PORT 释放"
if [ "$DRY" = 1 ]; then printf '   · [dry] 跳过（dry-run 没真停进程）\n'; else
wait_port_free "$DSH_PORT" 15 && ok "端口空闲" || die "端口仍被占用，软重启中止"; fi

step "⑤ 重新启动（后台脱离）并等**真就绪**"
# 只看端口会误判：端口先开、路由后挂，半启动的服务对任何请求都回 404。
# 真就绪 = 日志出现 token 行 + 该 URL 跟随跳转返回 200。
if [ "$DRY" = 1 ]; then
  printf '   · [dry] setsid nohup dsh web --port %s >> %s 2>&1 &\n' "$DSH_PORT" "$LOG"
  printf '   · [dry] 等 token 行 + curl 跟随跳转 200（最多 120 秒）\n'
  ok "（dry-run）"
elif dsh_start_and_wait "$LOG" 120; then
  ok "启动完成"
  printf '   · 刷新浏览器页面即可（登录 cookie 重启后仍有效）\n'
else
  rc=$?
  [ "$rc" = 3 ] && die "已有另一个启动在进行中（不是失败，是防连点）——等它跑完再点"
  need termux-notification && termux-notification --title "DSH 软重启失败" \
    --content "没拿到可用认证 URL，见 $LOG" --priority high 2>/dev/null
  die "启动失败（日志尾部已打印在上面）"
fi
done_
