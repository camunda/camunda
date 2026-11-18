---
name: noisy-neighbour-report
description: Build a per physical tenant report of a load test for the noisy neighbour work: how the load is shared, how many tenants still get the service their own load asks for, and how much load, latency, secondary storage latency and export queue vary between tenants, optionally against a reference run without a noisy tenant. Use after a physical tenants load test (camunda-physical-tenants-load-test / starter-ptN pods) has run for at least 15 minutes, or when asked to measure interference between tenants, compare solutions of the noisy neighbour proposal, or find which tenant lags behind.
---

# Noisy neighbour report

One command turns the Prometheus data of a physical tenants load test into a Markdown report that
answers the questions of the noisy neighbour proposal:

1. **How is the load shared?** Offered load, served load and fair share of every tenant.
2. **How many tenants still get what their own load asks for?** Served share, response latency,
   secondary storage latency and export queue against a target, per tenant and as counts.
3. **How alike are the quiet tenants treated?** Spread (mean, min, max, standard deviation, CV) and
   Jain's fairness index of load, latency, storage latency and export queue.
4. **How much worse than without the noisy tenant?** Ratio against a reference run.

The tenants come from the load tester pods the setup creates (`starter-ptN-*`, `worker-ptN-*`, and
`starter-*` for the default tenant). Broker numbers come from the `physicalTenant` label.

## Prerequisites

- The load test ran with the same inputs for every run you compare. Use the
  `camunda-physical-tenants-load-test` workflow inputs as the record of the load.
- It has run long enough: skip the first 10 minutes (warm up), then use at least 10 to 15 minutes.
- Prometheus is reachable. In another terminal, with the benchmark cluster as the current context:

  ```bash
  ./load-tests/loadtestctl/loadtestctl prometheus
  ```

  A remote Prometheus works through `LOADTESTCTL_PROMETHEUS_URL`, `_USER` and `_PASSWORD`, or
  `--prometheus-url`.

## Run it

```bash
python3 .agents/skills/noisy-neighbour-report/scripts/tenant_report.py c8-my-test \
  --duration 900 \
  --reference c8-my-test-reference \
  --output report.md --json report.json
```

Useful options:

| Option | Use |
| --- | --- |
| `--end <time>` | End of the window (RFC3339 or epoch). Default: now. Set it when the run is over |
| `--reference <ns>` / `--reference-end` | The run without a noisy tenant, to compute the interference |
| `--saturating pt1` | Name the noisy tenants. Default: tenants offering at least twice the median load |
| `--weights pt2=3` | Relative shares, if the tenants do not have equal weights |
| `--min-service 0.95` | Served share that counts as served |
| `--slo-seconds 5` | p99 limit for response latency and for the data to reach the storage |
| `--lag-seconds 5` | Limit for the time the exporter needs to clear its queue |

Run the tests with `python3 -m unittest discover -s .agents/skills/noisy-neighbour-report/tests`.

## Reading the report

| Section | What it tells | What to look for |
| --- | --- | --- |
| Do the tenants get the service | Counts of tenants meeting each target and all of them | The headline number for the proposal: how many of N tenants are unaffected |
| Per tenant | Everything per tenant | A quiet tenant with a low Served, high latency or a growing export queue is a victim |
| How the load is shared | Offered vs served vs fair share | The noisy tenant serving more than its fair share while quiet ones serve less |
| Variance between the quiet tenants | Spread over the tenants that behave | A high CV means uneven treatment. Jain's index near 1 means even |
| Against the reference run | Ratio to the same tenants without noise | Served below 1, latency and lag above 1: that is the interference |
| Cluster context | Backpressure, CPU, throttling | Throttling or dropped requests explain why the cluster is saturated |

Patterns:

| Pattern | Likely meaning |
| --- | --- |
| All tenants Served below 100%, evenly | The cluster is saturated and the load is shared fairly. No isolation problem, a capacity one |
| Quiet tenants Served below 100%, noisy tenant mostly served | The noisy tenant takes the capacity: no isolation |
| High CV of response latency, similar Served | The queueing delay is uneven between tenants |
| One tenant with a growing export queue and a high data availability p99 | The exporter lags for that tenant (shared exporter threads or storage). Look at the other tenants' export queue |
| Skipped above 0% | The load tester could not send the load. That is the generator, not the cluster |
| Data in storage p99 high but export lag low | The delay is after the export: the secondary storage indexing or the search path |

## Using it for the proposal

- Run the reference (no saturating tenant) and the noisy run with the same inputs, and repeat both, since
  single runs vary. Give `--reference` to get the interference ratios.
- Keep the report, the JSON and the workflow inputs together, and note the cluster capacity that was
  used. Publish only repeated, reproducible numbers (user story LT-3).
- To compare a solution with the baseline, run the report on both and compare the counts of the first
  section, the interference table and Jain's index.

## Metrics

See [references/metrics.md](references/metrics.md) for every query, the metric each number comes from
and the assumptions behind them.

## Gotchas

- **The tenant comes from the pod name.** Pods must be `starter-pt<N>-*` / `worker-pt<N>-*`, as the setup
  creates them, with no tenant meaning the default tenant. Other names are lumped into the default tenant.
- **Latency is what the load tester saw.** It includes the REST or gRPC stack. Compare runs over the same API.
- **Backpressure is per partition, not per tenant.** Quiet tenants can be rejected because of a noisy
  neighbour on the same partitions. That is what the report is meant to show.
- **The export queue is a position difference.** Committed minus exported position per partition, summed
  per tenant. Positions are not exactly records, so the lag in seconds is an estimate.
- **Check the Notes section first.** It lists what could not be measured, for example a missing tenant
  label on the exporter metrics or worker metrics that are not scraped. This tool has been unit tested
  against a fake Prometheus; verify the first real report against the Grafana dashboards.
- **Spread needs several quiet tenants.** With fewer than two, the variance section is empty.
