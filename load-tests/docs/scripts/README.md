# Scripts

This folder contains several scripts we wrote to test or debug things.

## Profile.sh

**Usage:**
Run executeProfiling.sh with a pod name, optional event type, and optional profiler options. It will download the async profiler package, run in your current namespace, copy necessary binaries to the pod, run the async profiler, and copy the resulting flamegraph back to your local disk.

**Syntax:**

```
./executeProfiling.sh [-p|--prefix PREFIX] <POD-NAME> [EVENT-TYPE] [ADDITIONAL-OPTIONS]
```

**Options:**
- `-p`, `--prefix` - Filename prefix for the generated report. Default: `flamegraph-`

**Event Types:**
- `cpu` - CPU profiling (default)
- `wall` - Wall clock time profiling (includes waiting/blocking time). Automatically uses `-t` flag to split by thread for better analysis.
- `alloc` - Memory allocation profiling

**Additional Options:**
You can pass additional flags to async-profiler as the third parameter. Common options include:
- `-t` - to profile threads separately
- `--title "My Title"` - Set a custom title for the flamegraph
- `--minwidth <percent>` - Omit frames smaller than specified percentage

See [async-profiler documentation](https://github.com/async-profiler/async-profiler/blob/master/docs/ProfilerOptions.md) for potential options.

Example with CPU profiling (default):

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2
...
Profiling for 100 seconds
Done
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-cpu.html release-8-8-0-alpha6-zeebe-2-flamegraph-cpu.html
tar: Removing leading `/' from member names

```

Example with wall clock profiling:

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2 wall
...
Profiling for 100 seconds
Done
+ kubectl cp release-8-8-0-alpha6-zeebe-2:/usr/local/camunda/data/flamegraph-wall.html release-8-8-0-alpha6-zeebe-2-flamegraph-wall.html
```

Example with additional profiler options:

```
 $ ./executeProfiling.sh release-8-8-0-alpha6-zeebe-2 cpu "-t"
...
Profiling for 100 seconds with flamegraph format
Done
```

**GitHub Actions Workflow:**
You can also use the [Profile Load Test workflow](https://github.com/camunda/camunda/actions/workflows/profile-load-test.yml) to profile pods running in load tests. This workflow allows you to select the load test name, pod name, event type (cpu/wall/alloc), and optional profiler options through the GitHub UI.

## loadTestMetrics.sh

**Usage:**
Runs every PromQL query defined in `queries.yaml` against a Prometheus HTTP endpoint and emits a `{name: value, ...}` JSON object on stdout. Failed/empty queries are omitted. Used by the [Camunda Load Test Metrics workflow](https://github.com/camunda/camunda/actions/workflows/camunda-load-test-metrics.yaml) and runnable locally against any reachable Prometheus.

**Syntax:**

```
./loadTestMetrics.sh <namespace> [duration_seconds] [endpoint] [extra_curl_opts]
```

**Arguments:**
- `namespace` — exact namespace label, e.g. `c8-pgoyal-quicker-pr-1234`. Required.
- `duration_seconds` — PromQL range-vector window. Default: `600`.
- `endpoint` — Prometheus base URL. Default: `http://localhost:9090` (assumes `kubectl port-forward` is open).
- `extra_curl_opts` — free-form curl options string, e.g. `--user "u:p"` for HTTP basic auth.

**Examples:**

Local dev (port-forward already open):

```
./loadTestMetrics.sh c8-pgoyal-quicker-pr-1234
```

Against the LDAP-protected ingress:

```
./loadTestMetrics.sh \
  c8-medic-daily-2026-05-08-abc1234-test 600 \
  https://ci-monitor.benchmark.camunda.cloud \
  "--user $PROM_USER:$PROM_PASS" > /tmp/results.json
```

zsh users: quote regex-looking arguments to avoid `no matches found` glob errors.

## loadTestReport.sh

**Usage:**
Builds a wider report for one load-test namespace and emits the values as JSON, CSV, or TSV. The
CSV/TSV column order follows `report-queries.json`, which is laid out for spreadsheet imports:
namespace and Docker image, cluster size, Camunda and secondary-storage resources, throughput,
latency, and backlog metrics.
Metrics that Prometheus does not return are kept as `null` in JSON and `NaN` in CSV/TSV by
default.
Historical gauge metrics, such as pod counts, limits, PVC sizes, and backlogs, are evaluated over
the full requested window rather than only at the end timestamp.
The namespace column comes from the requested namespace. The Docker image column is extracted from
`kube_pod_container_info` over the requested window, so deleted namespaces still work while the
historical series is retained.
Dashboard-style rates are calculated from short `--rate-interval` samples and then summarized over
the requested window using `--sample-step`, so long reports do not flatten p50/p99 CPU or
throughput into one coarse counter rate.
Throughput rates, CPU throttling, backpressure, and backlog values are averages over the requested
window; backpressure and backlog first take the highest partition value at each sample.

**Syntax:**

```
./loadTestReport.sh <namespace> [options]
```

**Common options:**
- `--duration-seconds <sec>`: query window duration. Default: `600`.
- `--rate-interval <dur>`: short Prometheus rate interval for dashboard-style rollups. Default:
`5m`.
- `--sample-step <dur>`: sample resolution for averaging dashboard-style window summaries. Default:
`1m`.
- `--template camunda|zeebe-gateway`: spreadsheet layout to emit. Use `camunda` for the current
orchestration cluster layout and `zeebe-gateway` for 8.7-style Zeebe broker plus Zeebe Gateway
deployments. Default: `camunda`.
- `--at <time>`: Prometheus query time anchor; the window ends at this RFC3339 or Unix timestamp.
- `--start <time> --end <time>`: exact reporting window; duration is derived automatically.
- `--endpoint <url>`: Prometheus base URL. Default: `http://localhost:9090`.
- `--curl-opts <opts>`: free-form curl options string, e.g. `--user "u:p"`.
- `--format json|csv|tsv`: output format. Default: `json`.
- `--no-header`: omit the CSV/TSV header row for direct spreadsheet row pasting.
- `--missing-value <value>`: placeholder for missing CSV/TSV metrics. Default: `NaN`.
- `--output <path>`: write the report to a file.

**Examples:**

Port-forwarded Prometheus, JSON:

```
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
./loadTestReport.sh c8-ck-baseline-20260814 --duration-seconds 1800
```

Exact historical window, TSV row ready to paste into a spreadsheet:

```
./loadTestReport.sh c8-ck-baseline-20260814 \
  --start 2026-08-14T10:00:00Z \
  --end 2026-08-14T10:30:00Z \
  --format tsv --no-header
```

Use `--missing-value null` if your spreadsheet should show `null` instead of `NaN` for missing
metrics.

8.7-style Zeebe broker plus Zeebe Gateway layout:

```
./loadTestReport.sh c8-ck-base-8736-endurance \
  --template zeebe-gateway \
  --start 2026-08-12T09:00:00Z \
  --end 2026-08-13T06:00:00Z \
  --format tsv --no-header
```

CI monitor ingress with basic auth:

```
./loadTestReport.sh c8-ck-baseline-20260814 \
  --duration-seconds 1800 \
  --endpoint https://ci-monitor.benchmark.camunda.cloud \
  --curl-opts "--user $PROM_USER:$PROM_PASS" \
  --format csv > /tmp/load-test-report.csv
```

## PartitionDistribution.sh

**Usage:**

```bash
./partitionDistribution.sh {nodes} {partitionCount} {replicationFactor}
```

This script will calculate the distribution of partitions in a cluster.

_Example Output:_

```bash
$ ./partitionDistribution.sh 3 3 3
Distribution:
P\N|	N 0|	N 1|	N 2
P 0|	L  |	F  |	F
P 1|	F  |	L  |	F
P 2|	F  |	F  |	L

Partitions per Node:
N 0: 3
N 1: 3
N 2: 3
```

