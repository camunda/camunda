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


def test_the_caller_can_supply_the_failing_run_when_the_plan_has_none():
    # A discovery failure leaves no plan, which is the case where the link to the run
    # that actually broke matters most.
    out = notify.text(
        _payload(run_url=""), "main", "https://triage",
        notify.STATE_TRIAGE_FAILED, "https://github.com/camunda/x/actions/runs/9",
    )

    assert "<https://github.com/camunda/x/actions/runs/9|Failing run ↗>" in out


def test_an_unreadable_report_is_not_reported_as_no_test_having_run():
    # `no-failing-specs-extracted` has exactly this shape — not job_level, no specs —
    # and there the tests DID run; their results could not be read.
    payload = _payload(
        suppressed=[
            _suppressed(reason="no-failing-specs-extracted", spec_count=0, fingerprints=[])
        ]
    )

    out = notify.text(payload, "main", "")

    assert "before any test ran" not in out
    assert "no readable test results" in out


def test_a_product_bug_is_not_captioned_with_a_covering_prs_number():
    # A fingerprint can be both tracked by a bug and claimed by a PR. The planner picks
    # the product-bug reason, so the reference must come from the product-bug index —
    # a merged index let the PR win and be labelled as the bug.
    payload = _payload(
        suppressed=[_suppressed(reason="tracked-by-open-product-bug")],
        references={
            "product_bugs": {"aaaaaaaa": "https://github.com/camunda/camunda/issues/1"},
            "covered_by": {"aaaaaaaa": "camunda/e2e#999"},
        },
    )

    out = notify.text(payload, "main", "")

    assert "camunda#1" in out
    assert "999" not in out


def test_a_failed_lookup_says_nobody_may_be_on_it_rather_than_an_agent_is():
    # discover fails closed: a failed in-flight lookup suppresses by claiming every key
    # is in flight. The suppression is right, but "an agent is already working on this"
    # is then a guess, and it is the one sentence that makes a medic move on.
    payload = _payload(
        suppressed=[_suppressed(reason="agent-already-running")],
        references={"lookups_failed": ["inflight"]},
    )

    out = notify.text(payload, "main", "")

    assert "already working on this area" not in out
    assert "could not check whether an agent is already running" in out
    assert "nobody may be on this" in out


def test_a_verified_lookup_keeps_the_plain_reason():
    payload = _payload(
        suppressed=[_suppressed(reason="agent-already-running")],
        references={"lookups_failed": []},
    )

    assert "already working on this area" in notify.text(payload, "main", "")


def test_a_candidate_on_another_branch_is_labelled_with_its_own_ref():
    # preview-env-smoke-test.yml deploys four minors from one `main` run, so a single
    # headline ref would label a stable-branch failure `main`.
    payload = _payload(
        suppressed=[
            _suppressed(base_ref="main"),
            _suppressed(
                base_ref="stable/8.9",
                reason="base-ref-not-supported-by-fix-agent",
                dispatch_key="stable/8.9:sm-smoke-e2e",
            ),
        ]
    )

    lines = [l for l in notify.text(payload, "main", "").splitlines() if l.startswith("•")]

    assert "(on `" not in lines[0]
    assert "(on `stable/8.9`)" in lines[1]


def test_a_partial_dispatch_names_how_many_actually_started():
    # `failed` and `partial` call for opposite reactions, so they cannot share wording:
    # one means nobody is on any of it, the other that part of it is unattended.
    payload = _payload(
        dispatches=[
            {"surface": "sm-smoke-e2e", "test_specs": [], "job_level": True},
            {"surface": "saas-smoke-e2e", "test_specs": [], "job_level": True},
        ]
    )

    out = notify.text(payload, "main", "", notify.STATE_PARTIAL, "", 1)

    assert "started only 1 of 2 fix agents" in out
    assert "those areas are unattended" in out
