# Proof of concept: runtime physical tenant set on one node (lifecycle slice 0)

Branch `pt-poc-lifecycle-tenant-set`, based on `main` at `3214417b37a`. Not for merge as is.

## What it proves

On a running broker with an embedded gateway, a physical tenant can be added, replaced and
removed through an internal Java service, without a node restart, while the other tenants keep
serving:

- **Add**: the tenant's partition group is provisioned, its broker and gateway modules start, and
  over gRPC it deploys a process, streams a job to an open job stream and completes the instance.
- **Replace**: only that tenant's module restarts. Under continuous gRPC load on another tenant
  there were no failed requests and the liveness probe never failed. The tenant keeps its
  incarnation and its broker module runs with the new config.
- **Invalid change**: rejected with the boot validations before anything changes; the old state
  stays (health, name, broker config, still serving).
- **Remove**: gRPC rejects the tenant as unknown (`NOT_FOUND`), REST answers 404, its data
  directory stays, and its id stays reserved.
- **Health**: `/actuator/health/physicalTenants/<id>` shows STARTING, RESTARTING, HEALTHY,
  DEGRADED or FAILED per tenant; liveness and readiness of the node do not depend on it.
- **No leaks**: 20 restarts of one tenant keep live actors, threads, RocksDB instances, open file
  descriptors and the meters of the other tenants constant. Two leaks were found and fixed.

## How to run

```bash
./mvnw install -Dquickly -T3
./mvnw verify -pl zeebe/qa/integration-tests -DskipTests=false -DskipUTs -Dquickly \
  -Dit.test='PhysicalTenantRuntimeLifecycleIT,PhysicalTenantRestartLeakIT,PhysicalTenantRestartWithDataIT'
```

Unit tests: `DefaultPhysicalTenantRegistryTest` (cluster), `PhysicalTenantEnvironmentsTest`
(configuration), `BrokerHealthCheckServiceTest`, `TopologyManagerImplTest`,
`ExporterRepositoryTest` (zeebe/broker), `RuntimePhysicalTenantInterceptorTest`
(zeebe/gateway-grpc).

Internal API, for slice 1:

```java
PhysicalTenantLifecycleService lifecycle = ...; // bean, broker profile
lifecycle.add("tenantb", Map.of("name", "Tenant B", "data.secondary-storage.type", "none", ...));
lifecycle.replace("tenantb", wholeSubtree);   // names relative to camunda.physical-tenants.<id>
lifecycle.remove("tenantb");
PhysicalTenantRegistry registry = ...;        // live set, health, listeners
```

## Design

| Piece | Where | What |
| --- | --- | --- |
| `PhysicalTenantRegistry`, `DefaultPhysicalTenantRegistry` | `cluster` | Extends the existing `PhysicalTenantIds`; is now the primary `PhysicalTenantIds` bean, so every per-request `known()` call follows the live set. Holds id, display name, incarnation id, health, and listeners. |
| `PhysicalTenantEnvironments` | `configuration` | Derives an environment in which a changed tenant's whole subtree replaces what it declared, so `PhysicalTenantResolver.of` runs every boot rule on a runtime change. |
| `PhysicalTenantConfigurationValidator` | `zeebe/broker` | The per-tenant validations moved out of `SystemContext`; boot and runtime call the same code. |
| `PhysicalTenantModules` / `PhysicalTenantModulesStep` | `zeebe/broker` | One startup step owns every tenant's partition manager; starts and stops a whole broker module (job stream service, partition manager, admin service) at runtime, provisions the partition group, rolls back a failed start. |
| `BrokerHealthCheckService` | `zeebe/broker` | Tenants can be released from and expected back into node readiness; per-tenant readiness can be awaited. |
| `GatewayPhysicalTenants` | `zeebe/gateway-grpc` | gRPC gateway module per tenant (security config, converter, auth handler), looked up per call; a tenant can be marked unavailable while it restarts. |
| `PhysicalTenantServedFilter` | `zeebe/gateway-rest` | Fails closed with 404 for a tenant without a matching HTTP security chain. |
| `PhysicalTenantsHealthContributor` | `dist` | Per-tenant actuator health, outside the liveness and readiness groups. |
| `PhysicalTenantLifecycleService` | `dist` | Orchestrates validate, registry, broker module, gateway module and health; one change at a time. |

Decisions taken where the scoping leaves a question open (each is easy to change):

- **Incarnation id (Q7 is open)**: a random UUID per add, kept across a replace, node-local and in
  memory. It is not persisted, not agreed between nodes, and boot tenants get a new one at every
  node start. Removed ids stay reserved on the node (option A of Q7 as well), so re-adding is
  rejected; the tombstone is in memory only.
- **Display name**: `camunda.physical-tenants.<id>.name`, read by the resolver, never bound into
  `Camunda`, shown in the registry and the actuator.
- **Input of a change**: the whole tenant subtree, names relative to the tenant (Q3 direction). No
  key classification yet: every key that passes validation is applied by a module restart.
- **Health model**: `STARTING`, `HEALTHY`, `RESTARTING`, `DEGRADED`, `FAILED`, `REMOVED` in the
  `cluster` module so dynamic-secrets can report `DEGRADED` through `reportHealth`. Actuator
  mapping: HEALTHY → UP, DEGRADED → DEGRADED (200), STARTING/RESTARTING → OUT_OF_SERVICE (503),
  FAILED → DOWN (503). A removed tenant is not listed (404); the registry still reports REMOVED.
- **Liveness and readiness**: node readiness (and with it liveness, which includes `brokerReady`)
  waits for boot tenants only. A tenant is released before its module stops and expected again
  once its partitions are installed, so one tenant never makes the node unready; taking the pod
  out of the Service would cut every tenant. Per-tenant readiness is the tenant's own health path.
- **Failure of a change after validation** (Q4): the tenant stays `FAILED`, gRPC answers
  `UNAVAILABLE`, and the next replace or remove recovers it. No automatic retry.
- **Concurrency**: one change at a time per node; a concurrent change is rejected (`IN_PROGRESS`),
  not queued.
- **`default`** (Q13 open): cannot be replaced or removed at runtime.
- **Partition group of an added tenant**: provisioned by the coordinator through the same
  provisioning as at startup, applied to the live cluster configuration. Remove does not tombstone
  the group (slice 1).
- **Boot path**: boot tenants still start their job stream and admin services in the existing
  steps; only the partition managers moved into the modules step. Runtime modules reuse the same
  per-tenant helpers.

## Call sites that froze the tenant set

Converted: the partition manager step loop, the job stream step (service supplier and shutdown),
the admin service step (tenant id supplier and shutdown), the broker health check service, the
embedded gateway step, `Gateway`, `AuthenticationInterceptor`, `EndpointManager`, the REST
pre-security filter (new), and, through the live `PhysicalTenantIds` bean, every consumer that
calls `known()` per request (MCP tenant filter, exporting, flow control, rebalancing and
migration-status endpoints, the standalone gateway's cluster states).

Left on purpose, with the reason:

| Call site | Why left |
| --- | --- |
| HTTP security chains (CSL) | Built once at startup by the Camunda Security Library; needs the CSL dispatcher chain. Added tenants answer 404 on REST and MCP over HTTP; a replaced tenant keeps its boot chain, so a change of security keys does not reach REST yet (risk 5/12). |
| `CheckpointSchedulerServiceStep` | Backup schedules are read once; an added tenant has no scheduled backups and a replaced tenant keeps its old schedule. Backups are slice 1. |
| Spring per-tenant beans (search clients, RDBMS data sources and exported-position suppliers, schema initialiser, secret store registries, user services for BASIC on gRPC) | Built from the boot resolver. A runtime-added tenant must use `data.secondary-storage.type=none` in this PoC; this is the "about 25 consumers" problem and the dynamic-secrets holder. |
| `SecondaryStorageReadinessMetrics` | Registers one gauge per boot tenant. |
| `RocksDbResources` | Shared memory pool sized at boot from the local partition count. |
| Standalone gateway | Builds `Gateway` from boot configs; has no lifecycle service. |

## Close paths and leaks

The leak test found and the branch fixes:

1. `TopologyManagerImpl` never unregistered its membership listener: one closed actor and its
   broker info leaked per restart (60 → 80 live `ActorTask`s over 20 restarts before the fix).
2. Broker job stream meters were never removed: a removed tenant kept them, and a restarted
   tenant reused a gauge bound to the closed service's counter (a silent stale value).

Also closed now: the exporter class loaders of a tenant (`ExporterRepository` is closeable).

Not closed, excluded explicitly from the leak test:

- `SecretStoreRegistry`: Carlo's PR #67248 adds the close path. Runtime-added tenants get the
  no-op registry, so the leak test does not exercise real stores.
- Meters that survive a remove by design or by another owner: `zeebe.cluster.partition.balanced`
  and `zeebe.gateway.topology.*` (the cluster configuration keeps the removed group until slice 1
  tombstones it), `zeebe.gateway.job.stream.*` (gateway job stream client keeps one set per
  tenant seen), `zeebe.messaging.*` (per-topic metrics of the shared messaging service). None
  grows per restart.
- The gossip property `brokerInfo:<group>` of a removed tenant stays on the member.

## Measurements

All on one in-process broker, laptop (Apple silicon), warm JVM, one partition per tenant, no
exporters, `secondary-storage: none`.

| What | Result | Method |
| --- | --- | --- |
| Restart of an idle tenant (replace call to first accepted command) | 79–337 ms, median ~128 ms over 20 restarts | `PhysicalTenantRestartLeakIT` |
| Restart with 1000 active instances | replace call 35–68 ms; partitions installed 50–91 ms; first accepted command 269–361 ms (5 restarts) | `PhysicalTenantRestartWithDataIT` |
| Resources over 20 restarts | actors 57 → 57, threads 106 → 106 (gRPC event loop threads excluded, they start lazily up to a fixed size), RocksDB instances 2 → 2, open fds 1393 → 1393 | Class histogram after a forced full GC, `ThreadMXBean`, `UnixOperatingSystemMXBean` |

The gap between "partitions installed" and "first accepted command" (~250 ms with data) shows
that `HEALTHY` (all local partitions have a Raft role) comes before the stream processor accepts
commands. These numbers do not represent real data volumes or exporters.

## Open questions for the team

1. Should `HEALTHY` wait for the partition health tree (stream processor and exporters), not just
   the Raft role? Today a tenant reports HEALTHY ~250 ms before it accepts commands.
2. Should node readiness ever fail for a tenant? This PoC says never after boot; the user story
   asks for "not ready for a stopped tenant", which here is per-tenant only.
3. Incarnation id: who generates and persists it (Q7), and do boot tenants keep theirs across
   node restarts? Today it is node-local and in memory.
4. Is replacing a tenant whose module failed to start, without an automatic retry, the right
   model for Q4 on a single node, before dynamic-config retries cluster-wide?
5. The REST filter fails closed for added and removed tenants, but a replaced tenant keeps its
   boot security chain. Should slice 1 reject security-key changes until the CSL dispatcher lands?
6. Partition-group provisioning at runtime reuses the coordinator-local startup provisioning. On
   a multi-node cluster every member must run the module before the bootstrap operations can
   apply; slice 1 owns that ordering.
7. `MigrationSnapshotDirector` logs an unhealthy transient snapshot on fresh partitions, for boot
   and runtime tenants alike, on this branch's base. Unrelated to the lifecycle, but noisy.

## Touch points with other work

- **Per-tenant actor pool (Carlo)**: a pool can be created in `PhysicalTenantRegistry.Listener
  #onAdded` (runs before the module starts) and closed in `onRemoved` (after it stopped). The
  modules step still takes the broker-wide `ActorSchedulingService`; the pool would need
  `PhysicalTenantContext` (or a lookup) to carry the tenant's scheduler.
- **SecretStoreRegistry close (PR #67248)**: the `close` helper of `PhysicalTenantLifecycleService`
  releases the old tenant context; add the registry close there.
- **Dynamic-secrets**: `PhysicalTenantRegistry#reportHealth(id, DEGRADED)`.
- **Slice 1 (dynamic-config apply, management API)**: call `PhysicalTenantLifecycleService`;
  `PhysicalTenantChangeException.Reason` maps to API errors; `PhysicalTenantModules#context`
  exposes the applied config of a running module.
