# Load test report

`load-test-report` builds a wide report for one load-test namespace by querying
Prometheus. It emits JSON for structured processing and CSV or TSV for spreadsheet
imports.

## Setup

Install `uv` once:

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
uv --version
```

If `uv` is not on your `PATH` after installation, restart your shell or add the
installation directory printed by the installer. A Python-packaged fallback also works:

```bash
python3 -m pip install --user uv
uv --version
```

The project dependencies are declared in [`pyproject.toml`](pyproject.toml) and locked
in [`uv.lock`](uv.lock). Keep `uv.lock` committed so local runs and CI use the same
resolved dependency graph.

## Test

Format Python files:

```bash
make format
```

Run Ruff linting and formatting checks:

```bash
make lint
```

Run the unit tests:

```bash
make test
```

Run the same target CI uses:

```bash
make check
```

## Usage

```bash
uv run load-test-report <namespace> [options]
```

Common options:

- `--duration-seconds <sec>`: query window duration. Default: `600`.
- `--rate-interval <dur>`: short Prometheus rate interval for dashboard-style rollups.
  Default: `5m`.
- `--sample-step <dur>`: sample resolution for window summaries. Default: `1m`.
- `--queries <path>`: YAML query file path. Default: packaged
  `report-queries.yaml`.
- `--at <time>`: Prometheus query time anchor. The report window ends at this RFC3339
  or Unix timestamp.
- `--start <time> --end <time>`: exact reporting window. The duration is derived from
  the two timestamps.
- `--endpoint <url>`: Prometheus base URL. Default: `http://localhost:9090`.
- `--token <token>`: bearer token for Prometheus.
- `--user <user> --password <password>`: basic auth credentials for Prometheus.
- `--format json|csv|tsv`: output format. Default: `json`.
- `--no-header`: omit the CSV or TSV header row for direct spreadsheet row pasting.
- `--missing-value <value>`: placeholder for missing CSV or TSV metrics. Default:
  `NaN`.
- `--output <path>`: write the report to a file.

## Query selection

Use `--queries` to select a YAML query file. The default is the packaged
[`report-queries.yaml`](src/load_test_report/report-queries.yaml), which covers the
current Camunda 8.8+ orchestration cluster layout plus common metrics.

The package also includes
[`report-queries-stable-87.yaml`](src/load_test_report/report-queries-stable-87.yaml)
for the Camunda 8.7 Zeebe broker and gateway layout. Select it with:

```bash
uv run load-test-report c8-ck-base-8736-endurance \
  --queries report-queries-stable-87.yaml
```

Custom files use the same top-level `queries:` schema.

The custom file case is useful when a report needs its own column set or PromQL, for
example a future daily load-test report.

## Output shape

CSV and TSV column order follows the selected query file. The packaged query sets are
laid out for spreadsheet imports:

1. namespace and Docker image
2. cluster size
3. Camunda or Zeebe resources
4. secondary-storage resources
5. throughput
6. latency
7. backlog

Write IOPS columns appear after disk usage for each storage-backed component. Metrics
that Prometheus does not return stay visible as `null` in JSON and `NaN` in CSV or TSV
by default.

## Query window semantics

The report window ends at `--at`, at `--end` when `--start --end` are provided, or at
Prometheus's current evaluation time.

Different column types use that window differently:

- Label columns, such as `namespace` and `docker_image`, read labels from series
  present during the window. This keeps deleted namespaces reportable while the
  historical series remains in Prometheus retention.
- Resource gauges, such as pod counts, CPU limits, memory limits, disk capacity, disk
  usage, heap, and RSS, use a max over the window so a restart or deleted namespace does
  not hide the value.
- CPU usage p50 and p99 use `rate()` samples from `--rate-interval`, then summarize
  those samples over the window with `quantile_over_time`.
- Average-rate columns, such as throughput, CPU throttling, write IOPS, backpressure,
  and backlog, use `rate()` or sampled values at `--rate-interval`, then average those
  samples over the window.
- Backpressure and backlog first pick the highest partition value at each sample, then
  average those sampled maxima over the window.
- Columns computed from seconds-denominated histograms but labeled in milliseconds,
  such as `ProcLat p50 (ms)`, multiply the PromQL result by `1000`.

## Query file schema

Each query entry is one report column, in emission order. The order controls the JSON
key order and the CSV or TSV column order.

Fields:

- `key`: machine key, used as the JSON `metrics` object key and CSV or TSV column
  identifier.
- `description`: human-readable explanation of what the column measures.
- `header`: human column label for CSV or TSV output.
- `query`: PromQL query evaluated against Prometheus.
- `valueLabel`: Prometheus label to extract instead of the sample value. Optional.

Packaged YAML entries keep the field order `key`, `description`, `header`, then
`valueLabel` when needed, then `query`.

## Query substitutions

Variables are substituted in the raw query file before YAML decoding:

- `$NAMESPACE`: exact load-test namespace, for example `c8-ck-baseline-20260814`.
- `$DURATION_S`: report window duration with an `s` suffix, for example `600s`.
- `$RATE_INTERVAL`: short `--rate-interval` used for dashboard-style rate samples.
- `$SAMPLE_STEP`: `--sample-step` subquery resolution used for window summaries, for
  example in `quantile_over_time` or `avg_over_time`.

## Examples

Port-forwarded Prometheus, JSON:

```bash
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
cd load-tests/docs/scripts/load_test_report
uv run load-test-report c8-ck-baseline-20260814 --duration-seconds 1800
```

Exact historical window, TSV row ready to paste into a spreadsheet:

```bash
uv run load-test-report c8-ck-baseline-20260814 \
  --start 2026-08-14T10:00:00Z \
  --end 2026-08-14T10:30:00Z \
  --format tsv --no-header
```

Use `--missing-value null` if your spreadsheet should show `null` instead of `NaN` for
missing metrics.

8.7-style Zeebe broker plus Zeebe Gateway layout:

```bash
uv run load-test-report c8-ck-base-8736-endurance \
  --queries report-queries-stable-87.yaml \
  --start 2026-08-12T09:00:00Z \
  --end 2026-08-13T06:00:00Z \
  --format tsv --no-header
```

CI monitor ingress with basic auth:

```bash
uv run load-test-report c8-ck-baseline-20260814 \
  --duration-seconds 1800 \
  --endpoint https://ci-monitor.benchmark.camunda.cloud \
  --user "$PROM_USER" \
  --password "$PROM_PASS" \
  --format csv > /tmp/load-test-report.csv
```

