#!/usr/bin/env python3
"""report_check - apply the test-layer policy to a self-check report.

Why this file exists: tests/selftest.sh only ever answers on the phone, so CI cannot run it.
What CI *can* do is read the machine-readable report the suite now writes and hold two lines:

  · logic and simulable failures are never allowed - if this machine could answer the check,
    a red one is a regression, not an environment problem;
  · a device failure is allowed only when tests/lib/categories.tsv marks it "allow", i.e. a
    reviewed decision that a container cannot answer that question. Everything else is
    unexpected, and "unexpected" is what the pull-request policy counts.

An item that carries no category at all is treated as a failure too, so a new assertion
cannot slip past classification.

Usage:
    python3 tests/report_check.py <report.json> [--markdown]

Exit codes: 0 the policy holds · 1 it does not · 2 the report itself is unusable.
"""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

SCHEMA = "dsh-selftest/1"
CATEGORIES = ("logic", "simulable", "device")
STATUSES = ("pass", "fail", "skip")
ITEM_KEYS = ("status", "tier", "id", "category", "ci_allowed", "detail")


class ReportError(Exception):
    """The report cannot be read as a report at all."""


def load_report(path: str | Path) -> dict[str, Any]:
    """Read a report document. Raises ReportError when it is not one."""
    try:
        text = Path(path).read_text(encoding="utf-8")
    except OSError as exc:
        raise ReportError(f"cannot read {path}: {exc}") from exc
    try:
        doc = json.loads(text)
    except json.JSONDecodeError as exc:
        raise ReportError(f"{path} is not valid JSON: {exc}") from exc
    if not isinstance(doc, dict):
        raise ReportError(f"{path} is not a JSON object")
    return doc


def check_schema(doc: dict[str, Any]) -> list[str]:
    """Every structural problem that would make the policy meaningless."""
    problems: list[str] = []
    if doc.get("schema") != SCHEMA:
        problems.append(f"schema is {doc.get('schema')!r}, expected {SCHEMA!r}")
    items = doc.get("items")
    if not isinstance(items, list):
        problems.append("items is missing or not a list")
        return problems

    for i, item in enumerate(items):
        if not isinstance(item, dict):
            problems.append(f"item {i} is not an object")
            continue
        where = item.get("id") or f"item {i}"
        for key in ITEM_KEYS:
            if key not in item:
                problems.append(f"{where}: no {key!r}")
        if item.get("status") not in STATUSES:
            problems.append(f"{where}: status {item.get('status')!r} is not one of {STATUSES}")
        if item.get("category") not in CATEGORIES:
            problems.append(f"{where}: category {item.get('category')!r} is not one of {CATEGORIES}")
        if not isinstance(item.get("ci_allowed"), bool):
            problems.append(f"{where}: ci_allowed is not a boolean")

    # The counts in the document must agree with the items: a report that lies about its own
    # tally would let a real failure through unnoticed.
    summary = doc.get("summary")
    if not isinstance(summary, dict):
        problems.append("summary is missing or not an object")
        return problems
    recomputed = tally(items)
    for category in CATEGORIES:
        for status in STATUSES:
            want = recomputed[category][status]
            got = summary.get(category, {}).get(status) if isinstance(summary.get(category), dict) else None
            if got != want:
                problems.append(f"summary says {category}/{status}={got}, the items say {want}")
    if summary.get("unclassified") != recomputed["unclassified"]:
        problems.append(f"summary says unclassified={summary.get('unclassified')}, the items say {recomputed['unclassified']}")
    for status in STATUSES:
        got = summary.get("total", {}).get(status) if isinstance(summary.get("total"), dict) else None
        if got != recomputed["total"][status]:
            problems.append(f"summary says total/{status}={got}, the items say {recomputed['total'][status]}")
    if summary.get("unexpected_failures") != len(unexpected_failures(items)):
        problems.append(
            f"summary says unexpected_failures={summary.get('unexpected_failures')}, "
            f"the items say {len(unexpected_failures(items))}"
        )
    return problems


def tally(items: list[dict[str, Any]]) -> dict[str, Any]:
    """Category × status counts, the totals, and the number of unclassified items."""
    counts: dict[str, Any] = {category: dict.fromkeys(STATUSES, 0) for category in CATEGORIES}
    counts["unclassified"] = 0
    counts["total"] = dict.fromkeys(STATUSES, 0)
    for item in items:
        if not isinstance(item, dict):
            continue
        category, status = item.get("category"), item.get("status")
        if status not in STATUSES:
            continue
        counts["total"][status] += 1
        if category in CATEGORIES:
            counts[category][status] += 1
        else:
            counts["unclassified"] += 1
    return counts


def unexpected_failures(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """The failures the merge policy refuses: everything this machine could have answered."""
    out = []
    for item in items:
        if not isinstance(item, dict) or item.get("status") != "fail":
            continue
        category = item.get("category")
        if category not in CATEGORIES:
            out.append(item)  # unclassified: nobody decided, so it cannot be waived
        elif category != "device" or not item.get("ci_allowed"):
            out.append(item)
    return out


def recorded_failures(items: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Failures that are real but answerable only off this machine — recorded, not waived.

    The device tier keeps failing here by design (no adb, no bridge, no Termux interpreter).
    Listing them makes the CI log say what the phone still has to answer, instead of hiding it.
    """
    out = []
    for item in items:
        if isinstance(item, dict) and item.get("status") == "fail" and item.get("category") == "device":
            if item.get("ci_allowed"):
                out.append(item)
    return out


def policy_problems(doc: dict[str, Any]) -> list[str]:
    """The policy verdict, as a list of reasons (empty means it holds)."""
    items = doc.get("items")
    if not isinstance(items, list):
        return ["no items to judge"]
    problems = []
    for item in unexpected_failures(items):
        where = item.get("id", "?")
        category = item.get("category") or "unclassified"
        allowed = "allowed off-phone" if item.get("ci_allowed") else "not answerable here"
        problems.append(f"{category} failure: {where} ({allowed}) - {item.get('detail', '')}")
    return problems


def summarize(doc: dict[str, Any], markdown: bool = False) -> str:
    """One line a human (or the run summary) can read."""
    raw = doc.get("items")
    items: list[dict[str, Any]] = raw if isinstance(raw, list) else []
    counts = tally(items)
    parts = [f"{category} {counts[category]['pass']}/{counts[category]['fail']}/{counts[category]['skip']}" for category in CATEGORIES]
    line = "passed/failed/skipped — " + " · ".join(parts)
    if counts["unclassified"]:
        line += f" · unclassified {counts['unclassified']}"
    if markdown:
        return f"`{line}`"
    return line


def main(argv: list[str]) -> int:
    flags = ("--markdown", "--verbose")
    args = [a for a in argv if a not in flags]
    markdown = "--markdown" in argv
    verbose = "--verbose" in argv
    if len(args) != 1:
        print(__doc__.strip().splitlines()[-1], file=sys.stderr)
        print("usage: python3 tests/report_check.py <report.json> [--markdown] [--verbose]", file=sys.stderr)
        return 2
    try:
        doc = load_report(args[0])
    except ReportError as exc:
        print(f"✘ {exc}", file=sys.stderr)
        return 2

    print(summarize(doc, markdown=markdown))
    schema = check_schema(doc)
    if schema:
        print(f"✘ the report does not describe itself correctly ({len(schema)} problem(s)):")
        for problem in schema:
            print(f"   · {problem}")
        return 2

    if verbose:
        raw = doc.get("items")
        recorded = recorded_failures(raw if isinstance(raw, list) else [])
        if recorded:
            print(f"· {len(recorded)} failure(s) this machine cannot answer (allowed, still to check on the phone):")
            for item in recorded:
                print(f"   · [{item.get('tier', '?')}] {item.get('id', '?')} - {item.get('detail', '')}")

    problems = policy_problems(doc)
    if problems:
        print(f"✘ the test-layer policy does not hold ({len(problems)} problem(s)):")
        for problem in problems:
            print(f"   · {problem}")
        print("   A logic or simulable failure must be fixed; a device failure needs either a fix or")
        print("   a reviewed 'allow' row in tests/lib/categories.tsv (see docs/test-layers.md).")
        return 1

    print("✔ logic and simulable are clean; every device failure is one this machine cannot answer")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
