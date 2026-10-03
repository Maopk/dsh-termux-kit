"""The writer and the checker have to agree on the report the suite actually produces.

`tests/fixtures/selftest-report.json` is a real report from `tests/selftest.sh`, run on a machine that
cannot answer the device questions (the throwaway HOME is scrubbed to `~`, the working copy to `$KIT`).
`test_report_check.py` builds its reports by hand, so it would keep passing while the writer drifted —
this file fails the moment the two sides disagree about a field name, a count or a category.
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
