#!/usr/bin/env python3
"""Render the PR-vs-daily load-test comparison Markdown table.

Invoked by the `Build comparison markdown` step of the `render-comparison`
job in `.github/workflows/camunda-pr-load-test.yaml`.
"""

from __future__ import annotations

import json
import math
import os
import sys
from datetime import datetime, timedelta
from typing import Any


# ---------- parsing ----------

def warn(msg: str) -> None:
    print(f"::warning::{msg}", file=sys.stderr)


def numeric_or_none(v: Any) -> float | None:
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, float)) and math.isfinite(v):
        return float(v)
    return None


def safe_json_parse(raw: str | None, label: str) -> dict:
    if not raw or not raw.strip():
        warn(f"{label} JSON is empty — column will render as n/a / '-'")
        return {}
    try:
        return json.loads(raw)
    except json.JSONDecodeError as e:
        warn(f"{label} JSON failed to parse — {e.msg}")
        return {}


# ---------- formatting ----------

def format_value(n: float | None, q: dict) -> str:
    if n is None:
        return "n/a"
    decimals = q.get("decimals", 2)
    unit = q.get("unit", "")
    fmt = q.get("format")
    if fmt == "integer":
        return f"{round(n):,}"
    if fmt == "percent":
        return f"{n:.{decimals}f}" + (unit if unit else "%")
    if fmt == "float":
        return f"{n:.{decimals}f}" + (f" {unit}" if unit else "")
    return str(n)


# ---------- comparison ----------

def delta(current: float | None, baseline: float | None) -> str:
    if current is None or baseline is None:
        return "n/a"
    if baseline == 0:
        return "0" if current == 0 else "+∞"
    diff = ((current - baseline) / baseline) * 100
    sign = "+" if diff >= 0 else ""
    return f"{sign}{diff:.1f}%"


def verdict(
    current: float | None,
    baseline: float | None,
    tolerance: float | None,
    higher_is_better: bool,
) -> str:
    if current is None or baseline is None:
        return "❔"
    if baseline == 0:
        if not higher_is_better:
            return "✅" if current == 0 else "⚠️"
        return "✅" if current > 0 else "❔"
    diff = ((current - baseline) / baseline) * 100
    tol = tolerance if tolerance is not None else 10
    if abs(diff) <= tol:
        return "✅"
    improved = diff > 0 if higher_is_better else diff < 0
    return "✅" if improved else "⚠️"


# ---------- rendering ----------

def load_inputs() -> dict:
    with open(os.environ["QUERIES_JSON_PATH"], encoding="utf-8") as f:
        queries_doc = json.load(f)
    with open(os.environ["OPTIMAL_JSON_PATH"], encoding="utf-8") as f:
        optimal = json.load(f)
    return {
        "namespace": os.environ.get("NAMESPACE", ""),
        "daily_namespace": os.environ.get("DAILY_NAMESPACE", ""),
        "daily_at": os.environ.get("DAILY_AT", ""),
        "duration_seconds": int(os.environ.get("DURATION_SECONDS", "0")),
        "storage_type": os.environ.get("STORAGE_TYPE", ""),
        "queries": queries_doc["queries"],
        "optimal_metrics": optimal.get("metrics", {}),
        "results": safe_json_parse(os.environ.get("PR_RESULTS_JSON"), "Current"),
        "daily_results": safe_json_parse(os.environ.get("DAILY_RESULTS_JSON"), "Daily"),
    }


def render_row(q: dict, ctx: dict) -> str:
    current = numeric_or_none(ctx["results"].get(q["name"]))
    daily = numeric_or_none(ctx["daily_results"].get(q["name"]))
    spec = ctx["optimal_metrics"].get(q["name"], {}) or {}
    optimal_val = spec.get("expected")
    daily_cell = "-" if daily is None else format_value(daily, q)
    return (
        f"| {q['description']} "
        f"| {format_value(current, q)} "
        f"| {daily_cell} "
        f"| {format_value(optimal_val, q)} "
        f"| {delta(current, daily)} "
        f"| {verdict(current, daily, spec.get('tolerance-percent'), q.get('higher_is_better', False))} |"
    )


def daily_window_start(daily_at: str, duration_seconds: int) -> str | None:
    # daily_at anchors the *end* of the PromQL range vector; the window starts
    # duration_seconds earlier. Returns None on parse failure so the header
    # falls back to omitting the window range rather than misreporting it.
    if not daily_at:
        return None
    try:
        end = datetime.fromisoformat(daily_at.replace("Z", "+00:00"))
    except ValueError:
        return None
    start = end - timedelta(seconds=duration_seconds)
    return start.strftime("%Y-%m-%dT%H:%M:%SZ")


def render_body(ctx: dict) -> str:
    storage = ctx["storage_type"]
    heading = f"## 📈 Load Test Metrics - {storage}" if storage else "## 📈 Load Test Metrics"
    daily_at = ctx["daily_at"]
    window_start = daily_window_start(daily_at, ctx["duration_seconds"])
    daily_line = (
        f"Daily reference: `{ctx['daily_namespace']}`"
        + (f" · first 30 min from `{window_start}`" if window_start else "")
        if ctx["daily_namespace"]
        else "_No completed daily-on-main run found; Daily column shows `-`._"
    )
    dashboard = (
        "https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe"
        f"?var-namespace={ctx['namespace']}"
    )
    run_link = (
        f"https://github.com/{os.environ.get('GITHUB_REPOSITORY', '')}"
        f"/actions/runs/{os.environ.get('GITHUB_RUN_ID', '')}"
    )

    lines = [
        heading,
        "",
        f"Window: {ctx['duration_seconds']}s · Namespace: `{ctx['namespace']}`",
        daily_line,
        "",
        "| Metric | Current | Daily | Optimal | Δ vs Daily | |",
        "|---|---:|---:|---:|---:|:---:|",
        *(render_row(q, ctx) for q in ctx["queries"]),
        "",
        f"📊 [Grafana dashboard]({dashboard}) · 🔁 [Workflow run]({run_link})",
        "",
        "_Verdict compares Current to Daily; ⚠️ flags regressions only._",
    ]
    return "\n".join(lines)


# ---------- I/O ----------

def emit(body: str) -> None:
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as f:
            f.write(body + "\n")

    output_path = os.environ.get("GITHUB_OUTPUT")
    if output_path:
        delim = "__COMPARISON_MD_EOF__"
        with open(output_path, "a", encoding="utf-8") as f:
            f.write(f"comparison-md<<{delim}\n{body}\n{delim}\n")


def main() -> int:
    emit(render_body(load_inputs()))
    return 0


if __name__ == "__main__":
    sys.exit(main())
