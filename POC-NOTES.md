# Proof of concept: what a physical tenant costs a node, and how to make it pay only where used

Branch `pt-poc-lazy-tenant-resources`, based on `main` at `3214417b37a`. Pushed, no pull request,
not reviewed.

## Words used in these notes

- **Tenant**: a physical tenant.
- **Database resources of a tenant** (RDBMS): two things a node builds for each tenant so that it
  can talk to the tenant's database. The *MyBatis setup* is the parsed set of SQL statements,
  about 3.3 MiB per tenant. The *connection pool* (HikariCP) holds open connections to the
  database, at least 2 once it has started.
- **Search client** (Elasticsearch or OpenSearch): the HTTP client a node uses to talk to a
  tenant's search cluster. Each one starts its own threads.
- **Exporter**: the part of a broker that copies a partition's records into the tenant's database
  or search cluster. Only the broker that leads the partition runs it.
- **Gateway**: the part of a node that answers API requests. Every broker here has one built in,
  and any of them can be asked about any tenant.
- **Lazy**: built on first use instead of at startup.
- **Idle release**: throwing a tenant's resources away once nothing has used them for a while, and
  building them again on the next use.
- **The harness**: `PhysicalTenantCostHarnessIT`, the test that measures all of this.

## What it shows

1. What a tenant costs a node can be measured in a repeatable way. The harness starts a cluster
   for a list of tenant counts and draws a straight line through heap, threads, connections and
   startup time against the number of tenants. The slope is the cost of one tenant.
2. A node can build a tenant's database resources only when it uses the tenant: a broker when it
   exports one of the tenant's partitions, a gateway when it answers one of the tenant's requests.
   The code that reads and writes data does not change. It holds stand-ins that build the real
   resources on the first SQL statement.
3. On 3 brokers, with one partition per tenant, this cut what a cluster at rest spends on each
   tenant from 18.0 to 11.1 MiB of heap, from 5.0 to 3.0 threads and from 6 to 2 connections. A
   broker spends about 0.42 MiB on a tenant whose data it does not export, against 3.9 MiB on
   `main`.
4. The `long` arrays that grew with the tenants belong to each partition, not to the tenant. Every
   partition, on its leader and its followers, set aside 4 MiB up front for a cache of scheduled
   commands. Letting the cache grow as it fills cuts the cost per tenant at rest from 11.1 to
   7.2 MiB in the same setup.
5. A node can throw away a tenant's database resources and search clients once nothing has used
   them for a while (`idle-release-timeout`, 10 minutes by default), and build them again on the
   next use, without closing anything that is in use. This does not lower the cost of a cluster
   at rest. It gives back what a burst of queries made a node build: after one search per tenant
   on one broker, the cost goes from 9.6 back to 7.2 MiB per tenant.
6. Search clients can be built on first use in the same way. On `main`, each broker holds two
   search clients per tenant, and each client starts about one thread per CPU: 107 threads per
   tenant on 3 brokers with 18 CPUs. With this change, a broker that does not serve a tenant has no
   client and no thread for it.
7. Tenants that use the same database and the same database user can share one connection pool
   per node (P5), with a guaranteed minimum and a maximum per tenant. A node's connections then
   stay the same as tenants are added. It does not work for tenants with their own database or
   their own credentials. See "Shared connection pool" below.

## How to run

```bash
# the harness (opt-in, about 8 minutes for 1..120 tenants on 3 brokers)
./mvnw verify -pl qa/acceptance-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantCostHarnessIT -Dcamunda.pt.cost.harness=true \
  -Dcamunda.pt.cost.tenants=1,10,20,40,80,120 -Dcamunda.pt.cost.brokers=3 \
  -Dcamunda.pt.cost.label=after -Djvm.test.extra.args=-Xmx8g
# optional: -Dcamunda.pt.cost.output=<dir> -Dcamunda.pt.cost.settle=PT45S
#           -Dcamunda.pt.cost.pool-idle-timeout=10s

# variants, added to the command above
#   -Dcamunda.pt.cost.storage=elasticsearch       tenants on Elasticsearch instead of RDBMS
#   -Dcamunda.pt.cost.shared-pool=true            all tenants on one database, sharing a pool
#   -Dcamunda.pt.cost.idle-release-timeout=PT10S  turns idle release on, and adds step 3 below
#   -Dcamunda.pt.cost.heap-dump-at=20             writes a heap dump after each step at 20 tenants

# the measurement of the MyBatis read/write split (opt-in)
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest=MyBatisStatementSplitCostTest -Dcamunda.pt.cost.harness=true

# the behaviour tests
./mvnw verify -pl dist -Dquickly -DskipTests=false -DskipITs \
  -Dtest='LazyPhysicalTenantResourceTest,PhysicalTenantMyBatisResourcesTest,RdbmsDataSourcesTest,SharedConnectionBudgetTest,PhysicalTenantSearchClientResourcesTest'
./mvnw verify -pl qa/acceptance-tests -Dquickly -DskipTests=false -DskipUTs \
  -Dit.test=PhysicalTenantLazyRdbmsResourcesIT
```

The harness writes `pt-cost-<label>.md` and `.csv` to `target/pt-cost`. The Markdown file also
lists the 20 classes whose instances grew most in each measurement.

## How the harness measures

The setup:

- All brokers run inside the test's own JVM, so every number is for the whole cluster, not for
  one broker.
- Each tenant has one partition, with no copies on other brokers. On 3 brokers, each broker
  exports a third of the tenants.
- On RDBMS, each tenant has its own in-memory H2 database, in a separate process so that it does
  not count towards the heap. With `shared-pool=true`, all tenants share one database instead,
  each with its own table name prefix, as in the load tests.
- On Elasticsearch, all tenants share one Elasticsearch container, each with its own index
  prefix. Each tenant count gets a fresh container.

Each run goes through up to three steps, and measures after each:

1. **No queries yet.** The cluster has started and is sitting still. A tenant's resources exist
   only on the one broker that exports its data. The harness waits until the thread count has
   not changed for 5 seconds.
2. **After one search per tenant on one broker.** The harness sends one search for every tenant,
   all to broker 0. Broker 0 has to build resources for every tenant it did not already have, so
   most tenants now exist on two brokers.
3. **After the searches stop and idle tenants are released.** Only in runs with idle release on.
   The harness stops sending searches and waits 20 seconds. With the 10-second timeout of these
   runs, broker 0 gives back the copies it built in step 2.

Idle release cannot make step 3 cheaper than step 1: the broker that exports a tenant keeps using
it. What it does is make the step 2 cost temporary. Without it, broker 0 keeps its extra copies
until it restarts.

In runs with idle release on, the thread count never stays still for 5 seconds, because exporters
keep rebuilding their tenants (see "Idle release" below). Those runs wait a fixed 5 seconds before
each measurement instead. Some tenants are already thrown away during startup and during the
searches, so only step 3 of those runs is usable.

What is measured:

- **Heap**: the heap in use after the JVM has collected garbage until the figure stops moving,
  minus the same figure before the cluster started.
- **Threads**: live threads in the JVM, minus those before the cluster started.
- **Connections**: on RDBMS, the open sessions that the H2 server reports. On Elasticsearch, the
  open HTTP connections that the Elasticsearch node reports.
- **Instances**: the number of objects of a few classes, from the JVM's class histogram. On RDBMS,
  Hikari pools and MyBatis setups. On Elasticsearch, search clients.
- **Startup**: from starting the cluster until every node is ready and every tenant's schema is
  in place on every node.

## Results

### First round, 2026-10-28: lazy database resources

3 brokers, step 1 (no queries yet), `main` / this branch. Same laptop, same JVM settings
(`-Xmx8g`).

| tenants |   startup   |      heap       |  threads  | connections | MyBatis setups |
|---------|-------------|-----------------|-----------|-------------|----------------|
| 1       | 10 s / 9 s  | 125 / 117 MiB   | 273 / 271 | 7 / 2       | 3 / 1          |
| 10      | 13 s / 12 s | 246 / 175 MiB   | 306 / 286 | 60 / 20     | 30 / 10        |
| 20      | 19 s / 20 s | 428 / 286 MiB   | 356 / 316 | 121 / 40    | 60 / 20        |
| 40      | 33 s / 34 s | 790 / 515 MiB   | 456 / 376 | 240 / 80    | 120 / 40       |
| 80      | 61 s / 59 s | 1517 / 964 MiB  | 656 / 496 | 482 / 160   | 240 / 80       |
| 120     | 92 s / 89 s | 2246 / 1410 MiB | 856 / 616 | 723 / 240   | 360 / 120      |

Cost of one tenant to the whole cluster (the slope of the straight line through the counts above;
every line fits its points closely, R² ≥ 0.993):

| code     | step                                      |   heap   | threads | connections | startup |
|----------|-------------------------------------------|----------|---------|-------------|---------|
| `main`   | no queries yet                            | 18.0 MiB | 5.0     | 6.0         | 0.70 s  |
| lazy     | no queries yet                            | 11.1 MiB | 3.0     | 2.0         | 0.67 s  |
| lazy     | after one search per tenant on one broker | 13.4 MiB | 3.6     | 3.3         |         |

On `main`, step 2 costs the same as step 1, because every node already holds every tenant.

With a single broker, which exports every tenant so laziness has nothing to skip, a tenant costs
10.2 MiB, 2.9 threads and 2 connections. Combined with the 3-broker figure, that puts a tenant at
about 10.2 MiB on the broker that exports it, and about 0.42 MiB on a broker that does not. On
`main`, a broker that does not export a tenant still spends about 3.9 MiB on it.

Building a tenant's MyBatis setup on first use took 71 ms at the median, 104 ms at the 90th
percentile and 382 ms at worst (172 builds, from the log). The first SQL statement of a tenant on
a node pays this once.

### Second round, 2026-11-03

Same laptop and settings.

#### RDBMS

| code                                            | step                                             | heap     | threads | connections |
|-------------------------------------------------|--------------------------------------------------|----------|---------|-------------|
| `main`                                          | no queries yet                                   | 18.0 MiB | 5.0     | 6.0         |
| lazy, before the cache fix (first round)        | no queries yet                                   | 11.1 MiB | 3.0     | 2.0         |
| lazy, before the cache fix (first round)        | after one search per tenant on one broker        | 13.4 MiB | 3.6     | 3.3         |
| lazy and the cache fix                          | no queries yet                                   | 7.2 MiB  | 3.0     | 2.0         |
| lazy and the cache fix                          | after one search per tenant on one broker        | 9.6 MiB  | 3.6     | 3.3         |
| lazy, the cache fix and idle release            | after the searches stop and tenants are released | 7.2 MiB  | 3.2     | 1.9         |

The last row is back at the "no queries yet" cost of the same code, as it should be. It comes from
a separate run of 1 to 80 tenants; the rows above it from runs of 1 to 120 tenants.

The cache fix is described under "The growing `long` arrays: the scheduled command cache" below.

#### Elasticsearch

| code                         | step                                             | threads | search clients |
|------------------------------|--------------------------------------------------|---------|----------------|
| `main`                       | no queries yet                                   | 107     | 6              |
| lazy clients                 | no queries yet                                   | 0       | 0              |
| lazy clients                 | after one search per tenant on one broker        | 12      | 1              |
| lazy clients, idle release   | after the searches stop and tenants are released | 0       | 0              |

These come from 1 to 20 tenants. An Elasticsearch tenant needs little heap, 1.3 to 1.9 MiB, and
the heap figures are too noisy to compare between rows. The "0" thread rows are a noisy line
around zero: the cluster's thread count no longer grows with the tenants. The raw thread counts of
the whole cluster:

| tenants | `main` | lazy, no queries yet | lazy, after the searches | lazy, after release |
|---------|--------|----------------------|--------------------------|---------------------|
| 1       | 506    | 394                  | 415                      | 397                 |
| 10      | 1407   | 267                  | 459                      | 397                 |
| 20      | 2557   | 277                  | 658                      | 282                 |

#### The growing `long` arrays: the scheduled command cache

The first round found that arrays of `long` grew with the tenants, and could not say what owned
them. A heap dump of 20 tenants on 3 brokers answers it.

Every partition runs a stream processor, on its leader and also on its followers. Each stream
processor keeps a cache of the commands it has scheduled (timer triggers, job time-outs, job
retries after back-off, message expiries), one cache per kind, so four. Each cache was built at its
full size of 100,000 entries straight away, so that it never has to grow. That is a 1 MiB array per
cache, 4 MiB per partition, even when the partition does nothing. With one partition per tenant it
looked like a cost of the tenant; it is a cost of the partition.

The first round's figure of "32,873 arrays" was wrong. It counted every `long` array in the heap,
most of them small and unrelated. 80 large arrays are the ones that grow.

The fix lets each cache start small (64 entries) and grow as it fills, up to the same limit. A full
cache uses the same memory as before. In the RDBMS table above, it is the drop from 11.1 to
7.2 MiB. It applies with or without physical tenants.

#### Idle release

After the searches stop, broker 0 throws away the resources it built for them, and each tenant is
back to one copy, on the broker that exports it. The extra 2.4 MiB per tenant from the searches is
gone. On Elasticsearch, no broker keeps a search client afterwards, because an exporter with
nothing to export does not talk to Elasticsearch.

On RDBMS, an exporter with nothing to export still does some periodic work against the database,
and that builds the tenant again on its broker after each release. With the 10-second timeout of
the harness, such a tenant is thrown away and rebuilt every few seconds. I did not find which
periodic task it is. If it runs more often than every 10 minutes, then with the default timeout
the exporting broker simply keeps its tenant; I did not measure that.

Two memory leaks showed up on the way, and both are fixed:

- **Pool metrics held released pools.** HikariCP registers its metrics (open connections, active
  connections and so on) once per pool. When a released pool was closed, its metrics stayed
  registered, and they kept the closed pool in memory. Worse, the next pool of the same tenant was
  given those same metrics, which went on reading the closed pool, so the tenant's pool metrics
  were wrong after the first release. Each tenant now registers its pool metrics once, and they
  read whichever pool is current (`StablePoolMetrics`).
- **Threads held released pools.** HikariCP remembers, in each thread that used a pool, which
  connections that thread used. Depending on how HikariCP is loaded, it remembers them with
  ordinary references, which keep the pool in memory for as long as the thread lives. That is the
  case in the tests and, I expect, when Camunda starts from its start scripts. HikariCP has a
  switch to use references that do not keep the pool alive; `RdbmsDataSources` turns it on
  (`com.zaxxer.hikari.useWeakReferences=true`) unless it is already set.

#### Search clients

On `main`, each broker holds two search clients per tenant:

- the tenant's main client, which every part of the node uses;
- a second, long-lived one, opened for each tenant only to answer the upgrade-readiness check
  (`ElasticsearchSchemaMigrationStatusProvider`).

OpenSearch adds a third, for requests that do not wait for their answer. Each client starts about
one thread per CPU to handle its network traffic. Here, 107 threads per tenant over 6 clients is
about 18 threads per client, on a laptop with 18 CPUs. On a node with 4 CPUs that would be about 4
to 5 threads per client, so 8 to 10 threads per tenant per node, or 1,300 to 1,600 threads per
broker at 160 tenants. I did not measure a 4-CPU node.

With this change, a tenant's search client opens its network connection, and starts its threads,
on its first request. The upgrade-readiness check uses the tenant's main client instead of its
own. A broker that does not serve a tenant has no client and no thread for it. A search for a
tenant on a broker opens one client there.

OpenSearch is wired the same way, but the harness only measures Elasticsearch.

#### Shared connection pool (P5)

With `connection-pool.shared=true`, tenants that set it and use the same database address, user
and password share one connection pool on each node, instead of one pool each. The shared pool is
sized from each tenant's settings:

- `guaranteed` (default 1): connections only that tenant can use.
- `shared-burst` (default 8): connections any tenant of the group can use beyond its guarantee.
  The pool holds every tenant's guarantee plus the largest burst any of them sets.
- `maximum-pool-size`: the most connections one tenant can hold at once.

A tenant first takes one of its own guaranteed connections, and only then one from the shared
burst. A busy tenant can take the whole burst but never another tenant's guaranteed connections,
so the others always get one (`SharedConnectionBudgetTest`).

All tenants on one H2 database, each with its own table prefix, 3 brokers, step 1:

| tenants | connections, pool per tenant | connections, shared pool | startup, database per tenant | startup, one shared database |
|---------|------------------------------|--------------------------|------------------------------|------------------------------|
| 1       | 2                            | 2                        | 9 s                          | 9 s                          |
| 10      | 20                           | 6                        | 11 s                         | 17 s                         |
| 20      | 40                           | 6                        | 17 s                         | 39 s                         |
| 40      | 80                           | 6                        | 30 s                         | 108 s                        |
| 80      | 160                          | did not start in 10 min  | 55 s                         | did not start in 10 min      |

With the shared pool, the connections no longer grow with the tenants: each of the 3 brokers
keeps 2 open, the pool's minimum. Threads per tenant drop from 3.0 to 1.8, with one pool per node
instead of one per tenant.

Startup got much slower with every tenant in one database, and 80 tenants did not start within
the harness's 10-minute limit. Each tenant takes its schema lock in its own table, so tenants are
not waiting for each other's lock. I suspect the tenants' table creation slowing each other down
inside the one H2 database, but I did not confirm it, and I did not try PostgreSQL. It needs
checking before one shared database is a recommended setup.

Where a shared pool works, and where it does not:

- It needs tenants whose connections are interchangeable: the same database address, user and
  password, with the tenants kept apart by table prefix. The load tests that hit #61935 are set
  up this way.
- It does not work for tenants with their own database, their own database user, or credentials
  that change independently. That includes the dynamic secrets spike, where each tenant's
  password changes on its own.
- For those tenants, the same guarantee-and-burst rule could sit in front of each tenant's own
  pool. It would cap the connections a node has in use across its tenants, but not the idle
  connections each pool keeps open. I did not build that.

The connection arithmetic for user story US-13: 10 brokers and 10 tenants, one partition each,
pools of at least 2 and at most 10 connections, against a database that allows 97 connections.

| setup                                                        | connections at rest | connections at peak |
|--------------------------------------------------------------|---------------------|---------------------|
| `main`                                                       | 200                 | 1,000               |
| lazy, only the exporting brokers use each tenant             | 20                  | 100                 |
| lazy, every broker answers queries for every tenant          | 200                 | 1,000               |
| lazy with idle release, once those queries have stopped      | 20                  | 100                 |
| shared pool per node, 1 guaranteed per tenant, burst of 8    | 20                  | 180                 |

The shared pool's peak is 10 nodes × (10 guaranteed + 8 burst). It is the only setup whose peak
does not depend on how the queries are spread over the brokers. To stay within 97 connections at
peak, each node can have at most 9: for example no guaranteed connections and a burst of 9, or
smaller pools per tenant.

### Splitting MyBatis into read and write statements (P7)

One tenant's MyBatis setup, PostgreSQL statements, averaged over 20 builds:

| statements                                 | KiB  | statements |
|--------------------------------------------|------|------------|
| all                                        | 3333 | 283        |
| read statements only                       | 3011 | 114        |
| write statements only, without result maps | 604  | 169        |

The read statements, and the result maps that turn rows into objects, are 90% of the setup. A
setup for a node that only answers queries would save 10%, which is not worth it. A setup for a
node that only exports would save about 80%, less in practice because the exporter also runs a
few reads. With lazy building, that only matters on a broker that exports a tenant but never
answers its queries: a broker without a gateway, or one whose gateway gets no traffic for the
tenant. I did not build the split.

### Extrapolation to the 8.10 failure (160 tenants, 20 brokers, about 2 GiB heap)

I did not run this setup; 20 brokers do not fit in one JVM on a laptop. With the costs measured
above, and one partition per tenant led by one broker:

- `main`: 8 exported tenants × 10.2 MiB + 152 other tenants × 3.9 MiB ≈ 670 MiB per broker;
- lazy, at rest: 8 × 10.2 + 152 × 0.42 ≈ 145 MiB per broker.

The MyBatis setup is 3.3 MiB here, against about 4.2 MiB in the 8.10 heap dumps; PR #66209
explains the difference. With 3 copies of each partition, a broker also holds follower copies,
which the harness does not measure. A broker that answers queries for every tenant builds every
tenant again (+3.5 MiB each), until idle release takes them back.

The cache fix takes another 4 MiB off every copy of a partition, leader or follower. With 3 copies
and one partition per tenant, a broker in this setup holds about 24 copies, so the fix saves about
96 MiB per broker on top of the figures above.

## Design decisions

- **The stand-ins are at the level of the SQL mappers, not of the beans.** The code that reads and
  writes data gets mapper objects (`LazyMappers`) and a session factory (`LazySqlSessionFactory`)
  that build the tenant's real MyBatis setup on first use, through one holder per tenant
  (`LazyPhysicalTenantResource`). About ten configurations go through every tenant at startup to
  build authorization checks, access controls and web-session clients, and none of them had to
  change. What they build is small: about 0.42 MiB for a tenant a broker does not export.
- **The pool starts late because HikariCP already does.** A HikariCP pool opens its first
  connection on the first request for one. The change removes every caller that asked for one at
  startup:
  - schema setup, the upgrade-readiness check and database vendor detection now use a plain
    connection that is closed straight after (`UnpooledDataSource`, built on a HikariCP class, so
    no new dependency);
  - the table row-count metrics only read tenants that are in use;
  - the health check only checks the pool of a tenant that is in use;
  - the debug log of the JDBC driver version skips pools that have not started.
- **Failures.** A tenant's resources are built once, even when several requests arrive together. If
  building them fails, the request gets an error naming the tenant
  (`PhysicalTenantResourceUnavailableException`), and the node does not try again for 5 seconds.
  A database that cannot be reached does not make the build fail, because building the MyBatis
  setup needs no connection; that tenant's statements fail when they try to connect. The existing
  schema check still answers that tenant's requests with 503 and `Retry-After`.
  `PhysicalTenantLazyRdbmsResourcesIT` checks this.
- **Health.** The per-tenant `rdbms` health entry reports a tenant that is not in use on this node
  as `UP` with `state: NOT_CREATED`, a tenant whose build failed as `DOWN`, and a tenant in use
  with the usual pool check. A database that cannot be reached is still reported per tenant by the
  schema health entry.
- **`lazy-initialization`.** `camunda.data.secondary-storage.rdbms.lazy-initialization` is `true`
  by default, and each tenant can override it. `false` builds the tenant's MyBatis setup at
  startup: a way to warm up a tenant that must answer its first query fast, and a way back to the
  old behaviour. The pool still starts on the first statement, as it did before for every tenant.
- **Release after a quiet period, not when leadership moves.** `idle-release-timeout` is set per
  storage, can be overridden per tenant, and `0` keeps resources until the node stops. A quiet
  period covers both a gateway whose burst of queries is over and a broker that no longer leads
  the tenant's partition: neither uses the tenant afterwards. Releasing on a leadership change
  would still need the timer for gateways, so the timer does both. The default of 10 minutes
  matches HikariCP's own default for closing an idle connection.
- **Nothing is released while in use.** Every SQL statement, database session and search request
  holds the tenant for as long as it runs. A release first takes the resources away from new
  users, then checks that nobody is holding them, and puts them back if someone is. Metrics, the
  health checks and the upgrade-readiness check only use a tenant that is already in use, and do
  not count as a use. Otherwise every metrics scrape would keep every tenant alive.
- **A pool that can start again.** A closed HikariCP pool cannot be reopened, so each tenant's pool
  is a wrapper (`ReleasablePool`) that swaps in a new, unstarted pool on release. Anything that
  holds the wrapper keeps working. The SQL mappers are kept with the tenant's MyBatis setup, so
  they are thrown away with it.
- **Search clients are lazy at the network layer.** The client libraries send every request
  through a "transport" object. `LazyElasticsearchTransport` and `LazyOpenSearchTransport` are
  transports that build the real one on the first request. The client objects that the rest of the
  node holds stay as they are. Schema setup already used its own short-lived client.
- **The shared pool is off by default.** `connection-pool.shared` is `false`. The
  guarantee-and-burst rule is a counter per tenant plus one for the burst, in front of any pool
  (`SharedConnectionBudget`).

## What is missing or unproven

- OpenSearch is wired but not measured: the harness only runs Elasticsearch.
- The exporter's own search clients (`ElasticsearchAdapter`, `CamundaBackgroundTaskManagerFactory`)
  are unchanged. They only exist on brokers that export the partition, but they also start about
  one thread per CPU each. Configuring fewer network threads per client
  (`IOReactorConfig.ioThreadCount`) would cut that for every client.
- A tenant whose database address does not say which database product it is (PostgreSQL, Oracle
  and so on) still has the product looked up over a connection at startup. That lookup uses a
  plain connection now, but if the tenant's database is down, startup fails, as it did before.
  The logs warn about it. Setting `database-vendor-id` avoids it.
- Schema setup still runs on every node for every tenant, over short-lived connections.
- The shared pool is a proof of concept behind a switch. The slow startup with one shared database
  is not explained, and nothing ran on PostgreSQL or under load. The variant that caps a node's
  connections across tenants with their own pools is not built.
- The periodic exporter task that rebuilds an idle RDBMS tenant is not identified.
- There is no test that 80 or more tenants start in a 2 GiB heap. See the extrapolation above.
- Nothing ran on a real cluster.

## Open questions for the team

1. If a load balancer spreads every tenant's API traffic over every gateway, each gateway builds
   every tenant again. Is idle release enough, or do we need gateways that route a tenant's
   requests to the brokers that hold it (direction item 5)?
2. Is 10 minutes the right default for `idle-release-timeout`, and should it be a public setting
   at all? The first statement after a release waits about 70 ms for the MyBatis setup.
3. Should schema setup run once per tenant, for example on the partition leader, instead of on
   every node?
4. Is `lazy-initialization` worth a public setting, or should building on first use always be on?
5. Is the scheduled command cache fix worth taking to `main` on its own? It saves 4 MiB per copy of
   every partition, with or without physical tenants.
6. Should the tenants of one installation share a database and user, which makes a shared pool
   possible, or each have their own, for isolation, separate credentials and dynamic secrets? The
   answer decides whether P5 is a shared pool or a cap on connections.

## Overlap with other work

- Carlo's spikes on a scheduler pool and a CPU limit per tenant: the harness is one place to
  measure the threads that a pool per tenant adds. They do not change how databases are wired.
- Direction item 12 / P6 (separate pools for the exporter and for queries) and P5 (shared pool):
  with this change, the pool starts on the first statement. A split pool or a shared pool can
  replace the HikariCP pool in `RdbmsDataSources`, and nothing else has to change.
- 

# 61935: brokers no longer open pools for tenants whose partitions they do not lead.

