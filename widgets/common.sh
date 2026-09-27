#!/data/data/com.termux/files/usr/bin/bash
# ============ DSH widget shared library (used by every task script) ============
# Design rules: one line per step + ✔/✘; verify whatever can be verified; always poll instead of sleeping blindly; idempotent, supports --dry-run
set -u

DSH_PORT="${DSH_PORT:-8080}"
HOME_DIR="/data/data/com.termux/files/home"
SHARED="$HOME_DIR/storage/shared"
DL="$SHARED/Download"
DSH_DIR="$DL/dsh"
LOGS="$HOME_DIR/.smoke"
DRY=0
T0=$(date +%s)

SELF="$(basename "$0")"
# ---- i18n: source the language layer that sits next to this file (or the installed copy) ----
# Source text in this repo is English; when ~/.dsh-lang says zh, dsh_msg() maps the string back.
_i18n_here="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" 2>/dev/null && pwd || echo "$HOME_DIR/.local/share/dsh-widgets")"
for _f in "$_i18n_here/i18n.sh" "$HOME_DIR/.local/share/dsh-widgets/i18n.sh"; do
  [ -f "$_f" ] && . "$_f" && break
done
# If the layer is missing for any reason, fall back to printing English unchanged.
command -v dsh_msg >/dev/null 2>&1 || dsh_msg() { printf '%s' "$1"; }

# Every user-visible line goes through dsh_msg() so the language switch reaches all 10 widgets
# without touching a single call site. The completion banners in die()/done_() below are DATA
# (parsed by dsh-status-pub / dsh-tasksd / the page panel) and must never be translated.
log()  { printf '%s %s\n' "$(date '+%H:%M:%S')" "$(dsh_msg "$*")"; }
step() { printf '\n▶ %s\n' "$(dsh_msg "$*")"; }
ok()   { printf '   ✔ %s\n' "$(dsh_msg "$*")"; }
warn() { printf '   ⚠ %s\n' "$(dsh_msg "$*")"; }
bad()  { printf '   ✘ %s\n' "$(dsh_msg "$*")" >&2; }
die()  { bad "$*"; publish_status; printf '\n[%s] failed (took %ss)\n' "$SELF" "$(( $(date +%s) - T0 ))"; exit 1; }
done_() { publish_status; printf '\n[%s] done, took %ss\n' "$SELF" "$(( $(date +%s) - T0 ))"; }
need()  { command -v "$1" >/dev/null 2>&1; }
run()   { if [ "$DRY" = 1 ]; then printf '   · [dry] %s\n' "$(dsh_msg "$*")"; else eval "$@"; fi; }

parse_args() { for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; esac; done; [ "$DRY" = 1 ] && log "== dry-run mode: print only, do not execute =="; }

# ---- Network / process probing (bash built-in /dev/tcp, zero dependencies) ----
port_open() { (exec 3<>/dev/tcp/127.0.0.1/"$1") 2>/dev/null && exec 3<&- 3>&- && return 0 || return 1; }
wait_port_open() { local p=$1 t=${2:-40} i=0; while [ $i -lt $((t*4)) ]; do port_open "$p" && return 0; sleep 0.25; i=$((i+1)); done; return 1; }
wait_port_free() { local p=$1 t=${2:-20} i=0; while [ $i -lt $((t*4)) ]; do port_open "$p" || return 0; sleep 0.25; i=$((i+1)); done; return 1; }
http_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$1/" 2>/dev/null; }

# ---- Processes ----
pids_of()  { pgrep -f "$1" 2>/dev/null | tr '\n' ' '; }
kill_wait() {   # $1=pattern $2=seconds $3=signal
  local pat="$1" t="${2:-10}" sig="${3:-TERM}" i=0
  if [ "${DRY:-0}" = 1 ]; then printf '   · [dry] kill -%s on processes matching %s\n' "$sig" "$pat"; return 0; fi
  local ps; ps=$(pids_of "$pat")
  [ -z "$ps" ] && return 0
  for p in $ps; do kill -"$sig" "$p" 2>/dev/null; done
  while [ $i -lt $((t*4)) ]; do ps=$(pids_of "$pat"); [ -z "$ps" ] && return 0; sleep 0.25; i=$((i+1)); done
  return 1
}

# ============ DSH startup / true-readiness check (2026-09-26: fix the "404 half-start" false positive) ============
# Why the port alone is not enough: dsh web "binds the port first, mounts routes later" —
#   the port is already listening while the plugin tree loads, but the `dsh web: http://…/?token=…` line
#   only prints once the whole tree is loaded (15~30s); until then no handler is mounted → 404.
#   Open the browser as soon as the port is up and you get 404 (401 with a stale token).
# So "ready" = ① the token line shows up in the log ② that URL returns 200 after following 303 ③ the process is still alive.
CRED_LOCK="$HOME_DIR/.dsh/.credentials.yaml.lock"
# The boot lock is **per port**: sandbox tests run on 8099 and must never contend with your 8080 for the same lock.
# (They used to share one → while my test held the lock, tapping "6_hard-restart-dsh" showed "another startup is already in progress")
BOOT_LOCK_DIR="$HOME_DIR/.dsh-boot-${DSH_PORT}.lock"
BOOT_LOCK_HELD=0
TOKEN_RE='http://127\.0\.0\.1:[0-9]+/\?token=[A-Za-z0-9_-]+'

dsh_pids()  { pgrep -f 'bin[.]js web' 2>/dev/null; }   # Brackets: avoid matching this very command line
dsh_alive() { [ -n "$(dsh_pids)" ]; }
dsh_log_reset() { if [ "${DRY:-0}" = 1 ]; then printf '   · [dry] reset the boot log\n'; else : > "$1"; fi; }
token_from_log() { local f="${1:-$(dsh_log)}"; [ -f "$f" ] && grep -oE "$TOKEN_RE" "$f" | tail -1; }

# Only a genuinely usable auth URL counts: no token → 401; stale token → 401; half-started → 404; only 200 is good
url_code()  { curl -sL -c /dev/null -o /dev/null -w '%{http_code}' --max-time 8 "$1" 2>/dev/null; }
url_ready() { [ "$(url_code "$1")" = "200" ]; }

# Boot mutex: keep a double tap from starting two dsh web instances (they fight over the credentials write lock → one always crashes)
# Idempotent: repeat calls from the same process just succeed (otherwise "widget takes the lock + shared start helper takes it again" blocks itself)
# Staleness needs both checks, neither alone is enough:
#   ① the recorded pid is dead; ② the lock itself is older than 5 minutes (pids get reused, so pid alone may look "alive" forever)
boot_lock_stale() {
  local p; p=$(cat "$BOOT_LOCK_DIR/pid" 2>/dev/null)
  if [ -z "$p" ] || ! kill -0 "$p" 2>/dev/null; then return 0; fi
  [ -n "$(find "$BOOT_LOCK_DIR" -maxdepth 0 -mmin +5 2>/dev/null)" ] && return 0
  return 1
}
boot_lock_acquire() {
  local p; p=$(cat "$BOOT_LOCK_DIR/pid" 2>/dev/null)
  if [ -d "$BOOT_LOCK_DIR" ] && [ "$p" = "$$" ]; then BOOT_LOCK_HELD=1; return 0; fi
  if mkdir "$BOOT_LOCK_DIR" 2>/dev/null; then printf '%s' "$$" > "$BOOT_LOCK_DIR/pid"; BOOT_LOCK_HELD=1; return 0; fi
  if boot_lock_stale; then
    rm -rf "$BOOT_LOCK_DIR" 2>/dev/null
    mkdir "$BOOT_LOCK_DIR" 2>/dev/null || return 1
    printf '%s' "$$" > "$BOOT_LOCK_DIR/pid"; BOOT_LOCK_HELD=1; return 0
  fi
  return 1
}
boot_lock_release() { [ "$BOOT_LOCK_HELD" = 1 ] && rm -rf "$BOOT_LOCK_DIR" 2>/dev/null; BOOT_LOCK_HELD=0; return 0; }
boot_lock_owner() { cat "$BOOT_LOCK_DIR/pid" 2>/dev/null; }

# Stale credentials write lock: dsh-atomic-write waits at most 2 seconds, and "an orphan lock can only be cleared by hand".
# When a process is killed with -9 (widget 6 does exactly that) the lock file stays forever → every later start is bound to fail. Hence the rule:
# with no dsh instance running it is an orphan and can be deleted. Returns 0=cleared 1=instance running, left alone 2=cannot delete 3=was not there
clear_orphan_cred_lock() {   # $1=lock path (defaults to the real path, for unit tests)
  local lk="${1:-$CRED_LOCK}"
  [ -f "$lk" ] || return 3
  dsh_alive && return 1
  rm -f "$lk" 2>/dev/null && return 0 || return 2
}

# Start dsh web and echo the new process pid (setsid forks, so $! is not the real pid → diff the process table before/after)
# DSH_WEB_EXTRA passes extra arguments (sandbox tests use "--patch ~/.smoke/patch.yml" to turn filetransfer off,
# otherwise the second instance fights for port 3199 and the plugin tree fails to load).
# Mind the order: dsh web "passes through from the first unknown option", so options that belong to
# the subcommand itself must come **before** --port; writing `dsh web --port 8099 --patch F` reports
# unknown option '--patch'.
dsh_start() {   # $1=log file
  local f="$1" before after newp x i=0
  before=$(dsh_pids | tr '\n' ' ')
  # DISPLAY is for `dsh-host-open-in-app`: on the Linux branch it uses
  #   `present(env.DISPLAY) || present(env.WAYLAND_DISPLAY)` to decide "is there a desktop",
  # and refuses the open action without one (the "this host has no usable desktop…" line in deliverables).
  # On Termux the real work is done by the bundled xdg-open (it is termux-open → handed to an Android viewer),
  # so we merely flip the "has a desktop" switch so DSH agrees to call xdg-open.
  # Set DSH_DISPLAY="" before starting if you do not want it.
  DISPLAY="${DSH_DISPLAY-:0}" setsid nohup dsh web ${DSH_WEB_EXTRA:-} --port "$DSH_PORT" >>"$f" 2>&1 </dev/null &
  while [ $i -lt 40 ]; do
    after=$(dsh_pids | tr '\n' ' ')
    newp=""
    for x in $after; do case " $before " in *" $x "*) ;; *) newp="$x"; break ;; esac; done
    [ -n "$newp" ] && { printf '%s' "$newp"; return 0; }
    sleep 0.25; i=$((i+1))
  done
  return 1
}

# Wait for true readiness: 0=success (echoes the URL) 1=timeout 2=process exited early
wait_dsh_ready() {   # $1=timeout seconds $2=log $3=pid (optional)
  local t="${1:-90}" f="$2" pid="${3:-}" i=0 u
  while [ $i -lt $((t*2)) ]; do
    if [ -n "$pid" ] && ! kill -0 "$pid" 2>/dev/null; then return 2; fi
    u=$(token_from_log "$f")
    [ -n "$u" ] && url_ready "$u" && { printf '%s\n' "$u"; return 0; }
    sleep 0.5; i=$((i+1))
  done
  return 1
}

# Start the service + wait for true readiness + write back ~/.dsh-url; on failure print the log tail (with the crash reason)
dsh_start_and_wait() {   # $1=log $2=timeout seconds
  local f="$1" t="${2:-90}" pid="" U="" rc=0
  # The boot mutex lives here so every caller (widgets 1/4/6, dsh-restart) is protected automatically,
  # so a new entry point cannot forget the lock and bring back "two taps → two instances fight over the write lock → one crashes".
  if ! boot_lock_acquire; then
    bad "Another startup is already in progress (lock held by pid $(boot_lock_owner)) — wait for it to finish, do not tap twice"
    return 3
  fi
  clear_orphan_cred_lock; rc=$?
  [ "$rc" = 0 ] && ok "Cleared the stale credentials write lock (startup is bound to fail while it is there)"
  [ "$rc" = 1 ] && warn "Credentials write lock is held (an instance is running), left untouched"
  pid=$(dsh_start "$f") || { bad "The dsh web process did not start"; tail -6 "$f" 2>/dev/null | sed 's/^/     /'; return 1; }
  ok "Process started (pid $pid)"
  U=$(wait_dsh_ready "$t" "$f" "$pid"); rc=$?
  case "$rc" in
    0) printf '%s\n' "$U" > "$HOME_DIR/.dsh-url"
       ok "Readiness check passed: the auth URL returns 200 after redirects"
       printf '   · %s\n' "$U"; return 0 ;;
    2) bad "The process exited during startup (most likely the plugin tree failed to load)"
       tail -10 "$f" 2>/dev/null | sed 's/^/     /'; return 2 ;;
    *) bad "No usable auth URL within ${t}s"
       tail -10 "$f" 2>/dev/null | sed 's/^/     /'; return 1 ;;
  esac
}

# ---- Wake / confirm the DSH bridge (2026-09-26 fix: the broadcast must carry the token) ----
# Why the token is mandatory: since bridge v1.7, when the service has been shut down (sleep/panic/unbound by the system),
# only a WAKE with the **correct token** may write itself back into the system accessibility list — that is the security boundary.
# I only changed droid-sock at first and forgot the bare `am broadcast` calls in the widgets → so after widget 2 used sleep,
# widgets 1/6/7/8 could not wake the bridge at all (hit for real: "bridge will not start" in the widget 8 log).
bridge_token() { [ -f "$HOME_DIR/.dsh-bridge-token" ] && cat "$HOME_DIR/.dsh-bridge-token" 2>/dev/null; }

bridge_wake() {   # Send one wake broadcast (with the token; without one only a weak wake is possible)
  local tok; tok=$(bridge_token)
  if [ -n "$tok" ]; then
    am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver --es token "$tok" >/dev/null 2>&1
    sleep 2; return 0
  fi
  warn "No ~/.dsh-bridge-token (revoked?) → weak wake only: it cannot wake an already stopped service"
  am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver >/dev/null 2>&1
  sleep 2; return 0
}

bridge_alive() {   # Listening ≠ working: a frozen service listens but never answers, so really ping it once
  [ -x "$HOME_DIR/.local/bin/droid-sock" ] || return 1
  timeout 12 "$HOME_DIR/.local/bin/droid-sock" ping >/dev/null 2>&1
}

bridge_ensure() {  # Works=0; otherwise wake and retry (two beats max — the system sometimes needs two to bind accessibility)
  bridge_alive && return 0
  bridge_wake; bridge_alive && return 0
  bridge_wake; bridge_alive
}


# ---- Task executor (for the web UI shortcut buttons) ----
# Loopback only, allowlist + token; starts and stops together with DSH.
TASKSD_PORT=8787
tasksd_pids() { pgrep -f '[d]sh-tasksd' 2>/dev/null; }
tasksd_alive() { port_open "$TASKSD_PORT"; }
tasksd_ensure() {
  tasksd_alive && return 0
  [ -x "$HOME_DIR/.local/bin/dsh-tasksd" ] || return 1
  setsid nohup "$HOME_DIR/.local/bin/dsh-tasksd" >>"$HOME_DIR/.smoke/tasksd.log" 2>&1 </dev/null &
  local i=0; while [ $i -lt 20 ]; do tasksd_alive && return 0; sleep 0.25; i=$((i+1)); done
  return 1
}
tasksd_stop() {
  local ps; ps=$(tasksd_pids); [ -z "$ps" ] && return 0
  for x in $ps; do kill "$x" 2>/dev/null; done
  return 0
}


# ---- Status publishing (for external apps / your own eyes) ----
# Writes status to shared storage Download/dsh/状态/status.json; the Console APK reads it to light the status lamp.
# Refreshed on every widget success/failure; a failure here does not affect the widget itself.
publish_status() {
  [ -x "$HOME_DIR/.local/bin/dsh-status-pub" ] || return 0
  if [ "${DRY:-0}" = 1 ]; then printf '   · [dry] publish status to Download/dsh/状态/\n'; return 0; fi
  timeout 40 "$HOME_DIR/.local/bin/dsh-status-pub" --quiet >/dev/null 2>&1 || true
  return 0
}

# ---- Logs ----
rotate_log() {  # $1=file $2=keep MB (default 2)
  local f="$1" mb="${2:-2}"
  [ -f "$f" ] || return 0
  local sz=$(( $(stat -c%s "$f" 2>/dev/null || echo 0) / 1048576 ))
  [ "$sz" -lt "$mb" ] && return 0
  run "mv -f '$f' '$f.bak' && : > '$f'"
  ok "Log rotated: $(basename "$f") (${sz}MB → .bak)"
}

# ---- Common paths ----
dsh_log() { echo "$HOME_DIR/.dsh-restart.log"; }

# ============ Widget output also lands on disk (added 2026-09-26) ============
# Why this exists: the output of a widget lives only in the session Termux:Widget pops up, and it is gone once that closes
# → when you say "it broke again" I have no evidence at all and can only guess.
# Now every widget appends its full output for this run to ~/.smoke/widget-<name>.log,
# so I can read it myself instead of asking you to repeat it. Set DSH_WIDGET_LOG=0 to disable (handy in tests).
if [ "${DSH_WIDGET_LOG:-1}" = 1 ] && [ -z "${DSH_WIDGET_LOG_DONE:-}" ]; then
  case "$SELF" in
  *.sh)   # Only real widget scripts log to disk; when sourced via bash -c etc. do not leave junk like widget-bash.log
    DSH_WIDGET_LOG_DONE=1   # Deliberately **not exported**: exporting would pass it to child processes (suite→widget, widget→widget)
                            # and the child widget would think it had already logged and skip its own log (hit for real)
    WLOG="$LOGS/widget-${SELF%.sh}.log"
    mkdir -p "$LOGS" 2>/dev/null
    # Keep only the last ~256KB so it does not grow forever
    if [ -f "$WLOG" ] && [ "$(stat -c%s "$WLOG" 2>/dev/null || echo 0)" -gt 262144 ]; then
      tail -c 32768 "$WLOG" > "$WLOG.tmp" 2>/dev/null && mv -f "$WLOG.tmp" "$WLOG" 2>/dev/null
    fi
    printf '\n===== %s  %s start (pid %s)=====\n' "$(date '+%F %T')" "$SELF" "$$" >> "$WLOG" 2>/dev/null
    exec > >(tee -a "$WLOG") 2>&1
    ;;
  esac
fi
