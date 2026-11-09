#!/usr/bin/env python3
"""Aggregates results-e2e.jsonl entries whose label contains the given tag: median (min..max) per
(load, variant) for the metrics that matter on Kubernetes."""
import json
import re
import statistics
import sys
from collections import defaultdict
from pathlib import Path

since = sys.argv[1] if len(sys.argv) > 1 else ""  # only entries after this label substring appears
rows = [json.loads(l) for l in (Path(__file__).parent / "results-e2e.jsonl").read_text().splitlines()]
if since:
    idx = max(i for i, r in enumerate(rows) if since in r["label"])
    rows = rows[idx:]
groups = defaultdict(list)
for r in rows:
    m = re.match(r"e2e rep=(\d+) load=(\S+) variant=(\S+)", r["label"])
    if m:
        groups[(m.group(2), m.group(3))].append(r)

KEYS = [
    "cores", "throttled_pct", "pi_created_per_s", "jobs_completed_per_s", "records_processed_per_core_s",
    "create_pi_p50_ms", "create_pi_p99_ms", "complete_job_p50_ms", "complete_job_p99_ms",
    "sp_latency_p50_ms", "sp_latency_p99_ms", "commit_latency_p99_ms", "exporting_latency_p99_ms",
]


def fmt(vals):
    vals = [v for v in vals if v is not None]
    if not vals:
        return "-"
    med = statistics.median(vals)
    s = f"{med:,.2f}" if med < 100 else f"{med:,.0f}"
    if len(vals) > 1:
        s += f" ({min(vals):,.1f}..{max(vals):,.1f})"
    return s


for load in sorted({k[0] for k in groups}):
    print(f"\n### {load}")
    variants = sorted(v for (l, v) in groups if l == load)
    print("| metric | " + " | ".join(f"{v} (n={len(groups[(load, v)])})" for v in variants) + " |")
    print("|---" * (len(variants) + 1) + "|")
    for k in KEYS:
        print(f"| {k} | " + " | ".join(fmt([r.get(k) for r in groups[(load, v)]]) for v in variants) + " |")
