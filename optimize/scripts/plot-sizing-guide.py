#!/usr/bin/env python3
"""
Generate hardware sizing-guide charts from benchmark-results.csv.

Output: benchmark-sizing-guide.png
Goal: advise customers on required resources for a given load target.
"""

import csv
import math
import sys
from pathlib import Path

import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np

SCRIPT_DIR = Path(__file__).parent
CSV_PATH = SCRIPT_DIR / "benchmark-results.csv"
OUTPUT_PATH = SCRIPT_DIR / "benchmark-sizing-guide.png"

# Validated ceiling: ~42 PI/s per partition before load > 40 (actor-thread bottleneck).
# Conservative: use 40 PI/s/partition for sizing recommendations.
CEILING_PIS_PER_PARTITION = 40
MAX_PARTITIONS_PER_BROKER = 2   # from capacity plan: 2 partitions/broker on n2-standard-16

# ── Data loading ──────────────────────────────────────────────────────────────

def _f(v):
    try:
        return float(v)
    except (ValueError, TypeError):
        return None

def _parse_m(v: str):
    """'15000m' → 15000, '15' → 15000."""
    v = v.strip()
    if v.endswith("m"):
        return int(v[:-1])
    try:
        return int(float(v) * 1000)
    except ValueError:
        return None

def _parse_gi(v: str):
    """'24Gi' → 24.0, '512Mi' → 0.5."""
    v = v.strip()
    if v.endswith("Gi"):
        return float(v[:-2])
    if v.endswith("Mi"):
        return float(v[:-2]) / 1024
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
            brokers = _f(row["Broker replicas"])
            es_replicas = _f(row["ES replicas"])
            broker_cpu_m = _parse_m(row["Broker CPU"])
            broker_mem_gi = _parse_gi(row["Broker memory"])
            es_cpu_m = _parse_m(row["ES CPU"])
            es_mem_gi = _parse_gi(row["ES memory"])
            partitions = _f(row["Partition count"])
            runs.append({
                "name":                row["Run"],
                "target":              target,
                "achieved":            achieved,
                "pct":                 achieved / target * 100,
                "brokers":             brokers,
                "partitions":          partitions,
                "broker_node":         row["Broker node"].strip(),
                "broker_cpu_m":        broker_cpu_m,
                "broker_mem_gi":       broker_mem_gi,
                "es_replicas":         es_replicas,
                "es_cpu_m":            es_cpu_m,
                "es_mem_gi":           es_mem_gi,
                "total_broker_cpu_m":  brokers * broker_cpu_m if brokers and broker_cpu_m else None,
                "total_broker_mem_gi": brokers * broker_mem_gi if brokers and broker_mem_gi else None,
                "total_es_mem_gi":     es_replicas * es_mem_gi if es_replicas and es_mem_gi else None,
                "total_es_cpu_m":      es_replicas * es_cpu_m if es_replicas and es_cpu_m else None,
                "part_load":           _f(row["Partition load"]),
                "raft_p99":            _f(row["Raft msg p99 (s)"]),
                "bp_drop":             _f(row["Backpressure drop %"]),
                "camunda_cpu":         _f(row["Camunda CPU throttle %"]),
            })
    return runs


# ── Color/style helpers ───────────────────────────────────────────────────────

PASS_COLOR      = "#2ca02c"
MARGINAL_COLOR  = "#ff7f0e"
FAIL_COLOR      = "#d62728"
PROJ_COLOR      = "#1f77b4"
GRID_ALPHA      = 0.3

def run_color(r):
    if r["pct"] >= 95:  return PASS_COLOR
    if r["pct"] >= 90:  return MARGINAL_COLOR
    return FAIL_COLOR

def run_marker(r):
    return "o" if r["pct"] >= 90 else "X"

def _legend_handles():
    return [
        mpatches.Patch(color=PASS_COLOR,     label="pass (≥95%)"),
        mpatches.Patch(color=MARGINAL_COLOR, label="marginal (90–95%)"),
        mpatches.Patch(color=FAIL_COLOR,     label="fail (<90%)"),
    ]

def _scatter(ax, runs, x_key, y_key, label_fn=None):
    for r in runs:
        x, y = r.get(x_key), r.get(y_key)
        if x is None or y is None:
            continue
        ax.scatter(x, y, color=run_color(r), marker=run_marker(r), s=90, zorder=4)
        if label_fn:
            ax.annotate(label_fn(r), (x, y),
                        textcoords="offset points", xytext=(5, 3), fontsize=7.5, color="#444")


def _proj_line(ax, xs, ys, label="Recommendation"):
    ax.plot(xs, ys, "--", color=PROJ_COLOR, linewidth=1.8, label=label, zorder=2)


def _finalize(ax, xlabel, ylabel, legend=True):
    ax.set_xlabel(xlabel)
    ax.set_ylabel(ylabel)
    ax.grid(True, alpha=GRID_ALPHA)
    if legend:
        ax.legend(fontsize=8, loc="upper left")


# ── Chart 1: Partitions vs Achieved PI/s ─────────────────────────────────────

def chart_partitions(ax, runs):
    ax.set_title("Partitions Required vs Load", fontweight="bold")

    _scatter(ax, runs, "achieved", "partitions",
             label_fn=lambda r: f"{int(r['brokers'])}b")

    # Recommendation line: 1 partition per CEILING_PIS_PER_PARTITION PI/s (ceiling)
    xs = np.linspace(0, 850, 200)
    recommended = np.ceil(xs / CEILING_PIS_PER_PARTITION).clip(min=3)
    _proj_line(ax, xs, recommended,
               label=f"Recommended (ceil(PI/s÷{CEILING_PIS_PER_PARTITION}))")

    # Annotate projected requirements at key tiers
    for target in [200, 300, 500, 800]:
        parts = math.ceil(target / CEILING_PIS_PER_PARTITION)
        ax.annotate(f"{target} PI/s→\n{parts}p",
                    (target, parts),
                    textcoords="offset points", xytext=(4, -18),
                    fontsize=7, color=PROJ_COLOR, arrowprops=dict(arrowstyle="-", color=PROJ_COLOR, lw=0.8))

    ax.set_xlim(0, 870)
    ax.set_ylim(0, 25)
    handles = _legend_handles() + [
        plt.Line2D([0], [0], linestyle="--", color=PROJ_COLOR, label=f"Recommended (ceil(PI/s÷{CEILING_PIS_PER_PARTITION}))")
    ]
    ax.legend(handles=handles, fontsize=7.5, loc="upper left")
    _finalize(ax, "Achieved PI/s", "Partition count", legend=False)


# ── Chart 2: Broker Count vs Achieved PI/s ───────────────────────────────────

def chart_brokers(ax, runs):
    ax.set_title("Broker Node Count vs Load", fontweight="bold")

    _scatter(ax, runs, "achieved", "brokers",
             label_fn=lambda r: r["broker_node"].replace("n2-standard-", "n2-std-"))

    # Recommendation: brokers = max(3, ceil(partitions / MAX_PARTITIONS_PER_BROKER))
    xs = np.linspace(0, 850, 200)
    def recommended_brokers(pis):
        parts = math.ceil(pis / CEILING_PIS_PER_PARTITION)
        return max(3, math.ceil(parts / MAX_PARTITIONS_PER_BROKER))
    rec_brokers = [recommended_brokers(x) for x in xs]
    _proj_line(ax, xs, rec_brokers, label=f"Recommended (ceil(partitions÷{MAX_PARTITIONS_PER_BROKER}), min 3)")

    # Node type boundary annotation
    ax.axvline(150, color="#aaa", linestyle=":", linewidth=1)
    ax.text(155, 8.5, "n2-std-16\n threshold", fontsize=7, color="#888")

    ax.set_xlim(0, 870)
    ax.set_ylim(0, 11)
    ax.set_yticks([3, 6, 9])
    handles = _legend_handles() + [
        plt.Line2D([0], [0], linestyle="--", color=PROJ_COLOR,
                   label=f"Recommended (ceil(partitions÷{MAX_PARTITIONS_PER_BROKER}), min 3)")
    ]
    ax.legend(handles=handles, fontsize=7.5, loc="upper left")
    _finalize(ax, "Achieved PI/s", "Broker replicas", legend=False)


# ── Chart 3: Total Broker CPU vs Achieved PI/s ───────────────────────────────

def chart_broker_cpu(ax, runs):
    ax.set_title("Total Broker CPU vs Load", fontweight="bold")

    _scatter(ax, runs, "achieved", "total_broker_cpu_m",
             label_fn=lambda r: f"{int(r['brokers'])}×{r['broker_node'].replace('n2-standard-','n2-std-')}")

    # Recommendation line derived from broker count projection × 15000m (n2-standard-16)
    xs = np.linspace(0, 850, 200)
    def rec_cpu(pis):
        if pis <= 120:
            # n2-standard-4 tier: 3000m/broker
            return max(3, math.ceil(math.ceil(pis / CEILING_PIS_PER_PARTITION) / MAX_PARTITIONS_PER_BROKER)) * 3000
        return max(3, math.ceil(math.ceil(pis / CEILING_PIS_PER_PARTITION) / MAX_PARTITIONS_PER_BROKER)) * 15000
    rec_y = [rec_cpu(x) for x in xs]
    _proj_line(ax, xs, rec_y, label="Recommended")

    # Right-axis labels in vCores
    ax2 = ax.twinx()
    ax2.set_ylim(ax.get_ylim()[0] / 1000, ax.get_ylim()[1] / 1000)
    ax2.set_ylabel("Total broker vCores (approx)", fontsize=8)

    ax.set_xlim(0, 870)
    ax.yaxis.set_major_formatter(plt.FuncFormatter(lambda v, _: f"{int(v/1000)}k"))
    handles = _legend_handles() + [
        plt.Line2D([0], [0], linestyle="--", color=PROJ_COLOR, label="Recommended")
    ]
    ax.legend(handles=handles, fontsize=7.5, loc="upper left")
    _finalize(ax, "Achieved PI/s", "Total broker CPU (millicores)", legend=False)


# ── Chart 4: Total Broker Memory vs Achieved PI/s ────────────────────────────

def chart_broker_mem(ax, runs):
    ax.set_title("Total Broker Memory vs Load", fontweight="bold")

    _scatter(ax, runs, "achieved", "total_broker_mem_gi",
             label_fn=lambda r: f"{int(r['brokers'])}×{int(r['broker_mem_gi'])}Gi")

    xs = np.linspace(0, 850, 200)
    def rec_mem(pis):
        if pis <= 120:
            return max(3, math.ceil(math.ceil(pis / CEILING_PIS_PER_PARTITION) / MAX_PARTITIONS_PER_BROKER)) * 2
        return max(3, math.ceil(math.ceil(pis / CEILING_PIS_PER_PARTITION) / MAX_PARTITIONS_PER_BROKER)) * 24
    rec_y = [rec_mem(x) for x in xs]
    _proj_line(ax, xs, rec_y, label="Recommended")

    # Annotate tiers
    for target, label in [(100, "3×2Gi"), (200, "3×24Gi"), (300, "6×24Gi"), (600, "9×24Gi?")]:
        mem = rec_mem(target)
        ax.annotate(label, (target, mem),
                    textcoords="offset points", xytext=(5, 4), fontsize=7, color=PROJ_COLOR)

    ax.set_xlim(0, 870)
    handles = _legend_handles() + [
        plt.Line2D([0], [0], linestyle="--", color=PROJ_COLOR, label="Recommended")
    ]
    ax.legend(handles=handles, fontsize=7.5, loc="upper left")
    _finalize(ax, "Achieved PI/s", "Total broker memory (GiB)", legend=False)


# ── Chart 5: Total ES Memory vs Achieved PI/s ────────────────────────────────

def chart_es_mem(ax, runs):
    ax.set_title("Total ES Memory vs Load", fontweight="bold")

    _scatter(ax, runs, "achieved", "total_es_mem_gi",
             label_fn=lambda r: f"{int(r['es_replicas'])}×{int(r['es_mem_gi'])}Gi")

    # ES scales with data volume, not just PI/s rate; capacity plan uses fixed 3-replica config.
    # Observed: 100 PI/s → 3×8=24Gi; 200+ PI/s → 3×36=108Gi.
    # Projection: add ~10Gi per 100 PI/s above 200 for longer retention headroom.
    xs = np.linspace(0, 850, 200)
    def rec_es(pis):
        if pis <= 120:
            return 24     # 3 × 8Gi
        base = 108        # 3 × 36Gi
        return base + max(0, (pis - 200) / 100) * 10
    rec_y = [rec_es(x) for x in xs]
    _proj_line(ax, xs, rec_y, label="Recommended (3 replicas)")

    ax.annotate("ES not rate-limited\nin any tested run\n(0% CPU throttle)",
                xy=(300, 108), xytext=(420, 60),
                fontsize=7.5, color="#555",
                arrowprops=dict(arrowstyle="->", color="#aaa", lw=0.8))

    ax.set_xlim(0, 870)
    ax.set_ylim(0, 220)
    handles = _legend_handles() + [
        plt.Line2D([0], [0], linestyle="--", color=PROJ_COLOR, label="Recommended (3 replicas)")
    ]
    ax.legend(handles=handles, fontsize=7.5, loc="upper left")
    _finalize(ax, "Achieved PI/s", "Total ES memory (GiB)", legend=False)


# ── Chart 6: Sizing Guide Table ───────────────────────────────────────────────

def chart_sizing_guide(ax, _runs):
    ax.axis("off")
    # Title drawn as text so it sits cleanly above the table regardless of axis scaling
    ax.text(0.5, 1.01, "Sizing Guide — Recommended Spec per Load Tier",
            transform=ax.transAxes, ha="center", va="bottom",
            fontsize=11, fontweight="bold")

    # Columns = load tiers; rows = resource dimensions
    col_labels = ["100 PI/s\n✓ tested", "200 PI/s\n✓ tested",
                  "300 PI/s\n✓ tested", "500 PI/s\n~ projected", "800 PI/s\n~ projected"]

    # (row label, [values per tier], is_key_metric, is_es_section)
    rows = [
        # ── Broker ────────────────────────────────────────────────
        ("Broker node type",  ["n2-std-4",   "n2-std-16",  "n2-std-16",  "n2-std-16",  "n2-std-16"],  False, False),
        ("Broker nodes",      ["3",           "3",           "6",           "9",           "12"],         False, False),
        ("Partitions",        ["3",           "6",           "12",          "15",          "20"],         True,  False),
        ("CPU / broker node", ["3 vCores",    "12 vCores",   "12 vCores",   "12 vCores",   "12 vCores"],  False, False),
        ("Total broker CPU",  ["9 vCores",    "36 vCores",   "72 vCores",   "108 vCores",  "144 vCores"], True,  False),
        ("RAM / broker node", ["2 Gi",        "24 Gi",       "24 Gi",       "24 Gi",       "24 Gi"],      False, False),
        ("Total broker RAM",  ["6 Gi",        "72 Gi",       "144 Gi",      "216 Gi",      "288 Gi"],     True,  False),
        # ── Elasticsearch ─────────────────────────────────────────
        ("ES node type",      ["n2-std-8",    "n2-std-16",   "n2-std-16",   "n2-std-16",   "n2-std-16"],  False, True),
        ("ES nodes",          ["3",           "3",           "3",           "3",           "3"],          False, True),
        ("RAM / ES node",     ["8 Gi",        "36 Gi",       "36 Gi",       "36 Gi",       "43 Gi"],      False, True),
        ("Total ES RAM",      ["24 Gi",       "108 Gi",      "108 Gi",      "108 Gi",      "130 Gi"],     True,  True),
    ]

    row_labels = [r[0] for r in rows]
    cell_data  = [r[1] for r in rows]
    n_rows, n_cols = len(rows), len(col_labels)

    # ── Colors ────────────────────────────────────────────────────
    HDR_TESTED    = "#1a5fa8"   # dark blue  — validated columns
    HDR_PROJECTED = "#b45309"   # dark amber — projected columns
    ROW_BROKER    = "#dbeafe"   # light blue — broker label column
    ROW_ES        = "#fef3c7"   # light amber — ES label column
    CELL_BROKER   = "#f0f7ff"
    CELL_ES       = "#fffbeb"
    KEY_BROKER    = "#bfdbfe"   # stronger blue for key metrics
    KEY_ES        = "#fde68a"   # stronger amber for key metrics
    PROJ_TEXT     = "#6b7280"   # grey text for projected columns

    table = ax.table(
        cellText=cell_data,
        rowLabels=row_labels,
        colLabels=col_labels,
        loc="center",
        cellLoc="center",
    )
    table.auto_set_font_size(False)
    table.set_fontsize(9)
    table.scale(1.05, 2.1)

    # Column headers
    for j in range(n_cols):
        cell = table[0, j]
        cell.set_facecolor(HDR_TESTED if j < 3 else HDR_PROJECTED)
        cell.set_text_props(color="white", fontweight="bold", fontsize=8)

    # Row label column and data cells
    for i, (_, _, is_key, is_es) in enumerate(rows):
        row_idx = i + 1

        # Row label
        lbl_cell = table[row_idx, -1]
        lbl_cell.set_facecolor(ROW_ES if is_es else ROW_BROKER)
        if is_key:
            lbl_cell.set_text_props(fontweight="bold")

        # Data cells
        for j in range(n_cols):
            cell = table[row_idx, j]
            if is_key:
                cell.set_facecolor(KEY_ES if is_es else KEY_BROKER)
                cell.set_text_props(fontweight="bold")
            else:
                cell.set_facecolor(CELL_ES if is_es else CELL_BROKER)
            if j >= 3:                         # projected columns
                cell.set_text_props(color=PROJ_TEXT)

    # Section divider — draw a horizontal line between broker and ES rows
    es_start = next(i for i, r in enumerate(rows) if r[3]) + 1   # +1 for header offset
    for j in range(-1, n_cols):
        cell = table[es_start, j]
        cell.visible_edges = "TBL" if j == -1 else "TBL"

    ax.text(0.5, 0.03,
            "* Projected from per-partition ceiling of 40 PI/s — not yet bench-validated",
            transform=ax.transAxes, ha="center", fontsize=7.5, color="#6b7280")


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    if not CSV_PATH.exists():
        print(f"ERROR: {CSV_PATH} not found", file=sys.stderr)
        sys.exit(1)

    runs = load_runs(CSV_PATH)
    if not runs:
        print("No COMPLETED runs found.", file=sys.stderr)
        sys.exit(1)

    print(f"Loaded {len(runs)} completed runs from {CSV_PATH.name}")

    fig = plt.figure(figsize=(18, 18))
    fig.suptitle("Zeebe Benchmark — Hardware Sizing Guide", fontsize=15, fontweight="bold", y=0.995)

    # 4 rows: [chart pair] [chart pair] [ES chart full-width] [table full-width]
    gs = fig.add_gridspec(4, 2,
                          height_ratios=[1, 1, 0.72, 1.45],
                          hspace=0.48, wspace=0.34,
                          left=0.06, right=0.97, top=0.97, bottom=0.02)

    chart_partitions(fig.add_subplot(gs[0, 0]), runs)
    chart_brokers(fig.add_subplot(gs[0, 1]), runs)
    chart_broker_cpu(fig.add_subplot(gs[1, 0]), runs)
    chart_broker_mem(fig.add_subplot(gs[1, 1]), runs)
    chart_es_mem(fig.add_subplot(gs[2, :]), runs)        # full width
    chart_sizing_guide(fig.add_subplot(gs[3, :]), runs)  # full width

    fig.savefig(OUTPUT_PATH, dpi=150, bbox_inches="tight")
    print(f"Saved → {OUTPUT_PATH}")


if __name__ == "__main__":
    main()
