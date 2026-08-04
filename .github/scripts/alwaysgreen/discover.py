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
from dataclasses import asdict
from pathlib import Path

import classify
import plan as planning

REPO = os.environ.get("ALWAYSGREEN_REPO", "camunda/camunda")
E2E_REPO = os.environ.get("ALWAYSGREEN_E2E_REPO", "camunda/c8-cross-component-e2e-tests")
FIX_WORKFLOW = os.environ.get("ALWAYSGREEN_FIX_WORKFLOW", "alwaysgreen-fix.yml")
FIX_LABEL = os.environ.get("ALWAYSGREEN_FIX_LABEL", "alwaysgreen-fix")


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
                cand.specs.extend(classify.failing_specs(report))
        else:
            has_reports = False

    cand.surface = classify.saas_surface_from_counts(counts, has_artifacts=has_reports)
    log(
        f"saas counts total={counts.total} failed={counts.failed} "
        f"flaky={counts.flaky} setup_failed={counts.setup_failed} -> {cand.surface}"
    )
    if cand.surface != classify.SURFACE_SAAS_E2E:
        # Only a genuine non-setup test failure is actionable in test code.
        cand.specs = []
    return cand


# ---------------------------------------------------------------------------
# Dedupe inputs
# ---------------------------------------------------------------------------


def covered_fingerprints() -> set[str]:
    prs = gh_json(
        [
            "pr", "list", "--repo", REPO,
            "--search", f"label:{FIX_LABEL} is:open",
            "--limit", "100", "--json", "number,body",
        ],
        [],
    )
    out: set[str] = set()
    for pr in prs if isinstance(prs, list) else []:
        out |= planning.parse_coverage_block(pr.get("body"))
    return out


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

        result = planning.plan_dispatches(
            candidates,
            covered_fingerprints=covered_fingerprints(),
            inflight_keys=keys,
            product_bug_fingerprints=product_bug_fingerprints(),
            max_dispatches=args.max_dispatches,
        )
        result.noise = noise

        run = gh_json(["api", f"repos/{REPO}/actions/runs/{args.run_id}"], {})
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
