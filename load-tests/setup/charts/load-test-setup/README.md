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

