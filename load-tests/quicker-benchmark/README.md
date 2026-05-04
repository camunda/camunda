# Quicker Benchmark

A finite, label-triggered load test that gives PR authors a comparable performance signal
within ~1 hour, posted as a comment on the PR.

Compared to the existing `benchmark` label (indefinite max load + flamegraphs), the
`quicker-benchmark` label runs the same `max` workload but stops cleanly after a configured
duration, scrapes Prometheus for throughput + latency + engine-side metrics, and posts a
table comparing the run against a static baseline.

## Triggering

| Trigger |                                                                                               How                                                                                                |
|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| On a PR | Apply the `quicker-benchmark` label. Removing the label tears the namespace down.                                                                                                                |
| Ad-hoc  | Run the `Camunda Pull Request Quicker Benchmark` workflow via `workflow_dispatch`. Optional inputs: `duration-seconds` (default 3600), `pr-number` (when set, the comment is posted on that PR). |

The two labels (`benchmark` and `quicker-benchmark`) deploy to **separate namespaces** and
can coexist on the same PR.

## What the comment shows

A table of current vs baseline for:

- Process instances started (and average throughput PI/s)
- Starter response latency p50/p99 — request submit → engine ack
- Starter data availability latency p50/p99 — engine ack → instance queryable
- Zeebe record processing rate
- Backpressure rejection total

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

