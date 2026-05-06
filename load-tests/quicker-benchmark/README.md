# Quicker Benchmark

A finite, label-triggered load test that gives PR authors a comparable performance signal
within ~10 minutes, posted as a comment on the PR.

Compared to the existing `benchmark` label (indefinite max load + flamegraphs), the quicker
benchmark runs one of the shared scenarios but stops cleanly after a configured duration,
scrapes throughput + engine-side metrics off each pod, and posts a table comparing the run
against a static baseline.

Two scenarios are supported:

|        Label        | Scenario in `camunda-load-test.yml` |                                         What it tests                                         |
|---------------------|-------------------------------------|-----------------------------------------------------------------------------------------------|
| `quicker-max`       | `max` (rate=300, platform tuning)   | Saturating throughput. Goal: 300 PI/s submitted ≈ 300 PI/s completed, no backpressure.        |
| `quicker-realistic` | `realistic`                         | Sustainable, real-world rate. Goal: ~51 PI/s submitted ≈ ~51 PI/s completed, no backpressure. |

Both labels can coexist on the same PR — each scenario runs in its own namespace and posts
its own PR comment. The `benchmark` label is independent and can also coexist with either
quicker label.

The platform takes ~7min to come up; the default 10min duration leaves ~3min of usable
load. Bump `duration-seconds` via `workflow_dispatch` for longer runs.

This feature is **self-contained** — it does not modify
[`load-tests/setup/default/Makefile`](../setup/default/Makefile) or
[`.github/workflows/camunda-load-test.yml`](../../.github/workflows/camunda-load-test.yml).
Instead, it calls the existing reusable workflow with the matching `scenario` and passes
`--set starter.durationLimit=<seconds>` through the `load-test-load` input. Helm honors
the last `--set`, so the duration override wins over the scenario's defaults.

## Triggering

| Trigger |                                                                                                        How                                                                                                         |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| On a PR | Apply `quicker-max` and/or `quicker-realistic`. A push to the PR re-runs every applied label in parallel (matrix). Removing a label tears down only that scenario's namespace.                                     |
| Ad-hoc  | Run the `Camunda Pull Request Quicker Benchmark` workflow via `workflow_dispatch`. Inputs: `scenario` (default `max`), `duration-seconds` (default 600), `pr-number` (when set, the comment is posted on that PR). |

## How completion is detected

Spring Boot's WebFlux server keeps the starter JVM alive after the creation loop ends, so
the pod stays `Running`. Instead of waiting on pod phase, the workflow:

1. Sleeps for `duration-seconds` once the platform is ready — the starter is configured
   with `--set starter.durationLimit=$duration_seconds`, so the gauge can't flip earlier
   anyway. This avoids burning kubectl/curl traffic during the run.
2. After the sleep, polls the `starter_run_finished` gauge directly off the starter pod's
   `/metrics` endpoint — the starter flips it to `1` when the loop exits. Each iteration
   re-resolves the pod by label and opens a fresh short-lived port-forward, so a k8s
   restart of the starter pod (OOM, eviction, manual delete) doesn't strand us on a dead
   pod name. Polling deadline is `duration-seconds + 300s` — enough to absorb one full
   restart's worth of extra runtime.

If k8s restarts the starter pod mid-run, the new pod runs another full `duration-limit`
from scratch — total wall-clock time exceeds what the comment header says, and submitted-
instance counts double-count the restart window. The workflow surfaces a ⚠️ warning with
the restart count when this happens; we don't try to recover. For the 10-minute default
this is rare enough to accept.

## How metrics are collected

The cluster runs a kube-prometheus-stack install in `monitoring/`, but the CI Teleport role
does not grant `pods/portforward` on that namespace — only on test namespaces. So instead
of querying the central Prometheus from CI, the workflow:

1. Calls the shared [`./.github/actions/await-load-test`](../../.github/actions/await-load-test/action.yml)
   composite. It waits for `app=camunda-platform` and
   `app.kubernetes.io/component=zeebe-client` pods to be Ready (with pod-reschedule
   retries) and then verifies gateway connectivity via the `clients` service's
   `app_connected` metric.
2. Port-forwards the **starter pod** and reads `/metrics` for the run-finished gauge and
   the instance counter.
3. Port-forwards each **camunda broker pod** in turn and reads `/actuator/prometheus`
   for `zeebe_element_instance_events_total`, `zeebe_dropped_request_count_total`, and
   `zeebe_received_request_count_total`. Per-broker counters only count partitions that
   broker leads, so all three scrapes are summed in an inline `awk` step.

> Sidenote: the shared `await-load-test` composite has a known `pipefail` issue — its
> first scrape during Spring actuator startup can return curl exit 52, which kills the
> step before its retry loop iterates. Daily/weekly tests don't trip it because they
> reach the action later in their boot sequence. We're tracking a standalone fix; if it
> bites a cold deploy here, the readiness step fails cleanly and a relabel/resync re-runs.

## What the comment shows

A table of current vs baseline for:

- Process instances submitted by the starter and average submission throughput (PI/s)
- Process instances completed end-to-end by the engine and average completion rate (PI/s)
- Backpressure as % of received requests (`dropped / received` over the run)

Each row is annotated:
- ✅ within the configured tolerance (or beyond it on the *better* side)
- ⚠️ outside tolerance on the *worse* side (regression)
- ❔ metric was missing or NaN

The comment also links to the live Grafana dashboard for the namespace and to the workflow
run for details.

## Baseline files

The renderer reads one of:

- [`load-tests/quicker-benchmark/baseline-max.yaml`](baseline-max.yaml) — for `quicker-max`
- [`load-tests/quicker-benchmark/baseline-realistic.yaml`](baseline-realistic.yaml) — for `quicker-realistic`

Each metric carries an `expected` value and a `tolerance-percent`. The current values are
**placeholders** until we have run the workflow against `main` enough times to set
realistic expectations.

### Updating a baseline

1. Trigger `workflow_dispatch` against `main` (no PR number, pick the relevant `scenario`)
   two or three times in a row.
2. Read the resulting tables from the run summaries.
3. Open a PR that updates the matching `baseline-*.yaml` to the median (or a slightly
   conservative value) and tightens `tolerance-percent` to a band that survives normal
   cluster noise.
4. Reference the workflow runs you sampled in the PR description so future updates have a
   trail to follow.

Update a baseline on intentional, accepted performance shifts (engine refactors, new
data layer rollouts) — not on flaky noise. If a single run regresses but the next ten
don't, leave the baseline alone. Keep the two baselines independent; a calibration run
for `max` does not justify changing `realistic`.

## Implementation pointers

- Trigger workflow: [.github/workflows/camunda-quicker-pr-load-test.yaml](../../.github/workflows/camunda-quicker-pr-load-test.yaml)
- Reusable deploy: [.github/workflows/camunda-load-test.yml](../../.github/workflows/camunda-load-test.yml) (called with `scenario: 'max'` or `scenario: 'realistic'` + `--set starter.durationLimit`)
- Readiness: [`./.github/actions/await-load-test`](../../.github/actions/await-load-test/action.yml) (pod-reschedule retries + gateway-connectivity check)
- Starter duration logic: [load-tests/load-tester/src/main/java/io/camunda/zeebe/starter/Starter.java](../load-tester/src/main/java/io/camunda/zeebe/starter/Starter.java) (`createContinuationCondition`)
- Starter counter + run-finished gauge: [StarterCounterMetricsDoc.java](../load-tester/src/main/java/io/camunda/zeebe/metrics/StarterCounterMetricsDoc.java)

