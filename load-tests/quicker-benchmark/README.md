# Quicker Benchmark

A finite, label-triggered load test that gives PR authors a comparable performance signal
within ~10 minutes, posted as a comment on the PR.

Compared to the existing `benchmark` label (indefinite max load + flamegraphs), the
`quicker-benchmark` label runs the same `max` workload but stops cleanly after a configured
duration, scrapes the cluster Prometheus for throughput + latency + engine-side metrics,
and posts a table comparing the run against a static baseline.

The platform takes ~7min to come up; the default 10min duration leaves ~3min of usable
load. Bump `duration-seconds` via `workflow_dispatch` for longer runs.

## Triggering

| Trigger |                                                                                               How                                                                                                |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| On a PR | Apply the `quicker-benchmark` label. Removing the label tears the namespace down.                                                                                                                |
| Ad-hoc  | Run the `Camunda Pull Request Quicker Benchmark` workflow via `workflow_dispatch`. Optional inputs: `duration-seconds` (default 600), `pr-number` (when set, the comment is posted on that PR). |

The two labels (`benchmark` and `quicker-benchmark`) deploy to **separate namespaces** and
can coexist on the same PR.

## How completion is detected

Spring Boot's WebFlux server keeps the starter JVM alive after the creation loop ends, so
the pod stays `Running`. Instead of waiting on pod phase, the workflow polls the
`starter_run_finished` gauge directly off the starter pod's `/metrics` endpoint — the
starter flips it to `1` when the loop exits.

If k8s restarts the starter pod mid-run (OOM, node failure, etc.), the new pod runs another
full `duration-limit` from scratch — total wall-clock time exceeds what the comment header
says, and submitted-instance counts double-count the restart window. The workflow surfaces
a ⚠️ warning with the restart count when this happens; we don't try to recover. For the
10-minute default this is rare enough to accept.

## How metrics are collected

The cluster runs a kube-prometheus-stack install in `monitoring/`, but the CI Teleport role
does not grant `pods/portforward` on that namespace — only on test namespaces. So instead
of querying the central Prometheus from CI, the workflow:

1. Port-forwards the **starter pod** and reads `/metrics` for the run-finished gauge,
   instance counter, and starter latency histograms.
2. Port-forwards each **camunda broker pod** in turn and reads `/actuator/prometheus`
   for `zeebe_element_instance_events_total`, `zeebe_dropped_request_count_total`, and
   `zeebe_received_request_count_total`. Per-broker counters only count their own
   partitions, so all three are scraped and the Python aggregator
   (`.github/scripts/aggregate-quicker-metrics.py`) sums them.
3. Computes histogram quantiles locally using Prometheus's same algorithm (find the bucket
   where cumulative count ≥ q × total, linearly interpolate). Cumulative bucket counts
   equal "all observations during the run" because each pod is fresh per test.

## What the comment shows

A table of current vs baseline for:

- Process instances submitted by the starter and average submission throughput (PI/s)
- Process instances completed end-to-end by the engine and average completion rate (PI/s)
- Starter response latency p50/p99 — request submit → engine ack
- Starter data availability latency p50/p99 — engine ack → instance queryable in secondary store
- Backpressure as % of received requests (`dropped / received` over the run)

Each row is annotated:
- ✅ within the configured tolerance (or beyond it on the *better* side)
- ⚠️ outside tolerance on the *worse* side (regression)
- ❔ metric was missing or NaN

The comment also links to the live Grafana dashboard for the namespace and to the workflow
run for details.

## Baseline file

The renderer reads `load-tests/quicker-benchmark/baseline.yaml`. Each metric carries an
`expected` value and a `tolerance-percent`. The default values in this directory are
**placeholders** until we have run the workflow against `main` enough times to set realistic
expectations.

### Updating the baseline

1. Trigger `workflow_dispatch` against `main` (no PR number) two or three times in a row.
2. Read the resulting tables from the run summaries.
3. Open a PR that updates `baseline.yaml` to the median (or a slightly conservative value)
   and tightens `tolerance-percent` to a band that survives normal cluster noise.
4. Reference the workflow runs you sampled in the PR description so future updates have a
   trail to follow.

Update the baseline on intentional, accepted performance shifts (engine refactors, new
data layer rollouts) — not on flaky noise. If a single run regresses but the next ten
don't, leave the baseline alone.

## Implementation pointers

- Trigger workflow: [.github/workflows/camunda-quicker-pr-load-test.yaml](../../.github/workflows/camunda-quicker-pr-load-test.yaml)
- Reusable deploy: [.github/workflows/camunda-load-test.yml](../../.github/workflows/camunda-load-test.yml) (scenario `quicker`)
- Helm wiring: [load-tests/setup/default/Makefile](../setup/default/Makefile) (`scenario=quicker`, `duration_limit` var)
- Starter duration logic: [load-tests/load-tester/src/main/java/io/camunda/zeebe/starter/Starter.java](../load-tester/src/main/java/io/camunda/zeebe/starter/Starter.java) (`createContinuationCondition`)
- Starter counter: [StarterCounterMetricsDoc.java](../load-tester/src/main/java/io/camunda/zeebe/metrics/StarterCounterMetricsDoc.java)

