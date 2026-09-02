#!/usr/bin/env python3
"""Discover AlwaysGreen failures in a run and emit dispatch payloads.

The I/O shell around `classify` and `plan`: everything here talks to `gh` or the
filesystem, and every decision is delegated to those two modules so the rules stay
unit-tested.

Usage:
    discover.py --run-id 123 --base-ref main [--out plan.json] [--max-dispatches 2]

Writes a JSON object to --out (default stdout):

    {"dispatches": [...], "suppressed": [...], "noise": [...], "blame": {...}}

Exit status is 0 even when nothing is dispatchable — an empty plan is a normal
outcome, not an error.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import tempfile
from datetime import datetime, timedelta, timezone
from dataclasses import asdict
from pathlib import Path

import classify
import plan as planning

REPO = os.environ.get("ALWAYSGREEN_REPO", "camunda/camunda")
E2E_REPO = os.environ.get("ALWAYSGREEN_E2E_REPO", "camunda/c8-cross-component-e2e-tests")
FIX_WORKFLOW = os.environ.get("ALWAYSGREEN_FIX_WORKFLOW", "alwaysgreen-fix.yml")
FIX_LABEL = os.environ.get("ALWAYSGREEN_FIX_LABEL", "alwaysgreen-fix")
#: Prefix of the per-dispatch-key label the fix workflow stamps on every PR it opens.
KEY_LABEL_PREFIX = "alwaysgreen-key:"
#: How long a no-fix verdict keeps its fingerprints out of dispatch.
#:
#: A no-fix verdict records no issue and nothing to close — FIX-AGENT.md is explicit
#: that it "expires on its own" — so it is a statement about the conditions the agent
#: met, not about the test. Conditions move on a scale of hours, and seven days let a
#: transient one mask a week of later failures: run 33464059381 met a Web Modeler
#: `/api/internal/login` 500, correctly declined to mask it, and thereby suppressed
#: both SaaS smoke tests until 2026-09-08 — long after the outage had cleared.
#:
#: The pipeline runs every 20-40 minutes and an agent takes 7-14, so a genuinely
#: persistent problem costs a few dispatches a day at this window rather than one a
#: week. If that proves too eager, the answer is an escalation ladder — stop
#: dispatching after N consecutive no-fix verdicts on one fingerprint and tell a
#: human — not a longer blind window.
NO_FIX_COOLDOWN_HOURS = int(os.environ.get("ALWAYSGREEN_NO_FIX_COOLDOWN_HOURS", "6"))

#: The knob changed unit with the window, so the old name is refused rather than
#: converted: reading `..._DAYS=7` as 168 hours would restore the very window this
#: replaced. Refusing it silently would be indistinguishable from it working, so a
#: leftover setting says so in the run instead.
if os.environ.get("ALWAYSGREEN_NO_FIX_COOLDOWN_DAYS"):
    print(
        "::warning::ALWAYSGREEN_NO_FIX_COOLDOWN_DAYS is no longer read. The no-fix "
        "cooldown is now set in hours via ALWAYSGREEN_NO_FIX_COOLDOWN_HOURS "
        f"(currently {NO_FIX_COOLDOWN_HOURS}h).",
        file=sys.stderr,
    )
#: How long an open fix PR keeps holding its dispatch key; see
#: planning.PR_LOCK_TTL_DAYS. Set to 0 to restore the old never-expiring lock.
PR_LOCK_TTL_DAYS = int(
    os.environ.get("ALWAYSGREEN_PR_LOCK_TTL_DAYS", str(planning.PR_LOCK_TTL_DAYS))
)
#: Cap on artifact downloads while reading past verdicts, so a burst of agent runs
#: cannot make triage slow.
NO_FIX_MAX_RUNS = 20
#: Repos a fix PR can land in, mirroring the fix workflow's own label list.
FIX_PR_REPOS = [
    REPO,
    E2E_REPO,
    os.environ.get("ALWAYSGREEN_HELM_REPO", "camunda/camunda-platform-helm"),
]


def log(message: str) -> None:
    print(message, file=sys.stderr)


def gh_json_ex(args: list[str], default) -> tuple[object, str]:
    """Run a gh command expecting JSON. Returns (value, error_text)."""
    try:
        out = subprocess.run(
            ["gh", *args], capture_output=True, text=True, timeout=120, check=True
        ).stdout.strip()
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as exc:
        stderr = (getattr(exc, "stderr", "") or str(exc)).strip()
        log(f"::warning::gh {' '.join(args[:3])} failed: {stderr[:200]}")
        return default, stderr
    if not out:
        return default, ""
    try:
        return json.loads(out), ""
    except json.JSONDecodeError:
        log(f"::warning::gh {' '.join(args[:3])} returned non-JSON output")
        return default, "invalid json"


def gh_text_ex(args: list[str]) -> tuple[str | None, str]:
    """Run a gh command expecting plain text. Returns (text, error_text).

    Job logs are large, so the timeout is longer than the JSON helper's; None means the
    call failed and the caller must not treat an empty log as "nothing was wrong".
    """
    try:
        return subprocess.run(
            ["gh", *args], capture_output=True, text=True, timeout=300, check=True
        ).stdout, ""
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError) as exc:
        return None, (getattr(exc, "stderr", "") or str(exc)).strip()


def gh_json(args: list[str], default):
    """Run a gh command expecting JSON on stdout. Returns `default` on any failure."""
    value, _ = gh_json_ex(args, default)
    return value


def download_artifacts(run_id: str, repo: str, pattern: str, dest: Path) -> bool:
    dest.mkdir(parents=True, exist_ok=True)
    try:
        subprocess.run(
            [
                "gh", "run", "download", str(run_id),
                "--repo", repo, "--pattern", pattern, "--dir", str(dest),
            ],
            capture_output=True, text=True, timeout=300, check=True,
        )
        return True
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired, OSError):
        return False


def read_json_files(root: Path, filename: str) -> list[dict]:
    out = []
    for path in sorted(root.rglob(filename)):
        try:
            out.append(json.loads(path.read_text()))
        except (json.JSONDecodeError, OSError):
            log(f"::warning::could not parse {path}")
    return out


# ---------------------------------------------------------------------------
# Failing jobs
# ---------------------------------------------------------------------------


def failure_annotations(check_run_url: str | None) -> list[str]:
    if not check_run_url:
        return []
    path = check_run_url.replace("https://api.github.com/", "")
    data = gh_json(["api", f"{path}/annotations"], [])
    if not isinstance(data, list):
        return []
    return [
        a.get("message") or ""
        for a in data
        if isinstance(a, dict) and a.get("annotation_level") == "failure"
    ]


def failing_jobs(run_id: str) -> list[dict]:
    data = gh_json(
        ["api", f"repos/{REPO}/actions/runs/{run_id}/jobs?per_page=100"], {}
    )
    return [
        j
        for j in (data.get("jobs") or [])
        if isinstance(j, dict) and j.get("conclusion") == "failure"
    ]


# ---------------------------------------------------------------------------
# Evidence
# ---------------------------------------------------------------------------


def sm_candidates(run_id: str, base_ref: str, job_name: str, workdir: Path) -> planning.Candidate:
    """Build the SM candidate from playwright-results-json on the AlwaysGreen run."""
    cand = planning.Candidate(
        base_ref=base_ref,
        surface=classify.SURFACE_SM_E2E,
        job_name=job_name,
        evidence_run_url=f"https://github.com/{REPO}/actions/runs/{run_id}",
        evidence_repo=REPO,
    )
    dest = workdir / "sm"
    if not download_artifacts(run_id, REPO, "playwright-results-json*", dest):
        log("::warning::no playwright-results-json artifact for the SM e2e failure")
        return cand

    for report in read_json_files(dest, "playwright-results.json"):
        suite = classify.suite_from_rootdir(
            ((report.get("config") or {}).get("rootDir")) if isinstance(report, dict) else None
        )
        cand.specs.extend(classify.failing_specs(report, suite=suite))
    return cand


def saas_candidate(run_id: str, base_ref: str, job_name: str, workdir: Path) -> planning.Candidate:
    """Build the SaaS candidate by following the downstream run into the e2e repo.

    The surface is recomputed from the downstream report rather than taken from the
    pipeline's own `downstream_category`, which cannot express `product` or `mixed`.
    """
    cand = planning.Candidate(
        base_ref=base_ref, surface=classify.SURFACE_SAAS_INFRA, job_name=job_name
    )

    cat_dir = workdir / "saas-category"
    downstream_url = ""
    if download_artifacts(run_id, REPO, "alwaysgreen-saas-category", cat_dir):
        for blob in read_json_files(cat_dir, "alwaysgreen-saas-category.json"):
            downstream_url = blob.get("downstream_run_url") or downstream_url

    if not downstream_url:
        log("::warning::no downstream SaaS run URL; treating as saas-infra")
        return cand

    cand.evidence_run_url = downstream_url
    cand.evidence_repo = E2E_REPO
    downstream_id = downstream_url.rstrip("/").rsplit("/", 1)[-1]

    arts = gh_json(
        ["api", f"repos/{E2E_REPO}/actions/runs/{downstream_id}/artifacts?per_page=100"],
        {},
    )
    names = [a.get("name", "") for a in (arts.get("artifacts") or [])]
    has_reports = any(n.startswith("json-report") for n in names)

    counts = classify.SpecCounts()
    if has_reports:
        dest = workdir / "saas"
        if download_artifacts(downstream_id, E2E_REPO, "json-report*", dest):
            for report in read_json_files(dest, "results.json"):
                c = classify.count_specs(report)
                counts = classify.SpecCounts(
                    counts.total + c.total,
                    counts.failed + c.failed,
                    counts.flaky + c.flaky,
                    counts.setup_failed + c.setup_failed,
                )
                suite = classify.suite_from_rootdir(
                    ((report.get("config") or {}).get("rootDir"))
                    if isinstance(report, dict)
                    else None
                )
                cand.specs.extend(classify.failing_specs(report, suite=suite))
        else:
            has_reports = False

    cand.surface = classify.saas_surface_from_counts(counts, has_artifacts=has_reports)
    log(
        f"saas counts total={counts.total} failed={counts.failed} "
        f"flaky={counts.flaky} setup_failed={counts.setup_failed} -> {cand.surface}"
    )
    if cand.surface == classify.SURFACE_SAAS_CI:
        # Every spec passed, so the report-derived specs describe nothing that
        # failed; the failing downstream jobs are the evidence instead.
        cand.specs = downstream_ci_specs(downstream_id)
        if not cand.specs:
            log("::warning::no diagnosable failing downstream job; withholding saas-ci")
            cand.surface = classify.SURFACE_SAAS_INFRA
    elif cand.surface != classify.SURFACE_SAAS_E2E:
        # Only a genuine non-setup test failure is actionable in test code.
        cand.specs = []
    return cand


#: How far back to look for the attempt that actually failed. Triage runs while
#: that attempt is still the latest, so this only matters when a run is replayed
#: after someone re-ran the downstream — walking the whole history would cost an
#: API call per attempt for no benefit.
MAX_ATTEMPT_LOOKBACK = 5


def downstream_failing_jobs(downstream_id: str, attempts: int) -> list[dict]:
    """Failing jobs of the most recent downstream attempt that had any.

    A re-run turns every job of the latest attempt green while the parent run
    keeps the conclusion triage reacted to, so reading only the latest attempt
    silently loses the evidence.
    """
    for attempt in range(attempts, max(attempts - MAX_ATTEMPT_LOOKBACK, 0), -1):
        data = gh_json(
            [
                "api",
                f"repos/{E2E_REPO}/actions/runs/{downstream_id}"
                f"/attempts/{attempt}/jobs?per_page=100",
            ],
            {},
        )
        failing = [
            j
            for j in (data.get("jobs") or [])
            if isinstance(j, dict) and j.get("conclusion") == "failure"
        ]
        if failing:
            if attempt != attempts:
                log(f"downstream re-run since triage; reading attempt {attempt}")
            return failing
    return []


def downstream_ci_specs(downstream_id: str) -> list[classify.FailingSpec]:
    """Describe the failing jobs of a downstream run whose specs all passed.

    The same noise prefilter as the parent run applies: a downstream job killed
    by a GitHub platform error or a cancellation carries no evidence and must not
    reach the agent.
    """
    run = gh_json(["api", f"repos/{E2E_REPO}/actions/runs/{downstream_id}"], {})
    workflow_path = run.get("path") or ""
    attempts = run.get("run_attempt") or 1

    specs: list[classify.FailingSpec] = []
    for job in downstream_failing_jobs(downstream_id, int(attempts)):
        steps = job.get("steps") or []
        verdict = classify.noise_verdict(
            conclusion="failure",
            step_count=len(steps),
            failure_annotations=failure_annotations(job.get("check_run_url")),
        )
        if verdict:
            log(f"downstream noise ({verdict}): {job.get('name')}")
            continue
        specs.append(
            classify.ci_job_spec(
                job.get("name") or "",
                workflow_path=workflow_path,
                failing_steps=[
                    s.get("name") or ""
                    for s in steps
                    if isinstance(s, dict) and s.get("conclusion") == "failure"
                ],
            )
        )
    return specs


# ---------------------------------------------------------------------------
# Dedupe inputs
# ---------------------------------------------------------------------------


def covered_fingerprints() -> set[str]:
    """Fingerprints claimed by an open fix PR, in any repo a fix can land in.

    Scoped to every repo in FIX_PR_REPOS, not just the monorepo: most fixes land in
    the e2e or chart repo, and reading only `REPO` made those coverage blocks
    invisible here, so their specs stayed dispatchable.
    """
    out: set[str] = set()
    for repo in FIX_PR_REPOS:
        prs = gh_json(
            [
                "pr", "list", "--repo", repo,
                "--search", f"label:{FIX_LABEL} is:open",
                "--limit", "100", "--json", "number,body",
            ],
            [],
        )
        for pr in prs if isinstance(prs, list) else []:
            out |= planning.parse_coverage_block(pr.get("body"))
    return out


def open_fix_pr_keys() -> tuple[set[str], set[str], bool]:
    """Dispatch keys that already have an open fix PR, in any repo a fix can land in.

    Read from the `alwaysgreen-key:<base_ref>:<surface>` label the fix workflow
    stamps, not from the PR body: the body's coverage block is written by the agent
    and cannot be relied on to exist.

    A PR past PR_LOCK_TTL_DAYS stops holding its key, so a fix PR left unreviewed
    cannot wedge its surface shut for good; `covered_fingerprints` still suppresses a
    repeat of the specs it already claims.

    Returns (keys, keys_with_coverage, ok). `keys_with_coverage` is the subset whose
    every holding PR claims at least one fingerprint, so `plan` can decide those keys
    per spec instead of locking the surface. A block that parses to nothing — absent,
    or present with no `fp=` line — claims nothing, and a key any of whose holders
    claims nothing stays out of the subset and keeps the coarse lock: the marker
    comment alone is not a statement of remit. As with `inflight_keys`, a failed
    lookup makes the caller suppress rather than risk a duplicate PR.
    """
    out: set[str] = set()
    uncovered: set[str] = set()
    ok = True
    now = datetime.now(timezone.utc)
    for repo in FIX_PR_REPOS:
        prs, err = gh_json_ex(
            [
                "pr", "list", "--repo", repo,
                "--search", f"label:{FIX_LABEL} is:open",
                "--limit", "100", "--json", "labels,number,createdAt,body",
            ],
            None,
        )
        if prs is None:
            log(f"::warning::open fix PR lookup failed for {repo}: {err.strip()[:200]}")
            ok = False
            continue
        for pr in prs if isinstance(prs, list) else []:
            keys: set[str] = set()
            for label in pr.get("labels") or []:
                name = (label.get("name") or "").strip()
                if name.startswith(KEY_LABEL_PREFIX):
                    key = name[len(KEY_LABEL_PREFIX) :].strip()
                    if key:
                        keys.add(key)
            if not keys:
                continue
            if planning.pr_lock_expired(
                pr.get("createdAt") or "", now, PR_LOCK_TTL_DAYS
            ):
                log(
                    f"lock expired after {PR_LOCK_TTL_DAYS}d: {repo}#{pr.get('number')} "
                    f"no longer holds {', '.join(sorted(keys))}"
                )
                continue
            out |= keys
            covered = planning.parse_coverage_block(pr.get("body"))
            if not covered:
                uncovered |= keys
            # Named in the log so a suppressed run says which PR held it shut without
            # anyone cross-listing open fix PRs by hand.
            log(
                f"open fix PR {repo}#{pr.get('number')} holds "
                f"{', '.join(sorted(keys))}: "
                + (
                    f"{len(covered)} spec(s) claimed, others dispatchable"
                    if covered
                    else "claims no specs, whole surface locked"
                )
            )
    return out, out - uncovered, ok


def inflight_keys() -> tuple[set[str], bool]:
    """Dispatch keys of in-progress fix-agent runs.

    Returns (keys, ok). On failure `ok` is False and the caller suppresses rather
    than dispatching: a duplicate PR is worse than a delay, and the next failing
    run retries in ~30-40 minutes anyway.
    """
    runs, err = gh_json_ex(
        [
            "run", "list", "--repo", REPO, "--workflow", FIX_WORKFLOW,
            "--limit", "50", "--json", "status,name,databaseId",
        ],
        None,
    )
    if runs is None:
        # A workflow with no runs yet — the state on first deployment — is not an
        # outage and must not wedge dispatch shut.
        if any(m in err.lower() for m in ("could not find any workflows", "no workflows", "404")):
            log("fix workflow has no runs yet; treating as nothing in flight")
            return set(), True
        return set(), False
    keys = {
        (r.get("name") or "").split("[", 1)[-1].split("]", 1)[0]
        for r in runs
        if r.get("status") in {"queued", "in_progress"} and "[" in (r.get("name") or "")
    }
    return {k for k in keys if k}, True


def recent_no_fix_fingerprints(workdir: Path) -> set[str]:
    """Fingerprints an agent investigated recently and could not safely fix.

    A verdict with an empty `prs` list opens no PR, so neither a coverage block nor a
    key label exists to record it, and the identical investigation was dispatched
    again on the next red run. The agent already uploads its manifest as
    `alwaysgreen-fix-<run_id>`, so its own artifacts are the record — no issue and no
    label are created anywhere, and the cooldown expires on its own rather than
    suppressing a surface indefinitely.

    Runs older than the cooldown are skipped before they are downloaded, and no more
    than NO_FIX_MAX_RUNS artifacts are fetched per triage run. Fingerprints already
    encode base_ref and surface, so verdicts from other dispatch keys cannot leak in.
    """
    runs = gh_json(
        [
            "run", "list", "--repo", REPO, "--workflow", FIX_WORKFLOW,
            "--status", "completed", "--limit", "50",
            "--json", "databaseId,createdAt",
        ],
        [],
    )
    if not isinstance(runs, list):
        return set()

    cutoff = datetime.now(timezone.utc) - timedelta(hours=NO_FIX_COOLDOWN_HOURS)
    out: set[str] = set()
    fetched = 0
    for run in runs:
        if fetched >= NO_FIX_MAX_RUNS:
            log(f"no-fix lookback capped at {NO_FIX_MAX_RUNS} runs")
            break
        created = run.get("createdAt") or ""
        try:
            when = datetime.fromisoformat(created.replace("Z", "+00:00"))
        except ValueError:
            continue
        if when < cutoff:
            continue
        run_id = run.get("databaseId")
        dest = workdir / f"verdict-{run_id}"
        if not download_artifacts(run_id, REPO, f"alwaysgreen-fix-{run_id}", dest):
            continue
        fetched += 1
        metas = read_json_files(dest, "fix-meta.json")
        fps = read_json_files(dest, "fingerprints.json")
        claimed = planning.verdict_fingerprints(
            metas[0] if metas else None, fps[0] if fps else None
        )
        if claimed:
            log(f"run {run_id} recorded no fix for {sorted(claimed)}")
        out |= claimed
    return out


def _e2e_touched_since(suite: str, since: str) -> bool:
    """True when the e2e repo changed this version's test code after `since`."""
    for path in (f"tests/{suite}", f"pages/{suite}"):
        commits = gh_json(
            [
                "api",
                f"repos/{E2E_REPO}/commits?path={path}&since={since}&per_page=5",
            ],
            [],
        )
        if isinstance(commits, list) and commits:
            shas = [str(c.get("sha") or "")[:7] for c in commits if isinstance(c, dict)]
            log(f"e2e {path} changed after the run started: {', '.join(shas)}")
            return True
    return False


def fixed_upstream_fingerprints(
    candidates: list[planning.Candidate], since: str
) -> set[str]:
    """Fingerprints whose test code was already superseded when triage ran.

    The suite executes the published `@camunda/e2e-test-suite` package, not repo
    source, so a merged fix keeps failing until the package is published. Every run
    inside that window dispatched an agent that could only conclude "already fixed,
    no diff to open" — runs 30887597964 and 31016165246 both did exactly that for
    e2e PR #2899, which merged 33 minutes after the first of them started.

    Anchored to the failing run's start, which makes it self-limiting: once the fix
    has landed, a later run that still fails started *after* the commit, nothing
    matches, and the agent is dispatched normally. It only ever withholds the window
    between a run beginning and a fix landing mid-flight.
    """
    if not since:
        return set()
    out: set[str] = set()
    touched: dict[str, bool] = {}
    for cand in candidates:
        for spec, fp in zip(cand.specs, cand.spec_fingerprints):
            suite = planning.spec_suite(spec.file)
            if not suite:
                continue
            if suite not in touched:
                touched[suite] = _e2e_touched_since(suite, since)
            if touched[suite]:
                out.add(fp)
    return out


def product_bug_fingerprints() -> set[str]:
    issues = gh_json(
        [
            "search", "issues", "nightly-product-bug is:issue",
            "--owner", "camunda", "--state", "open",
            "--limit", "200", "--json", "body",
        ],
        [],
    )
    out: set[str] = set()
    for issue in issues if isinstance(issues, list) else []:
        for line in (issue.get("body") or "").splitlines():
            if "nightly-product-bug fp=" in line:
                out.add(line.split("fp=", 1)[1].strip()[:8])
    return out


# ---------------------------------------------------------------------------
# Blame
# ---------------------------------------------------------------------------


def resolve_blame(head_sha: str) -> classify.Blame:
    prs = gh_json(["api", f"repos/{REPO}/commits/{head_sha}/pulls"], [])
    if not isinstance(prs, list):
        prs = []

    def lookup(number: int):
        return gh_json(
            ["api", f"repos/{REPO}/pulls/{number}"], {}
        )

    return classify.resolve_blame(head_sha=head_sha, prs=prs, lookup_pr=lookup)


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------


def job_log(job_id: str) -> str | None:
    """Raw log of one job, or None when it could not be read.

    A helm-install failure has no report artifact: the evidence is the job log and the
    diagnostics dump. None is kept distinct from an empty log so an unreadable one is
    reported as such instead of looking like a log that simply carried no markers.

    The job-level endpoint returns plain text; the run-level one returns a zip.
    """
    out, err = gh_text_ex(["api", f"repos/{REPO}/actions/jobs/{job_id}/logs"])
    if out is None:
        log(f"::warning::could not read log for job {job_id}: {err.strip()[:200]}")
        return None
    return out


def build_candidates(run_id: str, base_ref: str, workdir: Path):
    candidates: list[planning.Candidate] = []
    noise: list[tuple[str, str]] = []

    for job in failing_jobs(run_id):
        name = job.get("name") or ""
        surface = classify.surface_for_job(name)
        if surface is None:
            continue

        verdict = classify.noise_verdict(
            conclusion="failure",
            step_count=len(job.get("steps") or []),
            failure_annotations=failure_annotations(job.get("check_run_url")),
        )
        if verdict:
            noise.append((classify.job_leaf_name(name), verdict))
            log(f"noise ({verdict}): {classify.job_leaf_name(name)}")
            continue

        if surface == classify.SURFACE_SM_E2E:
            candidates.append(sm_candidates(run_id, base_ref, name, workdir))
        elif surface == classify.SURFACE_SAAS_E2E:
            candidates.append(saas_candidate(run_id, base_ref, name, workdir))
        elif surface == classify.SURFACE_HELM_INSTALL:
            text = job_log(str(job.get("id") or ""))
            if text is None:
                noise.append((classify.job_leaf_name(name), "helm-unreadable-log"))
                log("helm-install: log unreadable, withholding")
                continue
            verdict, detail = classify.helm_install_verdict(text)
            log(f"helm-install verdict: {verdict} ({detail})")
            if verdict != classify.HELM_ACTIONABLE:
                noise.append((classify.job_leaf_name(name), f"helm-{verdict}: {detail}"))
                continue
            candidates.append(
                planning.Candidate(
                    base_ref=base_ref, surface=surface, job_name=name, job_level=True,
                    evidence_run_url=f"https://github.com/{REPO}/actions/runs/{run_id}",
                    evidence_repo=REPO,
                )
            )
        else:
            candidates.append(
                planning.Candidate(
                    base_ref=base_ref, surface=surface, job_name=name, job_level=True,
                    evidence_run_url=f"https://github.com/{REPO}/actions/runs/{run_id}",
                    evidence_repo=REPO,
                )
            )

    return candidates, noise


def serialise(result: planning.Plan, blame: classify.Blame, run_id: str) -> dict:
    return {
        "run_url": f"https://github.com/{REPO}/actions/runs/{run_id}",
        "blame": asdict(blame),
        "dispatches": [
            {
                "base_ref": c.base_ref,
                "surface": c.surface,
                "dispatch_key": c.key,
                "job_name": classify.job_leaf_name(c.job_name),
                "also_failing_jobs": [
                    classify.job_leaf_name(n) for n in c.also_failing_jobs
                ],
                "evidence_run_url": c.evidence_run_url,
                "evidence_repo": c.evidence_repo,
                "job_level": c.job_level,
                "fingerprints": c.fingerprints,
                "test_specs": [
                    {
                        "file": s.file,
                        "test_name": s.test_name,
                        "error": s.error,
                        "project": s.project,
                        "attempts": s.attempts,
                        "statuses": s.statuses,
                        "deterministic": s.deterministic,
                    }
                    for s in c.specs
                ],
            }
            for c in result.dispatches
        ],
        "suppressed": [
            {
                "surface": s.candidate.surface,
                "dispatch_key": s.candidate.key,
                "reason": s.reason,
                "detail": s.detail,
            }
            for s in result.suppressed
        ],
        "noise": [{"job": j, "verdict": v} for j, v in result.noise],
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--run-id", required=True)
    ap.add_argument("--base-ref", required=True)
    ap.add_argument("--out", default="-")
    ap.add_argument("--max-dispatches", type=int, default=2)
    args = ap.parse_args()

    # Normalise before anything derives from it: the ref is part of every fingerprint
    # and is validated by the fix workflow.
    base_ref = classify.normalise_base_ref(args.base_ref)
    if base_ref != args.base_ref:
        log(f"normalised base_ref '{args.base_ref}' -> '{base_ref}'")

    with tempfile.TemporaryDirectory(prefix="alwaysgreen-") as tmp:
        workdir = Path(tmp)
        candidates, noise = build_candidates(args.run_id, base_ref, workdir)

        keys, keys_ok = inflight_keys()
        if not keys_ok:
            # Cannot prove nothing is running; suppress every candidate this tick.
            keys = {c.key for c in candidates}
            log("::warning::in-flight lookup failed; suppressing dispatch this run")

        pr_keys, pr_keys_covered, pr_keys_ok = open_fix_pr_keys()
        if not pr_keys_ok:
            pr_keys = {c.key for c in candidates}
            pr_keys_covered = set()
            log("::warning::open fix PR lookup failed; suppressing dispatch this run")

        run = gh_json(["api", f"repos/{REPO}/actions/runs/{args.run_id}"], {})
        started = run.get("run_started_at") or run.get("created_at") or ""

        result = planning.plan_dispatches(
            candidates,
            covered_fingerprints=covered_fingerprints(),
            inflight_keys=keys,
            open_pr_keys=pr_keys,
            open_pr_keys_with_coverage=pr_keys_covered,
            product_bug_fingerprints=product_bug_fingerprints(),
            recent_no_fix_fingerprints=recent_no_fix_fingerprints(workdir),
            fixed_upstream_fingerprints=fixed_upstream_fingerprints(candidates, started),
            max_dispatches=args.max_dispatches,
        )
        result.noise = noise

        blame = resolve_blame(run.get("head_sha") or "")

        payload = serialise(result, blame, args.run_id)

    text = json.dumps(payload, indent=2)
    if args.out == "-":
        print(text)
    else:
        Path(args.out).write_text(text + "\n")
    log(
        f"dispatches={len(payload['dispatches'])} "
        f"suppressed={len(payload['suppressed'])} noise={len(payload['noise'])}"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
