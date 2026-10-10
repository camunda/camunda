# Load test control

`loadtestctl` is a helper CLI for controlling and operating on load tests. Example use cases include extracting and aggregating metrics from load tests, opening dashboards, reviewing logs, obtaining authentication details, and more.

## Setup

This project uses the [`uv` package manager](https://docs.astral.sh/uv/).

To install it, run the following (see [the official installation guide](https://docs.astral.sh/uv/getting-started/installation/) for more options):

```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
uv --version
```

The project dependencies are declared in [`pyproject.toml`](pyproject.toml) and locked
in [`uv.lock`](uv.lock). Keep `uv.lock` committed so local runs and CI use the same
resolved dependency graph.

## Usage

```bash
uv run loadtestctl --help
```

## Features

### report

`loadtestctl report` builds a wide report for one load-test namespace by querying
Prometheus. It emits JSON for structured processing and CSV or TSV for spreadsheet
imports.

```bash
uv run loadtestctl report <namespace> [options]
```

Common options:

- `-d`, `--duration-seconds`, `--duration <sec>`: query window duration. Default: `600`.
- `-r`, `--rate-interval`, `--rate <dur>`: short Prometheus rate interval for dashboard-style rollups.
  Default: `5m`.
- `-s`, `--sample-step`, `--step <dur>`: sample resolution for window summaries. Default: `1m`.
- `-q`, `--queries <path>`: YAML query file path. Default: packaged
  `report-queries.yaml`.
- `--start <time>`: start of the reporting window as an RFC3339 or Unix timestamp. The
  duration is added to it to derive the end time, which must not be in the future.
  Default: now minus `--duration-seconds`, so the window ends now.
- `-e`, `--endpoint <url>`: Prometheus base URL. Default: `http://localhost:9090`.
- `-u`, `--user <user>` and `-p`, `--password <password>`: basic auth credentials for Prometheus.
- `-f`, `--format json|csv|tsv`: output format. Default: `json`.
- `--no-header`: omit the CSV or TSV header row for direct spreadsheet row pasting.
- `--missing-value <value>`: placeholder for missing CSV or TSV metrics. Default:
  `NaN`.
- `-o`, `--output <path>`: write the report to a file.

#### Environment variables

Every option can also be set through an environment variable named
`LOADTESTCTL_REPORT_<OPTION>`, for example `LOADTESTCTL_REPORT_DURATION_SECONDS`,
`LOADTESTCTL_REPORT_ENDPOINT`, `LOADTESTCTL_REPORT_USER` and
`LOADTESTCTL_REPORT_PASSWORD`. A flag on the command line overrides
the variable, and an empty variable counts as unset. Prefer `LOADTESTCTL_REPORT_PASSWORD` over `--password`, because command line
arguments are visible in the process list and the shell history. `uv run loadtestctl report
--help` lists the variable next to each option.

#### Examples

Port-forwarded Prometheus, JSON:

```bash
kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090
cd load-tests/loadtestctl
uv run loadtestctl report c8-ck-baseline-20260814 --duration-seconds 1800
```

Exact historical window, TSV row ready to paste into a spreadsheet:

```bash
uv run loadtestctl report c8-ck-baseline-20260814 \
  --start 2026-08-14T10:00:00Z \
  --duration-seconds 1800 \
  --format tsv --no-header
```

Use `--missing-value null` if your spreadsheet should show `null` instead of `NaN` for
missing metrics.

8.7-style Zeebe broker plus Zeebe Gateway layout:

```bash
uv run loadtestctl report c8-ck-base-8736-endurance \
  --queries report-queries-stable-87.yaml \
  --start 2026-08-12T09:00:00Z \
  --duration-seconds 79200 \
  --format tsv --no-header
```

CI monitor ingress with basic auth:

```bash
uv run loadtestctl report c8-ck-baseline-20260814 \
  --duration-seconds 1800 \
  --endpoint https://ci-monitor.benchmark.camunda.cloud \
  --user "$PROM_USER" \
  --password "$PROM_PASS" \
  --format csv > /tmp/load-test-report.csv
```

#### Queries

##### Query selection

Use `--queries` to select a YAML query file. The default is the packaged
[`report-queries.yaml`](src/loadtestctl/report/report-queries.yaml), which covers the
current Camunda 8.8+ orchestration cluster layout plus common metrics.

The package also includes
[`report-queries-stable-87.yaml`](src/loadtestctl/report/report-queries-stable-87.yaml)
for the Camunda 8.7 Zeebe broker and gateway layout. Select it with:

```bash
uv run loadtestctl report c8-ck-base-8736-endurance \
  --queries report-queries-stable-87.yaml
```

Custom files use the same top-level `queries:` schema.

The custom file case is useful when a report needs its own column set or custom PromQL queries.

##### Output shape

CSV and TSV column order follow the selected query file. The packaged query sets are
laid out for spreadsheet imports:

1. namespace and Docker image
2. cluster size
3. Camunda or Zeebe resources
4. secondary-storage resources
5. throughput
6. latency
7. backlog

Metrics that Prometheus does not return stay visible as `null` in JSON and `NaN` in CSV or TSV
by default.

##### Time windows and sampling

Three options control different time ranges when a query summarizes values across the
report window:

```text
Report window: 30 minutes                         --duration-seconds
|
+-- evaluate every 1 minute                       --sample-step
|   +-- at 10:01, calculate rate over 09:56-10:01 --rate-interval
|   +-- at 10:02, calculate rate over 09:57-10:02
|   `-- ...
|
`-- summarize approximately 31 values with p50, p99, or an average
```

- `--duration-seconds` defines the complete period summarized by the report.
- `--sample-step` controls how often a subquery evaluates an expression within that
  period. A smaller step preserves more temporal detail but requires more Prometheus
  computation.
- `--rate-interval` is the lookback used by `rate()` to calculate one value from a
  counter. It is not the sampling frequency. A longer interval produces a smoother
  rate, while a shorter interval reacts faster but must still contain enough Prometheus
  scrape samples.

Raw gauges can be summarized directly from their Prometheus scrape samples. Calculated
gauge expressions, such as partition backlog, use `--sample-step` to evaluate the
expression repeatedly. Counter queries either calculate one rate over the complete
report window or, for time summaries, calculate rates over `--rate-interval` and use
`--sample-step` to evaluate them across the report window.

The rate interval and sample step may be equal. This produces mostly non-overlapping
rate observations and reduces query cost, but a long interval then produces fewer
observations for p50 or p99. The defaults calculate a five-minute moving rate every
minute, combining a stable rate with finer temporal resolution.

##### Packaged query semantics

The report window ends with `--start` plus `--duration-seconds`, which is now when `--start` is omitted.

Different column types use that window differently:

- Label columns, such as `namespace` and `docker_image`, read labels from series
  present during the window. This keeps deleted namespaces reportable while the
  historical series remains in Prometheus retention.
- Resource gauges, such as pod counts, CPU limits, memory limits, disk capacity, disk
  usage, heap, and RSS, use a max over the window so a restart or deleted namespace does
  not hide the value.
- CPU usage p50 and p99 calculate rates over `--rate-interval`, evaluate them every
  `--sample-step`, and summarize those values with `quantile_over_time`.
- Average-rate columns, such as throughput, CPU throttling, write IOPS, backpressure,
  calculate each rate over `--rate-interval` and average evaluations taken every
  `--sample-step`.
- Backlog columns do not use `--rate-interval`. They evaluate derived gauge differences
  every `--sample-step` and average those values across the report window.
- Backpressure and backlog first pick the highest partition value at each sample, then
  average those sampled maxima over the window.
- Columns computed from seconds-denominated histograms but labeled in milliseconds,
  such as `ProcLat p50 (ms)`, multiply the PromQL result by `1000`.

##### Query file schema

Each query entry is one report column, in emission order. The order controls the JSON
key order and the CSV or TSV column order.

Fields:

- `key`: machine key, used as the JSON `metrics` object key and to preserve column
  order in CSV or TSV output.
- `description`: human-readable explanation of what the column measures.
- `header`: human column label for CSV or TSV output.
- `query`: PromQL query evaluated against Prometheus.
- `valueLabel`: Prometheus label to extract instead of the sample value. Optional.

Packaged YAML entries keep the field order `key`, `description`, `header`, then
`valueLabel` when needed, then `query`.

##### Query substitutions

Variables are substituted in the raw query file before YAML decoding:

- `$NAMESPACE`: exact load-test namespace, for example `c8-ck-baseline-20260814`.
- `$DURATION_S`: report window duration with an `s` suffix, for example `600s`.
- `$RATE_INTERVAL`: `--rate-interval` lookback used when calculating counter rates.
- `$SAMPLE_STEP`: `--sample-step` evaluation resolution used by subqueries, for example
  in `quantile_over_time` or `avg_over_time`.

## Development

### Test

Format Python files:

```bash
make format
```

Run mypy type checks:

```bash
make type-check
```

Run mypy, Ruff linting, and formatting checks:

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

