#!/data/data/com.termux/files/usr/bin/bash
# 7_reconnect-ai — run this after rebooting the phone: reconnect adb + wake the DSH bridge, then report the state of all three channels
# Prerequisite: adb needs "Wireless debugging" switched on in Developer options (pairing records are kept, so no pairing code again)
# ⚠️ Measured: once Wi-Fi drops, Android turns "Wireless debugging" off by itself → adb stops working with it (you have to switch it back on)
# Usage: 7_reconnect-ai.sh [--dry-run]
HOME_DIR="${DSH_HOME_DIR:-/data/data/com.termux/files/home}"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR" || exit
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1

step "① adb channel"
if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
  okf "Connected: %s" "$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
else
  warn "Not connected → calling \"8_enable-wireless-adb\" first (it flips the switch on automatically when online)"
  if [ -x "$SELF_DIR/8_enable-wireless-adb.sh" ]; then
    bash "$SELF_DIR/8_enable-wireless-adb.sh" $([ "$DRY" = 1 ] && echo --dry-run) 2>&1 | sed 's/^/     /' | tail -12 || true
  fi
  if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
    warn "Automatic enabling did not work, falling back to the manual path: try fixed port 5555 → find the port via mDNS"
    if [ -x "$HOME_DIR/.local/bin/droid" ]; then
      [ "$DRY" = 1 ] || timeout 40 "$HOME_DIR/.local/bin/droid" conn >/dev/null 2>&1
      if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
        P=$(timeout 30 "$HOME_DIR/.local/bin/droid" discover 2>/dev/null | grep -oE 'port=[0-9]+' | head -1 | cut -d= -f2)
        if [ -n "$P" ] && [ "$DRY" != 1 ]; then timeout 25 adb connect "127.0.0.1:$P" >/dev/null 2>&1; fi
      fi
    fi
    if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
      okf "Connected: %s" "$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
    else
      bad "adb still cannot connect: make sure Wi-Fi is connected + the bridge app has been granted WRITE_SECURE_SETTINGS"
    fi
  else
    ok "Automatic enabling succeeded"
  fi
fi

step "② DSH bridge channel"
if port_open 8788; then ok "Bridge listening on 127.0.0.1:8788"
else
  [ "$DRY" = 1 ] || bridge_wake
  bridge_alive && ok "Wake broadcast succeeded (with token)" || warn "The bridge did not start: token revoked or accessibility off (Settings→Accessibility→DSH bridge)"
fi

step "③ Status overview"
printf "$(dsh_msg '   DSH Web : %s\n')" "$(port_open "$DSH_PORT" && echo "running (HTTP $(http_code "$DSH_PORT"))" || echo 'not running')"
printf "$(dsh_msg '   adb     : %s\n')" "$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1; exit}' || echo 'not connected')"
printf "$(dsh_msg '   Bridge  : %s\n')" "$(port_open 8788 && echo 'listening' || echo 'not listening')"
printf "$(dsh_msg '   Tip     : you must switch "Wireless debugging" on once after each of these: a phone reboot / a Wi-Fi drop (Android turns it off automatically when Wi-Fi drops)\n')"
done_
