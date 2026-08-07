"""Unit tests for AlwaysGreen dispatch planning and the PR coverage block."""

from __future__ import annotations

import classify
import plan


def _spec(name="t", file="tests/SM-8.10/smoke-tests.spec.ts", deterministic=True):
    statuses = ["failed"] * 3 if deterministic else ["failed", "passed"]
    return classify.FailingSpec(file=file, test_name=name, statuses=statuses)


def _cand(
    surface=classify.SURFACE_SM_E2E,
    base_ref="main",
    specs=None,
    job_level=False,
    source="",
):
    return plan.Candidate(
        base_ref=base_ref,
        surface=surface,
        job_name="Playwright e2e after install - install on gke - agrn (1 of 1)",
        source=source,
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
    result = _plan([_cand(surface=classify.SURFACE_HELM_INSTALL)])
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_NOT_DISPATCHABLE


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


# ---------------------------------------------------------------------------
# Source namespacing
# ---------------------------------------------------------------------------


def test_dispatch_key_is_namespaced_by_source():
    assert (
        plan.dispatch_key("main", "sm-smoke-e2e", "connectors")
        == "connectors:main:sm-smoke-e2e"
    )


def test_two_sources_on_the_same_branch_do_not_suppress_each_other():
    # Both pipelines open PRs into the same e2e repository and both call their branch
    # "main", so an un-namespaced key would make either one's agent block the other's.
    connectors = _cand(source="connectors")
    monorepo = _cand(source="camunda")
    assert connectors.key != monorepo.key

    result = _plan([monorepo], inflight_keys={connectors.key})
    assert len(result.dispatches) == 1


def test_key_labels_fit_githubs_length_limit():
    # A label over the limit is rejected silently by `gh label create`, and the missing
    # label disables dedupe instead of failing the run — so this is asserted, not hoped.
    sources = ("camunda", "connectors", "camunda-operator")
    base_refs = ("main", "stable/8.7", "stable/8.8", "stable/8.9", "stable/8.10")
    for source in sources:
        for base_ref in base_refs:
            for surface in sorted(classify.DISPATCHABLE_SURFACES):
                label = plan.KEY_LABEL_PREFIX + plan.dispatch_key(
                    base_ref, surface, source
                )
                assert len(label) <= plan.MAX_LABEL_LENGTH, label


# ---------------------------------------------------------------------------
# Fifth dedupe layer: spec paths already open in a PR
# ---------------------------------------------------------------------------


def test_open_pr_touching_a_spec_path_suppresses_the_candidate():
    cand = _cand(specs=[_spec(file="tests/8.10/smoke-tests.spec.ts")])
    result = _plan([cand], claimed_paths={"tests/8.10/smoke-tests.spec.ts": 2951})
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_PATH_CLAIMED
    assert "#2951" in result.suppressed[0].detail


def test_a_sibling_version_path_does_not_shadow_the_failing_one():
    # Matching on basenames would make an open PR against 8.9 suppress an 8.10 failure.
    cand = _cand(specs=[_spec(file="tests/8.10/smoke-tests.spec.ts")])
    result = _plan([cand], claimed_paths={"tests/8.9/smoke-tests.spec.ts": 2951})
    assert len(result.dispatches) == 1


def test_job_level_candidates_are_not_path_claimed():
    cand = _cand(specs=[], job_level=True)
    result = _plan([cand], claimed_paths={"tests/8.10/smoke-tests.spec.ts": 1})
    assert len(result.dispatches) == 1


def test_path_claim_is_checked_after_evidence_so_the_reason_is_useful():
    cand = _cand(specs=[])
    result = _plan([cand], claimed_paths={"tests/8.10/smoke-tests.spec.ts": 1})
    assert result.suppressed[0].reason == plan.SUPPRESSED_NO_EVIDENCE
