#!/usr/bin/env python3
"""
Trigger Optimize load tests at multiple PI/s rates with ES resources scaled linearly.

Baseline (V2, Optimize enabled):
  - ES: 7.5 vCPU total, 6 GiB RAM total, 512 GiB disk (3 nodes) at ~51 PI/s throughput
  - Disk fill rate: ~10.7 GiB/h per PI/s

Usage:
    export GITHUB_TOKEN=ghp_...   # required — no CLI arg accepted
    python3 trigger-optimize-load-tests.py [--dry-run] [--ttl DAYS] [--ref BRANCH] [--rates 100,250,500,1000]
    python3 trigger-optimize-load-tests.py --scenario realistic --rates 500,1000
    python3 trigger-optimize-load-tests.py --broker-node-pool n2-standard-8 --rates 500
"""

import argparse
import json
import math
import os
import re
import subprocess
import sys
import urllib.request
from datetime import datetime
from dataclasses import dataclass

# ── Scaling constants derived from V2 experiments ────────────────────────────

# ES baseline at 51 PI/s throughput (without Optimize): 3 vCPU, 6 GiB RAM per cluster
ES_BASELINE_CPU_VCPU = 3.0       # total vCPU without Optimize at 51 PI/s
ES_BASELINE_MEM_GI   = 6.0       # total GiB RAM without Optimize at 51 PI/s
ES_BASELINE_RATE     = 51.0      # PI/s at baseline
OPTIMIZE_CPU_MULT    = 2.5       # Optimize adds ~2.5x CPU overhead on ES
OPTIMIZE_MEM_MULT    = 1.2       # Optimize adds ~20% memory overhead
DISK_FILL_RATE_GI_PER_H_PER_PIS = 10.7  # GiB/h per PI/s

ES_REPLICAS       = 3            # fixed node count — scale vertically, not horizontally
ES_SIZING_RATE    = 100          # PI/s used to size ES — resources are fixed across all rates
MAX_DISK_PER_NODE = 500          # GiB cap — disk doesn't scale linearly with rate

# Each worker pod activates up to 30 jobs and completes them after 300ms (default config).
# Throughput per pod = 30 / 0.3s = 100 jobs/sec. Scale replicas to match the starter rate.
WORKER_JOBS_PER_SEC_PER_POD = 100

# Named workers in the realistic benchmark (values-realistic-benchmark.yaml).
# All are scaled uniformly using WORKER_JOBS_PER_SEC_PER_POD as the conservative baseline
# (slowest workers: capacity=30, delay=300ms → 100 jobs/sec).
REALISTIC_WORKER_NAMES = [
    "customer-notification",
    "extract-data-from-document",
    "dispute-process-request-proof-from-vendor",
    "dispute-process-request-get-vendor-info",
    "refunding",
    "inform-about-successful-claim",
]

# ── Broker node pool presets ──────────────────────────────────────────────────
#
# Each preset matches a GKE node pool in the benchmark cluster and sets broker
# resources + scheduling (nodeSelector + toleration) + Zeebe thread counts
# consistently. Increasing CPU/memory without moving to the right node pool
# causes pods to go Pending (the request won't fit on the smaller node).
#
# camunda-platform-values.yaml defaults:  n2-standard-4, 3000m, 2Gi, 3/3 threads
# n2-standard-8 is the same pool ES uses: 7000m CPU, 8Gi RAM already in cluster.

@dataclass
class BrokerNodePool:
    name: str          # GKE node pool label value (benchmark-<name>)
    cpu: str           # CPU request/limit
    memory: str        # Memory request/limit
    cpu_threads: int   # zeebe cpuThreadCount
    io_threads: int    # zeebe ioThreadCount


BROKER_NODE_POOLS = {
    "n2-standard-4": BrokerNodePool(
        name="n2-standard-4",
        cpu="3000m",
        memory="2Gi",
        cpu_threads=3,
        io_threads=3,
    ),
    "n2-standard-8": BrokerNodePool(
        name="n2-standard-8",
        cpu="6000m",
        memory="8Gi",
        cpu_threads=6,
        io_threads=6,
    ),
    "n2-standard-16": BrokerNodePool(
        name="n2-standard-16",
        cpu="12000m",
        memory="16Gi",
        cpu_threads=12,
        io_threads=12,
    ),
}

# ── Workflow parameters ───────────────────────────────────────────────────────

REPO     = "camunda/camunda"
WORKFLOW = "camunda-load-test.yml"
REF_DEFAULT = "main"
TTL_DEFAULT = 3

REALISTIC_VALUES_URL = (
    "https://raw.githubusercontent.com/camunda/camunda-load-tests-helm"
    "/refs/heads/main/charts/camunda-load-tests/values-realistic-benchmark.yaml"
)

TYPICAL_BPMN_PATH = "bpmn/typical_process.bpmn"


# ── Data classes ─────────────────────────────────────────────────────────────

@dataclass
class ESConfig:
    replicas: int
    cpu_vcpu: float       # per node
    mem_gi: float         # per node
    heap_mi: int          # per node (MB)
    disk_gi: int          # per node


@dataclass
class TestConfig:
    rate: int
    name: str
    es: ESConfig


# ── Scaling logic ─────────────────────────────────────────────────────────────

def compute_es_config(rate: int, ttl_days: int) -> ESConfig:
    total_cpu = (ES_BASELINE_CPU_VCPU / ES_BASELINE_RATE) * rate * OPTIMIZE_CPU_MULT
    total_mem = (ES_BASELINE_MEM_GI  / ES_BASELINE_RATE) * rate * OPTIMIZE_MEM_MULT

    replicas = ES_REPLICAS

    cpu_per_node = total_cpu / replicas
    mem_per_node = total_mem / replicas

    # heap = 50% of node memory, capped at 31 GiB (ES recommendation)
    heap_gi = min(mem_per_node * 0.5, 31.0)
    heap_mi = int(heap_gi * 1024)

    disk_fill_per_node_gi = DISK_FILL_RATE_GI_PER_H_PER_PIS * rate * (ttl_days * 24) / replicas
    # Add 30% buffer; cap at MAX_DISK_PER_NODE — disk doesn't scale linearly at high rates
    disk_gi = min(MAX_DISK_PER_NODE, max(50, math.ceil(disk_fill_per_node_gi * 1.3)))

    return ESConfig(
        replicas=replicas,
        cpu_vcpu=round(cpu_per_node, 2),
        mem_gi=round(mem_per_node, 2),
        heap_mi=heap_mi,
        disk_gi=disk_gi,
    )


def build_test_config(rate: int, ttl_days: int, scenario: str, with_optimize: bool, brokers: int) -> TestConfig:
    es = compute_es_config(ES_SIZING_RATE, ttl_days)
    ts = datetime.now().strftime("%m%d%H%M")
    opt_tag = "opt" if with_optimize else "noopt"
    name = f"ajanoni-{ts}-{opt_tag}-{scenario[:4]}-{brokers}b-{rate}pis"
    return TestConfig(rate=rate, name=name, es=es)


# ── Helm value builders ────────────────────────────────────────────────────────

def _mcores(vcpu: float) -> str:
    return f"{int(round(vcpu * 1000))}m"


def _gi(value: float) -> str:
    return f"{math.ceil(value)}Gi"


def build_platform_helm_values(es: ESConfig, broker: BrokerNodePool, brokers: int,
                               partitions: int = 0) -> str:
    replication = min(3, brokers)
    partition_count = partitions if partitions > 0 else brokers
    es_prefix = "elasticsearch.master"
    flags = [
        # ES — only override disk size; CPU/memory/heap use cluster defaults (7000m/8Gi/3Gi)
        f"--set {es_prefix}.replicas={es.replicas}",
        f"--set {es_prefix}.persistence.size={es.disk_gi}Gi",
        # Broker topology — chart schema requires strings for these numeric fields
        f"--set-string orchestration.clusterSize={brokers}",
        f"--set-string orchestration.partitionCount={partition_count}",
        f"--set-string orchestration.replicationFactor={replication}",
        # Broker resources
        f"--set orchestration.resources.requests.cpu={broker.cpu}",
        f"--set orchestration.resources.limits.cpu={broker.cpu}",
        f"--set orchestration.resources.requests.memory={broker.memory}",
        f"--set orchestration.resources.limits.memory={broker.memory}",
        f"--set-string orchestration.cpuThreadCount={broker.cpu_threads}",
        f"--set-string orchestration.ioThreadCount={broker.io_threads}",
        # Broker scheduling — nodeSelector + toleration must match the node pool
        f"--set orchestration.nodeSelector.component=benchmark-{broker.name}",
        f"--set orchestration.tolerations[0].key=nodepool",
        f"--set orchestration.tolerations[0].operator=Equal",
        f"--set orchestration.tolerations[0].value={broker.name}",
        f"--set orchestration.tolerations[0].effect=NoSchedule",
    ]
    return " ".join(flags)


def build_load_test_load(rate: int, scenario: str) -> str:
    # Load test pods run on n2-standard-4 (4 vCPU / 16 GiB) per load-test-values.yaml tolerations.
    # n2-standard-4 = 4 vCPU / 16 GiB. GKE allocatable after kubelet reserve is ~3920m / ~14.3 GiB,
    # but benchmark cluster DaemonSets (Cilium, security agents, etc.) can consume ~1500m+ CPU.
    # Use 2000m CPU to safely fit; adjust after checking actual overhead with:
    #   kubectl describe node <benchmark-node> | grep -A5 "Allocated resources"
    cpu = "2000m"
    mem = "12Gi"
    worker_replicas = max(1, math.ceil(rate / WORKER_JOBS_PER_SEC_PER_POD))

    common = (
        f" --set starter.rate={rate}"
        f" --set starter.resources.requests.cpu={cpu}"
        f" --set starter.resources.limits.cpu={cpu}"
        f" --set starter.resources.requests.memory={mem}"
        f" --set starter.resources.limits.memory={mem}"
    )

    if scenario == "realistic":
        worker_flags = " ".join(
            f"--set workers.{name}.replicas={worker_replicas}"
            for name in REALISTIC_WORKER_NAMES
        )
        return f"-f {REALISTIC_VALUES_URL}{common} {worker_flags}"

    # typical: single generic worker, explicit BPMN path
    return (
        f"--set starter.bpmnXmlPath={TYPICAL_BPMN_PATH}"
        f"{common}"
        f" --set workers.worker.replicas={worker_replicas}"
    )


# ── GitHub API ────────────────────────────────────────────────────────────────

def trigger_workflow(token: str, name: str, ref: str, inputs: dict, dry_run: bool) -> None:
    url = f"https://api.github.com/repos/{REPO}/actions/workflows/{WORKFLOW}/dispatches"
    payload = {"ref": ref, "inputs": {k: str(v) for k, v in inputs.items()}}

    if dry_run:
        print(f"  [DRY-RUN] POST {url}")
        print(f"  payload: {json.dumps(payload, indent=4)}")
        return

    if not token:
        print("ERROR: set the GITHUB_TOKEN env var", file=sys.stderr)
        sys.exit(1)

    data = json.dumps(payload).encode()
    req = urllib.request.Request(
        url,
        data=data,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as resp:
            print(f"  Triggered — HTTP {resp.status}")
    except urllib.error.HTTPError as e:
        body = e.read().decode()
        print(f"  ERROR HTTP {e.code}: {body}", file=sys.stderr)
        sys.exit(1)


def _es_display(extra: str, es: "ESConfig") -> str:
    """Return ES cpu/mem/heap/node display string, preferring values parsed from extra-platform-values."""
    cpu = re.search(r'elasticsearch\.master\.resources\.requests\.cpu=(\d+)m', extra or "")
    mem = re.search(r'elasticsearch\.master\.resources\.requests\.memory=(\d+)Gi', extra or "")
    node = re.search(r'elasticsearch\.master\.nodeSelector\.component=benchmark-(n2-standard-\d+)', extra or "")

    cpu_str = f"{cpu.group(1)}m" if cpu else _mcores(es.cpu_vcpu)
    mem_gi = int(mem.group(1)) if mem else math.ceil(es.mem_gi)
    heap_gi = math.ceil(min(mem_gi * 0.5, 31.0))
    node_str = node.group(1) if node else "n2-standard-8"

    return f"node={node_str}  cpu={cpu_str}  mem={mem_gi}Gi  heap={heap_gi}Gi"


# ── Main ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true", help="Print configs without triggering")
    parser.add_argument("--rates", default="50", help="Comma-separated PI/s rates")
    parser.add_argument("--ttl", type=int, default=TTL_DEFAULT, help="Days before namespace auto-delete")
    parser.add_argument("--ref", default=REF_DEFAULT, help="Git ref (branch/tag/SHA)")
    parser.add_argument("--no-optimize", action="store_true", help="Disable Optimize in the load test")
    parser.add_argument(
        "--scenario",
        choices=["typical", "realistic"],
        default="typical",
        help="Load test scenario: 'typical' (typical_process.bpmn, generic worker) or "
             "'realistic' (bankDisputeHandling BPMN, named workers). Default: typical",
    )
    parser.add_argument(
        "--broker-node-pool",
        choices=list(BROKER_NODE_POOLS.keys()),
        default="n2-standard-4",
        help=(
            "GKE node pool for broker pods. Sets CPU, memory, thread counts, "
            "nodeSelector and toleration together so the pod schedules correctly. "
            "n2-standard-4 (default): 3000m/2Gi, 3 threads. "
            "n2-standard-8: 6000m/8Gi, 6 threads — needed for ≥500 PI/s. "
            "n2-standard-16: 12000m/16Gi, 12 threads — needed for ≥1000 PI/s."
        ),
    )
    parser.add_argument(
        "--extra-platform-values",
        default="",
        help="Additional --set/--set-string flags appended verbatim to the generated "
             "platform-helm-values (e.g. to override ES or broker resources). "
             "Example: \"--set elasticsearch.master.resources.requests.cpu=10500m\"",
    )
    parser.add_argument(
        "--brokers",
        type=int,
        default=3,
        help=(
            "Number of broker pods (clusterSize). replicationFactor = min(3, brokers). "
            "Default: 3."
        ),
    )
    parser.add_argument(
        "--partitions",
        type=int,
        default=0,
        help=(
            "Number of partitions (partitionCount). Defaults to --brokers when not set. "
            "Set higher than brokers to utilize all CPU threads on large nodes: each partition "
            "gets its own actor thread, so partitions = brokers × 2 doubles per-cluster throughput "
            "on n2-standard-16 (12 CPU threads per broker)."
        ),
    )
    args = parser.parse_args()

    token = os.environ.get("GITHUB_TOKEN", "")
    if not token:
        try:
            token = subprocess.check_output(["gh", "auth", "token"], text=True).strip()
        except Exception as e:
            print(f"WARNING: could not retrieve token via 'gh auth token': {e}", file=sys.stderr)

    broker = BROKER_NODE_POOLS[args.broker_node_pool]
    rates = [int(r.strip()) for r in args.rates.split(",")]

    effective_partitions = args.partitions if args.partitions > 0 else args.brokers
    for rate in rates:
        cfg = build_test_config(rate, args.ttl, args.scenario, not args.no_optimize, args.brokers)
        platform_values = build_platform_helm_values(cfg.es, broker, args.brokers, effective_partitions)
        if args.extra_platform_values:
            platform_values = platform_values + " " + args.extra_platform_values
        load_values     = build_load_test_load(cfg.rate, args.scenario)

        worker_replicas = max(1, math.ceil(rate / WORKER_JOBS_PER_SEC_PER_POD))
        print(f"\n{'='*60}")
        print(f"  Scenario: {args.scenario}")
        print(f"  Rate:     {rate} PI/s")
        print(f"  Name:     {cfg.name}")
        print(f"  Workers:  {worker_replicas} pod(s)")
        print(f"  Brokers:  {args.brokers} pods  partitions={effective_partitions}  replication={min(3, args.brokers)}  "
              f"node={broker.name}  cpu={broker.cpu}  mem={broker.memory}  "
              f"cpu_threads={broker.cpu_threads}  io_threads={broker.io_threads}")
        print(f"  ES nodes: {cfg.es.replicas}x  {_es_display(args.extra_platform_values, cfg.es)}  disk={cfg.es.disk_gi}Gi")
        if args.extra_platform_values:
            print(f"  extra:    {args.extra_platform_values}")
        print(f"  platform: {platform_values}")
        print(f"  load:     {load_values}")

        inputs = {
            "name":                 cfg.name,
            "ref":                  args.ref,
            "scenario":             "custom",
            "load-test-load":       load_values,
            "platform-helm-values": platform_values,
            "enable-optimize":      "false" if args.no_optimize else "true",
            "ttl":                  str(args.ttl),
            "stable-vms":           "false",
        }

        trigger_workflow(token, cfg.name, args.ref, inputs, args.dry_run)

    if args.dry_run:
        print("\n[DRY-RUN complete — no workflows were triggered]")
    else:
        print(f"\nTriggered {len(rates)} load test(s). Monitor: https://dashboard.benchmark.camunda.cloud/")


if __name__ == "__main__":
    main()