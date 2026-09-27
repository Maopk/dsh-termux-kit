#!/data/data/com.termux/files/usr/bin/bash
# 8_enable-wireless-adb — when online, switch "Wireless debugging" on and attach adb automatically (you never touch a switch)
#
# How it works: the TCP listener of adb depends on the "Wireless debugging" service, and the system shuts that service down whenever Wi-Fi drops.
#      This script goes through the DSH bridge (an accessibility app, loopback only, no network) to call Settings.Global.putInt
#      and set adb_wifi_enabled to 1 — the value behind that switch in Settings.
#      The only prerequisite: the bridge app has been granted WRITE_SECURE_SETTINGS (one time, with adb pm grant).
#
# Usage: 8_enable-wireless-adb.sh [--dry-run] [--force] [--no-ui]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
SOCK="$HOME_DIR/.local/bin/droid-sock"
DRY=0; FORCE=0; NO_UI=0
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --force) FORCE=1 ;; --no-ui) NO_UI=1 ;; esac; done
[ "$DRY" = 1 ] && log "== dry-run mode: print only, do not execute =="

bridge_alive() { port_open 8788; }
# The wake must carry the token (since v1.7: once the service is stopped, only a broadcast with the right token may re-authorize accessibility)
wake_bridge() { bridge_wake; }

step "① Bridge channel (loopback, no Wi-Fi needed)"
if bridge_alive; then ok "Bridge listening on 127.0.0.1:8788"
else
  warn "The bridge is not listening, sending a wake broadcast"
  wake_bridge
  bridge_alive && ok "Bridge woken" || die "The bridge will not start — check that Settings→Accessibility→DSH bridge is on"
fi

step "② Network state (the bridge really dials 1.1.1.1:443)"
NET=$($SOCK netstate 2>/dev/null || true)
echo "$(dsh_msg '$NET')" | sed 's/^/   /'
WIFI_ON=$(echo "$NET" | grep -oE '"wifi_on": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
ONLINE=$(echo "$NET" | grep -oE '"online": *(true|false)' | grep -oE '(true|false)$')
ADB_WIFI=$(echo "$NET" | grep -oE '"adb_wifi": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
[ "$WIFI_ON" = "1" ] && ok "Wi-Fi is on" || warn "Wi-Fi is off (Wireless debugging depends on Wi-Fi; only you can flip the Wi-Fi switch)"
[ "$ONLINE" = "true" ] && ok "Real internet reachability" || warn "No internet reachability detected (the switch will still be attempted)"
case "$ADB_WIFI" in 1) ok "\"Wireless debugging\" is already on" ;; 0) warn "\"Wireless debugging\" is currently off" ;; *) warn "Cannot read adb_wifi_enabled (permission may be missing)" ;; esac

# With Wi-Fi off, the TCP listener of adbd **cannot** come up:
#   ① Wireless debugging runs on the wlan interface; ② and the moment Wi-Fi goes off, Android clears adb_wifi_enabled straight back to 0
#      (measured: set it to 1 by hand and it reads back as 0) — so "forcing the switch" is a dead end.
# But Android 10+ does not let an app turn Wi-Fi on either. So this became **semi-automatic** (2026-09-26 evening):
#   open the Wi-Fi settings page → you tap Wi-Fi once → this widget **detects it and carries on** through every remaining step.
# One tap from you is all it takes; add --no-ui if you do not want it touching the screen.
if [ "$WIFI_ON" = "0" ] && [ "$FORCE" = 0 ]; then
  if [ "$NO_UI" = 1 ]; then
    step "③ Prerequisites not met (--no-ui: no screen interaction at all)"
    bad "Wi-Fi is off → the adb TCP listener will not appear"
    printf "$(dsh_msg '   Turn Wi-Fi on yourself and re-run this widget, or tap "7_reconnect-ai"\n')"
    printf '\n[%s] not executed: waiting for Wi-Fi (%ss elapsed)\n' "$SELF" "$(( $(date +%s) - T0 ))"
    exit 3
  fi
  step "③ Wi-Fi is off → opening the Wi-Fi page and waiting for you to turn it on (up to 90s)"
  warn "Android 10+ does not allow an app to turn Wi-Fi on, so only your tap can do it here (there is no other way)"
  if [ "$DRY" = 1 ]; then printf '   · [dry] am start -a android.settings.WIFI_SETTINGS\n'; ok "(dry-run)"
  else
    am start -a android.settings.WIFI_SETTINGS >/dev/null 2>&1 && ok "Wi-Fi settings page opened" || warn "Could not open the settings page (the quick-settings tile works too)"
  fi
  printf "$(dsh_msg '   · Once you turn Wi-Fi on this widget **continues by itself**, no need to tap me again\n')"
  W=0
  for i in $(seq 1 90); do
    [ "$DRY" = 1 ] && break
    N2=$(timeout 12 "$SOCK" netstate 2>/dev/null || true)
    W=$(printf '%s' "$N2" | grep -oE '"wifi_on": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    [ "$W" = "1" ] && { ok "Wi-Fi is on (waited ${i}s), carrying on"; break; }
    sleep 1
  done
  if [ "$DRY" != 1 ] && [ "$W" != "1" ]; then
    warn "Wi-Fi was not turned on within 90s"
    printf '\n[%s] not executed: timed out waiting for Wi-Fi (%ss elapsed)\n' "$SELF" "$(( $(date +%s) - T0 ))"
    exit 3
  fi
fi

step "③ Turn on the \"Wireless debugging\" switch"
if [ "$ADB_WIFI" = "1" ] && [ "$FORCE" = 0 ]; then
  ok "Already on, skipping (add --force to rewrite it anyway)"
else
  if [ "$DRY" = 1 ]; then printf '   · [dry] %s adbwifi 1\n' "$SOCK"
  else
    R=$($SOCK adbwifi 1 2>&1 | tail -1); echo "   $R"
    case "$R" in *'"adb_wifi": 1'*) ok "Switch set to 1" ;; *) warn "Write result unclear: $R" ;; esac
  fi
fi

# ③·check: **writing a setting ≠ adbd actually starting**. Measured 2026-09-26: the bridge set adb_wifi_enabled to 1,
# and the system cleared it back to 0 shortly after (this OS build only honors the manual switch-on under Developer options → Wireless debugging).
# Without reading it back we would scan 30,000 ports for nothing and finally report "no network" — the opposite of the truth — so confirm it here first.
if [ "$DRY" != 1 ]; then
  A4=""; O4=""
  for i in 1 2 3; do
    N4=$(timeout 12 "$SOCK" netstate 2>/dev/null || true)
    A4=$(printf '%s' "$N4" | grep -oE '"adb_wifi": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    [ "$A4" = "1" ] && break
    sleep 1
  done
  O4=$(printf '%s' "${N4:-}" | grep -oE '"online": *(true|false)' | grep -oE '(true|false)$')
  if [ "$A4" != "1" ]; then
    step "③·check: the switch did not stick (adb_wifi=${A4:-?})"
    bad "The setting was cleared back to 0 after being written — writing Settings.Global alone is not enough to start adbd"
    if [ "$O4" = "true" ]; then
      printf "$(dsh_msg '   The network works (online=true) → **not a network problem**: this OS build only honors the manual switch-on under Developer options → Wireless debugging\n')"
      printf "$(dsh_msg '   What to do: Settings → Developer options → Wireless debugging → turn it on once (about 20s; only then can this widget keep it up automatically)\n')"
    else
      printf "$(dsh_msg '   And the network is down too (online=%s) → Wireless debugging depends on "a usable Wi-Fi network", so connect to an AP first\n')" "${O4:-?}"
      printf "$(dsh_msg '   What to do: connect to a Wi-Fi network (not just flip the switch), then re-run this widget\n')"
    fi
    printf "$(dsh_msg '\n[%s] incomplete: the Wireless debugging switch did not stick (%ss elapsed)\n')" "$SELF" "$(( $(date +%s) - T0 ))"
    exit 3
  fi
  ok "Read back adb_wifi=1 (carrying on to find the port)"
fi

step "④ Find the wireless debugging port (mDNS → scan locally if that fails)"
# Why mDNS alone is not enough: dt= with no real network connection, multicast discovery sees nothing (hit for real),
# but adbd is usually listening on loopback anyway — scanning the local ports directly is more reliable.
PORT=""; CANDS=""
if [ "$DRY" = 1 ]; then printf '   · [dry] check fixed port 5555 first → mDNS → then scan 30000-60999\n'
else
  # 0) Fixed port first: after `adb tcpip 5555` adbd sits on 5555,
  #    and it is **not** inside the 30000-60999 scan range, so skipping this check wastes ~45s on mDNS + a scan.
  if port_open 5555; then ok "Fixed port 5555 is listening (tcpip mode), using it first"; CANDS="5555"; fi
  if [ -z "$CANDS" ]; then
  for i in 1 2 3; do
    PORT=$(timeout 12 "$HOME_DIR/.local/bin/droid" discover 2>/dev/null | grep -oE 'port=[0-9]+' | head -1 | cut -d= -f2)
    [ -n "$PORT" ] && break
    sleep 2
  done
  fi
  if [ -n "$CANDS" ]; then :
  elif [ -n "$PORT" ]; then ok "mDNS found port $PORT"; CANDS="$PORT"
  else
    warn "mDNS found nothing (it is useless without a network) → scanning the local ports instead"
    SCAN=$(python3 - <<'PYEOF' 2>/dev/null
import socket, concurrent.futures
def probe(x):
    s=socket.socket(); s.settimeout(0.12)
    try:
        s.connect(('127.0.0.1', x)); s.close(); return x
    except Exception: return None
found=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=500) as ex:
    for r in ex.map(probe, range(30000, 61000), chunksize=128):
        if r: found.append(r)
print(' '.join(str(x) for x in found))
PYEOF
)
    [ -n "$SCAN" ] && { ok "Local scan found candidate ports: $SCAN"; CANDS="$SCAN"; } || warn "The local scan found no listening port either (adbd did not start)"
  fi
fi

step "⑤ Connect adb"
if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
  ok "adb connected: $(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
else
  CONNECTED=0
  for p in $CANDS 5555; do
    t="127.0.0.1:$p"
    [ "$DRY" = 1 ] && { printf '   · [dry] adb connect %s\n' "$t"; CONNECTED=1; break; }
    timeout 15 adb connect "$t" >/dev/null 2>&1
    if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then ok "Connected to $t"; CONNECTED=1; break
    else adb disconnect "$t" >/dev/null 2>&1; fi   # a port that is not adbd leaves a fake offline entry, so clean it up right away
  done
  if [ "$CONNECTED" != 1 ]; then
    step "⑤·diagnosis: why it cannot connect (no longer just "cannot" connect)"
    N3=$(timeout 12 "$SOCK" netstate 2>/dev/null || true)
    W3=$(printf '%s' "$N3" | grep -oE '"wifi_on": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    O3=$(printf '%s' "$N3" | grep -oE '"online": *(true|false)' | grep -oE '(true|false)$')
    A3=$(printf '%s' "$N3" | grep -oE '"adb_wifi": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    printf "$(dsh_msg '   Now: wifi_on=%s online=%s adb_wifi=%s\n')" "$W3" "$O3" "$A3"
    if [ "$W3" = "1" ] && [ "$A3" = "0" ] && [ "$O3" != "true" ]; then
      bad "The Wi-Fi switch is on, but there is **no real network connection** → the system cleared \"Wireless debugging\" back to 0"
      printf "$(dsh_msg '   (Wireless debugging depends on "a usable Wi-Fi network", not just the switch; dialing 1.1.1.1:443 failed too)\n')"
      printf "$(dsh_msg '   What to do: connect to a Wi-Fi network (an AP) — not just flip the switch; re-run this widget once connected\n')"
    elif [ "$W3" = "1" ] && [ "$A3" = "0" ]; then
      bad "The network works (online=true), but the system cleared the switch back to 0 — not a network problem"
      printf "$(dsh_msg '   This OS build only honors the manual switch-on under Developer options → Wireless debugging\n')"
      printf "$(dsh_msg '   What to do: Settings → Developer options → Wireless debugging → turn it on once, then re-run this widget\n')"
    elif [ "$W3" = "1" ] && [ "$A3" = "1" ]; then
      bad "The switch is 1, but nothing is listening for adbd → the framework was not really started"
      printf "$(dsh_msg '   What to do: Developer options → Wireless debugging → turn it on by hand once (after that this widget can keep it up automatically)\n')"
    else
      bad "Prerequisites still unmet (wifi_on=$W3 online=$O3 adb_wifi=$A3)"
    fi
    exit 1
  fi
fi

step "⑥ Verify"
if [ "$DRY" != 1 ]; then
  echo -n "   shell: "; timeout 20 adb shell echo "adb-ok $(getprop ro.product.model 2>/dev/null)" 2>&1 | tail -1
  echo -n "   fixed port status: "; adb devices | awk 'NR>1{printf "%s(%s) ", $1, $2}'; echo
fi
printf "$(dsh_msg '\n  Tip: to keep adb available long term → leave both Wi-Fi and "Wireless debugging" on; to take it back → just turn "Wireless debugging" off\n')"
done_
