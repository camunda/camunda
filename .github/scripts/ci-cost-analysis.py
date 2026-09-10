#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import math
import re
import subprocess
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path


WORKFLOW_PATH_PREFIX = ".github/workflows/"
CI_RELEVANT_PREFIXES = (".github/workflows/", ".github/actions/")
CI_RELEVANT_EXACT = {"Jenkinsfile", "Makefile"}
EVENT_KEYS = {
    "push",
    "pull_request",
    "pull_request_target",
    "merge_group",
    "schedule",
    "workflow_dispatch",
    "workflow_call",
    "repository_dispatch",
}


def is_workflow_file(path: str) -> bool:
    return path.startswith(WORKFLOW_PATH_PREFIX) and path.endswith((".yml", ".yaml"))


@dataclass
class CommitChange:
    sha: str
    author: str
    subject: str
    file_statuses: list[tuple[str, str]]


@dataclass
class Finding:
    impact: str
    sha: str
    author: str
    pr: str
    file_path: str
    description: str
    estimated_minutes: int


def git(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True, stderr=subprocess.DEVNULL)


def maybe_git(*args: str) -> str:
    try:
        return git(*args)
    except subprocess.CalledProcessError:
        return ""


def is_ci_relevant(path: str) -> bool:
    if path in CI_RELEVANT_EXACT:
        return True
    if any(path.startswith(prefix) for prefix in CI_RELEVANT_PREFIXES):
        return True
    return path.endswith("/Jenkinsfile") or path.endswith("/Makefile")


def extract_pr(subject: str) -> str:
    match = re.search(r"\(#(\d+)\)", subject)
    if match:
        return f"#{match.group(1)}"
    match = re.search(r"#(\d+)", subject)
    return f"#{match.group(1)}" if match else "N/A"


def parse_commits(since: str) -> list[CommitChange]:
    log = git(
        "log",
        f"--since={since}",
        "--name-status",
        "--pretty=format:__COMMIT__%n%H%x1f%an%x1f%s",
        "--",
        *CI_RELEVANT_PREFIXES,
        *CI_RELEVANT_EXACT,
    )

    commits: list[CommitChange] = []
    sha = author = subject = ""
    file_statuses: list[tuple[str, str]] = []

    def flush() -> None:
        nonlocal file_statuses
        if sha and file_statuses:
            relevant = [(st, p) for st, p in file_statuses if is_ci_relevant(p)]
            if relevant:
                commits.append(CommitChange(sha=sha, author=author, subject=subject, file_statuses=relevant))
        file_statuses = []

    for line in log.splitlines():
        if line == "__COMMIT__":
            flush()
            sha = author = subject = ""
            continue
        if "\x1f" in line:
            parts = line.split("\x1f", 2)
            if len(parts) == 3:
                sha, author, subject = parts
            continue
        if not line.strip() or "\t" not in line:
            continue
        status, path = line.split("\t", 1)
        file_statuses.append((status.strip(), path.strip()))

    flush()
    return commits


def cron_field_count(field: str, minimum: int, maximum: int) -> int:
    if field == "*":
        return (maximum - minimum) + 1

    total = 0
    for chunk in field.split(","):
        chunk = chunk.strip()
        if not chunk:
            continue
        step = 1
        if "/" in chunk:
            chunk, step_text = chunk.split("/", 1)
            try:
                step = max(1, int(step_text))
            except ValueError:
                step = 1
        if chunk in ("*", ""):
            span = (maximum - minimum) + 1
        elif "-" in chunk:
            start_text, end_text = chunk.split("-", 1)
            try:
                start = int(start_text)
                end = int(end_text)
            except ValueError:
                continue
            span = max(0, end - start + 1)
        else:
            span = 1
        total += max(1, math.ceil(span / step))
    return max(total, 1)


def cron_runs_per_week(cron_expression: str) -> int:
    fields = cron_expression.split()
    if len(fields) != 5:
        return 1

    minute, hour, day_of_month, _month, day_of_week = fields
    runs_per_day = cron_field_count(minute, 0, 59) * cron_field_count(hour, 0, 23)

    if day_of_week != "*":
        days = cron_field_count(day_of_week.replace("7", "0"), 0, 6)
    elif day_of_month != "*":
        days = max(1, round((cron_field_count(day_of_month, 1, 31) * 7) / 31))
    else:
        days = 7

    return max(1, runs_per_day * days)


def parse_schedule_runs_per_week(text: str) -> int:
    crons = re.findall(r"cron:\s*['\"]([^'\"]+)['\"]", text)
    if not crons:
        return 1
    return sum(cron_runs_per_week(expr) for expr in crons)


def extract_jobs_and_timeout(text: str) -> tuple[set[str], dict[str, int], dict[str, str]]:
    lines = text.splitlines()
    jobs_indent = None
    jobs: set[str] = set()
    timeouts: dict[str, int] = {}
    runners: dict[str, str] = {}
    current_job = None
    current_job_indent = None

    for raw in lines:
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        indent = len(raw) - len(raw.lstrip(" "))

        if jobs_indent is None and stripped == "jobs:":
            jobs_indent = indent
            continue

        if jobs_indent is None:
            continue

        if indent <= jobs_indent:
            break

        if indent == jobs_indent + 2 and re.match(r"[A-Za-z0-9_-]+:\s*$", stripped):
            current_job = stripped.rstrip(":").strip()
            current_job_indent = indent
            jobs.add(current_job)
            continue

        if current_job is None:
            continue

        if current_job_indent is not None and indent <= current_job_indent:
            current_job = None
            current_job_indent = None
            continue

        timeout_match = re.match(r"timeout-minutes:\s*(\d+)", stripped)
        if timeout_match:
            timeouts[current_job] = int(timeout_match.group(1))

        runner_match = re.match(r"runs-on:\s*(.+)", stripped)
        if runner_match:
            runners[current_job] = runner_match.group(1).strip()

    return jobs, timeouts, runners


def estimate_workflow_timeout_minutes(timeouts: dict[str, int], job_count: int) -> int:
    if timeouts:
        return sum(timeouts.values())
    return max(15, job_count * 10)


def parse_sleep_minutes(line: str) -> int:
    total = 0
    for match in re.finditer(r"sleep\s+(\d+)([smhd]?)", line):
        value = int(match.group(1))
        unit = match.group(2)
        if unit == "h":
            total += value * 60
        elif unit == "d":
            total += value * 24 * 60
        elif unit == "s":
            total += math.ceil(value / 60)
        else:
            total += value
    return total


def categorize(delta_minutes: int, high_signal: bool) -> str:
    if delta_minutes < 0:
        return "Cost Reduction"
    if high_signal or delta_minutes >= 180:
        return "High"
    if delta_minutes >= 60:
        return "Moderate"
    if delta_minutes > 0:
        return "Low"
    return "Neutral"


def analyze_change(sha: str, author: str, subject: str, status: str, file_path: str) -> Finding:
    pr = extract_pr(subject)
    new_text = maybe_git("show", f"{sha}:{file_path}")
    old_text = maybe_git("show", f"{sha}^:{file_path}") if not status.startswith("A") else ""
    diff = maybe_git("show", "--unified=0", "--format=", sha, "--", file_path)

    new_jobs, new_timeouts, new_runners = extract_jobs_and_timeout(new_text)
    old_jobs, old_timeouts, _old_runners = extract_jobs_and_timeout(old_text)
    schedule_runs = parse_schedule_runs_per_week(new_text or old_text)

    reasons: list[str] = []
    delta_minutes = 0
    high_signal = False

    if status.startswith("A") and is_workflow_file(file_path):
        workflow_minutes = estimate_workflow_timeout_minutes(new_timeouts, len(new_jobs)) * max(1, schedule_runs)
        delta_minutes += workflow_minutes
        if "cron:" in new_text:
            reasons.append(f"new scheduled workflow added (~{schedule_runs} runs/week)")
        else:
            reasons.append("new workflow added")

    if is_workflow_file(file_path):
        added_jobs = sorted(new_jobs - old_jobs)
        removed_jobs = sorted(old_jobs - new_jobs)

        for job in added_jobs:
            job_timeout = new_timeouts.get(job, 15)
            job_runner = new_runners.get(job, "unknown runner")
            delta = job_timeout * max(1, schedule_runs)
            delta_minutes += delta
            reasons.append(f"new job `{job}` on `{job_runner}` (+~{delta} min/week)")
            if "self-hosted" in job_runner.lower() or "xl" in job_runner.lower():
                high_signal = True

        for job in removed_jobs:
            job_timeout = old_timeouts.get(job, 15)
            delta = job_timeout * max(1, schedule_runs)
            delta_minutes -= delta
            reasons.append(f"job `{job}` removed (-~{delta} min/week)")

        for job in sorted(new_jobs & old_jobs):
            old_timeout = old_timeouts.get(job)
            new_timeout = new_timeouts.get(job)
            if old_timeout is None or new_timeout is None or old_timeout == new_timeout:
                continue
            delta = (new_timeout - old_timeout) * max(1, schedule_runs)
            delta_minutes += delta
            direction = "increased" if delta > 0 else "reduced"
            reasons.append(f"timeout for `{job}` {direction} ({old_timeout}→{new_timeout})")

    added_sleep = 0
    removed_sleep = 0
    removed_if = 0
    added_if = 0
    added_events = set()
    removed_events = set()
    downstream_additions = 0
    downstream_removals = 0

    for line in diff.splitlines():
        if line.startswith("+++") or line.startswith("---"):
            continue
        if line.startswith("+"):
            content = line[1:]
            added_sleep += parse_sleep_minutes(content)
            if re.search(r"\bif:\s*.+", content):
                added_if += 1
            event_match = re.match(r"\s*([a-z_]+):\s*$", content)
            if event_match and event_match.group(1) in EVENT_KEYS:
                added_events.add(event_match.group(1))
            if "gh workflow run" in content or "repository_dispatch" in content or "workflow_dispatches" in content:
                downstream_additions += 1
            if re.search(r"runs-on:\s*.*(self-hosted|xl)", content, re.IGNORECASE):
                high_signal = True
        elif line.startswith("-"):
            content = line[1:]
            removed_sleep += parse_sleep_minutes(content)
            if re.search(r"\bif:\s*.+", content):
                removed_if += 1
            event_match = re.match(r"\s*([a-z_]+):\s*$", content)
            if event_match and event_match.group(1) in EVENT_KEYS:
                removed_events.add(event_match.group(1))
            if "gh workflow run" in content or "repository_dispatch" in content or "workflow_dispatches" in content:
                downstream_removals += 1

    sleep_delta = (added_sleep - removed_sleep) * max(1, schedule_runs)
    if sleep_delta:
        delta_minutes += sleep_delta
        reasons.append(f"sleep/wait duration changed by ~{sleep_delta:+d} min/week")

    if removed_if > added_if:
        delta = (removed_if - added_if) * 20
        delta_minutes += delta
        reasons.append("conditional guards removed or weakened")
        high_signal = True
    elif added_if > removed_if:
        delta = (added_if - removed_if) * 10
        delta_minutes -= delta
        reasons.append("additional conditional guards added")

    expanded_events = added_events - removed_events
    reduced_events = removed_events - added_events
    if expanded_events:
        delta = 20 * len(expanded_events)
        delta_minutes += delta
        reasons.append(f"trigger scope expanded ({', '.join(sorted(expanded_events))})")
    if reduced_events:
        delta = 15 * len(reduced_events)
        delta_minutes -= delta
        reasons.append(f"trigger scope reduced ({', '.join(sorted(reduced_events))})")

    if downstream_additions:
        delta = downstream_additions * 30
        delta_minutes += delta
        reasons.append("new downstream workflow dispatch detected")
        high_signal = True
    if downstream_removals:
        delta = downstream_removals * 25
        delta_minutes -= delta
        reasons.append("downstream workflow dispatch removed")

    if not reasons:
        reasons.append("CI-related file changed; no strong cost signal detected")

    impact = categorize(delta_minutes, high_signal)
    description = "; ".join(reasons)
    return Finding(
        impact=impact,
        sha=sha,
        author=author,
        pr=pr,
        file_path=file_path,
        description=description,
        estimated_minutes=delta_minutes,
    )


def render_markdown(findings: list[Finding], since: str) -> str:
    now = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    by_impact: dict[str, list[Finding]] = defaultdict(list)
    for finding in findings:
        by_impact[finding.impact].append(finding)

    ordered_impacts = ["High", "Moderate", "Low", "Neutral", "Cost Reduction"]

    lines: list[str] = [
        "# CI Cost Impact Analysis",
        "",
        f"- Generated at: {now}",
        f"- Analysis window: {since}",
        f"- Total CI-related changes analyzed: {len(findings)}",
        "",
    ]

    for impact in ordered_impacts:
        items = by_impact.get(impact, [])
        lines.append(f"## {impact} ({len(items)})")
        lines.append("")
        if not items:
            lines.append("- None")
            lines.append("")
            continue

        for item in items:
            short_sha = item.sha[:8]
            lines.append(f"- **{item.file_path}**")
            lines.append(f"  - Commit: `{short_sha}`")
            lines.append(f"  - Author: {item.author}")
            lines.append(f"  - PR: {item.pr}")
            lines.append(f"  - Change: {item.description}")
            lines.append(f"  - Estimated additional runner-minutes/week: `{item.estimated_minutes:+d}`")
            lines.append("")

    lines.append("## Summary")
    lines.append("")
    lines.append("| Impact | Change count | Estimated runner-minutes/week |")
    lines.append("|---|---:|---:|")

    total_minutes = 0
    for impact in ordered_impacts:
        items = by_impact.get(impact, [])
        minutes = sum(item.estimated_minutes for item in items)
        total_minutes += minutes
        lines.append(f"| {impact} | {len(items)} | {minutes:+d} |")

    lines.append(f"| **Total** | **{len(findings)}** | **{total_minutes:+d}** |")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Analyze recent CI-related changes for potential cost impact")
    parser.add_argument("--since", default="7 days ago", help="git --since expression (default: '7 days ago')")
    parser.add_argument("--since-days", type=int, default=None, help="Alternative to --since, e.g. 7")
    parser.add_argument("--output", default="ci-cost-impact-report.md", help="Output markdown path")
    args = parser.parse_args()

    since = args.since
    if args.since_days is not None:
        since = f"{args.since_days} days ago"

    commits = parse_commits(since)
    findings: list[Finding] = []

    for commit in commits:
        for status, file_path in commit.file_statuses:
            findings.append(analyze_change(commit.sha, commit.author, commit.subject, status, file_path))

    markdown = render_markdown(findings, since)
    output_path = Path(args.output)
    output_path.write_text(markdown, encoding="utf-8")
    print(f"Report written to {output_path}")


if __name__ == "__main__":
    main()
