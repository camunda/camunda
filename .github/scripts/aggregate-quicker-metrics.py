#!/usr/bin/env python3
"""Aggregate quicker-benchmark metrics from per-pod Prometheus text exports.

Inputs:
  --starter   path to the starter pod's /metrics dump
  --camunda-dir directory of <pod>.prom files, one per broker pod
  --duration  test duration in seconds (used for throughput averages)

Output: GITHUB_OUTPUT-style key=value lines on stdout.

Why this exists: the CI Teleport role grants pods/portforward in test namespaces but not
in the cluster's `monitoring/` namespace where Prometheus lives, so we scrape each pod
directly and aggregate ourselves. Per-broker counters only count their own partitions,
hence the directory of files (we sum across them).

Histogram quantiles use the same algorithm as Prometheus's histogram_quantile(): find the
bucket whose cumulative count crosses q*total, then linearly interpolate within that
bucket. Inf bucket is treated as the highest le with count==total. We don't need a rate
window because each pod is fresh per test, so cumulative bucket counts equal "all
observations during the run".
"""

from __future__ import annotations

import argparse
import math
import os
import re
import sys
from collections import defaultdict
from pathlib import Path

# Match a single sample line: name{labels} value [timestamp]
# We accept missing braces (no labels). Captures (name, labels, value).
SAMPLE_RE = re.compile(r"^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{[^}]*\})?\s+([^\s]+)")


def parse_labels(label_str: str | None) -> dict[str, str]:
    if not label_str:
        return {}
    inner = label_str.strip("{}").strip()
    if not inner:
        return {}
    out = {}
    # Simple parser; values are quoted, labels are key="value" pairs comma-separated.
    for pair in re.findall(r'([a-zA-Z_][a-zA-Z0-9_]*)="([^"]*)"', inner):
        out[pair[0]] = pair[1]
    return out


def parse_prom_text(path: Path) -> list[tuple[str, dict[str, str], float]]:
    """Yield (name, labels, value) for every non-comment line in a Prometheus text export."""
    out: list[tuple[str, dict[str, str], float]] = []
    with path.open() as fh:
        for raw in fh:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            m = SAMPLE_RE.match(line)
            if not m:
                continue
            name, labels, val = m.group(1), m.group(2), m.group(3)
            try:
                v = float(val)
            except ValueError:
                continue
            if math.isnan(v):
                continue
            out.append((name, parse_labels(labels), v))
    return out


def sum_metric(samples, name: str, label_match: dict[str, str] | None = None) -> float:
    total = 0.0
    for n, lbl, v in samples:
        if n != name:
            continue
        if label_match and any(lbl.get(k) != v_ for k, v_ in label_match.items()):
            continue
        total += v
    return total


def histogram_quantile(quantile: float, buckets: list[tuple[float, float]]) -> float | None:
    """Replicate Prometheus's histogram_quantile() over (le, cumulative_count) pairs.

    Buckets must be sorted by `le` ascending. The +Inf bucket should be present with
    cumulative count equal to the total observation count.
    """
    if not buckets:
        return None
    # Sort: +Inf last
    sorted_b = sorted(buckets, key=lambda x: (x[0] == math.inf, x[0]))
    total = sorted_b[-1][1]
    if total <= 0:
        return None
    rank = quantile * total
    # Find first bucket where cum count >= rank
    prev_le, prev_count = 0.0, 0.0
    for le, cum in sorted_b:
        if cum >= rank:
            if le == math.inf:
                # Cannot interpolate into +Inf; return the previous finite le.
                return prev_le
            count_in_bucket = cum - prev_count
            if count_in_bucket <= 0:
                return prev_le
            # Linear interpolation: rank within (prev_count, cum]
            frac = (rank - prev_count) / count_in_bucket
            return prev_le + (le - prev_le) * frac
        prev_le, prev_count = le, cum
    return sorted_b[-1][0]


def collect_buckets(samples, name: str) -> list[tuple[float, float]]:
    """Return [(le, cum_count)] for a histogram metric, summing across all label sets."""
    by_le: dict[float, float] = defaultdict(float)
    for n, lbl, v in samples:
        if n != f"{name}_bucket":
            continue
        le_str = lbl.get("le")
        if le_str is None:
            continue
        try:
            le = math.inf if le_str == "+Inf" else float(le_str)
        except ValueError:
            continue
        by_le[le] += v
    return sorted(by_le.items())


def emit(key: str, value: float | None) -> None:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        print(f"{key}=NaN")
    else:
        print(f"{key}={value}")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--starter", required=True, type=Path)
    p.add_argument("--camunda-dir", required=True, type=Path)
    p.add_argument("--duration", required=True, type=float)
    args = p.parse_args()

    if not args.starter.exists():
        print(f"::warning::starter scrape file missing: {args.starter}", file=sys.stderr)
        starter_samples: list = []
    else:
        starter_samples = parse_prom_text(args.starter)

    camunda_samples: list = []
    for f in sorted(args.camunda_dir.glob("*.prom")):
        camunda_samples.extend(parse_prom_text(f))

    # Starter-side metrics
    instances_started = sum_metric(starter_samples, "starter_process_instances_started_total")
    response_buckets = collect_buckets(starter_samples, "starter_response_latency_seconds")
    dataavail_buckets = collect_buckets(starter_samples, "starter_data_availability_latency_seconds")

    response_p50 = histogram_quantile(0.5, response_buckets)
    response_p99 = histogram_quantile(0.99, response_buckets)
    dataavail_p50 = histogram_quantile(0.5, dataavail_buckets)
    dataavail_p99 = histogram_quantile(0.99, dataavail_buckets)

    # Engine-side metrics, summed across all broker pods
    completed_pi_total = sum_metric(
        camunda_samples,
        "zeebe_element_instance_events_total",
        {"action": "completed", "type": "PROCESS"},
    )
    dropped_total = sum_metric(camunda_samples, "zeebe_dropped_request_count_total")
    received_total = sum_metric(camunda_samples, "zeebe_received_request_count_total")

    # Derived
    duration = max(args.duration, 1.0)
    completed_pi_rate = completed_pi_total / duration
    backpressure_pct = (
        (dropped_total / received_total * 100.0) if received_total > 0 else 0.0
    )

    emit("instances-started", instances_started)
    emit("response-p50-seconds", response_p50)
    emit("response-p99-seconds", response_p99)
    emit("dataavail-p50-seconds", dataavail_p50)
    emit("dataavail-p99-seconds", dataavail_p99)
    emit("completed-pi-total", completed_pi_total)
    emit("completed-pi-rate", completed_pi_rate)
    emit("backpressure-pct", backpressure_pct)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
