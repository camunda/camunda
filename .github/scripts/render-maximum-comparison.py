#!/usr/bin/env python3
"""Render the base-vs-PR maximum load test comparison Markdown table.

Invoked by the `Build comparison markdown` step of the `render-comparison` job in
`.github/workflows/camunda-pr-maximum-load-test.yaml`. Every side (base and PR) runs several
identical clusters above capacity, so the table compares medians and shows the spread of each side.
"""

import importlib.util
import json
import os
import statistics
import sys
from typing import Any

# Reuse the parsing and formatting helpers of the regression renderer (hyphenated module name).
_SCRIPT = os.path.join(os.path.dirname(__file__), "render-comparison.py")
_spec = importlib.util.spec_from_file_location("render_comparison", _SCRIPT)
rc = importlib.util.module_from_spec(_spec)
sys.modules["render_comparison"] = rc
_spec.loader.exec_module(rc)

# Identical clusters at 900 PI/s spread by about +-5%. With the median of 3 clusters per side only
# a change of roughly 10% or more is visible, so a smaller delta is reported as within noise.
DETECTION_LIMIT_PERCENT = 10
# A cluster this far from the median of its own side is flagged, it may have had a failover or a
# slow host and distorts the median less than the mean but is still worth a look.
OUTLIER_PERCENT = 15
# Metrics shown in the comparison, the first one is the headline.
METRICS = [
    "completed-pi-rate-per-second",
    "throughput-per-second",
    "backpressure-percent",
    "request-response-latency",
]


def values(runs: dict[str, dict], name: str) -> list[float]:
    found = (rc.numeric_or_none(results.get(name)) for results in runs.values())
    return [v for v in found if v is not None]


def median_or_none(vals: list[float]) -> float | None:
    return statistics.median(vals) if vals else None


def range_cell(vals: list[float], q: dict) -> str:
    if not vals:
        return "n/a"
    return f"{rc.format_value(min(vals), q)} to {rc.format_value(max(vals), q)}"


def outliers(runs: dict[str, dict], name: str) -> list[str]:
    """Namespaces whose value deviates from the median of their side by more than OUTLIER_PERCENT."""
    median = median_or_none(values(runs, name))
    if not median:
        return []
    flagged = []
    for namespace, results in runs.items():
        value = rc.numeric_or_none(results.get(name))
        if value is not None and abs(value - median) / median * 100 > OUTLIER_PERCENT:
            flagged.append(namespace)
    return flagged


def verdict(base: float | None, pr: float | None, higher_is_better: bool) -> str:
    if base is None or pr is None:
        return "❔"
    if base == 0:
        return "≈" if pr == 0 else "❔"
    diff = (pr - base) / base * 100
    if abs(diff) < DETECTION_LIMIT_PERCENT:
        return "≈ within noise"
    improved = diff > 0 if higher_is_better else diff < 0
    return "✅ better" if improved else "⚠️ worse"


def render_row(q: dict, base_runs: dict[str, dict], pr_runs: dict[str, dict]) -> str:
    base_vals = values(base_runs, q["name"])
    pr_vals = values(pr_runs, q["name"])
    base = median_or_none(base_vals)
    pr = median_or_none(pr_vals)
    return (
        f"| {q['description']} "
        f"| {rc.format_value(base, q)} ({range_cell(base_vals, q)}) "
        f"| {rc.format_value(pr, q)} ({range_cell(pr_vals, q)}) "
        f"| {rc.delta(pr, base)} "
        f"| {verdict(base, pr, q.get('higher_is_better', False))} |"
    )


def render_body(ctx: dict) -> str:
    setup = " - ".join(part for part in (ctx["storage_type"], ctx["protocol"]) if part)
    heading = f"## 📈 Maximum Load Test - {setup}" if setup else "## 📈 Maximum Load Test"
    by_name = {q["name"]: q for q in ctx["queries"]}
    rows = [render_row(by_name[name], ctx["base_runs"], ctx["pr_runs"]) for name in METRICS if name in by_name]
    flagged = outliers(ctx["base_runs"], METRICS[0]) + outliers(ctx["pr_runs"], METRICS[0])
    run_link = (
        f"https://github.com/{os.environ.get('GITHUB_REPOSITORY', '')}"
        f"/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}"
    )

    lines = [
        heading,
        "",
        f"Offered rate: {ctx['rate']} PI/s per cluster, above the capacity of the setup. "
        f"Clusters with results: base {len(ctx['base_runs'])}, PR {len(ctx['pr_runs'])}.",
        f"Base: `{ctx['base_sha']}` · PR: `{ctx['head_sha']}`",
        "",
        "| Metric | Base median (range) | PR median (range) | Δ | |",
        "|---|---:|---:|---:|:---|",
        *rows,
        "",
    ]
    if flagged:
        lines += [
            f"⚠️ Outliers (more than {OUTLIER_PERCENT}% from the median of their side, check for a "
            "failover or a slow host): " + ", ".join(f"`{ns}`" for ns in flagged),
            "",
        ]
    lines += [
        f"🔁 [Workflow run]({run_link})",
        "",
        f"_Identical clusters differ by about 5%, so only a difference of {DETECTION_LIMIT_PERCENT}% "
        "or more is visible. A smaller change is reported as within noise, it may still be real._",
    ]
    return "\n".join(lines)


def load_runs(results_dir: str, namespaces: str, side: str) -> dict[str, dict]:
    runs = {}
    for namespace in namespaces.split():
        # actions/download-artifact puts each artifact in a folder named like the artifact.
        path = os.path.join(results_dir, f"load-test-results-{namespace}", "results.json")
        results = rc.read_results(path, f"{side} {namespace}")
        if results:
            runs[namespace] = results
    return runs


def load_inputs() -> dict[str, Any]:
    with open(os.environ["QUERIES_JSON_PATH"], encoding="utf-8") as f:
        queries_doc = json.load(f)
    results_dir = os.environ["RESULTS_DIR"]
    return {
        "queries": queries_doc["queries"],
        "storage_type": os.environ.get("STORAGE_TYPE", ""),
        "protocol": os.environ.get("PROTOCOL", ""),
        "rate": os.environ.get("RATE", ""),
        "base_sha": os.environ.get("BASE_SHA", "")[:12],
        "head_sha": os.environ.get("HEAD_SHA", "")[:12],
        "base_runs": load_runs(results_dir, os.environ.get("BASE_NAMESPACES", ""), "Base"),
        "pr_runs": load_runs(results_dir, os.environ.get("PR_NAMESPACES", ""), "PR"),
    }


def main() -> int:
    rc.emit(render_body(load_inputs()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
