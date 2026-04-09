# Reporting Metrics Query

A continuous data pipeline that reads process variables from a source Elasticsearch index and aggregates them into a destination index for reporting.

## What it does

On each run, the script:
1. Fetches `REPORTING_PROCESS_*` variable records from `zeebe-record-variable` (source) that were written after the last known position
2. Groups them by process instance
3. Upserts aggregated process instance documents into `optimize-reporting-metrics` (destination)
4. Persists the latest position so the next run only processes new records

It loops continuously until `TOTAL_DURATION_S` is reached, sleeping between rounds when caught up.

## Configuration

All options are set at the top of `query.py`:

|      Variable      |              Default               |                            Meaning                             |
|--------------------|------------------------------------|----------------------------------------------------------------|
| `ES_HOST`          | `http://localhost:9200`            | Elasticsearch URL                                              |
| `SOURCE_INDEX`     | `zeebe-record-variable`            | Source index containing Zeebe variable records                 |
| `DEST_INDEX`       | `optimize-reporting-metrics`       | Destination index for aggregated metrics                       |
| `RUN_EVERY_S`      | `60`                               | Seconds to sleep between rounds when no pending records remain |
| `TOTAL_DURATION_S` | `3600`                             | Maximum total runtime in seconds before the script exits       |
| `PAGE_SIZE`        | `1000`                             | Number of records fetched per scroll page                      |
| `STATE_FILE`       | `.reporting_metrics_position.json` | File used to persist the last processed position               |

## Running

```bash
pip install elasticsearch
python query.py
```

Requires Elasticsearch running at `ES_HOST` with the source index populated by Zeebe.

### Resetting the position (full reprocess)

The script resumes from where it left off using `.reporting_metrics_position.json`. If you need to reprocess all records from the beginning (e.g. after deleting and recreating the destination index), delete that file before starting:

```bash
rm .reporting_metrics_position.json
python query.py
```

> **Warning:** without deleting the state file, re-running the script after a `DELETE optimize-reporting-metrics` will only import records written *after* the last saved position. Documents from earlier positions will be missing from the index.

The script prints a warning at startup if a state file is found, reminding you of this.

---

## Log output — glossary

Every term that appears in the logs is defined below.

### Startup

```
Starting — interval 60s, total 3600s
State file: /path/to/.reporting_metrics_position.json
```

|     Term     |                                         Meaning                                         |
|--------------|-----------------------------------------------------------------------------------------|
| `interval`   | Seconds the script sleeps between rounds when no pending records remain (`RUN_EVERY_S`) |
| `total`      | Maximum seconds the script will run before exiting (`TOTAL_DURATION_S`)                 |
| `State file` | Path to the JSON file that stores the last processed position                           |

---

### Round header

```
[Round 3] position=9380678
```

|    Term    |                                                                                 Meaning                                                                                 |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Round`    | Sequential round counter, incremented on every loop iteration                                                                                                           |
| `position` | The Elasticsearch `position` field value of the last record successfully processed in the previous round; only records with a higher position are fetched in this round |

---

### Index stats

Printed at the start and end of each round, and whenever the index is first created.

```
index stats (0.45s): live=1,234,567  deleted=45,678 (3.6% dirty)  size=2.3 GB  wasted≈82.5 MB
```

|    Term     |                                                                                            Meaning                                                                                             |
|-------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `index`     | The destination Elasticsearch index (`optimize-reporting-metrics`)                                                                                                                             |
| `live`      | Number of documents currently active and searchable in the index                                                                                                                               |
| `deleted`   | Number of documents that have been deleted or superseded but whose disk space has not yet been reclaimed by Elasticsearch (tombstones); these are invisible to queries but still consume space |
| `dirty_pct` | `deleted / (live + deleted) * 100` — the percentage of tracked document slots that are dead weight; a high value means the index is fragmented and a forcemerge would recover space            |
| `size`      | Total disk space used by the index, including both live documents and unreclaimed deleted tombstones                                                                                           |
| `wasted`    | Estimated bytes consumed by deleted tombstones (`size * deleted / (live + deleted)`); this space is not doing useful work and will be freed once Elasticsearch runs a segment merge            |

---

### Fetch phase

```
fetch : 2.34s — 5,642 REPORTING_PROCESS_* variable records from zeebe-record-variable in 6 page(s)
```

|          Term           |                                                     Meaning                                                      |
|-------------------------|------------------------------------------------------------------------------------------------------------------|
| `fetch`                 | The phase where records are read from the source index using the Elasticsearch scroll API                        |
| `REPORTING_PROCESS_*`   | Variable records whose `value.name` starts with the `REPORTING_PROCESS_` prefix; all other variables are ignored |
| `zeebe-record-variable` | The source Elasticsearch index written by Zeebe (the workflow engine)                                            |
| `page(s)`               | Number of scroll API calls made; each page returns up to `PAGE_SIZE` (1 000) records                             |

---

### Upsert phase

```
upsert: 1.12s — 845 PIs (300 created, 545 updated)
upsert: 1.12s — 845 PIs (300 created, 545 updated) ⚠ 2 errors
```

|     Term     |                                                                       Meaning                                                                       |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `upsert`     | The phase where documents are written to the destination index; each operation inserts a new document or updates an existing one (insert-or-update) |
| `PIs`        | Process instances — one document per unique `processInstanceKey`                                                                                    |
| `created`    | Documents that did not exist in the destination index and were newly inserted                                                                       |
| `updated`    | Documents that already existed and had one or more fields overwritten with new values                                                               |
| `⚠ N errors` | Number of individual bulk operations that Elasticsearch rejected; the rest of the batch still succeeds                                              |

---

### Round completion

```
[Round 3] done in 3.82s — 845 PIs (221 PI/s), new position: 9386320
```

|      Term      |                                                          Meaning                                                           |
|----------------|----------------------------------------------------------------------------------------------------------------------------|
| `done in`      | Wall-clock time for the entire round (fetch + upsert + index stats)                                                        |
| `PI/s`         | Throughput: process instances upserted per second                                                                          |
| `new position` | The highest `position` value seen in this round; saved to the state file and used as the starting point for the next round |

---

### Pending check

```
pending check: 0.18s — 2,341 variable records still pending in zeebe-record-variable
```

|      Term       |                                                           Meaning                                                            |
|-----------------|------------------------------------------------------------------------------------------------------------------------------|
| `pending`       | Variable records in the source index whose `position` is greater than the current position — i.e., records not yet processed |
| `pending check` | A count query run after each round to decide whether to sleep or immediately start the next round                            |

---

### Scheduling decision

```
2,341 records still pending — running next round immediately
```

```
caught up — sleeping 60s...
```

|      Term       |                                           Meaning                                            |
|-----------------|----------------------------------------------------------------------------------------------|
| `still pending` | Unprocessed records exist; the next round starts without sleeping                            |
| `caught up`     | No pending records remain; the script sleeps for `RUN_EVERY_S` seconds before checking again |

---

### Shutdown

```
Total duration reached (3600s). Stopping.
Finished. Total time: 3601.23s across 47 round(s).
```

The script exits cleanly once the total duration limit is reached. The state file retains the last position so the next invocation continues from where it stopped.

---

## Destination document fields

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

