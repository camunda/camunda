#!/usr/bin/env python3
"""Resolve the daily-on-main load test namespace + post-warmup anchor.

Invoked by the `resolve-daily-namespace` job in
`.github/workflows/camunda-pr-load-test.yaml`. Emits two `$GITHUB_OUTPUT`
values:

    namespace: c8-medic-daily-<YYYY-MM-DD>-<sha>-test  (gRPC daily namespace)
    at:        RFC3339 instant = soak.started_at + 1800s

Downstream `get-daily-metrics` re-queries Prometheus at that anchor with a
1800s range, giving the daily's first 30 min after its `prepare-soak`
warmup. Emits empty outputs when no usable daily run is found; the
consumer job is gated on the `namespace` output so the comparison skips
cleanly.

The daily workflow runs Mon-Fri at 02:00 UTC: setup (~10-20 min) + warmup
(15 min) + soak (3h) → done by ~05:30-06:00 UTC. If today's run isn't yet
complete (or it's the weekend), the resolver falls back to the previous
business day.
"""

from __future__ import annotations

import os
import subprocess
import sys
from datetime import date, datetime, timedelta, timezone

REPO = os.environ.get("GITHUB_REPOSITORY", "")
WORKFLOW = "camunda-daily-load-tests.yml"
ARTIFACT_PREFIX = "daily-load-test-metrics-"
ARTIFACT_NAME_PREFIX = ARTIFACT_PREFIX + "medic-daily-"
SOAK_JOB_NAME = "Soak (3 hours)"
WARMUP_SECONDS = 1800
# Daily cron + 3h soak + setup → done ~05:30 UTC. 06:00 is the earliest hour
# at which "today's run is plausibly complete and its soak.started_at is
# populated"; before that we anchor to the previous business day.
TODAY_AVAILABLE_UTC_HOUR = 6
LOOKBACK_BUSINESS_DAYS = 7


def warn(msg: str) -> None:
    print(f"::warning::{msg}", file=sys.stderr)


def gh(args: list[str]) -> str | None:
    """Run `gh` and return stdout text, or None on failure."""
    try:
        result = subprocess.run(
            ["gh", *args], check=False, capture_output=True, text=True,
        )
    except FileNotFoundError:
        warn("gh CLI not found on PATH")
        return None
    if result.returncode != 0:
        warn(f"gh {' '.join(args)} → exit {result.returncode}: {result.stderr.strip()}")
        return None
    return result.stdout


def is_weekday(d: date) -> bool:
    return d.weekday() < 5


def previous_business_day(d: date) -> date:
    d -= timedelta(days=1)
    while not is_weekday(d):
        d -= timedelta(days=1)
    return d


def candidate_dates(now: datetime) -> list[date]:
    """Newest-first list of daily-run dates to try."""
    today = now.date()
    start = (
        today
        if is_weekday(today) and now.hour >= TODAY_AVAILABLE_UTC_HOUR
        else previous_business_day(today)
    )
    out: list[date] = []
    cur = start
    for _ in range(LOOKBACK_BUSINESS_DAYS):
        out.append(cur)
        cur = previous_business_day(cur)
    return out


def find_run_id(target: date) -> str | None:
    out = gh([
        "run", "list",
        "--workflow", WORKFLOW,
        "--branch", "main",
        "--status", "completed",
        "--created", target.isoformat(),
        "--limit", "5",
        "--json", "databaseId",
        "--jq", ".[0].databaseId // empty",
        "--repo", REPO,
    ])
    if not out:
        return None
    rid = out.strip()
    return rid or None


def soak_started_at(run_id: str) -> str | None:
    out = gh([
        "api", f"repos/{REPO}/actions/runs/{run_id}/jobs",
        "--jq",
        f'.jobs[] | select(.name == "{SOAK_JOB_NAME}") | .started_at',
    ])
    if not out:
        return None
    for line in out.splitlines():
        line = line.strip()
        if line and line != "null":
            return line
    return None


def benchmark_from_artifacts(run_id: str) -> str | None:
    out = gh([
        "api", f"repos/{REPO}/actions/runs/{run_id}/artifacts",
        "--jq",
        ".artifacts[] | select(.expired == false) "
        f'| select(.name | startswith("{ARTIFACT_NAME_PREFIX}")) | .name',
    ])
    if not out:
        return None
    for line in out.splitlines():
        line = line.strip()
        if line:
            return line[len(ARTIFACT_PREFIX):]
    return None


def resolve(now: datetime) -> tuple[str, str] | None:
    for d in candidate_dates(now):
        rid = find_run_id(d)
        if not rid:
            continue
        soak_start = soak_started_at(rid)
        benchmark = benchmark_from_artifacts(rid)
        if not soak_start or not benchmark:
            warn(
                f"Daily run {rid} on {d} missing "
                f"{'soak.started_at' if not soak_start else 'metrics artifact'}; "
                "trying previous business day"
            )
            continue
        try:
            start_dt = datetime.fromisoformat(soak_start.replace("Z", "+00:00"))
        except ValueError:
            warn(f"Could not parse soak.started_at='{soak_start}' for run {rid}")
            continue
        anchor = (start_dt + timedelta(seconds=WARMUP_SECONDS)).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        namespace = f"c8-{benchmark}"
        print(
            f"Selected daily run {rid} (date: {d}, "
            f"namespace: {namespace}, anchor: {anchor})"
        )
        return namespace, anchor
    return None


def emit(namespace: str, at: str) -> None:
    path = os.environ.get("GITHUB_OUTPUT")
    line = f"namespace={namespace}\nat={at}\n"
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line)
    else:
        sys.stdout.write(line)


def main() -> int:
    if not REPO:
        warn("GITHUB_REPOSITORY env not set; cannot resolve daily run")
        emit("", "")
        return 0
    result = resolve(datetime.now(timezone.utc))
    if result is None:
        warn(
            "No completed daily-on-main run with both a soak.started_at and a "
            f"metrics artifact found in the last {LOOKBACK_BUSINESS_DAYS} "
            "business days; daily comparison will be skipped"
        )
        emit("", "")
        return 0
    emit(*result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
