#!/usr/bin/env python3
"""Resolve the daily-on-main load-test namespace + post-warmup anchor.

Emits `namespace` (`c8-medic-daily-<date>-<sha>-test`, gRPC) and `at`
(RFC3339 = soak.started_at + 1800s) to `$GITHUB_OUTPUT`. Empty on miss
so the downstream comparison job skips cleanly. Falls back to the
previous business day when today's daily isn't yet complete.
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
TODAY_AVAILABLE_UTC_HOUR = 6  # daily cron 02:00 UTC + setup + 3h soak → ~05:30
LOOKBACK_BUSINESS_DAYS = 7


def warn(msg: str) -> None:
    print(f"::warning::{msg}", file=sys.stderr)


def gh(args: list[str]) -> str | None:
    try:
        r = subprocess.run(["gh", *args], check=False, capture_output=True, text=True)
    except FileNotFoundError:
        warn("gh CLI not found on PATH")
        return None
    if r.returncode != 0:
        warn(f"gh {' '.join(args)} → exit {r.returncode}: {r.stderr.strip()}")
        return None
    return r.stdout


def is_weekday(d: date) -> bool:
    return d.weekday() < 5


def previous_business_day(d: date) -> date:
    d -= timedelta(days=1)
    while not is_weekday(d):
        d -= timedelta(days=1)
    return d


def candidate_dates(now: datetime) -> list[date]:
    """Newest-first list of dates to probe for a completed daily run."""
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
    return (out.strip() or None) if out else None


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
            warn(f"daily run {rid} ({d}) missing soak.started_at or metrics artifact")
            continue
        try:
            start_dt = datetime.fromisoformat(soak_start.replace("Z", "+00:00"))
        except ValueError:
            warn(f"unparseable soak.started_at={soak_start!r} for run {rid}")
            continue
        anchor = (start_dt + timedelta(seconds=WARMUP_SECONDS)).strftime(
            "%Y-%m-%dT%H:%M:%SZ"
        )
        namespace = f"c8-{benchmark}"
        print(f"daily run {rid} ({d}) → {namespace} @ {anchor}")
        return namespace, anchor
    return None


def emit(namespace: str, at: str) -> None:
    line = f"namespace={namespace}\nat={at}\n"
    path = os.environ.get("GITHUB_OUTPUT")
    if path:
        with open(path, "a", encoding="utf-8") as f:
            f.write(line)
    else:
        sys.stdout.write(line)


def main() -> int:
    if not REPO:
        warn("GITHUB_REPOSITORY env not set")
        emit("", "")
        return 0
    result = resolve(datetime.now(timezone.utc))
    if result is None:
        warn("no usable daily-on-main run found; daily comparison skipped")
        emit("", "")
        return 0
    emit(*result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
