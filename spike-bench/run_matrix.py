#!/usr/bin/env python3
"""Runs the benchmark matrix: every config x variant x rep, in shuffled variant order.

Each run is a fresh JVM in its own cgroup scope with a CPU quota (like a container limit).
Results are appended to results.jsonl, one JSON object per run.
"""
import json
import os
import random
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
OUT = HERE / sys.argv[1] if len(sys.argv) > 1 else HERE / "results.jsonl"
REPS = int(sys.argv[2]) if len(sys.argv) > 2 else 2
ONLY = sys.argv[3].split(",") if len(sys.argv) > 3 else None

ALL = ["main", "pr68725", "pr68754", "simple", "simple@threads=cpu"]
PLATFORM = ["main", "pr68725", "pr68754"]
K8S = "cpu=3 io=3"
TIMING = "warmupMs=2000 durationMs=4000"
STD = "cpu=4 io=4"

# name, cpu quota %, variants, args
# Kubernetes shape (load-test defaults): 3-CPU CFS quota, cpuThreadCount=3, ioThreadCount=3.
# broker-*: calibrated from a real broker at the `typical` load (50 PI/s, 3 partitions):
#   ~146 processed commands/s and ~370 exported records/s per partition; x6 for `max`-like load.
BROKER = "scenario=broker partitions=3 spWorkNs=40000 followUps=2 exportWorkNs=30000 flushEvery=500 flushMs=15 replicationUs=300 snapshotPeriodMs=3000 snapshotMs=300"
CONFIGS = [
    ("k8s-broker-typical", 300, ALL, f"{K8S} {BROKER} rate=450"),
    ("k8s-broker-max", 300, ALL, f"{K8S} {BROKER} rate=2700"),
    ("k8s-broker-max-inline", 300, ["simple", "simple@threads=cpu"], f"{K8S} {BROKER} rate=2700 blockMode=inline"),
    ("k8s-pingpong-1", 300, ALL, f"{K8S} scenario=pingpong pairs=1"),
    ("k8s-fanout-256", 300, ALL, f"{K8S} scenario=fanout workers=256 workNs=2000"),
    ("k8s-ingress-50k", 300, ALL, f"{K8S} scenario=ingress rate=50000 actors=32 workNs=5000"),
    ("k8s-ingress-250k", 300, ALL, f"{K8S} scenario=ingress rate=250000 actors=32 workNs=5000"),
    ("k8s-timers-500x10ms", 300, ALL, f"{K8S} scenario=timers actors=500 periodMs=10 workNs=1000"),
    ("k8s-fairness-12chains", 300, ALL, f"{K8S} scenario=fairness pairs=12 hopWorkNs=1000 rate=10000"),
    ("k8s-ioblock-k1-native", 300, ALL, f"{K8S} scenario=ioblock blockers=1 blockKind=native rate=50000"),
    ("k8s-ioblock-k3-native-inline", 300, ALL, f"{K8S} scenario=ioblock blockers=3 blockKind=native rate=50000 blockMode=inline"),
]


def main():
    configs = [c for c in CONFIGS if ONLY is None or c[0] in ONLY]
    total = sum(len(c[2]) for c in configs) * REPS
    done = 0
    start = time.time()
    with OUT.open("a") as out:
        for rep in range(REPS):
            for name, quota, variants, args in configs:
                order = list(variants)
                random.shuffle(order)
                for variant in order:
                    load_before = os.getloadavg()[0]
                    cmd = [str(HERE / "run1.sh"), variant, str(rep), str(quota), f"config={name}"]
                    cmd += args.split() + TIMING.split()
                    try:
                        res = subprocess.run(cmd, capture_output=True, text=True, timeout=90)
                        line = res.stdout.strip().splitlines()[-1] if res.stdout.strip() else ""
                        data = json.loads(line) if line else None
                    except (subprocess.TimeoutExpired, json.JSONDecodeError) as e:
                        data = None
                        line = repr(e)
                    if data is None:
                        data = {"variant": variant, "rep": rep, "config": name, "error": line[:300]}
                    subprocess.run(["pkill", "-9", "-f", "bin/java .*SchedBench"], check=False)
                    data["load1_before"] = round(load_before, 2)
                    out.write(json.dumps(data) + "\n")
                    out.flush()
                    done += 1
                    eta = (time.time() - start) / done * (total - done)
                    print(f"[{done}/{total}] rep={rep} {name} {variant} eta={eta/60:.0f}min", flush=True)


if __name__ == "__main__":
    main()
