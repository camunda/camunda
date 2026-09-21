# DMN evaluation load test (in-cluster k6)

A **self-contained** k6 load test for the Orchestration Cluster (OC) decision-
evaluation endpoint `POST /v2/decision-definitions/evaluation`. No dependency on
the k6-operator or any other chart — just a ConfigMap + a Job.

The generator runs **inside the cluster, on its own node**, so the laptop is not
the bottleneck and — critically — the generator does **not** consume broker CPU,
which would skew latency/throughput results.

## Why the results are trustworthy (isolation)

The benchmark broker nodes carry the taint `nodepool=n2-standard-4:NoSchedule`
and each broker takes a whole node. The k6 Job (`job.yaml`):

1. **Does not tolerate** that taint → the scheduler cannot place it on a broker
   node.
2. Adds **podAntiAffinity** against `app.kubernetes.io/component: zeebe-broker` →
   never co-scheduled with a broker even if the taint changes.
3. Has bounded `resources` so it stays within its own node.

If the pod stays `Pending`, there is no spare non-broker node — add one; do not
relax the isolation.

## Minimum-latency cluster

To measure the true floor, deploy the OC cluster with the minimal single-broker
overlay (single partition, **replication factor 1**, no exporter/secondary
storage, consistency checks off):

```
-f main/values/camunda-platform-values-defaults.yaml \
-f main/values/camunda-platform-values-dmn-minimal.yaml
```

RF1 removes the Raft-quorum round-trip, the biggest contributor to the ~14 ms
evaluation floor. This is a latency-reference topology, not a throughput/HA one.

## Deploy the decision

k6 does not deploy resources. Deploy the fully-synthetic, anonymized 2-rule table
`resources/small_decision.dmn` (decision id `small_decision`) once:

```bash
curl -sf -X POST "$CAMUNDA_BASE_URL/v2/deployments" \
  -H "Authorization: Bearer $TOKEN" \
  -F "resources=@resources/small_decision.dmn"
```

## Run

```bash
# defaults: small_decision, 100 rps, 60s
./run.sh

# ~200,000/min for 5 minutes (the steady-state target)
DMN_RATE=3334 DMN_DURATION=5m DMN_MAX_VUS=1000 ./run.sh

# ~400,000/min (the planning target)
DMN_RATE=6667 DMN_DURATION=5m DMN_MAX_VUS=2000 ./run.sh
```

`run.sh` (re)builds the ConfigMap from `dmn-evaluation.js`, applies the Job with
any exported `DMN_*`/`CAMUNDA_*` overrides, and streams the k6 summary. Set
`KUBECTL_NAMESPACE` to target a namespace.

### Load knobs (env)

| Env var           | Default              | Meaning                                 |
|-------------------|----------------------|-----------------------------------------|
| `DMN_RATE`        | `100`                | evaluations per **second**              |
| `DMN_DURATION`    | `60s`                | how long to sustain the rate            |
| `DMN_MAX_VUS`     | `200`                | cap on concurrent in-flight requests    |
| `DMN_DECISION_ID` | `small_decision`     | single-decision id                      |
| `DMN_MIX`         | —                    | weighted mix, e.g. `[{"id":"small_decision","weight":42},{"id":"graph_a_d10","weight":29}]` |
| `DMN_VARIABLES`   | `{}`                 | input variables (single mode)           |
| `CAMUNDA_BASE_URL`| `http://camunda-gateway:8080` | in-cluster REST endpoint       |

> `DMN_RATE` is per second: 200k/min ≈ 3334, 400k/min ≈ 6667.

### Auth

Auto-detected from env (checked in order): `CAMUNDA_BASIC_AUTH="user:pass"` →
Basic; `CAMUNDA_CLIENT_ID` (+ `CAMUNDA_CLIENT_SECRET`/`CAMUNDA_OAUTH_URL`/
`CAMUNDA_TOKEN_AUDIENCE`) → OAuth2 client-credentials Bearer; neither → no auth.
`job.yaml` shows how to wire the OAuth vars from the existing
`load-test-credentials` secret.

## Thresholds (SLO)

`p95 < 200ms`, `p99 < 350ms`, `>99.9%` success — k6 exits non-zero on breach.

## Clean up

```bash
kubectl delete job k6-dmn-evaluation
kubectl delete configmap k6-dmn-scripts
```
