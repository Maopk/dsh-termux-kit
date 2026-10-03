"""Unit tests for the test-layer policy in tests/report_check.py.

The policy is the part of the self-check that CI can actually enforce: which failures are
regressions and which ones only the phone could have answered. Those decisions are pure
functions over the report document, so they are tested here instead of on a device.
"""

from __future__ import annotations

import json

import pytest

import report_check as rc


def item(
    status: str = "pass",
    category: str = "logic",
    iid: str = "an assertion",
    ci_allowed: bool = False,
    detail: str = "detail",
    tier: str = "L3",
) -> dict:
    return {
        "status": status,
        "tier": tier,
        "id": iid,
        "category": category,
        "ci_allowed": ci_allowed,
        "detail": detail,
    }


def document(items: list[dict], **overrides: object) -> dict:
    """A self-consistent report for the given items (overrides break it on purpose)."""
    counts = rc.tally(items)
    summary = {
        "logic": dict(counts["logic"]),
        "simulable": dict(counts["simulable"]),
        "device": dict(counts["device"]),
        "total": dict(counts["total"]),
        "unexpected_failures": len(rc.unexpected_failures(items)),
        "unclassified": counts["unclassified"],
    }
    doc: dict[str, object] = {
        "schema": rc.SCHEMA,
        "suite": "widgets",
        "started": "2026-10-03T10:00:00+0000",
        "finished": "2026-10-03T10:00:05+0000",
        "items": items,
        "summary": summary,
    }
    doc.update(overrides)
    return doc


def test_a_clean_report_has_no_problems() -> None:
    doc = document(
        [
            item("pass", "logic", "a syntax check"),
            item("skip", "simulable", "a rehearsal", detail="no local server"),
            item("pass", "device", "a real run"),
        ]
    )
    assert rc.check_schema(doc) == []
    assert rc.policy_problems(doc) == []


@pytest.mark.parametrize("category", ["logic", "simulable"])
def test_a_failure_this_machine_could_answer_is_a_regression(category: str) -> None:
    doc = document([item("fail", category, "hard-coded lamp offsets", detail="overran in Chinese")])
    problems = rc.policy_problems(doc)
    assert len(problems) == 1
    assert "hard-coded lamp offsets" in problems[0]
    assert category in problems[0]


def test_a_device_failure_the_table_allows_is_waived() -> None:
    doc = document([item("fail", "device", "3_backup-dsh.sh", ci_allowed=True, detail="real run failed")])
    assert rc.policy_problems(doc) == []


def test_a_device_failure_nobody_allowed_is_unexpected() -> None:
    doc = document([item("fail", "device", "v1.8 soft stop durable", ci_allowed=False, detail="came back after stop")])
    problems = rc.policy_problems(doc)
    assert len(problems) == 1
    assert "not answerable here" in problems[0]


def test_an_unclassified_failure_is_never_waived() -> None:
    doc = document([item("fail", "", "mystery assertion", ci_allowed=True)])
    problems = rc.policy_problems(doc)
    assert len(problems) == 1
    assert "unclassified failure" in problems[0]
    # …and the schema says so too, so the document cannot be read as a clean run.
    assert any("category" in problem for problem in rc.check_schema(doc))


def test_the_document_must_agree_with_its_own_items() -> None:
    doc = document([item("fail", "logic", "a syntax check")])
    doc["summary"]["unexpected_failures"] = 0
    problems = rc.check_schema(doc)
    assert any("unexpected_failures" in problem for problem in problems)

    doc = document([item("pass", "logic", "a syntax check")])
    doc["summary"]["logic"]["pass"] = 7
    assert any("logic/pass" in problem for problem in rc.check_schema(doc))

    doc = document([item("pass", "logic", "a syntax check")])
    doc["summary"]["total"]["pass"] = 7
    assert any("total/pass" in problem for problem in rc.check_schema(doc))


def test_a_document_without_items_is_unusable() -> None:
    doc = document([])
    del doc["items"]
    assert any("items" in problem for problem in rc.check_schema(doc))
    assert rc.policy_problems(doc) == ["no items to judge"]


def test_load_report_reads_json_and_rejects_junk(tmp_path) -> None:
    good = tmp_path / "report.json"
    good.write_text(json.dumps(document([item()])), encoding="utf-8")
    assert rc.load_report(good)["schema"] == rc.SCHEMA

    junk = tmp_path / "junk.json"
    junk.write_text("{not json", encoding="utf-8")
    with pytest.raises(rc.ReportError):
        rc.load_report(junk)

    with pytest.raises(rc.ReportError):
        rc.load_report(tmp_path / "missing.json")


def test_main_exit_codes(tmp_path, capsys) -> None:
    clean = tmp_path / "clean.json"
    clean.write_text(json.dumps(document([item("pass", "logic", "a syntax check")])), encoding="utf-8")
    assert rc.main([str(clean)]) == 0
    assert "logic and simulable are clean" in capsys.readouterr().out

    broken = tmp_path / "broken.json"
    broken.write_text(json.dumps(document([item("fail", "logic", "a syntax check")])), encoding="utf-8")
    assert rc.main([str(broken)]) == 1
    assert "logic failure" in capsys.readouterr().out

    lying = tmp_path / "lying.json"
    doc = document([item("pass", "logic", "a syntax check")])
    doc["summary"]["logic"]["pass"] = 3
    lying.write_text(json.dumps(doc), encoding="utf-8")
    assert rc.main([str(lying)]) == 2

    assert rc.main([str(tmp_path / "nope.json")]) == 2
    assert rc.main([]) == 2


def test_summarize_reads_like_a_tally() -> None:
    doc = document(
        [
            item("pass", "logic"),
            item("fail", "device", "3_backup-dsh.sh", ci_allowed=True),
            item("skip", "device"),
        ]
    )
    line = rc.summarize(doc)
    assert "logic 1/0/0" in line
    assert "device 0/1/1" in line
    assert rc.summarize(doc, markdown=True).startswith("`")


def test_unexpected_failures_lists_only_the_refused_ones() -> None:
    allowed = item("fail", "device", "7_reconnect-ai.sh", ci_allowed=True)
    refused = item("fail", "device", "sandbox URL usable", ci_allowed=False)
    unclassified = item("fail", "", "mystery")
    ok = item("fail", "logic", "a syntax check")
    ids = [i["id"] for i in rc.unexpected_failures([allowed, refused, unclassified, ok, item()])]
    assert ids == ["sandbox URL usable", "mystery", "a syntax check"]


def test_recorded_failures_are_listed_but_not_refused() -> None:
    allowed = item("fail", "device", "3_backup-dsh.sh", ci_allowed=True, tier="L4")
    refused = item("fail", "device", "sandbox URL usable", ci_allowed=False, tier="L5")
    assert [i["id"] for i in rc.recorded_failures([allowed, refused, item()])] == ["3_backup-dsh.sh"]
    assert rc.recorded_failures([refused]) == []
    assert rc.unexpected_failures([allowed, refused]) == [refused]


def test_verbose_names_the_recorded_failures(tmp_path, capsys) -> None:
    path = tmp_path / "r.json"
    path.write_text(
        json.dumps(document([item("fail", "device", "3_backup-dsh.sh", ci_allowed=True, tier="L4")])),
        encoding="utf-8",
    )
    assert rc.main(["--verbose", str(path)]) == 0
    out = capsys.readouterr().out
    assert "cannot answer" in out
    assert "[L4] 3_backup-dsh.sh" in out
