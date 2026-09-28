#!/data/data/com.termux/files/usr/bin/bash
# 1_start-dsh — start DSH Web and open the browser; if it is already running, just open it (idempotent)
# 2026-09-26: three real bugs fixed:
#   ① Readiness no longer looks at the port alone. dsh web "binds the port first, mounts routes later": during plugin-tree load the port
#      is already listening, but the token line only prints once the whole tree is loaded (15~30s), so earlier requests get a 404.
#      The old logic treated a half-start as ready → the browser opened on "cannot reach 127.0.0.1, HTTP ERROR 404".
#      Now: wait for the token line and for that URL to return 200 after redirects before calling it usable.
#   ② Take the mutex before starting. A double tap starts two dsh web instances at once and both fight over the .credentials.yaml
#      write lock (which waits at most 2 seconds) → one of them is bound to crash while the plugin tree loads.
#   ③ Clear the orphan credentials lock before starting. After a -9 the lock file is left behind and every later start fails.
# Usage: 1_start-dsh.sh [--dry-run] [--no-open]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; OPEN=1
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --no-open) OPEN=0 ;; esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1

LOG="$(dsh_log)"
OPENER="$HOME_DIR/.local/bin/dsh-browser-open"
# ⚠ .dsh-url is the file the USER follows. The selftest runs this very widget against a
#   throwaway instance, and if that run is interrupted its detached DSH finishes later and
#   overwrites .dsh-url with the sandbox URL — the user then follows it into the sandbox
#   (seen for real 2026-09-27). DSH_URL_FILE lets such a caller redirect it.
URLFILE="${DSH_URL_FILE:-$HOME_DIR/.dsh-url}"

open_browser() {   # $1=url
  [ "$OPEN" = 1 ] || { warn "Not opening the browser here: the caller opens the page itself (--no-open)"; return 0; }
  if [ -x "$OPENER" ]; then run "'$OPENER' '$1' >/dev/null 2>&1"; else run "termux-open-url '$1'"; fi
}

# ── 把页面地址回传给调用方（协议行，不是给人看的文案，所以不走 dsh_msg、不翻译）──────────
# 为什么需要：Termux 在**后台**发 am start 会被 Android 静默拦掉（dsh-browser-open 里有原话），
# 于是"从控制台启动 DSH"的页面永远不出现，用户干等 → 觉得"比点小组件慢得多"（2026-09-28 定位）。
# 控制台 App 自己是前台，它 startActivity 不会被拦 —— 所以由它来开页面，脚本只负责把地址**放在
# 输出的最后两行**交回去。地址里带 token，控制台那边会把它从日志里剔掉。
emit_page() {   # $1=url（空则不打印任何东西）
  [ -n "$1" ] || return 0
  printf 'DSH_AUTH_URL=%s\n' "$1"
  local p=""
  [ -f "$HOME_DIR/.dsh-pwa" ] && p=$(head -1 "$HOME_DIR/.dsh-pwa" 2>/dev/null | tr -d '[:space:]')
  printf 'DSH_PWA_PKG=%s\n' "$p"
}

# Also restore the AI channels (a failure here does not affect the startup itself)
restore_channels() {
  step "Restore the AI control channels (a failure does not affect startup)"
if [ "$DRY" = 1 ]; then printf '   · [dry] start the task executor dsh-tasksd (for the web buttons)\n'; ok "(dry-run)"
elif tasksd_ensure; then okf "Task executor on 127.0.0.1:%s (the web buttons at the bottom right now work)" "$TASKSD_PORT"
else warn "Task executor did not start (the web buttons will report that no token can be read)"; fi
  # adb: `droid conn` only dials the port recorded last time, which is exactly what fails after a
  # Wi-Fi drop (Android clears the Wireless-debugging switch → no adbd to dial). droid-ensure does
  # the whole ladder — read Wi-Fi state, borrow the **bridge** to flip that one system setting, find
  # the port, connect, verify — and says precisely which rung failed. It sits here, and not in a step
  # of its own, because this function runs on **both** paths ("already running" exits before the
  # cold-start steps would ever reach it).
  if [ -x "$HOME_DIR/.local/bin/droid-ensure" ]; then
    if [ "$DRY" = 1 ]; then printf "$(dsh_msg '   · [dry] droid-ensure (make adb usable, or say exactly why not)\n')"; ok "(dry-run)"
    elif AOUT=$(timeout 180 "$HOME_DIR/.local/bin/droid-ensure" 2>&1); then
      printf '   ✔ %s\n' "$(printf '%s' "$AOUT" | tail -1)"
    else
      warn "adb is not usable right now (the bridge channel is unaffected):"
      printf '%s\n' "$AOUT" | tail -3 | sed 's/^/     /'
      warn "when Wi-Fi is back, tap the 8_ widget once (or run droid-ensure) to bring adb back"
    fi
  elif [ -x "$HOME_DIR/.local/bin/droid" ]; then
    if [ "$DRY" = 1 ]; then printf '   · [dry] droid conn\n'; ok "(dry-run)"
    elif timeout 25 "$HOME_DIR/.local/bin/droid" conn >/dev/null 2>&1; then ok "adb connected"
    else warn "adb not connected (Wi-Fi was off or the phone rebooted → turn on \"Wireless debugging\" first)"; fi
  else
    warn "droid-ensure is not installed — skipping the adb check"
  fi
  if [ "$DRY" = 1 ]; then printf '   · [dry] wake the bridge (with token) and ping it\n'; ok "(dry-run)"
  elif bridge_alive; then ok "DSH bridge is listening"
  elif bridge_ensure; then ok "DSH bridge woken (a broadcast with the token lets v1.7 re-authorize accessibility)"
  else warn "DSH bridge not responding: token revoked or accessibility off (Settings→Accessibility→DSH bridge)"; fi
}

step "① Take a wakelock (keeps Termux from being frozen in the background)"
# Termux is frozen in the background by the system/vivo → the DSH process freezes with it, which shows up as "cannot read" after switching back.
# Holding a wakelock is the most direct cure (a built-in Termux command, no extra permissions, but it must be taken again after a reboot).
if need termux-wake-lock; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] termux-wake-lock\n'; ok "(dry-run)"
  elif termux-wake-lock 2>/dev/null; then ok "Wakelock held"
  else warn "Could not get the wakelock"; fi
else warn "No termux-wake-lock command"; fi

stepf "② Check whether something already serves %s" "$DSH_PORT"
if port_open "$DSH_PORT"; then
  okf "A service is already on %s" "$DSH_PORT"
  printf "$(dsh_msg '   · If the page says "cannot read": pull to refresh first (the browser login cookie survives a reboot, no need for a new URL)\n')"

  step "③ Get a **truly usable** auth URL (a stale token gives 401, a half-start gives 404, neither can be opened directly)"
  U=""
  C=$(grep -oE "$TOKEN_RE" "$URLFILE" 2>/dev/null | tail -1)
  if [ -n "$C" ] && url_ready "$C"; then
    U="$C"; ok "The cached auth URL still works"
  elif [ -n "$C" ]; then
    warnf "The cached auth URL no longer works (a token left by an old instance, HTTP %s" "$(url_code "$C"))"
  fi
  if [ -z "$U" ]; then
    C=$(token_from_log "$LOG")
    if [ -n "$C" ] && url_ready "$C"; then
      U="$C"; printf '%s\n' "$U" > "$URLFILE"; ok "Took the auth URL of the current instance from the boot log and wrote it back to .dsh-url"
    fi
  fi
  if [ -n "$U" ]; then
    step "④ Open the browser"
    open_browser "$U"
  else
    warn "This instance was not started by this widget: its token only printed in the terminal that started it, so I cannot get it"
    warn "For a usable auth URL, tap \"4_soft-restart-dsh\" or \"6_hard-restart-dsh\" to fetch a fresh one"
  fi
  restore_channels
  emit_page "$U"   # 放在最后：控制台只截取输出的尾部，协议行必须在尾巴上
  done_; exit 0
fi

step "③ Boot mutex (keeps a double tap from starting two instances that fight over the lock)"
if [ "$DRY" = 1 ]; then
  printf "$(dsh_msg '   · [dry] mkdir %s\n')" "$BOOT_LOCK_DIR"; ok "(dry-run)"
elif boot_lock_acquire; then
  okf "Boot lock acquired (%s)" "$BOOT_LOCK_DIR"
  trap 'boot_lock_release' EXIT
else
  badf "Another startup is already in progress (lock held by pid %s)" "$(boot_lock_owner)"
  printf "$(dsh_msg '   Wait for it to finish before tapping this widget again; if you are sure nothing is starting, delete %s\n')" "$BOOT_LOCK_DIR"
  die "A startup is already running"
fi

step "④ Clear the orphan credentials write lock"
clear_orphan_cred_lock; rc=$?
case "$rc" in
  0) ok "Cleared (an orphan lock left by -9 makes every later start fail)" ;;
  1) warn "Lock is held (an instance is running), left untouched" ;;
  2) warn "The lock exists but cannot be deleted; startup may fail" ;;
  3) ok "No leftover lock" ;;
esac

step "⑤ Reset the boot log (the token is taken from this log only)"
rotate_log "$LOG" 2
dsh_log_reset "$LOG"
ok "Boot log reset"

step "⑥ Start dsh web in the background (detached from the widget session) and wait for **true readiness**"
if [ "$DRY" = 1 ]; then
  printf "$(dsh_msg '   · [dry] setsid nohup dsh web --port %s >> %s 2>&1 &\n')" "$DSH_PORT" "$LOG"
  printf "$(dsh_msg '   · [dry] wait for the token line + curl returning 200 after redirects (up to 120s)\n')"
  ok "(dry-run)"
elif dsh_start_and_wait "$LOG" 120; then
  ok "Startup complete"
else
  rc=$?
  [ "$rc" = 3 ] && die "Another startup is already in progress (not a failure, just double-tap protection) — wait for it to finish"
  need termux-notification && termux-notification --title "DSH start failed" \
    --content "No usable auth URL, see $LOG" --priority high 2>/dev/null
  die "Startup failed (the log tail is printed above)"
fi

step "⑦ Open the browser"
U=$(grep -oE "$TOKEN_RE" "$URLFILE" 2>/dev/null | tail -1)
[ -n "$U" ] && open_browser "$U" || warn "No usable auth URL, not opening the browser (avoids landing on an error page)"

restore_channels
emit_page "$U"   # 放在最后：控制台只截取输出的尾部，协议行必须在尾巴上
done_
