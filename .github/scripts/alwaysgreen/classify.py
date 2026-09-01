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
# Base ref
# ---------------------------------------------------------------------------

_QUEUE_REF_RE = re.compile(r"^gh-readonly-queue/(?P<base>.+?)/pr-\d+")


def normalise_base_ref(ref: str) -> str:
    """Reduce a git ref to the branch the failure belongs to.

    A merge_group run reports `gh-readonly-queue/<base>/pr-<n>-<sha>` as its ref, and a
    caller may pass a fully-qualified `refs/heads/<base>`. Both must collapse to the
    base branch: the ref is part of every fingerprint, so a per-PR queue ref would make
    each run's fingerprints unique and defeat dedupe entirely, and the fix workflow
    validates the value against the supported branches.
    """
    value = (ref or "").strip()
    if value.startswith("refs/heads/"):
        value = value[len("refs/heads/") :]
    match = _QUEUE_REF_RE.match(value)
    if match:
        return match.group("base")
    return value


# ---------------------------------------------------------------------------
# Surface classification
# ---------------------------------------------------------------------------

SURFACE_SM_E2E = "sm-smoke-e2e"
SURFACE_SAAS_E2E = "saas-smoke-e2e"
SURFACE_SAAS_PROVISIONING = "saas-provisioning"
SURFACE_SAAS_CI = "saas-ci"
SURFACE_SAAS_INFRA = "saas-infra"
SURFACE_HELM_INSTALL = "helm-install"
SURFACE_HELM_CLEANUP = "helm-cleanup"
SURFACE_BUILD = "build"
SURFACE_CI_INFRA = "ci-infra"

#: Surfaces dispatched to the agent. Everything else is recorded and reported but not
#: handed over. helm-install is gated further by helm_install_verdict: the failure has
#: to look like the chart rather than the cluster.
DISPATCHABLE_SURFACES = frozenset(
    {SURFACE_SM_E2E, SURFACE_SAAS_E2E, SURFACE_SAAS_CI, SURFACE_HELM_INSTALL}
)

#: A pure propagator: it fails whenever the reusable helm workflow failed and
#: carries no independent signal.
IGNORED_JOB_PREFIXES = ("Observe Helm chart Integration Tests status",)

#: Literal prefixes, deliberately stopping before the first `${{`, matched
#: against the trailing segment of a (possibly nested) job name. An entry may
#: be a compiled regex instead of a literal string when the interpolated job
#: name has a rendered value (not just an unrendered `${{ }}`) in the middle
#: of the fragment being matched, e.g. `${{ matrix.suite }}` in the SM e2e job
#: name (camunda-platform-helm#6841) — a plain prefix can't skip over that.
_SURFACE_PREFIXES: tuple[tuple[str | re.Pattern[str], str], ...] = (
    (re.compile(r"^Playwright e2e .*after install\b"), SURFACE_SM_E2E),
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
        if isinstance(prefix, re.Pattern):
            if prefix.match(leaf):
                return surface
        elif leaf.startswith(prefix):
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

#: Error signatures that mark a failed setup spec as an environment problem the fix
#: agent has no lever on, matched against the spec's last attempt error.
#:
#: The file name alone is not the signal. `tests/8.x/test-setup.spec.ts` mixes two
#: unrelated families: cluster/org provisioning ("Create Default Cluster"), and
#: ordinary Playwright UI flows ("Create Project Folder for User N", which drives
#: `ModelerHomePage.createCrossComponentProjectFolder`). Only the first is reliably
#: environment. Sampled over the 15 failing SaaS downstream runs to 2026-09-01, the
#: failing setup specs were 10 cluster-health assertions and 4 locator clicks in the
#: Modeler flow.
#:
#: Those 4 are not one thing, and the message does not separate them: on run
#: 33483343722 the trace shows the app healthy and the button present, failing on a
#: retry where the project already existed (a test bug), while run 33463316996 shows
#: the identical message caused by a Modeler `/api/internal/login` 500 that left the
#: page on its loading spinner (an outage). Triage sees only the report and cannot
#: tell those apart; the fix agent reads the trace and can, and its `no-fix` verdict
#: plus the cooldown bound the cost of guessing wrong. Dropping the whole family on
#: the file name loses the fixable half silently, and is already inconsistent with
#: the same failure arriving via `smoke-tests.spec.ts`, which is dispatched.
_PROVISIONING_ERROR_MARKERS = (
    # Cluster came up but never reached a healthy status.
    'Received: "Unhealthy"',
    # Auth0/identity provider refused the login the whole suite depends on.
    "AUTH0_RATE_LIMIT",
    "identity provider returned an error page",
)


def is_provisioning_error(message: str | None) -> bool:
    """Whether a failed setup spec's error points at the environment, not the test.

    Deliberately narrow: anything unrecognised reads as a test failure and gets an
    agent. The reverse bias is what let a renamed button sit red across three runs
    with no dispatch — an agent spent on an environment failure costs one run and a
    `no-fix` verdict that then suppresses the surface for the cooldown, while a
    missed test failure is silent until someone reads the nightly by hand.

    A login that failed on a changed selector therefore still dispatches: its
    message carries the locator, not an identity-provider marker.

    Matched against the ANSI-stripped message. Playwright colours its assertion
    diff and highlights the differing run *inside* the word, so the raw text of a
    cluster-health failure carries colour codes both around and within
    `"Unhealthy"` and no marker matches until they are stripped.
    """
    text = clean_error(message, limit=4000)
    return any(marker in text for marker in _PROVISIONING_ERROR_MARKERS)


def _last_error_message(spec: dict) -> str:
    """The error of a spec's final attempt, which is the one that made it fail."""
    tests = spec.get("tests") or []
    first = tests[0] if tests else {}
    results = (first or {}).get("results") or []
    last = results[-1] if results else {}
    return ((last or {}).get("error") or {}).get("message") or ""


@dataclass(frozen=True)
class SpecCounts:
    total: int = 0
    failed: int = 0
    flaky: int = 0
    provisioning_failed: int = 0


def count_specs(report: Any) -> SpecCounts:
    """Count specs in a report, mirroring the categories the pipeline reports."""
    total = failed = flaky = provisioning_failed = 0
    for spec in iter_specs(report):
        total += 1
        ok = spec.get("ok")
        tests = spec.get("tests") or []
        retried = any(len((t or {}).get("results") or []) > 1 for t in tests)
        if ok is False:
            failed += 1
            if _SETUP_SPEC_RE.search(spec.get("file") or "") and is_provisioning_error(
                _last_error_message(spec)
            ):
                provisioning_failed += 1
        elif ok is True and retried:
            flaky += 1
    return SpecCounts(total, failed, flaky, provisioning_failed)


def saas_surface_from_counts(counts: SpecCounts, *, has_artifacts: bool) -> str:
    """Sub-classify a SaaS downstream failure from real spec counts.

    Deliberately computed here rather than read from the pipeline's own
    `downstream_category`: that value is produced by a one-level spec walk, so
    `product` and `mixed` are unreachable and every failure reads as
    `infrastructure`.

    Zero failing specs is not the same as nothing to fix. When the reports exist
    and every spec passed, the run went red on a job around the tests — org
    cleanup, report merge, a TestRail upload — which is a bug in a workflow this
    org owns and is dispatched as `saas-ci`. Only a run with no reports at all
    stays `saas-infra`: there the downstream died before Playwright produced any
    evidence, and the failing job is as likely to be the provisioning API as
    anything in the repository.

    `saas-provisioning` needs every failing spec to be a provisioning failure by
    its *error* (see `is_provisioning_error`), not merely by living in
    `test-setup.spec.ts`. A run that trips one recognised environment error and one
    locator failure is a run with a test bug in it, and goes to the agent.
    """
    if not has_artifacts or counts.total == 0:
        return SURFACE_SAAS_INFRA
    if counts.failed == 0:
        return SURFACE_SAAS_CI
    if counts.provisioning_failed == counts.failed:
        return SURFACE_SAAS_PROVISIONING
    return SURFACE_SAAS_E2E


# ---------------------------------------------------------------------------
# Helm install: chart problem or cluster problem?
# ---------------------------------------------------------------------------

HELM_ACTIONABLE = "actionable"
HELM_INFRASTRUCTURE = "infrastructure"

#: A failing install is worth an agent only when something points at the chart or its
#: values. Every helm-install failure sampled on main and stable/8.8 in 2026-07/08 was
#: the cluster failing to place or attach the workload instead, so the default is to
#: withhold: dispatching there costs an agent run and can only produce a guess.
_HELM_CONFIG_MARKERS = (
    "INSTALLATION FAILED",
    "UPGRADE FAILED",
    # helm install prefixes schema errors with INSTALLATION FAILED, but template and
    # dry-run print the bare message, so match that too.
    "values don't meet the specifications",
    "YAML parse error",
    "error validating data",
    "error converting YAML",
    "CreateContainerConfigError",
    "CrashLoopBackOff",
    "Back-off restarting failed container",
    "couldn't find key",
    "is invalid:",
)

#: Only used to explain the verdict; the decision does not depend on them, so an
#: unseen infrastructure shape still withholds rather than dispatching.
_HELM_INFRA_MARKERS = (
    "FailedScheduling",
    "Insufficient cpu",
    "Insufficient memory",
    "untolerated taint",
    "node affinity/selector",
    "Multi-Attach error",
    "FailedAttachVolume",
    "ImagePullBackOff",
    "ErrImagePull",
    "TLS handshake timeout",
    "context deadline exceeded",
)


def helm_install_verdict(log_text: str) -> tuple[str, str]:
    """Classify a failed Helm install from its job log.

    Returns (verdict, detail). A chart marker wins over an infrastructure one: a values
    bug can crash a pod that the scheduler also complained about, and the chart reading
    is the one an agent can act on.
    """
    text = log_text or ""
    for marker in _HELM_CONFIG_MARKERS:
        if marker in text:
            return HELM_ACTIONABLE, marker
    for marker in _HELM_INFRA_MARKERS:
        if marker in text:
            return HELM_INFRASTRUCTURE, marker
    return HELM_INFRASTRUCTURE, "no chart-level failure signal"


# ---------------------------------------------------------------------------
# Spec → source path
# ---------------------------------------------------------------------------

_ROOTDIR_SUITE_RE = re.compile(r"/tests/(?P<suite>(?:SM-)?\d+\.\d+)/?$")


def suite_from_rootdir(root_dir: str | None) -> str | None:
    """Extract the test-suite directory (e.g. `SM-8.10`) from `config.rootDir`.

    Both surfaces report bare basenames like `smoke-tests.spec.js`: the helm chart
    points Playwright at the published npm package (`.../dist/tests/SM-8.10`) and
    the SaaS run executes in the e2e repo checkout (`.../tests/8.10`). `rootDir` is
    the reliable way to recover the suite in either layout. AlwaysGreen runs only
    SM and SaaS, so no c8Run prefix is matched.
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


def ci_job_spec(
    job_name: str, *, workflow_path: str, failing_steps: Iterable[str]
) -> FailingSpec:
    """Represent a failing CI job as a spec, for a run that had no failing test.

    A `saas-ci` failure has no Playwright spec to name, so the job stands in for
    one: `file` is the workflow that owns the job, which is the file the fix has
    to change. `statuses` is a single failed attempt, so `deterministic` is True
    and a CI bug is never mistaken for flakiness and "fixed" with a longer wait.
    """
    steps = [s for s in failing_steps if s]
    return FailingSpec(
        file=workflow_path,
        test_name=job_leaf_name(job_name),
        error=(
            f"CI job failed at step: {', '.join(steps)}"
            if steps
            else "CI job failed with no failing step recorded"
        ),
        attempts=1,
        statuses=["failed"],
    )


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
