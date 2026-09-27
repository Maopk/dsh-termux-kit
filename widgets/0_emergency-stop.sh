#!/data/data/com.termux/files/usr/bin/bash
# 0_emergency-stop — revoke all of the AI control over this phone in one tap (works without looking at the screen or tapping a button)
# Revokes four paths: ① DSH bridge (accessibility) ② token ③ shared-dir command channel ④ adb wireless debugging
# Usage: 0_emergency-stop.sh [--dry-run]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1

log "Emergency stop: the goal is to cut all four AI control paths"

step "① DSH bridge (accessibility service)"
if [ "$DRY" = 1 ]; then printf '   · [dry] droid-sock panic\n'
  ok "(dry-run)"
elif [ -x "$HOME_DIR/.local/bin/droid-sock" ] && timeout 8 "$HOME_DIR/.local/bin/droid-sock" panic >/dev/null 2>&1; then
  ok "Bridge killed itself: port closed + accessibility switched itself off"
else
  warn "The bridge did not respond (it may already have been stopped)"
fi

step "② Revoke the token"
if [ -f "$HOME_DIR/.dsh-bridge-token" ]; then
  run "mv -f '$HOME_DIR/.dsh-bridge-token' '$HOME_DIR/.dsh-bridge-token.revoked-$(date +%s)'"
  ok "Token revoked (renamed and kept for the record)"
else
  ok "No token file, nothing to revoke"
fi

step "③ Empty the shared-dir command channel (AutoX bridge / page remote control)"
run ": > '$SHARED/dsh-droid/cmd.json' 2>/dev/null || true"
run ": > '$HOME_DIR/.dsh-look-cmd.json' 2>/dev/null || true"
ok "Command files emptied"

step "④ Cut adb wireless debugging (the strongest channel of all)"
if need adb && adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
  run "timeout 10 adb usb >/dev/null 2>&1 || true"
  ok "adbd switched back to USB mode (the TCP listener is gone)"
  run "timeout 10 adb disconnect >/dev/null 2>&1 || true"
  run "adb kill-server >/dev/null 2>&1 || true"
  ok "adb disconnected and the server was stopped"
else
  warn "adb is not connected right now (the cleanup below handles anything that was)"
  run "adb kill-server >/dev/null 2>&1 || true"
fi

step "⑤ Kill any automation processes I may have left running"
K=0
for pat in "auto-look.sh" "droid-hub" "dsh-control" "dsh-eval" "pair-now" "deep-nav" "adb-recon"; do
  for pid in $(ps -ef 2>/dev/null | grep "[${pat:0:1}]${pat:1}" | awk '{print $2}'); do
    kill "$pid" 2>/dev/null && K=$((K+1))
  done
done
ok "Killed $K related processes"

step "⑥ Open the accessibility settings page so you can confirm at a glance"
if [ "$DRY" = 1 ]; then printf '   · [dry] am start accessibility settings\n'; else am start -a android.settings.ACCESSIBILITY_SETTINGS >/dev/null 2>&1; fi

step "Self-check"
sleep 1
port_open 8788 && bad "8788 is still listening (unexpected)" || ok "8788 is closed"
adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q . && warn "An adb device is still connected (a reboot always clears it)" || ok "No adb devices connected"
printf "$(dsh_msg '\nIf the phone is still acting on its own, do this in order: hold Volume +/- for 3s → emergency stop in the notification shade → reboot the phone → safe mode\nRescue card: %s/dsh/文档/手机失控自救卡.md\n')" "$DL"
done_
