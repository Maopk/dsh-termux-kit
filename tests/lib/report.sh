#!/usr/bin/env bash
# report.sh — the pure half of the self-check's machine-readable report.
#
# Why this file exists
#   The suite runs on the phone, where this kit never assumes Python is installed, so the
#   report is produced by shell. But the *decisions* behind it — which layer an assertion
#   can be answered in, how a report is serialised, what counts as an unexpected failure —
#   are pure text in / text out, so they live here and are unit-tested in CI by
#   tests/lib-tests.sh (and validated end to end by tests/report_check.py).
#
# Contracts (all unit-tested in tests/lib-tests.sh):
#   report_level <tier>                       set the tier of the assertions that follow
#   report_category <tier> <id>               → logic | simulable | device | "" (unknown)
#   report_ci_allowed <tier> <id>             → 1 | 0
#   report_escape <text>                      → JSON-safe text, no surrounding quotes
#   report_flatten <text>                     → one line: tabs, CR and LF become spaces
#   report_item <status> <id> <category> <detail>
#                                             → one tab-separated row (the accumulator)
#   report_summary <rows>                     → the "summary" object
#   report_json <rows> [started] [finished]   → the whole document
#   report_unclassified <rows>                → rows whose category is not one of the three
#
# Row layout, tab separated: status  tier  id  category  ci_allowed  detail
# Status is normalised to pass | fail | skip.

REPORT_LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_CATEGORIES="${REPORT_CATEGORIES:-$REPORT_LIB_DIR/categories.tsv}"
REPORT_SCHEMA="dsh-selftest/1"
REPORT_SUITE="${REPORT_SUITE:-widgets}"
REPORT_LEVEL="${REPORT_LEVEL:-}"

# The tier already says where a check can be answered; categories.tsv only carries the
# exceptions. An unknown tier yields "" on purpose: tests/report_check.py turns that into
# a CI failure, so a new assertion cannot slip in unclassified.
report_default_category() { # <tier>
  case "${1:-}" in
    L1|L3)         printf 'logic' ;;
    L2)            printf 'simulable' ;;
    L0|L4|L5|SKIP) printf 'device' ;;
    *)             printf '' ;;
  esac
}

# One reviewed table, two lookups. A "<tier>:<id>" row wins over a bare "<id>" row, because
# the same id really does appear in several tiers (3_backup-dsh.sh is rehearsed in L2 and
# run for real in L4, and only the second one needs the phone).
report_lookup() { # <tier> <id> <category|ci> → value, or "" when nothing matches
  local tier="${1:-}" id="$2" field="$3" want k cat ci why
  [ -f "$REPORT_CATEGORIES" ] || return 0
  for want in "$tier:$id" "$id"; do
    case "$want" in :*|"") continue ;; esac
    while IFS=$'\t' read -r k cat ci why; do
      case "$k" in ''|'#'*) continue ;; esac
      [ "$k" = "$want" ] || continue
      if [ "$field" = ci ]; then
        if [ "$ci" = allow ]; then printf '1'; else printf '0'; fi
      else
        printf '%s' "$cat"
      fi
      return 0
    done < "$REPORT_CATEGORIES"
  done
  printf ''
}

report_category() { # <tier> <id>
  local cat
  cat="$(report_lookup "${1:-}" "$2" category)"
  [ -n "$cat" ] || cat="$(report_default_category "${1:-}")"
  printf '%s' "$cat"
}

report_ci_allowed() { # <tier> <id>
  local v
  v="$(report_lookup "${1:-}" "$2" ci)"
  printf '%s' "${v:-0}"
}

report_level() { REPORT_LEVEL="${1:-}"; }

report_flatten() { # <text> — a row must stay exactly one line
  local s="$1"
  s="${s//$'\t'/ }"
  s="${s//$'\r'/ }"
  s="${s//$'\n'/ }"
  printf '%s' "$s"
}

report_escape() { # <text> — JSON string body
  local s
  s="$(report_flatten "$1")"
  s="${s//\\/\\\\}"
  s="${s//\"/\\\"}"
  printf '%s' "$s"
}

report_status_norm() { # PASS|FAIL|SKIP → pass|fail|skip (already lower case stays as is)
  case "${1:-}" in
    PASS|pass) printf 'pass' ;;
    FAIL|fail) printf 'fail' ;;
    SKIP|skip) printf 'skip' ;;
    *)         printf '%s' "$(printf '%s' "${1:-}" | tr '[:upper:]' '[:lower:]')" ;;
  esac
}

report_item() { # <status> <id> <category> <detail> → one accumulator row
  local tier="$REPORT_LEVEL" st id cat detail
  st="$(report_status_norm "$1")"
  id="$(report_flatten "$2")"
  cat="$(report_flatten "$3")"
  # An empty category means "look it up": the suite then cannot record a category that the
  # table and the tier default disagree with.
  [ -n "$cat" ] || cat="$(report_category "$tier" "$id")"
  detail="$(report_flatten "$4")"
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' \
    "$st" "$tier" "$id" "$cat" "$(report_ci_allowed "$tier" "$id")" "$detail"
}

report_unclassified() { # <rows> → "<tier>\t<id>" for every row without a known category
  printf '%s\n' "${1:-}" | awk -F'\t' '
    NF >= 4 {
      cat = $4
      if (cat != "logic" && cat != "simulable" && cat != "device") print $2 "\t" $3
    }'
}

report_summary() { # <rows> → the summary object
  printf '%s\n' "${1:-}" | awk -F'\t' '
    NF >= 6 {
      cat = $4; st = $1
      if (cat != "logic" && cat != "simulable" && cat != "device") { unclassified++; next }
      cnt[cat, st]++
      total[st]++
      if (st == "fail") {
        # A logic or simulable failure is a regression on this machine. A device failure is
        # only tolerated when the reviewed table says a container cannot answer it.
        if (cat == "device") { if ($5 != "1") unexpected++ } else unexpected++
      }
    }
    END {
      printf "{"
      printf "\"logic\": {\"pass\": %d, \"fail\": %d, \"skip\": %d}, ", cnt["logic","pass"]+0, cnt["logic","fail"]+0, cnt["logic","skip"]+0
      printf "\"simulable\": {\"pass\": %d, \"fail\": %d, \"skip\": %d}, ", cnt["simulable","pass"]+0, cnt["simulable","fail"]+0, cnt["simulable","skip"]+0
      printf "\"device\": {\"pass\": %d, \"fail\": %d, \"skip\": %d}, ", cnt["device","pass"]+0, cnt["device","fail"]+0, cnt["device","skip"]+0
      printf "\"total\": {\"pass\": %d, \"fail\": %d, \"skip\": %d}, ", total["pass"]+0, total["fail"]+0, total["skip"]+0
      printf "\"unexpected_failures\": %d, ", unexpected+0
      printf "\"unclassified\": %d", unclassified+0
      printf "}"
    }'
}

report_json() { # <rows> [started] [finished] → the whole document
  local rows="${1:-}" started="${2:-}" finished="${3:-}" line st tier id cat ci detail first=1
  printf '{\n'
  printf '  "schema": "%s",\n' "$(report_escape "$REPORT_SCHEMA")"
  printf '  "suite": "%s",\n' "$(report_escape "$REPORT_SUITE")"
  printf '  "started": "%s",\n' "$(report_escape "$started")"
  printf '  "finished": "%s",\n' "$(report_escape "$finished")"
  printf '  "items": ['
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    IFS=$'\t' read -r st tier id cat ci detail <<<"$line"
    [ -n "$st" ] || continue
    if [ "$first" = 1 ]; then first=0; else printf ','; fi
    printf '\n    {"status": "%s", "tier": "%s", "id": "%s", "category": "%s", "ci_allowed": %s, "detail": "%s"}' \
      "$(report_escape "$st")" "$(report_escape "$tier")" "$(report_escape "$id")" \
      "$(report_escape "$cat")" \
      "$( [ "$ci" = 1 ] && printf true || printf false )" \
      "$(report_escape "$detail")"
  done <<<"$rows"
  if [ "$first" = 1 ]; then printf ']'; else printf '\n  ]'; fi
  printf ',\n'
  printf '  "summary": %s\n' "$(report_summary "$rows")"
  printf '}\n'
}
