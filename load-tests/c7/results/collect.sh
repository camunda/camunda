#!/bin/bash
# Turns one benchmark run into a self-contained results folder:
#
#   <out>/<run-id>/
#     metadata.json    run id, window, namespace, configuration (images, rate, sizing)
#     report.json      loadtestctl report with report-queries-c7.yaml (one row, C8-compatible keys)
#     report.csv       same, as a CSV row with header
#     timeseries.csv   1-minute series: throughput, latency, backlog, history size (growing state)
#     cloudwatch.json  Aurora metrics for the window (only when AURORA_INSTANCE_ID is set)
#
# Environment:
#   PROMETHEUS_URL     default http://localhost:9090
#   METRICS_NAMESPACE  Prometheus namespace label (local | c7-<name> | ecs-<prefix>-c7)
#   START, END         RFC3339 or Unix seconds; END defaults to now
#   DURATION           seconds, used when START is empty (default 1800)
#   RUN_ID             default <namespace>-<end time>
#   OUT_DIR            default ./results-out
#   RESULTS_BUCKET     optional: upload to s3://<bucket>/c7/<run-id>/
#   AURORA_CLUSTER_ID, AURORA_INSTANCE_ID   optional: fetch CloudWatch metrics
#   RUN_CONFIG         optional JSON with the run configuration
#   LOADTESTCTL        default "loadtestctl"
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
PROMETHEUS_URL="${PROMETHEUS_URL:-http://localhost:9090}"
METRICS_NAMESPACE="${METRICS_NAMESPACE:?set METRICS_NAMESPACE}"
DURATION="${DURATION:-1800}"
OUT_DIR="${OUT_DIR:-./results-out}"
LOADTESTCTL="${LOADTESTCTL:-loadtestctl}"
QUERIES="${QUERIES:-$HERE/report-queries-c7.yaml}"

to_epoch() {
  python3 -c 'import sys,datetime as d;v=sys.argv[1];print(int(float(v)) if v.replace(".","",1).isdigit() else int(d.datetime.fromisoformat(v.replace("Z","+00:00")).timestamp()))' "$1"
}

end_epoch=$(to_epoch "${END:-$(date -u +%s)}")
if [[ -n "${START:-}" ]]; then
  start_epoch=$(to_epoch "$START")
else
  start_epoch=$((end_epoch - DURATION))
fi
start_iso=$(date -u -d "@$start_epoch" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -r "$start_epoch" +%Y-%m-%dT%H:%M:%SZ)
end_iso=$(date -u -d "@$end_epoch" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -r "$end_epoch" +%Y-%m-%dT%H:%M:%SZ)
RUN_ID="${RUN_ID:-${METRICS_NAMESPACE}-$(date -u -d "@$end_epoch" +%Y%m%d-%H%M 2>/dev/null || date -u -r "$end_epoch" +%Y%m%d-%H%M)}"
out="$OUT_DIR/$RUN_ID"
mkdir -p "$out"
echo "Run $RUN_ID: $METRICS_NAMESPACE $start_iso .. $end_iso -> $out"

# 1. Summary report (same tool and format as the C8 load tests)
export LOADTESTCTL_PROMETHEUS_URL="$PROMETHEUS_URL"
for fmt in json csv; do
  $LOADTESTCTL report "$METRICS_NAMESPACE" --queries "$QUERIES" \
    --start "$start_iso" --end "$end_iso" --format "$fmt" --output "$out/report.$fmt"
done

# 2. Time series and metadata
python3 - "$PROMETHEUS_URL" "$METRICS_NAMESPACE" "$start_epoch" "$end_epoch" "$out" "$RUN_ID" <<'PY'
import csv, json, os, sys, urllib.parse, urllib.request, datetime

prom, ns, start, end, out, run_id = sys.argv[1:7]
series = {
    "started_pi_per_s": f'sum(rate(c7_process_instances_total{{namespace="{ns}", event="started"}}[2m]))',
    "completed_pi_per_s": f'sum(rate(c7_process_instances_total{{namespace="{ns}", event="completed"}}[2m]))',
    "pi_duration_p50_ms": f'1000 * histogram_quantile(0.5, sum by (le) (rate(c7_process_instance_duration_seconds_bucket{{namespace="{ns}"}}[2m])))',
    "pi_duration_p99_ms": f'1000 * histogram_quantile(0.99, sum by (le) (rate(c7_process_instance_duration_seconds_bucket{{namespace="{ns}"}}[2m])))',
    "start_latency_p99_ms": f'1000 * histogram_quantile(0.99, sum by (le) (rate(starter_response_latency_seconds_bucket{{namespace="{ns}"}}[2m])))',
    "open_external_tasks": f'sum(cnpg_c7_runtime_external_tasks{{namespace="{ns}"}})',
    "history_process_instances": f'max(cnpg_c7_table_rows{{namespace="{ns}", table="act_hi_procinst"}})',
    "db_size_bytes": f'max(cnpg_c7_db_size_bytes{{namespace="{ns}"}})',
    "engine_cpu_cores": f'sum(process_cpu_usage{{namespace="{ns}", application="c7-engine"}} * system_cpu_count{{namespace="{ns}", application="c7-engine"}})',
    "db_pool_pending": f'sum(hikaricp_connections_pending{{namespace="{ns}", application="c7-engine"}})',
}
rows = {}
for name, q in series.items():
    url = f"{prom}/api/v1/query_range?" + urllib.parse.urlencode({"query": q, "start": start, "end": end, "step": "60"})
    with urllib.request.urlopen(url, timeout=60) as r:
        result = json.load(r)["data"]["result"]
    for ts, v in (result[0]["values"] if result else []):
        rows.setdefault(int(ts), {})[name] = v
with open(os.path.join(out, "timeseries.csv"), "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["time"] + list(series))
    for ts in sorted(rows):
        t = datetime.datetime.fromtimestamp(ts, datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
        w.writerow([t] + [rows[ts].get(n, "") for n in series])

config = json.loads(os.environ.get("RUN_CONFIG") or "{}")
meta = {
    "run_id": run_id,
    "product": "camunda7",
    "namespace": ns,
    "start": datetime.datetime.fromtimestamp(int(start), datetime.timezone.utc).isoformat(),
    "end": datetime.datetime.fromtimestamp(int(end), datetime.timezone.utc).isoformat(),
    "duration_seconds": int(end) - int(start),
    "config": config,
    "git_sha": os.environ.get("GIT_SHA", ""),
    "created_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
}
with open(os.path.join(out, "metadata.json"), "w") as f:
    json.dump(meta, f, indent=2)
print(f"timeseries.csv: {len(rows)} samples")
PY

# 3. Aurora CloudWatch metrics (ECS only)
if [[ -n "${AURORA_INSTANCE_ID:-}" ]]; then
  python3 - "$AURORA_INSTANCE_ID" "${AURORA_CLUSTER_ID:-}" "$start_epoch" "$end_epoch" > "$out/cloudwatch.json" <<'PY'
import json, subprocess, sys
instance, cluster, start, end = sys.argv[1:5]
inst = ["CPUUtilization", "DBLoad", "DBLoadCPU", "DBLoadNonCPU", "CommitThroughput", "CommitLatency",
        "DatabaseConnections", "FreeableMemory", "WriteIOPS", "ReadIOPS", "WriteThroughput", "WriteLatency"]
clus = ["VolumeWriteIOPs", "VolumeReadIOPs", "VolumeBytesUsed"]
queries = []
for i, m in enumerate(inst):
    queries.append({"Id": f"i{i}", "Label": m, "MetricStat": {"Metric": {"Namespace": "AWS/RDS", "MetricName": m,
        "Dimensions": [{"Name": "DBInstanceIdentifier", "Value": instance}]}, "Period": 60, "Stat": "Average"}})
if cluster:
    for i, m in enumerate(clus):
        queries.append({"Id": f"c{i}", "Label": m, "MetricStat": {"Metric": {"Namespace": "AWS/RDS", "MetricName": m,
            "Dimensions": [{"Name": "DBClusterIdentifier", "Value": cluster}]}, "Period": 300 if m.startswith("Volume") else 60, "Stat": "Average"}})
res = json.loads(subprocess.check_output(["aws", "cloudwatch", "get-metric-data", "--start-time", start, "--end-time", end,
    "--metric-data-queries", json.dumps(queries), "--output", "json"]))
out = {}
for r in res["MetricDataResults"]:
    vals = r["Values"]
    out[r["Label"]] = {"avg": sum(vals) / len(vals) if vals else None, "max": max(vals) if vals else None,
                       "points": [{"t": t, "v": v} for t, v in zip(r["Timestamps"], vals)]}
json.dump(out, sys.stdout, indent=2)
PY
  echo "cloudwatch.json written"
fi

# 4. Upload
if [[ -n "${RESULTS_BUCKET:-}" ]]; then
  aws s3 cp --recursive "$out" "s3://$RESULTS_BUCKET/c7/$RUN_ID/"
  echo "Uploaded to s3://$RESULTS_BUCKET/c7/$RUN_ID/"
fi
echo "Done: $out"
