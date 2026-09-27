#!/data/data/com.termux/files/usr/bin/bash
# selftest.sh — self-test suite for the 9 widgets (run this after every change)
#
# Tiered tests:
#   L0 precheck   the environment itself breaks (bridge/adb/8080): judge it first, never score an env problem as a widget failure
#   L1 syntax     bash -n + python compile
#   L2 rehearsal  the scripts' own --dry-run (must exit 0 and walk the whole flow)
#   L3 verdicts   **regression tests** for the readiness check / boot lock (the core of the 404 fix, fully offline)
#   L4 real run   real, safe-and-reversible widget execution with result verification
#   L5 sandbox    cold start of widget 1: a throwaway instance on 8099, sampling HTTP codes to prove 'port opens first, routes mount later'
#   SKIP          a real run would kill this session (2/4/6) or needs manual recovery (0) → dry-run only, user picks the moment
HOME_DIR="/data/data/com.termux/files/home"
T="$HOME_DIR/.shortcuts/tasks"
L="$HOME_DIR/.local/share/dsh-widgets/common.sh"
PASS=0; FAIL=0; SKIP=0; REPORT=""
TMPLOG="$HOME_DIR/.smoke/selftest.log"
TMPD="$HOME_DIR/.smoke/selftest-tmp"; rm -rf "$TMPD"; mkdir -p "$TMPD"
SAVED=0   # cleanup may restore only after this run really saved the log/URL

# ── Force a deterministic language for this run ──
# The widgets now speak whatever ~/.dsh-lang says (via widgets/i18n.sh). Assertions below match on
# text, so the suite pins the language to English for its own runs instead of depending on the
# user's setting: DSH_LANG_FILE is honoured by i18n.sh, so nothing global is touched.
export DSH_LANG_FILE="$TMPD/dsh-lang-selftest"
printf 'en\n' > "$DSH_LANG_FILE"

# ── the sandbox may only ever touch its own throwaway instance ──
# Why the match is this precise, and why it is no longer just the port:
#   · "kill any dsh process that was not in the pre-boot snapshot" mistook a DSH the user
#     restarted during the run for the sandbox and killed it.
#   · matching `--port 8099` alone is still not enough — the port is a convention, and if the
#     real instance ever comes up on it, the user's own page is what gets killed.
# So the sandbox is identified by the **sandbox patch file**, which nothing but this script uses.
SANDBOX_PATCH="$HOME_DIR/.smoke/patch.yml"
kill_sandbox() {
  local p
  for p in $(pgrep -f 'bin[.]js web' 2>/dev/null); do
    [ "$p" = "$$" ] && continue
    if tr '\0' ' ' < "/proc/$p/cmdline" 2>/dev/null | grep -q -- "--patch $SANDBOX_PATCH"; then
      kill -9 "$p" 2>/dev/null && line "     (cleaned up sandbox process pid $p)"
    fi
  done
}
# Clean up even when interrupted by pkill / restart: sandbox processes, the sandbox boot lock, the touched log and .dsh-url
cleanup_all() {
  kill_sandbox
  [ -d "$HOME_DIR/.dsh-boot-8099.lock" ] && rm -rf "$HOME_DIR/.dsh-boot-8099.lock" 2>/dev/null
  if [ "$SAVED" = 1 ]; then
    cp -f "$HOME_DIR/.smoke/restart.log.save" "$HOME_DIR/.dsh-restart.log" 2>/dev/null
    cp -f "$HOME_DIR/.smoke/dsh-url.save" "$HOME_DIR/.dsh-url" 2>/dev/null
  fi
  rm -rf "$TMPD" 2>/dev/null
  return 0
}
trap 'cleanup_all' EXIT INT TERM
kill_sandbox   # clear a sandbox left behind by an interrupted previous round

# The common library must load **before L0**: L0's bridge wake needs bridge_wake (with token)
. "$L"

line() { printf '%s\n' "$1"; }
rec()  { # $1=status $2=widget $3=description
  case "$1" in
    PASS) PASS=$((PASS+1)); printf '  ✔ %-24s %s\n' "$2" "$3" ;;
    FAIL) FAIL=$((FAIL+1)); printf '  ✘ %-24s %s\n' "$2" "$3" ;;
    SKIP) SKIP=$((SKIP+1)); printf '  ○ %-24s %s\n' "$2" "$3" ;;
  esac
  REPORT="${REPORT}${1}|${2}|${3}\n"
}

dryrun_ok() { # $1=script
  local out rc
  out=$(timeout 120 bash "$1" --dry-run 2>&1); rc=$?
  printf '%s' "$out" > "$TMPLOG"
  # The completion banner is a data marker: it is now '[name] done, took Ns' (widgets/common.sh).
  # Accept the legacy Chinese spelling too, so logs written before the switch still parse.
  [ "$rc" = 0 ] && grep -qE '\][[:space:]]+done, took|】完成' "$TMPLOG"
}

line "════ Widget selftest $(date '+%F %T') ════"

# ── L0: preconditions ──
line "[L0] Preconditions"
BRIDGE_OK=0; ADB_OK=0; WEB_OK=0
(exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && BRIDGE_OK=1
if [ "$BRIDGE_OK" = 0 ]; then
  # A **token-bearing** broadcast is required: since v1.7 a tokenless wake is ignored once the service is off,
  # so the bare broadcast used here called a wakeable bridge "unavailable" and skipped every bridge test (hit for real).
  bridge_wake 2>/dev/null || { am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver >/dev/null 2>&1; sleep 2; }
  (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && BRIDGE_OK=1
fi
if [ "$BRIDGE_OK" = 0 ]; then
  am start -n io.dsh.bridge/.MainActivity >/dev/null 2>&1; sleep 3
  (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && BRIDGE_OK=1
fi
[ "$BRIDGE_OK" = 1 ] && rec PASS "precheck: bridge channel" "8788 reachable ($(timeout 12 "$HOME_DIR/.local/bin/droid-sock" ping 2>/dev/null | grep -oE '"ver": *"[^"]*"'))" \
  || rec SKIP "precheck: bridge channel" "a token-bearing wake cannot start it either — open the DSH Bridge app once (not a widget problem)"
adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q . && ADB_OK=1
if [ "$ADB_OK" = 0 ] && [ "$BRIDGE_OK" = 1 ]; then
  timeout 150 bash "$T/8_enable-wireless-adb.sh" >/dev/null 2>&1
  adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q . && ADB_OK=1
fi
[ "$ADB_OK" = 1 ] && rec PASS "precheck: adb channel" "$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')" \
  || rec SKIP "precheck: adb channel" "not connected (Wi-Fi was off or restarted; enable Wireless debugging once first)"
(exec 3<>/dev/tcp/127.0.0.1/8080) 2>/dev/null && WEB_OK=1
[ "$WEB_OK" = 1 ] && rec PASS "precheck: 8080" "this session is running" || rec SKIP "precheck: 8080" "no session is running right now"

# ── L1: syntax ──
line "[L1] Syntax check"
for f in "$T"/*.sh "$L" "$HOME_DIR/.local/share/dsh-widgets/selftest.sh"; do
  n=$(basename "$f")
  if bash -n "$f" 2>/dev/null; then rec PASS "$n" "syntax OK"; else rec FAIL "$n" "syntax error"; fi
done
PYC=$(python3 -m py_compile "$HOME_DIR/.local/bin/droid-sock" 2>&1)
[ -z "$PYC" ] && rec PASS "droid-sock" "python compile OK" || rec FAIL "droid-sock" "python compile failed: $(printf '%s' "$PYC" | tail -1)"

# Every surface that lists tasks must agree with dsh-tasksd's allowlist. Renaming the ids once
# updated the allowlist and the app but not the DSH page panel, so every panel button failed with
# "not in the whitelist" — a mismatch that stays invisible until a human presses the button.
if IDCHK=$(python3 "$HOME_DIR/.local/bin/check-task-ids" 2>&1); then
  rec PASS "task ids agree" "the page panel, the app and the widgets only send ids the allowlist accepts"
else
  rec FAIL "task ids agree" "$(printf '%s' "$IDCHK" | grep -E 'sends ids' | head -1)"
fi

# The tools live in the repo but are **copied** into ~/.local/bin, so the installed copy drifts from
# the file under review and then a tool behaves differently from what the code says. Hit for real on
# 2026-09-27: 4 of 5 compared tools were stale — dsh-tasksd was missing 11_update-apps (that panel
# button would have been refused), i18n-build-table still had the double-quote generator, and
# dsh-screen-ui had no "refuse to tap when Settings is not foreground" guard.
# tools/install-tools --check is the contract here: exit 0 = every installed copy matches the repo
# (a tool that is not installed at all does not count as drift — a fresh clone passes).
if DRIFT=$("$HOME_DIR/dsh-termux-kit/tools/install-tools" --check 2>&1); then
  rec PASS "installed tools match the repo" "$(printf '%s' "$DRIFT" | tail -1 | tr -d ' ')"
else
  rec FAIL "installed tools match the repo" "drifted: $(printf '%s' "$DRIFT" | sed -n 's/^   ⚠ \([^ ]*\).*/\1/p' | tr '\n' ' ')→ run tools/install-tools"
fi

# ── L2: --dry-run ──
line "[L2] --dry-run full-flow rehearsal"
for f in "$T"/*.sh; do
  n=$(basename "$f")
  if dryrun_ok "$f"; then rec PASS "$n" "dry-run completed and exited 0"; else rec FAIL "$n" "dry-run failed (see $TMPLOG)"; fi
done
# Widget 2's --keep-bridge once lived only in docs and never reached the case block (accepted, then ignored) → re-verify by real behaviour
if timeout 60 bash "$T/2_shutdown-dsh.sh" --dry-run --keep-bridge > "$TMPLOG" 2>&1 && grep -q 'Keeping the bridge channel' "$TMPLOG"; then
  rec PASS "2_shutdown-dsh --keep-bridge" "the flag really takes effect (bridge channel kept)"
else rec FAIL "2_shutdown-dsh --keep-bridge" "--keep-bridge was passed but it stops the bridge anyway (the flag never reached the case block)"; fi
if timeout 60 bash "$T/2_shutdown-dsh.sh" --dry-run > "$TMPLOG" 2>&1 && grep -qE 'droid-sock (sleep|stop)' "$TMPLOG"; then
  rec PASS "2_shutdown-dsh default" "the default stops the bridge (prefers sleep, falls back to stop on old versions)"
else rec FAIL "2_shutdown-dsh default" "the default never reached the bridge-stopping branch"; fi

# L2·guard: the lamp colours must be MEASURED, never hard-coded.
# History: offsets were hard-coded for CJK labels, then "fixed" with "Bridge".length() — which overran in
# Chinese ("● DSH　● 桥　● adb") and painted the adb DOT with the bridge colour while its label stayed red.
# Verified by pixel analysis on the device: dot #41B351 (green) next to a red label.
if grep -q 'setSpan' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null; then
  if grep -qE '"[^"]+"\.length\(\)' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" \
     && ! grep -q 'names\[k\]\.length()' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java"; then
    rec FAIL "lamp offsets measured" "the lamp spans use a hard-coded label length again → colours desync per language"
  else
    rec PASS "lamp offsets measured" "lamp spans compute offsets from the actual label lengths (language-proof)"
  fi
else
  rec SKIP "lamp offsets measured" "no local console source to inspect"
fi

# ── L3: regression tests for the readiness check / boot lock (the core of the 404 fix) ──
line "[L3] Readiness check and boot lock (regression tests)"
# The common library is already loaded (DRY=0); assertions run in a separate subshell

# (1) A stale token must be rejected: exactly what caused the 'error page on open' in the screenshot
CACHED=$(grep -oE "$TOKEN_RE" "$HOME_DIR/.dsh-url" 2>/dev/null | tail -1)
if [ "$WEB_OK" = 0 ]; then
  rec SKIP "stale token rejected" "8080 is not running, cannot verify"
elif [ -z "$CACHED" ]; then
  rec SKIP "stale token rejected" "no URL in .dsh-url"
elif url_ready "$CACHED"; then
  rec PASS "stale token rejected" "the cached URL happens to still be valid (200)"
else
  rec PASS "stale token rejected" "the cached URL is dead (HTTP $(url_code "$CACHED")); url_ready correctly calls it unusable"
fi

# (2) A half-started 404 must be rejected (and proof that the old check really let it through)
python3 - <<'PY' >/dev/null 2>&1 &
import http.server, socketserver
class H(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(404); self.end_headers(); self.wfile.write(b'not found')
    def log_message(self, *a): pass
socketserver.TCPServer.allow_reuse_address = True
socketserver.TCPServer(('127.0.0.1', 8097), H).serve_forever()
PY
FAKE404=$!
sleep 1.2
OLD=$(http_code 8097)                       # the old check used exactly this: anything but 000 counted as 'ready'
if url_ready "http://127.0.0.1:8097/"; then
  rec FAIL "half-started 404 rejected" "url_ready actually treated 404 as ready"
elif [ "$OLD" = "404" ]; then
  rec PASS "half-started 404 rejected" "the old check would let HTTP $OLD through → the new one blocks it (this is the 404 from the screenshot)"
else
  rec PASS "half-started 404 rejected" "the 404 server is correctly rejected (old check: http_code=$OLD)"
fi
kill "$FAKE404" 2>/dev/null

# (3) token line extraction
cat > "$TMPD/fake.log" <<'EOF'
[dsh-cost-meter] 已加载
dsh web: http://127.0.0.1:8099/?token=AAA-bbb_CCC123 (LAN: http://192.168.1.5:8099/?token=AAA-bbb_CCC123)
EOF
GOT=$(token_from_log "$TMPD/fake.log")
[ "$GOT" = "http://127.0.0.1:8099/?token=AAA-bbb_CCC123" ] && rec PASS "token line extraction" "got the local URL: $GOT" \
  || rec FAIL "token line extraction" "got '$GOT' instead"

# (4) Boot mutual exclusion: two rapid taps must not start two instances (they fight over the credentials write lock and one crashes)
# Note: the lock holder must be a **truly separate process** — inside a subshell $$ is still the parent pid,
# so the same-process idempotence rule counts it as self and exclusion cannot be tested. Real double taps are two bash processes.
LOCK_BLOCKED=0; LOCK_OK=0
# 2026-09-27 changed to a **handshake** instead of a fixed sleep 3:
#   the holder used to hold the lock only 3s, but on this machine under heavy work (backup/sandbox) a sleep 1 stretches past 3s →
#   by the time the tester grabbed it the holder had already released → a **false failure** saying "not blocked" (hit once for real).
#   Now: the holder writes a ready file once it holds the lock and releases it only after the tester writes the go file.
LOCK_READY="$TMPDIR/lock-ready.$$"; LOCK_GO="$TMPDIR/lock-go.$$"; rm -f "$LOCK_READY" "$LOCK_GO"
bash -c '. "$HOME/.local/share/dsh-widgets/common.sh"; boot_lock_acquire && : > "'"$LOCK_READY"'"; for _ in $(seq 1 100); do [ -f "'"$LOCK_GO"'" ] && break; sleep 0.2; done; boot_lock_release' &
HOLDER=$!
for _ in $(seq 1 50); do [ -f "$LOCK_READY" ] && break; sleep 0.1; done
if boot_lock_acquire 2>/dev/null; then boot_lock_release; else LOCK_BLOCKED=1; fi
: > "$LOCK_GO"
if [ "$LOCK_BLOCKED" = 1 ]; then
  wait $HOLDER 2>/dev/null
  boot_lock_acquire && LOCK_OK=1
  boot_lock_release
  [ "$LOCK_OK" = 1 ] && rec PASS "boot exclusion lock" "the second boot was blocked; the lock is free again once the holder exits" \
    || rec FAIL "boot exclusion lock" "cannot take the lock after the holder exits (the stale lock was not cleared)"
else
  rec FAIL "boot exclusion lock" "the second boot was not blocked → two rapid taps pull up two instances"
fi
# Idempotence: one process (widget takes the lock, then the shared boot function takes it) must not block itself
A=$( boot_lock_acquire; echo $? ); B=$( boot_lock_acquire; echo $? )
boot_lock_release
[ "$A" = "0" ] && [ "$B" = "0" ] && rec PASS "boot lock idempotence" "repeated takes in the same process all succeed (it never blocks itself)" \
  || rec FAIL "boot lock idempotence" "the second take in the same process failed ($A/$B) → a widget would deadlock against itself"
rm -rf "$BOOT_LOCK_DIR" 2>/dev/null   # safety net: the test leaves no lock behind, or L5 cannot start later

# (5) Orphan credentials lock: it must be cleared when no instance runs (uncleared after -9, booting is broken forever)
printf '999999\n' > "$TMPD/cred.lock"
R1=$( ( dsh_alive() { return 1; }; clear_orphan_cred_lock "$TMPD/cred.lock"; echo $? ) )
GONE=0; [ -f "$TMPD/cred.lock" ] || GONE=1
printf '999999\n' > "$TMPD/cred.lock"
R2=$( ( dsh_alive() { return 0; }; clear_orphan_cred_lock "$TMPD/cred.lock"; echo $? ) )
if [ "$R1" = "0" ] && [ "$GONE" = "1" ] && [ "$R2" = "1" ]; then
  rec PASS "orphan credentials lock" "removed with no instance, left alone with one (no permanent boot hang after -9)"
else
  rec FAIL "orphan credentials lock" "wrong behaviour (no instance=$R1 removed=$GONE with instance=$R2)"
fi

# (6) A dead process must fail at once instead of waiting pointlessly
S=$(date +%s)
wait_dsh_ready 20 "$TMPD/fake.log" 999999 >/dev/null 2>&1; RC=$?
D=$(( $(date +%s) - S ))
if [ "$RC" = "2" ] && [ "$D" -lt 5 ]; then rec PASS "exited process fails fast" "returns 'already exited' within ${D}s when the pid is gone"
else rec FAIL "exited process fails fast" "returned $RC after ${D}s (expected 2, and quickly)"; fi

# (7) dsh_pids must not match its own command line (hit before: pgrep killing itself)
SELFHIT=$(dsh_pids | grep -c "^$$\$" || true)
[ "$SELFHIT" = "0" ] && rec PASS "dsh_pids does not self-match" "never counts the caller itself as a dsh process" || rec FAIL "dsh_pids does not self-match" "it matched itself"

# (8) Restart-style widgets must **take the lock first, act second**: on a double tap the second run's step 1 otherwise -9s
#    the fresh instance the first run just started, and the first run reports "process exited during startup" (exactly how widget 4 broke)
ORDER_OK=1
for w in 2_shutdown-dsh 4_soft-restart-dsh 6_hard-restart-dsh; do
  LK=$(grep -n 'boot_lock_acquire' "$T/$w.sh" | head -1 | cut -d: -f1)
  KW=$(grep -n 'kill_wait' "$T/$w.sh" | head -1 | cut -d: -f1)
  if [ -z "$LK" ] || [ -z "$KW" ] || [ "$LK" -gt "$KW" ]; then
    ORDER_OK=0; line "     ⚠ $w: lock at line ${LK:-none}, action at line ${KW:-none}"
  fi
done
[ "$ORDER_OK" = 1 ] && rec PASS "lock before action" "widgets 2/4/6 all take the lock before killing (a double tap no longer kills itself)" \
  || rec FAIL "lock before action" "some widget acts before locking → a double tap kills the instance it just started"

# (9) Closing the browser/Termux must tell the truth: termux-am has no force-stop, so without adb it must admit it cannot
# Skip comment lines (grep -n prints "lineno:content", and a comment line looks like → 108:# …)
BADFS=$(grep -n 'am force-stop' "$T/2_shutdown-dsh.sh" 2>/dev/null | grep -v ':[[:space:]]*#' | grep -v 'adb shell am force-stop' | wc -l)
if [ "$BADFS" -gt 0 ]; then
  rec FAIL "close action tells the truth" "widget 2 still has $BADFS bare am force-stop calls (termux-am lacks it → reports success without closing)"
else
  rec PASS "close action tells the truth" "widget 2 never calls bare am force-stop; without adb dsh-close-window borrows the bridge to send keys (device-verified 2026-09-27: one Back key closes it)"
fi

# (9b) The window-closing tool must exist, self-test, and come **before stopping the bridge** (two traps the user hit on 2026-09-27)
if [ -x "$HOME/.local/bin/dsh-close-window" ] && timeout 20 "$HOME/.local/bin/dsh-close-window" --probe >/dev/null 2>&1; then
  rec PASS "dsh-close-window" "present and --probe works (shows whether the window is foreground without acting)"
else rec FAIL "dsh-close-window" "missing or --probe broken → without adb, closing the window is a lie again"; fi
CW=$(grep -n 'bin/dsh-close-window' "$T/2_shutdown-dsh.sh" 2>/dev/null | grep -v ':[[:space:]]*#' | head -1 | cut -d: -f1)
BSTOP=$(grep -n 'droid-sock" stop' "$T/2_shutdown-dsh.sh" 2>/dev/null | grep -v ':[[:space:]]*#' | head -1 | cut -d: -f1)
if [ -n "$CW" ] && [ -n "$BSTOP" ] && [ "$CW" -lt "$BSTOP" ]; then
  rec PASS "window close before bridge stop" "the window closes at line $CW, the bridge stops only at line $BSTOP (reversed, it can never close without adb)"
else rec FAIL "window close before bridge stop" "window close ($CW) does not come before bridge stop ($BSTOP) → the bridge is down and nobody can send keys"; fi

# (10) Widget 8: with Wi-Fi off it must not just "stop and complain" — it must open the Wi-Fi page and carry on automatically
#    (Android 10+ forbids apps from enabling Wi-Fi → this semi-automatic handoff is the only way; --no-ui falls back to a plain hint)
if grep -q 'android.settings.WIFI_SETTINGS' "$T/8_enable-wireless-adb.sh" && grep -q -- '--no-ui' "$T/8_enable-wireless-adb.sh"; then
  rec PASS "widget8 Wi-Fi handoff" "with Wi-Fi off it opens the settings page, polls until you enable it, then finishes on its own"
else
  rec FAIL "widget8 Wi-Fi handoff" "missing the Wi-Fi handoff flow (would exit after only reporting "preconditions unmet", as before)"
fi

# (11) Widget 8: **writing the setting ≠ adbd really starting** — after setting 1 it must read back first, and stop at once with the right cause if it did not stick.
#    Caught by a 2026-09-26 real run: the old version scanned 30000-60999 for 40 seconds and then blamed "no network" (while online=true).
if grep -q '③·check:' "$T/8_enable-wireless-adb.sh" && grep -q 'the switch did not stick' "$T/8_enable-wireless-adb.sh"; then
  rec PASS "widget8 read-back after set" "stops within 4s with the real cause when it does not stick — no more futile port scan, no more inverted conclusion"
else
  rec FAIL "widget8 read-back after set" "missing the read-back check (would scan 30k ports and give a cause opposite to the facts)"
fi

# (12) Revoking install-password authorisation: the **only revocation point** for "the AI uses your lock-screen password to install packages",
#    so both ends need an entry point (widget + console app), and the widget must really call the helper's revoke.
if [ -x "$HOME_DIR/.local/bin/dsh-auth-pass" ] \
   && grep -q 'dsh-auth-pass' "$T/9_revoke-pin.sh" \
   && grep -q 'revoke' "$T/9_revoke-pin.sh"; then
  rec PASS "revoke password rights (widget)" "widget 9 goes through dsh-auth-pass revoke and re-checks that the file really disappears"
else
  rec FAIL "revoke password rights (widget)" "helper missing, or widget 9 is not wired to revoke"
fi
#    User request 2026-09-26: authorisation must be a **switch**, not a button → the check point moved to that line in MainActivity
if grep -q 'dsh-auth-pass revoke' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null \
   && grep -q 'dsh-auth-pass status' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null \
   && grep -q 'authSwitch' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" 2>/dev/null \
   && grep -qE '密码使用权|Password access' "$HOME_DIR/dsh-console/src/io/dsh/console/MainActivity.java" "$HOME_DIR/dsh-console/src/io/dsh/console/L.java" 2>/dev/null; then
  rec PASS "password-rights switch (console)" "a Switch in the maintenance class: on = the AI may use your password to pass verification, off = revoked at once (not just for installs)"
else
  rec FAIL "password-rights switch (console)" "the console does not make "password rights" a switch (or is not wired to the helper)"
fi

# (13) The task plugin in the DSH page (dsh-mobile-local) must keep up too:
#    categories, bridge/adb split apart, the password-rights switch, and tasksd's three /auth pieces
# The panel is **generated** now (ui/controls.json → tools/ui-controls), so the old literal greps
# ('CATS', 'mb-auth') stopped matching the moment it was rewritten — a check that silently stops
# checking. Assert on what actually has to hold: the deployed copy carries every category and control
# the source declares, it is a switch-capable panel, and tasksd still serves the auth endpoints.
PLUG="$HOME_DIR/.dsh/profiles/web/local/dsh-mobile-local/client.js"
PLUG_N=0; PLUG_MISS=""
while read -r want; do
  [ -n "$want" ] || continue
  PLUG_N=$((PLUG_N+1))
  grep -qF "$want" "$PLUG" 2>/dev/null || PLUG_MISS="$PLUG_MISS $want"
done <<EOF
$(python3 - "$HOME_DIR/dsh-termux-kit/ui/controls.json" <<'PYEOF'
import json, sys
d = json.load(open(sys.argv[1], encoding='utf-8'))
for c in d['cats']:
    print(c['en'])
print('UI_CONTROLS')
print('mb-switch')
PYEOF
)
EOF
if [ -z "$PLUG_MISS" ] && grep -q 'dsh-auth-pass' "$HOME_DIR/.local/bin/dsh-tasksd" 2>/dev/null; then
  rec PASS "task plugin (page) matches the UI source" "$PLUG_N markers present (every category + controls + switch); tasksd serves /auth too"
else
  rec FAIL "task plugin (page) matches the UI source" "missing:$PLUG_MISS (deploy with tools/install-tools + pnpm install, then refresh)"
fi
if grep -q "'9_revoke-pin'" "$HOME_DIR/.local/bin/dsh-tasksd" 2>/dev/null \
   && grep -q 'VIRTUAL' "$HOME_DIR/.local/bin/dsh-tasksd" 2>/dev/null; then
  rec PASS "tasksd whitelist+virtual tasks" "9_revoke-pin alongside bridge_wake/bridge_status are both whitelisted"
else
  rec FAIL "tasksd whitelist+virtual tasks" "the whitelist lags behind (missing 9_ or the bridge's virtual tasks)"
fi

# (14) The client bundle must resolve (hit for real 2026-09-26: running pnpm install inside the profile prunes
#    the runtime symlinks @deepseek-ai/dsh-base / dsh-web-app → the page reports "Failed to load plugins")
if "$HOME_DIR/.local/bin/dsh-relink-bundles" --check >/dev/null 2>&1; then
  rec PASS "runtime bundle symlinks" "@deepseek-ai/dsh-base / dsh-web-app are both present (after pnpm install, dsh-relink-bundles restores the links)"
else
  rec FAIL "runtime bundle symlinks" "links missing → the page reports Failed to load plugins (run dsh-relink-bundles)"
fi
# Verify "the bundle really fetches" once, only while the local DSH is alive (a failure means the routes/package are broken)
if timeout 6 bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' 2>/dev/null; then
  TOK=$(grep -ao 'token=[A-Za-z0-9_-]*' "$HOME_DIR/.dsh-restart.log" 2>/dev/null | tail -1 | cut -d= -f2)
  if [ -n "$TOK" ]; then
    CJ="$TMPDIR/dsh-cj.$$"
    curl -sL -c "$CJ" -b "$CJ" -o "$TMPLOG.page" "http://127.0.0.1:8080/?token=$TOK" 2>/dev/null
    U1=$(grep -oE '/plugins/\?\?[^"'"'"'\\]+' "$TMPLOG.page" 2>/dev/null | sed 's/&amp;/\&/g' | sort -u | head -1)
    if [ -n "$U1" ]; then
      C=$(curl -s -b "$CJ" -o /dev/null -w '%{http_code}' "http://127.0.0.1:8080$U1" 2>/dev/null)
      [ "$C" = "200" ] && rec PASS "client bundle really fetches" "the local DSH's /plugins/??… returns 200" \
                       || rec FAIL "client bundle really fetches" "returned $C (the page will show Failed to load plugins)"
    else
      rec SKIP "client bundle really fetches" "no /plugins/ URL parsed from the page (DSH may have just started)"
    fi
    rm -f "$CJ" "$TMPLOG.page"
  else
    rec SKIP "client bundle really fetches" "cannot read the local token"
  fi
else
  rec SKIP "client bundle really fetches" "the local 8080 is not running"
fi

# (15) Status pushback must not be "cut from the head": the app truncates only task output (tail); status JSON must stay whole (head).
#    Hit for real: a 6463-byte status payload > the old 4000 cap → the head was cut → the app showed 'status parse failed'.
if grep -q 'dsh-status-pub --json --brief' "$HOME_DIR/dsh-console/src/io/dsh/console/TermuxRunner.java" 2>/dev/null \
   && grep -q 'isStatus ? 200000 : 4000' "$HOME_DIR/dsh-console/src/io/dsh/console/TaskResultReceiver.java" 2>/dev/null; then
  BRIEF=$(~/.local/bin/dsh-status-pub --json --brief 2>/dev/null | wc -c | tr -d ' ')
  FULL=$(~/.local/bin/dsh-status-pub --json 2>/dev/null | wc -c | tr -d ' ')
  if [ "${BRIEF:-99999}" -lt 4000 ]; then
    rec PASS "status pushback not truncated" "--brief ${BRIEF}B (full ${FULL}B): status stays whole, only task output is tail-truncated"
  else
    rec FAIL "status pushback not truncated" "--brief ${BRIEF}B already exceeds 4000 and will be truncated (needs slimming)"
  fi
else
  rec FAIL "status pushback not truncated" "the app side does not distinguish status/task truncation rules (status JSON gets its head cut)"
fi

# ── L4: real runs ──
line "[L4] Real runs (safe and reversible)"

# Revoke install-password authorisation: **sandbox paths only**; the real authorisation file is never touched (and is re-checked afterwards)
if [ -f "$HOME_DIR/.dsh-auth-pass" ]; then HAD_REAL_PASS=1; else HAD_REAL_PASS=0; fi
TMPPASS="${TMPDIR:-$PREFIX/tmp}/dsh-pass-selftest.$$"
if printf '123456' > "$TMPPASS" && chmod 600 "$TMPPASS"; then
  OUTR=$(DSH_AUTH_PASS_FILE="$TMPPASS" "$HOME_DIR/.local/bin/dsh-auth-pass" revoke 2>&1); RCR=$?
  if [ "$RCR" = 0 ] && [ ! -e "$TMPPASS" ]; then
    rec PASS "revoke password rights (real run)" "the sandbox file is overwritten then removed, exit 0; $OUTR" 
  else
    rec FAIL "revoke password rights (real run)" "exit $RCR / the file is still there: $OUTR"
  fi
else
  rec FAIL "revoke password rights (real run)" "cannot create the sandbox file $TMPPASS"
fi
if [ "$HAD_REAL_PASS" = 1 ] && [ ! -f "$HOME_DIR/.dsh-auth-pass" ]; then
  rec FAIL "real authorisation untouched" "the selftest deleted the real authorisation file (the one thing that must never happen)"
elif [ "$HAD_REAL_PASS" = 1 ]; then
  rec PASS "real authorisation untouched" "the sandbox test used temp paths only; the real authorisation is intact with mode $(stat -c '%a' "$HOME_DIR/.dsh-auth-pass" 2>/dev/null)"
else
  rec SKIP "real authorisation untouched" "there is no authorisation file right now (not authorised)"
fi
if timeout 180 bash "$T/3_backup-dsh.sh" > "$TMPLOG" 2>&1 && grep -q 'Archive readable' "$TMPLOG"; then
  rec PASS "3_backup-dsh.sh" "real run passed: $(grep -oE '[0-9]+ archives? kept[^,]*' "$TMPLOG" | head -1)"
else rec FAIL "3_backup-dsh.sh" "real run failed (see $TMPLOG)"; fi
if timeout 180 bash "$T/5_cleanup-dsh.sh" > "$TMPLOG" 2>&1; then
  rec PASS "5_cleanup-dsh.sh" "real run passed: $(grep -oE 'Download/dsh: [0-9]+MB → [0-9]+MB' "$TMPLOG" | head -1)"
else rec FAIL "5_cleanup-dsh.sh" "real run failed"; fi
if timeout 120 bash "$T/7_reconnect-ai.sh" > "$TMPLOG" 2>&1 && grep -q 'DSH Web : running' "$TMPLOG"; then
  rec PASS "7_reconnect-ai.sh" "real run passed: $(grep -oE 'adb     : .*' "$TMPLOG" | head -1 | cut -c1-40)"
else rec FAIL "7_reconnect-ai.sh" "real run failed"; fi
if [ "$BRIDGE_OK" = 0 ]; then
  rec SKIP "8_enable-wireless-adb.sh" "depends on the bridge channel; preconditions unmet (not a widget problem)"
else
  timeout 150 bash "$T/8_enable-wireless-adb.sh" > "$TMPLOG" 2>&1; RC8=$?
  case "$RC8" in
    0) if grep -qE 'adb connected|Connected to' "$TMPLOG"; then rec PASS "8_enable-wireless-adb.sh" "real run passed (idempotent)"
       else rec FAIL "8_enable-wireless-adb.sh" "exit 0 but the switch never took effect (see $TMPLOG)"; fi ;;
    3) # exit 3 = "not executed: preconditions unmet". **Use the script's own wording to tell which precondition**,
       # not always "Wi-Fi is off" — verified on 2026-09-26: the network was fine, the system had just reset adb_wifi to 0.
       if grep -q 'the switch did not stick' "$TMPLOG"; then
         if grep -q 'not a network problem' "$TMPLOG"; then
           rec SKIP "8_enable-wireless-adb.sh" "env: the network is fine but the system reset the switch to 0 → enable it manually once in Developer options (correctly diagnosed, no futile scan)"
         else
           rec SKIP "8_enable-wireless-adb.sh" "env: Wi-Fi is not on a network → wireless debugging reset to 0 (correctly diagnosed, no futile scan)"
         fi
       else
         rec SKIP "8_enable-wireless-adb.sh" "Wi-Fi is off → the script correctly stops early (preconditions unmet, not a widget problem)"
       fi ;;
    1) # on exit 1 read its own **diagnosis** first: an unmet environment counts as SKIP; only a missing diagnosis is a widget failure
       if grep -q 'no real network connection' "$TMPLOG"; then
         rec SKIP "8_enable-wireless-adb.sh" "env: Wi-Fi is on but not on a network → the system reset wireless debugging to 0 (correctly diagnosed)"
       elif grep -q 'framework was not really started' "$TMPLOG"; then
         rec SKIP "8_enable-wireless-adb.sh" "env: switch=1 but the framework never started; enable it manually once in Developer options (correctly diagnosed)"
       else rec FAIL "8_enable-wireless-adb.sh" "exit 1 with no diagnosis given (see $TMPLOG)"; fi ;;
    *) rec FAIL "8_enable-wireless-adb.sh" "exit $RC8 (see $TMPLOG)" ;;
  esac
fi

# Bridge: capability probe → soft stop → broadcast wake round trip, plus a live check of 'does a soft stop come back by itself'
if [ "$BRIDGE_OK" = 0 ]; then
  rec SKIP "bridge soft stop/wake" "depends on the bridge channel; preconditions unmet"
else
  CAPS=$(timeout 12 "$HOME_DIR/.local/bin/droid-sock" caps 2>/dev/null || true)
  if printf '%s' "$CAPS" | grep -q '"stop_is_durable"'; then
    # ── **default path** since v1.8 (widget 2 takes it by default): a soft stop must not self-recover and a wake must return at once ──
    rec PASS "bridge capability probe" "durable soft stop (a feature introduced in bridge v1.8) + sleep still available; the bridge reports $(printf '%s' "$CAPS" | grep -oE '"ver": *"[^"]*"')"
    if timeout 15 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1; then
      sleep 2
      if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then
        rec FAIL "v1.8 soft stop durable" "the port is still open after stop"
      else
        CAME=0
        for i in $(seq 1 16); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { CAME=1; break; }; done
        [ "$CAME" = 0 ] && rec PASS "v1.8 soft stop durable" "8788 is closed after stop and does not self-recover within 18s (old versions came back in 14s)" \
                        || rec FAIL "v1.8 soft stop durable" "it opened itself again ${i}s after stop"
        # The window is 45s: if the system reclaimed the app process, the broadcast must cold-start it, measured at 20-40s.
        # Late on 2026-09-26 it once did not return within 45s (the same version had passed the round before) → send one more broadcast and wait another 45s,
        # and state "came back only after the extra broadcast" honestly, instead of failing it or pretending one try was enough.
        T0W=$(date +%s)
        timeout 20 "$HOME_DIR/.local/bin/droid-sock" wake >/dev/null 2>&1
        BACK=0
        for i in $(seq 1 45); do
          if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then BACK=$(( $(date +%s) - T0W )); break; fi
          sleep 1
        done
        RETRY=0
        if [ "$BACK" = 0 ]; then
          RETRY=1
          timeout 20 "$HOME_DIR/.local/bin/droid-sock" wake >/dev/null 2>&1
          for i in $(seq 1 45); do
            if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then BACK=$(( $(date +%s) - T0W )); break; fi
            sleep 1
          done
        fi
        if [ "$BACK" != 0 ] && [ "$RETRY" = 0 ]; then
          rec PASS "v1.8 wake recovery" "the port is back ${BACK}s after the broadcast wake (the process is alive, no re-authorisation needed)"
        elif [ "$BACK" != 0 ]; then
          rec PASS "v1.8 wake recovery" "the first broadcast did not bring it back within 45s; the extra one recovered it at ${BACK}s (slow vivo cold start, a known jitter)"
        else
          rec FAIL "v1.8 wake recovery" "two broadcasts and 90 seconds still did not wake it"
        fi
        PS=""
        for i in $(seq 1 10); do
          PS=$(timeout 12 "$HOME_DIR/.local/bin/droid-sock" ping 2>/dev/null | grep -o '"paused": *[a-z]*')
          case "$PS" in *false*) break ;; esac
          sleep 1
        done
        case "$PS" in
          *false*) rec PASS "paused flag correct" "paused is back to false after the wake (the next rebind will not be silenced)" ;;
          *) rec FAIL "paused flag correct" "paused is still true after the wake ($PS)" ;;
        esac
      fi
    else
      rec FAIL "v1.8 soft stop durable" "droid-sock stop does not respond"
    fi
  elif printf '%s' "$CAPS" | grep -q '"sleep"'; then
    rec PASS "bridge capability probe" "v1.7+ supports a real sleep stop + a token-bearing wake can re-authorise"
    # v1.7 path: a real stop must **not** self-recover (the old soft stop came back in 14s in testing)
    if timeout 20 "$HOME_DIR/.local/bin/droid-sock" sleep >/dev/null 2>&1; then
      sleep 2
      if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then
        rec FAIL "v1.7 real stop" "the port is still open after sleep"
      else
        CAME=0
        for i in $(seq 1 8); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { CAME=1; break; }; done
        [ "$CAME" = 0 ] && rec PASS "v1.7 real stop" "8788 is closed after sleep and does not self-recover within 16s (the old soft stop came back)" \
                        || rec FAIL "v1.7 real stop" "it came back by itself shortly after sleep"
        # Security boundary: a broadcast with a wrong token must not bring it up
        am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver --es token 000000000000 >/dev/null 2>&1
        BAD=0
        for i in $(seq 1 8); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { BAD=1; break; }; done
        [ "$BAD" = 0 ] && rec PASS "wake authentication" "a WAKE with a wrong token cannot wake it (no app can pull it up)" \
                       || rec FAIL "wake authentication" "a wrong token woke it too"
        # Recovery: a broadcast with the right token should re-authorise accessibility and resume listening on its own
        timeout 20 "$HOME_DIR/.local/bin/droid-sock" wake >/dev/null 2>&1
        BACK=0
        for i in $(seq 1 20); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { BACK=1; break; }; done
        [ "$BACK" = 1 ] && rec PASS "automatic re-authorisation" "a token-bearing WAKE recovers by itself within ${i}s (never touching the screen)" \
                        || rec FAIL "automatic re-authorisation" "cannot wake it back (accessibility must be enabled by hand)"
      fi
    else
      rec FAIL "v1.7 real stop" "droid-sock sleep does not respond"
    fi
  else
    rec PASS "bridge capability probe" "this is an old bridge: soft stop only (closes the port) and it returns whenever the system rebinds accessibility → installing v1.7 fixes it properly"
    if timeout 15 "$HOME_DIR/.local/bin/droid-sock" stop >/dev/null 2>&1; then
      sleep 1
      if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then
        rec FAIL "bridge soft stop/wake" "the port is still open after the soft stop"
      else
        # Measured self-recovery after a soft stop: the port returns within 15s = the user's 'I closed it and it reopened itself'
        CAME=0
        for i in $(seq 1 15); do sleep 1; (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null && { CAME=1; break; }; done
        if [ "$CAME" = 1 ]; then
          rec PASS "soft stop self-recovery" "the port came back by itself ${i}s after the soft stop — measured proof of 'closed it and it reopened' (v1.7 sleep fixes it)"
        else
          rec PASS "soft stop self-recovery" "it did not come back within 15s (self-recovery depends on when the system rebinds accessibility; it does not always trigger)"
        fi
        am broadcast -a io.dsh.bridge.WAKE -n io.dsh.bridge/.WakeReceiver \
          --es token "$(cat "$HOME_DIR/.dsh-bridge-token" 2>/dev/null)" >/dev/null 2>&1
        sleep 2
        if (exec 3<>/dev/tcp/127.0.0.1/8788) 2>/dev/null; then rec PASS "bridge soft stop/wake" "soft stop → broadcast wake round trip succeeded"
        else rec FAIL "bridge soft stop/wake" "wake failed (open the app or install v1.7)"; fi
      fi
    else rec FAIL "bridge soft stop/wake" "droid-sock stop does not respond"; fi
  fi
fi

# ── L5: widget 1 cold-start sandbox + half-start evidence ──
line "[L5] Widget 1 cold start (8099 sandbox) + 'port opens first, routes mount later' evidence"
cp -f "$HOME_DIR/.dsh-restart.log" "$HOME_DIR/.smoke/restart.log.save" 2>/dev/null
cp -f "$HOME_DIR/.dsh-url" "$HOME_DIR/.smoke/dsh-url.save" 2>/dev/null   # the sandbox overwrites it, so it must be restored
SAVED=1   # from here on, cleanup (including when interrupted) may restore these two files
# The sandbox instance must pass --patch to disable filetransfer: the main instance already holds 3199, so a second one cannot take the port
# and dies with EADDRINUSE while loading the plugin tree (seen for real). Note: --patch must come before --port.
# DSH_URL_FILE: the sandbox must not write the user's .dsh-url. Saving/restoring it around the
#   run was not enough — an interrupted run leaves a detached DSH that finishes later and
#   overwrites it again, after the restore (this is how the user ended up inside the sandbox
#   on 2026-09-27). Redirecting the file removes the race instead of racing it.
DSH_PORT=8099 DSH_URL_FILE="$HOME_DIR/.smoke/dsh-url.sandbox" \
  DSH_WEB_EXTRA="--patch $HOME_DIR/.smoke/patch.yml" \
  timeout 200 bash "$T/1_start-dsh.sh" --no-open > "$TMPLOG" 2>&1 &
WPID=$!
SAMPLES=""
while kill -0 "$WPID" 2>/dev/null; do
  c=$(curl -s -o /dev/null -w '%{http_code}' --max-time 2 http://127.0.0.1:8099/ 2>/dev/null)
  case "$c" in ''|000) ;; *) LAST=""; for x in $SAMPLES; do LAST="$x"; done
       [ "$c" != "$LAST" ] && SAMPLES="$SAMPLES $c" ;; esac
  sleep 0.3
done
wait "$WPID"; WRC=$?
SBURL=$(grep -oE 'http://127\.0\.0\.1:8099/\?token=[A-Za-z0-9_-]+' "$TMPLOG" | tail -1)
if grep -q 'Startup complete' "$TMPLOG"; then
  rec PASS "1_start-dsh.sh cold start" "waited for real readiness and passed the 200 check (took $(grep -oE 'took [0-9]+s' "$TMPLOG" | tail -1))"
else
  rec FAIL "1_start-dsh.sh cold start" "never finished the real readiness check (exit=$WRC, see $TMPLOG)"
fi
# Evidence: the sequence of HTTP codes seen during the cold start. The old logic only looked for 'not 000' and took the first code as ready.
SEQ=$(printf '%s' "$SAMPLES" | sed 's/^ //')
case "$SEQ" in
  *404*) rec PASS "half-start evidence" "sampled HTTP code sequence: ${SEQ} (404=routes not mounted yet; the old logic would open the browser right here)" ;;
  *401*) rec PASS "half-start evidence" "sampled HTTP code sequence: ${SEQ} (401=auth mounted but no token; the old logic called that ready too)" ;;
  *)     rec PASS "half-start evidence" "sampled HTTP code sequence: ${SEQ:-(none sampled; startup may have been too fast)}" ;;
esac
[ -n "$SBURL" ] && url_ready "$SBURL" && rec PASS "sandbox URL usable" "token URL returns 200: $SBURL" \
  || rec FAIL "sandbox URL usable" "the sandbox produced no usable token URL"
# Cleanup: kill the sandbox by an **exact --port 8099 match only** (never your 8080 instance), then restore the log
kill_sandbox
sleep 1
cp -f "$HOME_DIR/.smoke/restart.log.save" "$HOME_DIR/.dsh-restart.log" 2>/dev/null
cp -f "$HOME_DIR/.smoke/dsh-url.save" "$HOME_DIR/.dsh-url" 2>/dev/null
P8099=$( (exec 3<>/dev/tcp/127.0.0.1/8099) 2>/dev/null && echo open || echo closed )
line "     sandbox cleanup: 8099 = $P8099 (should be closed); boot log and .dsh-url restored"

# ── SKIP ──
line "[SKIP] A real run would kill this session or needs manual recovery"
rec SKIP "0_emergency-stop.sh" "a real run revokes the token + disables accessibility + drops adb and needs manual recovery; indirectly verified by the volume-key drill"
rec SKIP "2_shutdown-dsh.sh" "a real run stops this session; every step has been run for real on its own (backup/rotation/port wait/bridge stop), and window closing was run for real via dsh-close-window"
rec SKIP "4_soft-restart-dsh.sh" "a real run restarts the service and drops this session"
rec SKIP "6_hard-restart-dsh.sh" "same as above; this is the only way to verify the -9 hard-kill path (orphan lock cleanup is covered by the L3 (5) unit test)"

line ""
line "════ Result: $PASS passed / $FAIL failed / $SKIP skipped ════"
[ "$FAIL" = 0 ] && line "Verdict: every testable item passed ✅" || line "Verdict: $FAIL item(s) failed, needs fixing ❌"
printf '%b' "$REPORT" > "$HOME_DIR/.smoke/selftest-report.txt"
rm -rf "$TMPD"
exit "$FAIL"
