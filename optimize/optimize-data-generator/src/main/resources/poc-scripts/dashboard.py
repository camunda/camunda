"""
Business Value Dashboard — ES query benchmark
=============================================
Executes the 5 Business Value Dashboard queries once per interval, logs per-query
timings each round, and prints a summary with averages on Ctrl+C.

Four queries run against `optimize-reporting-metrics`; `active_processes` runs
against `optimize-process-instance` since active instances are not present in the
reporting-metrics index.

Usage:
    pip install "elasticsearch>=8,<9"
    python3 dashboard.py [--host localhost] [--port 9200] [--interval 1]

Options:
    --host       Elasticsearch host      (default: localhost)
    --port       Elasticsearch HTTP port (default: 9200)
    --interval   Seconds between rounds  (default: 1)
"""

import argparse
import sys
import time
from collections import defaultdict

from elasticsearch import Elasticsearch

# ── Configuration ─────────────────────────────────────────────────────────────
ES_HOST         = "http://localhost:9200"
METRICS_INDEX    = "optimize-reporting-metrics"
PI_INDEX         = "optimize-process-instance"
DEFAULT_INTERVAL = 1  # seconds between rounds

# Date window used for "scoped KPI" query — last 6 months
SCOPED_START    = "now-6M/M"
SCOPED_END      = "now/M"
# ──────────────────────────────────────────────────────────────────────────────

QUERY_NAMES = [
    "kpi_unscoped",
    "kpi_scoped",
    "top_processes",
    "trend",
    "active_processes",
]


def q_kpi_unscoped() -> dict:
    """
    KPI tiles — no date filter.
    Returns aggregate totals across all process instances:
      totalCost sum, valueCreated sum, tokenUsage sum, record count,
      and per-boolean-flag counts (slaBreached, escalated, manualOverride).
    """
    return {
        "size": 0,
        "aggs": {
            "totalCost_sum":       {"sum":         {"field": "totalCost"}},
            "valueCreated_sum":    {"sum":         {"field": "valueCreated"}},
            "tokenUsage_sum":      {"sum":         {"field": "tokenUsage"}},
            "pi_count":            {"value_count": {"field": "processInstanceKey"}},
            "slaBreached_count":   {"filter": {"term": {"slaBreached": True}}},
            "escalated_count":     {"filter": {"term": {"escalated": True}}},
            "manualOverride_count":{"filter": {"term": {"manualOverride": True}}},
            "agentTaskCount_sum":  {"sum":         {"field": "agentTaskCount"}},
            "humanTaskCount_sum":  {"sum":         {"field": "humanTaskCount"}},
            "autoTaskCount_sum":   {"sum":         {"field": "autoTaskCount"}},
        },
    }


def q_kpi_scoped(start: str = SCOPED_START, end: str = SCOPED_END) -> dict:
    """
    KPI tiles — filtered to a specific date window (lastSeenAt).
    Same aggregations as unscoped, restricted to the configured time range.
    """
    return {
        "size": 0,
        "query": {
            "range": {
                "lastSeenAt": {"gte": start, "lte": end}
            }
        },
        "aggs": {
            "totalCost_sum":       {"sum":         {"field": "totalCost"}},
            "valueCreated_sum":    {"sum":         {"field": "valueCreated"}},
            "tokenUsage_sum":      {"sum":         {"field": "tokenUsage"}},
            "pi_count":            {"value_count": {"field": "processInstanceKey"}},
            "slaBreached_count":   {"filter": {"term": {"slaBreached": True}}},
            "escalated_count":     {"filter": {"term": {"escalated": True}}},
            "manualOverride_count":{"filter": {"term": {"manualOverride": True}}},
            "agentTaskCount_sum":  {"sum":         {"field": "agentTaskCount"}},
            "humanTaskCount_sum":  {"sum":         {"field": "humanTaskCount"}},
            "autoTaskCount_sum":   {"sum":         {"field": "autoTaskCount"}},
        },
    }


def q_top_processes(size: int = 10) -> dict:
    """
    Top Processes table.
    Groups by processDefinitionKey (top `size` buckets), then resolves the human-readable
    process label via an inner terms sub-aggregation (first bucket key).
    Reports per-process: PI count, totalCost sum, valueCreated sum, tokenUsage sum.
    """
    return {
        "size": 0,
        "aggs": {
            "by_process": {
                "terms": {
                    "field": "processDefinitionKey",
                    "size":  size,
                    "order": {"totalCost_sum": "desc"},
                },
                "aggs": {
                    "processLabel": {
                        "terms": {"field": "processLabel", "size": 1}
                    },
                    "pi_count":        {"value_count": {"field": "processInstanceKey"}},
                    "totalCost_sum":   {"sum":         {"field": "totalCost"}},
                    "valueCreated_sum":{"sum":         {"field": "valueCreated"}},
                    "tokenUsage_sum":  {"sum":         {"field": "tokenUsage"}},
                },
            }
        },
    }


def q_trend(interval: str = "month") -> dict:
    """
    Monthly Trend chart.
    Buckets all records by `lastSeenAt` calendar month, reporting per-month:
    totalCost sum, valueCreated sum, PI count.
    Uses `lastSeenAt` (import timestamp proxy) as the time axis.
    """
    return {
        "size": 0,
        "aggs": {
            "by_month": {
                "date_histogram": {
                    "field":             "lastSeenAt",
                    "calendar_interval": interval,
                    "format":            "yyyy-MM",
                    "min_doc_count":     1,
                },
                "aggs": {
                    "totalCost_sum":   {"sum":         {"field": "totalCost"}},
                    "valueCreated_sum":{"sum":         {"field": "valueCreated"}},
                    "pi_count":        {"value_count": {"field": "processInstanceKey"}},
                },
            }
        },
    }


def q_active_processes() -> dict:
    """
    Active Processes KPI.
    Counts process instances currently in ACTIVE state from optimize-process-instance.
    Queried separately from the metrics index since reporting-metrics only holds
    completed/terminated instances.
    """
    return {
        "size": 0,
        "query": {
            "term": {"state": "ACTIVE"}
        },
        "aggs": {
            "activeProcesses": {"value_count": {"field": "processInstanceId"}}
        },
    }


ALL_QUERIES = {
    "kpi_unscoped":     q_kpi_unscoped,
    "kpi_scoped":       q_kpi_scoped,
    "top_processes":    q_top_processes,
    "trend":            q_trend,
    "active_processes": q_active_processes,
}


def index_for(name: str) -> str:
    return PI_INDEX if name == "active_processes" else METRICS_INDEX


def run_query(es: Elasticsearch, name: str) -> tuple[float, dict]:
    """Execute a single named query, return (elapsed_seconds, raw_response)."""
    body = ALL_QUERIES[name]()
    t0 = time.monotonic()
    resp = es.search(index=index_for(name), body=body)
    elapsed = time.monotonic() - t0
    return elapsed, resp.body


def extract_highlights(name: str, resp: dict) -> str:
    """Return a short human-readable result string for each query type."""
    aggs = resp.get("aggregations", {})
    try:
        if name in ("kpi_unscoped", "kpi_scoped"):
            pi_count   = int(aggs["pi_count"]["value"])
            cost_sum   = aggs["totalCost_sum"]["value"] or 0.0
            value_sum  = aggs["valueCreated_sum"]["value"] or 0.0
            token_sum  = int(aggs["tokenUsage_sum"]["value"] or 0)
            return (
                f"pi={pi_count:,}  cost={cost_sum:,.0f}  "
                f"value={value_sum:,.0f}  tokens={token_sum:,}"
            )
        elif name == "top_processes":
            buckets = aggs["by_process"]["buckets"]
            return f"{len(buckets)} process bucket(s)"
        elif name == "trend":
            buckets = aggs["by_month"]["buckets"]
            months  = [b["key_as_string"] for b in buckets]
            span    = f"{months[0]}..{months[-1]}" if months else "—"
            return f"{len(buckets)} month(s)  [{span}]"
        elif name == "active_processes":
            count = int(aggs["activeProcesses"]["value"])
            return f"active={count:,}"
    except (KeyError, TypeError, IndexError):
        pass
    return "—"


def print_summary(all_times: dict[str, list[float]], total_elapsed: float) -> None:
    sep = "═" * 80
    print(f"\n{sep}")
    print("  BENCHMARK SUMMARY")
    print(sep)

    if not any(all_times.values()):
        print("  No rounds completed.")
        print(sep)
        return

    header = f"  {'Query':<22}  {'Rounds':>6}  {'Total':>9}  {'Avg':>9}  {'Min':>9}  {'Max':>9}"
    print(header)
    print("  " + "-" * 76)

    for name in QUERY_NAMES:
        times = all_times[name]
        if not times:
            continue
        rounds = len(times)
        total  = sum(times)
        avg    = total / rounds
        mn     = min(times)
        mx     = max(times)
        print(
            f"  {name:<22}  {rounds:>6}  {total:>8.2f}s  "
            f"{avg:>8.3f}s  {mn:>8.3f}s  {mx:>8.3f}s"
        )

    print("  " + "-" * 76)
    print(f"  Total wall time: {total_elapsed:.2f}s")
    print(sep)


def main() -> None:
    parser = argparse.ArgumentParser(description="Business Value Dashboard ES query benchmark")
    parser.add_argument("--host",     default="localhost")
    parser.add_argument("--port",     type=int, default=9200)
    parser.add_argument("--interval", type=float, default=DEFAULT_INTERVAL,
                        help="Seconds between rounds (default: 1)")
    args = parser.parse_args()

    host = f"http://{args.host}:{args.port}"
    es   = Elasticsearch(host)

    all_times: dict[str, list[float]] = defaultdict(list)
    round_num     = 0
    overall_start = time.monotonic()

    print(f"Benchmarking Business Value Dashboard queries against {host}/{METRICS_INDEX}")
    print(f"Interval: {args.interval}s  |  Press Ctrl+C to stop and print summary\n")

    try:
        while True:
            round_num += 1
            round_start = time.monotonic()
            print(f"[Round {round_num}]")

            for name in QUERY_NAMES:
                try:
                    elapsed, resp = run_query(es, name)
                    all_times[name].append(elapsed)
                    highlights = extract_highlights(name, resp)
                    print(f"  {name:<22}  {elapsed:.3f}s  {highlights}")
                except Exception as exc:
                    print(f"  {name:<22}  ERROR: {exc}")

            round_elapsed = time.monotonic() - round_start
            wait = max(0.0, args.interval - round_elapsed)
            print(f"  round time: {round_elapsed:.3f}s  |  sleeping {wait:.2f}s\n")
            if wait > 0:
                time.sleep(wait)

    except KeyboardInterrupt:
        print("\nInterrupted.")

    total_elapsed = time.monotonic() - overall_start
    print_summary(all_times, total_elapsed)
    print(f"Finished. {round_num} round(s) in {total_elapsed:.2f}s.")


if __name__ == "__main__":
    main()
