# Proof of concept: per-tenant cost and lazy per-tenant RDBMS resources

Branch `pt-poc-lazy-tenant-resources`, based on `main` at `3214417b37a`. Local only, not reviewed.

## What it proves

1. The cost of a physical tenant on a node can be measured in a reproducible way. The harness
   `PhysicalTenantCostHarnessIT` starts an in-process cluster for a list of tenant counts and fits
   heap, threads, connections and startup time against the tenant count.
2. A node can create a tenant's MyBatis configuration and Hikari pool only when it uses the tenant.
   A broker uses a tenant when it exports one of its partitions. A gateway uses a tenant when it
   serves one of its requests. The existing readers, writers, services and authorization beans
   stay as they are. They hold mapper proxies that build the tenant on the first statement.
3. On a 3-broker cluster, each tenant with one single-replica partition, the change cuts the idle
   cost per tenant from 18.0 to 11.1 MiB of heap, from 5.0 to 3.0 threads and from 6 to 2
   connections, cluster-wide. On a broker, a tenant that the broker does not host costs about
   0.42 MiB instead of about 3.9 MiB.

## How to run

```bash
# the harness (opt-in, about 8 minutes for 1..120 tenants on 3 brokers)
./mvnw verify -pl qa/acceptance-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantCostHarnessIT -Dcamunda.pt.cost.harness=true \
  -Dcamunda.pt.cost.tenants=1,10,20,40,80,120 -Dcamunda.pt.cost.brokers=3 \
  -Dcamunda.pt.cost.label=after -Djvm.test.extra.args=-Xmx8g
# optional: -Dcamunda.pt.cost.output=<dir> -Dcamunda.pt.cost.settle=PT45S
#           -Dcamunda.pt.cost.pool-idle-timeout=10s

# the MyBatis read/write split measurement (opt-in)
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest=MyBatisStatementSplitCostTest -Dcamunda.pt.cost.harness=true

# the behaviour tests
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest='LazyPhysicalTenantResourceTest,PhysicalTenantMyBatisResourcesTest,RdbmsDataSourcesTest'
./mvnw verify -pl qa/acceptance-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantLazyRdbmsResourcesIT
```

The harness writes `pt-cost-<label>.md` and `.csv` to `target/pt-cost`. The Markdown file also
lists the 20 classes that grew most for each sample.

How the harness measures:

- All brokers run in the test JVM, so the numbers are for the whole cluster. Each tenant has one
  partition with one replica, so each of 3 brokers hosts a third of the tenants.
- The tenants' databases are in-memory H2 databases in an H2 TCP server in a child JVM. They do
  not count towards the heap.
- Heap: used heap after repeated full GCs until it changes by less than 1%, minus the same value
  before the cluster starts.
- Classes: `GC.class_histogram` through the `DiagnosticCommand` MBean, minus the baseline.
- Connections: open sessions as the H2 server counts them (`INFORMATION_SCHEMA.SESSIONS`).
- Startup: from `TestCluster.start()` until every node is ready and every tenant's schema is
  initialized on every node.
- Phase `idle`: after startup, when the thread count has been stable for 5 s. Phase
  `queried-on-one-gateway`: after one REST search for every tenant through the gateway of
  broker 0.

## Results

3 brokers, idle, before (`main`) / after (this branch). Same machine, same JVM settings
(`-Xmx8g`), measured on 2026-10-28.

| tenants |   startup   |      heap       |  threads  | connections | MyBatis configurations |
|---------|-------------|-----------------|-----------|-------------|------------------------|
| 1       | 10 s / 9 s  | 125 / 117 MiB   | 273 / 271 | 7 / 2       | 3 / 1                  |
| 10      | 13 s / 12 s | 246 / 175 MiB   | 306 / 286 | 60 / 20     | 30 / 10                |
| 20      | 19 s / 20 s | 428 / 286 MiB   | 356 / 316 | 121 / 40    | 60 / 20                |
| 40      | 33 s / 34 s | 790 / 515 MiB   | 456 / 376 | 240 / 80    | 120 / 40               |
| 80      | 61 s / 59 s | 1517 / 964 MiB  | 656 / 496 | 482 / 160   | 240 / 80               |
| 120     | 92 s / 89 s | 2246 / 1410 MiB | 856 / 616 | 723 / 240   | 360 / 120              |

Fitted cost per tenant, whole 3-broker cluster (least squares, R² ≥ 0.993 for every fit):

|                                            |   heap   | threads | connections | startup |
|--------------------------------------------|----------|---------|-------------|---------|
| before, idle                               | 18.0 MiB | 5.0     | 6.0         | 0.70 s  |
| after, idle                                | 11.1 MiB | 3.0     | 2.0         | 0.67 s  |
| after, every tenant queried on one gateway | 13.4 MiB | 3.6     | 3.3         |         |

On `main`, the queried phase is the same as idle, because every node already holds every tenant.

Single broker, after (every tenant hosted, so lazy creation has nothing to skip): 10.2 MiB,
2.9 threads and 2 connections per tenant. From this and the 3-broker fit, a broker spends about
10.2 MiB on a tenant that it hosts and about 0.42 MiB on a tenant that it does not host. On `main`,
a tenant that it does not host costs about 3.9 MiB.

Creating a tenant's MyBatis on first use took 71 ms median, 104 ms p90 and 382 ms max (172
creations, from the log line of `LazyPhysicalTenantResource`). The first statement of a tenant on
a node pays this once.

### MyBatis read/write split (P7)

One tenant's MyBatis graph (PostgreSQL statements, 20 graphs averaged):

|                   graph                    | KiB  | statements |
|--------------------------------------------|------|------------|
| full                                       | 3333 | 283        |
| read statements only                       | 3011 | 114        |
| write statements only, without result maps | 604  | 169        |

The read statements and their result maps are 90% of the graph. A gateway-only graph saves 10%,
so the split is not worth it for gateways. An exporter-only graph would save about 80%, but the
exporter also runs a few selects, so the real saving is smaller. With lazy creation, an
exporter-only graph matters only on a broker that exports a tenant whose queries it never serves:
a standalone broker, or an embedded gateway that gets no traffic for the tenant. I did not
implement the split.

### Extrapolation to the 8.10 failure (160 tenants, 20 brokers, about 2 GiB heap)

I did not run this shape. 20 brokers do not fit in one local JVM. With the measured per-tenant
costs and one leader partition for each tenant:

- before: 8 hosted × 10.2 MiB + 152 not hosted × 3.9 MiB ≈ 670 MiB per broker for tenants;
- after, idle: 8 × 10.2 + 152 × 0.42 ≈ 145 MiB per broker.

The MyBatis graph is 3.3 MiB here, against about 4.2 MiB in the 8.10 heap dumps. PR #66209
explains the difference. With replication factor 3, a broker also hosts follower partitions. The
harness does not measure them, because it uses one replica. The gain disappears for a gateway
that receives queries for every tenant: it builds every tenant again (+3.5 MiB each).

## Design decisions

- **Lazy creation at the mapper level, not at the bean level.** `RdbmsMapperBundle` gets mapper
  proxies (`LazyMappers`) and a `LazySqlSessionFactory`. Both resolve the tenant's
  `PhysicalTenantMyBatis` through one `LazyPhysicalTenantResource`. About ten configurations
  iterate `readersByPhysicalTenant()` at startup to build authorization checkers, resource access
  controllers and web-session clients. None of them had to change. The objects they create are
  small. The fitted cost of a tenant that the node does not host is about 0.42 MiB.
- **The pool is lazy because Hikari is lazy.** `new HikariDataSource()` starts its pool on the
  first `getConnection()`. The PoC removes every startup caller of that method:
  - schema initialization, the migration-status provider and vendor detection use a new unpooled
    data source (`UnpooledDataSource`, which wraps Hikari's `DriverDataSource`, so no new
    dependency);
  - the row-count gauges read only tenants that have been created;
  - the health indicator checks the pool only of a created tenant;
  - the JDBC driver debug log skips pools that have not started.
- **Failure handling.** `LazyPhysicalTenantResource` creates the resource once under concurrent
  use. A failed creation throws `PhysicalTenantResourceUnavailableException`, which names the
  tenant. The holder records the failure and does not retry for 5 s. A database that cannot be
  reached does not fail the creation, because building MyBatis needs no connection. That tenant's
  statements fail on the connection. The existing schema-initialization gate still rejects its
  requests with 503 and `Retry-After`. `PhysicalTenantLazyRdbmsResourcesIT` checks this.
- **Health.** The per-tenant `rdbms` contributor reports a tenant that has not been created as `UP`
  with `state: NOT_CREATED`. A failed creation is `DOWN`, and a created tenant gets the usual
  `DataSourceHealthIndicator`. A database that cannot be reached is still reported per tenant by
  the schema-initialization indicator.
- **Config flag.** `camunda.data.secondary-storage.rdbms.lazy-initialization` defaults to `true`,
  and a tenant can override it. `false` builds the tenant's MyBatis at startup. It is a pre-warm
  for a latency-sensitive tenant and a way back to the old behaviour. The pool still starts on the
  first statement, as before for any tenant.
- **No release of idle tenants.** A tenant that a node created stays created until the node stops.
  Releasing after a period of inactivity, or after a leader moves, fits `LazyPhysicalTenantResource`
  (close, then allow creation again). I left it out because it needs a policy decision.

## What is stubbed or missing

- Elasticsearch/OpenSearch: not changed. `PhysicalTenantSearchClientReadersConfiguration` still
  builds a client for every tenant at startup. `LazyPhysicalTenantResource` would fit a lazy
  `DocumentBasedSearchClient`. The harness does not measure ES.
- Vendor detection over a connection, for URLs that do not name the vendor, still runs at startup.
  It now uses an unpooled connection, but a tenant whose database is down still fails startup in
  that case. That was already the case before, and the logs warn about it. The fix is to set
  `database-vendor-id`.
- Schema initialization still runs on every node for every tenant, over short-lived connections.
- P5, one shared pool across tenants with a minimum and a maximum per tenant: not built.
- A test that 80 or more tenants start in a 2 GiB heap: not built. See the extrapolation above.
- A long-lived `[J` share stays the same before and after: about 1.4 MiB of `long[]` per tenant per
  broker (32,873 arrays, 512 MB at 120 tenants on 3 brokers). It grows with tenants × brokers, not
  with hosted partitions. I did not find the owner. Histogram buckets of per-tenant metrics are a
  guess, not verified. It is the next largest cost for a tenant that a node does not host.

## Open questions for the team

1. Embedded gateways: if a load balancer spreads every tenant's REST traffic over every gateway,
   each gateway creates every tenant again. Is topology-aware routing (direction item 5), or a
   release of idle tenants, needed for lazy creation to pay off under load?
2. Should a node release a tenant's MyBatis and pool when it loses leadership of the tenant's last
   partition, or after a period of inactivity? This also decides when connections go back to the
   database.
3. Should schema initialization run once for each tenant (for example on the partition leader),
   instead of on every node?
4. Is `lazy-initialization` worth a public configuration property, or should lazy creation be
   unconditional?
5. What owns the `long[]` share of about 1.4 MiB per tenant per broker?

## Overlap with other work

- Carlo's per-tenant actor pool and CPU-limit spikes: the harness gives one place to measure the
  threads per tenant that a pool per tenant adds. They do not change the RDBMS wiring.
- Direction item 12 / P6 (separate pools for the exporter and queries) and P5 (shared pool): with
  this change, the pool starts with the first statement. A split pool or a shared pool can replace
  the `HikariDataSource` in `RdbmsDataSources`. The rest stays the same.
- 

# 61935: brokers no longer open pools for tenants whose partitions they do not lead.

