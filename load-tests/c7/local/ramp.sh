#!/bin/bash
# Steps the start rate and prints one line of metrics per step, to find the saturation point.
#
#   ./ramp.sh "100 200 300" [step_seconds]
#
# Each step: set RATE, wait step_seconds, then average the last half of the step.
set -euo pipefail
cd "$(dirname "$0")"
rates="${1:-100 200 300}"
step="${2:-240}"
window="$((step / 2))s"
prom=http://localhost:9090/api/v1/query

q() {
  curl -s --data-urlencode "query=$1" "$prom" |
    python3 -c 'import json,sys;r=json.load(sys.stdin)["data"]["result"];v=float(r[0]["value"][1]) if r else float("nan");print(f"{v:.3g}")'
}

printf "%6s %8s %8s %9s %8s %8s %8s %8s %8s %8s %9s\n" \
  rate started complete open_tasks p50_s p99_s eng_cpu pg_cpu pool_pend start_p99 wal_kb/pi
for rate in $rates; do
  RATE=$rate docker compose up -d starter >/dev/null 2>&1
  sleep "$step"
  pg_cpu=$(docker stats --no-stream --format '{{.Name}} {{.CPUPerc}}' | awk '/postgres-1/ {sub("%","",$2); printf "%.2f", $2/100}')
  printf "%6s %8s %8s %9s %8s %8s %8s %8s %8s %8s %9s\n" "$rate" \
    "$(q "sum(rate(c7_process_instances_total{event=\"started\"}[$window]))")" \
    "$(q "sum(rate(c7_process_instances_total{event=\"completed\"}[$window]))")" \
    "$(q 'sum(cnpg_c7_runtime_external_tasks)')" \
    "$(q "histogram_quantile(0.5, sum by (le)(rate(c7_process_instance_duration_seconds_bucket[$window])))")" \
    "$(q "histogram_quantile(0.99, sum by (le)(rate(c7_process_instance_duration_seconds_bucket[$window])))")" \
    "$(q 'sum(process_cpu_usage{pod="engine"} * system_cpu_count{pod="engine"})')" \
    "$pg_cpu" \
    "$(q "avg_over_time(sum(hikaricp_connections_pending)[$window:15s])")" \
    "$(q "histogram_quantile(0.99, sum by (le)(rate(starter_response_latency_seconds_bucket[$window])))")" \
    "$(q "sum(rate(cnpg_c7_wal_wal_bytes[$window])) / sum(rate(c7_process_instances_total{event=\"completed\"}[$window])) / 1024")"
done
