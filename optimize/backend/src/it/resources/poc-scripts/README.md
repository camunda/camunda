# POC Benchmark Scripts

Three scripts for generating synthetic Zeebe data and benchmarking the Business Value Dashboard queries against Elasticsearch.

|     Script     |                                                      Purpose                                                       |
|----------------|--------------------------------------------------------------------------------------------------------------------|
| `ingestion.py` | Continuous incremental pipeline: reads variable records from Zeebe and upserts aggregated process instance metrics |
| `dashboard.py` | Query benchmark: runs the 5 dashboard queries in a loop and reports per-query timing                               |
| `gen_loop.py`  | Generator loop: repeatedly invokes `ZeebeDataGeneratorCli` as a subprocess to grow the synthetic dataset           |

---

## `ingestion.py`

A continuous data pipeline that reads process variables from `zeebe-record-variable` and aggregates them into `optimize-reporting-metrics`.

### What it does

On each round the script:
1. Fetches `REPORTING_PROCESS_*` variable records from `zeebe-record-variable` (source) that were written after the last known position
2. Groups them by process instance
3. Upserts aggregated process instance documents into `optimize-reporting-metrics` (destination)
4. Persists the latest position so the next run only processes new records

It loops continuously until `TOTAL_DURATION_S` is reached, sleeping between rounds when caught up.

### Configuration

All options are set at the top of `ingestion.py`:

|      Variable      |              Default               |                            Meaning                             |
|--------------------|------------------------------------|----------------------------------------------------------------|
| `ES_HOST`          | `http://localhost:9200`            | Elasticsearch URL                                              |
| `SOURCE_INDEX`     | `zeebe-record-variable`            | Source index containing Zeebe variable records                 |
| `DEST_INDEX`       | `optimize-reporting-metrics`       | Destination index for aggregated metrics                       |
| `RUN_EVERY_S`      | `60`                               | Seconds to sleep between rounds when no pending records remain |
| `TOTAL_DURATION_S` | `3600`                             | Maximum total runtime in seconds before the script exits       |
| `PAGE_SIZE`        | `1000`                             | Number of records fetched per scroll page                      |
| `STATE_FILE`       | `.reporting_metrics_position.json` | File used to persist the last processed position               |

### Running

```bash
pip install elasticsearch
python3 ingestion.py
```

Requires Elasticsearch running at `ES_HOST` with the source index populated by Zeebe or `gen_loop.py`.

### Resetting the position (full reprocess)

The script resumes from where it left off using `.reporting_metrics_position.json`. To reprocess all records from the beginning (e.g. after deleting and recreating the destination index), delete that file first:

```bash
rm .reporting_metrics_position.json
python3 ingestion.py
```

> **Warning:** without deleting the state file, re-running after a `DELETE optimize-reporting-metrics` will only import records written *after* the last saved position.

---

### Log output — glossary

#### Startup

```
Starting — interval 60s, total 3600s
State file: /path/to/.reporting_metrics_position.json
```

|     Term     |                                         Meaning                                         |
|--------------|-----------------------------------------------------------------------------------------|
| `interval`   | Seconds the script sleeps between rounds when no pending records remain (`RUN_EVERY_S`) |
| `total`      | Maximum seconds the script will run before exiting (`TOTAL_DURATION_S`)                 |
| `State file` | Path to the JSON file that stores the last processed position                           |

#### Round header

```
[Round 3] position=9380678
```

|    Term    |                                                                                 Meaning                                                                                 |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Round`    | Sequential round counter, incremented on every loop iteration                                                                                                           |
| `position` | The Elasticsearch `position` field value of the last record successfully processed in the previous round; only records with a higher position are fetched in this round |

#### Index stats

```
index stats (0.45s): live=1,234,567  deleted=45,678 (3.6% dirty)  size=2.3 GB  wasted≈82.5 MB
```

|    Term     |                                                                Meaning                                                                |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `live`      | Number of documents currently active and searchable in the index                                                                      |
| `deleted`   | Number of documents deleted or superseded but not yet reclaimed by Elasticsearch; invisible to queries but still consuming disk space |
| `dirty_pct` | `deleted / (live + deleted) * 100` — percentage of tracked document slots that are dead weight; high values indicate fragmentation    |
| `size`      | Total disk space used by the index, including live documents and unreclaimed tombstones                                               |
| `wasted`    | Estimated bytes consumed by deleted tombstones; freed once Elasticsearch runs a segment merge                                         |

#### Fetch phase

```
fetch : 2.34s — 5,642 REPORTING_PROCESS_* variable records from zeebe-record-variable in 6 page(s)
```

|         Term          |                                                     Meaning                                                      |
|-----------------------|------------------------------------------------------------------------------------------------------------------|
| `fetch`               | Phase where records are read from the source index using the Elasticsearch scroll API                            |
| `REPORTING_PROCESS_*` | Variable records whose `value.name` starts with the `REPORTING_PROCESS_` prefix; all other variables are ignored |
| `page(s)`             | Number of scroll API calls made; each page returns up to `PAGE_SIZE` (1 000) records                             |

#### Upsert phase

```
upsert: 1.12s — 845 PIs (300 created, 545 updated)
upsert: 1.12s — 845 PIs (300 created, 545 updated) ⚠ 2 errors
```

|     Term     |                                                           Meaning                                                            |
|--------------|------------------------------------------------------------------------------------------------------------------------------|
| `upsert`     | Phase where documents are written to the destination index; each operation inserts a new document or updates an existing one |
| `PIs`        | Process instances — one document per unique `processInstanceKey`                                                             |
| `created`    | Documents that did not exist and were newly inserted                                                                         |
| `updated`    | Documents that already existed and had one or more fields overwritten                                                        |
| `⚠ N errors` | Number of individual bulk operations that Elasticsearch rejected; the rest of the batch still succeeds                       |

#### Round completion

```
[Round 3] done in 3.82s — 845 PIs (221 PI/s), new position: 9386320
```

|      Term      |                                                          Meaning                                                           |
|----------------|----------------------------------------------------------------------------------------------------------------------------|
| `done in`      | Wall-clock time for the entire round (fetch + upsert + index stats)                                                        |
| `PI/s`         | Throughput: process instances upserted per second                                                                          |
| `new position` | The highest `position` value seen in this round; saved to the state file and used as the starting point for the next round |

#### Pending check

```
pending check: 0.18s — 2,341 variable records still pending in zeebe-record-variable
```

|      Term       |                                                    Meaning                                                     |
|-----------------|----------------------------------------------------------------------------------------------------------------|
| `pending`       | Variable records in the source index whose `position` is greater than the current position — not yet processed |
| `pending check` | A count query run after each round to decide whether to sleep or immediately start the next round              |

#### Scheduling decision

```
2,341 records still pending — running next round immediately
caught up — sleeping 60s...
```

|      Term       |                                           Meaning                                            |
|-----------------|----------------------------------------------------------------------------------------------|
| `still pending` | Unprocessed records exist; the next round starts without sleeping                            |
| `caught up`     | No pending records remain; the script sleeps for `RUN_EVERY_S` seconds before checking again |

---

### Destination document fields

Each document in `optimize-reporting-metrics` represents one process instance and maps directly from the `REPORTING_PROCESS_*` variable names in Zeebe.

|            Field            |  Type  |                Source variable                |
|-----------------------------|--------|-----------------------------------------------|
| `baselineCost`              | float  | `REPORTING_PROCESS_baselineCost`              |
| `llmCost`                   | float  | `REPORTING_PROCESS_llmCost`                   |
| `automationCost`            | float  | `REPORTING_PROCESS_automationCost`            |
| `totalCost`                 | float  | `REPORTING_PROCESS_totalCost`                 |
| `valueCreated`              | float  | `REPORTING_PROCESS_valueCreated`              |
| `agentTaskCount`            | int    | `REPORTING_PROCESS_agentTaskCount`            |
| `humanTaskCount`            | int    | `REPORTING_PROCESS_humanTaskCount`            |
| `autoTaskCount`             | int    | `REPORTING_PROCESS_autoTaskCount`             |
| `tokenUsage`                | int    | `REPORTING_PROCESS_tokenUsage`                |
| `processLabel`              | string | `REPORTING_PROCESS_processLabel`              |
| `startDate`                 | string | `REPORTING_PROCESS_startDate`                 |
| `endDate`                   | string | `REPORTING_PROCESS_endDate`                   |
| `errorCount`                | int    | `REPORTING_PROCESS_errorCount`                |
| `retryCount`                | int    | `REPORTING_PROCESS_retryCount`                |
| `processingTimeMs`          | int    | `REPORTING_PROCESS_processingTimeMs`          |
| `queueWaitTimeMs`           | int    | `REPORTING_PROCESS_queueWaitTimeMs`           |
| `apiCallCount`              | int    | `REPORTING_PROCESS_apiCallCount`              |
| `complianceChecksPassed`    | int    | `REPORTING_PROCESS_complianceChecksPassed`    |
| `dataVolumeMb`              | float  | `REPORTING_PROCESS_dataVolumeMb`              |
| `confidenceScore`           | float  | `REPORTING_PROCESS_confidenceScore`           |
| `co2EmissionsKg`            | float  | `REPORTING_PROCESS_co2EmissionsKg`            |
| `customerSatisfactionScore` | float  | `REPORTING_PROCESS_customerSatisfactionScore` |
| `fraudRiskScore`            | float  | `REPORTING_PROCESS_fraudRiskScore`            |
| `externalServiceCostUsd`    | float  | `REPORTING_PROCESS_externalServiceCostUsd`    |
| `slaBreached`               | bool   | `REPORTING_PROCESS_slaBreached`               |
| `escalated`                 | bool   | `REPORTING_PROCESS_escalated`                 |
| `manualOverride`            | bool   | `REPORTING_PROCESS_manualOverride`            |

---

## `dashboard.py`

Benchmarks the five Business Value Dashboard queries against Elasticsearch, printing per-query timing each round and a full summary on exit.

### Queries

|       Query        |            Index             |                                          Description                                          |
|--------------------|------------------------------|-----------------------------------------------------------------------------------------------|
| `kpi_unscoped`     | `optimize-reporting-metrics` | Aggregate totals (cost, value, tokens, task counts, flag counts) across all process instances |
| `kpi_scoped`       | `optimize-reporting-metrics` | Same aggregations filtered to the last 6 months via `lastSeenAt`                              |
| `top_processes`    | `optimize-reporting-metrics` | Top 10 processes by total cost, with per-process PI count, cost, value, and token sums        |
| `trend`            | `optimize-reporting-metrics` | Monthly date-histogram of cost, value, and PI count over the full history                     |
| `active_processes` | `optimize-process-instance`  | Count of process instances currently in `ACTIVE` state                                        |

`active_processes` targets `optimize-process-instance` because the reporting-metrics index only holds completed and terminated instances.

### Configuration

|      Variable      |           Default            |                    Meaning                    |
|--------------------|------------------------------|-----------------------------------------------|
| `ES_HOST`          | `http://localhost:9200`      | Elasticsearch URL                             |
| `METRICS_INDEX`    | `optimize-reporting-metrics` | Index for the four aggregation queries        |
| `PI_INDEX`         | `optimize-process-instance`  | Index for the active-processes count query    |
| `DEFAULT_INTERVAL` | `1`                          | Seconds between benchmark rounds              |
| `SCOPED_START`     | `now-6M/M`                   | Start of the date window used by `kpi_scoped` |
| `SCOPED_END`       | `now/M`                      | End of the date window used by `kpi_scoped`   |

### Running

```bash
pip install "elasticsearch>=8,<9"
python3 dashboard.py [--host localhost] [--port 9200] [--interval 1]
```

Press `Ctrl+C` to stop and print the full benchmark summary.

### Log output

Each round prints one line per query:

```
[Round 42]
  kpi_unscoped            0.008s  pi=2,914,999  cost=1,122,667,903  value=2,451,285,054  tokens=74,393,876,870
  kpi_scoped              0.006s  pi=2,914,999  cost=1,122,667,903  value=2,451,285,054  tokens=74,393,876,870
  top_processes           0.004s  6 process bucket(s)
  trend                   0.003s  7 month(s)  [2025-10..2026-04]
  active_processes        0.002s  active=81,302
  round time: 0.023s  |  sleeping 0.98s
```

On exit a summary table is printed with total, average, minimum, and maximum latency per query across all rounds.

---

## `gen_loop.py`

Wraps `ZeebeDataGeneratorCli` in a continuous Python loop. Each iteration runs the Java generator as a subprocess and streams its output live. Because the generator picks up from the last position and instance key in Elasticsearch, successive iterations layer new synthetic data on top of the existing dataset.

### Running

```bash
# Basic usage — uses ZeebeDataGeneratorCli defaults (300 000 instances, prefix zeebe-record)
python3 gen_loop.py

# Pass custom generator arguments after --
python3 gen_loop.py -- --instances 100000 --update-rate 0.25 --prefix zeebe-record

# Rebuild the module before starting, then pause 5s between runs
python3 gen_loop.py --build --pause 5 -- --instances 50000
```

### Options

|     Option      |       Default        |                                         Meaning                                          |
|-----------------|----------------------|------------------------------------------------------------------------------------------|
| `--project-dir` | auto-detected        | Path to the camunda monorepo root (walks up from the script location looking for `mvnw`) |
| `--mvnw`        | `<project-dir>/mvnw` | Path to the Maven wrapper                                                                |
| `--pause`       | `0`                  | Seconds to sleep between generator runs                                                  |
| `--build`       | false                | Rebuild `optimize/backend` with `-Dquickly` before starting the loop                     |

Everything after `--` is forwarded verbatim to `ZeebeDataGeneratorCli`. See that class's `--help` (or the generator README) for the full list of generator flags.

### Classpath resolution

On the first run, `gen_loop.py` calls `mvn dependency:build-classpath` to collect all jars required to run `ZeebeDataGeneratorCli` and caches the result in `/tmp/gen_loop_classpath.txt`. Subsequent runs reuse the cached file, skipping the Maven call entirely. Delete that file to force re-resolution after a dependency change.
