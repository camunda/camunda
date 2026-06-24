#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, asdict
from datetime import datetime, timedelta, timezone
from functools import partial
from typing import Any, Dict, List, Optional, Tuple

import requests

GITHUB_API = "https://api.github.com"
DEFAULT_ACCEPT = "application/vnd.github+json"


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


def parse_github_datetime(s: str) -> datetime:
    if s.endswith("Z"):
        s = s[:-1] + "+00:00"
    return datetime.fromisoformat(s)


class GitHubAPIError(RuntimeError):
    pass


class GitHubClient:
    def __init__(self, token: Optional[str], base_url: str = GITHUB_API, timeout: int = 30):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.session = requests.Session()
        self.session.headers.update(
            {"Accept": DEFAULT_ACCEPT, "User-Agent": "main-branch-health-script"}
        )
        if token:
            self.session.headers["Authorization"] = f"Bearer {token}"

    def get(self, path: str, params: Optional[dict] = None) -> requests.Response:
        url = f"{self.base_url}{path}"
        resp = self.session.get(url, params=params, timeout=self.timeout)
        if resp.status_code >= 400:
            try:
                body = resp.json()
            except Exception:
                body = resp.text[:500]
            raise GitHubAPIError(f"GitHub API error {resp.status_code} for {url}: {body}")
        return resp

    def get_json(self, path: str, params: Optional[dict] = None) -> Any:
        return self.get(path, params=params).json()


@dataclass(frozen=True)
class CommitRef:
    sha: str
    committed_at: datetime
    html_url: str


@dataclass
class CommitHealth:
    sha: str
    short_sha: str
    committed_at: str
    commit_url: str
    success: int
    total: int
    overall: str  # success|failure|nodata
    conclusions: Dict[str, int]
    failed_jobs: List[str]
    source: str  # checks|statuses|none


def past_hours_window_utc(hours: int) -> Tuple[datetime, datetime]:
    """Returns [start,end) in UTC for the past N hours."""
    end_utc = utc_now()
    start_utc = end_utc - timedelta(hours=hours)
    return start_utc, end_utc


def list_commits_page(
    gh: GitHubClient, owner: str, repo: str, branch: str, page: int, per_page: int
) -> List[CommitRef]:
    data = gh.get_json(
        f"/repos/{owner}/{repo}/commits",
        params={"sha": branch, "page": page, "per_page": per_page},
    )
    if not isinstance(data, list):
        raise GitHubAPIError(f"Expected list response for commits page, got: {type(data)}")

    out: List[CommitRef] = []
    for c in data:
        sha = c["sha"]
        html_url = c.get("html_url", f"https://github.com/{owner}/{repo}/commit/{sha}")

        commit = c["commit"]
        dt_str = (commit.get("committer") or {}).get("date") or (commit.get("author") or {}).get("date")
        if not dt_str:
            continue
        committed_at = parse_github_datetime(dt_str)

        out.append(CommitRef(sha=sha, committed_at=committed_at, html_url=html_url))
    return out


def list_commits_from_window(
    gh: GitHubClient,
    owner: str,
    repo: str,
    branch: str,
    start_utc: datetime,
    end_utc: datetime,
    per_page: int,
    max_pages: int,
) -> Tuple[List[CommitRef], int]:
    """
    Pages through commits newest->older, collects all commits in [start_utc,end_utc),
    and stops once we are older than start_utc (i.e., once the window is fully covered).
    Returns (commits, pages_scanned).
    """
    results: List[CommitRef] = []
    pages_scanned = 0

    for page in range(1, max_pages + 1):
        commits = list_commits_page(gh, owner, repo, branch, page=page, per_page=per_page)
        pages_scanned = page

        if not commits:
            break

        stop = False
        for c in commits:
            if c.committed_at >= end_utc:
                # today (or later) in the chosen tz -> ignore
                continue
            if start_utc <= c.committed_at < end_utc:
                results.append(c)
                continue
            if c.committed_at < start_utc:
                # we've gone older than yesterday -> we can stop scanning
                stop = True
                break

        if stop:
            break

    # Keep newest->oldest ordering in output (same as scan order already)
    return results, pages_scanned


def list_completed_check_runs_for_commit(
    gh: GitHubClient, owner: str, repo: str, sha: str
) -> List[Dict[str, Any]]:
    suites = gh.get_json(
        f"/repos/{owner}/{repo}/commits/{sha}/check-suites",
        params={"per_page": 100},
    )
    check_suites = suites.get("check_suites", []) or []

    all_runs: List[Dict[str, Any]] = []
    for suite in check_suites:
        suite_id = suite["id"]
        workflow_name = suite.get("workflow_name")
        suite_runs = list_check_runs_for_suite(gh, owner, repo, suite_id)
        if workflow_name:
            for run in suite_runs:
                run.setdefault("workflow_name", workflow_name)
        all_runs.extend(suite_runs)

    return [r for r in all_runs if r.get("status") == "completed"]


def list_check_runs_for_suite(
    gh: GitHubClient, owner: str, repo: str, suite_id: int, per_page: int = 100
) -> List[Dict[str, Any]]:
    runs: List[Dict[str, Any]] = []
    for page in range(1, 51):
        payload = gh.get_json(
            f"/repos/{owner}/{repo}/check-suites/{suite_id}/check-runs",
            params={"per_page": per_page, "page": page},
        )
        page_runs = payload.get("check_runs", []) or []
        runs.extend(page_runs)
        if len(page_runs) < per_page:
            break
    return runs


def debug_check_metadata(gh: GitHubClient, owner: str, repo: str, sha: str) -> None:
    suites = gh.get_json(
        f"/repos/{owner}/{repo}/commits/{sha}/check-suites",
        params={"per_page": 100},
    )
    check_suites = suites.get("check_suites", []) or []
    if not check_suites:
        print("No check suites found for debug.")
        return

    print(f"Check suites found: {len(check_suites)}")
    suites_with_runs = []
    total_runs = 0
    for suite in check_suites:
        suite_id = suite["id"]
        suite_runs = list_check_runs_for_suite(gh, owner, repo, suite_id)
        total_runs += len(suite_runs)
        suites_with_runs.append((suite, suite_runs))

    print(f"Total check runs found across suites: {total_runs}")

    suite = next((s for s, runs in suites_with_runs if runs), check_suites[0])
    suite_keys = sorted(suite.keys())
    print("Sample check suite keys:")
    print(", ".join(suite_keys))
    print()
    print("Sample check suite workflow/app info:")
    print(
        json.dumps(
            {
                "workflow_name": suite.get("workflow_name"),
                "workflow_run": (suite.get("workflow_run") or {}),
                "app": (suite.get("app") or {}),
            },
            indent=2,
        )
    )

    check_runs = next((runs for _, runs in suites_with_runs if runs), [])
    if not check_runs:
        print("No check runs found for debug.")
        return

    run = check_runs[0]
    run_keys = sorted(run.keys())
    print("Sample check run keys:")
    print(", ".join(run_keys))
    print()
    print("Sample check run workflow/app info:")
    print(
        json.dumps(
            {
                "name": run.get("name"),
                "workflow_name": run.get("workflow_name"),
                "workflow_run": (run.get("workflow_run") or {}),
                "app": (run.get("app") or {}),
                "check_suite": (run.get("check_suite") or {}),
            },
            indent=2,
        )
    )


def summarize_check_runs(
    check_runs: List[Dict[str, Any]]
) -> Tuple[int, int, Dict[str, int], List[str]]:
    conclusions: Dict[str, int] = {}
    total = 0
    success = 0
    failed_jobs: List[str] = []
    for r in check_runs:
        if r.get("status") != "completed":
            continue
        conc = r.get("conclusion") or "unknown"
        conclusions[conc] = conclusions.get(conc, 0) + 1
        if conc == "skipped":
            continue
        total += 1
        if conc == "success":
            success += 1
        else:
            name = r.get("name") or "unknown"
            check_suite = r.get("check_suite") or {}
            run_app_name = (r.get("app") or {}).get("name")
            suite_app_name = (check_suite.get("app") or {}).get("name")
            workflow = (
                r.get("workflow_name")
                or (r.get("workflow_run") or {}).get("name")
                or (run_app_name if run_app_name and run_app_name != "GitHub Actions" else None)
                or check_suite.get("workflow_name")
                or (check_suite.get("workflow_run") or {}).get("name")
                or (suite_app_name if suite_app_name and suite_app_name != "GitHub Actions" else None)
            )
            if workflow:
                failed_jobs.append(f"{workflow} / {name} ({conc})")
            else:
                failed_jobs.append(f"{name} ({conc})")
    return success, total, conclusions, failed_jobs


def get_combined_status_summary(
    gh: GitHubClient, owner: str, repo: str, sha: str
) -> Optional[Tuple[int, int, Dict[str, int], List[str]]]:
    combined = gh.get_json(f"/repos/{owner}/{repo}/commits/{sha}/status")
    statuses = combined.get("statuses", []) or []
    if not statuses:
        return None

    total = 0
    success = 0
    conclusions: Dict[str, int] = {}
    failed_jobs: List[str] = []
    for s in statuses:
        state = s.get("state") or "unknown"
        total += 1
        conclusions[state] = conclusions.get(state, 0) + 1
        if state == "success":
            success += 1
        else:
            context = s.get("context") or "unknown"
            failed_jobs.append(f"{context} ({state})")
    return success, total, conclusions, failed_jobs


def overall_from_counts(total: int, success: int) -> str:
    if total == 0:
        return "nodata"
    return "success" if success == total else "failure"


def compute_health_for_commit(
    gh: GitHubClient, owner: str, repo: str, commit: CommitRef, fallback_to_statuses: bool
) -> CommitHealth:
    sha = commit.sha
    short_sha = sha[:7]

    try:
        check_runs = list_completed_check_runs_for_commit(gh, owner, repo, sha)
        success, total, conclusions, failed_jobs = summarize_check_runs(check_runs)

        if total == 0 and fallback_to_statuses:
            fb = get_combined_status_summary(gh, owner, repo, sha)
            if fb is not None:
                s2, t2, conc2, failed2 = fb
                return CommitHealth(
                    sha=sha,
                    short_sha=short_sha,
                    committed_at=commit.committed_at.isoformat(),
                    commit_url=commit.html_url,
                    success=s2,
                    total=t2,
                    overall=overall_from_counts(t2, s2),
                    conclusions=conc2,
                    failed_jobs=failed2,
                    source="statuses",
                )

        return CommitHealth(
            sha=sha,
            short_sha=short_sha,
            committed_at=commit.committed_at.isoformat(),
            commit_url=commit.html_url,
            success=success,
            total=total,
            overall=overall_from_counts(total, success),
            conclusions=conclusions,
            failed_jobs=failed_jobs,
            source="checks",
        )
    except GitHubAPIError:
        if fallback_to_statuses:
            fb = get_combined_status_summary(gh, owner, repo, sha)
            if fb is not None:
                s2, t2, conc2, failed2 = fb
                return CommitHealth(
                    sha=sha,
                    short_sha=short_sha,
                    committed_at=commit.committed_at.isoformat(),
                    commit_url=commit.html_url,
                    success=s2,
                    total=t2,
                    overall=overall_from_counts(t2, s2),
                    conclusions=conc2,
                    failed_jobs=failed2,
                    source="statuses",
                )
        raise


def compute_health_for_commit_with_client(
    token: Optional[str],
    base_url: str,
    timeout: int,
    owner: str,
    repo: str,
    commit: CommitRef,
    fallback_to_statuses: bool,
) -> CommitHealth:
    gh = GitHubClient(token=token, base_url=base_url, timeout=timeout)
    return compute_health_for_commit(
        gh=gh,
        owner=owner,
        repo=repo,
        commit=commit,
        fallback_to_statuses=fallback_to_statuses,
    )


def fmt_table(rows: List[CommitHealth]) -> str:
    headers = ["sha", "committed_at", "summary", "overall", "source", "failed_jobs"]
    body = [
        [
            r.short_sha,
            r.committed_at,
            f"{r.success}/{r.total}",
            r.overall,
            r.source,
            ", ".join(r.failed_jobs) if r.failed_jobs else "-",
        ]
        for r in rows
    ]
    widths = [len(h) for h in headers]
    for row in body:
        for i, cell in enumerate(row):
            widths[i] = max(widths[i], len(str(cell)))

    def line(cols: List[str]) -> str:
        return "  ".join(str(c).ljust(widths[i]) for i, c in enumerate(cols))

    out = [line(headers), line(["-" * w for w in widths])]
    out += [line(r) for r in body]
    return "\n".join(out)


def format_failed_job_totals(rows: List[CommitHealth]) -> str:
    totals: Dict[str, int] = {}
    for r in rows:
        for job in r.failed_jobs:
            totals[job] = totals.get(job, 0) + 1

    if not totals:
        return "No failed jobs detected."

    lines = ["Failed jobs totals:"]
    for job, count in sorted(totals.items(), key=lambda item: (-item[1], item[0])):
        lines.append(f"  {job}: {count}")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description="Compute health for all commits from the past N hours.")
    ap.add_argument("--owner", default="camunda")
    ap.add_argument("--repo", default="camunda")
    ap.add_argument("--branch", default="main")
    ap.add_argument("--hours", type=int, default=3, help="Look back this many hours (default 3).")
    ap.add_argument("--per-page", type=int, default=100)
    ap.add_argument("--max-pages", type=int, default=50, help="Safety cap; increase if repo is extremely active.")
    ap.add_argument("--fallback-to-statuses", action="store_true")
    ap.add_argument("--output", choices=["json", "table"], default="table")
    ap.add_argument("--sleep-between", type=float, default=0.0)
    ap.add_argument(
        "--debug-checks",
        nargs="?",
        const="",
        help="Print sample check suite/run fields for a commit SHA (or first commit) and exit.",
    )
    ap.add_argument(
        "--workers",
        type=int,
        default=8,
        help="Number of parallel workers for commit checks (default 8).",
    )

    args = ap.parse_args()

    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    gh = GitHubClient(token=token)

    start_utc, end_utc = past_hours_window_utc(args.hours)

    commits, pages_scanned = list_commits_from_window(
        gh=gh,
        owner=args.owner,
        repo=args.repo,
        branch=args.branch,
        start_utc=start_utc,
        end_utc=end_utc,
        per_page=args.per_page,
        max_pages=args.max_pages,
    )

    if not commits:
        msg = (
            f"No commits found in past {args.hours} hours for {args.owner}/{args.repo}@{args.branch}.\n"
            f"Window (UTC): [{start_utc.isoformat()} .. {end_utc.isoformat()})\n"
            f"Pages scanned: {pages_scanned} (per_page={args.per_page}, max_pages={args.max_pages})."
        )
        print(msg)
        return 0

    if args.debug_checks is not None:
        debug_sha = args.debug_checks or commits[0].sha
        debug_check_metadata(gh, args.owner, args.repo, debug_sha)
        return 0

    results: List[CommitHealth] = []
    if args.workers <= 1:
        for c in commits:
            results.append(
                compute_health_for_commit(
                    gh=gh,
                    owner=args.owner,
                    repo=args.repo,
                    commit=c,
                    fallback_to_statuses=args.fallback_to_statuses,
                )
            )
            if args.sleep_between > 0:
                time.sleep(args.sleep_between)
    else:
        if args.sleep_between > 0:
            print("--sleep-between ignored when --workers > 1", file=sys.stderr)
        task = partial(
            compute_health_for_commit_with_client,
            token,
            gh.base_url,
            gh.timeout,
            args.owner,
            args.repo,
            fallback_to_statuses=args.fallback_to_statuses,
        )
        with ThreadPoolExecutor(max_workers=args.workers) as executor:
            results = list(executor.map(task, commits))

    if args.output == "table":
        print(fmt_table(results))
        print()
        print(format_failed_job_totals(results))
    else:
        failed_job_totals: Dict[str, int] = {}
        for r in results:
            for job in r.failed_jobs:
                failed_job_totals[job] = failed_job_totals.get(job, 0) + 1
        payload = {
            "repo": f"{args.owner}/{args.repo}",
            "branch": args.branch,
            "hours": args.hours,
            "window_utc": {"start": start_utc.isoformat(), "end": end_utc.isoformat()},
            "generated_at_utc": utc_now().isoformat(),
            "pages_scanned": pages_scanned,
            "commits_found": len(commits),
            "results": [asdict(r) for r in results],
            "failed_job_totals": failed_job_totals,
        }
        print(json.dumps(payload, indent=2))

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
