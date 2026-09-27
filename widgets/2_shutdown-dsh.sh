#!/data/data/com.termux/files/usr/bin/bash
# 2_shutdown-dsh — full shutdown: graceful stop → forced-kill fallback → backup → rotate → wait for the port → **close the window** → soft-stop the bridge → stop the task executor → publish status → release the wakelock
#              (closes the AI control channels too by default; add --keep-bridge to keep the bridge)
# 2026-09-27: two fixes (from your real-world testing):
#   ① Closing the window used to run after "stopping the bridge" → without adb it never closed at all (Termux am has no force-stop).
#      Now it **closes the window before stopping the bridge**, delegated to ~/.local/bin/dsh-close-window (adb first, otherwise key events through the bridge).
#   ② Termux **no longer kills itself** by default: the last step used to kill -9 Termux, which immediately put the Console app out of action
#      (it can only work through Termux RUN_COMMAND, so once Termux dies a refresh has to wait for the system to cold-start it).
#      To really shut Termux down too: add --close-termux.
# Usage: 2_shutdown-dsh.sh [--dry-run] [--no-backup] [--keep-browser] [--keep-bridge] [--full-stop] [--close-termux]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_BAK=1; KEEP_BROWSER=0; KEEP_TERMUX=1; KEEP_BRIDGE=0; FULL_STOP=0
# 2026-09-26 fix: --keep-bridge existed only in the docs and was missing from this case, so it was silently ignored
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --no-backup) DO_BAK=0 ;; --keep-browser) KEEP_BROWSER=1 ;; --keep-termux) KEEP_TERMUX=1 ;; --close-termux) KEEP_TERMUX=0 ;; --keep-bridge) KEEP_BRIDGE=1 ;; --full-stop) FULL_STOP=1 ;; esac; done
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1
BROWSER_PKG="com.android.chrome"
LOG="$(dsh_log)"

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

step "① Stop dsh web gracefully (SIGTERM, up to 10s)"
if [ -z "$(pids_of 'bin.js web')" ]; then
  ok "No dsh web running"
else
  kill_wait "bin.js web" 10 TERM && ok "Exited gracefully" || { warn "Still alive after 10s, forcing kill"; kill_wait "bin.js web" 5 KILL >/dev/null; ok "Force-killed"; }
fi

step "② Clean up Lingshu child processes"
if [ -n "$(pids_of 'md_cg')" ]; then kill_wait "md_cg" 5 KILL >/dev/null; ok "md_cg ended"; else ok "No leftover md_cg"; fi

step "③ State backup"
if [ "$DO_BAK" = 1 ] && [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-backup\n'; else
    OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -1); okf "%s" "${OUT:-backed up}"
  fi
else
  warn "Skipping backup"
fi

step "④ Log rotation (archive the auth URL into .dsh-url first, then clear the log)"
CUR=$(token_from_logf "%s" "$LOG" 2>/dev/null)
[ -n "$CUR" ] && { printf '%s\n' "$CUR" > "$HOME_DIR/.dsh-url"; ok "Auth URL archived to .dsh-url"; } \
              || warn "No token line in the log (this instance may not have been started by this widget)"
rotate_logf "%s" "$(dsh_log)" 2
dsh_log_reset "$(dsh_log)"
ok "Boot log reset"

stepf "⑤ Wait for port %s to be released (up to 15s)" "$DSH_PORT"
if [ "$DRY" = 1 ]; then printf '   · [dry] skipped (dry-run did not really stop the process, so the port will not be released)\n'; else
wait_port_free "$DSH_PORT" 15 && ok "Port released" || { bad "Port is still in use"; need fuser && fuser -k "$DSH_PORT"/tcp 2>/dev/null; sleep 1; port_open "$DSH_PORT" && die "Port is still in use" || ok "Force-released"; }; fi

step "⑥ Close the DSH window (**must come before stopping the bridge**: without adb it can only work through the bridge)"
# 2026-09-27 fix: this step used to run after "stopping the bridge", so without adb it never did anything —
#   Termux am has no force-stop subcommand, and the only channel that can close a window owned by another app is the accessibility bridge (already stopped in ⑦).
#   Now: with adb it uses adb force-stop; without adb it goes through the bridge (bring the window to the front + press Back until it disappears).
if [ "$KEEP_BROWSER" = 0 ]; then
  if [ "$DRY" = 1 ]; then
    printf "$(dsh_msg '   · [dry] dsh-close-window (adb first, otherwise key events through the bridge)\n')"; ok "(dry-run)"
  elif [ -x "$HOME_DIR/.local/bin/dsh-close-window" ] && "$HOME_DIR/.local/bin/dsh-close-window"; then
    ok "Browser / PWA window closed"
  else
    warn "Could not close the window (see the lines above) — swipe it away from Recents yourself, or turn on adb and let me do it"
  fi
else
  warn "Keeping the browser window, as requested"
fi

step "⑦ Shut down the AI control channel (DSH bridge)"
# Why a "soft stop" alone is not enough: it only closes port 8788, accessibility stays enabled; the system can rebind
# the service at any moment → onServiceConnected brings the port and the persistent notification back. That is the "I turned it off and it turned itself back on".
# [2026-09-26 evening, retuned from real testing]
#   · Default = **soft stop** (stop): close the port + drop the persistent notification, but keep the app process. Since bridge v1.8 it remembers
#     "the user asked for it to stay stopped", so a system accessibility rebind will **not turn it back on** (exactly the effect you want),
#     and the process is still there → tapping widget 1/7/8 brings it back at once, at zero cost.
#   · --full-stop = sleep (a real stop: accessibility is turned off too). Measured cost: once the app process is fully gone,
#     vivo blocks the broadcast and will not let it auto-start (that is exactly how it went offline today) → you will most likely need to turn accessibility on by hand once.
#     So it is no longer the default; use it only when you explicitly want it "pulled out completely".
if [ "$KEEP_BRIDGE" = 0 ]; then
  if [ "$DRY" = 1 ]; then
    printf "$(dsh_msg '   · [dry] droid-sock %s\n')" "$([ "$FULL_STOP" = 1 ] && echo 'sleep (real stop)' || echo 'stop (soft stop, no self-recovery since v1.8)')"
    ok "(dry-run)"
  else
    CAPS=$(timeout 12 "$HOME_DIR/.local/bin/droid-sock" caps 2>/dev/null || true)
    if [ "$FULL_STOP" = 1 ]; then
      if printf '%s' "$CAPS" | grep -q '"sleep"'; then
        if timeout 15 "$HOME_DIR/.local/bin/droid-sock" sleep >/dev/null 2>&1; then
          sleep 1
          port_open 8788 && warn "The bridge still holds 8788, check again in a moment" || ok "Bridge fully stopped (port/notification/accessibility all off)"
          warn "Note: after a real stop the app process exits and vivo may refuse to let a broadcast revive it — if it will not wake up, turn accessibility on by hand"
        else warn "The sleep command did not succeed (the bridge may already be stopped)"; fi
      else
        warn "This bridge version has no sleep, falling back to a soft stop"
        timeout 12 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1 && ok "Soft-stopped" || warn "The bridge did not respond (it may already have been stopped)"
      fi
    else
      if timeout 12 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1; then
        ok "Bridge soft-stopped: port 8788 closed, persistent notification dropped"
        if printf '%s' "$CAPS" | grep -q '"stop_is_durable"'; then
          ok "v1.8: it now remembers to "stay" off → a system accessibility rebind will not turn it back on"
        else
          warn "This bridge version is older: after a soft stop a system accessibility rebind may bring it back; installing v1.8 fixes that for good"
        fi
        ok "To bring it back: tap widget 1/7/8 (the process is still there and returns on the first call)"
      else
        warn "The bridge did not respond (it may have been stopped already, or the app was killed by the system)"
      fi
    fi
  fi
else
  warn "Keeping the bridge channel, as requested (--keep-bridge)"
fi

step "⑧ Take the task executor down (it only serves the web buttons)"
if [ "$DRY" = 1 ]; then printf '   · [dry] tasksd_stop\n'; ok "(dry-run)"
elif tasksd_alive; then tasksd_stop; sleep 1; tasksd_alive && warn "Still running" || ok "Stopped"; else ok "Was not running"; fi

step "⑨ Publish status + release the boot lock (both must happen before anything that may kill Termux)"
publish_status
# Release the boot lock explicitly: if step ⑩ really does kill -9 Termux, the EXIT trap never runs →
# an orphan lock is left behind and the next "start DSH" reports "a startup is already running" (hit this one for real).
boot_lock_release && ok "Boot lock released"

step "⑩ Release the wakelock; Termux is kept by default (only --close-termux closes it)"
if need termux-wake-unlock; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] termux-wake-unlock\n'; ok "(dry-run)"
  else termux-wake-unlock 2>/dev/null && ok "Wakelock released (no point staying resident once DSH is off)" || warn "Failed to release the wakelock (it may never have been taken)"; fi
fi
if [ "$KEEP_TERMUX" = 0 ]; then
  printf "$(dsh_msg '   · Closing Termux in 3 seconds… (--close-termux)\n')"; [ "$DRY" = 1 ] || sleep 3
  if [ "$DRY" = 1 ]; then printf '   · [dry] kill the Termux app process (cmdline=com.termux)\n'
  else
    TPID=$(for p in /proc/[0-9]*; do c=$(tr -d '\0' < "$p/cmdline" 2>/dev/null); [ "$c" = "com.termux" ] && basename "$p"; done | head -1)
    if [ -n "$TPID" ]; then kill -9 "$TPID" 2>/dev/null && okf "Termux app process killed (pid %s)" "$TPID"; else warn "No Termux app process found"; fi
  fi
else
  ok "Termux kept (default): the Console app stays available; DSH/bridge/task executor are all stopped, so it barely uses power"
  printf "$(dsh_msg '     (to close Termux too: add --close-termux)\n')"
fi
done_
