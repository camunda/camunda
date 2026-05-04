#!/usr/bin/env python3
"""
Run Optimize benchmarks and optionally auto-scale to find minimum sizing for each load.

Wraps trigger-optimize-load-tests.py — all trigger flags are forwarded as-is.

Monitoring flags:
  --collect-after  minutes of continuous pod health before collecting (default: 5)
  --timeout        total minutes to wait for healthy state (default: 40)
  --grafana-url    Grafana base URL (default: https://dashboard.benchmark.camunda.cloud)
  GRAFANA_TOKEN    env var — Grafana API token
  GRAFANA_COOKIE   env var — OAuth-proxy cookie (e.g. VouchCookie=...). Takes precedence.
  --output         CSV file to append results to (default: benchmark-results.csv)

Auto-scaling mode (--auto-scale):
  Runs rates 50 → 100 → 200 → 400 → 800 PI/s (doubling each time, up to --max-rate).
  For each rate:
    - If achieved ≥ 90% of target → success, move to next rate (resources reset to defaults)
    - If achieved < 90% → scale broker + ES by 50% (CPU/memory/node pool) and retry
    - Stops scaling when both broker and ES hit n2-standard-16 limits

Usage:
    export GRAFANA_COOKIE="VouchCookie=..."
    python3 run-benchmark.py --auto-scale [--max-rate 800] [--ref main] [--dry-run]
    python3 run-benchmark.py --auto-scale --optimize          # enable Optimize
    python3 run-benchmark.py --rates 500 --broker-node-pool n2-standard-8
    python3 run-benchmark.py --collect-only c8-ajanoni-05011234-opt-typi-3b-50pis
"""

import argparse
import csv
import json
import math
import os
import subprocess
import sys
import time
import urllib.request
from dataclasses import dataclass, replace
from datetime import datetime, timezone
from pathlib import Path


class _Tee:
    """Write to both the original stream and a log file simultaneously."""
    def __init__(self, stream, log_path: Path):
        self._stream = stream
        self._log = open(log_path, "a", encoding="utf-8", buffering=1)

    def write(self, data):
        self._stream.write(data)
        self._log.write(data)

    def flush(self):
        self._stream.flush()
        self._log.flush()

    def fileno(self):
        return self._stream.fileno()

    def close(self):
        self._log.close()


def _backup(path: Path, ts: str) -> None:
    if path.exists():
        dest = path.with_suffix(f".{ts}{path.suffix}")
        import shutil
        shutil.copy2(path, dest)
        print(f"[backup] {path.name} → {dest.name}")


def _setup_log(log_path: Path, csv_path: Path) -> None:
    ts = datetime.now().strftime("%Y%m%d_%H%M%S")
    _backup(csv_path, ts)
    _backup(log_path, ts)
    log_path.parent.mkdir(parents=True, exist_ok=True)
    sys.stdout = _Tee(sys.stdout, log_path)
    sys.stderr = _Tee(sys.stderr, log_path)
    print(f"[log] {log_path}")

SCRIPT_DIR = Path(__file__).parent
TRIGGER_SCRIPT = SCRIPT_DIR / "trigger-optimize-load-tests.py"

GRAFANA_URL_DEFAULT = "https://dashboard.benchmark.camunda.cloud"
CSV_DEFAULT = SCRIPT_DIR / "benchmark-results.csv"
CAPACITY_PLAN_DEFAULT = SCRIPT_DIR / "capacity-plan.csv"

CSV_COLUMNS = [
    "Run",
    "Timestamp",
    "Status",
    "Target PI/s",
    "Activated PI/s",
    "Activated PI %",
    "Completed PI/s",
    "Backpressure drop %",
    "ES CPU throttle %",
    "Camunda CPU throttle %",
    "Broker replicas",
    "Broker node",
    "Broker CPU",
    "Broker memory",
    "Broker heap",
    "Broker threads CPU",
    "Broker threads IO",
    "ES replicas",
    "ES node",
    "ES CPU",
    "ES memory",
    "ES heap",
    "Dropped req/s",
    "Max in-flight req",
    "ES export lag",
    "ES flush p99 (s)",
    "ES flush fail rate",
    "ES disk used %",
    "grafana_timestamp",
]

# ── Node pool specs ────────────────────────────────────────────────────────────
# max_cpu_m: safe upper bound for CPU requests on this pool (GKE allocatable minus DaemonSet overhead)
# max_mem_gi: safe upper bound for memory requests

BROKER_POOL_SPECS = {
    # default_cpu_m / default_mem_gi: what the trigger script sets for this pool (kept in sync)
    # max_cpu_m / max_mem_gi: safe upper bound on this node type for scaling decisions
    "n2-standard-4":  {"default_cpu_m": 3000,  "default_mem_gi":  2.0,
                        "max_cpu_m": 3800,  "max_mem_gi": 14.0, "cpu_threads": 3,  "io_threads": 3},
    "n2-standard-8":  {"default_cpu_m": 6000,  "default_mem_gi":  8.0,
                        "max_cpu_m": 7500,  "max_mem_gi": 30.0, "cpu_threads": 6,  "io_threads": 6},
    "n2-standard-16": {"default_cpu_m": 12000, "default_mem_gi": 16.0,
                        "max_cpu_m": 15000, "max_mem_gi": 60.0, "cpu_threads": 12, "io_threads": 12},
}
BROKER_POOLS_ORDERED = ["n2-standard-4", "n2-standard-8", "n2-standard-16"]

ES_POOL_SPECS = {
    "n2-standard-8":  {"default_cpu_m": 7000,  "default_mem_gi":  8.0, "max_cpu_m": 7500,  "max_mem_gi": 30.0},
    "n2-standard-16": {"default_cpu_m": 14000, "default_mem_gi": 16.0, "max_cpu_m": 15000, "max_mem_gi": 60.0},
}
ES_POOLS_ORDERED = ["n2-standard-8", "n2-standard-16"]

# ── Resource state ─────────────────────────────────────────────────────────────

@dataclass
class ResourceState:
    broker_pool: str
    broker_cpu_m: int
    broker_mem_gi: float
    broker_replicas: int
    es_node: str
    es_cpu_m: int
    es_mem_gi: float
    es_replicas: int


DEFAULT_RESOURCES = ResourceState(
    broker_pool="n2-standard-4",
    broker_cpu_m=BROKER_POOL_SPECS["n2-standard-4"]["default_cpu_m"],
    broker_mem_gi=BROKER_POOL_SPECS["n2-standard-4"]["default_mem_gi"],
    broker_replicas=3,
    es_node="n2-standard-8",
    es_cpu_m=7000,
    es_mem_gi=8.0,
    es_replicas=3,
)


def default_state_for_pool(pool: str, brokers: int) -> ResourceState:
    spec = BROKER_POOL_SPECS[pool]
    return replace(
        DEFAULT_RESOURCES,
        broker_pool=pool,
        broker_cpu_m=spec["default_cpu_m"],
        broker_mem_gi=spec["default_mem_gi"],
        broker_replicas=brokers,
    )

SCALE_FACTOR = 1.5
SUCCESS_THRESHOLD = 0.90
AUTO_SCALE_RATES_START = 50
MAX_AUTO_RATE_DEFAULT = 800


def _select_pool(cpu_m: int, pools_ordered: list, pool_specs: dict) -> str:
    for name in pools_ordered:
        if cpu_m <= pool_specs[name]["max_cpu_m"]:
            return name
    return pools_ordered[-1]


def scale_up(state: ResourceState) -> ResourceState:
    new_broker_cpu = int(state.broker_cpu_m * SCALE_FACTOR)
    new_broker_mem = state.broker_mem_gi * SCALE_FACTOR
    new_es_cpu = int(state.es_cpu_m * SCALE_FACTOR)
    new_es_mem = state.es_mem_gi * SCALE_FACTOR

    new_broker_pool = _select_pool(new_broker_cpu, BROKER_POOLS_ORDERED, BROKER_POOL_SPECS)
    new_es_node = _select_pool(new_es_cpu, ES_POOLS_ORDERED, ES_POOL_SPECS)

    # If we moved to a larger node pool, snap up to that pool's default — no point paying
    # for a bigger node but using less than its default allocation.
    bp = BROKER_POOL_SPECS[new_broker_pool]
    new_broker_cpu = min(max(new_broker_cpu, bp["default_cpu_m"]), bp["max_cpu_m"])
    new_broker_mem = min(max(new_broker_mem, bp["default_mem_gi"]), bp["max_mem_gi"])

    ep = ES_POOL_SPECS[new_es_node]
    new_es_cpu = min(max(new_es_cpu, ep["default_cpu_m"]), ep["max_cpu_m"])
    new_es_mem = min(max(new_es_mem, ep["default_mem_gi"]), ep["max_mem_gi"])

    return replace(
        state,
        broker_pool=new_broker_pool,
        broker_cpu_m=new_broker_cpu,
        broker_mem_gi=new_broker_mem,
        es_node=new_es_node,
        es_cpu_m=new_es_cpu,
        es_mem_gi=new_es_mem,
    )


def is_at_max(state: ResourceState) -> bool:
    bp = BROKER_POOL_SPECS[BROKER_POOLS_ORDERED[-1]]
    ep = ES_POOL_SPECS[ES_POOLS_ORDERED[-1]]
    return state.broker_cpu_m >= bp["max_cpu_m"] and state.es_cpu_m >= ep["max_cpu_m"]


# ── Broker info helpers ────────────────────────────────────────────────────────

def _broker_heap(mem_gi: float) -> str:
    heap_mib = int(mem_gi * 1024 * 0.25)
    return f"{heap_mib}Mi"


def _es_heap(mem_gi: float) -> str:
    heap_gi = min(mem_gi * 0.5, 31.0)
    return f"{math.ceil(heap_gi)}Gi"


def build_broker_info(state: ResourceState) -> dict:
    pool = BROKER_POOL_SPECS[state.broker_pool]
    mem_gi_ceil = math.ceil(state.broker_mem_gi)
    es_mem_gi_ceil = math.ceil(state.es_mem_gi)
    return {
        "Broker replicas": state.broker_replicas,
        "Broker node": state.broker_pool,
        "Broker CPU": f"{state.broker_cpu_m}m",
        "Broker memory": f"{mem_gi_ceil}Gi",
        "Broker heap": _broker_heap(state.broker_mem_gi),
        "Broker threads CPU": pool["cpu_threads"],
        "Broker threads IO": pool["io_threads"],
        "ES replicas": state.es_replicas,
        "ES node": state.es_node,
        "ES CPU": f"{state.es_cpu_m}m",
        "ES memory": f"{es_mem_gi_ceil}Gi",
        "ES heap": _es_heap(state.es_mem_gi),
    }


# ── Extra helm overrides for scaled resources ──────────────────────────────────

def build_resource_overrides(state: ResourceState) -> str:
    """Build --set flags that override broker CPU/memory/threads and ES resources."""
    pool = BROKER_POOL_SPECS[state.broker_pool]
    cpu_str = f"{state.broker_cpu_m}m"
    mem_str = f"{math.ceil(state.broker_mem_gi)}Gi"
    es_cpu_str = f"{state.es_cpu_m}m"
    es_mem_str = f"{math.ceil(state.es_mem_gi)}Gi"

    flags = [
        # Broker resource overrides (the trigger script sets pool defaults; these win via last-set)
        f"--set orchestration.resources.requests.cpu={cpu_str}",
        f"--set orchestration.resources.limits.cpu={cpu_str}",
        f"--set orchestration.resources.requests.memory={mem_str}",
        f"--set orchestration.resources.limits.memory={mem_str}",
        f"--set-string orchestration.cpuThreadCount={pool['cpu_threads']}",
        f"--set-string orchestration.ioThreadCount={pool['io_threads']}",
        # ES resource overrides
        f"--set elasticsearch.master.resources.requests.cpu={es_cpu_str}",
        f"--set elasticsearch.master.resources.limits.cpu={es_cpu_str}",
        f"--set elasticsearch.master.resources.requests.memory={es_mem_str}",
        f"--set elasticsearch.master.resources.limits.memory={es_mem_str}",
    ]

    # Move ES to n2-standard-16 if needed
    if state.es_node == "n2-standard-16":
        flags += [
            f"--set elasticsearch.master.nodeSelector.component=benchmark-{state.es_node}",
            f"--set elasticsearch.master.tolerations[0].key=nodepool",
            f"--set elasticsearch.master.tolerations[0].operator=Equal",
            f"--set elasticsearch.master.tolerations[0].value={state.es_node}",
            f"--set elasticsearch.master.tolerations[0].effect=NoSchedule",
        ]

    return " ".join(flags)


# ── Grafana helpers ────────────────────────────────────────────────────────────

def _grafana_get(grafana_url: str, token: str, path: str, cookie: str = "") -> dict:
    url = grafana_url.rstrip("/") + path
    headers = {"Accept": "application/json"}
    if cookie:
        headers["Cookie"] = cookie
    else:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            body = resp.read()
    except urllib.error.HTTPError as e:
        raise RuntimeError(f"Grafana HTTP {e.code} for {path}: {e.read()[:200]}") from e
    if not body:
        raise RuntimeError(f"Grafana returned empty response for {path}")
    try:
        return json.loads(body)
    except json.JSONDecodeError as e:
        if body.lstrip().startswith(b"<"):
            raise RuntimeError(
                f"Grafana returned an HTML page for {path} — "
                "proxy auth failed. Set the GRAFANA_COOKIE env var with the "
                "VouchCookie value from your browser session."
            ) from e
        raise RuntimeError(f"Non-JSON Grafana response ({path}): {body[:200]}") from e


def _find_prometheus_uid(grafana_url: str, token: str, cookie: str) -> str:
    datasources = _grafana_get(grafana_url, token, "/api/datasources", cookie)
    for ds in datasources:
        if ds.get("type") == "prometheus":
            return ds["uid"]
    raise RuntimeError("No Prometheus datasource found in Grafana")


def _promql(grafana_url: str, token: str, cookie: str, ds_uid: str, query: str, at_time: float = 0.0):
    path = f"/api/datasources/proxy/uid/{ds_uid}/api/v1/query"
    t = at_time if at_time else time.time()
    qs = f"?query={urllib.request.quote(query)}&time={t:.0f}"
    try:
        data = _grafana_get(grafana_url, token, path + qs, cookie)
        results = data.get("data", {}).get("result", [])
        if not results:
            return None
        return float(results[0]["value"][1])
    except Exception as e:
        print(f"  WARNING: PromQL query failed ({e}): {query[:80]}", file=sys.stderr)
        return None


def collect_metrics(grafana_url: str, token: str, cookie: str, namespace: str,
                    target_rate: int, broker_info: dict, at_time: float = 0.0) -> dict:
    collect_time = at_time if at_time else time.time()
    grafana_ts = datetime.utcfromtimestamp(collect_time).strftime("%Y-%m-%dT%H:%M:%SZ")
    print(f"\nQuerying Grafana for namespace={namespace} (t={collect_time:.0f}) ...")
    ds_uid = _find_prometheus_uid(grafana_url, token, cookie)

    ns = namespace
    achieved = _promql(grafana_url, token, cookie, ds_uid,
        f'sum(rate(zeebe_executed_instances_total{{namespace="{ns}",action="activated"}}[5m]))',
        collect_time)
    dropped = _promql(grafana_url, token, cookie, ds_uid,
        f'sum(rate(zeebe_dropped_request_count_total{{namespace="{ns}"}}[5m]))', collect_time)
    inflight = _promql(grafana_url, token, cookie, ds_uid,
        f'max(zeebe_backpressure_inflight_requests_count{{namespace="{ns}"}})', collect_time)

    export_lag = _promql(grafana_url, token, cookie, ds_uid,
        f'sum('
        f'max by(partition) (zeebe_log_appender_last_committed_position{{namespace="{ns}"}}) -'
        f' max by(partition) (zeebe_exporter_last_exported_position{{namespace="{ns}",exporter="elasticsearch"}})'
        f')',
        collect_time)
    es_flush_p99 = _promql(grafana_url, token, cookie, ds_uid,
        f'histogram_quantile(0.99, sum(rate('
        f'zeebe_elasticsearch_exporter_flush_duration_seconds_bucket{{namespace="{ns}"}}[5m])) by (le))',
        collect_time)
    es_flush_fail = _promql(grafana_url, token, cookie, ds_uid,
        f'sum(rate(zeebe_elasticsearch_exporter_failed_flush_total{{namespace="{ns}"}}[5m]))'
        f' / sum(rate(zeebe_elasticsearch_exporter_flush_duration_seconds_count{{namespace="{ns}"}}[5m]))',
        collect_time)
    es_disk_pct = _promql(grafana_url, token, cookie, ds_uid,
        f'avg(kubelet_volume_stats_used_bytes{{namespace="{ns}",persistentvolumeclaim=~".*elastic.*"}}'
        f' / kubelet_volume_stats_capacity_bytes{{namespace="{ns}",persistentvolumeclaim=~".*elastic.*"}} * 100)',
        collect_time)
    bp_drop_pct = _promql(grafana_url, token, cookie, ds_uid,
        f'avg('
        f'(sum by(partition) (rate(zeebe_dropped_request_count_total{{namespace="{ns}"}}[5m]))'
        f' / sum by(partition) (rate(zeebe_received_request_count_total{{namespace="{ns}"}}[5m])))'
        f' * 100)',
        collect_time)
    es_cpu_throttle = _promql(grafana_url, token, cookie, ds_uid,
        f'avg('
        f'rate(container_cpu_cfs_throttled_periods_total{{namespace="{ns}",pod=~".*elastic.*"}}[5m])'
        f' / rate(container_cpu_cfs_periods_total{{namespace="{ns}",pod=~".*elastic.*"}}[5m])'
        f') * 100',
        collect_time)
    camunda_cpu_throttle = _promql(grafana_url, token, cookie, ds_uid,
        f'avg('
        f'rate(container_cpu_cfs_throttled_periods_total{{namespace="{ns}",pod=~".*(orchestration|zeebe|camunda).*"}}[5m])'
        f' / rate(container_cpu_cfs_periods_total{{namespace="{ns}",pod=~".*(orchestration|zeebe|camunda).*"}}[5m])'
        f') * 100',
        collect_time)
    completed_pis = _promql(grafana_url, token, cookie, ds_uid,
        f'sum(rate(zeebe_element_instance_events_total{{namespace="{ns}",action="completed",type="PROCESS"}}[5m]))',
        collect_time)

    achieved_pis = round(achieved, 1) if achieved is not None else "N/A"
    achieved_pct = (
        f"{round(achieved / target_rate * 100, 1)}%"
        if achieved is not None and target_rate > 0
        else "N/A"
    )
    dropped_rps = round(dropped, 3) if dropped is not None else "N/A"
    max_inflight = round(inflight, 0) if inflight is not None else "N/A"
    es_lag_val = int(round(export_lag)) if export_lag is not None else "N/A"
    es_p99_val = round(es_flush_p99, 3) if es_flush_p99 is not None else "N/A"
    es_fail_val = round(es_flush_fail, 4) if es_flush_fail is not None else "N/A"
    es_disk_val = round(es_disk_pct, 1) if es_disk_pct is not None else "N/A"
    bp_drop_val = round(bp_drop_pct, 2) if bp_drop_pct is not None else "N/A"
    es_throttle_val = round(es_cpu_throttle, 1) if es_cpu_throttle is not None else "N/A"
    camunda_throttle_val = round(camunda_cpu_throttle, 1) if camunda_cpu_throttle is not None else "N/A"
    completed_val = round(completed_pis, 1) if completed_pis is not None else "N/A"

    print(f"  Activated PI/s:   {achieved_pis}")
    print(f"  Completed PI/s:   {completed_val}")
    print(f"  Dropped req/s:    {dropped_rps}")
    print(f"  Backpressure drop:{bp_drop_val}%")
    print(f"  Max in-flight:    {max_inflight}")
    print(f"  ES export lag:    {es_lag_val}")
    print(f"  ES flush p99:     {es_p99_val}s")
    print(f"  ES flush fail:    {es_fail_val}")
    print(f"  ES disk used:     {es_disk_val}%")
    print(f"  ES CPU throttle:  {es_throttle_val}%")
    print(f"  Camunda throttle: {camunda_throttle_val}%")

    return {
        "Activated PI/s": achieved_pis,
        "Activated PI %": achieved_pct,
        "Dropped req/s": dropped_rps,
        "Max in-flight req": max_inflight,
        "ES export lag": es_lag_val,
        "ES flush p99 (s)": es_p99_val,
        "ES flush fail rate": es_fail_val,
        "ES disk used %": es_disk_val,
        "Backpressure drop %": bp_drop_val,
        "ES CPU throttle %": es_throttle_val,
        "Camunda CPU throttle %": camunda_throttle_val,
        "Completed PI/s": completed_val,
        "grafana_timestamp": grafana_ts,
        **broker_info,
    }


# ── kubectl helpers ────────────────────────────────────────────────────────────

def _pods_all_healthy(namespace: str):
    """Return (all_healthy, summary). Ignores Completed pods."""
    try:
        out = subprocess.check_output(
            ["kubectl", "get", "pods", "-n", namespace, "--no-headers"],
            text=True,
            stderr=subprocess.PIPE,
        )
    except subprocess.CalledProcessError as e:
        stderr = e.stderr.strip()
        if "tsh" in stderr or "exec: executable" in stderr or "getting credentials" in stderr:
            return False, "kubectl auth error — run: tsh kube login"
        last_line = next((l.strip() for l in reversed(stderr.splitlines()) if l.strip()), stderr)
        return False, f"kubectl error: {last_line}"

    lines = [l for l in out.strip().splitlines() if l]
    if not lines:
        return False, "no pods found"

    unhealthy = []
    for line in lines:
        parts = line.split()
        if len(parts) < 3:
            unhealthy.append(line)
            continue
        name, ready, status = parts[0], parts[1], parts[2]
        if status == "Completed":
            continue
        if status != "Running":
            unhealthy.append(f"{name}:{status}")
            continue
        if "/" in ready:
            cur, total = ready.split("/")
            if cur != total:
                unhealthy.append(f"{name}:ready={ready}")

    if unhealthy:
        return False, f"unhealthy: {', '.join(unhealthy)}"
    return True, f"{len(lines)} pods running"


def wait_for_healthy(namespace: str, collect_after_min: int, timeout_min: int) -> bool:
    poll_interval = 30
    collect_after_sec = collect_after_min * 60
    timeout_sec = timeout_min * 60

    healthy_since = None
    started = time.monotonic()

    print(f"\nWaiting for namespace={namespace} to be healthy "
          f"(need {collect_after_min}m continuous, timeout={timeout_min}m) ...")

    while True:
        elapsed = time.monotonic() - started
        if elapsed > timeout_sec:
            print(f"  TIMEOUT after {timeout_min}m — pods never reached stable healthy state")
            return False

        healthy, summary = _pods_all_healthy(namespace)
        now = time.monotonic()

        if healthy:
            if healthy_since is None:
                healthy_since = now
                print(f"  [{_fmt_elapsed(elapsed)}] All pods healthy ({summary}) — "
                      f"need {collect_after_min}m continuous ...")
            else:
                healthy_for = now - healthy_since
                remaining = collect_after_sec - healthy_for
                if healthy_for >= collect_after_sec:
                    print(f"  [{_fmt_elapsed(elapsed)}] Healthy for {collect_after_min}m — "
                          f"proceeding to metric collection")
                    return True
                print(f"  [{_fmt_elapsed(elapsed)}] Still healthy ({summary}) — "
                      f"{remaining:.0f}s until collection ...")
        else:
            if healthy_since is not None:
                print(f"  [{_fmt_elapsed(elapsed)}] Health reset ({summary}) — restarting timer")
            else:
                print(f"  [{_fmt_elapsed(elapsed)}] Not healthy yet ({summary})")
            healthy_since = None

        time.sleep(poll_interval)


def _fmt_elapsed(seconds: float) -> str:
    m, s = divmod(int(seconds), 60)
    return f"{m:02d}:{s:02d}"


# ── Namespace cleanup ─────────────────────────────────────────────────────────

def delete_namespace(namespace: str, expected_name: str) -> None:
    expected_namespace = f"c8-{expected_name}"
    if namespace != expected_namespace:
        print(f"  ABORT: refusing to delete '{namespace}' — expected '{expected_namespace}'",
              file=sys.stderr)
        return

    try:
        result = subprocess.run(
            ["kubectl", "get", "namespace", namespace, "--no-headers", "-o", "name"],
            capture_output=True, text=True,
        )
        found = result.stdout.strip()
    except Exception as e:
        print(f"  WARNING: could not verify namespace existence: {e}", file=sys.stderr)
        return

    if not found:
        print(f"\nNamespace {namespace} not found — nothing to delete.")
        return

    try:
        labels_out = subprocess.check_output(
            ["kubectl", "get", "namespace", namespace, "-o",
             "jsonpath={.metadata.labels.camunda\\.io/purpose}"],
            text=True, stderr=subprocess.PIPE,
        )
        purpose = labels_out.strip()
    except subprocess.CalledProcessError:
        purpose = ""

    if purpose and purpose != "load-test":
        print(f"  ABORT: namespace '{namespace}' has purpose='{purpose}', not 'load-test' — "
              f"refusing to delete", file=sys.stderr)
        return

    deletion_ts = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    print(f"\nDeleting namespace {namespace} (last active: {deletion_ts}) ...")
    try:
        subprocess.check_call(["kubectl", "delete", "namespace", namespace])
        print(f"  Deleted.")
    except subprocess.CalledProcessError as e:
        print(f"  WARNING: could not delete namespace: {e}", file=sys.stderr)


# ── CSV ────────────────────────────────────────────────────────────────────────

def write_csv(output: Path, row: dict) -> None:
    exists = output.exists()
    with open(output, "a", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS, extrasaction="ignore")
        if not exists:
            writer.writeheader()
        writer.writerow(row)
    print(f"  [{row.get('Status', '?')}] row written → {output.name}")


def update_csv_row(output: Path, run_name: str, updates: dict) -> None:
    if not output.exists():
        return
    with open(output, newline="") as f:
        rows = list(csv.DictReader(f))
    for row in rows:
        if row.get("Run") == run_name:
            row.update(updates)
            break
    with open(output, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    print(f"  [{updates.get('Status', '?')}] row updated → {output.name}")


def recover_grafana_errors(csv_path: Path, grafana_url: str, token: str, cookie: str) -> None:
    """Re-query Grafana for every row with Activated PI/s = GRAFANA_ERROR, using the row's timestamp."""
    if not csv_path.exists():
        print(f"ERROR: {csv_path} not found", file=sys.stderr)
        return

    with open(csv_path, newline="") as f:
        rows = list(csv.DictReader(f))

    errors = [r for r in rows if r.get("Activated PI/s") == "GRAFANA_ERROR"
              or r.get("Status") == "ERROR"]
    if not errors:
        print("No GRAFANA_ERROR rows found in CSV — nothing to recover.")
        return

    print(f"Found {len(errors)} GRAFANA_ERROR row(s) to recover.\n")

    try:
        ds_uid = _find_prometheus_uid(grafana_url, token, cookie)
    except Exception as e:
        print(f"ERROR: cannot connect to Grafana: {e}", file=sys.stderr)
        return

    recovered = 0
    for row in rows:
        if row.get("Activated PI/s") != "GRAFANA_ERROR":
            continue

        name = row["Run"]
        namespace = f"c8-{name}"
        try:
            rate = int(row["Target PI/s"])
        except (ValueError, KeyError):
            rate = 0

        # Use grafana_timestamp (the actual collection attempt time) if available,
        # fall back to the run start Timestamp.
        try:
            raw_ts = row.get("grafana_timestamp") or row.get("Timestamp", "")
            ts_str = raw_ts.rstrip("Z")
            at_time = datetime.fromisoformat(ts_str).replace(tzinfo=timezone.utc).timestamp()
        except Exception:
            at_time = time.time()

        print(f"Recovering {name}  (namespace={namespace}, t={at_time:.0f}) ...")

        ns = namespace
        achieved = _promql(grafana_url, token, cookie, ds_uid,
            f'sum(rate(zeebe_executed_instances_total{{namespace="{ns}",action="activated"}}[5m]))',
            at_time)
        dropped = _promql(grafana_url, token, cookie, ds_uid,
            f'sum(rate(zeebe_dropped_request_count_total{{namespace="{ns}"}}[5m]))', at_time)
        inflight = _promql(grafana_url, token, cookie, ds_uid,
            f'max(zeebe_backpressure_inflight_requests_count{{namespace="{ns}"}})', at_time)
        export_lag = _promql(grafana_url, token, cookie, ds_uid,
            f'sum('
            f'max by(partition) (zeebe_log_appender_last_committed_position{{namespace="{ns}"}}) -'
            f' max by(partition) (zeebe_exporter_last_exported_position{{namespace="{ns}",exporter="elasticsearch"}})'
            f')',
            at_time)
        es_flush_p99 = _promql(grafana_url, token, cookie, ds_uid,
            f'histogram_quantile(0.99, sum(rate('
            f'zeebe_elasticsearch_exporter_flush_duration_seconds_bucket{{namespace="{ns}"}}[5m])) by (le))',
            at_time)
        es_flush_fail = _promql(grafana_url, token, cookie, ds_uid,
            f'sum(rate(zeebe_elasticsearch_exporter_failed_flush_total{{namespace="{ns}"}}[5m]))'
            f' / sum(rate(zeebe_elasticsearch_exporter_flush_duration_seconds_count{{namespace="{ns}"}}[5m]))',
            at_time)
        es_disk_pct = _promql(grafana_url, token, cookie, ds_uid,
            f'avg(kubelet_volume_stats_used_bytes{{namespace="{ns}",persistentvolumeclaim=~".*elastic.*"}}'
            f' / kubelet_volume_stats_capacity_bytes{{namespace="{ns}",persistentvolumeclaim=~".*elastic.*"}} * 100)',
            at_time)
        bp_drop_pct = _promql(grafana_url, token, cookie, ds_uid,
            f'avg('
            f'(sum by(partition) (rate(zeebe_dropped_request_count_total{{namespace="{ns}"}}[5m]))'
            f' / sum by(partition) (rate(zeebe_received_request_count_total{{namespace="{ns}"}}[5m])))'
            f' * 100)',
            at_time)
        es_cpu_throttle = _promql(grafana_url, token, cookie, ds_uid,
            f'avg('
            f'rate(container_cpu_cfs_throttled_periods_total{{namespace="{ns}",pod=~".*elastic.*"}}[5m])'
            f' / rate(container_cpu_cfs_periods_total{{namespace="{ns}",pod=~".*elastic.*"}}[5m])'
            f') * 100',
            at_time)
        camunda_cpu_throttle = _promql(grafana_url, token, cookie, ds_uid,
            f'avg('
            f'rate(container_cpu_cfs_throttled_periods_total{{namespace="{ns}",pod=~".*(orchestration|zeebe|camunda).*"}}[5m])'
            f' / rate(container_cpu_cfs_periods_total{{namespace="{ns}",pod=~".*(orchestration|zeebe|camunda).*"}}[5m])'
            f') * 100',
            at_time)
        completed_pis = _promql(grafana_url, token, cookie, ds_uid,
            f'sum(rate(zeebe_element_instance_events_total{{namespace="{ns}",action="completed",type="PROCESS"}}[5m]))',
            at_time)

        if achieved is None and dropped is None and inflight is None:
            print(f"  Still no data — skipping (metrics may have expired from Prometheus)")
            continue

        achieved_pis = round(achieved, 1) if achieved is not None else "N/A"
        achieved_pct = (
            f"{round(achieved / rate * 100, 1)}%" if achieved is not None and rate > 0 else "N/A"
        )
        dropped_val = round(dropped, 3) if dropped is not None else "N/A"
        inflight_val = round(inflight, 0) if inflight is not None else "N/A"
        es_lag_val = int(round(export_lag)) if export_lag is not None else "N/A"
        es_p99_val = round(es_flush_p99, 3) if es_flush_p99 is not None else "N/A"
        es_fail_val = round(es_flush_fail, 4) if es_flush_fail is not None else "N/A"
        es_disk_val = round(es_disk_pct, 1) if es_disk_pct is not None else "N/A"
        bp_drop_val = round(bp_drop_pct, 2) if bp_drop_pct is not None else "N/A"
        es_throttle_val = round(es_cpu_throttle, 1) if es_cpu_throttle is not None else "N/A"
        camunda_throttle_val = round(camunda_cpu_throttle, 1) if camunda_cpu_throttle is not None else "N/A"
        completed_val = round(completed_pis, 1) if completed_pis is not None else "N/A"
        row["Status"] = "COMPLETED"
        row["Activated PI/s"] = achieved_pis
        row["Activated PI %"] = achieved_pct
        row["Dropped req/s"] = dropped_val
        row["Max in-flight req"] = inflight_val
        row["ES export lag"] = es_lag_val
        row["ES flush p99 (s)"] = es_p99_val
        row["ES flush fail rate"] = es_fail_val
        row["ES disk used %"] = es_disk_val
        row["Backpressure drop %"] = bp_drop_val
        row["ES CPU throttle %"] = es_throttle_val
        row["Camunda CPU throttle %"] = camunda_throttle_val
        row["Completed PI/s"] = completed_val
        print(f"  Activated PI/s={achieved_pis}  Completed PI/s={completed_val}  Dropped={dropped_val}  "
              f"BP drop={bp_drop_val}%  In-flight={inflight_val}  ES lag={es_lag_val}  "
              f"flush p99={es_p99_val}s  disk={es_disk_val}%  "
              f"ES throttle={es_throttle_val}%  Camunda throttle={camunda_throttle_val}%")
        recovered += 1

    # Rewrite the CSV in-place with updated rows
    with open(csv_path, "w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=CSV_COLUMNS, extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)

    print(f"\nRecovered {recovered}/{len(errors)} row(s) → Status=COMPLETED. CSV updated: {csv_path}")


# ── Time helpers ──────────────────────────────────────────────────────────────

def _utcnow() -> str:
    return datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")


def _parse_at_time(value: str) -> float:
    """Parse --at-time: Unix timestamp float or ISO 8601 UTC string. Returns 0.0 if not set."""
    if not value:
        return 0.0
    try:
        return float(value)
    except ValueError:
        pass
    ts_str = value.rstrip("Z")
    return datetime.fromisoformat(ts_str).replace(tzinfo=timezone.utc).timestamp()


# ── Capacity plan ─────────────────────────────────────────────────────────────

def _state_from_plan_row(row: dict) -> tuple:
    """Parse a capacity-plan.csv row into (rate, ResourceState)."""
    state = ResourceState(
        broker_pool=row["broker_node"].strip(),
        broker_cpu_m=int(row["broker_cpu_m"]),
        broker_mem_gi=float(row["broker_mem_gi"]),
        broker_replicas=int(row["brokers"]),
        es_node=row["es_node"].strip(),
        es_cpu_m=int(row["es_cpu_m"]),
        es_mem_gi=float(row["es_mem_gi"]),
        es_replicas=int(row.get("es_replicas", 3)),
    )
    return int(row["rates"]), state


# ── Trigger + parse ────────────────────────────────────────────────────────────

def run_trigger(trigger_args: list, dry_run: bool):
    cmd = [sys.executable, str(TRIGGER_SCRIPT)] + trigger_args
    print(f"Running: {' '.join(cmd)}\n")

    result = subprocess.run(cmd, capture_output=True, text=True)
    output = result.stdout
    print(output)
    if result.returncode != 0:
        print(result.stderr, file=sys.stderr)
        sys.exit(result.returncode)

    for line in output.splitlines():
        stripped = line.strip()
        if stripped.startswith("Name:"):
            name = stripped.split(":", 1)[1].strip()
            return name, output

    if dry_run:
        return None, output

    print("ERROR: could not parse 'Name:' from trigger output", file=sys.stderr)
    sys.exit(1)


# ── Single benchmark run ───────────────────────────────────────────────────────

def run_one(rate: int, state: ResourceState, args, grafana_token: str,
            grafana_cookie: str, extra_passthrough: list) -> dict:
    """Trigger, monitor, collect and record one benchmark run. Returns the row dict."""
    overrides = build_resource_overrides(state)
    trigger_args = [
        "--rates", str(rate),
        "--ttl", str(args.ttl),
        "--ref", args.ref,
        "--broker-node-pool", state.broker_pool,
        "--brokers", str(state.broker_replicas),
        "--extra-platform-values", overrides,
    ] + extra_passthrough
    if not args.optimize:
        trigger_args.append("--no-optimize")
    if args.dry_run:
        trigger_args.append("--dry-run")

    name, _ = run_trigger(trigger_args, args.dry_run)

    broker_info = build_broker_info(state)

    if args.dry_run:
        print(f"\n[DRY-RUN] Would monitor namespace=c8-<name> at {rate} PI/s.")
        return {"Activated PI/s": None}

    namespace = f"c8-{name}"
    print(f"\nNamespace: {namespace}")

    # Write initial row immediately so the run is recorded even if the script crashes later
    initial_row = {
        "Run": name,
        "Timestamp": datetime.utcnow().isoformat(timespec="seconds") + "Z",
        "Status": "STARTED",
        "Target PI/s": rate,
        "Activated PI/s": "",
        "Activated PI %": "",
        "Dropped req/s": "",
        "Max in-flight req": "",
        **broker_info,
    }
    write_csv(args.output, initial_row)

    healthy = wait_for_healthy(namespace, args.collect_after, args.timeout)

    if not healthy:
        update_csv_row(args.output, name, {"Status": "ERROR", "Activated PI/s": "TIMEOUT"})
        delete_namespace(namespace, name)
        return {**initial_row, "Status": "ERROR", "Activated PI/s": "TIMEOUT"}

    if not grafana_token and not grafana_cookie:
        print("WARNING: no Grafana credentials — skipping metric collection", file=sys.stderr)
        update_csv_row(args.output, name, {"Status": "COMPLETED"})
        return {**initial_row, "Status": "COMPLETED"}

    grafana_ts = datetime.utcnow().strftime("%Y-%m-%dT%H:%M:%SZ")
    try:
        metrics = collect_metrics(
            args.grafana_url, grafana_token, grafana_cookie, namespace, rate, broker_info
        )
        update_csv_row(args.output, name, {"Status": "COMPLETED", **metrics})
        row = {**initial_row, "Status": "COMPLETED", **metrics}
    except Exception as e:
        print(f"ERROR: Grafana collection failed: {e}", file=sys.stderr)
        print(f"  Re-collect with: --collect-only {namespace} --rates {rate}", file=sys.stderr)
        update_csv_row(args.output, name, {
            "Status": "ERROR", "Activated PI/s": "GRAFANA_ERROR", "grafana_timestamp": grafana_ts,
        })
        row = {**initial_row, "Status": "ERROR", "Activated PI/s": "GRAFANA_ERROR",
               "grafana_timestamp": grafana_ts}

    delete_namespace(namespace, name)
    return row


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    # Monitoring flags
    parser.add_argument("--collect-after", type=int, default=5, metavar="MIN",
                        help="Minutes of continuous pod health before collecting (default: 5)")
    parser.add_argument("--timeout", type=int, default=40, metavar="MIN",
                        help="Total minutes to wait for healthy pods (default: 40)")
    parser.add_argument("--grafana-url", default=GRAFANA_URL_DEFAULT)
    parser.add_argument("--output", type=Path, default=CSV_DEFAULT)
    parser.add_argument("--log", type=Path, default=None, metavar="FILE",
                        help="Append all console output to FILE in addition to stdout/stderr. "
                             "Defaults to <output-stem>.log next to the CSV.")
    parser.add_argument("--collect-only", metavar="NAMESPACE",
                        help="Skip deploy/monitor — collect Grafana metrics for an existing namespace")
    parser.add_argument("--at-time", metavar="TIME",
                        help="Query Grafana at this UTC time instead of now. "
                             "Accepts Unix timestamp (e.g. 1777650000) or ISO 8601 "
                             "(e.g. 2026-05-01T16:20:00Z). Required when the namespace "
                             "has already been deleted.")
    parser.add_argument("--capacity-plan", nargs="?", const=str(CAPACITY_PLAN_DEFAULT),
                        metavar="FILE",
                        help="Run every entry in a capacity-plan CSV "
                             f"(default: {CAPACITY_PLAN_DEFAULT.name}). "
                             "Columns: Status, rates, brokers, broker_node, broker_cpu_m, "
                             "broker_mem_gi, es_node, es_cpu_m, es_mem_gi, es_replicas.")
    parser.add_argument("--recover", action="store_true",
                        help="Re-query Grafana for all GRAFANA_ERROR rows in the CSV, "
                             "using each row's stored timestamp. Refresh GRAFANA_COOKIE first.")

    parser.add_argument("--optimize", action="store_true", default=False,
                        help="Enable Optimize in the load test (default: disabled)")

    # Auto-scale flags
    parser.add_argument("--auto-scale", action="store_true",
                        help="Adaptive loop: double rate on success (≥90%%), scale resources by 50%% on failure")
    parser.add_argument("--max-rate", type=int, default=MAX_AUTO_RATE_DEFAULT,
                        help=f"Maximum PI/s for auto-scale loop (default: {MAX_AUTO_RATE_DEFAULT})")
    parser.add_argument("--start-rate", type=int, default=AUTO_SCALE_RATES_START,
                        help="Resume auto-scale loop from this rate (default: 50). "
                             "Use to restart after a crash without re-running completed rates.")
    parser.add_argument("--scale-steps", type=int, default=0, metavar="N",
                        help="Pre-apply N scale-up steps to the initial resources at --start-rate. "
                             "Use when resuming after a crash that happened mid-scaling. "
                             "Example: --start-rate 200 --scale-steps 1 resumes at 200 PI/s "
                             "with resources already scaled up once (n2-standard-8/4500m).")

    # Trigger flags (also used to seed ResourceState defaults)
    parser.add_argument("--broker-node-pool", default="n2-standard-4",
                        choices=list(BROKER_POOL_SPECS.keys()))
    parser.add_argument("--brokers", type=int, default=3)
    parser.add_argument("--rates", default="50",
                        help="Comma-separated PI/s rates (ignored when --auto-scale is set)")
    parser.add_argument("--ref", default="main")
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--ttl", type=int, default=1)

    args, extra = parser.parse_known_args()

    log_path = args.log or args.output.with_suffix(".log")
    _setup_log(log_path, args.output)

    grafana_token = os.environ.get("GRAFANA_TOKEN", "")
    grafana_cookie = os.environ.get("GRAFANA_COOKIE", "")

    # ── collect-only mode ──────────────────────────────────────────────────────
    if args.collect_only:
        if not grafana_token and not grafana_cookie:
            print("ERROR: --collect-only requires Grafana credentials. "
                  "Set GRAFANA_TOKEN or GRAFANA_COOKIE env vars.",
                  file=sys.stderr)
            sys.exit(1)
        namespace = args.collect_only
        name = namespace.removeprefix("c8-")
        try:
            rate = int(name.rsplit("-", 1)[-1].rstrip("pis"))
        except ValueError:
            rate = int(args.rates.split(",")[0].strip())
        state = default_state_for_pool(args.broker_node_pool, args.brokers)
        broker_info = build_broker_info(state)
        at_time = _parse_at_time(args.at_time)
        metrics = collect_metrics(args.grafana_url, grafana_token, grafana_cookie, namespace, rate, broker_info,
                                  at_time=at_time)
        row = {
            "Run": name,
            "Timestamp": datetime.utcnow().isoformat(timespec="seconds") + "Z",
            "Target PI/s": rate,
            **metrics,
        }
        write_csv(args.output, row)
        return

    # ── capacity-plan mode ─────────────────────────────────────────────────────
    if args.capacity_plan:
        plan_path = Path(args.capacity_plan)
        if not plan_path.exists():
            print(f"ERROR: capacity plan not found: {plan_path}", file=sys.stderr)
            sys.exit(1)
        with open(plan_path, newline="") as f:
            plan_rows = list(csv.DictReader(f))
        if not plan_rows:
            print(f"No rows in {plan_path.name}")
            return
        print(f"Running {len(plan_rows)} entries from {plan_path.name}\n")
        for i, row in enumerate(plan_rows, 1):
            try:
                rate, state = _state_from_plan_row(row)
            except (KeyError, ValueError) as e:
                print(f"  Skipping row {i}: bad format ({e})", file=sys.stderr)
                continue
            status = row.get("Status", "")
            print(f"\n{'#'*60}")
            print(f"# CAPACITY PLAN [{i}/{len(plan_rows)}]: {rate} PI/s  status={status}  [{_utcnow()}]")
            print(f"{'#'*60}")
            run_one(rate, state, args, grafana_token, grafana_cookie, extra)
        print(f"\n{'#'*60}")
        print(f"# CAPACITY PLAN complete. Results in {args.output}")
        print(f"{'#'*60}")
        return

    # ── recover mode ───────────────────────────────────────────────────────────
    if args.recover:
        if not grafana_token and not grafana_cookie:
            print("ERROR: --recover requires Grafana credentials. "
                  "Set GRAFANA_TOKEN or GRAFANA_COOKIE env vars.", file=sys.stderr)
            sys.exit(1)
        recover_grafana_errors(args.output, args.grafana_url, grafana_token, grafana_cookie)
        return

    # ── auto-scale mode ────────────────────────────────────────────────────────
    if args.auto_scale:
        rate = args.start_rate
        pending_scale_steps = args.scale_steps
        while rate <= args.max_rate:
            state = DEFAULT_RESOURCES
            for _ in range(pending_scale_steps):
                state = scale_up(state)
            pending_scale_steps = 0  # only apply to the first (resume) rate
            attempt = 0
            print(f"\n{'#'*60}")
            print(f"# AUTO-SCALE: rate={rate} PI/s  [{_utcnow()}]")
            print(f"{'#'*60}")
            while True:
                attempt += 1
                print(f"\n  [attempt {attempt}] [{_utcnow()}] rate={rate}  broker={state.broker_pool} "
                      f"{state.broker_cpu_m}m/{math.ceil(state.broker_mem_gi)}Gi  "
                      f"es={state.es_node} {state.es_cpu_m}m/{math.ceil(state.es_mem_gi)}Gi")

                row = run_one(rate, state, args, grafana_token, grafana_cookie, extra)

                achieved = row.get("Activated PI/s")
                if args.dry_run:
                    break

                if achieved in (None, "N/A", "TIMEOUT"):
                    # Can't measure — treat as failure and scale up
                    if is_at_max(state):
                        print(f"\n  SATURATED at rate={rate}: resources maxed, achieved={achieved}")
                        break
                    state = scale_up(state)
                    continue

                try:
                    achieved_f = float(achieved)
                except (ValueError, TypeError):
                    achieved_f = 0.0

                if achieved_f >= rate * SUCCESS_THRESHOLD:
                    print(f"\n  SUCCESS at rate={rate}: achieved={achieved_f:.1f} "
                          f"({achieved_f/rate*100:.0f}%) ✓")
                    break
                else:
                    print(f"\n  UNDERPERFORMING at rate={rate}: achieved={achieved_f:.1f} "
                          f"({achieved_f/rate*100:.0f}%) — scaling up resources")
                    if is_at_max(state):
                        print(f"  SATURATED: already at max resources for rate={rate}")
                        break
                    state = scale_up(state)

            rate *= 2

        print(f"\n{'#'*60}")
        print(f"# AUTO-SCALE complete. Results in {args.output}")
        print(f"{'#'*60}")
        return

    # ── manual (multi-rate) mode ───────────────────────────────────────────────
    rates = [int(r.strip()) for r in args.rates.split(",")]
    state = default_state_for_pool(args.broker_node_pool, args.brokers)

    for rate in rates:
        run_one(rate, state, args, grafana_token, grafana_cookie, extra)

    if args.dry_run:
        print("\n[DRY-RUN complete — nothing deployed or monitored]")


if __name__ == "__main__":
    main()