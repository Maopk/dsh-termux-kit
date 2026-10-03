#!/data/data/com.termux/files/usr/bin/bash
# ci-gates - every check that can run against this repo alone (no phone, no Android SDK).
#
# This is the off-phone half of tests/selftest.sh: the same battery CONTRIBUTING.md
# calls the gates. Two things make it runnable away from the phone:
#   · the two app source variables point at the mirrors in apps/ (not at ~/dsh-console)
#   · HOME is a throw-away directory, so the one gate that looks at an install folder
#     (~/.local/bin) has nothing of yours to look at
#
# Usage:
#   bash tools/ci-gates.sh
#   DSH_CONSOLE_DIR=~/dsh-console DSH_BRIDGE_DIR=~/droid-bridge bash tools/ci-gates.sh
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

export DSH_KIT_REPO="${DSH_KIT_REPO:-$ROOT}"
export DSH_CONSOLE_DIR="${DSH_CONSOLE_DIR:-$ROOT/apps/console}"
export DSH_BRIDGE_DIR="${DSH_BRIDGE_DIR:-$ROOT/apps/bridge}"
# The tools print ✔ / ✘ / ⚠. On a machine whose locale is not UTF-8 (Windows, a bare
# container) python would die on those characters instead of reporting its verdict.
export PYTHONUTF8=1
export PYTHONIOENCODING=utf-8

PY="${PYTHON:-python3}"
NODE="${NODE:-node}"

GATE_HOME="${TMPDIR:-/tmp}/dsh-ci-gates-home.$$"
mkdir -p "$GATE_HOME"
cleanup() { rm -rf "$GATE_HOME"; }
trap cleanup EXIT

PASS=0; FAIL=0; SKIPPED=0

need() { command -v "$1" >/dev/null 2>&1; }

gate() {   # gate <name> <command...>
  local name="$1" rc; shift
  printf '\n▶ %s\n' "$name"
  if "$@"; then
    PASS=$((PASS + 1)); printf '   ✔ %s\n' "$name"
  else
    rc=$?; FAIL=$((FAIL + 1)); printf '   ✘ %s (exit %s)\n' "$name" "$rc" >&2
  fi
}

skip() {   # skip <name> <reason>
  SKIPPED=$((SKIPPED + 1)); printf '\n▶ %s\n   · skipped: %s\n' "$1" "$2"
}

printf 'kit=%s\nconsole=%s\nbridge=%s\n' "$DSH_KIT_REPO" "$DSH_CONSOLE_DIR" "$DSH_BRIDGE_DIR"
printf 'python=%s node=%s\n' "$PY" "$NODE"

# The gates from tools/pre-push-check, plus the secrets scan. All of them are read-only:
# none writes into the repo, and the install-folder one gets its own HOME.
gate 'task ids: every list only sends ids the allowlist accepts' "$PY" tools/check-task-ids
gate 'secrets: no token, key or phone number in the tree'       bash tools/check-no-secrets.sh
gate 'ui controls: panel and both apps agree on 11 items'       "$PY" tools/ui-controls check
gate 'i18n table: generated blocks match i18n/zh.json'          "$PY" tools/i18n-table check
gate 'i18n java fix: console Lang.java is clean' \
  "$PY" tools/i18n-java-fix "$DSH_CONSOLE_DIR/src/io/dsh/console/Lang.java" --check
gate 'i18n java fix: bridge Lang.java is clean' \
  "$PY" tools/i18n-java-fix "$DSH_BRIDGE_DIR/src/io/dsh/bridge/Lang.java" --check
gate 'install tools: report only, against a throw-away HOME' \
  env HOME="$GATE_HOME" bash tools/install-tools --check

# The report checker's unit tests are Python, so they need pytest. CI has it from
# requirements-dev.txt; anywhere else install the same pin (tools/ci-shellcheck.sh does the
# same for its wheel) and say so honestly if even that is not possible.
if ! need "$PY"; then
  skip 'python unit tests' 'python3 is not on PATH'
else
  if ! "$PY" -c 'import pytest' >/dev/null 2>&1; then
    PIN="$(sed -n 's/^pytest==\(.*\)$/\1/p' requirements-dev.txt | head -1)"
    [ -n "$PIN" ] && "$PY" -m pip install --quiet --disable-pip-version-check "pytest==$PIN" >/dev/null 2>&1
  fi
  if "$PY" -c 'import pytest' >/dev/null 2>&1; then
    gate 'python unit tests: the report checker against a real report' "$PY" -m pytest tests/unit -q
  else
    skip 'python unit tests' 'pytest is not installed and could not be installed'
  fi
fi

# These two need node: the audit shells out to the panel render test.
if need "$NODE"; then
  gate 'panel render: three passes, the panel must not vanish on tap' "$NODE" tools/panel-render-test
  gate 'i18n audit: copy, theme, palette, status wording'             "$PY" tools/i18n-audit --quiet
else
  skip 'panel render' 'node is not on PATH'
  skip 'i18n audit'   'node is not on PATH (the audit runs panel-render-test)'
fi

printf '\n════ Result: %s passed / %s failed / %s skipped ════\n' "$PASS" "$FAIL" "$SKIPPED"
[ "$FAIL" = 0 ] || exit 1
printf '✔ all off-phone gates passed\n'
exit 0
