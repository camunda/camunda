"""Unit tests for the evidence and dedupe snapshot discover hands to the planner.

`test_plan.py` passes `open_pr_keys_with_coverage` in ready-made, so it asserts what
`plan` does with the answer, never how it is computed. The derivation is where the
"does this whole surface stay locked" decision is actually made — per-PR key grouping,
the TTL skip, and the intersection over holders — so it is tested here against a stubbed
`open_fix_prs`.
"""

from __future__ import annotations

import json
from datetime import datetime, timedelta, timezone

import classify
import discover
import plan as planning

NOW = datetime.now(timezone.utc)


def _ago(**kw):
    return (NOW - timedelta(**kw)).isoformat().replace("+00:00", "Z")


def _pr(number, keys, *, claims=(), age_hours=0, mergeable="MERGEABLE"):
    body = ""
    if claims:
        body = f"Fixes.\n\n{planning.render_coverage_block(set(claims))}\n"
    return {
        "number": number,
        "body": body,
        "createdAt": _ago(hours=age_hours),
        "labels": [{"name": f"{discover.KEY_LABEL_PREFIX}{k}"} for k in keys],
        "mergeable": mergeable,
    }


def _stub(monkeypatch, prs, *, ok=True):
    """Serve `prs` from the first fix repo and nothing from the rest.

    Keyed on the repo, not on a call counter: a counter is consumed by the first
    `dedupe_inputs` pass over FIX_PR_REPOS, so a second call in the same test would be
    served empty lists and any assertion about it would pass vacuously.
    """

    def fake(repo):
        return (list(prs), ok) if repo == discover.FIX_PR_REPOS[0] else ([], ok)

    monkeypatch.setattr(discover, "open_fix_prs", fake)


def test_a_claiming_holder_frees_its_key_for_other_specs(monkeypatch):
    _stub(monkeypatch, [_pr(1, ["main:saas-smoke-e2e"], claims=["aaaaaaaa"])])
    covered, keys, per_spec, _refs, ok = discover.dedupe_inputs()
    assert ok is True
    assert covered == {"aaaaaaaa"}
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == {"main:saas-smoke-e2e"}


def test_a_holder_claiming_nothing_keeps_its_key_locked(monkeypatch):
    _stub(monkeypatch, [_pr(1, ["main:saas-smoke-e2e"])])
    covered, keys, per_spec, _refs, ok = discover.dedupe_inputs()
    assert covered == set()
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == set()


def test_an_empty_coverage_block_claims_nothing(monkeypatch):
    # The marker alone is not a statement of remit, so it must not free the surface.
    pr = _pr(1, ["main:saas-smoke-e2e"])
    pr["body"] = f"Fixes.\n\n{planning.COVERAGE_BEGIN}\nfp=\n{planning.COVERAGE_END}\n"
    _stub(monkeypatch, [pr])
    _covered, keys, per_spec, _refs, _ok = discover.dedupe_inputs()
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == set()


def test_one_non_claiming_holder_locks_a_key_another_holder_claims(monkeypatch):
    # The intersection over holders: this is the case a per-PR view gets wrong.
    _stub(
        monkeypatch,
        [
            _pr(1, ["main:saas-smoke-e2e"], claims=["aaaaaaaa"]),
            _pr(2, ["main:saas-smoke-e2e"]),
        ],
    )
    covered, keys, per_spec, _refs, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == set()


def test_an_expired_non_claiming_holder_does_not_lock_a_claiming_one(monkeypatch):
    # Only ACTIVE holders decide the key, so an expired PR cannot veto a fresh one.
    _stub(
        monkeypatch,
        [
            _pr(1, ["main:saas-smoke-e2e"], claims=["aaaaaaaa"]),
            _pr(2, ["main:saas-smoke-e2e"], age_hours=99),
        ],
    )
    _covered, keys, per_spec, _refs, _ok = discover.dedupe_inputs()
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == {"main:saas-smoke-e2e"}


def test_an_expired_holder_releases_its_key_but_keeps_its_claims(monkeypatch):
    # The specs a PR claims stay claimed while it is open; only the coarse key lock is
    # time-bound, so the failure it fixed is still suppressed per spec.
    _stub(monkeypatch, [_pr(1, ["main:saas-smoke-e2e"], claims=["aaaaaaaa"], age_hours=99)])
    covered, keys, per_spec, _refs, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == set()
    assert per_spec == set()


def test_a_pr_carrying_two_key_labels_holds_both(monkeypatch):
    _stub(
        monkeypatch,
        [_pr(1, ["main:saas-smoke-e2e", "stable/8.10:saas-smoke-e2e"], claims=["aaaaaaaa"])],
    )
    _covered, keys, per_spec, _refs, _ok = discover.dedupe_inputs()
    assert keys == {"main:saas-smoke-e2e", "stable/8.10:saas-smoke-e2e"}
    assert per_spec == keys


def test_a_pr_with_no_key_label_still_contributes_its_claims(monkeypatch):
    # A fix PR whose key label was never stamped: it locks nothing, but the specs it
    # claims must still suppress a repeat.
    _stub(monkeypatch, [_pr(1, [], claims=["aaaaaaaa"])])
    covered, keys, per_spec, _refs, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == set()
    assert per_spec == set()


def test_a_failed_lookup_reports_not_ok(monkeypatch):
    # Coverage and keys are one snapshot behind one `ok`. A partial read must not let
    # the caller skip the coarse lock while believing nothing is claimed.
    _stub(monkeypatch, [_pr(1, ["main:saas-smoke-e2e"], claims=["aaaaaaaa"])], ok=False)
    _covered, _keys, _per_spec, _refs, ok = discover.dedupe_inputs()
    assert ok is False


# ---------------------------------------------------------------------------
# Stale (CONFLICTING) fix PRs
# ---------------------------------------------------------------------------


def test_a_conflicting_holders_claim_does_not_count_as_covered(monkeypatch):
    # c8-cross-component-e2e-tests#3154: claimed three fingerprints, then sat
    # CONFLICTING for two weeks while every nightly hitting those specs was
    # suppressed as already covered.
    _stub(
        monkeypatch,
        [_pr(1, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"], mergeable="CONFLICTING")],
    )
    covered, keys, per_spec, _refs, _ok = discover.dedupe_inputs()
    assert covered == set()
    # The key still counts as claiming something, so the coarse per-surface lock
    # still lifts for its neighbours; only the specific (untrustworthy) claim is
    # dropped.
    assert keys == {"main:sm-smoke-e2e"}
    assert per_spec == {"main:sm-smoke-e2e"}


def test_a_conflicting_holder_beside_a_healthy_one_still_covers_the_spec(monkeypatch):
    # Two PRs holding the same key, one stale and one not: the healthy PR's claim
    # must still count even though its stale sibling's does not.
    _stub(
        monkeypatch,
        [
            _pr(1, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"], mergeable="CONFLICTING"),
            _pr(2, ["main:sm-smoke-e2e"], claims=["bbbbbbbb"]),
        ],
    )
    covered, _keys, _per_spec, _refs, _ok = discover.dedupe_inputs()
    assert covered == {"bbbbbbbb"}


def test_an_unknown_mergeable_state_still_counts_as_covered(monkeypatch):
    # GitHub has not finished computing mergeability yet; must not be read as broken.
    _stub(
        monkeypatch,
        [_pr(1, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"], mergeable="UNKNOWN")],
    )
    covered, _keys, _per_spec, _refs, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}


# ---------------------------------------------------------------------------
# Per-matrix-leg evidence
# ---------------------------------------------------------------------------


def _playwright_report(spec_file, title):
    return {
        "config": {"rootDir": "/home/runner/work/e2e-tests/tests/SM-8.10"},
        "suites": [
            {
                "specs": [],
                "suites": [
                    {
                        "specs": [
                            {
                                "file": spec_file,
                                "title": title,
                                "ok": False,
                                "tests": [
                                    {"results": [{"status": "failed"}]},
                                ],
                            }
                        ]
                    }
                ],
            }
        ],
    }


def _stub_downloads(monkeypatch, reports_by_pattern):
    """Serve one Playwright report per artifact pattern, into the caller's dir."""
    seen = []

    def fake(run_id, repo, pattern, dest):
        seen.append((pattern, dest))
        report = reports_by_pattern.get(pattern)
        if report is None:
            return False
        dest.mkdir(parents=True, exist_ok=True)
        (dest / "playwright-results.json").write_text(json.dumps(report))
        return True

    monkeypatch.setattr(discover, "download_artifacts", fake)
    return seen


def test_preview_env_legs_read_their_own_artifact(monkeypatch, tmp_path):
    # preview-env-smoke-test.yml uploads one JSON report per version in a single
    # run. Downloading a shared `playwright-results-json*` blob would give every
    # failing leg every version's failures.
    seen = _stub_downloads(
        monkeypatch,
        {
            "playwright-results-json-8.9-*": _playwright_report(
                "smoke-tests.spec.js", "8.9 broke"
            ),
            "playwright-results-json-8.10-*": _playwright_report(
                "smoke-tests.spec.js", "8.10 broke"
            ),
        },
    )

    first = discover.sm_candidates("1", "stable/8.9", "Run 8.9 Smoke Tests", tmp_path)
    second = discover.sm_candidates("1", "stable/8.10", "Run 8.10 Smoke Tests", tmp_path)

    assert [s.test_name for s in first.specs] == ["8.9 broke"]
    assert [s.test_name for s in second.specs] == ["8.10 broke"]
    # Distinct download directories, or the second leg would re-read the first's
    # report alongside its own.
    assert seen[0][1] != seen[1][1]
    # Distinct dispatch keys, so neither leg's fix displaces the other's.
    assert first.key != second.key


def test_helm_chart_sm_job_keeps_the_shared_artifact_pattern(monkeypatch, tmp_path):
    seen = _stub_downloads(
        monkeypatch,
        {"playwright-results-json*": _playwright_report("smoke-tests.spec.js", "boom")},
    )

    cand = discover.sm_candidates(
        "1",
        "main",
        "Helm chart Integration Tests / agrn - install - gke / "
        "Playwright e2e after install - install on gke - agrn (1 of 1)",
        tmp_path,
    )

    assert seen[0][0] == "playwright-results-json*"
    assert [s.test_name for s in cand.specs] == ["boom"]


def test_serialise_resolves_blame_per_dispatch_base_ref():
    # preview-env-smoke-test.yml dispatches against several branches from one
    # run, so a single run-wide blame would name a main-branch PR as the cause
    # of a stable/8.9 failure. Each dispatch must get blame for its own ref.
    main_blame = classify.Blame(
        reviewer="main-author", author="main-author", pr_number=1, via="pr-author"
    )
    stable_blame = classify.Blame(
        reviewer="stable-author", author="stable-author", pr_number=2, via="pr-author"
    )
    resolved = {"main": main_blame, "stable/8.9": stable_blame}

    result = planning.Plan(
        dispatches=[
            planning.Candidate(
                base_ref="main",
                surface=classify.SURFACE_SM_E2E,
                job_name="Run 8.11 Smoke Tests",
            ),
            planning.Candidate(
                base_ref="stable/8.9",
                surface=classify.SURFACE_SM_E2E,
                job_name="Run 8.9 Smoke Tests",
            ),
        ]
    )

    payload = discover.serialise(
        result, main_blame, "1", blame_for_ref=lambda ref: resolved[ref]
    )

    dispatches_by_ref = {d["base_ref"]: d for d in payload["dispatches"]}
    assert dispatches_by_ref["main"]["blame"]["author"] == "main-author"
    assert dispatches_by_ref["stable/8.9"]["blame"]["author"] == "stable-author"


def test_resolve_blame_for_ref_uses_the_refs_own_tip_commit(monkeypatch):
    # branch_tip_sha resolves "stable/8.9" to its own tip, not the calling run's
    # head_sha, so resolve_blame_for_ref attributes the failure to whichever PR
    # actually landed on that branch.
    calls = []

    def fake_gh_json(args, default):
        calls.append(args)
        if args[:2] == ["api", f"repos/{discover.REPO}/commits/stable/8.9"]:
            return {"sha": "stable-tip-sha"}
        if args[:2] == [
            "api",
            f"repos/{discover.REPO}/commits/stable-tip-sha/pulls",
        ]:
            return [{"merge_commit_sha": "stable-tip-sha", "number": 9, "user": {"login": "stable-author"}}]
        return default

    monkeypatch.setattr(discover, "gh_json", fake_gh_json)

    blame = discover.resolve_blame_for_ref("stable/8.9")

    assert blame.author == "stable-author"
    assert blame.pr_number == 9


# ---------------------------------------------------------------------------
# Downstream CI evidence
# ---------------------------------------------------------------------------


def _job(name, conclusion, *, steps=()):
    return {
        "name": name,
        "conclusion": conclusion,
        "check_run_url": f"https://api.github.com/repos/x/check-runs/{name}",
        "steps": [{"name": n, "conclusion": c} for n, c in steps],
    }


def _stub_downstream(monkeypatch, *, jobs, annotations_by_job=None):
    """Serve one downstream run, its single attempt's jobs, and their annotations."""
    annotations_by_job = annotations_by_job or {}

    def fake(args, default):
        target = args[-1]
        if "/jobs" in target:
            return {"jobs": list(jobs)}
        return {
            "path": ".github/workflows/playwright_saas_pr_trigger_monorepo.yml",
            "run_attempt": 1,
        }

    monkeypatch.setattr(discover, "gh_json", fake)
    monkeypatch.setattr(
        discover,
        "failure_annotations",
        lambda url: list(annotations_by_job.get((url or "").rsplit("/", 1)[-1], [])),
    )


#: What GitHub annotates a job it could never place on a runner with.
NOT_ACQUIRED = (
    "The job was not acquired by Runner of type hosted even after multiple attempts"
)

FINALIZE = "Finalize SaaS E2E Smoke Tests Check"


def test_a_queued_job_github_never_placed_is_the_evidence(monkeypatch):
    # The shape that went undispatched: every spec passed, no job reported a
    # failure, and the run is red only because `finalize-check-run` sat queued
    # until GitHub gave up on it. Withholding leaves main red with no agent.
    _stub_downstream(
        monkeypatch,
        jobs=[
            _job("Run tests (chromium-v2)", "success"),
            _job(FINALIZE, "cancelled"),
        ],
        annotations_by_job={FINALIZE: [NOT_ACQUIRED]},
    )
    specs = discover.downstream_ci_specs("1")
    assert [s.test_name for s in specs] == [FINALIZE]
    assert specs[0].file.endswith("playwright_saas_pr_trigger_monorepo.yml")
    assert "cancelled" in specs[0].error


def test_a_job_cancelled_part_way_through_a_step_stays_noise(monkeypatch):
    # Somebody cancelled the run. The job got a runner and ran, so the fallback
    # picks it up, and only the annotation tells it apart from one that stalled.
    _stub_downstream(
        monkeypatch,
        jobs=[
            _job(FINALIZE, "cancelled", steps=[("Mark check as passed", "cancelled")])
        ],
        annotations_by_job={FINALIZE: ["The operation was canceled."]},
    )
    assert discover.downstream_ci_specs("1") == []


def test_a_stalled_job_with_nothing_to_show_stays_noise(monkeypatch):
    _stub_downstream(monkeypatch, jobs=[_job(FINALIZE, "cancelled")])
    assert discover.downstream_ci_specs("1") == []


def test_a_cancelled_job_beside_a_failing_one_is_not_evidence(monkeypatch):
    # The cancellation follows from the failure, so naming it too would dispatch
    # the agent at a job that has nothing wrong with it.
    _stub_downstream(
        monkeypatch,
        jobs=[
            _job("lint", "failure", steps=[("Run eslint", "failure")]),
            _job(FINALIZE, "cancelled"),
        ],
        annotations_by_job={FINALIZE: [NOT_ACQUIRED]},
    )
    specs = discover.downstream_ci_specs("1")
    assert [s.test_name for s in specs] == ["lint"]
    assert specs[0].error == "CI job failed at step: Run eslint"


def test_a_failing_job_with_no_steps_and_no_annotation_stays_noise(monkeypatch):
    _stub_downstream(
        monkeypatch, jobs=[_job("Create cluster generation on INT", "failure")]
    )
    assert discover.downstream_ci_specs("1") == []


# ---------------------------------------------------------------------------
# References: which PR or issue accounts for a suppressed failure
# ---------------------------------------------------------------------------
#
# The notifier tests hand-build these maps, so without producer-side assertions here a
# regression could drop or misassign every PR link in Slack and leave the suite green.


def test_a_claimed_fingerprint_reports_the_pr_that_claimed_it(monkeypatch):
    _stub(monkeypatch, [_pr(3951, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"])])

    _covered, _keys, _per_spec, refs, _ok = discover.dedupe_inputs()

    repo = discover.FIX_PR_REPOS[0]
    assert refs["covered_by"] == {"aaaaaaaa": f"{repo}#3951"}


def test_the_first_claimant_is_the_one_reported(monkeypatch):
    # The PR reported must be the one whose claim actually suppressed the dispatch.
    _stub(
        monkeypatch,
        [
            _pr(1, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"]),
            _pr(2, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"]),
        ],
    )

    _covered, _keys, _per_spec, refs, _ok = discover.dedupe_inputs()

    assert refs["covered_by"]["aaaaaaaa"].endswith("#1")


def test_a_locked_key_reports_every_pr_holding_it(monkeypatch):
    _stub(
        monkeypatch,
        [_pr(10, ["main:saas-smoke-e2e"]), _pr(11, ["main:saas-smoke-e2e"])],
    )

    _covered, _keys, _per_spec, refs, _ok = discover.dedupe_inputs()

    repo = discover.FIX_PR_REPOS[0]
    assert refs["keys"]["main:saas-smoke-e2e"] == [f"{repo}#10", f"{repo}#11"]


def test_a_stale_prs_claim_is_reported_by_neither_index(monkeypatch):
    # Its claims are dropped so a fresh agent gets a chance; reporting it as the PR to
    # wait on would point the medic at a PR that can never land.
    _stub(
        monkeypatch,
        [_pr(9, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"], mergeable="CONFLICTING")],
    )

    _covered, _keys, _per_spec, refs, _ok = discover.dedupe_inputs()

    assert refs["covered_by"] == {}


def test_an_expired_lock_is_not_reported_as_holding_the_key(monkeypatch):
    _stub(
        monkeypatch,
        [_pr(8, ["main:sm-smoke-e2e"], age_hours=planning.PR_LOCK_TTL_HOURS + 1)],
    )

    _covered, _keys, _per_spec, refs, _ok = discover.dedupe_inputs()

    assert refs["keys"] == {}


def test_a_product_bug_reports_its_issue_url(monkeypatch):
    monkeypatch.setattr(
        discover,
        "gh_json",
        lambda args, default: [
            {
                "body": "Fingerprint: nightly-product-bug fp=abcd1234\n",
                "url": "https://github.com/camunda/camunda/issues/55864",
            }
        ],
    )

    fps, urls = discover.product_bug_fingerprints()

    assert fps == {"abcd1234"}
    assert urls == {"abcd1234": "https://github.com/camunda/camunda/issues/55864"}


def test_a_product_bug_without_a_url_still_suppresses(monkeypatch):
    # The fingerprint is what decides; the URL is only what the message prints.
    monkeypatch.setattr(
        discover,
        "gh_json",
        lambda args, default: [{"body": "nightly-product-bug fp=abcd1234"}],
    )

    fps, urls = discover.product_bug_fingerprints()

    assert fps == {"abcd1234"}
    assert urls == {}


def test_a_suppressed_candidate_carries_its_own_base_ref(monkeypatch):
    # Not the run's: one preview-env run suppresses candidates for several branches.
    result = planning.Plan(
        suppressed=[
            planning.Suppression(
                planning.Candidate(
                    base_ref="stable/8.9",
                    surface=classify.SURFACE_SM_E2E,
                    job_name="Run 8.9 Smoke Tests",
                ),
                planning.SUPPRESSED_IN_FLIGHT,
            )
        ]
    )

    payload = discover.serialise(
        result, classify.Blame(None, None, None, "none"), "1", base_ref="main"
    )

    assert payload["suppressed"][0]["base_ref"] == "stable/8.9"
    assert payload["base_ref"] == "main"


# ---------------------------------------------------------------------------
# End-to-end smoke test over _run()
# ---------------------------------------------------------------------------
#
# Every other test here calls one function, so a changed return arity between a
# producer and its only caller is invisible to them: `dedupe_inputs` grew a fifth
# value and `_run` kept unpacking four, which raises `ValueError` on every real
# triage while the whole suite stayed green. This test exists to fail on that.


def _run_discovery(monkeypatch, tmp_path, **overrides):
    """Run `_run` with every lookup stubbed, and nothing left able to reach the network.

    `gh_json`/`gh_json_ex` are stubbed too, not just the named producers: this suite runs
    with no token, so an unstubbed lookup would not merely be slow — it would fail closed,
    suppress the candidate, and fail the assertion for a reason that has nothing to do
    with what is being tested. Stubbing the transport makes that impossible rather than
    relying on the stub list staying complete as producers are renamed.
    """
    import json as _json
    import sys as _sys

    import plan as planning

    cand = planning.Candidate(
        base_ref="main",
        surface=classify.SURFACE_SM_E2E,
        job_name="Playwright e2e smoke after install",
        specs=[
            classify.FailingSpec(
                file="tests/SM-8.10/a.spec.ts",
                test_name="logs in",
                error="boom",
                project="chromium",
                attempts=1,
                statuses=["failed"],
            )
        ],
    )

    def _no_network_json(args, default):
        return default

    def _no_network_json_ex(args, default):
        # Empty-but-OK, so a lookup reached through the transport reports "nothing
        # found" rather than the fail-closed "could not prove it".
        return [], ""

    stubs = {
        "gh_json": _no_network_json,
        "gh_json_ex": _no_network_json_ex,
        "build_candidates": lambda run_id, base_ref, workdir: ([cand], []),
        "inflight_keys": lambda: (set(), True),
        "dedupe_inputs": lambda: (set(), set(), set(), {"covered_by": {}, "keys": {}}, True),
        "covered_fingerprints": lambda: (set(), {}),
        "open_fix_pr_keys": lambda: (set(), {}, True),
        "paths_claimed_by_open_prs": lambda paths: ({}, True),
        "product_bug_fingerprints": lambda: (set(), {}),
        "resolve_blame": lambda sha: classify.Blame(None, None, None, "none"),
    }
    stubs.update(overrides)
    for name, value in stubs.items():
        if hasattr(discover, name):
            monkeypatch.setattr(discover, name, value)

    out = tmp_path / "plan.json"
    monkeypatch.setattr(
        _sys,
        "argv",
        ["discover.py", "--run-id", "1", "--base-ref", "main", "--out", str(out)],
    )
    runner = getattr(discover, "_run", None) or discover.main
    assert runner() == 0
    return _json.loads(out.read_text())


def test_discovery_produces_a_plan_the_notifier_can_render(monkeypatch, tmp_path):
    payload = _run_discovery(monkeypatch, tmp_path)

    assert payload["base_ref"] == "main"
    assert "covered_by" in payload["references"]
    assert "keys" in payload["references"]
    assert "product_bugs" in payload["references"]
    assert len(payload["dispatches"]) == 1


def test_a_failed_lookup_is_named_in_the_plan(monkeypatch, tmp_path):
    # What lets the Slack message say "could not verify" instead of asserting that
    # someone else is already on the failure.
    payload = _run_discovery(
        monkeypatch, tmp_path, inflight_keys=lambda: (set(), False)
    )

    assert "inflight" in payload["references"]["lookups_failed"]
    assert payload["dispatches"] == []
