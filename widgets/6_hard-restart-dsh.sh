#!/data/data/com.termux/files/usr/bin/bash
# 6_hard-restart-dsh — the most thorough: graceful stop → -9 kill of every related process → backup → rotate → wait for the port → start → true-readiness check
# Use it after changing plugins; client modules carry a rev, so **refresh the page** after the restart — no need to close the browser
# Usage: 6_hard-restart-dsh.sh [--dry-run] [--no-backup] [--close-browser] (--keep-browser kept for compatibility)
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_BAK=1; CLOSE_BROWSER=0
for a in "$@"; do case "$a" in
  --dry-run) DRY=1 ;;
  --no-backup) DO_BAK=0 ;;
  --close-browser) CLOSE_BROWSER=1 ;;
  --keep-browser) CLOSE_BROWSER=0 ;;   # Legacy flag: keeping the browser is the default now, so it is harmless
esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1
LOG="$(dsh_log)"; BROWSER_PKG="com.android.chrome"

# ── Boot mutex: **take the lock at the very start** (fixed 2026-09-26) ──
# Why it must be taken "before killing processes": the lock used to be taken only at start time, so on a double tap
# the step ① of the second run would -9 the fresh instance the first run had just started → the first run reported "the process exited during startup".
# That is exactly how it broke for real (widget 4 log: one failure + one success, with the output interleaved).
if [ "$DRY" != 1 ]; then
  if boot_lock_acquire; then
    trap 'boot_lock_release' EXIT
  else
    badf "Another start/restart is already in progress (lock held by pid %s)" "$(boot_lock_owner)"
    printf "$(dsh_msg '   Wait for it to finish before tapping again; if you are sure nothing is running, delete %s\n')" "$BOOT_LOCK_DIR"
    die "A startup is already running (double-tap protection)"
  fi
fi

step "① Graceful stop (up to 8s)"
[ -n "$(pids_of 'bin.js web')" ] && { kill_wait "bin.js web" 8 TERM && ok "Exited gracefully" || warn "Did not exit, switching to a forced kill"; } || ok "Nothing is running"

step "② Force-kill every related process (-9)"
K=0
for pat in "bin.js web" "md_cg" "dsh-termux-runtime" "dsh web"; do
  P=$(pids_of "$pat"); [ -z "$P" ] && continue
  if [ "$DRY" = 1 ]; then printf '   · [dry] kill -9 %s (%s)\n' "$P" "$pat"; continue; fi
  for p in $P; do kill -9 "$p" 2>/dev/null && K=$((K+1)); done
  sleep 0.3
done
okf "Force-killed %s processes" "$K"
if [ "$DRY" = 1 ]; then printf '   · [dry] skip the leftover check (dry-run really kills nothing)\n'; elif [ -n "$(pids_of 'bin.js web')" ]; then die "dsh web is still left over: $(pids_of 'bin.js web')"; else ok "Confirmed: no leftover dsh web"; fi

step "②·b Clear the orphan credentials write lock (the inevitable result of -9; while it is there nothing can ever start)"
if [ "$DRY" = 1 ]; then printf '   · [dry] delete %s when no instance is running\n' "$CRED_LOCK"; ok "(dry-run)"
else
  clear_orphan_cred_lock; rc=$?
  case "$rc" in
    0) ok "Orphan lock cleared (dsh-atomic-write waits at most 2 seconds, and an orphan lock makes every start fail)" ;;
    1) warn "Lock is held (an instance is still running), left untouched" ;;
    2) bad "The lock exists but cannot be deleted; startup will most likely fail" ;;
    3) ok "No leftover lock" ;;
  esac
fi

step "③ State backup"
if [ "$DO_BAK" = 1 ] && [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  [ "$DRY" = 1 ] && printf '   · [dry] dsh-backup\n' || { OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -1); okf "%s" "${OUT:-backed up}"; }
else warn "Skipping backup"; fi

step "④ Log rotation"
rotate_log "$LOG" 2; run ": > '$LOG'"; ok "Boot log reset"

stepf "⑤ Wait for port %s to be released" "$DSH_PORT"
if [ "$DRY" = 1 ]; then printf '   · [dry] skipped (dry-run really kills nothing)\n'; elif ! wait_port_free "$DSH_PORT" 15; then
  warn "Port is still in use, trying to force it free"
  run "fuser -k '$DSH_PORT'/tcp 2>/dev/null || true"; sleep 1
  port_open "$DSH_PORT" && die "Port $DSH_PORT cannot be released" || ok "Force-released"
else ok "Port is free"; fi

step "⑥ Browser and PWA (the page you are viewing is **left alone** by default)"
# 2026-09-26 change: this used to force-stop Chrome by default, on the grounds of "making sure the new client modules load".
# But client module URLs carry a rev (content changes → rev changes), so **an ordinary refresh is enough**; and force-killing the browser costs you:
#   ① the conversation page you are reading vanishes on the spot;
#   ② when the system restores the tab it may land right in the "service is still starting" window → the service answers 404 to everything
#      → the config GET of the balance-whale widget fails → it pops up "failed to read settings, saving paused so your existing settings are not overwritten".
# So keeping it is the default now; if you really want the browser closed too, add --close-browser.
if [ "$CLOSE_BROWSER" = 1 ]; then
  # 2026-09-27 fix: both `am force-stop` + `pkill -f webapk` here **did nothing** —
  #   Termux am has no force-stop subcommand, and pkill can only kill processes of its own UID, so it cannot touch a window owned by Chrome.
  #   Now it is all delegated to dsh-close-window (adb first, and without adb it sends keys through the accessibility bridge).
  if [ "$DRY" = 1 ]; then
    printf "$(dsh_msg '   · [dry] dsh-close-window\n')"; ok "(dry-run)"
  elif [ -x "$HOME_DIR/.local/bin/dsh-close-window" ] && "$HOME_DIR/.local/bin/dsh-close-window"; then
    ok "Browser / PWA window closed"
  else
    warn "Could not close the window (reason above) — swipe it away from Recents yourself, or turn on adb"
  fi
else
  ok "Keeping the browser (after the restart **refresh the page** to load the new client modules; the login cookie survives a restart)"
fi

step "⑦ Start (detached, in the background)"
if [ "$DRY" = 1 ]; then printf '   · [dry] setsid nohup dsh web --port %s >> %s 2>&1 &\n' "$DSH_PORT" "$LOG"; ok "(dry-run)"
elif boot_lock_acquire; then
  trap 'boot_lock_release' EXIT; ok "Boot lock acquired"
else
  badf "Another startup is already in progress (pid %s), wait for it to finish" "$(boot_lock_owner)"; die "A startup is already running"
fi

step "⑧ Readiness check (wait for the token line + 200 after redirects, no longer port-only)"
if [ "$DRY" = 1 ]; then
  printf "$(dsh_msg '   · [dry] wait for the token line + curl returning 200 after redirects (up to 120s)\n')"; ok "(dry-run)"
elif dsh_start_and_wait "$LOG" 120; then
  ok "Startup complete"
  printf "$(dsh_msg '   · Just refresh the browser page (the login cookie survives a restart)\n')"
else
  rc=$?
  [ "$rc" = 3 ] && die "Another startup is already in progress (not a failure, just double-tap protection) — wait for it to finish"
  need termux-notification && termux-notification --title "DSH hard restart failed" \
    --content "No usable auth URL, see $LOG" --priority high 2>/dev/null
  die "Startup failed (the log tail is printed above)"
fi

step "⑨ Also restore the AI channels"
if [ -x "$HOME_DIR/.local/bin/droid" ]; then
  [ "$DRY" = 1 ] && printf '   · [dry] droid conn\n' || {
    timeout 25 "$HOME_DIR/.local/bin/droid" conn >/dev/null 2>&1 && ok "adb connected" \
      || warn "adb not connected (Wi-Fi was off or the phone rebooted → turn on \"Wireless debugging\" once)"; }
fi
if [ "$DRY" = 1 ]; then printf '   · [dry] wake the bridge (with token) and ping it\n'; ok "(dry-run)"
elif bridge_alive; then ok "DSH bridge is listening"
elif bridge_ensure; then ok "DSH bridge woken"
else warn "DSH bridge not responding: token revoked or accessibility off (Settings→Accessibility→DSH bridge)"; fi
done_
