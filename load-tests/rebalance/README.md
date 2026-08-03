# Coordinated rebalance validation suite

Drives a sustained, varied run of the coordinated leadership transfer (CLT) rebalance against a
load-test cluster, and asserts on Prometheus as it goes.

The reason it asserts during the run rather than after it: a rebalance is fast — a whole one can be
over inside a second, a single transfer inside tens of milliseconds — so it is entirely possible to
drive a three-hour run whose dashboard panels stay empty and only find out at the end. Every
scenario here therefore states what its metrics should show and fails loudly, in place, when they do
not.

## What it needs

|                                |                                                                                                                           |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| A load-test cluster            | 6 brokers, 12 partitions, replication factor 3 (see below)                                                                |
| `kubectl` access               | to the benchmark cluster, for the brokers and for `monitoring/kube-prometheus-stack-prometheus`                           |
| `zbchaos`                      | built from [camunda/zeebe-chaos](https://github.com/camunda/zeebe-chaos) with `cluster rebalance` and `verify leadership` |
| `jq`, `curl`, `awk`, `python3` | on `PATH`                                                                                                                 |

Replication factor 3 of 6 brokers matters: at RF equal to cluster size every broker replicates every
partition, so the coordinator can always reach a partition through its own `RaftPartitionServer`. At
RF 3 of 6 each broker replicates half of them, which is the only shape that exercises
`LeadershipTransferClient` — the coordinator addressing a partition it does not replicate.

```bash
gh workflow run camunda-load-test.yml --repo camunda/camunda \
  --field name=mk-clt-sustained-YYYYMMDD \
  --field ref=<branch> \
  --field scenario=typical \
  --field secondary-storage-type=none \
  --field enable-optimize=false \
  --field ttl=2 \
  --field platform-helm-values="--set-string orchestration.clusterSize=6 --set-string orchestration.partitionCount=12 --set-string orchestration.replicationFactor=3 --set-string prometheusServiceMonitor.scrapeInterval=10s"
```

`secondary-storage-type=none` keeps the exporter and secondary storage out of the picture, so any
latency or throughput movement is attributable to a transfer. It needs one fix on bring-up, because
the chart renders `zeebe.broker.exporters: {}` and Spring cannot bind an empty map, so every broker
crash-loops:

```bash
cat > /tmp/override.yml <<'EOF'
zeebe:
  broker:
    exporters:
      MetricsExporter:
        className: io.camunda.zeebe.broker.exporter.metrics.MetricsExporter
EOF
kubectl create configmap camunda-configuration -n "$NS" \
  --from-file=application-override.yml=/tmp/override.yml --dry-run=client -o json \
  | jq '.data' > /tmp/patch-data.json
kubectl patch configmap camunda-configuration -n "$NS" --type merge \
  -p "$(jq -n --slurpfile d /tmp/patch-data.json '{data: $d[0]}')"
sleep 75                                     # let kubelet sync the projected volume
kubectl delete pod -n "$NS" -l app.kubernetes.io/component=zeebe-broker
```

The key must be `MetricsExporter`, matching `MetricsExporter.defaultExporterId()`, so that the
broker's own registration overwrites this entry rather than adding a second one that would
double-count execution metrics.

Also suspend the chart's `leader-balancer` CronJob, which POSTs the **legacy** `/actuator/rebalance`
every ten minutes and would move leadership underneath every measurement. Preflight refuses to start
otherwise:

```bash
kubectl patch cronjob leader-balancer -n "$NS" -p '{"spec":{"suspend":true}}'
```

## Running it

```bash
# The whole suite: roughly three and a half hours
./run.sh -n c8-mk-clt-sustained-20260803 --zbchaos ~/git/zeebe-chaos/go-chaos/zbchaos

# A shakedown of the same scenarios in minutes, to check the wiring before committing hours
./run.sh -n <ns> --zbchaos <path> --time-scale 0.05 --only baseline,imbalance-and-rebalance

# One scenario, or a resume after a scenario failed
./run.sh -n <ns> --zbchaos <path> --only lag-refusal
./run.sh -n <ns> --zbchaos <path> --from coordinator-kill
```

`--time-scale` multiplies every wait, so a whole run can be rehearsed quickly. It deliberately does
not scale the pause between scenarios that lets their metrics be scraped, so a shrunk run behaves
the same way a full one does rather than attributing one scenario's counters to the next.

The run keeps every port forward it needs alive itself, respawning them when a broker restart kills
one, and clears any injected replication lag on exit.

## The scenarios

|                           |                                                                     What it establishes                                                                      |
|---------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `baseline`                | 20 minutes of a balanced cluster under load: the state every imbalance below is created against, and the completion rate the saturation scenario stays under |
| `api-surface`             | Every broker answers for the coordinator; a dry run plans without moving anything; overrides are echoed; bad settings are refused                            |
| `balanced-rebalance`      | Rebalancing a balanced cluster costs nothing — no pause, no transfer, every partition counted as already balanced                                            |
| `imbalance-and-rebalance` | The core case: leadership drifted by restarts is brought back under load, one partition at a time, and serialisation is proved from the coordinator's log    |
| `saturation-rebalance`    | The same, with load raised to just under the measured ceiling: longer catch-ups, more rejected writes, still bounded                                         |
| `lag-refusal`             | Manufactured replication lag past the 8MB threshold, so the admission check refuses a transfer (`LAG_TOO_HIGH`) — and a refusal freezes nothing              |
| `lag-timeout`             | A catch-up that cannot finish inside the replication timeout: the proof that a pause is bounded by something other than the happy path                       |
| `cancel-mid-rebalance`    | A cancelled rebalance lets its in-flight transfer finish, leaves the rest alone, and frees every partition                                                   |
| `coordinator-kill`        | The coordinator killed mid-rebalance leaves no partition frozen and no gauges behind; a later rebalance still works                                          |
| `broker-down`             | A partition the rebalance wanted to move and could not is `FAILED`, not `SKIPPED` — an operator must be able to tell those apart                             |
| `leader-wait-timeout`     | The coordinator giving up while a leader is still working: whether the partition is left frozen, and for how long                                            |
| `soak`                    | 45 minutes of rebalances with broker restarts between them, watching for stuck state, leaders that refuse later transfers, and metric series leaks           |

Scenarios that need an imbalance create their own by restarting brokers, since nothing moves
leadership back after a restart — which is the reason the feature exists.

The lag scenarios manufacture lag with `tc netem` in an ephemeral container, because the Camunda
image has no `tc`. Load alone will not produce lag on this hardware: a broker down for two minutes
under saturation caught up within twelve seconds, and setting the threshold to zero admits every
transfer anyway, since the gate is `lag > threshold` and a follower at genuinely zero lag passes it.

## What a run leaves behind

Under `--out` (default `~/Documents/clt-test-plan/runs/<namespace>-<timestamp>/`):

|                                           |                                                                                                                                                       |
|-------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SUMMARY.md`                              | Per-scenario windows with Grafana links, and every failed assertion                                                                                   |
| `run.log`                                 | The whole narrative                                                                                                                                   |
| `timeline.jsonl`                          | Scenario windows as epoch milliseconds, for slicing dashboards or PromQL                                                                              |
| `NN-<scenario>/assertions.tsv`            | Every assertion with its actual value and the query behind it                                                                                         |
| `NN-<scenario>/*.json`                    | Rebalance status bodies — the only place each partition's terminal state survives, since the coordinator's gauges are torn down when a rebalance ends |
| `NN-<scenario>/notes.log`                 | Observations recorded rather than asserted, where either outcome is legitimate and which one happened is the point                                    |
| `NN-<scenario>/coordinator-narrative.log` | The coordinator's own account of each ask and outcome, which is what proves serialisation                                                             |

Write these somewhere durable rather than a temp directory. A previous run left its evidence in a
session-scoped one against a namespace with a two-day TTL, and the raw evidence behind its write-up
is gone.

## Metrics the suite asserts on

|                                  Metric                                  |     Published by      |                                                                                                                                What it answers                                                                                                                                 |
|--------------------------------------------------------------------------|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `zeebe_cluster_partition_balanced`                                       | every member          | Whether each partition is led by its highest-priority member. Published at all times, so it is the imbalance detector as well as the convergence check. Reduced pessimistically per partition before averaging, because members disagree while the two gossiped views converge |
| `zeebe_cluster_rebalance_elapsed_seconds`                                | the coordinator       | Whole-rebalance duration, by how it ended                                                                                                                                                                                                                                      |
| `zeebe_cluster_rebalance_partition_duration_seconds`                     | the coordinator       | Time on one partition, tagged with what became of it — the fastest read on why a rebalance did what it did. Counts partitions resolved without asking anyone under `ALREADY_BALANCED`                                                                                          |
| `zeebe_cluster_rebalance_partition_state`, `..._pending`                 | the coordinator       | Live progress. **Torn down when a rebalance ends**, so no scenario asserts on catching them mid-flight                                                                                                                                                                         |
| `zeebe_cluster_rebalance_partition_paused`, `..._pause_duration_seconds` | each partition leader | What a rebalance costs in availability. Not tied to a coordinator being up, so these are what the fault scenarios read                                                                                                                                                         |
| `zeebe_raft_replication_lag_bytes`                                       | each partition leader | The input the admission check compares against the threshold                                                                                                                                                                                                                   |
| `zeebe_flow_control_total{outcome="partitionPaused"}`                    | each partition leader | Writes a transfer rejected. A client sees these as retryable `RESOURCE_EXHAUSTED`                                                                                                                                                                                              |

## Related

- Epic: [#3630](https://github.com/camunda/camunda/issues/3630); coordinator and API: [#56815](https://github.com/camunda/camunda/issues/56815)
- Chaos experiments for the same feature: [#56821](https://github.com/camunda/camunda/issues/56821), in `camunda/zeebe-chaos`
- Dashboard panels: the `Rebalancing` row of `monitor/grafana/zeebe.json`
- Load-test operations: [`load-tests/README.md`](../README.md), [`load-tests/docs/metrics.md`](../docs/metrics.md)

