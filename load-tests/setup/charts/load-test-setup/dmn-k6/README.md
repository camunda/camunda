# DMN evaluation k6 workload

An optional, in-cluster [k6](https://k6.io/) load generator that measures the
DMN decision-evaluation floor and throughput of the Orchestration Cluster,
following the same load-test-setup pattern as the BPMN load-tester.

It is a gated part of the `load-test-setup` chart (`dmnK6.enabled`), so it is
deployed and run the same way as every other load test — via
`camunda-load-test.yml` (`scenario: dmn`) or `make install scenario=dmn`.

## What it does

1. **Deploys** the bundled decision(s) (`resources/*.dmn`) through the REST
   `POST /v2/deployments` endpoint (init container `deploy-decisions`), then
   waits until an evaluation returns `200` so k6 never races an undeployed
   decision.
2. **Drives** `POST /v2/decision-definitions/evaluation` at a **fixed arrival
   rate** (`constant-arrival-rate`) so target throughput is held independent of
   latency — latency degradation shows up as a growing VU count, not a slowing
   rate. Thresholds: `p95 < 200 ms`, `p99 < 350 ms`, checks `> 99.9%`.

Auth and the base URL are read from the `load-test-credentials` Secret, so the
generator reuses the exact same OIDC client and audience as the load-tester.

## CPU isolation

The generator should not steal CPU from the brokers, or the latency numbers are
skewed. The Job **prefers** a broker-free node via a `preferredDuringScheduling`
`podAntiAffinity` against `zeebe-broker` pods (weight 100). It is a preference,
not a hard requirement, so the Job always schedules even on a cluster where every
free node already runs a broker — it just co-locates in that case. Give the
cluster a spare non-broker node to keep the generator fully isolated.

## Running it

Pair with the RF1 + in-memory platform overlay
(`camunda-platform-values-dmn-minimal.yaml`) for the lowest-latency floor while
**keeping Operate visibility** (Elasticsearch and the `camunda` exporter stay on;
`replicationFactor 1` drops the Raft quorum round-trip and `persistenceType:
memory` puts the Zeebe data dir on a tmpfs so the per-commit fsync never hits
disk):

```bash
# via the load-test workflow (recommended)
#   scenario: dmn
#   secondary-storage-type: elasticsearch      # keep Operate visibility
#   platform-helm-values: -f camunda-platform-values-dmn-minimal.yaml
#   load-test-setup-helm-values: --set dmnK6.rate=3334 --set dmnK6.duration=5m --set dmnK6.maxVUs=1000

# or locally, against a scaffolded namespace
make install scenario=dmn \
  additional_load_test_setup_configuration="--set dmnK6.rate=3334 --set dmnK6.duration=5m"
```

Reference rates: `3334` ≈ 200k/min, `6667` ≈ 400k/min.

Read the results from the k6 Job's logs:

```bash
kubectl -n <namespace> logs -f job/dmn-k6
```

## Knobs (`dmnK6.*` in values.yaml)

| Value        | Default             | Meaning                                          |
|--------------|---------------------|--------------------------------------------------|
| `rate`       | `100`               | Evaluations per second (held constant)           |
| `duration`   | `60s`               | How long to sustain the rate                     |
| `maxVUs`     | `200`               | Cap on concurrent in-flight requests             |
| `decisionId` | `small_decision`    | Decision evaluated in single mode                |
| `mix`        | `""`                | JSON `[{id,weight,variables?}]` weighted mix      |
| `image`      | `grafana/k6:0.55.0` | k6 image (pinned for reproducible numbers)       |
| `resources`  | 2 CPU / 1Gi         | Generator resource bounds                         |

## Anonymized fixtures

`resources/small_decision.dmn` is a fully synthetic 2-rule decision table
(input `category`, output `result`). Add more `*.dmn` files here (and reference
them via `dmnK6.mix`) to probe larger tables; the init container deploys every
`*.dmn` it finds.
