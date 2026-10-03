#!/data/data/com.termux/files/usr/bin/bash
# ci-selftest - run tests/selftest.sh against a throw-away phone-like HOME and record it.
#
# Why a wrapper: selftest.sh is written for the phone. This builds the smallest fake
# phone that lets the offline tiers (L1 syntax, L2 dry-run, L3 the readiness/boot-lock
# regression tests) do real work, and points HOME_DIR/HOME/TMPDIR at a temporary
# directory so the tiers that need adb, the bridge or the real ~/.dsh* files can only
# report FAIL/SKIP - never touch anything of yours.
#
# What it seeds (the same layout the phone has):
#   widgets/<N>_*.sh      →  $HOME/.shortcuts/tasks/
#   widgets/common.sh     →  $HOME/.local/share/dsh-widgets/   (plus i18n.sh and this suite)
#   tools/*               →  $HOME/.local/bin/
#
# A few checks cannot pass without the phone: adb, the DSH bridge service, an installed
# DSH page plugin, or the Termux interpreter path that the scripts' shebangs point at
# (executing a tool directly off-phone is "bad interpreter", not a code defect). Those
# are listed in OFF_PHONE below: they are reported, but they do not fail the run. Any
# OTHER failure does - that is the whole point of running this in CI.
#
# Usage:
#   bash tests/ci-selftest.sh                 # record the run; fail only on unexpected failures
#   bash tests/ci-selftest.sh --strict        # exit with the suite's own code (no failed check at all)
#   bash tests/ci-selftest.sh /path/out.txt   # also choose where the report goes
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STRICT=0
REPORT=""
for a in "$@"; do
  case "$a" in
    --strict) STRICT=1 ;;
    -*) printf 'usage: bash tests/ci-selftest.sh [--strict] [report.txt]\n' >&2; exit 2 ;;
    *) REPORT="$a" ;;
  esac
done

WORK="${DSH_CI_WORK:-${TMPDIR:-/tmp}}/dsh-ci-selftest.$$"
H="$WORK/home"
T="$H/.shortcuts/tasks"
LIBDIR="$H/.local/share/dsh-widgets"
BINDIR="$H/.local/bin"
LOG="$WORK/selftest.log"
[ -n "$REPORT" ] || REPORT="$WORK/ci-selftest-report.txt"

mkdir -p "$T" "$LIBDIR" "$BINDIR" "$H/.smoke" "$H/storage/shared/Download" "$WORK/tmp"
cp "$ROOT"/widgets/[0-9]*.sh "$T"/                 # only the numbered widgets live there
cp "$ROOT/widgets/common.sh" "$ROOT/widgets/i18n.sh" "$LIBDIR/"
cp "$ROOT/tests/selftest.sh" "$LIBDIR/"            # L1 checks the installed copy too
for f in "$ROOT"/tools/*; do
  [ -f "$f" ] && cp "$f" "$BINDIR/" && chmod +x "$BINDIR/$(basename "$f")"
done

# Checks that need the phone itself (or its Termux interpreter path). Everything else must pass.
OFF_PHONE='installed tools match the repo|dsh-close-window|task plugin \(page\) matches|runtime bundle symlinks|revoke password rights|1_start-dsh\.sh *cold start|sandbox URL usable|real run failed|3_backup-dsh\.sh *dry-run|8_enable-wireless-adb\.sh *dry-run|11_update-apps\.sh *dry-run'

TMPDIR="$WORK/tmp" \
DSH_HOME_DIR="$H" \
HOME="$H" \
DSH_WIDGET_LOG=0 \
DSH_LANG_FILE="$WORK/dsh-lang" \
DSH_KIT_REPO="$ROOT" \
DSH_CONSOLE_DIR="$ROOT/apps/console" \
DSH_BRIDGE_DIR="$ROOT/apps/bridge" \
PYTHONUTF8=1 PYTHONIOENCODING=utf-8 \
  bash tests/selftest.sh >"$LOG" 2>&1
RC=$?

SUMMARY="$(grep -E 'Result: [0-9]+ passed' "$LOG" | tail -1)"
FAILED="$(grep -E '^  ✘' "$LOG" || true)"
N_FAILED=$(printf '%s' "$FAILED" | grep -c '✘' || true)
N_OFF=$(printf '%s\n' "$FAILED" | grep -cE "$OFF_PHONE" || true)
UNEXPECTED="$(printf '%s\n' "$FAILED" | grep -vE "$OFF_PHONE" | grep '✘' || true)"
N_UNEXPECTED=$(printf '%s' "$UNEXPECTED" | grep -c '✘' || true)

{
  printf 'kit:     %s\nhome:    %s\nlog:     %s\n' "$ROOT" "$H" "$LOG"
  printf 'widgets: %s installed, tools: %s installed\n\n' "$(ls "$T" | wc -l)" "$(ls "$BINDIR" | wc -l)"
  printf '%s\n\n' "${SUMMARY:-（没有拿到 Result 汇总行 —— 套件自己中途断了）}"
  printf 'selftest.sh exit: %s\n' "$RC"
  printf 'failed: %s (needs the phone: %s · unexpected: %s)\n\n' "$N_FAILED" "$N_OFF" "$N_UNEXPECTED"
  printf -- '--- 需要真机的失败（记录，不算红）---\n%s\n\n' "$(printf '%s\n' "$FAILED" | grep -E "$OFF_PHONE" || true)"
  printf -- '--- 预期之外的失败（这些会让 CI 变红）---\n%s\n' "${UNEXPECTED:-（无）}"
} | tee "$REPORT"

if [ -f "$H/.smoke/selftest-report.txt" ]; then
  printf '\nreport:  %s\n' "$H/.smoke/selftest-report.txt"
fi

if [ -z "$SUMMARY" ]; then
  exit 1          # the suite did not reach its summary: that is a real break
fi
if [ "$STRICT" = 1 ]; then
  exit "$RC"
fi
[ "$N_UNEXPECTED" = 0 ] || exit 1
exit 0
