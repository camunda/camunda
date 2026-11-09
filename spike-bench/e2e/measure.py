#!/usr/bin/env python3
"""Measures the running bench-broker over a window: CPU and CFS throttling of the container,
throughput, and latency percentiles from the broker's Prometheus metrics.

Usage: measure.py <seconds> <label> [--actors]  -> appends one JSON line to results-e2e.jsonl
"""
import json
import re
import subprocess
import sys
import time
import urllib.request
from collections import defaultdict
from pathlib import Path

LINE = re.compile(r'^([a-zA-Z_:][a-zA-Z0-9_:]*)(\{(.*)\})? (\S+)$')
LABEL = re.compile(r'(\w+)="((?:[^"\\]|\\.)*)"')


def scrape():
    text = urllib.request.urlopen("http://localhost:9600/actuator/prometheus", timeout=10).read().decode()
    samples = []
    for line in text.splitlines():
        if line.startswith("#") or not line:
            continue
        m = LINE.match(line)
        if not m:
            continue
        name, _, labels, value = m.groups()
        try:
            v = float(value)
        except ValueError:
            continue
        samples.append((name, dict(LABEL.findall(labels or "")), v))
    return samples


def cgroup():
    cid = subprocess.check_output(["docker", "inspect", "-f", "{{.Id}}", "bench-broker"], text=True).strip()
    stat = {}
    for l in Path(f"/sys/fs/cgroup/system.slice/docker-{cid}.scope/cpu.stat").read_text().splitlines():
        k, v = l.split()
        stat[k] = int(v)
    return stat


def total(samples, name, **match):
    return sum(v for n, l, v in samples if n == name and all(l.get(k) == mv for k, mv in match.items()))


def by_label(samples, name, label, **match):
    out = defaultdict(float)
    for n, l, v in samples:
        if n == name and all(l.get(k) == mv for k, mv in match.items()):
            out[l.get(label)] += v
    return out


def quantiles(s0, s1, base, qs=(0.5, 0.99), **match):
    """Quantiles of the histogram increase over the window (buckets summed over other labels)."""
    def buckets(samples):
        b = defaultdict(float)
        for n, l, v in samples:
            if n == base + "_bucket" and all(l.get(k) == mv for k, mv in match.items()):
                le = float("inf") if l["le"] == "+Inf" else float(l["le"])
                b[le] += v
        return b
    b0, b1 = buckets(s0), buckets(s1)
    les = sorted(b1)
    counts = [b1[le] - b0.get(le, 0.0) for le in les]
    totalc = counts[-1] if counts else 0
    res = {}
    for q in qs:
        if totalc <= 0:
            res[q] = None
            continue
        target = q * totalc
        prev_le, prev_c = 0.0, 0.0
        for le, c in zip(les, counts):
            if c >= target:
                if le == float("inf"):
                    res[q] = prev_le
                else:
                    frac = (target - prev_c) / max(c - prev_c, 1e-9)
                    res[q] = prev_le + (le - prev_le) * frac
                break
            prev_le, prev_c = le, c
    return res, totalc


def ms(x):
    return None if x is None else round(x * 1000, 2)


def main():
    seconds = float(sys.argv[1])
    label = sys.argv[2]
    actors = "--actors" in sys.argv
    s0, c0, t0 = scrape(), cgroup(), time.time()
    time.sleep(seconds)
    s1, c1, t1 = scrape(), cgroup(), time.time()
    dt = t1 - t0
    out = {"label": label, "seconds": round(dt, 1)}

    periods = c1["nr_periods"] - c0["nr_periods"]
    out["cores"] = round((c1["usage_usec"] - c0["usage_usec"]) / 1e6 / dt, 3)
    out["throttled_pct"] = round(100 * (c1["nr_throttled"] - c0["nr_throttled"]) / max(1, periods), 1)
    out["throttled_ms_per_s"] = round((c1["throttled_usec"] - c0["throttled_usec"]) / 1000 / dt, 1)

    def rate(name, **m):
        return round((total(s1, name, **m) - total(s0, name, **m)) / dt, 1)

    out["pi_created_per_s"] = rate("zeebe_process_instance_creations_total")
    out["pi_completed_per_s"] = rate("zeebe_executed_instances_total", action="completed", type="ROOT_PROCESS_INSTANCE")
    out["jobs_completed_per_s"] = rate("zeebe_job_events_total", action="completed")
    out["records_processed_per_s"] = rate("zeebe_stream_processor_records_total", action="processed")
    out["records_exported_per_s"] = rate("zeebe_exporter_events_total", action="exported")
    cores = out["cores"] or 1e-9
    out["records_processed_per_core_s"] = round(out["records_processed_per_s"] / cores, 1)

    for key, base, m in [
        ("create_pi", "http_server_requests_seconds", {"uri": "/v2/process-instances", "method": "POST"}),
        ("complete_job", "http_server_requests_seconds", {"uri": "/v2/jobs/{jobKey}/completion", "method": "POST"}),
        ("activate_jobs", "http_server_requests_seconds", {"uri": "/v2/jobs/activation", "method": "POST"}),
        ("sp_latency", "zeebe_stream_processor_latency_seconds", {}),
        ("commit_latency", "zeebe_log_appender_commit_latency_seconds", {}),
        ("exporting_latency", "zeebe_exporting_latency_seconds", {}),
        ("processing_sched_delay", "zeebe_processing_scheduling_delay_seconds", {}),
    ]:
        q, n = quantiles(s0, s1, base, **m)
        out[f"{key}_p50_ms"] = ms(q[0.5])
        out[f"{key}_p99_ms"] = ms(q[0.99])
        out[f"{key}_count"] = int(n)

    if actors:
        cnt0 = by_label(s0, "zeebe_actor_task_execution_count_total", "actorName")
        cnt1 = by_label(s1, "zeebe_actor_task_execution_count_total", "actorName")
        sum0 = by_label(s0, "zeebe_actor_task_execution_latency_seconds_sum", "actorName")
        sum1 = by_label(s1, "zeebe_actor_task_execution_latency_seconds_sum", "actorName")
        c_0 = by_label(s0, "zeebe_actor_task_execution_latency_seconds_count", "actorName")
        c_1 = by_label(s1, "zeebe_actor_task_execution_latency_seconds_count", "actorName")
        prof = []
        for a in cnt1:
            turns = (cnt1[a] - cnt0.get(a, 0)) / dt
            n = c_1.get(a, 0) - c_0.get(a, 0)
            busy = sum1.get(a, 0) - sum0.get(a, 0)
            prof.append({
                "actor": a,
                "turns_per_s": round(turns, 1),
                "mean_turn_us": round(busy / n * 1e6, 1) if n else None,
                "busy_cores": round(busy / dt, 3),
            })
        prof.sort(key=lambda x: -x["busy_cores"])
        out["actors"] = prof
        sched = {}
        for st in ("Future", "Timer", "None"):
            q, n = quantiles(s0, s1, "zeebe_actor_job_scheduling_latency_seconds", subscriptionType=st)
            sched[st] = {"p50_us": None if q[0.5] is None else round(q[0.5] * 1e6, 1),
                         "p99_us": None if q[0.99] is None else round(q[0.99] * 1e6, 1),
                         "jobs_per_s": round(n / dt, 1)}
        out["job_scheduling_latency"] = sched

    with open(Path(__file__).parent / "results-e2e.jsonl", "a") as f:
        f.write(json.dumps(out) + "\n")
    print(json.dumps(out, indent=1))


if __name__ == "__main__":
    main()
