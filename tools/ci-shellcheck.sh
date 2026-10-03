#!/data/data/com.termux/files/usr/bin/bash
# ci-shellcheck - run ShellCheck (and optionally bash -n) over every script here.
#
# Why a script instead of a one-liner in the workflow: every shebang in this repo
# points at /data/data/com.termux/... , so the file list cannot come from the
# executable bit or from `find -name '*.sh'`. This walks the tree, picks the shell
# files by their shebang, and hands them to the ShellCheck pinned in
# requirements-dev.txt (so CI and a laptop run the same one).
#
# The severity is passed on the command line as well as in .shellcheckrc: the rc file
# carries the per-code exemptions, the CLI flag makes sure a run from an odd working
# directory still means "warning and above".
#
# Usage:
#   bash tools/ci-shellcheck.sh               # ShellCheck, exit 1 on a finding
#   bash tools/ci-shellcheck.sh --syntax-only # bash -n only, no ShellCheck needed
#   bash tools/ci-shellcheck.sh --list        # print the files it would check
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT" || exit 1

MODE=lint
for a in "$@"; do
  case "$a" in
    --syntax-only) MODE=syntax ;;
    --list)        MODE=list ;;
    -h|--help)     sed -n '2,17p' "$0"; exit 0 ;;
    *) printf 'usage: bash tools/ci-shellcheck.sh [--syntax-only|--list]\n' >&2; exit 2 ;;
  esac
done

# recon/ is a snapshot of the published tree, _local/ is scratch space - neither is
# part of the gate. Everything else is picked by shebang.
files() {
  find . -type f \
    -not -path './recon/*' -not -path './_local/*' -not -path './.git/*' \
    -not -path './out/*' -not -path './build/*' \
    | LC_ALL=C sort \
    | while IFS= read -r f; do
        # -c + tr: the tree also holds APKs and tgz files, and a null byte in a line
        # makes bash print a warning for something that is not a script at all.
        first="$(head -c 512 "$f" 2>/dev/null | tr -d '\000' | head -n 1)"
        case "$first" in
          *bash*|*/sh|*' sh'*) printf '%s\n' "$f" ;;
        esac
      done
  # tools/check-no-secrets.sh is documented as `bash tools/check-no-secrets.sh` and
  # carries its usage line where the shebang would be, so it needs naming here.
  printf '%s\n' './tools/check-no-secrets.sh'
}

mapfile -t FILES < <(files)
printf 'shell files: %s\n' "${#FILES[@]}"

if [ "$MODE" = list ]; then
  printf '%s\n' "${FILES[@]}"
  exit 0
fi

CHECKED=0
FAIL=0

if [ "$MODE" = syntax ]; then
  for f in "${FILES[@]}"; do
    CHECKED=$((CHECKED + 1))
    if out="$(bash -n "$f" 2>&1)"; then :; else
      printf '✘ syntax: %s\n%s\n' "$f" "$out"
      FAIL=$((FAIL + 1))
    fi
  done
  printf '\n%s files, %s with a syntax error\n' "$CHECKED" "$FAIL"
else
  SHELLCHECK="${SHELLCHECK:-shellcheck}"
  if ! command -v "$SHELLCHECK" >/dev/null 2>&1; then
    printf '✘ shellcheck not found - install the pinned one with:\n    pip install -r requirements-dev.txt\n' >&2
    exit 2
  fi
  printf 'shellcheck: %s\n' "$("$SHELLCHECK" --version | grep -m1 'version:')"
  for f in "${FILES[@]}"; do
    CHECKED=$((CHECKED + 1))
    if out="$("$SHELLCHECK" -S warning -f gcc "$f" 2>&1)"; then :; else
      printf '%s\n' "$out"
      FAIL=$((FAIL + 1))
    fi
  done
  printf '\n%s files, %s with a finding (severity >= warning; per-code exemptions in .shellcheckrc)\n' \
    "$CHECKED" "$FAIL"
fi

[ "$FAIL" = 0 ] || exit 1
printf '✔ static shell check clean\n'
exit 0
