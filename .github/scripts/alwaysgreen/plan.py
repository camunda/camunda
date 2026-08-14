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

import classify

#: Reasons a candidate was not dispatched, surfaced in the job summary.
SUPPRESSED_NOT_DISPATCHABLE = "surface-not-in-increment"
SUPPRESSED_IN_FLIGHT = "agent-already-running"
SUPPRESSED_PR_COVERED = "open-pr-covers-all-specs"
SUPPRESSED_PR_OPEN = "open-fix-pr-for-surface"
SUPPRESSED_PRODUCT_BUG = "tracked-by-open-product-bug"
SUPPRESSED_RECENT_NO_FIX = "no-fix-verdict-within-cooldown"
SUPPRESSED_NO_EVIDENCE = "no-failing-specs-extracted"
SUPPRESSED_CAP = "per-run-cap-reached"


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

    @property
    def key(self) -> str:
        return dispatch_key(self.base_ref, self.surface)

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
            return [classify.job_fingerprint(self.base_ref, self.surface, self.job_name)]
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


def plan_dispatches(
    candidates: list[Candidate],
    *,
    covered_fingerprints: set[str],
    inflight_keys: set[str],
    open_pr_keys: set[str],
    product_bug_fingerprints: set[str],
    recent_no_fix_fingerprints: set[str] | None = None,
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
    """
    plan = Plan()
    no_fix = recent_no_fix_fingerprints or set()

    for cand in candidates:
        # Before anything reads .specs or derives fingerprints from them.
        cand.specs = dedupe_specs(cand.specs)

        if cand.surface not in dispatchable_surfaces:
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_NOT_DISPATCHABLE, cand.surface)
            )
            continue

        if cand.key in inflight_keys:
            plan.suppressed.append(Suppression(cand, SUPPRESSED_IN_FLIGHT, cand.key))
            continue

        # An open fix PR for this surface stops a second agent regardless of what the
        # PR body claims. The coverage block is written by the agent, so a PR that
        # omitted it would otherwise be invisible here and the failure re-dispatched.
        if cand.key in open_pr_keys:
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

        # Drop specs an open PR or a recent no-fix verdict already accounts for;
        # dispatch only what is left.
        if not cand.job_level:
            remaining = [
                s
                for s, fp in zip(cand.specs, cand.spec_fingerprints)
                if fp not in covered_fingerprints
                and fp not in product_bug_fingerprints
                and fp not in no_fix
            ]
            if not remaining:
                plan.suppressed.append(Suppression(cand, SUPPRESSED_PR_COVERED))
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
