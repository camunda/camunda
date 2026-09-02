"""Unit tests for the dedupe snapshot discover hands to the planner.

`test_plan.py` passes `open_pr_keys_with_coverage` in ready-made, so it asserts what
`plan` does with the answer, never how it is computed. The derivation is where the
"does this whole surface stay locked" decision is actually made — per-PR key grouping,
the TTL skip, and the intersection over holders — so it is tested here against a stubbed
`open_fix_prs`.
"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

import discover
import plan as planning

NOW = datetime.now(timezone.utc)


def _ago(**kw):
    return (NOW - timedelta(**kw)).isoformat().replace("+00:00", "Z")


def _pr(number, keys, *, claims=(), age_hours=0):
    body = ""
    if claims:
        body = f"Fixes.\n\n{planning.render_coverage_block(set(claims))}\n"
    return {
        "number": number,
        "body": body,
        "createdAt": _ago(hours=age_hours),
        "labels": [{"name": f"{discover.KEY_LABEL_PREFIX}{k}"} for k in keys],
    }


def _stub(monkeypatch, prs, *, ok=True):
    """Serve `prs` from the first fix repo and nothing from the rest."""
    seen = {"n": 0}

    def fake(repo):
        seen["n"] += 1
        return (list(prs), ok) if seen["n"] == 1 else ([], ok)

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
