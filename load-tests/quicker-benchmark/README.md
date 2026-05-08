# Quicker Benchmark

A finite, label-triggered load test that gives PR authors a comparable performance signal
within ~10 minutes, posted as a comment on the PR.

Compared to the existing `benchmark` label (indefinite max load + flamegraphs), the quicker
benchmark runs one of the shared scenarios but stops cleanly after a configured duration,
queries throughput + engine-side metrics from the cluster's central Prometheus via PromQL,
and posts a table comparing the run against a static baseline.

One scenario is supported:

|     Label     | Scenario in `camunda-load-test.yml` |                                     What it tests                                      |
|---------------|-------------------------------------|----------------------------------------------------------------------------------------|
| `quicker-max` | `max` (rate=300, platform tuning)   | Saturating throughput. Goal: 300 PI/s submitted ≈ 300 PI/s completed, no backpressure. |

The `benchmark` label is independent and can coexist with `quicker-max` on the same PR.

The platform takes ~7min to come up; the default 10min duration leaves ~3min of usable
load. Bump `duration-seconds` via `workflow_dispatch` for longer runs.

This feature is **self-contained** — it does not modify
[`load-tests/setup/default/Makefile`](../setup/default/Makefile) or
[`.github/workflows/camunda-load-test.yml`](../../.github/workflows/camunda-load-test.yml).
Instead, it calls the existing reusable workflow with the matching `scenario` and passes
`--set starter.durationLimit=<seconds>` through the `load-test-load` input. Helm honors
the last `--set`, so the duration override wins over the scenario's defaults.

## Triggering

| Trigger |                                                                                          How                                                                                           |
|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| On a PR | Apply `quicker-max`. A push to the PR re-runs the test. Removing the label tears down the namespace.                                                                                   |
| Ad-hoc  | Run the `Camunda Pull Request Quicker Benchmark` workflow via `workflow_dispatch`. Inputs: `duration-seconds` (default 600), `pr-number` (when set, the comment is posted on that PR). |

## How completion is detected

Spring Boot's WebFlux server keeps the starter JVM alive after the creation loop ends, so
the pod stays `Running`. Instead of waiting on pod phase, the workflow:

1. Sleeps for `duration-seconds` once the platform is ready — the starter is configured
   with `--set starter.durationLimit=$duration_seconds`, so the gauge can't flip earlier
   anyway. This avoids burning kubectl/curl traffic during the run.
2. After the sleep, polls `starter_run_finished{namespace="..."}` via PromQL against the
   cluster's central Prometheus over its LDAP-protected ingress at
   `https://ci-monitor.benchmark.camunda.cloud` (creds imported from Vault). The starter
   flips the gauge to `1` when the loop exits; Prometheus picks it up on the next scrape
   (~30s). Polling deadline is `duration-seconds + 300s` — enough to absorb one full
   restart's worth of extra runtime.

If k8s restarts the starter pod mid-run, the new pod runs another full `duration-limit`
from scratch — total wall-clock time exceeds what the comment header says, and submitted-
instance counts double-count the restart window. The workflow surfaces a ⚠️ warning with
the restart count when this happens; we don't try to recover. For the 10-minute default
this is rare enough to accept.

## How metrics are collected

End-of-run metrics are pulled from the cluster's central Prometheus, exposed on the
LDAP-protected ingress at `https://ci-monitor.benchmark.camunda.cloud`. The workflow:

1. Calls the shared [`./.github/actions/await-load-test`](../../.github/actions/await-load-test/action.yml)
   composite. It waits for `app=camunda-platform` and
   `app.kubernetes.io/component=zeebe-client` pods to be Ready (with pod-reschedule
   retries) and then verifies gateway connectivity via the `clients` service's
   `app_connected` metric.
2. Imports `PROM_USER` / `PROM_PASS` from Vault
   (`secret/data/common/ldap/ci-infra`) via `hashicorp/vault-action`.
3. Sleeps for `duration-seconds` (the starter can't flip its `run_finished` gauge before
   then anyway).
4. Polls `starter_run_finished{namespace="..."}` via authenticated `curl` against
   `$PROM_URL/api/v1/query` (~30s scrape latency, accepted trade for code simplicity)
   until it flips to `1` or the deadline (`duration + 300s`) expires.
5. Reads the starter pod's restart count via `kubectl` (used to surface a ⚠️ in the
   comment if k8s restarted the starter mid-run — wall-clock time then exceeds the
   configured duration and the comparison is no longer apples-to-apples).
6. Runs [`validate-queries.sh`](validate-queries.sh) twice:
   - Once against the PR's namespace, writing `{name: number_or_null, ...}`
     to `/tmp/results.json`.
   - Once against the regex `c8-medic-daily-.*-test`, writing the same shape
     to `/tmp/results-daily.json`. This picks up the most recent active daily
     run on `main` (see `.github/workflows/camunda-daily-load-tests.yml`),
     queried over the same `$DURATION_S` window so the numbers are
     apples-to-apples.
7. Renders the comment table from `queries.yaml` × `/tmp/results.json` ×
   `/tmp/results-daily.json` × `optimal.json`.

> Sidenote: the shared `await-load-test` composite has a known `pipefail` issue — its
> first scrape during Spring actuator startup can return curl exit 52, which kills the
> step before its retry loop iterates. Daily/weekly tests don't trip it because they
> reach the action later in their boot sequence. We're tracking a standalone fix; if it
> bites a cold deploy here, the readiness step fails cleanly and a relabel/resync re-runs.

## What the comment shows

A table with three reference columns per metric:

| Column  |                                    Source                                    |
|---------|------------------------------------------------------------------------------|
| Current | This PR's run                                                                |
| Daily   | Most recent active daily run on `main` (regex match `c8-medic-daily-*-test`) |
| Optimal | Static aspirational target from [`optimal.json`](optimal.json)               |

The verdict (✅/⚠️/❔) compares **Current vs Daily** so the signal is
"did this PR regress vs main today?" Tolerance bands come from `optimal.json`'s
`tolerance-percent`. If the Daily column is empty (no active daily in the
lookback window), the row renders `-` for Daily and ❔ for the verdict.

Today's metric set:

- Process instances submitted by the starter (`starter_process_instances_started_total`)
- Root process instances completed by the engine (`zeebe_executed_instances_total{action="completed", type="ROOT_PROCESS_INSTANCE"}`)
- Submission throughput, avg (PI/s)
- Completion rate, avg (PI/s)
- Gateway-accepted submission rate (gRPC `or` HTTP)
- Completion ratio — fraction of gateway-accepted submissions that completed end-to-end
- Backpressure as % of received requests (`dropped / received` over the run)
- Data availability latency (P99)
- Request-response latency (P99) — round-trip from starter to gateway-response

Each row is annotated:
- ✅ within the configured tolerance (or beyond it on the *better* side)
- ⚠️ outside tolerance on the *worse* side (regression)
- ❔ metric was missing/NaN, or no Daily reference was available

The comment also links to the live Grafana dashboard for the namespace and to the workflow
run for details.

## Adding or changing a metric

The set of metrics is **data-driven** from [`queries.yaml`](queries.yaml). To add one:

1. Append a new entry to `queries.yaml` with a unique `name`, the PromQL `query`,
   `higher_is_better`, `format` (`integer` / `float` / `percent`), and optionally
   `decimals` and `unit`. Use `$NAMESPACE` and `$DURATION_S` as template variables —
   `validate-queries.sh` substitutes them before sending to Prometheus. The
   query must use `namespace=~"$NAMESPACE"` (regex match) so the same query
   targets both a single PR namespace and the daily regex pattern.
2. Add a matching key under `metrics` in `optimal.json`:
   - For **count-shaped** metrics (anything that grows linearly with the run
     window — e.g. `process-instances-started`), use `per-second` as the
     value. The renderer multiplies by `duration-seconds` so the Optimal
     column scales correctly when `workflow_dispatch` overrides the duration.
   - For **rate / ratio / percent / latency** metrics, use `expected` as the
     duration-invariant target value.
   - Both shapes carry a `tolerance-percent`.
3. That's it — the workflow's two `Run PromQL queries` steps and the
   `Render and post results` step all iterate over the queries list, so no
   workflow YAML edit is needed.

## Reference values

The PR comment uses two reference columns alongside Current:

- **Daily** — re-queried live every PR run from the most recent active
  `c8-medic-daily-*-test` namespace on main. This is what drives the verdict
  (✅/⚠️). No file to maintain; the daily workflow keeps it fresh.
- **Optimal** — static aspirational target stored in
  [`load-tests/quicker-benchmark/optimal.json`](optimal.json). Each metric
  key under `metrics` must match a `name:` from `queries.yaml`. Carries
  either `expected` or `per-second` plus `tolerance-percent`. The current
  values are **placeholders** until we calibrate from clean main runs.

### Updating Optimal

1. Trigger `workflow_dispatch` against `main` (no PR number) two or three times in a row.
2. Read the resulting tables from the run summaries.
3. Open a PR that updates `optimal.json` to the median (or a slightly
   conservative value) and tightens `tolerance-percent` to a band that survives normal
   cluster noise.
4. Reference the workflow runs you sampled in the PR description so future updates have a
   trail to follow.

Update Optimal on intentional, accepted performance shifts (engine refactors, new
data layer rollouts) — not on flaky noise. If a single run regresses but the next ten
don't, leave Optimal alone.

## Implementation pointers

- Trigger workflow: [.github/workflows/camunda-quicker-pr-load-test.yaml](../../.github/workflows/camunda-quicker-pr-load-test.yaml)
- Reusable deploy: [.github/workflows/camunda-load-test.yml](../../.github/workflows/camunda-load-test.yml) (called with `scenario: 'max'` + `--set starter.durationLimit`)
- Readiness: [`./.github/actions/await-load-test`](../../.github/actions/await-load-test/action.yml) (pod-reschedule retries + gateway-connectivity check)
- Metrics source: [`queries.yaml`](queries.yaml) (PromQL definitions; edit here to add/remove metrics)
- Query runner: [`validate-queries.sh`](validate-queries.sh) (substitutes vars + curls Prometheus; emits JSON)
- Prometheus endpoint: `https://ci-monitor.benchmark.camunda.cloud` (LDAP-protected; creds from Vault `secret/data/common/ldap/ci-infra`)
- Starter duration logic: [load-tests/load-tester/src/main/java/io/camunda/zeebe/starter/Starter.java](../load-tester/src/main/java/io/camunda/zeebe/starter/Starter.java) (`createContinuationCondition`)
- Starter counter + run-finished gauge: [StarterCounterMetricsDoc.java](../load-tester/src/main/java/io/camunda/zeebe/metrics/StarterCounterMetricsDoc.java)

