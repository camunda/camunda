#!/usr/bin/env python3
"""Render the PR-vs-daily load-test comparison Markdown table.

Reads queries (JSON form of queries.yaml), optimal targets, and per-namespace
metric results from env vars, then writes the same Markdown body to both
$GITHUB_STEP_SUMMARY and the `comparison-md` step output via $GITHUB_OUTPUT.

Invoked by the `Build comparison markdown` step of the
`render-comparison` job in `.github/workflows/camunda-pr-load-test.yaml`.
"""

from __future__ import annotations

import json
import math
import os
import sys
from typing import Any


def warn(msg: str) -> None:
    # GitHub Actions `::warning::` annotation; mirrors core.warning() from
    # actions/github-script.
    print(f"::warning::{msg}", file=sys.stderr)


def numeric_or_none(v: Any) -> float | None:
    if isinstance(v, bool):
        return None
    if isinstance(v, (int, float)) and math.isfinite(v):
        return float(v)
    return None


def safe_json_parse(raw: str | None, label: str) -> dict:
    # Upstream job outputs are empty strings when the job failed or was
    # skipped (e.g. no daily namespace found). Parse defensively.
    if not raw or not raw.strip():
        warn(f"{label} JSON is empty — column will render as n/a / '-'")
        return {}
    try:
        return json.loads(raw)
    except json.JSONDecodeError as e:
        warn(f"{label} JSON failed to parse — {e.msg}")
        return {}


def format_value(n: float | None, q: dict) -> str:
    if n is None or not math.isfinite(n):
        return "n/a"
    decimals = q.get("decimals", 2)
    unit = q.get("unit", "") or ""
    fmt = q.get("format")
    if fmt == "integer":
        return f"{round(n):,}"
    if fmt == "percent":
        return f"{n:.{decimals}f}" + (unit if unit else "%")
    if fmt == "float":
        return f"{n:.{decimals}f}" + (f" {unit}" if unit else "")
    return str(n)


def format_or_dash(n: float | None, q: dict) -> str:
    # Daily column uses '-' (not 'n/a') so a missing baseline reads visually
    # distinct from "the query ran and got null".
    if n is None or not math.isfinite(n):
        return "-"
    return format_value(n, q)


def optimal_for(spec: dict) -> float | None:
    if spec.get("expected") is not None:
        return spec["expected"]
    if spec.get("per-second") is not None:
        return spec["per-second"]
    return None


def verdict(
    current: float | None,
    baseline: float | None,
    tolerance: float | None,
    higher_is_better: bool,
) -> str:
    if current is None or not math.isfinite(current) or baseline is None:
        return "❔"
    if baseline == 0:
        if not higher_is_better and current == 0:
            return "✅"
        if not higher_is_better and current > 0:
            return "⚠️"
        return "✅" if current > 0 else "❔"
    diff = ((current - baseline) / baseline) * 100
    tol = tolerance if tolerance is not None else 10
    if abs(diff) <= tol:
        return "✅"
    improved = diff > 0 if higher_is_better else diff < 0
    return "✅" if improved else "⚠️"


def delta(current: float | None, baseline: float | None) -> str:
    if current is None or not math.isfinite(current) or baseline is None:
        return "n/a"
    if baseline == 0:
        return "0" if current == 0 else "+∞"
    diff = ((current - baseline) / baseline) * 100
    sign = "+" if diff >= 0 else ""
    return f"{sign}{diff:.1f}%"


def load_inputs() -> dict:
    queries_path = os.environ["QUERIES_JSON_PATH"]
    optimal_path = os.environ["OPTIMAL_JSON_PATH"]
    with open(queries_path, encoding="utf-8") as f:
        queries_doc = json.load(f)
    with open(optimal_path, encoding="utf-8") as f:
        optimal = json.load(f)
    return {
        "namespace": os.environ.get("NAMESPACE", ""),
        "daily_namespace": os.environ.get("DAILY_NAMESPACE", "") or "",
        "duration_seconds": int(os.environ.get("DURATION_SECONDS", "0")),
        "storage_type": os.environ.get("STORAGE_TYPE", "") or "",
        "queries": queries_doc["queries"],
        "optimal_metrics": optimal.get("metrics", {}) or {},
        "results": safe_json_parse(os.environ.get("PR_RESULTS_JSON"), "Current"),
        "daily_results": safe_json_parse(os.environ.get("DAILY_RESULTS_JSON"), "Daily"),
    }


def render_row(q: dict, ctx: dict) -> str:
    current = numeric_or_none(ctx["results"].get(q["name"]))
    daily = numeric_or_none(ctx["daily_results"].get(q["name"]))
    spec = ctx["optimal_metrics"].get(q["name"], {}) or {}
    optimal_val = optimal_for(spec)
    return (
        f"| {q['description']} "
        f"| {format_value(current, q)} "
        f"| {format_or_dash(daily, q)} "
        f"| {format_value(optimal_val, q)} "
        f"| {delta(current, daily)} "
        f"| {verdict(current, daily, spec.get('tolerance-percent'), q.get('higher_is_better', False))} |"
    )


def render_body(ctx: dict) -> str:
    storage = ctx["storage_type"]
    heading = (
        f"## 📈 Load Test Metrics - {storage}"
        if storage
        else "## 📈 Load Test Metrics"
    )
    daily_line = (
        f"Daily reference: `{ctx['daily_namespace']}`"
        if ctx["daily_namespace"]
        else "_No active daily-on-main namespace found; Daily column shows `-`._"
    )
    dashboard = (
        "https://dashboard.benchmark.camunda.cloud/d/zeebe-dashboard/zeebe"
        f"?var-namespace={ctx['namespace']}"
    )
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    run_link = f"https://github.com/{repo}/actions/runs/{run_id}"

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
        (
            "_Metrics defined in `load-tests/docs/scripts/queries.yaml`. "
            "Daily reference is the most recent active `c8-medic-daily-*-test` namespace on main. "
            "Optimal target at `load-tests/docs/scripts/optimal.json`. "
            "Verdict compares Current to Daily; tolerance from Optimal. "
            "⚠️ only fires on the regression side — improvements outside tolerance still show ✅._"
        ),
    ]
    return "\n".join(lines)


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
    ctx = load_inputs()
    body = render_body(ctx)
    emit(body)
    return 0


if __name__ == "__main__":
    sys.exit(main())
