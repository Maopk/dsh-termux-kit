#!/data/data/com.termux/files/usr/bin/bash
# 4_soft-restart-dsh — no killing: SIGTERM alone for a graceful stop → backup → rotate logs → wait for the port → start → verify
# Gives up after 15 seconds (never -9) and points you to "6_hard-restart-dsh"
# Usage: 4_soft-restart-dsh.sh [--dry-run] [--no-backup]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_BAK=1
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --no-backup) DO_BAK=0 ;; esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1
LOG="$(dsh_log)"

# ── Boot mutex: **take the lock at the very start** (fixed 2026-09-26) ──
# Why it must be taken "before killing processes": the lock used to be taken only at start time, so on a double tap
# the step ① of the second run would -9 the fresh instance the first run had just started → the first run reported "the process exited during startup".
# That is exactly how it broke for real (widget 4 log: one failure + one success, with the output interleaved).
if [ "$DRY" != 1 ]; then
  if boot_lock_acquire; then
    trap 'boot_lock_release' EXIT
  else
    bad "Another start/restart is already in progress (lock held by pid $(boot_lock_owner))"
    printf "$(dsh_msg '   Wait for it to finish before tapping again; if you are sure nothing is running, delete %s\n')" "$BOOT_LOCK_DIR"
    die "A startup is already running (double-tap protection)"
  fi
fi

step "① Graceful stop (SIGTERM, up to 15s; no escalation to a forced kill)"
if [ -z "$(pids_of 'bin.js web')" ]; then
  ok "Nothing was running, going straight to startup"
else
  T=$(date +%s)
  kill_wait "bin.js web" 15 TERM && ok "Exited gracefully ($(( $(date +%s) - T ))s)" || {
    bad "Still alive after 15s — **soft restart abandoned**, no forced kill was performed"
    printf "$(dsh_msg '   Use the widget "6_hard-restart-dsh" instead (it force-kills with -9)\n')"
    printf "$(dsh_msg '   Current processes: %s\n')" "$(pids_of 'bin.js web')"
    exit 2
  }
fi

step "② State backup"
if [ "$DO_BAK" = 1 ] && [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  [ "$DRY" = 1 ] && printf '   · [dry] dsh-backup\n' || { OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -1); ok "${OUT:-backed up}"; }
else warn "Skipping backup"; fi

step "③ Log rotation"
rotate_log "$LOG" 2; run ": > '$LOG'"; ok "Boot log reset"

step "④ Wait for port $DSH_PORT to be released"
if [ "$DRY" = 1 ]; then printf '   · [dry] skipped (dry-run did not really stop the process)\n'; else
wait_port_free "$DSH_PORT" 15 && ok "Port is free" || die "Port is still in use, soft restart aborted"; fi

step "⑤ Start again (detached, in the background) and wait for **true readiness**"
# The port alone misleads: the port opens first and routes mount later, so a half-started service answers 404 to everything.
# True readiness = the token line appears in the log + that URL returns 200 after redirects.
if [ "$DRY" = 1 ]; then
  printf "$(dsh_msg '   · [dry] setsid nohup dsh web --port %s >> %s 2>&1 &\n')" "$DSH_PORT" "$LOG"
  printf "$(dsh_msg '   · [dry] wait for the token line + curl returning 200 after redirects (up to 120s)\n')"
  ok "(dry-run)"
elif dsh_start_and_wait "$LOG" 120; then
  ok "Startup complete"
  printf "$(dsh_msg '   · Just refresh the browser page (the login cookie survives a restart)\n')"
else
  rc=$?
  [ "$rc" = 3 ] && die "Another startup is already in progress (not a failure, just double-tap protection) — wait for it to finish"
  need termux-notification && termux-notification --title "DSH soft restart failed" \
    --content "No usable auth URL, see $LOG" --priority high 2>/dev/null
  die "Startup failed (the log tail is printed above)"
fi
done_
