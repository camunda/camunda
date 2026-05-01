# Optimize Benchmark Scripts

Two scripts for triggering and measuring Camunda load tests on the benchmark cluster.

- **`trigger-optimize-load-tests.py`** — low-level: builds Helm values and dispatches a GitHub Actions workflow.
- **`run-benchmark.py`** — high-level: wraps the trigger script, monitors pod health, collects Grafana metrics, writes results to CSV, and optionally auto-scales resources to find minimum sizing.

---

## Prerequisites

- Python 3.9+
- `kubectl` configured for the benchmark cluster (run `tsh kube login` if auth expires)
- `gh` CLI authenticated, or `GITHUB_TOKEN` set
- A Grafana session cookie or API token

---

## Credentials (environment variables only)

Secrets are never accepted as CLI arguments.

| Variable         | Required for                    | How to get it                                              |
|------------------|---------------------------------|------------------------------------------------------------|
| `GITHUB_TOKEN`   | Triggering workflows            | `export GITHUB_TOKEN=$(gh auth token)`                     |
| `GRAFANA_TOKEN`  | Grafana API (service account)   | Grafana → Administration → Service accounts                |
| `GRAFANA_COOKIE` | Grafana OAuth proxy (preferred) | Copy `VouchCookie=...` from browser DevTools → Network tab |

`GRAFANA_COOKIE` takes precedence over `GRAFANA_TOKEN` when both are set.

```bash
export GITHUB_TOKEN=$(gh auth token)
export GRAFANA_COOKIE="VouchCookie=<value-from-browser>"
```

---

## `trigger-optimize-load-tests.py`

Dispatches `camunda-load-test.yml` on `camunda/camunda`. Computes Helm values for Elasticsearch sizing, broker topology, node pool scheduling, and load test workers.

### Options

| Flag                       | Default        | Description                                                              |
|----------------------------|----------------|--------------------------------------------------------------------------|
| `--rates`                  | `50`           | Comma-separated PI/s rates. Each triggers a separate workflow run.       |
| `--ref`                    | `main`         | Git branch/tag/SHA for the workflow.                                     |
| `--ttl`                    | `3`            | Days before the namespace is auto-deleted.                               |
| `--scenario`               | `typical`      | `typical` (one_task.bpmn, generic worker) or `realistic` (bank dispute). |
| `--broker-node-pool`       | `n2-standard-4`| GKE node pool for broker pods. Sets CPU, memory, threads, scheduling.    |
| `--brokers`                | `3`            | Broker pod count (= clusterSize = partitionCount).                       |
| `--no-optimize`            | *(unset)*      | Disable the Optimize component in the load test.                         |
| `--extra-platform-values`  | `""`           | Additional `--set`/`--set-string` flags appended to platform Helm values.|
| `--dry-run`                | *(unset)*      | Print computed values without triggering anything.                       |

### Node pool presets

| Pool            | CPU      | Memory | Threads (CPU/IO) | Use case                    |
|-----------------|----------|--------|------------------|-----------------------------|
| `n2-standard-4` | `3000m`  | `2Gi`  | 3 / 3            | Default, up to ~200 PI/s    |
| `n2-standard-8` | `6000m`  | `8Gi`  | 6 / 6            | 200–500 PI/s                |
| `n2-standard-16`| `12000m` | `16Gi` | 12 / 12          | 500–800 PI/s                |

### Examples

```bash
# Single run at 100 PI/s (typical scenario, no Optimize)
python3 trigger-optimize-load-tests.py --rates 100 --no-optimize

# Multiple rates in one command
python3 trigger-optimize-load-tests.py --rates 100,250,500 --no-optimize

# Larger broker node pool for high throughput
python3 trigger-optimize-load-tests.py --rates 500 --broker-node-pool n2-standard-8 --no-optimize

# Realistic (bank dispute) scenario with Optimize enabled
python3 trigger-optimize-load-tests.py --rates 250 --scenario realistic

# Override ES CPU on top of auto-computed values
python3 trigger-optimize-load-tests.py --rates 500 \
  --extra-platform-values "--set elasticsearch.master.resources.requests.cpu=10500m"

# Dry run — inspect values without triggering
python3 trigger-optimize-load-tests.py --rates 200 --dry-run
```

---

## `run-benchmark.py`

Wraps `trigger-optimize-load-tests.py` with pod health monitoring, Grafana metric collection, and CSV output. Any flags not recognized by `run-benchmark.py` are forwarded directly to the trigger script (e.g. `--scenario realistic`).

### Modes

| Mode              | Flag(s)                  | Description                                              |
|-------------------|--------------------------|----------------------------------------------------------|
| **Manual**        | `--rates`                | Run one or more fixed rates, collect results.            |
| **Auto-scale**    | `--auto-scale`           | Adaptive loop: double rate on success, scale on failure. |
| **Capacity plan** | `--capacity-plan [FILE]` | Run every row in a capacity-plan CSV.                    |
| **Collect-only**  | `--collect-only NS`      | Query Grafana for an existing namespace, no deploy.      |
| **Recover**       | `--recover`              | Re-query GRAFANA_ERROR rows in the CSV.                  |

### All options

**Monitoring**

| Flag              | Default  | Description                                                                    |
|-------------------|----------|--------------------------------------------------------------------------------|
| `--collect-after` | `5`      | Minutes of continuous pod health required before collecting metrics.           |
| `--timeout`       | `40`     | Total minutes to wait for pods to reach healthy state before marking ERROR.    |
| `--grafana-url`   | `https://dashboard.benchmark.camunda.cloud` | Grafana base URL.           |
| `--output`        | `benchmark-results.csv` | CSV file for results (appended, never overwritten).         |
| `--log`           | `<output>.log`          | Log file (mirrors all stdout/stderr output).                |
| `--collect-only`  | —        | Collect metrics for a running namespace without triggering a new deployment.   |
| `--at-time`       | *(now)*  | Query Grafana at this UTC time. Accepts Unix timestamp or ISO 8601 (`2026-05-01T16:20:00Z`). Use with `--collect-only` when the namespace has already been deleted. |
| `--capacity-plan` | `capacity-plan.csv` | Run every row in a capacity-plan CSV (see below).                |
| `--recover`       | —        | Re-query Grafana for all rows with `GRAFANA_ERROR` status.                     |

**Deployment**

| Flag                 | Default          | Description                                                           |
|----------------------|------------------|-----------------------------------------------------------------------|
| `--optimize`         | *(disabled)*     | Enable the Optimize component (default: off).                         |
| `--broker-node-pool` | `n2-standard-4`  | Starting node pool for brokers (manual mode).                         |
| `--brokers`          | `3`              | Number of broker pods.                                                |
| `--rates`            | `50`             | Comma-separated PI/s rates (manual mode only; ignored by auto-scale). |
| `--ref`              | `main`           | Git branch/tag/SHA for the workflow.                                  |
| `--ttl`              | `1`              | Namespace TTL in days.                                                |
| `--dry-run`          | —                | Print trigger output, skip deploy/monitoring.                         |

**Auto-scale**

| Flag            | Default | Description                                                                     |
|-----------------|---------|---------------------------------------------------------------------------------|
| `--auto-scale`  | —       | Enable adaptive auto-scale loop.                                                |
| `--max-rate`    | `800`   | Stop the loop when this PI/s is reached.                                        |
| `--start-rate`  | `50`    | Start (or resume) the loop from this rate.                                      |
| `--scale-steps` | `0`     | Pre-apply N scale-up steps before the first run. Used when resuming after crash.|

### Capacity-plan mode

Runs every row in a capacity-plan CSV as a sequential benchmark. Each row maps directly to a
`ResourceState`, so resources are applied exactly as specified — no auto-scaling logic is involved.

The CSV must have these columns:

| Column         | Description                                      |
|----------------|--------------------------------------------------|
| `Status`       | Label only (`VALIDATED`, `EXTRAPOLATED`, etc.)   |
| `rates`        | Target PI/s                                      |
| `brokers`      | Broker pod count                                 |
| `broker_node`  | GKE node pool (`n2-standard-4/8/16`)             |
| `broker_cpu_m` | Broker CPU in millicores (e.g. `15000`)          |
| `broker_mem_gi`| Broker memory in GiB (e.g. `24`)                |
| `es_node`      | ES node pool (`n2-standard-8/16`)                |
| `es_cpu_m`     | ES CPU in millicores                             |
| `es_mem_gi`    | ES memory in GiB                                 |
| `es_replicas`  | Elasticsearch node count (usually `3`)           |

```bash
# Run all entries in the default capacity-plan.csv
python3 run-benchmark.py --capacity-plan

# Run a custom plan file
python3 run-benchmark.py --capacity-plan results/my-plan.csv
```

### Auto-scale loop

The loop starts at `--start-rate` (default 50 PI/s) and doubles on each success:
`50 → 100 → 200 → 400 → 800`

For each rate:
1. Deploy with current resources (default: `n2-standard-4`, 3000m CPU, 2Gi memory).
2. Wait for all pods to be healthy for `--collect-after` minutes.
3. Query Grafana and compute `achieved PI/s`.
4. **If `achieved ≥ 90% of target`** → success. Move to the next (doubled) rate, reset resources to defaults.
5. **If `achieved < 90% of target`** → scale broker + ES resources by 50% and retry at the same rate.
6. Stop scaling when both broker and ES are at `n2-standard-16` maximum limits.

#### Resource scaling tiers

Broker nodes scale vertically, snapping to pool defaults when crossing a boundary:

```
n2-standard-4  → n2-standard-8  → n2-standard-16
  3000m/2Gi    →   6000m/8Gi    →   12000m/16Gi   (defaults, snap-to)
  max: 3800m   →   max: 7500m   →   max: 15000m
```

Elasticsearch always runs on `n2-standard-8` by default, moving to `n2-standard-16` if needed:

```
n2-standard-8  → n2-standard-16
  7000m/8Gi    →  14000m/16Gi   (defaults, snap-to)
  max: 7500m   →  max: 15000m
```

When a scale-up crosses into a larger pool, resources snap up to that pool's defaults to avoid
running an oversized node type at undersized allocation.

### CSV output

Results are written to `benchmark-results.csv` (append mode). Each run gets a row written
immediately after trigger (`Status=STARTED`), updated to `COMPLETED` or `ERROR` after collection.

| Column             | Description                                                            |
|--------------------|------------------------------------------------------------------------|
| `Run`              | Namespace name without the `c8-` prefix                               |
| `Timestamp`        | UTC time the run was triggered (ISO 8601)                              |
| `Status`           | `STARTED` → `COMPLETED` or `ERROR`                                    |
| `Target PI/s`      | Requested throughput                                                   |
| `Achieved PI/s`    | Measured PI/s from Grafana, or `TIMEOUT` / `GRAFANA_ERROR`            |
| `Achieved %`       | `achieved / target * 100`                                              |
| `Broker replicas`  | Number of broker pods                                                  |
| `Broker node`      | GKE node pool used for brokers                                         |
| `Broker CPU`       | CPU request/limit set on broker pods                                   |
| `Broker memory`    | Memory request/limit                                                   |
| `Broker heap`      | JVM heap (25% of memory)                                               |
| `Broker threads CPU` / `IO` | Zeebe thread counts                                        |
| `ES replicas`      | Elasticsearch node count                                               |
| `ES node`          | GKE node pool for ES                                                   |
| `ES CPU` / `ES memory` / `ES heap` | ES resource allocation                              |
| `Dropped req/s`    | Zeebe dropped request rate (backpressure indicator)                    |
| `Max in-flight req`| Maximum in-flight request count                                        |
| `ES export lag`    | Total un-exported log records across all partitions (position delta)   |
| `ES flush p99 (s)` | p99 ES exporter flush duration in seconds                              |
| `ES flush fail rate` | Fraction of ES flushes that failed (0.0–1.0)                         |
| `ES disk used %`   | Average PVC fill level across Elasticsearch nodes                      |
| `Backpressure drop %` | Average per-partition backpressure drop rate as % of received requests |
| `ES CPU throttle %` | Average CPU throttling % across Elasticsearch pods                    |
| `Camunda CPU throttle %` | Average CPU throttling % across broker/orchestration pods         |
| `Completed PI/s`   | Completed process instances per second                                 |
| `grafana_timestamp`| UTC time of the first Grafana query attempt (used for recovery)        |

At the start of each run, both the CSV and the log file are backed up with a timestamp suffix
(e.g. `benchmark-results.20260501_143022.csv`).

### Examples

```bash
# Auto-scale from 50 to 800 PI/s (typical scenario, no Optimize)
export GRAFANA_COOKIE="VouchCookie=..."
python3 run-benchmark.py --auto-scale

# Auto-scale with Optimize enabled
python3 run-benchmark.py --auto-scale --optimize

# Auto-scale with realistic scenario (forwarded to trigger script)
python3 run-benchmark.py --auto-scale --scenario realistic

# Auto-scale up to 400 PI/s only
python3 run-benchmark.py --auto-scale --max-rate 400

# Manual run at fixed rates
python3 run-benchmark.py --rates 100,250

# Manual run on a bigger node pool
python3 run-benchmark.py --rates 500 --broker-node-pool n2-standard-8

# Dry run — inspect trigger output, no deployment
python3 run-benchmark.py --auto-scale --dry-run

# Collect Grafana metrics for an already-running namespace
python3 run-benchmark.py --collect-only c8-ajanoni-05011234-noopt-typi-3b-100pis --rates 100

# Collect metrics for a namespace that has already been deleted (query at the time it was active)
python3 run-benchmark.py --collect-only c8-ajanoni-05011151-noopt-typi-12b-800pis --rates 800 \
  --brokers 12 --broker-node-pool n2-standard-16 \
  --at-time 2026-05-01T16:20:00Z

# Resume auto-scale after a crash at 200 PI/s (first scale-up attempt)
python3 run-benchmark.py --auto-scale --start-rate 200 --scale-steps 1

# Recover all GRAFANA_ERROR rows after refreshing the cookie
export GRAFANA_COOKIE="VouchCookie=<new-value>"
python3 run-benchmark.py --recover

# Write results to a custom CSV and log
python3 run-benchmark.py --auto-scale --output results/run-01.csv --log results/run-01.log
```

### Resuming after a crash

If the script crashes or is interrupted mid-run, use `--start-rate` and `--scale-steps` to resume
without re-running completed rates.

**Example:** The script crashed during the 200 PI/s attempt, which was already the 2nd try (first
scale-up applied). The CSV shows a `STARTED` or `ERROR` row for that attempt.

```bash
# Resume at 200 PI/s with resources already scaled up once
python3 run-benchmark.py --auto-scale --start-rate 200 --scale-steps 1
```

`--scale-steps N` pre-applies N `scale_up()` steps to the initial resources before the first run
at `--start-rate`. `N=0` means start from pool defaults; `N=1` means one scale-up already applied
(e.g. broker moved to n2-standard-8 at its default allocation).

### Recovering Grafana errors

When a Grafana query fails (network issue, expired cookie, proxy auth), the row is written with
`Status=ERROR` and `Achieved PI/s=GRAFANA_ERROR`. The `grafana_timestamp` column records when
the query was attempted, so Prometheus can be queried at the correct point in time during recovery.

```bash
# 1. Refresh your Grafana cookie in the browser
# 2. Export the new value
export GRAFANA_COOKIE="VouchCookie=<new-value>"

# 3. Re-query all GRAFANA_ERROR rows
python3 run-benchmark.py --recover
```

The recovery mode reads `grafana_timestamp` from each error row (falling back to `Timestamp`) and
issues a PromQL instant query at that UTC time. Successfully recovered rows are updated to
`Status=COMPLETED` in place.

Note: Prometheus has a finite retention window (~2 weeks by default on the benchmark cluster).
Recovery will not work for runs older than the retention period.
