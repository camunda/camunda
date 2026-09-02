"""Turn classified AlwaysGreen failures into dispatch decisions.

Pure functions, like `classify`. `discover.py` fetches the inputs; everything that
decides *whether* and *what* to dispatch lives here so it can be tested without a
token.

Two levels of identity are used, for different jobs:

* A per-spec fingerprint identifies one failing test. It goes in the fix PR's
  coverage block and is what suppresses a re-dispatch once a PR covers it.
* A dispatch key, `<base_ref>:<surface>`, identifies one agent's remit. At most one
  agent per key may be in flight. This is the rule that actually holds the line when
  the same cause fails many consecutive runs: on 2026-07-23 seventeen runs failed on
  one root cause roughly 30-40 minutes apart, while an agent takes 15-60 minutes, so
  a PR-existence check alone loses the race repeatedly.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

import classify

#: Reasons a candidate was not dispatched, surfaced in the job summary.
SUPPRESSED_NOT_DISPATCHABLE = "surface-not-in-increment"
SUPPRESSED_IN_FLIGHT = "agent-already-running"
SUPPRESSED_PR_COVERED = "open-pr-covers-all-specs"
SUPPRESSED_PR_OPEN = "open-fix-pr-for-surface"
SUPPRESSED_PRODUCT_BUG = "tracked-by-open-product-bug"
SUPPRESSED_RECENT_NO_FIX = "no-fix-verdict-within-cooldown"
SUPPRESSED_FIXED_UPSTREAM = "fixed-upstream-after-run-started"
SUPPRESSED_UNSUPPORTED_REF = "base-ref-not-supported-by-fix-agent"
#: Every spec was dropped, but by more than one of the sources above.
SUPPRESSED_ALL_ACCOUNTED = "all-specs-already-accounted-for"
SUPPRESSED_NO_EVIDENCE = "no-failing-specs-extracted"
SUPPRESSED_CAP = "per-run-cap-reached"


#: Branches the fix agent accepts. Must stay in sync with the `Validate inputs` case
#: statement in alwaysgreen-fix.yml: dispatching anything else spends a runner only to
#: fail on the agent's first step, which is what happened to run 31115770750 on
#: `ci/alwaysgreen-helm-live-check`.
SUPPORTED_BASE_REFS = frozenset(
    {"main", "stable/8.7", "stable/8.8", "stable/8.9", "stable/8.10"}
)


#: How long an open fix PR's key label keeps holding its dispatch key. The lock is
#: there to win the race described above, but nothing ever released it: the label
#: stops matching `is:open` only when a human merges or closes the PR, so the agent's
#: own output locked the agent out of its own surface. camunda-platform-helm#6927
#: claimed `main:sm-smoke-e2e` on 2026-08-20 and then sat unreviewed, and every `main`
#: triage for the following six days reported `open-fix-pr-for-surface` and dispatched
#: nothing. Past the TTL this coarse per-surface lock lifts and the per-spec coverage
#: block takes over: a repeat of the same failure is still suppressed as
#: `open-pr-covers-all-specs`, while a genuinely new one gets an agent.
#:
#: The TTL alone still leaves the surface shut for two days against failures the
#: holding PR never claimed, so `open_pr_keys_with_coverage` narrows the lock further:
#: a PR that published a coverage block is read at spec granularity immediately, and
#: only a PR that published none falls back to locking its whole surface.
PR_LOCK_TTL_DAYS = 2


def pr_lock_expired(
    created_at: str | None, now: datetime, ttl_days: int = PR_LOCK_TTL_DAYS
) -> bool:
    """Whether an open fix PR is too old to keep holding its dispatch key.

    A missing or unparseable timestamp keeps the lock, and `ttl_days <= 0` disables
    expiry altogether: the bias matches the `ok` flags in discover's key lookups,
    where an unproven state suppresses rather than risks a duplicate PR.

    Either timestamp is read as UTC when it carries no offset, so a naive `now` does
    not raise against GitHub's offset-aware `createdAt`.
    """
    if ttl_days <= 0:
        return False
    text = (created_at or "").strip()
    if not text:
        return False
    try:
        created = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return False
    if created.tzinfo is None:
        created = created.replace(tzinfo=timezone.utc)
    if now.tzinfo is None:
        now = now.replace(tzinfo=timezone.utc)
    return now - created > timedelta(days=ttl_days)


def spec_suite(spec_file: str) -> str | None:
    """The version directory a spec lives in: `tests/SM-8.10/x.spec.ts` -> `SM-8.10`."""
    parts = (spec_file or "").split("/")
    if len(parts) >= 3 and parts[0] == "tests" and parts[1]:
        return parts[1]
    return None


def dispatch_key(base_ref: str, surface: str) -> str:
    return f"{base_ref}:{surface}"


def dedupe_specs(specs: list[classify.FailingSpec]) -> list[classify.FailingSpec]:
    """Collapse repeats of the same (file, test_name), keeping the first.

    A SaaS run on 8.8/8.9 publishes one Playwright report per Tasklist generation
    (`json-report-v1` and `json-report-v2`), and discover concatenates every report
    it downloads. A spec that fails in both generations therefore arrives twice, which
    doubles the agent's spec list and the fingerprints it is told to claim — observed
    on run 31016165246, where two failing tests were dispatched as four.
    """
    out: list[classify.FailingSpec] = []
    seen: set[tuple[str, str]] = set()
    for spec in specs:
        identity = (spec.file, spec.test_name)
        if identity in seen:
            continue
        seen.add(identity)
        out.append(spec)
    return out


def verdict_fingerprints(
    fix_meta: object, fingerprints: object
) -> set[str]:
    """Fingerprints a finished agent run leaves suppressed, from its own artifacts.

    Empty unless the run produced a manifest that opened nothing. A crashed agent
    (no manifest, or one that will not parse) and a run that opened a PR must both
    contribute nothing: the first is an infrastructure failure rather than a verdict,
    and the second is already covered by the PR's own coverage block.
    """
    if not isinstance(fix_meta, dict):
        return set()
    prs = fix_meta.get("prs")
    if not isinstance(prs, list) or prs:
        return set()
    if not isinstance(fingerprints, list):
        return set()
    return {str(fp)[:8] for fp in fingerprints if fp}


@dataclass
class Candidate:
    """One surface that failed on one base ref, with the evidence for it."""

    base_ref: str
    surface: str
    job_name: str
    specs: list[classify.FailingSpec] = field(default_factory=list)
    #: Run whose artifacts hold the evidence. For SaaS this is the downstream run
    #: in the e2e repository, not the AlwaysGreen run.
    evidence_run_url: str = ""
    evidence_repo: str = ""
    #: Set when the surface produced no per-spec detail (job-level failure).
    job_level: bool = False
    #: Further jobs of the same surface, folded in by `merge_by_key`. Kept so a merged
    #: job-level dispatch still claims a fingerprint per job and leaves none of them
    #: uncovered and re-dispatchable on the next run.
    also_failing_jobs: list[str] = field(default_factory=list)

    @property
    def key(self) -> str:
        return dispatch_key(self.base_ref, self.surface)

    @property
    def job_names(self) -> list[str]:
        return [self.job_name, *self.also_failing_jobs]

    @property
    def spec_fingerprints(self) -> list[str]:
        return [
            classify.spec_fingerprint(self.base_ref, self.surface, s.file, s.test_name)
            for s in self.specs
        ]

    @property
    def fingerprints(self) -> list[str]:
        """Every fingerprint this candidate would claim in a PR coverage block."""
        if self.job_level or not self.specs:
            return list(
                dict.fromkeys(
                    classify.job_fingerprint(self.base_ref, self.surface, n)
                    for n in self.job_names
                )
            )
        return self.spec_fingerprints

    @property
    def deterministic_specs(self) -> list[classify.FailingSpec]:
        return [s for s in self.specs if s.deterministic]


@dataclass
class Suppression:
    candidate: Candidate
    reason: str
    detail: str = ""


@dataclass
class Plan:
    dispatches: list[Candidate] = field(default_factory=list)
    suppressed: list[Suppression] = field(default_factory=list)
    #: Failing jobs dropped by the noise prefilter, for the summary only.
    noise: list[tuple[str, str]] = field(default_factory=list)


def merge_by_key(candidates: list[Candidate]) -> list[Candidate]:
    """Collapse candidates sharing a dispatch key into one, keeping input order.

    Candidates are built per failing job while a key is one agent's remit, and one
    surface routinely fails as several jobs: `Playwright e2e full after install` and
    `Playwright e2e smoke after install` are both `sm-smoke-e2e`, and a multi-cell
    matrix yields one `helm-install` job per cell. `plan_dispatches` reads
    `open_pr_keys` and `inflight_keys` once up front and never adds a key it has just
    planned, so same-key candidates could not see each other and both dispatched —
    two agents, then two PRs stamping the one key label.
    c8-cross-component-e2e-tests#3071 and #3073 are such a pair, and freeing
    `stable/8.9:saas-smoke-e2e` needed two merges instead of one.
    """
    merged: dict[str, Candidate] = {}
    for cand in candidates:
        first = merged.get(cand.key)
        if first is None:
            merged[cand.key] = cand
            continue
        first.specs.extend(cand.specs)
        for name in cand.job_names:
            if name and name not in first.job_names:
                first.also_failing_jobs.append(name)
        first.evidence_run_url = first.evidence_run_url or cand.evidence_run_url
        first.evidence_repo = first.evidence_repo or cand.evidence_repo
        first.job_level = first.job_level or cand.job_level
    return list(merged.values())


def plan_dispatches(
    candidates: list[Candidate],
    *,
    covered_fingerprints: set[str],
    inflight_keys: set[str],
    open_pr_keys: set[str],
    product_bug_fingerprints: set[str],
    open_pr_keys_with_coverage: set[str] | None = None,
    recent_no_fix_fingerprints: set[str] | None = None,
    fixed_upstream_fingerprints: set[str] | None = None,
    supported_base_refs: frozenset[str] = SUPPORTED_BASE_REFS,
    max_dispatches: int = 2,
    dispatchable_surfaces: frozenset[str] = classify.DISPATCHABLE_SURFACES,
) -> Plan:
    """Decide which candidates to hand to the fix agent.

    Checks are ordered cheapest-and-most-decisive first so the summary reports the
    most useful reason when several apply.

    `recent_no_fix_fingerprints` are those an agent investigated inside the cooldown
    window and could not safely fix. Without them a `not-determined` verdict leaves no
    state anywhere — no PR, so no coverage block and no key label — and the identical
    forensic run is dispatched again on the next failure.

    `fixed_upstream_fingerprints` are those whose test code changed in the e2e repo
    after the failing run started, so the run executed source that is already superseded.

    `open_pr_keys_with_coverage` are the keys of `open_pr_keys` whose every holding PR
    published a coverage block. Those PRs state which specs they claim, so the coarse
    per-surface lock is skipped for them and the per-spec accounting below decides.
    """
    plan = Plan()
    no_fix = recent_no_fix_fingerprints or set()
    fixed_upstream = fixed_upstream_fingerprints or set()
    pr_keys_with_coverage = open_pr_keys_with_coverage or set()

    for cand in merge_by_key(candidates):
        # Before anything reads .specs or derives fingerprints from them.
        cand.specs = dedupe_specs(cand.specs)

        if cand.base_ref not in supported_base_refs:
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_UNSUPPORTED_REF, cand.base_ref)
            )
            continue

        if cand.surface not in dispatchable_surfaces:
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_NOT_DISPATCHABLE, cand.surface)
            )
            continue

        if cand.key in inflight_keys:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_IN_FLIGHT, cand.key))
            continue

        # An open fix PR that published no coverage block stops a second agent on its
        # surface: the block is written by the agent, so a PR that omitted it states
        # nothing about which specs it claims and the whole surface has to be assumed.
        # A PR that did publish one is authoritative per spec through
        # `covered_fingerprints`, so locking its surface only hides its neighbours: on
        # 2026-09-02 c8-cross-component-e2e-tests#3267 claimed three cluster-creation
        # setup specs on `main:saas-smoke-e2e`, and run 33605992250's unrelated
        # `smoke-tests.spec.ts` failure was suppressed as `open-fix-pr-for-surface`
        # rather than dispatched.
        if cand.key in open_pr_keys and cand.key not in pr_keys_with_coverage:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_PR_OPEN, cand.key))
            continue

        if not cand.job_level and not cand.specs:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_NO_EVIDENCE))
            continue

        fps = cand.fingerprints

        if fps and all(f in product_bug_fingerprints for f in fps):
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_PRODUCT_BUG, ",".join(sorted(set(fps))))
            )
            continue

        if fps and all(f in no_fix for f in fps):
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_RECENT_NO_FIX, ",".join(sorted(set(fps))))
            )
            continue

        if fps and all(f in fixed_upstream for f in fps):
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_FIXED_UPSTREAM, ",".join(sorted(set(fps))))
            )
            continue

        # Drop specs an open PR, a product bug, a recent no-fix verdict or an upstream
        # fix already accounts for; dispatch only what is left. Which source dropped
        # each one is tracked so the summary names the real blocker: reporting every
        # empty remainder as "open-pr-covers-all-specs" hid the other three.
        if not cand.job_level:
            accounted: list[str] = []
            remaining = []
            for spec, fp in zip(cand.specs, cand.spec_fingerprints):
                if fp in covered_fingerprints:
                    accounted.append(SUPPRESSED_PR_COVERED)
                elif fp in product_bug_fingerprints:
                    accounted.append(SUPPRESSED_PRODUCT_BUG)
                elif fp in no_fix:
                    accounted.append(SUPPRESSED_RECENT_NO_FIX)
                elif fp in fixed_upstream:
                    accounted.append(SUPPRESSED_FIXED_UPSTREAM)
                else:
                    remaining.append(spec)
            if not remaining:
                sources = sorted(set(accounted))
                plan.suppressed.append(
                    Suppression(cand, sources[0], "")
                    if len(sources) == 1
                    else Suppression(cand, SUPPRESSED_ALL_ACCOUNTED, ",".join(sources))
                )
                continue
            cand.specs = remaining
        elif fps and all(f in covered_fingerprints for f in fps):
            plan.suppressed.append(Suppression(cand, SUPPRESSED_PR_COVERED))
            continue

        if len(plan.dispatches) >= max_dispatches:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_CAP))
            continue

        plan.dispatches.append(cand)

    return plan


# ---------------------------------------------------------------------------
# PR coverage block
# ---------------------------------------------------------------------------

COVERAGE_BEGIN = "<!-- alwaysgreen-fixed"
COVERAGE_END = "-->"


def parse_coverage_block(body: str | None) -> set[str]:
    """Extract fingerprints a fix PR claims to cover.

    Format, one per line inside an HTML comment so it renders invisibly:

        <!-- alwaysgreen-fixed
        fp=1a2b3c4d
        fp=5e6f7a8b
        -->
    """
    text = body or ""
    start = text.find(COVERAGE_BEGIN)
    if start == -1:
        return set()
    end = text.find(COVERAGE_END, start)
    block = text[start : end if end != -1 else len(text)]
    out: set[str] = set()
    for line in block.splitlines():
        line = line.strip()
        if line.startswith("fp="):
            value = line[3:].strip()
            if value:
                out.add(value)
    return out


def render_coverage_block(fingerprints: set[str]) -> str:
    """Render a coverage block, sorted so repeated updates produce a stable diff."""
    lines = [COVERAGE_BEGIN] + [f"fp={fp}" for fp in sorted(fingerprints)] + [COVERAGE_END]
    return "\n".join(lines)


def merge_coverage_block(body: str | None, new_fingerprints: set[str]) -> str:
    """Return the body with the coverage block replaced by the union of fingerprints.

    Existing entries are never dropped: an accumulating PR must keep claiming every
    test it already fixed, or a later triage run would re-dispatch them.
    """
    text = (body or "").rstrip()
    existing = parse_coverage_block(text)
    merged = existing | set(new_fingerprints)

    start = text.find(COVERAGE_BEGIN)
    if start == -1:
        return f"{text}\n\n{render_coverage_block(merged)}\n"

    end = text.find(COVERAGE_END, start)
    tail = text[end + len(COVERAGE_END) :] if end != -1 else ""
    return f"{text[:start]}{render_coverage_block(merged)}{tail}".rstrip() + "\n"
