"""A real report, so the checker is tested against something it did not write itself.

`tests/fixtures/selftest-report.json` is one full report from `tests/selftest.sh` — 70 assertions, all three
categories, the eleven device failures the policy records rather than waives (the throwaway HOME is scrubbed
to `~`, the working copy to `$KIT`). The cases in `test_report_check.py` build their reports by hand, so they
would keep passing while the checker's behaviour on a real artifact drifted; this file would not.

The writer's own shape is not covered here: `tests/lib-tests.sh` asserts it field by field, and the container
job runs the suite and hands its report to this same checker.
"""

from __future__ import annotations

from pathlib import Path

from report_check import (
    check_schema,
    load_report,
    policy_problems,
    recorded_failures,
    summarize,
    tally,
    unexpected_failures,
)

FIXTURE = Path(__file__).resolve().parent.parent / "fixtures" / "selftest-report.json"


def test_fixture_is_a_report_the_checker_accepts() -> None:
    doc = load_report(FIXTURE)
    assert doc["schema"] == "dsh-selftest/1"
    assert check_schema(doc) == []


def test_fixture_records_device_failures_instead_of_waiving_them() -> None:
    items = load_report(FIXTURE)["items"]
    recorded = recorded_failures(items)
    assert len(recorded) == 11
    assert all(item["category"] == "device" and item["ci_allowed"] for item in recorded)
    assert unexpected_failures(items) == []


def test_fixture_has_no_unclassified_assertions() -> None:
    doc = load_report(FIXTURE)
    assert doc["summary"]["unclassified"] == 0
    assert {item["category"] for item in doc["items"]} == {"logic", "simulable", "device"}


def test_fixture_policy_holds_and_counts_are_consistent() -> None:
    doc = load_report(FIXTURE)
    assert policy_problems(doc) == []
    counted = tally(doc["items"])
    for category in ("logic", "simulable", "device"):
        assert doc["summary"][category] == counted[category]
    assert doc["summary"]["total"] == counted["total"]


def test_fixture_summary_reads_like_the_line_ci_prints() -> None:
    line = summarize(load_report(FIXTURE))
    assert "logic" in line
    assert "simulable" in line
    assert "device" in line
