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
SUPPRESSED_NO_EVIDENCE = "no-failing-specs-extracted"
SUPPRESSED_CAP = "per-run-cap-reached"
SUPPRESSED_PATH_CLAIMED = "spec-path-claimed-by-open-pr"

#: GitHub rejects a label longer than this, and the dispatch key is stamped on fix
#: PRs as `ag-key:<key>`. Asserted over the supported matrix in test_plan.py so a new
#: source repo or base ref cannot silently push a label past the limit — the failure
#: mode is a label that is never created, which disables dedupe without any error.
KEY_LABEL_PREFIX = "ag-key:"
MAX_LABEL_LENGTH = 50


def dispatch_key(base_ref: str, surface: str, source: str = "") -> str:
    """Identify one failing surface on one branch of one source pipeline.

    `source` is the source repository's name (`connectors`, `camunda`). Several
    pipelines open fix PRs into the same e2e repository and share branch names, so
    without it `main:saas-smoke-e2e` from connectors and from the monorepo are the
    same string and each would suppress the other's dispatch.

    Fingerprints deliberately stay un-namespaced: they identify a failing *test*,
    which is the same test whichever pipeline observed it, so one fix PR should
    cover it for all of them.
    """
    return f"{source}:{base_ref}:{surface}" if source else f"{base_ref}:{surface}"


@dataclass
class Candidate:
    """One surface that failed on one base ref, with the evidence for it."""

    base_ref: str
    surface: str
    job_name: str
    #: Source repository name the failing run belongs to, e.g. `connectors`.
    source: str = ""
    specs: list[classify.FailingSpec] = field(default_factory=list)
    #: Run whose artifacts hold the evidence. For SaaS this is the downstream run
    #: in the e2e repository, not the AlwaysGreen run.
    evidence_run_url: str = ""
    evidence_repo: str = ""
    #: Set when the surface produced no per-spec detail (job-level failure).
    job_level: bool = False

    @property
    def key(self) -> str:
        return dispatch_key(self.base_ref, self.surface, self.source)

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
    claimed_paths: dict[str, int] | None = None,
    max_dispatches: int = 2,
    dispatchable_surfaces: frozenset[str] = classify.DISPATCHABLE_SURFACES,
) -> Plan:
    """Decide which candidates to hand to the fix agent.

    Checks are ordered cheapest-and-most-decisive first so the summary reports the
    most useful reason when several apply.
    """
    plan = Plan()

    for cand in candidates:
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

        # Author-agnostic: the other agents editing these files (the e2e repo's own
        # nightly triage, another product pipeline, a human) dedupe on schemes this
        # one cannot see, so the only reliable signal is that the file is already
        # open in a PR. Keyed on exact repo-relative paths, so tests/8.9/x.spec.ts
        # never shadows tests/8.10/x.spec.ts.
        claimed = claimed_paths or {}
        if not cand.job_level and claimed:
            hits = sorted({(s.file, claimed[s.file]) for s in cand.specs if s.file in claimed})
            if hits:
                plan.suppressed.append(
                    Suppression(
                        cand,
                        SUPPRESSED_PATH_CLAIMED,
                        ", ".join(f"{path} (#{number})" for path, number in hits),
                    )
                )
                continue

        fps = cand.fingerprints

        if fps and all(f in product_bug_fingerprints for f in fps):
            plan.suppressed.append(
                Suppression(cand, SUPPRESSED_PRODUCT_BUG, ",".join(sorted(set(fps))))
            )
            continue

        # Drop specs an open PR already covers; dispatch only what is left.
        if not cand.job_level:
            remaining = [
                s
                for s, fp in zip(cand.specs, cand.spec_fingerprints)
                if fp not in covered_fingerprints
                and fp not in product_bug_fingerprints
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
