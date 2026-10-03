#!/data/data/com.termux/files/usr/bin/bash
# lib-tests - unit tests for the pure helpers in widgets/common.sh.
#
# Why this file exists: the widget scripts themselves need the phone (adb, the bridge,
# port 8080), so tests/selftest.sh can only ever say something useful on the phone. The
# helpers those scripts share are plain bash though. This file exercises them against a
# throw-away HOME, a local HTTP server and a temp log file: no adb, no bridge, no real
# ~/.dsh* path, no network beyond 127.0.0.1.
#
# Usage: bash tests/lib-tests.sh
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="${TMPDIR:-/tmp}/dsh-lib-tests.$$"
H="$WORK/home"
LIBDIR="$H/.local/share/dsh-widgets"
LOCK="$H/.dsh-boot-libtest.lock"

cleanup() {
  [ -n "${SRV_PID:-}" ] && kill "$SRV_PID" 2>/dev/null
  [ -n "${SLEEP_PID:-}" ] && kill "$SLEEP_PID" 2>/dev/null
  rm -rf "$WORK"
}
trap cleanup EXIT

rm -rf "$WORK"
mkdir -p "$LIBDIR" "$H/.smoke" "$H/.dsh" "$WORK/www"
cp "$ROOT/widgets/common.sh" "$ROOT/widgets/i18n.sh" "$LIBDIR/"
printf 'en' > "$WORK/dsh-lang"

# A throw-away HOME: nothing below may touch the real one.
export DSH_WIDGET_LOG=0          # the library writes a widget log on load otherwise
export DSH_LANG_FILE="$WORK/dsh-lang"
export HOME_DIR="$H"             # the library sets this to the phone path at load time

# shellcheck source=/dev/null
. "$LIBDIR/common.sh"
HOME_DIR="$H"
SHARED="$H/storage/shared"
DL="$SHARED/Download"
DSH_DIR="$DL/dsh"
LOGS="$H/.smoke"
BOOT_LOCK_DIR="$LOCK"
CRED_LOCK="$H/.dsh/.credentials.yaml.lock"

PASS=0; FAIL=0; SKIP=0
assert_eq() {   # assert_eq <label> <want> <got>
  if [ "$2" = "$3" ]; then PASS=$((PASS + 1)); printf '   ✔ %s\n' "$1"
  else FAIL=$((FAIL + 1)); printf '   ✘ %s\n       want [%s]\n       got  [%s]\n' "$1" "$2" "$3"; fi
}
assert_ok() {   # assert_ok <label> <command...>
  local label="$1"; shift
  if "$@"; then PASS=$((PASS + 1)); printf '   ✔ %s\n' "$label"
  else FAIL=$((FAIL + 1)); printf '   ✘ %s (expected success, exit %s)\n' "$label" "$?"; fi
}
assert_fail() { # assert_fail <label> <command...>
  local label="$1"; shift
  if "$@"; then FAIL=$((FAIL + 1)); printf '   ✘ %s (expected failure)\n' "$label"
  else PASS=$((PASS + 1)); printf '   ✔ %s\n' "$label"; fi
}
rc_of() { "$@" >/dev/null 2>&1; printf '%s' "$?"; }

free_port() {
  python3 - <<'PY'
import socket
s = socket.socket()
s.bind(("127.0.0.1", 0))
print(s.getsockname()[1])
s.close()
PY
}

printf 'library: %s\nwork dir: %s\n' "$LIBDIR/common.sh" "$WORK"

PORT="$(free_port)"
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WORK/www" >"$WORK/srv.log" 2>&1 &
SRV_PID=$!
CLOSED="$(free_port)"

# Wait until the server is really listening before asking anything about it: a cold
# python3 takes a moment (a slow CI runner much more than a warm laptop), and the
# assertions below are about port_open, not about how fast http.server starts.
# This poll is deliberately independent of the library under test.
ready=0
for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do
  if python3 -c 'import socket,sys; s=socket.socket(); s.settimeout(0.5); sys.exit(0 if s.connect_ex(("127.0.0.1", int(sys.argv[1]))) == 0 else 1)' "$PORT" 2>/dev/null; then
    ready=1
    break
  fi
  sleep 0.2
done
if [ "$ready" -ne 1 ]; then
  printf '   ! the local server never came up on port %s (see %s)\n' "$PORT" "$WORK/srv.log"
fi

printf '\n▶ token_from_log: read the auth URL back out of a boot log\n'
printf 'noise\nhttp://127.0.0.1:8080/?token=AbC-123_xyz\nmore noise\n' > "$WORK/boot.log"
assert_eq "the token URL comes out of the log" 'http://127.0.0.1:8080/?token=AbC-123_xyz' "$(token_from_log "$WORK/boot.log")"
assert_eq "a missing log means no URL" '' "$(token_from_log "$WORK/nope.log")"
printf 'http://127.0.0.1:8080/?token=OLD\nhttp://127.0.0.1:8080/?token=NEW\n' > "$WORK/two.log"
assert_eq "after a restart the last token wins" 'http://127.0.0.1:8080/?token=NEW' "$(token_from_log "$WORK/two.log")"

printf '\n▶ ports and URLs: talk to a real socket, not a mock\n'
assert_ok "port_open sees the local server" port_open "$PORT"
assert_fail "port_open is false for a closed port" port_open "$CLOSED"
assert_ok "wait_port_open returns once the port answers" wait_port_open "$PORT" 5
assert_ok "wait_port_free returns for a free port" wait_port_free "$CLOSED" 5
assert_eq "url_code is 200 for the server" '200' "$(url_code "http://127.0.0.1:$PORT/")"
assert_eq "url_code is 000 for a closed port" '000' "$(url_code "http://127.0.0.1:$CLOSED/")"
assert_ok "url_ready needs the 200" url_ready "http://127.0.0.1:$PORT/"

printf '\n▶ boot lock: one instance at a time, and a stale lock can be taken over\n'
assert_ok "acquire creates the lock" boot_lock_acquire
assert_eq "the lock records this process" "$$" "$(boot_lock_owner)"
assert_ok "a second acquire from the same process is a no-op" boot_lock_acquire
assert_fail "a fresh lock held by a live process is not stale" boot_lock_stale
assert_ok "release removes it" boot_lock_release
assert_eq "release leaves no pid behind" '' "$(boot_lock_owner)"

# Same call from a child process, so the recorded pid dies with it.
bash -c '. "$1"; HOME_DIR="$2"; BOOT_LOCK_DIR="$3"; export DSH_WIDGET_LOG=0; boot_lock_acquire' \
  _ "$LIBDIR/common.sh" "$H" "$LOCK" >/dev/null 2>&1
assert_eq "a child process can take the lock" 'yes' "$([ -f "$LOCK/pid" ] && echo yes || echo no)"
assert_ok "a lock whose owner is gone counts as stale" boot_lock_stale
assert_ok "acquire takes over the stale lock" boot_lock_acquire
assert_eq "and now records this process" "$$" "$(boot_lock_owner)"
boot_lock_release

# The other staleness rule: the recorded pid is alive but the lock is older than 5
# minutes (pids get reused, so the pid alone can look alive forever). The mtime is moved
# back so the test does not have to wait five minutes.
rm -rf "$LOCK"; mkdir -p "$LOCK"
sleep 30 & SLEEP_PID=$!
printf '%s' "$SLEEP_PID" > "$LOCK/pid"
touch -d '10 minutes ago' "$LOCK" 2>/dev/null
if [ -n "$(find "$LOCK" -maxdepth 0 -mmin +5 2>/dev/null)" ]; then
  assert_ok "a 10-minute-old lock with a live pid is stale too" boot_lock_stale
else
  SKIP=$((SKIP + 1)); printf '   · skipped: the age rule (this platform would not move the mtime back)\n'
fi
kill "$SLEEP_PID" 2>/dev/null; SLEEP_PID=
rm -rf "$LOCK"

printf '\n▶ credentials lock: only an orphan may be removed\n'
# dsh_alive() is stubbed in both directions on purpose: whether some dsh web happens to
# run on this machine is not what this test is about.
dsh_alive() { return 0; }        # pretend an instance is up
: > "$CRED_LOCK"
assert_eq "a running dsh keeps its lock"        '1' "$(rc_of clear_orphan_cred_lock "$CRED_LOCK")"
assert_eq "so the file is still there"          'yes' "$([ -f "$CRED_LOCK" ] && echo yes || echo no)"
dsh_alive() { return 1; }        # no instance: the lock is an orphan
assert_eq "an orphan lock is cleared"           '0' "$(rc_of clear_orphan_cred_lock "$CRED_LOCK")"
assert_eq "and the file is gone"                'no' "$([ -f "$CRED_LOCK" ] && echo yes || echo no)"
assert_eq "nothing to clear reports 3"          '3' "$(rc_of clear_orphan_cred_lock "$H/.dsh/nope.lock")"

printf '\n▶ rotate_log: grow past the limit, move the old file aside\n'
SMALL="$H/small.log"; printf 'a few lines\n' > "$SMALL"
assert_eq "a small log is left alone" '0' "$(rc_of rotate_log "$SMALL" 2)"
assert_eq "and there is no .bak" 'no' "$([ -f "$SMALL.bak" ] && echo yes || echo no)"
BIG="$H/big.log"
head -c 3000000 /dev/zero | tr '\0' 'x' > "$BIG"
assert_eq "a 3MB log rotates" '0' "$(rc_of rotate_log "$BIG" 1)"
assert_eq "the old content is in .bak" '3000000' "$(stat -c%s "$BIG.bak" 2>/dev/null || echo missing)"
assert_eq "and the live log starts empty again" '0' "$(stat -c%s "$BIG" 2>/dev/null || echo missing)"

printf '\n▶ run() and the common paths\n'
MARK="$H/marker"
DRY=1
run "touch '$MARK'" >/dev/null 2>&1
assert_eq "run() under --dry-run prints and does not execute" 'no' "$([ -f "$MARK" ] && echo yes || echo no)"
DRY=0
run "touch '$MARK'" >/dev/null 2>&1
assert_eq "run() executes otherwise" 'yes' "$([ -f "$MARK" ] && echo yes || echo no)"
assert_eq "dsh_log lives in HOME_DIR" "$H/.dsh-restart.log" "$(dsh_log)"
DRY=0
parse_args --dry-run >/dev/null 2>&1
assert_eq "parse_args turns --dry-run into DRY=1" '1' "$DRY"
DRY=0

printf '\n════ Result: %s passed / %s failed / %s skipped ════\n' "$PASS" "$FAIL" "$SKIP"
[ "$FAIL" = 0 ] || exit 1
printf '✔ widgets/common.sh behaves (no phone involved)\n'
exit 0
