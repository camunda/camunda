"""Classification logic for AlwaysGreen failure triage.

Pure functions only — no network, no subprocess. `discover.py` supplies the data
fetched from the GitHub API and the downloaded artifacts. Keeping the two apart is
what makes this file unit-testable without a cluster or a token.

The rules encoded here were derived from every failed run of
docker-build-helm-integration.yml in a 300-run window (29 failures). The
non-obvious ones:

* A `failure` conclusion does not imply a fixable failure. GitHub platform
  internal errors and mid-job cancellations both surface as `failure` with no
  usable evidence, and two of the 29 were exactly that.
* Skipped jobs keep unrendered `${{ }}` in their names, so patterns must anchor
  on the literal prefix and only failing jobs may be matched.
* Playwright nests `suites[].suites[].specs[]`. Counting one level deep yields
  zero and silently mislabels every failure.
* A failing job name identifies the surface that broke, not the repository that
  needs the fix: the most common SM e2e failure is a Keycloak deploy problem.
"""

from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field
from typing import Any, Iterable, Iterator

# ---------------------------------------------------------------------------
# Platform noise
# ---------------------------------------------------------------------------

#: Annotation text GitHub emits when the job never really ran. Matched as a
#: substring because the message is sometimes wrapped or suffixed.
PLATFORM_ERROR_MARKER = "internal error when running your job"
CANCELLED_MARKER = "The operation was canceled"

#: Verdicts that must never reach the fix agent.
NOISE_PLATFORM = "platform-flake"
NOISE_CANCELLED = "cancelled"
NOISE_NO_EVIDENCE = "no-evidence"


def noise_verdict(
    *,
    conclusion: str,
    step_count: int,
    failure_annotations: Iterable[str],
) -> str | None:
    """Return a noise verdict for a failing job, or None if it looks diagnosable.

    `failure_annotations` are the messages of the job's failure-level check-run
    annotations. A job with no steps *and* no failure annotation carries no
    evidence at all — observed on `Create cluster generation on INT`.
    """
    if conclusion != "failure":
        return None

    messages = [m for m in failure_annotations if m]
    joined = "\n".join(messages)

    if PLATFORM_ERROR_MARKER in joined:
        return NOISE_PLATFORM
    if CANCELLED_MARKER in joined:
        return NOISE_CANCELLED
    if step_count == 0 and not messages:
        return NOISE_NO_EVIDENCE
    return None


# ---------------------------------------------------------------------------
# Surface classification
# ---------------------------------------------------------------------------

SURFACE_SM_E2E = "sm-smoke-e2e"
SURFACE_SAAS_E2E = "saas-smoke-e2e"
SURFACE_SAAS_PROVISIONING = "saas-provisioning"
SURFACE_SAAS_INFRA = "saas-infra"
SURFACE_HELM_INSTALL = "helm-install"
SURFACE_HELM_CLEANUP = "helm-cleanup"
SURFACE_BUILD = "build"
SURFACE_CI_INFRA = "ci-infra"

#: Surfaces the first increment dispatches. Everything else is recorded and
#: reported but not handed to the agent yet.
DISPATCHABLE_SURFACES = frozenset({SURFACE_SM_E2E, SURFACE_SAAS_E2E})

#: A pure propagator: it fails whenever the reusable helm workflow failed and
#: carries no independent signal.
IGNORED_JOB_PREFIXES = ("Observe Helm chart Integration Tests status",)

#: Literal prefixes, deliberately stopping before the first `${{`, matched
#: against the trailing segment of a (possibly nested) job name.
_SURFACE_PREFIXES: tuple[tuple[str, str], ...] = (
    ("Playwright e2e after install", SURFACE_SM_E2E),
    ("Trigger SaaS E2E tests", SURFACE_SAAS_E2E),
    ("install for install on", SURFACE_HELM_INSTALL),
    ("Cleanup - install on", SURFACE_HELM_CLEANUP),
    ("Build and Push Docker Image", SURFACE_BUILD),
    ("Preflight checks", SURFACE_CI_INFRA),
    ("Check Run Conditions", SURFACE_CI_INFRA),
    ("Format Identifier", SURFACE_CI_INFRA),
)

_FRONTEND_BUILD_RE = re.compile(r"^Build \w+ Frontend$")


def job_leaf_name(job_name: str) -> str:
    """Return the last segment of a nested reusable-workflow job name."""
    return job_name.rsplit("/", 1)[-1].strip()


def surface_for_job(job_name: str) -> str | None:
    """Map a *failing* job name to the surface that broke.

    Callers must filter on `conclusion == "failure"` first: a skipped
    `Playwright e2e after install …` job is present in most runs and would
    otherwise be misread as an SM e2e failure.
    """
    leaf = job_leaf_name(job_name)

    for ignored in IGNORED_JOB_PREFIXES:
        if leaf.startswith(ignored):
            return None

    for prefix, surface in _SURFACE_PREFIXES:
        if leaf.startswith(prefix):
            return surface

    if _FRONTEND_BUILD_RE.match(leaf):
        return SURFACE_BUILD

    return None


# ---------------------------------------------------------------------------
# Playwright report parsing
# ---------------------------------------------------------------------------


def iter_specs(report: Any) -> Iterator[dict]:
    """Yield every spec in a Playwright JSON report, at any nesting depth.

    Playwright emits a file-level suite whose `specs` is empty and whose
    `suites` holds the describe-level suites that actually carry specs, so a
    single-level walk finds nothing.
    """
    if isinstance(report, dict):
        specs = report.get("specs")
        if isinstance(specs, list):
            for spec in specs:
                if isinstance(spec, dict):
                    yield spec
        for value in report.values():
            if isinstance(value, (dict, list)):
                yield from iter_specs(value)
    elif isinstance(report, list):
        for item in report:
            if isinstance(item, (dict, list)):
                yield from iter_specs(item)


_SETUP_SPEC_RE = re.compile(r"test-setup\.spec\.[jt]s$")


@dataclass(frozen=True)
class SpecCounts:
    total: int = 0
    failed: int = 0
    flaky: int = 0
    setup_failed: int = 0


def count_specs(report: Any) -> SpecCounts:
    """Count specs in a report, mirroring the categories the pipeline reports."""
    total = failed = flaky = setup_failed = 0
    for spec in iter_specs(report):
        total += 1
        ok = spec.get("ok")
        tests = spec.get("tests") or []
        retried = any(len((t or {}).get("results") or []) > 1 for t in tests)
        if ok is False:
            failed += 1
            if _SETUP_SPEC_RE.search(spec.get("file") or ""):
                setup_failed += 1
        elif ok is True and retried:
            flaky += 1
    return SpecCounts(total, failed, flaky, setup_failed)


def saas_surface_from_counts(counts: SpecCounts, *, has_artifacts: bool) -> str:
    """Sub-classify a SaaS downstream failure from real spec counts.

    Deliberately computed here rather than read from the pipeline's own
    `downstream_category`: that value is produced by a one-level spec walk, so
    `product` and `mixed` are unreachable and every failure reads as
    `infrastructure`.
    """
    if not has_artifacts or counts.total == 0:
        return SURFACE_SAAS_INFRA
    if counts.failed == 0:
        return SURFACE_SAAS_INFRA
    if counts.setup_failed == counts.failed:
        return SURFACE_SAAS_PROVISIONING
    return SURFACE_SAAS_E2E


# ---------------------------------------------------------------------------
# Spec → source path
# ---------------------------------------------------------------------------

_ROOTDIR_SUITE_RE = re.compile(r"/dist/tests/(?P<suite>(?:SM-|c8Run-)?\d+\.\d+)/?$")


def suite_from_rootdir(root_dir: str | None) -> str | None:
    """Extract the test-suite directory (e.g. `SM-8.10`) from `config.rootDir`.

    The helm chart points Playwright at the published npm package, so the report's
    `file` fields are bare basenames like `smoke-tests.spec.js`. `rootDir` carries
    the resolved suite directory and is the reliable way to recover it.
    """
    if not root_dir:
        return None
    match = _ROOTDIR_SUITE_RE.search(root_dir.rstrip())
    return match.group("suite") if match else None


def source_spec_path(spec_file: str, *, suite: str | None) -> str:
    """Map a report `file` value to its path in the e2e test repository.

    The npm package ships compiled `.js`; the source is `.ts`.
    """
    name = (spec_file or "").strip()
    if not name:
        return ""
    if name.endswith(".spec.js"):
        name = name[: -len(".spec.js")] + ".spec.ts"
    if "/" in name:
        return name
    return f"tests/{suite}/{name}" if suite else name


# ---------------------------------------------------------------------------
# Failing-spec extraction
# ---------------------------------------------------------------------------

_ANSI_RE = re.compile(r"\x1b\[[0-9;]*[A-Za-z]")


def clean_error(message: str | None, *, limit: int = 600) -> str:
    """Strip ANSI sequences and control characters, then truncate."""
    text = _ANSI_RE.sub("", message or "")
    text = "".join(ch for ch in text if ch >= " " or ch == "\n")
    return text[:limit]


@dataclass
class FailingSpec:
    file: str
    test_name: str
    error: str = ""
    project: str = ""
    attempts: int = 0
    statuses: list[str] = field(default_factory=list)

    @property
    def deterministic(self) -> bool:
        """True when every attempt failed.

        A `failed → passed` sequence is flakiness, which calls for a waiting or
        retry fix rather than a behavioural change.
        """
        return bool(self.statuses) and all(s == "failed" for s in self.statuses)


def failing_specs(report: Any, *, suite: str | None = None) -> list[FailingSpec]:
    """Extract failing specs with their retry history."""
    out: list[FailingSpec] = []
    for spec in iter_specs(report):
        if spec.get("ok") is not False:
            continue
        tests = spec.get("tests") or []
        first = tests[0] if tests else {}
        results = (first or {}).get("results") or []
        last = results[-1] if results else {}
        out.append(
            FailingSpec(
                file=source_spec_path(spec.get("file") or "", suite=suite),
                test_name=spec.get("title") or "",
                error=clean_error(((last or {}).get("error") or {}).get("message")),
                project=(first or {}).get("projectName") or "",
                attempts=len(results),
                statuses=[r.get("status") or "" for r in results],
            )
        )
    return out


# ---------------------------------------------------------------------------
# Fingerprints
# ---------------------------------------------------------------------------


def fingerprint(*parts: str) -> str:
    """Stable 8-character id used for dedupe and for the PR coverage block.

    Failing *step* names are deliberately not an input: they are frequently
    absent (a cancelled job, or a job whose steps never ran), which would make
    the same failure hash differently between runs.
    """
    joined = "::".join(p or "" for p in parts)
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()[:8]


def spec_fingerprint(base_ref: str, surface: str, file: str, test_name: str) -> str:
    return fingerprint(base_ref, surface, file, test_name)


def job_fingerprint(base_ref: str, surface: str, job_name: str) -> str:
    return fingerprint(base_ref, surface, job_leaf_name(job_name))


# ---------------------------------------------------------------------------
# Blame
# ---------------------------------------------------------------------------

_BACKPORT_TITLE_RE = re.compile(r"^\[Backport [^\]]+\]\s.*\(#(?P<number>\d+)\)\s*$")


def is_bot(login: str | None) -> bool:
    return bool(login) and login.endswith("[bot]")


def originating_pr(prs: list[dict], head_sha: str) -> dict | None:
    """Pick the PR that actually produced `head_sha`.

    `/commits/{sha}/pulls` also returns open PRs that merely contain the commit
    as an ancestor, so match on `merge_commit_sha` and fall back to the first
    entry only when nothing matches.
    """
    for pr in prs:
        if pr.get("merge_commit_sha") == head_sha:
            return pr
    return prs[0] if prs else None


def backported_pr_number(title: str | None) -> int | None:
    """Return the original PR number referenced by a backport PR title."""
    match = _BACKPORT_TITLE_RE.match((title or "").strip())
    return int(match.group("number")) if match else None


@dataclass(frozen=True)
class Blame:
    #: Login to request review from; None when only a bot could be identified.
    reviewer: str | None
    #: Login to name in the PR body, even when it is a bot.
    author: str | None
    pr_number: int | None
    #: How the reviewer was resolved, for the job summary.
    via: str


def resolve_blame(
    *,
    head_sha: str,
    prs: list[dict],
    lookup_pr: Any = None,
) -> Blame:
    """Resolve the author of the change that broke the run.

    `lookup_pr` is an optional callable taking a PR number and returning a PR
    dict, used to follow a backport PR to its original. Injected so this stays
    testable without network access.
    """
    pr = originating_pr(prs, head_sha)
    if pr is None:
        return Blame(reviewer=None, author=None, pr_number=None, via="no-pr")

    author = (pr.get("user") or {}).get("login")
    number = pr.get("number")

    if not is_bot(author):
        return Blame(reviewer=author, author=author, pr_number=number, via="pr-author")

    original_number = backported_pr_number(pr.get("title"))
    if original_number and lookup_pr:
        original = lookup_pr(original_number) or {}
        original_author = (original.get("user") or {}).get("login")
        if original_author and not is_bot(original_author):
            return Blame(
                reviewer=original_author,
                author=original_author,
                pr_number=original_number,
                via="backport-original",
            )

    return Blame(reviewer=None, author=author, pr_number=number, via="bot-unresolved")
