"""Unit tests for AlwaysGreen dispatch planning and the PR coverage block."""

from __future__ import annotations

import classify
import plan


def _spec(name="t", file="tests/SM-8.10/smoke-tests.spec.ts", deterministic=True):
    statuses = ["failed"] * 3 if deterministic else ["failed", "passed"]
    return classify.FailingSpec(file=file, test_name=name, statuses=statuses)


def _cand(surface=classify.SURFACE_SM_E2E, base_ref="main", specs=None, job_level=False):
    return plan.Candidate(
        base_ref=base_ref,
        surface=surface,
        job_name="Playwright e2e after install - install on gke - agrn (1 of 1)",
        specs=list(specs if specs is not None else [_spec()]),
        job_level=job_level,
    )


def _plan(cands, **kw):
    kw.setdefault("covered_fingerprints", set())
    kw.setdefault("inflight_keys", set())
    kw.setdefault("open_pr_keys", set())
    kw.setdefault("product_bug_fingerprints", set())
    return plan.plan_dispatches(cands, **kw)


# ---------------------------------------------------------------------------
# Dispatch decisions
# ---------------------------------------------------------------------------


def test_dispatchable_surface_with_specs_is_dispatched():
    result = _plan([_cand()])
    assert len(result.dispatches) == 1
    assert result.suppressed == []


def test_non_dispatchable_surface_is_recorded_not_dispatched():
    result = _plan([_cand(surface=classify.SURFACE_BUILD)])
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_NOT_DISPATCHABLE


def test_helm_install_reaches_the_agent():
    # Whether a helm-install failure is worth an agent is decided earlier, by
    # helm_install_verdict in discover; anything that gets here is already actionable.
    result = _plan([_cand(surface=classify.SURFACE_HELM_INSTALL, specs=[], job_level=True)])
    assert len(result.dispatches) == 1


def test_saas_ci_job_failure_reaches_the_agent():
    # A downstream run whose specs all passed still has a fixable defect: the
    # failing job stands in for a spec, with the workflow that owns it as `file`.
    ci_spec = classify.ci_job_spec(
        "Delete created organizations",
        workflow_path=".github/workflows/playwright_saas_pr_trigger_monorepo.yml",
        failing_steps=["Delete orgs"],
    )
    result = _plan([_cand(surface=classify.SURFACE_SAAS_CI, specs=[ci_spec])])
    assert len(result.dispatches) == 1
    assert result.dispatches[0].key == "main:saas-ci"
    assert result.dispatches[0].deterministic_specs == [ci_spec]


def test_saas_ci_dedupes_per_job_not_per_run():
    # Two different jobs failing must produce two fingerprints, so a PR that
    # fixes one does not suppress dispatch for the other.
    a = classify.ci_job_spec("Delete created organizations", workflow_path="w.yml", failing_steps=[])
    b = classify.ci_job_spec("merge-reports", workflow_path="w.yml", failing_steps=[])
    cand = _cand(surface=classify.SURFACE_SAAS_CI, specs=[a, b])
    assert len(set(cand.spec_fingerprints)) == 2

    covered = {cand.spec_fingerprints[0]}
    result = _plan([cand], covered_fingerprints=covered)
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == ["merge-reports"]


def test_in_flight_agent_blocks_the_same_surface():
    # The 2026-07-23 case: consecutive runs, same cause, agent still working.
    cand = _cand()
    result = _plan([cand], inflight_keys={"main:sm-smoke-e2e"})
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_IN_FLIGHT


def test_in_flight_on_another_branch_does_not_block():
    result = _plan([_cand(base_ref="main")], inflight_keys={"stable/8.9:sm-smoke-e2e"})
    assert len(result.dispatches) == 1


def test_in_flight_on_another_surface_does_not_block():
    result = _plan([_cand()], inflight_keys={"main:saas-smoke-e2e"})
    assert len(result.dispatches) == 1


def test_fully_covered_candidate_is_suppressed():
    cand = _cand()
    result = _plan([cand], covered_fingerprints=set(cand.spec_fingerprints))
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_PR_COVERED


def test_partially_covered_candidate_dispatches_only_uncovered_specs():
    covered_spec = _spec(name="already fixed")
    fresh_spec = _spec(name="new failure")
    cand = _cand(specs=[covered_spec, fresh_spec])
    covered_fp = classify.spec_fingerprint(
        "main", classify.SURFACE_SM_E2E, covered_spec.file, covered_spec.test_name
    )

    result = _plan([cand], covered_fingerprints={covered_fp})
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == ["new failure"]


def test_product_bug_suppresses_the_candidate():
    cand = _cand()
    result = _plan([cand], product_bug_fingerprints=set(cand.spec_fingerprints))
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_PRODUCT_BUG


def test_candidate_with_no_specs_is_suppressed_as_no_evidence():
    result = _plan([_cand(specs=[])])
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_NO_EVIDENCE


def test_job_level_candidate_dispatches_without_specs():
    result = _plan([_cand(specs=[], job_level=True)])
    assert len(result.dispatches) == 1


def test_cap_limits_dispatches_and_records_the_rest():
    cands = [
        _cand(surface=classify.SURFACE_SM_E2E),
        _cand(surface=classify.SURFACE_SAAS_E2E),
    ]
    result = _plan(cands, max_dispatches=1)
    assert len(result.dispatches) == 1
    assert result.suppressed[0].reason == plan.SUPPRESSED_CAP


def test_in_flight_is_checked_before_cap_so_the_reason_is_useful():
    cands = [
        _cand(surface=classify.SURFACE_SM_E2E),
        _cand(surface=classify.SURFACE_SAAS_E2E),
    ]
    result = _plan(cands, inflight_keys={"main:sm-smoke-e2e"}, max_dispatches=1)
    reasons = {s.reason for s in result.suppressed}
    assert plan.SUPPRESSED_IN_FLIGHT in reasons
    assert len(result.dispatches) == 1
    assert result.dispatches[0].surface == classify.SURFACE_SAAS_E2E


# ---------------------------------------------------------------------------
# Fingerprint identity
# ---------------------------------------------------------------------------


def test_dispatch_key_shape():
    assert plan.dispatch_key("main", "sm-smoke-e2e") == "main:sm-smoke-e2e"


def test_job_level_candidate_uses_a_job_fingerprint():
    cand = _cand(specs=[], job_level=True)
    assert cand.fingerprints == [
        classify.job_fingerprint("main", classify.SURFACE_SM_E2E, cand.job_name)
    ]


def test_deterministic_specs_filter():
    cand = _cand(specs=[_spec("a"), _spec("b", deterministic=False)])
    assert [s.test_name for s in cand.deterministic_specs] == ["a"]


# ---------------------------------------------------------------------------
# Coverage block
# ---------------------------------------------------------------------------


def test_parse_coverage_block():
    body = "Some description\n\n<!-- alwaysgreen-fixed\nfp=aaaaaaaa\nfp=bbbbbbbb\n-->\n"
    assert plan.parse_coverage_block(body) == {"aaaaaaaa", "bbbbbbbb"}


def test_parse_returns_empty_when_absent():
    assert plan.parse_coverage_block("no block here") == set()
    assert plan.parse_coverage_block(None) == set()


def test_merge_appends_block_when_missing():
    merged = plan.merge_coverage_block("Body text", {"aaaaaaaa"})
    assert "fp=aaaaaaaa" in merged
    assert merged.startswith("Body text")


def test_merge_is_a_union_and_never_drops_existing():
    body = "Body\n\n<!-- alwaysgreen-fixed\nfp=aaaaaaaa\n-->\n"
    merged = plan.merge_coverage_block(body, {"bbbbbbbb"})
    assert plan.parse_coverage_block(merged) == {"aaaaaaaa", "bbbbbbbb"}


def test_merge_is_idempotent():
    body = plan.merge_coverage_block("Body", {"aaaaaaaa"})
    again = plan.merge_coverage_block(body, {"aaaaaaaa"})
    assert plan.parse_coverage_block(again) == {"aaaaaaaa"}
    assert again.count(plan.COVERAGE_BEGIN) == 1


def test_merge_preserves_text_after_the_block():
    body = "Head\n\n<!-- alwaysgreen-fixed\nfp=aaaaaaaa\n-->\n\nTail text"
    merged = plan.merge_coverage_block(body, {"bbbbbbbb"})
    assert "Head" in merged and "Tail text" in merged
    assert plan.parse_coverage_block(merged) == {"aaaaaaaa", "bbbbbbbb"}


def test_render_is_sorted_for_stable_diffs():
    rendered = plan.render_coverage_block({"cccccccc", "aaaaaaaa", "bbbbbbbb"})
    assert rendered.index("aaaaaaaa") < rendered.index("bbbbbbbb") < rendered.index("cccccccc")


def test_open_fix_pr_blocks_the_same_surface():
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan([cand], open_pr_keys={"main:sm-smoke-e2e"})
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_PR_OPEN]


def test_open_fix_pr_on_another_surface_does_not_block():
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan([cand], open_pr_keys={"main:saas-smoke-e2e"})
    assert len(result.dispatches) == 1


def test_open_fix_pr_blocks_even_when_the_body_claims_nothing():
    # The coverage block is agent-written, so an empty one must not let a second
    # agent through while the first PR is still open.
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan([cand], covered_fingerprints=set(), open_pr_keys={"main:sm-smoke-e2e"})
    assert result.dispatches == []
