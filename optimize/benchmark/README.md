# Optimize Execution Plan Benchmark

Benchmarks all 111 `ProcessExecutionPlan` entries end-to-end against the
Optimize REST API across three synthetic dataset sizes (S / M / L).

Produces a ranked CSV showing P50/P95 latency per plan per dataset, with
GREEN / YELLOW / RED verdicts — plans that are RED at M scale are removal
candidates.

## Prerequisites

| Requirement | Notes |
|---|---|
| Python 3.10+ | `pip install requests` |
| Running Optimize instance | Default: `http://localhost:8090` |
| Running Elasticsearch | Default: `http://localhost:9200` |
| Camunda monorepo built | Only needed for the `seed` phase |

## Quick Start

```bash
cd optimize/benchmark

# Full run: seed data → create reports → evaluate all plans
python bench_optimize.py

# Results land in results.csv (sorted by p95 at M descending)
```

## Phases

The script is split into three phases you can run independently.
This matters because seeding 1M instances takes time — you don't want
to re-seed just to re-run evaluation.

### 1. `seed` — populate Elasticsearch

Calls the Java `ZeebeDataGeneratorCli` for each dataset size.
Flushes and force-merges ES between sizes to avoid cache bleed.
Then waits for Optimize to finish importing.

**Before running seed for the first time, compile IT sources:**
```bash
./mvnw -pl optimize/backend test-compile -Dquickly -T1C
```

Then seed all three datasets:
```bash
python bench_optimize.py --phase seed
```

Or seed a single dataset:
```bash
python bench_optimize.py --phase seed --dataset M
```

Dataset sizes:

| Size | Instances | Process Defs |
|------|-----------|--------------|
| S    | 10,000    | 3            |
| M    | 100,000   | 6            |
| L    | 1,000,000 | 6            |

### 2. `create-reports` — create all 111 reports in Optimize

POSTs one saved report per plan via `POST /api/report/process/single`.
Report IDs are written to `report_ids.json` after each creation — if
you re-run, already-created reports are skipped.

```bash
python bench_optimize.py --phase create-reports
```

### 3. `evaluate` — measure latency

For each `plan × dataset`: runs 5 warmup calls (discarded, to warm JIT
and ES caches) then 10 measured calls. Records P50, P95, HTTP status,
and verdict. Results are appended to `results.csv` after each plan so a
crash loses at most one row — re-running resumes from where it left off.

```bash
python bench_optimize.py --phase evaluate
python bench_optimize.py --phase evaluate --dataset M   # one size only
```

## Output Files

| File | Description |
|---|---|
| `report_ids.json` | Map of plan name → Optimize report ID. Written by `create-reports`, read by `evaluate`. |
| `results.csv` | Benchmark results, one row per plan × dataset. |

### CSV columns

| Column | Description |
|---|---|
| `plan` | `ProcessExecutionPlan` enum name |
| `dataset` | S, M, or L |
| `p50_ms` | Median wall-clock response time (ms) |
| `p95_ms` | 95th-percentile wall-clock response time (ms) |
| `es_took_ms` | ES-side took from response body (-1 if unavailable) |
| `status` | HTTP status code of the last measured call |
| `cost` | Theoretical cost tier: TRIVIAL / LOW / MEDIUM / HIGH |
| `verdict` | GREEN / YELLOW / RED |

### Verdict thresholds

| Verdict | Condition |
|---|---|
| GREEN | p95 < 2s at M **and** L/M growth factor < 10x |
| YELLOW | p95 2–5s at M **or** growth factor 10–15x |
| RED | p95 > 5s at M **or** growth factor > 15x |

RED plans at M scale are primary removal candidates.

## All Options

```
python bench_optimize.py --help
```

| Flag | Default | Description |
|---|---|---|
| `--phase` | `all` | `all`, `seed`, `create-reports`, or `evaluate` |
| `--dataset` | `all` | `S`, `M`, `L`, or `all` |
| `--optimize-url` | `http://localhost:8090` | Optimize base URL |
| `--es-url` | `http://localhost:9200` | ES base URL (for flush/merge) |
| `--es-host` | `localhost` | ES host passed to the Java generator |
| `--es-port` | `9200` | ES port passed to the Java generator |
| `--es-user` | _(none)_ | ES basic-auth username |
| `--es-password` | _(none)_ | ES basic-auth password |
| `--auth` | `demo:demo` | Optimize credentials as `user:password` |
| `--repo-root` | _(auto)_ | Path to the monorepo root (for `mvnw`) |
| `--ids-file` | `report_ids.json` | Where to persist report IDs |
| `--output` | `results.csv` | CSV output path |
| `--warmups` | `5` | Warmup calls per report (discarded) |
| `--measured` | `10` | Measured calls per report |
| `--import-wait` | `120` | Max seconds to wait for Optimize import after seeding |

## Common Workflows

```bash
# Re-evaluate M only (data already seeded, reports already created)
python bench_optimize.py --phase evaluate --dataset M

# Run against a remote Optimize instance
python bench_optimize.py \
  --optimize-url http://optimize.internal:8090 \
  --es-url http://es.internal:9200 \
  --auth admin:secret

# Increase measured calls for more stable P95
python bench_optimize.py --phase evaluate --measured 20

# Wipe reports and start fresh
rm report_ids.json results.csv
python bench_optimize.py --phase create-reports
python bench_optimize.py --phase evaluate
```

## Cost Tiers

Each plan is pre-classified by its expected ES query complexity:

| Tier | What it means |
|---|---|
| TRIVIAL | Single number result — baseline check |
| LOW | Date histogram, single series |
| MEDIUM | HYPER_MAP or flow-node / assignee terms agg |
| HIGH | VARIABLE in groupBy or distributedBy — nested doc traversal |

`DISTRIBUTED_BY_VARIABLE` plans (HIGH tier, nested agg as outer wrapper)
are the primary removal candidates and are watched most closely.
