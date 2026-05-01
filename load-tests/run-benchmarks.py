#!/usr/bin/env python3
"""
Zeebe benchmark automation script.

Triggers camunda-load-test.yml with different configurations, waits for each
deployment to complete, samples Grafana/Prometheus metrics over a stable window,
and appends results to a CSV file.

Usage:
    pip install requests
    export GITHUB_TOKEN=<token with workflow write permission>
    export GRAFANA_TOKEN=<Grafana service account token>
    python3 run-benchmarks.py

The script runs configs sequentially. Each run takes roughly:
    ~30 min  build + deploy (workflow)
  + ~10 min  stabilization wait (timers settle)
  +  ~5 min  metric sampling window
  = ~45 min per config

Results are appended to benchmark_results.csv so you can interrupt and resume.
"""

import csv
import json
import os
import sys
import time
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from typing import Optional

import requests

# ── Environment ───────────────────────────────────────────────────────────────

GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
GRAFANA_TOKEN = os.environ.get("GRAFANA_TOKEN", "")
GRAFANA_URL = os.environ.get("GRAFANA_URL", "https://dashboard.benchmark.camunda.cloud")
GITHUB_REPO = "camunda/camunda"
WORKFLOW_FILE = "camunda-load-test.yml"
OUTPUT_CSV = "benchmark_results.csv"

# Branch/ref to build and deploy
DEFAULT_REF = "main"

# Seconds to wait after the workflow completes before sampling metrics.
# The typical process has 1-minute timer events, which create a burst at T+1min
# and T+2min. Wait long enough for those bursts to settle.
STABILIZATION_WAIT_S = 10 * 60  # 10 minutes

# Duration of the metric sampling window (seconds).
METRIC_WINDOW_S = 5 * 60  # 5 minutes

# GitHub run poll interval (seconds).
POLL_INTERVAL_S = 60


# ── Data model ────────────────────────────────────────────────────────────────

@dataclass
class BenchmarkConfig:
    # Human-readable name — used as the Kubernetes namespace (prefixed with c8-)
    name: str

    # Workload
    target_pi_s: int
    load_test_load: str       # --set starter.rate=... etc.
    platform_helm_values: str  # --set orchestration.* etc.

    # Broker topology (for CSV columns — must match what platform_helm_values sets)
    broker_replicas: int
    partition_count: int
    broker_node: str
    broker_cpu_request: str
    broker_cpu_limit: str
    broker_memory: str        # e.g. "12Gi"
    broker_cpu_threads: int
    broker_io_threads: int

    # Elasticsearch (static across runs)
    es_replicas: int = 3
    es_node: str = "n2-standard-8"
    es_cpu: str = "7000m"
    es_memory: str = "8Gi"
    es_heap: str = "3Gi"

    # Workflow inputs
    ref: str = DEFAULT_REF
    scenario: str = "typical"
    secondary_storage: str = "elasticsearch"
    ttl: int = 1
    stable_vms: bool = False


@dataclass
class BenchmarkResult:
    run: str
    timestamp: str
    target_pi_s: int
    achieved_pi_s: Optional[float]
    achieved_pct: Optional[float]
    broker_replicas: int
    broker_node: str
    broker_cpu: str
    broker_memory: str
    broker_heap: str
    broker_threads_cpu: int
    broker_threads_io: int
    es_replicas: int
    es_node: str
    es_cpu: str
    es_memory: str
    es_heap: str
    dropped_req_s: Optional[float]
    max_inflight_req: Optional[float]
    notes: str = ""


CSV_FIELDS = [
    "run", "timestamp", "target_pi_s", "achieved_pi_s", "achieved_pct",
    "broker_replicas", "broker_node", "broker_cpu", "broker_memory", "broker_heap",
    "broker_threads_cpu", "broker_threads_io",
    "es_replicas", "es_node", "es_cpu", "es_memory", "es_heap",
    "dropped_req_s", "max_inflight_req", "notes",
]


# ── GitHub API ────────────────────────────────────────────────────────────────

def _gh_headers() -> dict:
    return {
        "Authorization": f"Bearer {GITHUB_TOKEN}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


def trigger_workflow(config: BenchmarkConfig) -> datetime:
    """Trigger workflow_dispatch. Returns the time just before dispatch."""
    namespace = f"c8-{config.name}"
    url = (
        f"https://api.github.com/repos/{GITHUB_REPO}"
        f"/actions/workflows/{WORKFLOW_FILE}/dispatches"
    )
    payload = {
        "ref": config.ref,
        "inputs": {
            "name": namespace,
            "ref": config.ref,
            "scenario": config.scenario,
            "secondary-storage-type": config.secondary_storage,
            "load-test-load": config.load_test_load,
            "platform-helm-values": config.platform_helm_values,
            "ttl": str(config.ttl),
            "stable-vms": str(config.stable_vms).lower(),
            "enable-optimize": "true",
        },
    }
    dispatch_time = datetime.now(timezone.utc)
    resp = requests.post(url, headers=_gh_headers(), json=payload, timeout=30)
    resp.raise_for_status()
    print(f"  [{config.name}] Triggered → namespace: {namespace}")
    return dispatch_time


def find_run_id(dispatch_time: datetime, retries: int = 12) -> Optional[int]:
    """Poll until a workflow run created after dispatch_time appears."""
    url = (
        f"https://api.github.com/repos/{GITHUB_REPO}"
        f"/actions/workflows/{WORKFLOW_FILE}/runs"
    )
    for attempt in range(retries):
        resp = requests.get(url, headers=_gh_headers(), params={"per_page": 10}, timeout=30)
        resp.raise_for_status()
        for run in resp.json().get("workflow_runs", []):
            created = datetime.fromisoformat(run["created_at"].replace("Z", "+00:00"))
            if created >= dispatch_time:
                return run["id"]
        print(f"  Run not visible yet (attempt {attempt + 1}/{retries}), retrying in 15s…")
        time.sleep(15)
    return None


def wait_for_run(run_id: int, label: str) -> str:
    """Block until the workflow run completes. Returns conclusion string."""
    url = f"https://api.github.com/repos/{GITHUB_REPO}/actions/runs/{run_id}"
    while True:
        resp = requests.get(url, headers=_gh_headers(), timeout=30)
        resp.raise_for_status()
        data = resp.json()
        status, conclusion = data["status"], data.get("conclusion")
        elapsed = _elapsed_min(data["created_at"])
        print(f"  [{label}] run={run_id} status={status} conclusion={conclusion} elapsed={elapsed:.0f}min")
        if status == "completed":
            return conclusion or "unknown"
        time.sleep(POLL_INTERVAL_S)


def _elapsed_min(created_at_iso: str) -> float:
    created = datetime.fromisoformat(created_at_iso.replace("Z", "+00:00"))
    return (datetime.now(timezone.utc) - created).total_seconds() / 60


# ── Grafana / Prometheus API ──────────────────────────────────────────────────

_grafana_ds_uid: Optional[str] = None


def _grafana_headers() -> dict:
    return {"Authorization": f"Bearer {GRAFANA_TOKEN}"}


def _discover_prometheus_uid() -> str:
    """Find the UID of the Prometheus datasource in Grafana."""
    global _grafana_ds_uid
    if _grafana_ds_uid:
        return _grafana_ds_uid
    resp = requests.get(
        f"{GRAFANA_URL}/api/datasources",
        headers=_grafana_headers(),
        timeout=15,
    )
    resp.raise_for_status()
    for ds in resp.json():
        if ds.get("type") == "prometheus":
            _grafana_ds_uid = ds["uid"]
            print(f"  Grafana Prometheus datasource UID: {_grafana_ds_uid}")
            return _grafana_ds_uid
    raise RuntimeError("No Prometheus datasource found in Grafana")


def _query_range(promql: str, start: float, end: float, step: int = 30) -> list[float]:
    """Run a Prometheus range query via Grafana and return all float values."""
    uid = _discover_prometheus_uid()
    url = f"{GRAFANA_URL}/api/datasources/proxy/uid/{uid}/api/v1/query_range"
    params = {"query": promql, "start": int(start), "end": int(end), "step": step}
    try:
        resp = requests.get(url, headers=_grafana_headers(), params=params, timeout=30)
        resp.raise_for_status()
    except Exception as e:
        print(f"  Grafana query failed: {e}  query={promql[:80]}")
        return []
    values = []
    for series in resp.json().get("data", {}).get("result", []):
        for _, v in series.get("values", []):
            try:
                values.append(float(v))
            except (ValueError, TypeError):
                pass
    return values


def _avg(values: list[float]) -> Optional[float]:
    return round(sum(values) / len(values), 2) if values else None


def fetch_metrics(namespace: str, start: float, end: float) -> dict:
    """
    Query the three key metrics for a namespace over [start, end].

    Metric names sourced from monitor/grafana/zeebe.json and
    monitor/grafana/camunda-performance.json dashboards.
    """
    ns = namespace

    # PI activation rate — zeebe_executed_instances_total{action="activated"}
    # is the same counter the camunda-performance dashboard uses for "PI/s"
    pi_vals = _query_range(
        f'sum(rate(zeebe_executed_instances_total{{namespace=~"{ns}",action="activated"}}[1m]))',
        start, end,
    )

    # Total dropped requests per second across all partitions
    drop_vals = _query_range(
        f'sum(rate(zeebe_dropped_request_count_total{{namespace=~"{ns}"}}[1m]))',
        start, end,
    )

    # Minimum per-partition in-flight request limit (most constrained partition)
    limit_vals = _query_range(
        f'min(zeebe_backpressure_requests_limit{{namespace=~"{ns}"}})',
        start, end,
    )

    return {
        "achieved_pi_s": _avg(pi_vals),
        "dropped_req_s": _avg(drop_vals),
        "max_inflight_req": _avg(limit_vals),
    }


# ── Core orchestration ────────────────────────────────────────────────────────

def run_benchmark(config: BenchmarkConfig) -> BenchmarkResult:
    namespace = f"c8-{config.name}"
    timestamp = datetime.now(timezone.utc).isoformat()
    notes = ""

    print(f"\n{'─' * 60}")
    print(f"Config : {config.name}")
    print(f"Target : {config.target_pi_s} PI/s")
    print(f"{'─' * 60}")

    # 1. Trigger
    dispatch_time = trigger_workflow(config)
    time.sleep(10)

    # 2. Locate the GitHub Actions run
    run_id = find_run_id(dispatch_time)
    if not run_id:
        notes = "ERROR: run_id not found after dispatch"
        print(f"  {notes}")
        return _build_result(config, namespace, timestamp, {}, notes)

    print(f"  Run ID: {run_id}")

    # 3. Wait for build + deploy to complete
    conclusion = wait_for_run(run_id, config.name)
    if conclusion != "success":
        notes = f"workflow_conclusion={conclusion}"
        print(f"  Workflow did not succeed: {notes}")
        return _build_result(config, namespace, timestamp, {}, notes)

    # 4. Stabilization — let the cluster settle after deployment.
    #    The typical_process.bpmn has 1-minute timer events that create a burst
    #    at T+1min and T+2min after starters begin. Wait past those bursts.
    print(f"  Workflow complete. Waiting {STABILIZATION_WAIT_S // 60}min for cluster to stabilise…")
    time.sleep(STABILIZATION_WAIT_S)

    # 5. Sample metrics
    print(f"  Sampling metrics for {METRIC_WINDOW_S // 60}min…")
    sample_start = time.time()
    time.sleep(METRIC_WINDOW_S)
    sample_end = time.time()

    if not GRAFANA_TOKEN:
        notes = "GRAFANA_TOKEN not set — metrics skipped"
        print(f"  {notes}")
        return _build_result(config, namespace, timestamp, {}, notes)

    metrics = fetch_metrics(namespace, sample_start, sample_end)
    print(f"  Metrics: {metrics}")
    return _build_result(config, namespace, timestamp, metrics, notes)


def _build_result(
    config: BenchmarkConfig,
    namespace: str,
    timestamp: str,
    metrics: dict,
    notes: str,
) -> BenchmarkResult:
    achieved = metrics.get("achieved_pi_s")
    pct = round(achieved / config.target_pi_s * 100, 1) if achieved else None
    mem_gi = float(config.broker_memory.rstrip("Gi"))
    heap = f"~{mem_gi * 0.25:.1f} GiB"  # default MaxRAMPercentage=25

    return BenchmarkResult(
        run=namespace,
        timestamp=timestamp,
        target_pi_s=config.target_pi_s,
        achieved_pi_s=achieved,
        achieved_pct=pct,
        broker_replicas=config.broker_replicas,
        broker_node=config.broker_node,
        broker_cpu=f"{config.broker_cpu_request} req / {config.broker_cpu_limit} limit",
        broker_memory=config.broker_memory,
        broker_heap=heap,
        broker_threads_cpu=config.broker_cpu_threads,
        broker_threads_io=config.broker_io_threads,
        es_replicas=config.es_replicas,
        es_node=config.es_node,
        es_cpu=config.es_cpu,
        es_memory=config.es_memory,
        es_heap=config.es_heap,
        dropped_req_s=metrics.get("dropped_req_s"),
        max_inflight_req=metrics.get("max_inflight_req"),
        notes=notes,
    )


def append_csv(result: BenchmarkResult) -> None:
    write_header = not os.path.exists(OUTPUT_CSV)
    with open(OUTPUT_CSV, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_FIELDS)
        if write_header:
            writer.writeheader()
        writer.writerow(asdict(result))
    print(f"  Written → {OUTPUT_CSV}")


# ── Helpers for building platform_helm_values ─────────────────────────────────

def platform_values(
    cluster_size: int,
    partition_count: int,
    cpu_threads: int,
    cpu_req: str,
    cpu_limit: str,
    memory: str,
    node: str = "n2-standard-8",
) -> str:
    """Build the --set / --set-string flags for the Camunda platform Helm chart."""
    return " ".join([
        "--set-file 'orchestration.extraConfiguration[1].content=./camunda-platform-override-values.yaml'",
        f"--set-string orchestration.clusterSize={cluster_size}",
        f"--set-string orchestration.partitionCount={partition_count}",
        f"--set-string orchestration.cpuThreadCount={cpu_threads}",
        f"--set orchestration.resources.requests.cpu={cpu_req}",
        f"--set orchestration.resources.limits.cpu={cpu_limit}",
        f"--set orchestration.resources.requests.memory={memory}",
        f"--set orchestration.resources.limits.memory={memory}",
        f"--set orchestration.nodeSelector.component=benchmark-{node}",
        "--set orchestration.tolerations[0].key=nodepool",
        "--set orchestration.tolerations[0].operator=Equal",
        f"--set orchestration.tolerations[0].value={node}",
        "--set orchestration.tolerations[0].effect=NoSchedule",
    ])


def load_test_load(rate: int, starter_replicas: int = 3, worker_replicas: int = 20) -> str:
    return (
        f"--set starter.rate={rate} "
        f"--set starter.replicas={starter_replicas} "
        f"--set workers.worker.replicas={worker_replicas}"
    )


# ── Test matrix ───────────────────────────────────────────────────────────────
#
# Edit this list to define what you want to sweep.
# Names must be unique and DNS-safe (lowercase, hyphens only).
# Each run takes ~45 minutes.

TEST_MATRIX = [
    # ── Fix: fewer partitions, more memory, more workers ──────────────────────
    BenchmarkConfig(
        name="500-6b-6p-n8-6cpu-12g",
        target_pi_s=500,
        load_test_load=load_test_load(500, worker_replicas=20),
        platform_helm_values=platform_values(6, 6, 6, "6000m", "6000m", "12Gi"),
        broker_replicas=6, partition_count=6,
        broker_node="n2-standard-8",
        broker_cpu_request="6000m", broker_cpu_limit="6000m",
        broker_memory="12Gi", broker_cpu_threads=6, broker_io_threads=3,
    ),
    # ── Compare: 12 partitions with same resources ────────────────────────────
    BenchmarkConfig(
        name="500-6b-12p-n8-6cpu-12g",
        target_pi_s=500,
        load_test_load=load_test_load(500, worker_replicas=20),
        platform_helm_values=platform_values(6, 12, 6, "6000m", "6000m", "12Gi"),
        broker_replicas=6, partition_count=12,
        broker_node="n2-standard-8",
        broker_cpu_request="6000m", broker_cpu_limit="6000m",
        broker_memory="12Gi", broker_cpu_threads=6, broker_io_threads=3,
    ),
    # ── Compare: fewer brokers (3), fewer partitions ──────────────────────────
    BenchmarkConfig(
        name="500-3b-3p-n8-6cpu-12g",
        target_pi_s=500,
        load_test_load=load_test_load(500, worker_replicas=20),
        platform_helm_values=platform_values(3, 3, 6, "6000m", "6000m", "12Gi"),
        broker_replicas=3, partition_count=3,
        broker_node="n2-standard-8",
        broker_cpu_request="6000m", broker_cpu_limit="6000m",
        broker_memory="12Gi", broker_cpu_threads=6, broker_io_threads=3,
    ),
    # ── Stretch: push to 1000 PI/s once optimal topology is known ─────────────
    # BenchmarkConfig(
    #     name="1000-6b-6p-n8-6cpu-12g",
    #     target_pi_s=1000,
    #     load_test_load=load_test_load(1000, worker_replicas=30),
    #     platform_helm_values=platform_values(6, 6, 6, "6000m", "6000m", "12Gi"),
    #     broker_replicas=6, partition_count=6,
    #     broker_node="n2-standard-8",
    #     broker_cpu_request="6000m", broker_cpu_limit="6000m",
    #     broker_memory="12Gi", broker_cpu_threads=6, broker_io_threads=3,
    # ),
]


# ── Entry point ───────────────────────────────────────────────────────────────

def main() -> None:
    if not GITHUB_TOKEN:
        sys.exit("ERROR: GITHUB_TOKEN environment variable is required")
    if not GRAFANA_TOKEN:
        print("WARNING: GRAFANA_TOKEN not set — runs will execute but metrics won't be fetched")

    total = len(TEST_MATRIX)
    print(f"Benchmark sweep: {total} config(s) → {OUTPUT_CSV}")
    print(f"Estimated time: ~{total * 45} minutes\n")

    for i, config in enumerate(TEST_MATRIX, 1):
        print(f"\n[{i}/{total}] Starting config: {config.name}")
        result = run_benchmark(config)
        append_csv(result)

        if i < total:
            print("\n  Waiting 2min before next run to avoid namespace conflicts…")
            time.sleep(120)

    print(f"\n✓ Done. Results written to {OUTPUT_CSV}")


if __name__ == "__main__":
    main()
