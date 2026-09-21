# k6 scripts

k6 load-test scripts, mounted into the k6-operator `TestRun` pods by
`templates/k6/configmap-scripts.yaml` (which globs `k6/scripts/**.js`) and selected
per-test via `.Values.k6.tests` in `values.yaml`.

## Available scripts

| Script                          | What it does                                                        |
|---------------------------------|--------------------------------------------------------------------|
| `test.default.js`               | Baseline: polls topology and searches process instances.           |
| `test.dmn-evaluation.js`        | Evaluates a **single** decision (defaults to the 2-rule table).     |
| `test.dmn-evaluation-mixed.js`  | Evaluates a **weighted mix** of decisions of different table sizes. |

## DMN evaluation (`test.dmn-evaluation.js`)

Drives `POST /v2/decision-definitions/evaluation` at a **fixed arrival rate**
(requests/second) so target throughput is held independent of latency. Thresholds
encode the SLO: `p95 < 200ms`, `p99 < 350ms`, and `>99.9%` success.

Defaults to `DMN_DECISION_ID=small_decision` (the tiny 2-rule table), so out of the
box this is the **minimum-latency / floor probe**.

Enable it in `values.yaml`:

```yaml
k6:
  tests:
    dmn-evaluation:
      enabled: true
      script: test.dmn-evaluation.js
```

Tunables (env vars, with defaults):

| Env var           | Default          | Meaning                                  |
|-------------------|------------------|------------------------------------------|
| `DMN_DECISION_ID` | `small_decision` | `decisionDefinitionId` to evaluate       |
| `DMN_VARIABLES`   | `{}`             | JSON object of input variables           |
| `DMN_RATE`        | `100`            | evaluations per second                   |
| `DMN_DURATION`    | `60s`            | how long to sustain the rate             |
| `DMN_MAX_VUS`     | `200`            | cap on concurrent in-flight requests     |

> `DMN_RATE` is per second. 200,000/min ≈ `DMN_RATE=3334`; 400,000/min ≈ `DMN_RATE=6667`.
> Raise `DMN_MAX_VUS` accordingly, and run k6 with enough `parallelism` to source that load.

The decision must be deployed first. The k6 pods authenticate with the same
`load-test-credentials` secret as `test.default.js`.

### Deploying the decision

k6 does not deploy resources. Deploy the fully-synthetic, anonymized 2-rule table
in `k6/resources/small_decision.dmn` (decision id `small_decision`) once, before
the run — e.g. against the REST API:

```bash
curl -sf -X POST "$CAMUNDA_BASE_URL/v2/deployments" \
  -H "Authorization: Bearer $TOKEN" \
  -F "resources=@k6/resources/small_decision.dmn"
```

## Mixed sizes (`test.dmn-evaluation-mixed.js`)

Same request/thresholds, but each call picks a decision from a **weighted mix** of
different table sizes, defaulting to the S-34406 shape (~42% the 2-rule table, the
rest large graphs). Latency is tagged per `decision` so it can be broken down per
table size in Prometheus.

```yaml
k6:
  tests:
    dmn-evaluation-mixed:
      enabled: true
      script: test.dmn-evaluation-mixed.js
```

Override the mix via env (relative weights):

```
DMN_MIX='[{"id":"small_decision","weight":42},{"id":"graph_a_d10","weight":29},{"id":"graph_b_d10","weight":29}]'
```

`DMN_RATE`, `DMN_DURATION`, `DMN_MAX_VUS` behave as above.

## Minimum-latency cluster

To measure the lowest achievable per-evaluation latency, deploy the Camunda cluster
with the minimal single-broker overlay (single partition, **replication factor 1**,
no exporter/secondary storage, consistency checks off):

```
-f main/values/camunda-platform-values-defaults.yaml \
-f main/values/camunda-platform-values-dmn-minimal.yaml
```

RF1 removes the Raft-quorum round-trip, which is the single biggest contributor to
the ~14 ms evaluation floor. This is a latency reference topology, not a
production/throughput one.

### Running locally

```bash
CAMUNDA_BASE_URL=http://localhost:8080 \
CAMUNDA_CLIENT_ID=... CAMUNDA_CLIENT_SECRET=... \
CAMUNDA_OAUTH_URL=... CAMUNDA_TOKEN_AUDIENCE=... \
DMN_DECISION_ID=small_decision DMN_RATE=100 DMN_DURATION=60s \
k6 run test.dmn-evaluation.js
```
