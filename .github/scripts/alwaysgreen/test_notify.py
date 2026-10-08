import notify


def _suppressed(**kw):
    entry = {
        "surface": "sm-smoke-e2e",
        "dispatch_key": "main:sm-smoke-e2e",
        "reason": "open-pr-covers-all-specs",
        "detail": "",
        "job_name": "Playwright e2e smoke after install",
        "also_failing_jobs": [],
        "job_level": False,
        "spec_count": 5,
        "fingerprints": ["aaaaaaaa", "bbbbbbbb"],
    }
    entry.update(kw)
    return entry


def _payload(**kw):
    payload = {
        "run_url": "https://github.com/camunda/camunda/actions/runs/1",
        "base_ref": "main",
        "references": {},
        "dispatches": [],
        "suppressed": [],
        "noise": [],
    }
    payload.update(kw)
    return payload


def test_a_suppressed_area_names_what_failed_why_and_the_covering_pr():
    # The whole point of the rewrite: the message reported `open-pr-covers-all-specs x2`
    # and named neither the surface nor the PR, so reading it told the medic nothing
    # they could act on without opening the triage run.
    payload = _payload(
        suppressed=[_suppressed()],
        references={"covered_by": {"aaaaaaaa": "camunda/c8-cross-component-e2e-tests#3951"}},
    )

    out = notify.text(payload, "main", "https://triage")

    assert "no fix agent needed" in out
    assert "Self-Managed E2E" in out
    assert "5 failing tests" in out
    assert "already covered by an open fix PR" in out
    assert (
        "<https://github.com/camunda/c8-cross-component-e2e-tests/pull/3951"
        "|c8-cross-component-e2e-tests#3951>"
    ) in out
    assert "open-pr-covers-all-specs" not in out


def test_each_failing_area_gets_its_own_line():
    payload = _payload(
        suppressed=[
            _suppressed(),
            _suppressed(
                surface="saas-smoke-e2e",
                dispatch_key="main:saas-smoke-e2e",
                reason="agent-already-running",
                spec_count=1,
                fingerprints=["cccccccc"],
            ),
        ]
    )

    lines = [l for l in notify.text(payload, "main", "").splitlines() if l.startswith("•")]

    assert len(lines) == 2
    assert "Self-Managed E2E" in lines[0]
    assert "SaaS E2E" in lines[1] and "1 failing test," in lines[1]


def test_a_product_bug_suppression_links_the_issue():
    payload = _payload(
        suppressed=[_suppressed(reason="tracked-by-open-product-bug")],
        references={
            "product_bugs": {"bbbbbbbb": "https://github.com/camunda/camunda/issues/55864"}
        },
    )

    out = notify.text(payload, "main", "")

    assert "tracked as a known product bug" in out
    assert "|camunda#55864>" in out


def test_a_surface_locked_by_a_pr_links_the_holder_not_a_fingerprint():
    # `open-fix-pr-for-surface` is decided per dispatch key, so the fingerprint index
    # holds no answer for it and the key index is the only one that does.
    payload = _payload(
        suppressed=[_suppressed(reason="open-fix-pr-for-surface")],
        references={
            "keys": {"main:sm-smoke-e2e": ["camunda/camunda-platform-helm#7451"]},
            "covered_by": {"aaaaaaaa": "camunda/wrong#1"},
        },
    )

    out = notify.text(payload, "main", "")

    assert "camunda-platform-helm#7451" in out
    assert "wrong#1" not in out


def test_more_references_than_fit_are_counted_not_dropped():
    payload = _payload(
        suppressed=[_suppressed(fingerprints=[f"fp{i}" for i in range(5)])],
        references={"covered_by": {f"fp{i}": f"camunda/e2e#{i}" for i in range(5)}},
    )

    out = notify.text(payload, "main", "")

    assert "(+2 more)" in out


def test_a_reason_without_a_sentence_still_shows_its_code():
    # A suppression path added to plan.py but not translated here must be visible in
    # the channel from the day it first fires, not silently rendered as "no reason".
    payload = _payload(suppressed=[_suppressed(reason="brand-new-reason")])

    out = notify.text(payload, "main", "")

    assert "brand-new-reason" in out


def test_a_job_level_failure_does_not_claim_zero_failing_tests():
    payload = _payload(
        suppressed=[
            _suppressed(
                surface="helm-install",
                reason="agent-already-running",
                job_level=True,
                spec_count=0,
                fingerprints=["dddddddd"],
            )
        ]
    )

    out = notify.text(payload, "main", "")

    assert "0 failing test" not in out
    assert "the job failed before any test ran" in out


def test_merged_job_level_failures_report_the_job_count():
    payload = _payload(
        suppressed=[
            _suppressed(
                job_level=True,
                spec_count=0,
                also_failing_jobs=["Playwright e2e full after install"],
            )
        ]
    )

    assert "2 failing jobs" in notify.text(payload, "main", "")


def test_a_dispatch_names_the_first_test_the_agent_was_given():
    payload = _payload(
        dispatches=[
            {
                "surface": "sm-smoke-e2e",
                "job_name": "Playwright e2e smoke after install",
                "also_failing_jobs": [],
                "job_level": False,
                "test_specs": [
                    {"file": "tests/SM-8.10/a.spec.ts", "test_name": "logs in"},
                    {"file": "tests/SM-8.10/b.spec.ts", "test_name": "deploys"},
                ],
            }
        ]
    )

    out = notify.text(payload, "main", "https://triage")

    assert "dispatched 1 fix agent" in out
    assert "tests/SM-8.10/a.spec.ts" in out
    assert "(+1 more)" in out


def test_a_withheld_dispatch_says_so_rather_than_announcing_an_agent():
    payload = _payload(
        dispatches=[{"surface": "sm-smoke-e2e", "test_specs": [], "job_level": True}]
    )

    dry = notify.text(payload, "main", "", notify.STATE_DRY_RUN)
    failed = notify.text(payload, "main", "", notify.STATE_FAILED)

    assert "would dispatch" in dry and "switched off" in dry
    assert "failed to start" in failed and "nobody is working on this yet" in failed


def test_a_failed_triage_says_the_failure_is_unattended():
    out = notify.text(_payload(), "main", "https://triage", notify.STATE_TRIAGE_FAILED)

    assert "triage failed" in out
    assert "unattended" in out
    assert "<https://triage|Triage run ↗>" in out


def test_undiagnosable_jobs_are_reported_when_nothing_else_is():
    payload = _payload(
        noise=[
            {"job": "install for install on gke", "verdict": "helm-cluster-fault: oom"},
            {"job": "Cleanup", "verdict": "helm-cluster-fault: oom"},
        ]
    )

    out = notify.text(payload, "main", "")

    assert "2 failing jobs could not be diagnosed" in out
    assert "helm-cluster-fault x2" in out


def test_an_empty_plan_says_nothing_matched_rather_than_nothing_at_all():
    assert "No failing job matched" in notify.text(_payload(), "main", "")


def test_a_hostile_branch_name_cannot_break_out_of_its_code_span():
    out = notify.text(_payload(), "main`<http://evil|click>`", "")

    assert "`main&lt;http://evil|click&gt;`" in out


def test_links_are_omitted_when_their_url_is_missing():
    out = notify.text(_payload(run_url=""), "main", "")

    assert "Failing run" not in out
    assert notify.ASK_CHANNEL_NAME in out


def test_a_path_claim_falls_back_to_the_human_detail_when_it_has_no_refs():
    # plan.py writes this one as `tests/x.spec.ts (#123)`, which is already the
    # answer — unlike every other detail, which is a fingerprint list.
    payload = _payload(
        suppressed=[
            _suppressed(
                reason="spec-path-claimed-by-open-pr",
                detail="tests/SM-8.10/a.spec.ts (#123)",
            )
        ]
    )

    out = notify.text(payload, "main", "")

    assert "already touches these test files: tests/SM-8.10/a.spec.ts (#123)" in out
