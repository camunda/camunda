# Load Test Setup Helm Chart

This Helm Chart sets up the surrounding infrastructure for a Camunda load test namespace.

* The **namespace** itself, labeled with the owner, a reclaim deadline, and (optionally) an AZ pin.
* The `camunda-credentials` and `load-test-credentials` secrets, with deterministic passwords
  generated via Helm's `derivePassword` so reinstalls (e.g. after a TTL cleanup) don't rotate
  credentials out from under the platform release.
* A **leader-balancer** CronJob that periodically triggers Zeebe partition leader rebalancing.
* An optional **chaos-killer** CronJob that randomly deletes matching pods to simulate unscheduled
  restarts.
* An optional **ECK-managed Elasticsearch** cluster (`elasticsearch.enabled=true`), for load tests
  that use Elasticsearch as secondary storage.
* An optional **[OpenSearch](https://opensearch-project.github.io/helm-charts/)** cluster
  (`opensearch.enabled=true`), for load tests that use OpenSearch as secondary storage.
* An optional **CNPG-managed PostgreSQL cluster** for Camunda's own secondary storage
  (`postgresql.enabled=true`), for load tests that use PostgreSQL as secondary storage. See the
  [PostgreSQL (Camunda secondary storage)](#postgresql-camunda-secondary-storage) section below for
  details.
* An optional **Keycloak instance**, backed by its own **PostgreSQL cluster** (`keycloak.enabled`,
  default: `true`). See the [Keycloak](#keycloak) section below for details.
* An optional **metrics-exporter** deployment to query the report internal
  metrics from the Camunda components (see [the `metrics-exporter`
  component](../../../metrics-exporter))
* An optional **[Prometheus Elasticsearch exporter](https://github.com/prometheus-community/helm-charts/tree/main/charts/prometheus-elasticsearch-exporter)**
  subchart to monitor Elasticsearch/OpenSearch (`prometheus-elasticsearch-exporter.enabled`).
* The **`load-tester` subchart** ([`camunda-load-tests`](https://github.com/camunda/camunda-load-tests-helm)),
  which deploys the actual load generators (starter/worker). Can be disabled for a bare
  infrastructure-only setup.

This chart is currently only used for the internal load test infrastructure and
is not made to be generally reusable.

## Dependencies

|                                                                     Chart                                                                     |                Alias                |                          Enabled when                          |
|-----------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------|----------------------------------------------------------------|
| [`camunda-load-tests`](https://github.com/camunda/camunda-load-tests-helm)                                                                    | `load-tester`                       | `load-tester.enabled` (default: `true`)                        |
| [`prometheus-elasticsearch-exporter`](https://github.com/prometheus-community/helm-charts/tree/main/charts/prometheus-elasticsearch-exporter) | `prometheus-elasticsearch-exporter` | `prometheus-elasticsearch-exporter.enabled` (default: `false`) |
| [`opensearch`](https://opensearch-project.github.io/helm-charts/)                                                                             | `opensearch`                        | `opensearch.enabled` (default: `false`)                        |

## Prometheus Exporter for Elasticsearch/OpenSearch

This Helm Chart can deploy and configure the
[`prometheus-elasticsearch-exporter`](https://github.com/prometheus-community/helm-charts/tree/main/charts/prometheus-elasticsearch-exporter)
subchart. To enable the exporter, configure your values file with:

```yaml
prometheus-elasticsearch-exporter:
  enabled: true
```

If you deploy Elasticsearch or OpenSearch, you most likely want to enable the exporter so Prometheus can scrape metrics from it.

## Keycloak

### Keycloak instance

Keycloak is deployed using the [Keycloak Operator](https://www.keycloak.org/guides#operator) and the [`Keycloak` resource](https://www.keycloak.org/operator/advanced-configuration).

### PostgreSQL cluster

Keycloak is backed by PostgreSQL (PG). The PG cluster is deployed using the [CloudNativePG Operator (CNPG)](https://cloudnative-pg.io/), which manages:

* The PG cluster itself, via a [`Cluster` resource](https://cloudnative-pg.io/docs/1.30/cloudnative-pg.v1#cluster)
* The Kubernetes Secret to share the username/password used by the Keycloak user to connect into PostgreSQL itself.

When a CNPG cluster is created, the CNPG Operator creates the underlying Kubernetes resources
(non-exhaustive list, see [the doc](https://cloudnative-pg.io/docs/1.30/) for the full details):

1. A dedicated Kubernetes service account: the SA exists in the target namespace and represents the
   Kubernetes identity used by the underlying PG cluster
2. The Kubernetes RBAC to allow the SA to read its secrets, etc.
3. New pod(s) to represent the actual PostgreSQL node(s)

The PG cluster is immediately initialized at creation time using the `bootstrap` mechanism, with a
single database owned by a single user. This ensures that when the PG cluster starts, it's already
usable by Keycloak without further provisioning to be done.

## PostgreSQL (Camunda secondary storage)

For load tests that run Camunda with PostgreSQL as secondary storage (`secondary_storage=postgresql`),
this chart can also deploy a second, independent CNPG-managed PostgreSQL cluster
(`postgresql.enabled=true`) for Camunda itself — separate from the Keycloak PostgreSQL cluster
described above.

> [!NOTE]
> Unlike Keycloak's cluster, this one is entirely self-contained in the load
> test namespace and doesn't deploy or require resources outside of that
> namespace (except for the CNPG Operator, indirectly).

### Connection pooling (PgBouncer)

Each broker pod opens one connection pool per physical tenant, so client connections to
Postgres scale as (broker pods x physical tenants) — a 10-broker, 10-tenant load test
produces a structural floor of ~200 mostly-idle connections, which exceeds Postgres's
default `max_connections=100` under load.

Setting `postgresql.pooler.enabled=true` deploys a CNPG [`Pooler`
resource](https://cloudnative-pg.io/docs/1.30/connection_pooling) (PgBouncer, transaction
pooling mode) in front of the Cluster above. A GKE benchmark trial confirmed this collapses
steady-state Postgres connections by roughly an order of magnitude, with zero client
queueing and zero connection timeouts, without raising `max_connections` — including through a
full simultaneous restart of all broker pods.

This is opt-in and off by default. Enabling it also requires switching
`orchestration.data.secondaryStorage.rdbms.url` (in the platform values) to the Pooler's
Service and appending `?prepareThreshold=0` — see `--use-pgbouncer` in
[`../../newLoadTest.sh`](../../newLoadTest.sh), which does both at namespace scaffold time.
Setting `postgresql.pooler.enabled` directly (e.g. via
`additional_load_test_setup_configuration`) without the matching JDBC URL change leaves
Camunda connected straight to the Cluster's `-rw` Service — the Pooler deploys but nothing
routes through it.

### Connection monitoring

`PodMonitor`s are wired automatically for the Cluster's and (when enabled) the Pooler's own connection metrics — no separate toggle.

## k6 tests

In addition to [the "load tester" application](../../../load-tester), it's possible to run tests
using [k6](https://grafana.com/docs/k6/latest/) against the deployed Camunda cluster.

### In which case to use k6 tests?

* This is an experimental, unofficial, partially supported, way to run load tests against Camunda cluster
* Feedback welcome on [#oc-reliability-testing](https://camunda.slack.com/archives/C0807665N8G)
* Use it for ad-hoc REST API tests, and if it's simpler to use than patching the load-tester app

Use the [`load-tester` subchart](../../../load-tester/README.md) instead for:

* Process instance creation and job worker load. k6 has no official Zeebe client.
* Load that needs backpressure or step-down-on-error behavior. Not built into k6.
* Using the official Camunda SDK (Java, node.js, etc.)
* Requests that depend on eventually-consistent data (e.g. reading data right after writing it).
  k6 has no built-in way to share state between VUs or scenarios — this scaffold has no
  cross-executor/scenario communication.

#### What to expect

* Write tests using Javascript. No access to the Camunda node.js SDK though
* Automatic metrics: http, etc.
* Can be distributed across many pods, if needed
* "as code" configuration for adjusting load

See [Grafana Dashboards](https://dashboard.benchmark.camunda.cloud/dashboards/f/bfx7qui8e1bswa/k6)

### Which tests are deployed?

* `default` runs for every storage type and version:
  - checks the cluster topology
  - deploys a small BPMN diagram through `/v2/deployments` (sanity check, not a load test)
* `data-read`: reads data through the search APIs, so it needs a secondary
  storage. It is disabled when read benchmarks are off (for
  `secondary_storage=none` and when the `perform-read-benchmarks` workflow
  input is false). Also disabled for 8.7, which does not have the
  `/v2/process-definitions/search` endpoint (`k6_data_read_supported=false` in
  `stable-87/Makefile`).
* `dmn`: deploys `k6/data/small_decision.dmn` and evaluates it through
  `/v2/decision-definitions/evaluation` with a ramping arrival rate. Disabled by
  default; the `dmn` scenario (see `common.mk`) enables it alone and sizes it for
  6667 evaluations/s. The load profile comes from the `DMN_RATE`, `DMN_RAMP_UP`,
  `DMN_DURATION`, `DMN_PRE_ALLOCATED_VUS` and `DMN_MAX_VUS` variables, set
  through `k6.tests.dmn.env`.
* `dmn-large`: deploys a synthetic decision graph (48 MiB, 12 decisions, 8479
  rules by default), or a portfolio of them, and evaluates it with input
  vectors checking the returned `rate`. The files are too large for a
  ConfigMap: an init container of each runner generates them with
  `k6/generators/generate_tariff_dmn.py` (deterministic, seconds), see
  `k6.tests.<name>.generator` in `values.yaml`. Disabled by default; the
  `dmn-large` scenario enables it alone, with the same `DMN_*` load variables as
  `dmn` (set through `k6.tests.dmn-large.env`). Its shape follows large
  real-world rating models: three large FIRST-hit tables, the largest one
  evaluated four times per request. To compare models and request profiles:
  - In `k6.tests.dmn-large.generator.env`:
    - `DMN_SIZE_MIB` (default 48): file size; the rule counts scale with it.
    - `DMN_KB_PER_RULE` (default: 6.0 like the reference models): average KB
      of XML per rule. Most production models have ~3.8 (more, shorter rules
      per MiB), which costs more engine time per MiB.
    - `DMN_PORTFOLIO_SCALE` (default 0: one model): deploys `round(420 x
      scale)` models with the size mix of a production portfolio (36% under
      1 MiB, 27% 1-5 MiB, 24% 5-10 MiB, 13% 10-20 MiB; those under 0.25 MiB
      are generated at 0.25 MiB) instead of one model, to measure the heap of
      the parsed models per partition leader. 0.1 deploys 42 models (~180
      MiB); 1 deploys ~1.8 GiB, which the k6 runner holds in memory for the
      deployment (raise its memory) and the brokers parse for every leader
      (~10 times the file size).
  - In `k6.tests.dmn-large.env`:
    - `DMN_DEPTH_MIX` (default empty: every vector in turn, which covers every
      rule of a single model): share of requests per depth bucket, the position
      of the matched rule in the largest table: `q1` to `q4` (quarters);
      `payload`, shaped like a measured production request (no specific rule
      of the two largest tables matches, but half their rules pass the first
      columns); or `fallthrough` (the catch-all rules answer). Example:
      `q1=25,q2=25,q3=25,q4=25`. Escape the commas in `--set-string`:
      `k6.tests.dmn-large.env.DMN_DEPTH_MIX=q1=50\,q4=50`.
    - `DMN_EXTRA_VARIABLES` (default 0): variables the model does not read,
      added to every request, as real payloads often carry them; the engine
      copies all the variables for every rule it checks.
    - `DMN_HOT_MODELS` (default empty: same share for every model): traffic
      skew over a portfolio, `<% of the models>:<% of the requests>`, e.g.
      `20:80`.

  Requests are tagged with their `depth` and the `size` of their model. A
  profile close to production: `DMN_KB_PER_RULE=3.8`,
  `DMN_DEPTH_MIX=payload=100` and `DMN_EXTRA_VARIABLES=14`; at 20 MiB with the
  reference density, a `payload` request costs about as much engine time as a
  real 20 MB rating model with a production payload.

### How to add a new test?

1. Add the script under [`k6/scripts/`](k6/scripts) (e.g. `test.mytest.js`).
2. Register it in `k6.tests` in [`values.yaml`](values.yaml):

   ```yaml
   k6:
     tests:
       my-test:
         enabled: true
         script: test.mytest.js
   ```

   This creates a separate `TestRun` named `k6-my-test` that you can interact with using:

   ```
   kubectl get testrun k6-my-test
   ```

> [!NOTE]
> Upgrading a deployed k6 test is not supported. The k6 operator does not restart a `TestRun`
> when it is updated, so the running test keeps the old script and configuration. To apply a
> change, delete the `TestRun` (or the namespace) and deploy again.
>
> [!WARNING]
> k6 will ignore [the `thresholds` options](https://grafana.com/docs/k6/latest/using-k6/thresholds/) due
> to [the `K6_NO_THRESHOLDS` environment variable in `testrun.yaml`](templates/k6/testrun.yaml).
> This keeps k6 from using more and more memory as the test runs.
>
> To read the results, take a look at the Grafana dashboards.

#### How to run the k6 tests locally?

It's possible to run a k6 test locally, it needs:

* The environment variables to authenticate and access the Camunda API.
* Direct access to the Camunda services: you can use `kubectl port-forward`.

```shell
kubectl port-forward -n <load-test-namespace> svc/keycloak 18080:18080
kubectl port-forward -n <load-test-namespace> svc/camunda 8080:8080

# Export the credentials from the secret as environment variables in the current shell.
eval "$(kubectl get secret load-test-credentials -n <load-test-namespace> -o json \
  | jq -r '.data | map_values(@base64d) | @sh "
      export CAMUNDA_CLIENT_ID=\(.clientId)
      export CAMUNDA_CLIENT_SECRET=\(.clientSecret)
      export CAMUNDA_OAUTH_URL=\(.authServer | sub("^https?://[^/]+"; "http://localhost:18080"))
      export CAMUNDA_TOKEN_AUDIENCE=\(.authorizationAudience)"')"

CAMUNDA_BASE_URL=http://localhost:8080 k6 run k6/scripts/test.mytest.js
```

> [!NOTE]
> The OAuth host is rewritten to use the `keycloak` port-forward locally,
> instead of the default in-cluster URL.

#### How to run the k6 unit tests?

The scripts in [`k6/scripts/`](k6/scripts) have unit tests in [`k6/tests/`](k6/tests). They run with
the node.js built-in test runner (node 24 or later) and replace the `k6` and `k6/http` modules with
mocks. Run them from this directory:

```shell
make -C k6 test
```

### Reusing common helpers

[`k6/scripts/lib.camunda.js`](k6/scripts/lib.camunda.js) provides the shared Camunda client:

```js
import { Client } from './lib.camunda.js';
```

* `new Client()` — create it at module level in the test script, so that each VU has its own
  client. It checks the environment variables, fetches and renews the token by itself, and has one
  method per endpoint (e.g. `topology()`), plus `get()` and `post()` to add new ones.
* `client.waitUntilReady()` — call in `setup()` to wait for Camunda to be ready.

## Caveats

Known issues and limitations of the k6 tests:

1. [k6 worker pods are not automatically restarted if they fail](https://github.com/grafana/k6-operator/issues/898): this can skew tests results.
2. Not possible to use the Camunda node.js SDK: k6 is not node.js and the SDK
   depends on [node.js APIs which are not available in k6](https://grafana.com/docs/k6/latest/using-k6/modules/#use-nodejs-modules).
3. When calling HTTP methods, don't set tags with high cardinality values: high
   cardinality labels have a **severe** impact on Prometheus and can render it
   completely unusable.
4. Use `console.xxx()` logging calls carefully: it's easy to create many
   clients with k6, but a logger inside a tight loop can produce massive logs,
   costing a lot of money.
5. `setup()` result is copied into each VU: this `context` is not shared between VUs.
6. k6 [thresholds](https://grafana.com/docs/k6/latest/using-k6/thresholds/) are
   [not reported into Prometheus](https://github.com/grafana/k6/issues/4587).
   Thresholds support is anyway disabled by default as it increases k6 memory
   usage drastically.
7. Don't build large multipart bodies with the [`FormData`
   jslib](https://jslib.k6.io/formdata/0.0.2/index.js): it's not memory efficient for large payload
   (see [k6#3888](https://github.com/grafana/k6/issues/3888),
   [k6#5760](https://github.com/grafana/k6/issues/5760)). Instead, pass an `http.file()` to
   `Client.post()` with `{ multipart: true }`.

