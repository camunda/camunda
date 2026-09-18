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
    covered, keys, per_spec, ok = discover.dedupe_inputs()
    assert ok is True
    assert covered == {"aaaaaaaa"}
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == {"main:saas-smoke-e2e"}


def test_a_holder_claiming_nothing_keeps_its_key_locked(monkeypatch):
    _stub(monkeypatch, [_pr(1, ["main:saas-smoke-e2e"])])
    covered, keys, per_spec, ok = discover.dedupe_inputs()
    assert covered == set()
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == set()


def test_an_empty_coverage_block_claims_nothing(monkeypatch):
    # The marker alone is not a statement of remit, so it must not free the surface.
    pr = _pr(1, ["main:saas-smoke-e2e"])
    pr["body"] = f"Fixes.\n\n{planning.COVERAGE_BEGIN}\nfp=\n{planning.COVERAGE_END}\n"
    _stub(monkeypatch, [pr])
    _covered, keys, per_spec, _ok = discover.dedupe_inputs()
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
    covered, keys, per_spec, _ok = discover.dedupe_inputs()
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
    _covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert keys == {"main:saas-smoke-e2e"}
    assert per_spec == {"main:saas-smoke-e2e"}


def test_an_expired_holder_releases_its_key_but_keeps_its_claims(monkeypatch):
    # The specs a PR claims stay claimed while it is open; only the coarse key lock is
    # time-bound, so the failure it fixed is still suppressed per spec.
    _stub(monkeypatch, [_pr(1, ["main:saas-smoke-e2e"], claims=["aaaaaaaa"], age_hours=99)])
    covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == set()
    assert per_spec == set()


def test_a_pr_carrying_two_key_labels_holds_both(monkeypatch):
    _stub(
        monkeypatch,
        [_pr(1, ["main:saas-smoke-e2e", "stable/8.10:saas-smoke-e2e"], claims=["aaaaaaaa"])],
    )
    _covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert keys == {"main:saas-smoke-e2e", "stable/8.10:saas-smoke-e2e"}
    assert per_spec == keys


def test_a_pr_with_no_key_label_still_contributes_its_claims(monkeypatch):
    # A fix PR whose key label was never stamped: it locks nothing, but the specs it
    # claims must still suppress a repeat.
    _stub(monkeypatch, [_pr(1, [], claims=["aaaaaaaa"])])
    covered, keys, per_spec, _ok = discover.dedupe_inputs()
    assert covered == {"aaaaaaaa"}
    assert keys == set()
    assert per_spec == set()


def test_a_failed_lookup_reports_not_ok(monkeypatch):
    # Coverage and keys are one snapshot behind one `ok`. A partial read must not let
    # the caller skip the coarse lock while believing nothing is claimed.
    _stub(monkeypatch, [_pr(1, ["main:saas-smoke-e2e"], claims=["aaaaaaaa"])], ok=False)
    _covered, _keys, _per_spec, ok = discover.dedupe_inputs()
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
    covered, keys, per_spec, _ok = discover.dedupe_inputs()
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
    covered, _keys, _per_spec, _ok = discover.dedupe_inputs()
    assert covered == {"bbbbbbbb"}


def test_an_unknown_mergeable_state_still_counts_as_covered(monkeypatch):
    # GitHub has not finished computing mergeability yet; must not be read as broken.
    _stub(
        monkeypatch,
        [_pr(1, ["main:sm-smoke-e2e"], claims=["aaaaaaaa"], mergeable="UNKNOWN")],
    )
    covered, _keys, _per_spec, _ok = discover.dedupe_inputs()
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
