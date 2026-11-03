# Proof of concept: per-tenant cost and lazy per-tenant RDBMS and search resources

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
4. The `long` arrays that grew with the tenants belong to each partition, not to the tenant. Every
   partition, on its leader and its followers, set aside 4 MiB up front for a cache of scheduled
   commands. Letting the cache grow as it fills cuts the idle cost per tenant from 11.1 to
   7.2 MiB in the same setup.
5. A node can throw away a tenant's database resources and search clients once nothing has used
   them for a while (`idle-release-timeout`, 10 minutes by default), and build them again on the
   next use, without closing anything that is in use. This does not lower the cost of a cluster
   at rest. It gives back what a burst of queries made a node build: after one search per tenant
   on one broker, the cost goes from 9.6 back to 7.2 MiB per tenant.
6. Elasticsearch and OpenSearch clients can be built on first use in the same way. On `main`, each
   broker holds two HTTP clients per tenant, and each client starts about one thread per CPU: 107
   threads per tenant on 3 brokers with 18 CPUs. With this change, a broker that does not serve a
   tenant has no client and no thread for it.
7. Tenants that use the same database and the same database user can share one connection pool
   per node (P5), with a guaranteed minimum and a maximum per tenant. A node's connections then
   stay the same as tenants are added. It does not work for tenants with their own database or
   their own credentials. See the shared pool section.

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

# harness variants, combined with the flags above
#   -Dcamunda.pt.cost.storage=elasticsearch     every tenant on one Elasticsearch container,
#                                               its own index prefix, a fresh container per count
#   -Dcamunda.pt.cost.shared-pool=true          every tenant on one H2 database, its own table
#                                               prefix, connection-pool.shared=true
#   -Dcamunda.pt.cost.idle-release-timeout=PT10S  adds the phase after-idle-release
#   -Dcamunda.pt.cost.heap-dump-at=20           writes an .hprof after each phase of 20 tenants

# the behaviour tests
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest='LazyPhysicalTenantResourceTest,PhysicalTenantMyBatisResourcesTest,RdbmsDataSourcesTest,SharedConnectionBudgetTest,PhysicalTenantSearchClientResourcesTest'
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
  broker 0. Phase `after-idle-release`: two idle release timeouts after the last query. With a
  release timeout, the thread count does not settle, because exporters recreate their tenants,
  so the harness waits a fixed 5 s instead, and the `idle` phase may already have released some
  tenants.
- On Elasticsearch, connections are the open HTTP connections of the Elasticsearch node
  (`_nodes/stats/http`), and the instance columns are `RestClient`s and I/O reactors.

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

### Follow-up, 2026-11-03

Same laptop and settings as above: a 3-broker cluster in one JVM, each tenant with one partition
and one copy of it. Every number is the cost of one tenant to the whole cluster, worked out by
fitting a straight line through several tenant counts.

#### How each run is measured

Each run goes through up to three steps, and the harness takes a measurement after each:

1. **No queries yet.** The cluster has started and is sitting still. A tenant's database
   resources (its MyBatis setup and its connection pool) exist only on the one broker that exports
   the tenant's data.
2. **After one search per tenant on one broker.** The harness sends one search for every tenant,
   all to broker 0. Broker 0 has to build resources for every tenant it did not already have, so
   most tenants now exist on two brokers.
3. **After the searches stop and idle tenants are released.** Only in runs with idle release on.
   The harness stops sending searches and waits 20 seconds. A node throws away a tenant's
   resources once nothing has used them for 10 seconds (the default is 10 minutes), so broker 0
   gives back the copies it built in step 2.

Idle release cannot make step 3 cheaper than step 1: the broker that exports a tenant keeps using
it. What it does is make the step 2 cost temporary. Without it, broker 0 keeps its extra copies
until it restarts.

#### RDBMS

| code                                            | step                                      | heap     | threads | connections |
|-------------------------------------------------|-------------------------------------------|----------|---------|-------------|
| `main`                                          | no queries yet                            | 18.0 MiB | 5.0     | 6.0         |
| lazy resources, before the cache fix (above)    | no queries yet                            | 11.1 MiB | 3.0     | 2.0         |
| lazy resources, before the cache fix (above)    | after one search per tenant on one broker | 13.4 MiB | 3.6     | 3.3         |
| lazy resources and the cache fix                | no queries yet                            | 7.2 MiB  | 3.0     | 2.0         |
| lazy resources and the cache fix                | after one search per tenant on one broker | 9.6 MiB  | 3.6     | 3.3         |
| lazy resources, the cache fix and idle release  | after the searches stop and tenants are released | 7.2 MiB  | 3.2     | 1.9         |

The last row is back at the "no queries yet" cost of the same code, as it should be. It comes from
a separate run of 1 to 80 tenants; the rows above it from runs of 1 to 120 tenants. In the idle
release run, the first two steps are not usable on their own: with a 10-second timeout, some
tenants had already been thrown away during startup and during the searches.

The cache fix is the change described under "The `long[]` share" below.

#### Elasticsearch

| code                         | step                                             | threads | search clients |
|------------------------------|--------------------------------------------------|---------|----------------|
| `main`                       | no queries yet                                   | 107     | 6              |
| lazy clients                 | no queries yet                                   | 0       | 0              |
| lazy clients                 | after one search per tenant on one broker        | 12      | 1              |
| lazy clients, idle release   | after the searches stop and tenants are released | 0       | 0              |

These fit 1 to 20 tenants. The heap of an Elasticsearch tenant is small, 1.3 to 1.9 MiB, and too
noisy to compare between rows. The "0 threads" rows are a noisy fit around zero: the thread count
of the cluster no longer grows with the tenants. The raw thread counts of the whole cluster:

| tenants | `main` | lazy, no queries yet | lazy, after the searches | lazy, after release |
|---------|--------|----------------------|--------------------------|---------------------|
| 1       | 506    | 394                  | 415                      | 397                 |
| 10      | 1407   | 267                  | 459                      | 397                 |
| 20      | 2557   | 277                  | 658                      | 282                 |

#### The `long[]` share: the scheduled command cache

The earlier notes found that arrays of `long` grew with the tenants, and could not say what owned
them. A heap dump of 20 tenants on 3 brokers answers it.

Every partition runs a stream processor, on its leader and also on its followers. Each stream
processor keeps a cache of the commands it has scheduled (timer triggers, job time-outs, job
retries after back-off, message expiries), one cache per kind, so four. Each cache was built at its
full size of 100,000 entries straight away, so that it never has to grow. That is a 1 MiB array per
cache, 4 MiB per partition, even when the partition does nothing. With one partition per tenant it
looked like a cost of the tenant; it is a cost of the partition.

The earlier note of "32,873 arrays" was wrong. It counted every `long` array in the heap, most of
them small and unrelated. 80 large arrays are the ones that grow.

The fix lets each cache start small (64 entries) and grow as it fills, up to the same limit. A full
cache uses the same memory as before. In the RDBMS table above, it is the drop from 11.1 to
7.2 MiB.

#### Idle release

After the searches stop, broker 0 throws away the resources it built for them, and each tenant is
back to one copy, on the broker that exports it. The extra 2.4 MiB per tenant from the searches is
gone. On Elasticsearch, no broker keeps a search client afterwards, because an exporter with
nothing to export does not talk to Elasticsearch.

On RDBMS, an exporter with nothing to export still runs some periodic work against the database,
and that builds the tenant again on its broker after each release. With the 10-second timeout of
the harness, such a tenant is thrown away and rebuilt every few seconds. With the 10-minute
default, the periodic work comes round sooner than the timeout, so the exporting broker simply
keeps its tenant. I did not find which periodic task it is.

Two leaks showed up on the way, and both are fixed:

- HikariCP registers its gauges per pool. A released pool's gauges stayed in the meter registries
  above the tenant's own, held the closed pool, and were handed back, still reading the closed
  pool, to the next pool of the same name. Each tenant now registers HikariCP's meters once
  (`StablePoolMetrics`), and they read whichever pool is current.
- HikariCP keeps the connections a thread used in a thread local of that thread. Loaded by the
  system class loader, as in the tests and from the distribution's start scripts, it holds them
  strongly, so a released pool stayed reachable from every thread that used it.
  `RdbmsDataSources` sets `com.zaxxer.hikari.useWeakReferences=true` unless it is set already.

#### Elasticsearch and OpenSearch

On `main`, each node holds two Elasticsearch `RestClient`s per tenant: the tenant's shared client
from `SearchClients`, and one from `ElasticsearchSchemaMigrationStatusProvider`, which opened a
long-lived client per tenant for the upgrade-readiness endpoint. OpenSearch adds a separate async
client. Each client is an Apache async HTTP client whose I/O reactor starts one dispatcher thread
per CPU: 107 threads per tenant over 6 clients is about 18 per client on this 18-CPU laptop. On a
4-CPU node that would be about 4 to 5 threads per client, so 8 to 10 threads per tenant per node,
or 1,300 to 1,600 threads per broker at 160 tenants. I did not measure a 4-CPU node.

With this change, the clients of a tenant are facades over a lazy transport, the migration status
provider reads through the tenant's shared client, and a node that does not serve a tenant has no
client and no thread for it. Querying a tenant on a gateway opens one client there. The heap of
an Elasticsearch tenant is small either way, about 1.5 to 1.9 MiB per tenant on 3 brokers.

#### Shared pool (P5)

`connection-pool.shared=true` puts every tenant that sets it, and uses the same URL, username and
password, on one pool per node. The pool holds each tenant's `guaranteed` connections plus the
largest `shared-burst` of the group. A tenant first takes one of its guaranteed connections, then
competes for the burst, up to its `maximum-pool-size`. A busy tenant can take the whole burst but
never another tenant's guarantee (`SharedConnectionBudgetTest`).

All tenants on one H2 database, each with its own table prefix:

| tenants | connections, own pools | connections, shared pool | startup, own databases | startup, shared database |
|---------|------------------------|--------------------------|------------------------|--------------------------|
| 1       | 2                      | 2                        | 9 s                    | 9 s                      |
| 10      | 20                     | 6                        | 11 s                   | 17 s                     |
| 20      | 40                     | 6                        | 17 s                   | 39 s                     |
| 40      | 80                     | 6                        | 30 s                   | 108 s                    |
| 80      | 160                    | did not start in 10 min  | 55 s                   | did not start in 10 min  |

With the shared pool, a node's connections no longer grow with its tenants: 3 brokers hold 2 each,
the shared pool's minimum idle. Threads per tenant drop from 3.0 to 1.8, with one HikariCP pool
per node instead of one per tenant.

Startup on one shared database grew much faster than with a database per tenant, and 80 tenants
did not start within the 10-minute limit. Each tenant's Liquibase lock table has its own prefix,
so it is not a shared Liquibase lock. I suspect DDL contention inside the one H2 database, but I
did not confirm it, and I did not run it on PostgreSQL. It needs checking before a shared database
is the recommended layout.

Where a shared pool applies:

- It needs tenants whose connections are interchangeable: the same URL, user and password, with
  the tenants separated by table prefix. The load tests that hit #61935 use this layout.
- It does not apply to tenants with their own database, own user, or rotated credentials. That
  includes the dynamic secrets spike, where each tenant's password changes on its own.
- For those, the same budget class in front of each tenant's own pool would cap the connections a
  node checks out across its tenants. It would not cap the idle connections each pool keeps open.
  I did not build that variant.

US-13 arithmetic, 10 brokers, 10 tenants, one partition each, `minimum-idle` 2,
`maximum-pool-size` 10, a database with 97 usable connections:

| setup                                          | idle connections | peak connections |
|------------------------------------------------|------------------|------------------|
| `main`                                         | 200              | 1,000            |
| lazy, exporters only                           | 20               | 100              |
| lazy, every gateway queries every tenant       | 200              | 1,000            |
| lazy + release, after the queries stop         | 20               | 100              |
| shared pool per node, guaranteed 1, burst 8    | 20               | 180              |

The shared pool's peak is 10 nodes × (10 guaranteed + 8 burst). Only the shared pool caps the
peak independently of how traffic is spread over the gateways. To fit 97 connections at peak, a
node can have 9 at most: for example `guaranteed` 0 and `shared-burst` 9, or a lower
`maximum-pool-size` per tenant with own pools.

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
that receives queries for every tenant: it builds every tenant again (+3.5 MiB each), until the
idle release takes them back.

The cache fix takes another 4 MiB off every partition replica, leader or follower. With
replication factor 3 and one partition per tenant, a broker of that shape hosts about 24
replicas, so it saves about 96 MiB per broker on top of the figures above.

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
- **Release after a period without use, not on a leader change.** `idle-release-timeout` (on
  every secondary storage, overridable per tenant, `0` to keep) covers both a gateway whose burst
  of queries has ended and a broker whose leader moved: neither uses the tenant afterwards. A
  release on leader change would also need the gateway case, so I left it to the timer. The
  default of 10 minutes is the pool's own idle timeout for its connections.
- **Leases, so a release never closes anything in use.** Every mapper call, session, request and
  async request holds the tenant through a lease. A release takes the resource away first and
  puts it back if a lease appeared in the meantime. Metrics, the health checks and the
  upgrade-readiness provider use the tenant only while it exists, and do not count as a use;
  otherwise every Prometheus scrape would keep every tenant alive.
- **A pool that can start again.** A closed Hikari pool cannot restart, so each tenant's pool is a
  `ReleasablePool` that replaces its Hikari pool on release. Whoever holds the data source keeps a
  working one. The mappers are cached on the tenant's MyBatis, so they go with it.
- **Search clients are lazy at the transport.** `LazyElasticsearchTransport` and
  `LazyOpenSearchTransport` implement the client libraries' transport interface. The
  `ElasticsearchClient`, `OpenSearchClient` and `OpenSearchAsyncClient` objects every bean holds
  stay as they are. The schema initializer already used its own short-lived client.
- **Shared pool behind a flag.** `connection-pool.shared` defaults to `false`. The budget is a
  semaphore per tenant and one for the burst, in front of any data source
  (`SharedConnectionBudget`).

## What is stubbed or missing

- OpenSearch is built but not measured: the harness runs Elasticsearch only.
- The exporter's own Elasticsearch clients (`ElasticsearchAdapter`, `CamundaBackgroundTaskManagerFactory`)
  are only on brokers that host the partition, and are unchanged. Their I/O reactors also start
  one thread per CPU; a smaller `IOReactorConfig.ioThreadCount` would cut that for every client.
- Vendor detection over a connection, for URLs that do not name the vendor, still runs at startup.
  It now uses an unpooled connection, but a tenant whose database is down still fails startup in
  that case. That was already the case before, and the logs warn about it. The fix is to set
  `database-vendor-id`.
- Schema initialization still runs on every node for every tenant, over short-lived connections.
- P5: the shared pool is a proof of concept behind a flag. The slow startup on one shared H2
  database is not explained, and it is not tested on PostgreSQL or under load. The variant that
  caps a node's connections across tenants with their own pools is not built.
- The RDBMS periodic task that recreates an idle exporter's tenant is not identified.
- A test that 80 or more tenants start in a 2 GiB heap: not built. See the extrapolation above.

## Open questions for the team

1. Embedded gateways: if a load balancer spreads every tenant's REST traffic over every gateway,
   each gateway creates every tenant again. Is topology-aware routing (direction item 5), or a
   release of idle tenants, needed for lazy creation to pay off under load?
2. Is 10 minutes the right default for `idle-release-timeout`, and should it be a public property
   at all? The first statement after a release pays about 70 ms to rebuild MyBatis.
3. Should schema initialization run once for each tenant (for example on the partition leader),
   instead of on every node?
4. Is `lazy-initialization` worth a public configuration property, or should lazy creation be
   unconditional?
5. Is the scheduled command cache fix worth taking to `main` on its own? It applies with or without
   physical tenants: 4 MiB per partition replica.
6. Should tenants of one installation share a database and user (shared pool possible), or each
   have their own (isolation, own credentials, dynamic secrets)? The answer decides whether P5 is
   a shared pool or a connection cap.

## Overlap with other work

- Carlo's per-tenant actor pool and CPU-limit spikes: the harness gives one place to measure the
  threads per tenant that a pool per tenant adds. They do not change the RDBMS wiring.
- Direction item 12 / P6 (separate pools for the exporter and queries) and P5 (shared pool): with
  this change, the pool starts with the first statement. A split pool or a shared pool can replace
  the `HikariDataSource` in `RdbmsDataSources`. The rest stays the same.
- 

# 61935: brokers no longer open pools for tenants whose partitions they do not lead.

