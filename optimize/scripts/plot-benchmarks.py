#!/usr/bin/env python3
"""
Generate benchmark analysis charts from benchmark-results.csv.

Output: benchmark-analysis.png (multi-panel figure in the same directory).
"""

import csv
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

SCRIPT_DIR = Path(__file__).parent
CSV_PATH = SCRIPT_DIR / "benchmark-results.csv"
OUTPUT_PATH = SCRIPT_DIR / "benchmark-analysis.png"

# ── Load data ─────────────────────────────────────────────────────────────────

def _f(v):
    try:
        return float(v)
    except (ValueError, TypeError):
        return None

def load_runs(path: Path) -> list[dict]:
    runs = []
    with open(path, newline="") as f:
        for row in csv.DictReader(f):
            if row.get("Status") != "COMPLETED":
                continue
            achieved = _f(row["Activated PI/s"])
            target = _f(row["Target PI/s"])
            if achieved is None or target is None:
                continue
            runs.append({
                "name":          row["Run"],
                "target":        target,
                "achieved":      achieved,
                "pct":           achieved / target * 100,
                "brokers":       _f(row["Broker replicas"]),
                "partitions":    _f(row["Partition count"]),
                "part_load":     _f(row["Partition load"]),
                "raft_p99":      _f(row["Raft msg p99 (s)"]),
                "bp_drop":       _f(row["Backpressure drop %"]),
                "append_inflight": _f(row["Append inflight"]),
                "stream_rate":   _f(row["Stream proc rate"]),
                "journal_kbps":  _f(row["Journal KB/s"]),
                "net_tx":        _f(row["Broker net TX MB/s"]),
                "camunda_cpu_throttle": _f(row["Camunda CPU throttle %"]),
                "gc_overhead":   _f(row["GC overhead"]),
                "node":          row["Broker node"].strip(),
            })
    return runs


def pass_color(pct):
    if pct >= 95:
        return "#2ca02c"   # green
    if pct >= 90:
        return "#ff7f0e"   # orange
    return "#d62728"       # red


def pass_label(pct):
    if pct >= 95:
        return "pass (≥95%)"
    if pct >= 90:
        return "marginal (90–95%)"
    return "fail (<90%)"


# ── Chart 1: Achieved vs Target ───────────────────────────────────────────────

def chart_achieved_vs_target(ax, runs):
    ax.set_title("Achieved vs Target PI/s", fontweight="bold")

    seen = set()
    for r in runs:
        color = pass_color(r["pct"])
        label = pass_label(r["pct"])
        lbl = label if label not in seen else None
        seen.add(label)
        ax.scatter(r["target"], r["achieved"], color=color, s=100, zorder=3, label=lbl)
        config = f"{int(r['brokers'])}b/{int(r['partitions'])}p"
        ax.annotate(config, (r["target"], r["achieved"]),
                    textcoords="offset points", xytext=(6, 3), fontsize=7.5, color="#444")

    # Perfect line and 90% threshold line
    mx = max(r["target"] for r in runs) * 1.08
    xs = np.linspace(0, mx, 100)
    ax.plot(xs, xs, "--", color="#aaa", linewidth=1, label="100% (target)")
    ax.plot(xs, xs * 0.90, ":", color="#ff7f0e", linewidth=1, label="90% threshold")

    ax.set_xlabel("Target PI/s")
    ax.set_ylabel("Achieved PI/s")
    ax.set_xlim(0, mx)
    ax.set_ylim(0, mx)
    ax.legend(fontsize=8, loc="upper left")
    ax.grid(True, alpha=0.3)


# ── Chart 2: Partition Load vs Achieved % ────────────────────────────────────

def chart_partition_load(ax, runs):
    ax.set_title("Partition Load vs Achievement\n(actor-thread bottleneck)", fontweight="bold")

    seen = set()
    for r in runs:
        if r["part_load"] is None:
            continue
        color = pass_color(r["pct"])
        label = pass_label(r["pct"])
        lbl = label if label not in seen else None
        seen.add(label)
        ax.scatter(r["part_load"], r["pct"], color=color, s=100, zorder=3, label=lbl)
        ax.annotate(f"{int(r['target'])} PI/s", (r["part_load"], r["pct"]),
                    textcoords="offset points", xytext=(5, 2), fontsize=7.5, color="#444")

    ax.axvline(40, color="#d62728", linestyle="--", linewidth=1.2, label="load=40 threshold")
    ax.axhline(90, color="#ff7f0e", linestyle=":", linewidth=1, label="90% threshold")
    ax.set_xlabel("Partition Load (%)")
    ax.set_ylabel("Achieved %")
    ax.set_ylim(50, 105)
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)


# ── Chart 3: Raft p99 vs Achieved % ─────────────────────────────────────────

def chart_raft(ax, runs):
    ax.set_title("Raft msg p99 vs Achievement\n(replication bottleneck)", fontweight="bold")

    seen = set()
    for r in runs:
        if r["raft_p99"] is None:
            continue
        color = pass_color(r["pct"])
        label = pass_label(r["pct"])
        lbl = label if label not in seen else None
        seen.add(label)
        ax.scatter(r["raft_p99"], r["pct"], color=color, s=100, zorder=3, label=lbl)
        ax.annotate(f"{int(r['target'])}PI/s\n{int(r['brokers'])}b/{int(r['partitions'])}p",
                    (r["raft_p99"], r["pct"]),
                    textcoords="offset points", xytext=(5, 2), fontsize=7, color="#444")

    ax.axvline(1.0, color="#d62728", linestyle="--", linewidth=1.2, label="1.0s warning")
    ax.axhline(90, color="#ff7f0e", linestyle=":", linewidth=1, label="90% threshold")
    ax.set_xlabel("Raft msg p99 (s)")
    ax.set_ylabel("Achieved %")
    ax.set_ylim(50, 105)
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)


# ── Chart 4: BP Drop vs Partition Load ───────────────────────────────────────

def chart_bp_vs_load(ax, runs):
    ax.set_title("Backpressure Drop % vs Partition Load", fontweight="bold")

    seen = set()
    for r in runs:
        if r["part_load"] is None or r["bp_drop"] is None:
            continue
        color = pass_color(r["pct"])
        label = pass_label(r["pct"])
        lbl = label if label not in seen else None
        seen.add(label)
        ax.scatter(r["part_load"], r["bp_drop"], color=color, s=100, zorder=3, label=lbl)
        ax.annotate(f"{int(r['target'])}PI/s", (r["part_load"], r["bp_drop"]),
                    textcoords="offset points", xytext=(5, 2), fontsize=7.5, color="#444")

    ax.axvline(40, color="#d62728", linestyle="--", linewidth=1.2, label="load=40 threshold")
    ax.set_xlabel("Partition Load (%)")
    ax.set_ylabel("Backpressure Drop %")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)


# ── Chart 5: Key stress metrics per run (normalized bar chart) ────────────────

def chart_stress_bars(ax, runs):
    ax.set_title("Relative Stress per Run\n(normalized to max observed value)", fontweight="bold")

    keys = [
        ("part_load",  "Partition Load", 40),
        ("raft_p99",   "Raft p99 (s)",  1.0),
        ("bp_drop",    "BP Drop %",     50),
        ("append_inflight", "Append Inflight", 100),
    ]

    # Normalize each metric to its observed max
    maxes = {k: max((r[k] for r in runs if r[k] is not None), default=1) for k, _, _ in keys}

    n_metrics = len(keys)
    n_runs = len(runs)
    width = 0.15
    x = np.arange(n_runs)
    colors = ["#1f77b4", "#ff7f0e", "#2ca02c", "#9467bd"]

    for i, (key, label, threshold) in enumerate(keys):
        values = [r[key] / maxes[key] if r[key] is not None else 0 for r in runs]
        bars = ax.bar(x + i * width, values, width, label=label, color=colors[i], alpha=0.8)

    # Short run labels
    short_labels = []
    for r in runs:
        short_labels.append(f"{int(r['target'])}PI/s\n{int(r['brokers'])}b/{int(r['partitions'])}p")

    ax.set_xticks(x + width * (n_metrics - 1) / 2)
    ax.set_xticklabels(short_labels, fontsize=7)
    ax.set_ylabel("Normalized value (1.0 = observed max)")
    ax.legend(fontsize=8, loc="upper left")
    ax.grid(True, alpha=0.3, axis="y")
    ax.set_ylim(0, 1.2)


# ── Chart 6: Capacity extrapolation ──────────────────────────────────────────

def chart_extrapolation(ax, runs):
    ax.set_title("Capacity Extrapolation\n(actor ceiling ≈50 PI/s/partition; Raft ceiling TBD)",
                 fontweight="bold")

    # Actor model: each partition handles ~50 PI/s max (based on load≈40 threshold at 42 PI/s/partition)
    # Raft: at 500 PI/s with 12 partitions, Raft fails → cap around 400 PI/s for 12 partitions
    actor_ceiling_per_partition = 50  # PI/s per partition before load > 40

    partition_range = np.arange(3, 25)
    actor_capacity = partition_range * actor_ceiling_per_partition

    ax.plot(partition_range, actor_capacity, "-", color="#1f77b4", linewidth=2,
            label=f"Actor ceiling ({actor_ceiling_per_partition} PI/s/partition)")

    # Raft degrades sharply beyond a threshold — approximated from data points:
    # 6p → ~500 PI/s (load ~54, starts failing)
    # 12p → ~400-450 PI/s (Raft saturates at 500)
    # Model: Raft ceiling grows sub-linearly; rough fit from 2 data points
    # At 6p raft fails at 500, passes at 300 → ceiling ~420
    # At 12p raft fails at 500, passes at 300 → ceiling maybe ~380-400
    # Approximate Raft ceiling as 350 + sqrt(partitions) * 15 (rough heuristic)
    raft_capacity = 350 + np.sqrt(partition_range) * 20
    ax.plot(partition_range, raft_capacity, "--", color="#d62728", linewidth=2,
            label="Raft ceiling (estimated from data)")

    # Effective capacity = min of both
    effective = np.minimum(actor_capacity, raft_capacity)
    ax.fill_between(partition_range, 0, effective, alpha=0.08, color="#2ca02c")
    ax.plot(partition_range, effective, "-", color="#2ca02c", linewidth=2.5,
            label="Effective ceiling (min of both)")

    # Overlay actual data points
    seen = set()
    for r in runs:
        if r["partitions"] is None:
            continue
        color = pass_color(r["pct"])
        label_str = pass_label(r["pct"])
        lbl = label_str if label_str not in seen else None
        seen.add(label_str)
        ax.scatter(r["partitions"], r["achieved"], color=color, s=120, zorder=5,
                   marker="D", label=lbl)
        ax.annotate(f"{int(r['achieved'])}", (r["partitions"], r["achieved"]),
                    textcoords="offset points", xytext=(4, 4), fontsize=7.5)

    # Target reference lines
    for target_pis, label in [(300, "300 PI/s"), (500, "500 PI/s"), (800, "800 PI/s")]:
        ax.axhline(target_pis, color="#aaa", linestyle=":", linewidth=1)
        ax.text(23.5, target_pis + 5, label, fontsize=7.5, color="#888", ha="right")

    ax.set_xlabel("Partition count")
    ax.set_ylabel("Throughput (PI/s)")
    ax.set_xlim(2, 25)
    ax.set_ylim(0, 1300)
    ax.legend(fontsize=8, loc="upper left")
    ax.grid(True, alpha=0.3)


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    if not CSV_PATH.exists():
        print(f"ERROR: {CSV_PATH} not found", file=sys.stderr)
        sys.exit(1)

    runs = load_runs(CSV_PATH)
    if not runs:
        print("No COMPLETED runs found in CSV.", file=sys.stderr)
        sys.exit(1)

    print(f"Loaded {len(runs)} completed runs from {CSV_PATH.name}")

    fig = plt.figure(figsize=(18, 13))
    fig.suptitle("Zeebe Benchmark Analysis", fontsize=15, fontweight="bold", y=0.98)

    gs = fig.add_gridspec(3, 2, hspace=0.48, wspace=0.32,
                          left=0.07, right=0.97, top=0.94, bottom=0.06)

    chart_achieved_vs_target(fig.add_subplot(gs[0, 0]), runs)
    chart_partition_load(fig.add_subplot(gs[0, 1]), runs)
    chart_raft(fig.add_subplot(gs[1, 0]), runs)
    chart_bp_vs_load(fig.add_subplot(gs[1, 1]), runs)
    chart_stress_bars(fig.add_subplot(gs[2, 0]), runs)
    chart_extrapolation(fig.add_subplot(gs[2, 1]), runs)

    fig.savefig(OUTPUT_PATH, dpi=150, bbox_inches="tight")
    print(f"Saved → {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
