"""Unit tests for AlwaysGreen dispatch planning and the PR coverage block."""

from __future__ import annotations

import pathlib
import re
from datetime import datetime, timedelta, timezone

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
    job_name="Playwright e2e after install - install on gke - agrn (1 of 1)",
):
    return plan.Candidate(
        base_ref=base_ref,
        surface=surface,
        job_name=job_name,
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


def test_recent_no_fix_verdict_suppresses_the_candidate():
    # An agent already investigated this inside the cooldown and could not fix it
    # safely; its own uploaded manifest is the only record, since no PR was opened.
    cand = _cand()
    result = _plan([cand], recent_no_fix_fingerprints=set(cand.spec_fingerprints))
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_RECENT_NO_FIX


def test_no_fix_spec_does_not_block_a_new_failure_beside_it():
    tracked = _spec(name="unfixable")
    fresh = _spec(name="new failure")
    cand = _cand(specs=[tracked, fresh])
    tracked_fp = classify.spec_fingerprint(
        "main", classify.SURFACE_SM_E2E, tracked.file, tracked.test_name
    )

    result = _plan([cand], recent_no_fix_fingerprints={tracked_fp})
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == ["new failure"]


def test_no_fix_defaults_to_nothing_suppressed():
    # The argument is optional so callers that predate it keep dispatching.
    result = plan.plan_dispatches(
        [_cand()],
        covered_fingerprints=set(),
        inflight_keys=set(),
        open_pr_keys=set(),
        product_bug_fingerprints=set(),
    )
    assert len(result.dispatches) == 1


def test_fixed_upstream_suppresses_the_candidate():
    # The run executed the published package, and the fix merged after it started.
    cand = _cand()
    result = _plan([cand], fixed_upstream_fingerprints=set(cand.spec_fingerprints))
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_FIXED_UPSTREAM


def test_fixed_upstream_spec_does_not_block_a_new_failure_beside_it():
    stale = _spec(name="already fixed upstream")
    fresh = _spec(name="new failure")
    cand = _cand(specs=[stale, fresh])
    stale_fp = classify.spec_fingerprint(
        "main", classify.SURFACE_SM_E2E, stale.file, stale.test_name
    )

    result = _plan([cand], fixed_upstream_fingerprints={stale_fp})
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == ["new failure"]


# ---------------------------------------------------------------------------
# Base refs the fix agent accepts
# ---------------------------------------------------------------------------


def test_unsupported_base_ref_is_not_dispatched():
    # The fix workflow rejects anything outside its whitelist on its first step, so
    # dispatching it spends a runner to produce a confusing failure (run 31115770750
    # on ci/alwaysgreen-helm-live-check).
    result = _plan([_cand(base_ref="ci/alwaysgreen-helm-live-check")])
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_UNSUPPORTED_REF
    assert result.suppressed[0].detail == "ci/alwaysgreen-helm-live-check"


def test_every_supported_base_ref_is_dispatchable():
    for ref in plan.SUPPORTED_BASE_REFS:
        result = _plan([_cand(base_ref=ref)])
        assert len(result.dispatches) == 1, ref


def test_supported_base_refs_match_the_fix_workflow_whitelist():
    # These two lists drifted the moment stable/8.10 was cut: the branch got the whole
    # pipeline and started running AlwaysGreen, while both whitelists still ended at
    # stable/8.9, so every 8.10 failure was withheld. Assert them equal rather than
    # trusting a comment to keep them in step.
    workflow = (
        pathlib.Path(__file__).resolve().parents[2]
        / "workflows"
        / "alwaysgreen-fix.yml"
    ).read_text()
    # Tolerant of formatting so this fails on drift and not on a reflow: `[^)]` spans
    # newlines for a wrapped list, whitespace is allowed around each `|`, and every
    # token is stripped before comparing.
    match = re.search(r"^\s*(main\s*\|[^)]*)\)\s*;;", workflow, re.M)
    assert match, "no base_ref case statement found in alwaysgreen-fix.yml"
    refs = {token.strip() for token in match.group(1).split("|")} - {""}
    assert refs == set(plan.SUPPORTED_BASE_REFS)


def test_unsupported_ref_is_reported_before_any_other_reason():
    # Checked first so the summary names the real blocker rather than the cap.
    result = _plan(
        [_cand(base_ref="feature/x"), _cand(surface=classify.SURFACE_SAAS_E2E)],
        max_dispatches=1,
    )
    assert result.suppressed[0].reason == plan.SUPPRESSED_UNSUPPORTED_REF


# ---------------------------------------------------------------------------
# Suite of a spec
# ---------------------------------------------------------------------------


def test_spec_suite_from_a_mapped_spec_path():
    assert plan.spec_suite("tests/SM-8.10/smoke-tests.spec.ts") == "SM-8.10"
    assert plan.spec_suite("tests/8.9/smoke-tests.spec.ts") == "8.9"


def test_spec_suite_is_none_for_anything_else():
    # A saas-ci synthetic spec carries a workflow path, and a bare filename means the
    # report gave no rootDir to map from.
    assert plan.spec_suite(".github/workflows/playwright_saas.yml") is None
    assert plan.spec_suite("smoke-tests.spec.ts") is None
    assert plan.spec_suite("") is None
    assert plan.spec_suite("tests/") is None


# ---------------------------------------------------------------------------
# Reading a past run's verdict off its own artifacts
# ---------------------------------------------------------------------------


def test_verdict_of_a_run_that_opened_nothing_suppresses_its_fingerprints():
    assert plan.verdict_fingerprints(
        {"category": "not-determined", "prs": []}, ["76a3348c", "2e9d6176"]
    ) == {"76a3348c", "2e9d6176"}


def test_verdict_of_a_run_that_opened_a_pr_suppresses_nothing():
    # Already covered by that PR's own coverage block; double-counting it here would
    # keep suppressing after the PR is closed or merged.
    assert plan.verdict_fingerprints({"prs": [{"number": 1}]}, ["76a3348c"]) == set()


def test_crashed_run_suppresses_nothing():
    # No manifest is an infrastructure failure, not a verdict, and must not be
    # mistaken for "we looked and there was nothing to do".
    assert plan.verdict_fingerprints(None, ["76a3348c"]) == set()
    assert plan.verdict_fingerprints("not json", ["76a3348c"]) == set()
    assert plan.verdict_fingerprints({"prs": "broken"}, ["76a3348c"]) == set()


def test_malformed_prs_shapes_suppress_nothing():
    # A readable manifest whose `prs` is missing, null or an object must not be read
    # as "opened nothing": only a genuine empty list is a no-fix verdict.
    for meta in ({}, {"prs": None}, {"prs": {}}, {"other": 1}):
        assert plan.verdict_fingerprints(meta, ["76a3348c"]) == set(), meta


def test_verdict_without_fingerprints_suppresses_nothing():
    assert plan.verdict_fingerprints({"prs": []}, None) == set()
    assert plan.verdict_fingerprints({"prs": []}, []) == set()


def test_verdict_fingerprints_are_normalised_and_deduped():
    assert plan.verdict_fingerprints(
        {"prs": []}, ["76a3348c", "76a3348c", "", None]
    ) == {"76a3348c"}


def test_mixed_suppression_sources_are_named_not_reported_as_pr_coverage():
    # Each spec is dropped by a different source. Reporting the whole candidate as
    # "open-pr-covers-all-specs" would hide the other sources from the summary.
    by_pr = _spec(name="covered by pr")
    by_upstream = _spec(name="fixed upstream")
    cand = _cand(specs=[by_pr, by_upstream])
    fp = {
        s.test_name: classify.spec_fingerprint(
            "main", classify.SURFACE_SM_E2E, s.file, s.test_name
        )
        for s in (by_pr, by_upstream)
    }

    result = _plan(
        [cand],
        covered_fingerprints={fp["covered by pr"]},
        fixed_upstream_fingerprints={fp["fixed upstream"]},
    )
    assert result.dispatches == []
    assert result.suppressed[0].reason == plan.SUPPRESSED_ALL_ACCOUNTED
    assert result.suppressed[0].detail == ",".join(
        sorted({plan.SUPPRESSED_PR_COVERED, plan.SUPPRESSED_FIXED_UPSTREAM})
    )


def test_single_suppression_source_keeps_its_own_reason():
    # Only one source accounts for every spec, so the specific reason survives.
    a, b = _spec(name="a"), _spec(name="b")
    cand = _cand(specs=[a, b])
    fps = {
        classify.spec_fingerprint("main", classify.SURFACE_SM_E2E, s.file, s.test_name)
        for s in (a, b)
    }
    # Partial overlap with a second source would otherwise be indistinguishable.
    result = _plan([cand], covered_fingerprints=fps)
    assert result.suppressed[0].reason == plan.SUPPRESSED_PR_COVERED
    assert result.suppressed[0].detail == ""


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
# Spec dedupe
# ---------------------------------------------------------------------------


def test_dedupe_collapses_the_same_spec_from_two_reports():
    # SaaS 8.8/8.9 publish json-report-v1 and json-report-v2; discover concatenates
    # both, so a spec failing in each generation arrives twice.
    a, b = _spec(name="flow"), _spec(name="flow")
    assert [s.test_name for s in plan.dedupe_specs([a, b])] == ["flow"]


def test_dedupe_keeps_distinct_tests_and_distinct_files():
    specs = [
        _spec(name="flow"),
        _spec(name="connector"),
        _spec(name="flow", file="tests/8.9/other.spec.ts"),
    ]
    assert len(plan.dedupe_specs(specs)) == 3


def test_dedupe_preserves_order():
    specs = [_spec(name="b"), _spec(name="a"), _spec(name="b")]
    assert [s.test_name for s in plan.dedupe_specs(specs)] == ["b", "a"]


def test_duplicated_specs_are_dispatched_once():
    # Run 31016165246 dispatched two failing tests as four, with each fingerprint
    # repeated, because nothing deduped across the two reports.
    cand = _cand(specs=[_spec(name="flow"), _spec(name="connector")] * 2)
    result = _plan([cand])
    dispatched = result.dispatches[0]
    assert [s.test_name for s in dispatched.specs] == ["flow", "connector"]
    assert len(dispatched.fingerprints) == len(set(dispatched.fingerprints)) == 2


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


def test_open_fix_pr_with_a_coverage_block_does_not_block_an_unclaimed_spec():
    # Run 33605992250: c8-cross-component-e2e-tests#3267 claimed the cluster-creation
    # setup specs on `main:saas-smoke-e2e`, and an unrelated smoke-test failure on the
    # same surface was suppressed instead of dispatched.
    claimed = _spec(name="Create AWS Cluster", file="tests/8.10/test-setup.spec.ts")
    fresh = _spec(
        name="Most Common Flow User Flow With All Apps",
        file="tests/8.10/smoke-tests.spec.ts",
    )
    cand = _cand(surface=classify.SURFACE_SAAS_E2E, specs=[claimed, fresh])
    claimed_fp = classify.spec_fingerprint(
        "main", classify.SURFACE_SAAS_E2E, claimed.file, claimed.test_name
    )

    result = _plan(
        [cand],
        covered_fingerprints={claimed_fp},
        open_pr_keys={"main:saas-smoke-e2e"},
        open_pr_keys_with_coverage={"main:saas-smoke-e2e"},
    )
    assert len(result.dispatches) == 1
    assert [s.test_name for s in result.dispatches[0].specs] == [fresh.test_name]


def test_open_fix_pr_with_a_coverage_block_still_suppresses_the_specs_it_claims():
    cand = _cand(surface=classify.SURFACE_SAAS_E2E)
    result = _plan(
        [cand],
        covered_fingerprints=set(cand.spec_fingerprints),
        open_pr_keys={"main:saas-smoke-e2e"},
        open_pr_keys_with_coverage={"main:saas-smoke-e2e"},
    )
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_PR_COVERED]


def test_a_second_holder_without_a_coverage_block_keeps_the_surface_locked():
    # `keys_with_coverage` is the intersection over holders, so one PR that published
    # nothing still locks the surface even beside one that published a block.
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan(
        [cand],
        open_pr_keys={"main:sm-smoke-e2e"},
        open_pr_keys_with_coverage=set(),
    )
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_PR_OPEN]


def test_in_flight_agent_still_blocks_a_coverage_declaring_surface():
    # Narrowing the PR lock must not touch the concurrency rule: one agent per key.
    cand = _cand(surface=classify.SURFACE_SM_E2E)
    result = _plan(
        [cand],
        inflight_keys={"main:sm-smoke-e2e"},
        open_pr_keys={"main:sm-smoke-e2e"},
        open_pr_keys_with_coverage={"main:sm-smoke-e2e"},
    )
    assert result.dispatches == []
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_IN_FLIGHT]


# ---------------------------------------------------------------------------
# PR lock expiry
# ---------------------------------------------------------------------------

NOW = datetime(2026, 8, 26, 12, 0, tzinfo=timezone.utc)


def _ago(**kw):
    return (NOW - timedelta(**kw)).isoformat().replace("+00:00", "Z")


def test_fresh_fix_pr_keeps_holding_its_key():
    assert plan.pr_lock_expired(_ago(hours=6), NOW, 2) is False


def test_fix_pr_past_the_ttl_releases_its_key():
    # camunda-platform-helm#6927 held main:sm-smoke-e2e unreviewed from 2026-08-20,
    # and every main triage for six days dispatched nothing.
    assert plan.pr_lock_expired("2026-08-20T14:56:44Z", NOW, 2) is True


def test_ttl_boundary_is_inclusive_of_the_lock():
    assert plan.pr_lock_expired(_ago(days=2), NOW, 2) is False
    assert plan.pr_lock_expired(_ago(days=2, minutes=1), NOW, 2) is True


def test_unreadable_timestamp_keeps_the_lock():
    for value in ("", None, "yesterday", "2026-13-45T00:00:00Z"):
        assert plan.pr_lock_expired(value, NOW, 2) is False


def test_naive_created_at_is_read_as_utc():
    assert plan.pr_lock_expired("2026-08-20T14:56:44", NOW, 2) is True


def test_naive_now_does_not_raise_against_an_offset_aware_created_at():
    naive_now = NOW.replace(tzinfo=None)
    assert plan.pr_lock_expired("2026-08-20T14:56:44Z", naive_now, 2) is True
    assert plan.pr_lock_expired(_ago(hours=6), naive_now, 2) is False


def test_zero_ttl_restores_the_never_expiring_lock():
    assert plan.pr_lock_expired("2026-01-01T00:00:00Z", NOW, 0) is False


# ---------------------------------------------------------------------------
# Same-key merge
# ---------------------------------------------------------------------------


def test_two_jobs_of_one_surface_dispatch_a_single_agent():
    # The 2026-08-26 shape: "Playwright e2e full after install" and "Playwright e2e
    # smoke after install" are both sm-smoke-e2e, so triage reported them as x2 and
    # would have dispatched two agents onto one key.
    result = _plan([
        _cand(job_name="Playwright e2e full after install - agrn (1 of 1)"),
        _cand(job_name="Playwright e2e smoke after install - agrn (1 of 1)"),
    ])
    assert len(result.dispatches) == 1
    assert result.suppressed == []


def test_merge_unions_specs_across_jobs_without_duplicating_them():
    merged = plan.merge_by_key([
        _cand(job_name="full", specs=[_spec("flow"), _spec("shared")]),
        _cand(job_name="smoke", specs=[_spec("shared"), _spec("connector")]),
    ])
    assert len(merged) == 1
    result = _plan(merged)
    names = [s.test_name for s in result.dispatches[0].specs]
    assert names == ["flow", "shared", "connector"]


def test_merge_records_the_folded_in_job_names():
    merged = plan.merge_by_key([_cand(job_name="full"), _cand(job_name="smoke")])
    assert merged[0].job_names == ["full", "smoke"]


def test_merged_job_level_candidate_claims_a_fingerprint_per_job():
    # One helm-install job per matrix cell: a merged dispatch must still cover both,
    # or the uncovered one is re-dispatched on the next failing run.
    merged = plan.merge_by_key([
        _cand(surface=classify.SURFACE_HELM_INSTALL, specs=[], job_level=True,
              job_name="install for install on gke - agrn"),
        _cand(surface=classify.SURFACE_HELM_INSTALL, specs=[], job_level=True,
              job_name="install for install on gke - esss"),
    ])
    assert merged[0].fingerprints == [
        classify.job_fingerprint("main", classify.SURFACE_HELM_INSTALL, n)
        for n in ("install for install on gke - agrn", "install for install on gke - esss")
    ]


def test_merge_leaves_distinct_keys_alone_and_keeps_order():
    merged = plan.merge_by_key([
        _cand(surface=classify.SURFACE_SAAS_E2E),
        _cand(surface=classify.SURFACE_SM_E2E),
        _cand(surface=classify.SURFACE_SM_E2E, base_ref="stable/8.9"),
    ])
    assert [c.key for c in merged] == [
        "main:saas-smoke-e2e", "main:sm-smoke-e2e", "stable/8.9:sm-smoke-e2e"
    ]


def test_merged_candidate_is_suppressed_once_not_per_job():
    result = _plan(
        [_cand(job_name="full"), _cand(job_name="smoke")],
        open_pr_keys={"main:sm-smoke-e2e"},
    )
    assert [s.reason for s in result.suppressed] == [plan.SUPPRESSED_PR_OPEN]


def test_merged_candidate_consumes_one_slot_of_the_cap():
    result = _plan(
        [
            _cand(job_name="full"),
            _cand(job_name="smoke"),
            _cand(surface=classify.SURFACE_SAAS_E2E),
        ],
        max_dispatches=2,
    )
    assert [c.surface for c in result.dispatches] == [
        classify.SURFACE_SM_E2E, classify.SURFACE_SAAS_E2E
    ]
    assert result.suppressed == []
