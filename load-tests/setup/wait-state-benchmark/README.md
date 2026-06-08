# Wait-State Benchmark Setup

Helm value overrides for the four wait-state benchmark scenarios described in issue #52037.
Apply on top of the standard `setup/default/` values when triggering a load test.

## How to run

Each scenario is a single Helm override file. Pass it as an extra `-f` argument after the
default values:

```bash
# Start a namespace using the standard script, then apply the scenario override:
. ./newLoadTest.sh wait-state-s1 elasticsearch 2

# Inside the generated namespace folder, install with the scenario overlay:
helm upgrade --install <release> <chart> \
  -f ../default/values/camunda-platform-values-defaults.yaml \
  -f ../default/values/camunda-platform-values-elasticsearch.yaml \
  -f ../default/values/load-test-values.yaml \
  -f ../wait-state-benchmark/<scenario>.yaml
```

Alternatively trigger via the GitHub Actions `camunda-load-test.yml` workflow using the
`extra-values` input to pass the scenario file path.

---

## Scenarios

### Scenario 1 — Baseline (wait states disabled)
**File:** `scenario-1-baseline.yaml`

Standard 300 PI/s single-task load with wait states **disabled**. This is the control
run — no wait-state writes happen. Run this first to establish the unaffected throughput
numbers before enabling the feature.

Read queries: disabled (wait-state table is empty; querying it yields no signal).

### Scenario 1b — Wait states enabled, no read queries
**File:** `scenario-1b-enabled.yaml`

Identical to Scenario 1 except `wait-states.enabled=true`. Measures the pure exporter
overhead (2 extra secondary-storage ops per PI: INSERT on CREATED, DELETE on COMPLETED)
against the baseline throughput numbers.

Read queries: disabled (isolates write overhead).

### Scenario 2 — Query latency under load
**File:** `scenario-2-query-latency.yaml`

Same 300 PI/s single-task load, wait states enabled, **read benchmarks enabled**. The
three wait-state queries (`wait_state_by_process_instance_key`, `wait_state_by_element_type`,
`wait_state_all`) run every 30 s alongside the standard query set. Measures p50/p99 query
latency and whether periodic reads noticeably affect PI throughput.

### Scenario 3 — Accumulation under slow workers
**File:** `scenario-3-accumulation.yaml`

300 PI/s, wait states enabled, worker completion delay raised to **5 s** (vs the default
300 ms). Wait states accumulate because jobs sit active much longer. Read benchmarks
enabled to track how query latency evolves as the table grows. Run for at least 60 min.

Key question: does `wait_state_all` (broad scan) degrade as row count climbs?

### Scenario 4 — Write amplification (4× parallel tasks)
**File:** `scenario-4-parallel-tasks.yaml`

Same 300 PI/s but each PI runs **four parallel service tasks** (`four_parallel_tasks.bpmn`).
4× more wait-state writes per PI (1 200 INSERTs/s + 1 200 DELETEs/s at 300 PI/s).
Wait states enabled, read queries disabled (isolates write amplification).
