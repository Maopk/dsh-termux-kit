#!/data/data/com.termux/files/usr/bin/bash
# 6_硬重启DSH —— 最彻底：优雅停 → -9 强杀所有相关进程 → 备份 → 轮换 → 等端口 → 启动 → 真就绪校验
# 插件改动后用它；客户端模块带 rev，重启后**刷新页面**即可，不需要关浏览器
# 用法：6_硬重启DSH.sh [--dry-run] [--no-backup] [--close-browser]（--keep-browser 为兼容保留）
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_BAK=1; CLOSE_BROWSER=0
for a in "$@"; do case "$a" in
  --dry-run) DRY=1 ;;
  --no-backup) DO_BAK=0 ;;
  --close-browser) CLOSE_BROWSER=1 ;;
  --keep-browser) CLOSE_BROWSER=0 ;;   # 旧参数：现在的默认就是保留，写了也无害
esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1
LOG="$(dsh_log)"; BROWSER_PKG="com.android.chrome"

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

step "① 优雅停止（最多 8 秒）"
[ -n "$(pids_of 'bin.js web')" ] && { kill_wait "bin.js web" 8 TERM && ok "优雅退出" || warn "未退出，转强杀"; } || ok "没有在跑"

step "② 强杀所有相关进程（-9）"
K=0
for pat in "bin.js web" "md_cg" "dsh-termux-runtime" "dsh web"; do
  P=$(pids_of "$pat"); [ -z "$P" ] && continue
  if [ "$DRY" = 1 ]; then printf '   · [dry] kill -9 %s（%s）\n' "$P" "$pat"; continue; fi
  for p in $P; do kill -9 "$p" 2>/dev/null && K=$((K+1)); done
  sleep 0.3
done
ok "已强杀 $K 个进程"
if [ "$DRY" = 1 ]; then printf '   · [dry] 跳过残留检查（dry-run 不会真杀）\n'; elif [ -n "$(pids_of 'bin.js web')" ]; then die "仍有 dsh web 残留：$(pids_of 'bin.js web')"; else ok "确认无 dsh web 残留"; fi

step "②·补 清理孤儿 credentials 写锁（-9 的必然后果，不清则之后永远启动不了）"
if [ "$DRY" = 1 ]; then printf '   · [dry] 无实例时删除 %s\n' "$CRED_LOCK"; ok "（dry-run）"
else
  clear_orphan_cred_lock; rc=$?
  case "$rc" in
    0) ok "已清理孤儿锁（dsh-atomic-write 的等待上限只有 2 秒，孤儿锁会让每次启动都失败）" ;;
    1) warn "锁被占用（还有实例在跑），未动它" ;;
    2) bad "锁存在但删不掉，启动很可能失败" ;;
    3) ok "没有残留锁" ;;
  esac
fi

step "③ 状态备份"
if [ "$DO_BAK" = 1 ] && [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  [ "$DRY" = 1 ] && printf '   · [dry] dsh-backup\n' || { OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -1); ok "${OUT:-已备份}"; }
else warn "跳过备份"; fi

step "④ 日志轮换"
rotate_log "$LOG" 2; run ": > '$LOG'"; ok "启动日志已重置"

step "⑤ 等端口 $DSH_PORT 释放"
if [ "$DRY" = 1 ]; then printf '   · [dry] 跳过（dry-run 没真杀进程）\n'; elif ! wait_port_free "$DSH_PORT" 15; then
  warn "端口仍占用，尝试强制释放"
  run "fuser -k '$DSH_PORT'/tcp 2>/dev/null || true"; sleep 1
  port_open "$DSH_PORT" && die "端口 $DSH_PORT 无法释放" || ok "已强制释放"
else ok "端口空闲"; fi

step "⑥ 浏览器与 PWA（默认**不动**你正在看的页面）"
# 2026-09-26 改：以前这里默认 force-stop Chrome，理由是"保证新客户端模块加载"。
# 但客户端模块 URL 带 rev（内容变则 rev 变），**普通刷新就够了**；而强杀浏览器的代价是：
#   ① 你正在看的对话页当场消失；
#   ② 标签被系统恢复时可能正好落在"服务还在启动"的窗口里 → 服务对一切请求回 404
#      → 余额小鲸鱼挂件的配置 GET 失败 → 弹「设置读取失败，已暂停保存以免覆盖你的原有设置」。
# 所以默认改成保留；确实想连浏览器一起关，加 --close-browser。
if [ "$CLOSE_BROWSER" = 1 ]; then
  run "am force-stop '$BROWSER_PKG'"; ok "已关闭 $BROWSER_PKG"
  run "pkill -f webapk 2>/dev/null || true"; ok "已结束 PWA(webapk) 进程"
else
  ok "保留浏览器（重启完成后**刷新页面**即可加载新客户端模块；登录 cookie 重启后依然有效）"
fi

step "⑦ 启动（后台脱离）"
if [ "$DRY" = 1 ]; then printf '   · [dry] setsid nohup dsh web --port %s >> %s 2>&1 &\n' "$DSH_PORT" "$LOG"; ok "（dry-run）"
elif boot_lock_acquire; then
  trap 'boot_lock_release' EXIT; ok "已取得启动锁"
else
  bad "另一个启动正在进行中（pid $(boot_lock_owner)），等它跑完再点"; die "已有启动在跑"
fi

step "⑧ 就绪校验（等 token 行 + 跟随跳转 200，不再只看端口）"
if [ "$DRY" = 1 ]; then
  printf '   · [dry] 等 token 行 + curl 跟随跳转 200（最多 120 秒）\n'; ok "（dry-run）"
elif dsh_start_and_wait "$LOG" 120; then
  ok "启动完成"
  printf '   · 刷新浏览器页面即可（登录 cookie 重启后仍有效）\n'
else
  rc=$?
  [ "$rc" = 3 ] && die "已有另一个启动在进行中（不是失败，是防连点）——等它跑完再点"
  need termux-notification && termux-notification --title "DSH 硬重启失败" \
    --content "没拿到可用认证 URL，见 $LOG" --priority high 2>/dev/null
  die "启动失败（日志尾部已打印在上面）"
fi

step "⑨ 顺带恢复 AI 通道"
if [ -x "$HOME_DIR/.local/bin/droid" ]; then
  [ "$DRY" = 1 ] && printf '   · [dry] droid conn\n' || {
    timeout 25 "$HOME_DIR/.local/bin/droid" conn >/dev/null 2>&1 && ok "adb 已连接" \
      || warn "adb 未连接（Wi-Fi 关过或重启过 → 先开一次「无线调试」）"; }
fi
if [ "$DRY" = 1 ]; then printf '   · [dry] 唤醒桥（带 token）并 ping\n'; ok "（dry-run）"
elif bridge_alive; then ok "DSH 桥在监听"
elif bridge_ensure; then ok "DSH 桥已唤醒"
else warn "DSH 桥没响应：token 被吊销 或 无障碍被关（设置→无障碍→DSH 桥）"; fi
done_
