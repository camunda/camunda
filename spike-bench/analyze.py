#!/usr/bin/env python3
"""Aggregates results.jsonl: median over reps per (config, variant), with min..max spread."""
import json
import statistics
import sys
from collections import defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
FILES = sys.argv[1:] or ["results.jsonl"]
ORDER = ["main", "pr68725", "pr68754", "vthread", "vthread-spin2000", "simple", "simple@threads=cpu"]

rows = []
for f in FILES:
    rows += [json.loads(l) for l in (HERE / f).read_text().splitlines() if l.strip()]

errors = [r for r in rows if "error" in r]
groups = defaultdict(list)
for r in rows:
    if "error" not in r:
        groups[(r["config"], r["variant"])].append(r)


def metric_keys(scenario):
    base = ["throughput_per_s", "cores_total", "cfs_throttled_pct"]
    lat = {
        "pingpong": ["rtt_p50_us", "rtt_p99_us", "rtt_p999_us"],
        "fanout": ["round_p50_us", "round_p99_us"],
        "ingress": ["lat_p50_us", "lat_p99_us", "lat_p999_us"],
        "timers": ["jitter_p50_us", "jitter_p99_us"],
        "fairness": ["ext_p50_us", "ext_p99_us", "ext_done_ratio", "rtt_p99_us"],
        "broker": ["cmd_p50_us", "cmd_p99_us", "cmd_p999_us", "export_p99_us", "done_ratio"],
        "ioblock": ["cpu_p50_us", "cpu_p99_us", "cpu_p999_us", "io_p50_us", "io_p99_us", "io_done_ratio"],
    }[scenario]
    return base + lat


def fmt(values):
    med = statistics.median(values)
    lo, hi = min(values), max(values)
    def f(v):
        return f"{v:,.0f}" if abs(v) >= 1000 else f"{v:.2f}"
    spread = f" ({f(lo)}..{f(hi)})" if len(values) > 1 and hi > 0 and (hi - lo) / max(abs(med), 1e-9) > 0.15 else ""
    return f(med) + spread


configs = []
for (c, _), rs in groups.items():
    if c not in configs:
        configs.append(c)

for c in configs:
    variants = [v for v in ORDER if (c, v) in groups]
    scenario = groups[(c, variants[0])][0]["scenario"]
    keys = metric_keys(scenario)
    print(f"\n### {c}  (reps: {', '.join(f'{v}={len(groups[(c, v)])}' for v in variants)})")
    print("| variant | " + " | ".join(keys) + " |")
    print("|---" * (len(keys) + 1) + "|")
    for v in variants:
        rs = groups[(c, v)]
        cells = [fmt([r[k] for r in rs if k in r]) if any(k in r for r in rs) else "-" for k in keys]
        print(f"| {v} | " + " | ".join(cells) + " |")

if errors:
    print(f"\n{len(errors)} failed runs:")
    for e in errors:
        print(" ", e["config"], e["variant"], e["rep"], e["error"][:120])
