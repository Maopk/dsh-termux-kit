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
# The verdict is not a grep over the log. The suite writes ~/.smoke/selftest.json, where every
# assertion carries a category and an "off-phone allowed" flag taken from tests/lib/categories.tsv
# (a reviewed file in the repo); tests/report_check.py reads that report and decides:
#   · a logic or simulable failure is a regression — red, always;
#   · a device failure with no reviewed allow-row is unexpected — red;
#   · a device failure the table allows is recorded here, and still has to be checked on the phone.
# The categories are the contract; see docs/test-layers.md.
#
# Usage:
#   bash tests/ci-selftest.sh                 # record the run; fail when the policy check fails
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
JSON="$H/.smoke/selftest.json"
[ -n "$REPORT" ] || REPORT="$WORK/ci-selftest-report.txt"

mkdir -p "$T" "$LIBDIR" "$BINDIR" "$H/.smoke" "$H/storage/shared/Download" "$WORK/tmp"
cp "$ROOT"/widgets/[0-9]*.sh "$T"/                 # only the numbered widgets live there
cp "$ROOT/widgets/common.sh" "$ROOT/widgets/i18n.sh" "$LIBDIR/"
cp "$ROOT/tests/selftest.sh" "$LIBDIR/"            # L1 checks the installed copy too
for f in "$ROOT"/tools/*; do
  [ -f "$f" ] && cp "$f" "$BINDIR/" && chmod +x "$BINDIR/$(basename "$f")"
done

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

{
  printf 'kit:     %s\nhome:    %s\nlog:     %s\n' "$ROOT" "$H" "$LOG"
  printf 'widgets: %s installed, tools: %s installed\n\n' "$(ls "$T" | wc -l)" "$(ls "$BINDIR" | wc -l)"
  printf '%s\n\n' "${SUMMARY:-（没有拿到 Result 汇总行 —— 套件自己中途断了）}"
  printf 'selftest.sh exit: %s\n\n' "$RC"
} | tee "$REPORT"

# The policy check owns the exit code. --verbose lists the device failures it recorded, so the
# log still says what the phone has to answer instead of hiding it behind a green build.
POLICY=2
if [ -f "$JSON" ]; then
  python3 "$ROOT/tests/report_check.py" --verbose "$JSON" 2>&1 | tee -a "$REPORT"
  POLICY=${PIPESTATUS[0]}
else
  printf '✘ no report at %s — the suite did not reach the end of its run\n' "$JSON" | tee -a "$REPORT"
fi
printf '\nreport:  %s\njson:    %s\n' "$REPORT" "$JSON"

if [ -z "$SUMMARY" ]; then
  exit 1          # the suite did not reach its summary: that is a real break
fi
if [ "$STRICT" = 1 ]; then
  exit "$RC"
fi
exit "$POLICY"
