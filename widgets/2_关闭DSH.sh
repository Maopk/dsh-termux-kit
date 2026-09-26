#!/data/data/com.termux/files/usr/bin/bash
# 2_关闭DSH —— 彻底关闭：优雅停 → 兜底强杀 → 备份 → 轮换 → 等端口释放 → **关窗口** → 软停桥 → 收任务器 → 发布状态 → 释放 wakelock
#              （默认连 AI 控制通道一起关；要保留桥通道加 --keep-bridge）
# 2026-09-27 两处修正（用户实测反馈）：
#   ① 关窗口这步以前排在"停桥"之后 → 没有 adb 时永远关不掉（Termux 的 am 无 force-stop）。
#      现在**先关窗口再停桥**，关窗口交给 ~/.local/bin/dsh-close-window（adb 优先，否则借桥按键）。
#   ② Termux 默认**不再自杀**：以前最后一步 kill -9 掉 Termux，副作用是控制台 App 立刻不可用
#      （它只能靠 Termux 的 RUN_COMMAND 干活，Termux 一死，刷新要等系统冷启动它）。
#      确实要连 Termux 一起关：加 --close-termux。
# 用法：2_关闭DSH.sh [--dry-run] [--no-backup] [--keep-browser] [--keep-bridge] [--full-stop] [--close-termux]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_BAK=1; KEEP_BROWSER=0; KEEP_TERMUX=1; KEEP_BRIDGE=0; FULL_STOP=0
# 2026-09-26 修正：--keep-bridge 以前只在文档里、没进这个 case，写了也被无视
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --no-backup) DO_BAK=0 ;; --keep-browser) KEEP_BROWSER=1 ;; --keep-termux) KEEP_TERMUX=1 ;; --close-termux) KEEP_TERMUX=0 ;; --keep-bridge) KEEP_BRIDGE=1 ;; --full-stop) FULL_STOP=1 ;; esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1
BROWSER_PKG="com.android.chrome"
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

step "① 优雅停止 dsh web（SIGTERM，最多 10 秒）"
if [ -z "$(pids_of 'bin.js web')" ]; then
  ok "没有在跑的 dsh web"
else
  kill_wait "bin.js web" 10 TERM && ok "已优雅退出" || { warn "10 秒未退出，强杀"; kill_wait "bin.js web" 5 KILL >/dev/null; ok "已强杀"; }
fi

step "② 清理灵枢子进程"
if [ -n "$(pids_of 'md_cg')" ]; then kill_wait "md_cg" 5 KILL >/dev/null; ok "md_cg 已结束"; else ok "无 md_cg 残留"; fi

step "③ 状态备份"
if [ "$DO_BAK" = 1 ] && [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-backup\n'; else
    OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -1); ok "${OUT:-已备份}"
  fi
else
  warn "跳过备份"
fi

step "④ 日志轮换（先把认证 URL 存进 .dsh-url，再清日志）"
CUR=$(token_from_log "$LOG" 2>/dev/null)
[ -n "$CUR" ] && { printf '%s\n' "$CUR" > "$HOME_DIR/.dsh-url"; ok "认证 URL 已存档到 .dsh-url"; } \
              || warn "日志里没有 token 行（本次实例可能不是本组件启动的）"
rotate_log "$(dsh_log)" 2
dsh_log_reset "$(dsh_log)"
ok "启动日志已重置"

step "⑤ 等端口 $DSH_PORT 释放（最多 15 秒）"
if [ "$DRY" = 1 ]; then printf '   · [dry] 跳过（dry-run 没真停进程，端口不会释放）\n'; else
wait_port_free "$DSH_PORT" 15 && ok "端口已释放" || { bad "端口仍被占用"; need fuser && fuser -k "$DSH_PORT"/tcp 2>/dev/null; sleep 1; port_open "$DSH_PORT" && die "端口仍占用" || ok "已强制释放"; }; fi

step "⑥ 关闭 DSH 窗口（**必须在停桥之前**：没有 adb 时只能借桥的手）"
# 2026-09-27 修正：原来这步排在"停桥"之后，于是没有 adb 时它永远是假的——
#   Termux 的 am 没有 force-stop 子命令，唯一能关别人窗口的通道是无障碍桥（已在 ⑦ 被停掉）。
#   现在：有 adb 走 adb force-stop；没有就借桥（把窗口带到前台 + 连按返回键直到它消失）。
if [ "$KEEP_BROWSER" = 0 ]; then
  if [ "$DRY" = 1 ]; then
    printf '   · [dry] dsh-close-window（adb 优先，否则借桥按键）\n'; ok "（dry-run）"
  elif [ -x "$HOME_DIR/.local/bin/dsh-close-window" ] && "$HOME_DIR/.local/bin/dsh-close-window"; then
    ok "浏览器 / PWA 窗口已关闭"
  else
    warn "窗口没能关掉（原因见上面那几行）—— 可从最近任务手动划掉，或开 adb 让我关"
  fi
else
  warn "按要求保留浏览器窗口"
fi

step "⑦ 关闭 AI 控制通道（DSH 桥）"
# 为什么不能只「软停」：软停只关 8788 端口，无障碍服务仍然启用；系统随时会重新绑定
# 这个服务 → onServiceConnected 又把端口和常驻通知拉回来。这就是「明明关了它自己又开」。
# 【2026-09-26 晚 按实测重定】
#   · 默认＝**软停**（stop）：关端口 + 撤常驻通知，但 App 进程留着。桥 v1.8 起会记住
#     "用户要求停着"，所以系统重绑无障碍时**不会再自己开**（这正是你要的效果），
#     而且进程还在 → 点组件 1/7/8 一叫就回来，零成本。
#   · --full-stop ＝ sleep（真停：连无障碍都关掉）。实测代价：App 进程彻底退出后，
#     vivo 会拦住广播不让它自启（今天就是这么失联的）→ 很可能需要你手动开一次无障碍。
#     所以它不再是默认，只在你明确要"拔干净"时用。
if [ "$KEEP_BRIDGE" = 0 ]; then
  if [ "$DRY" = 1 ]; then
    printf '   · [dry] droid-sock %s\n' "$([ "$FULL_STOP" = 1 ] && echo 'sleep（真停）' || echo 'stop（软停，v1.8 起不会自恢复）')"
    ok "（dry-run）"
  else
    CAPS=$(timeout 12 "$HOME_DIR/.local/bin/droid-sock" caps 2>/dev/null || true)
    if [ "$FULL_STOP" = 1 ]; then
      if printf '%s' "$CAPS" | grep -q '"sleep"'; then
        if timeout 15 "$HOME_DIR/.local/bin/droid-sock" sleep >/dev/null 2>&1; then
          sleep 1
          port_open 8788 && warn "桥仍占着 8788，稍等再看" || ok "桥已彻底停止（端口/通知/无障碍全关）"
          warn "注意：真停之后 App 进程会退出，vivo 可能不允许广播把它拉起来 —— 唤不醒就手动开一次无障碍"
        else warn "sleep 指令没成功（桥可能已经停着）"; fi
      else
        warn "当前桥版本没有 sleep，退回软停"
        timeout 12 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1 && ok "已软停" || warn "桥没响应（可能本来就停着）"
      fi
    else
      if timeout 12 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1; then
        ok "桥已软停：8788 端口关闭、常驻通知已撤"
        if printf '%s' "$CAPS" | grep -q '"stop_is_durable"'; then
          ok "v1.8：已记住"别再自己开" → 系统重绑无障碍也不会自己恢复"
        else
          warn "当前桥版本较旧：软停后系统重绑无障碍时它可能自己回来；装 v1.8 可根治"
        fi
        ok "恢复方式：点组件 1/7/8 即可（进程还在，一叫就回来）"
      else
        warn "桥没响应（可能本来就停着，或 App 被系统清掉了）"
      fi
    fi
  fi
else
  warn "按要求保留桥通道（--keep-bridge）"
fi

step "⑧ 收走任务执行器（它只服务于网页按钮）"
if [ "$DRY" = 1 ]; then printf '   · [dry] tasksd_stop\n'; ok "（dry-run）"
elif tasksd_alive; then tasksd_stop; sleep 1; tasksd_alive && warn "还在跑" || ok "已停止"; else ok "本来就没跑"; fi

step "⑨ 发布状态 + 释放启动锁（都必须在「可能杀 Termux」之前做）"
publish_status
# 显式释放启动锁：第 ⑩ 步若真去 kill -9 Termux，EXIT trap 不会执行 →
# 会留下孤儿锁，下次「启动 DSH」直接报"已有启动在跑"（实测踩过这个坑）。
boot_lock_release && ok "启动锁已释放"

step "⑩ 释放 wakelock；Termux 默认保留（--close-termux 才关）"
if need termux-wake-unlock; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] termux-wake-unlock\n'; ok "（dry-run）"
  else termux-wake-unlock 2>/dev/null && ok "已释放 wakelock（DSH 都关了就没必要继续常驻）" || warn "释放 wakelock 失败（可能本来就没拿）"; fi
fi
if [ "$KEEP_TERMUX" = 0 ]; then
  printf '   · 3 秒后关闭 Termux…（--close-termux）\n'; [ "$DRY" = 1 ] || sleep 3
  if [ "$DRY" = 1 ]; then printf '   · [dry] 结束 Termux App 进程（cmdline=com.termux）\n'
  else
    TPID=$(for p in /proc/[0-9]*; do c=$(tr -d '\0' < "$p/cmdline" 2>/dev/null); [ "$c" = "com.termux" ] && basename "$p"; done | head -1)
    if [ -n "$TPID" ]; then kill -9 "$TPID" 2>/dev/null && ok "已结束 Termux App 进程（pid $TPID）"; else warn "没找到 Termux App 进程"; fi
  fi
else
  ok "Termux 保留（默认）：控制台 App 立刻可用；DSH/桥/任务器都已停，几乎不耗电"
  printf '     （要连 Termux 一起关：加 --close-termux）\n'
fi
done_
