# Health, readiness, and status semantics for multi-physical-tenant clusters

**DRI**: Lena Schönburg (Zeebe Distributed Platform)

**Status**: Draft (8.10)

**Purpose**: Define what health, readiness, and liveness mean when one
orchestration cluster hosts multiple physical tenants, specify the new
cluster-wide `/cluster/v2/status` and `/cluster/v2/topology` endpoints, and
derive the code-level consequences (probe groups, schema initialization,
gateway health indicators).

**Audience**: Engineers working on the broker/gateway distribution, the v2
REST API, the data layer, and SREs operating multi-tenant clusters.

Relates to: physical tenants epic (camunda/camunda#50509), Day 2
Operations/Management milestone (kickoff doc §8.2/8.3, open questions 1–3),
camunda/camunda#54299 (per-tenant data-layer failure isolation).

## Context

A physical tenant (PT) is an independent engine inside one orchestration
cluster: its own Raft partition group, its own secondary storage, sharing
broker and gateway nodes with other PTs. This breaks the single-tenant
assumption behind today's health surface in three places.

### What is already per-PT on main

- **Broker readiness** (`brokerReady`, included in both the readiness and
  liveness groups by
  `dist/src/main/java/io/camunda/application/initializers/HealthConfigurationInitializer.java:120-175`)
  is PT-aware: `BrokerHealthCheckService.isBrokerReady()` requires every
  expected physical tenant to have registered its partitions and every local
  bootstrap partition across all tenants to be installed
  (`zeebe/broker/.../monitoring/BrokerHealthCheckService.java:146-156`).
  Partition install is a Raft-internal condition and does not depend on
  secondary storage.
- **`GET /v2/status`** is already resolved per PT
  (`zeebe/gateway-rest/.../controller/StatusController.java:32-35`): it
  returns 204 when at least one partition of the resolved tenant's group has
  a healthy leader, else 503
  (`service/.../TopologyServices.java:60-77`). It is deliberately
  unauthenticated (`security: []` in
  `zeebe/gateway-protocol/src/main/proto/v2/cluster.yaml:36-39`). The
  unprefixed path resolves to the default tenant; the
  `/physical-tenants/{physicalTenantId}/v2/status` form reaches any tenant.
- **`GET /v2/topology`** (REST) and the gRPC `Topology` RPC are per-PT and
  include per-partition, per-broker role and health for the resolved group
  (`TopologyController.java:32-36`, `EndpointManager.java:365-409`,
  `BrokerTopologyManagerImpl.java:65-69`).

### What is not

- **Gateway health indicators** (`ClusterAwarenessHealthIndicator`,
  `PartitionLeaderAwarenessHealthIndicator` and their delayed liveness
  variants) read the no-arg `BrokerTopologyManager::getTopology`, which is
  pinned to the default group
  (`dist/.../GatewayModuleConfiguration.java:162-166`,
  `zeebe/broker-client/.../BrokerTopologyManager.java:28-30`). Gateway
  probes therefore reflect only the default tenant.
- **Schema initialization is all-or-nothing.** For ES/OS,
  `SearchEngineSchemaInitializer.afterPropertiesSet()` initializes every
  tenant's schema and — whenever an HTTP gateway is enabled — does so
  synchronously, so an unreachable secondary storage of *one* tenant aborts
  the entire Spring context
  (`dist/.../search/SearchEngineSchemaInitializer.java:56-106`,
  `SearchEngineDatabaseConfiguration.java:67-83`). For RDBMS,
  `DefaultRdbmsSchemaManagerRegistry.afterPropertiesSet()` is deliberately
  fail-fast per tenant (`db/rdbms/.../DefaultRdbmsSchemaManagerRegistry.java:60-68`);
  camunda/camunda#54299 tracks isolating this.
- **`schemaReadinessCheck` aggregates over all tenants**
  (`SchemaReadinessCheck.java:23-26` delegates to
  `SearchEngineSchemaInitializer.isInitialized()`, which is "all tenants
  initialized"), so once startup survives, one tenant's uninitialized schema
  still marks the whole node not-ready.
- There is no cluster-wide status or topology endpoint; `/v2/status` and
  `/v2/topology` are group-scoped by design.

K8s probes (camunda-platform-helm `orchestration` component) hit
`/actuator/health/startup`, `/actuator/health/readiness`, and
`/actuator/health/liveness` on the management port (9600). Load balancers
and SRE checks use `/v2/status`.

> **Pending input**: how SaaS SRE tooling consumes `/v2/status` and the
> actuator groups today, and what it needs per tenant (survey in progress).
> The cluster-wide status semantics below (D4) may be adjusted based on it.

## Decision

**D1. Three distinct health surfaces, with distinct questions.**

|                               Surface                                |                 Question it answers                 |  Scope  |
|----------------------------------------------------------------------|-----------------------------------------------------|---------|
| Actuator probes (`/actuator/health/{startup,readiness,liveness}`)    | Should K8s restart this pod / route traffic to it?  | Node    |
| Per-PT status (`[/physical-tenants/{id}]/v2/status`, `/v2/topology`) | Can this tenant process work right now?             | One PT  |
| Cluster-wide status (`/cluster/v2/status`, `/cluster/v2/topology`)   | What is the state of the whole cluster, per tenant? | All PTs |

These are not interchangeable: probes stay node-scoped and must not become
"all tenants fully healthy", because K8s acting on a probe (restarting a pod,
withholding traffic) always affects *all* tenants on that node — a per-tenant
failure must not trigger a node-wide remedy.

**D2. One tenant's secondary-storage failure degrades that tenant, not the
node.**

- **Liveness**: unchanged, node-scoped, never depends on secondary storage
  (this rule already exists —
  `HealthConfigurationInitializer.java:108-119`).
- **Readiness**: remains node-scoped. The Raft-level condition (all expected
  tenants registered, all local partitions installed) is kept: it has no
  external dependency and guards rolling-restart safety. The
  secondary-storage-dependent contributors change from "all tenants" to "at
  least one serviceable tenant": a node is ready when it can correctly serve
  *some* tenant. Requests for a degraded tenant are rejected at request time
  with 503 and a clear problem detail rather than by failing the node probe.
- **Startup**: schema initialization must not abort the Spring context when
  a single tenant's storage is unavailable. Per-tenant initialization runs
  isolated, failures mark the tenant degraded, and initialization is retried
  in the background until it succeeds. This applies the same principle to
  both paths: ES/OS (`SearchEngineSchemaInitializer`) and RDBMS
  (camunda/camunda#54299, which owns the RDBMS implementation and whose open
  design questions 1–5 this ADR answers at the semantic level).
- A tenant is **degraded** when its schema is not initialized or its
  secondary storage is unusable; degraded state is surfaced per tenant (D3,
  D4), in logs, and in metrics — not via node probes.

Single-tenant clusters keep today's operational behavior: with only the
default tenant configured, "at least one serviceable tenant" and "all
tenants serviceable" coincide, and fail-fast-equivalent feedback comes from
the tenant being visibly degraded rather than from a crash loop.
*Whether ES/OS keeps blocking startup when the* only *configured tenant's
storage is down (strict status quo) or converges on retry-in-background for
consistency is left to implementation with Houssain's input on DB
startup/liveness behavior.*

**D3. `/v2/status` semantics are unchanged and per-PT.**

Status quo, recorded for compatibility: 204 iff at least one partition of
the resolved tenant's group has a healthy leader, else 503; unauthenticated;
unprefixed path = default tenant. Existing single-tenant consumers see no
change. Additionally, a degraded secondary storage (D2) does **not** flip
`/v2/status` to 503 — it reports engine processability, matching today's
semantics which never consulted secondary storage.

**D4. New `GET /cluster/v2/status`: structured, per-tenant, with a
conservative aggregate.**

Unlike `/v2/status` (bodyless, binary), the cluster-wide endpoint returns a
body — its purpose is observability across tenants, not LB gating:

- 200 with a JSON body whenever the gateway can answer; per-tenant entries
  with at minimum `physicalTenantId` and a status of
  `HEALTHY | DEGRADED | DOWN`, where `HEALTHY` = the tenant's `/v2/status`
  criterion plus non-degraded secondary storage, `DEGRADED` = processing
  possible but secondary storage/schema impaired, `DOWN` = no healthy leader
  on any partition.
- A top-level aggregate `status`: `HEALTHY` (all tenants healthy),
  `DEGRADED` (some impaired), `DOWN` (no tenant can process). HTTP 503 is
  returned only in the `DOWN` case, so naive HTTP-code-only monitors retain
  a meaningful signal.
- This endpoint also serves as the (optional) tenant list from the kickoff
  doc: its entries enumerate all configured tenants. No separate
  list-tenants endpoint is added in 8.10.

**D5. New `GET /cluster/v2/topology`: all partition groups, grouped by
tenant.**

The response contains the cluster-level fields (`clusterId`, broker list
with host/port/version) once, and per-tenant blocks with that tenant's
`partitionsCount`, `replicationFactor`, and per-partition role/health per
broker — the same partition schema as `/v2/topology` to keep mappers and
clients uniform. `/v2/topology` itself stays group-scoped and unchanged.
Data source: the existing per-group topologies
(`BrokerTopologyManagerImpl.topologyPerGroup`); no new broker protocol is
required.

**D6. Gateway health indicators become group-aware.**

`ClusterAwarenessHealthIndicator` and
`PartitionLeaderAwarenessHealthIndicator` (and delayed liveness variants)
aggregate across all known partition groups instead of the default group:
cluster awareness = topology known and brokers present (group-independent);
partition-leader awareness = at least one partition in *any* group has a
leader (consistent with D2's "at least one serviceable tenant" for
node-scoped probes).

**D7. Authorization follows ADR 002.**

`/cluster/v2/status` is unauthenticated, mirroring `/v2/status` (it exposes
tenant ids and coarse health — same class of information the unauthenticated
`/v2/status` family already leaks per tenant; revisit if the SRE survey
contradicts). `/cluster/v2/topology` requires the pre-configured
cluster-admin (see ADR 002). Both get OpenAPI specs in
`zeebe/gateway-protocol/src/main/proto/v2/` with `x-required-permissions`
per docs/adr/security/001.

## Consequences

- K8s never takes down a node — and with it all tenants — because one
  tenant's database is broken; #54299's design questions on readiness and
  user-visible behavior are answered (serve healthy tenants; 503 for the
  degraded one; background retry; per-tenant surfacing).
- `SearchEngineSchemaInitializer` and `SchemaReadinessCheck` need rework:
  per-tenant isolation, background retry, and a readiness contribution that
  fails only when *no* tenant is serviceable. The RDBMS counterpart is
  implemented under #54299.
- Two new REST endpoints + OpenAPI specs; `StatusController`/
  `TopologyController` gain cluster-scoped siblings (using the existing
  `@ClusterScoped` exemption from the PT prefix).
- Gateway probe semantics change subtly in multi-tenant setups (leader in
  any group instead of the default group); single-tenant behavior is
  identical.
- Monitoring guidance changes: "is tenant X ok" questions move from node
  probes to `/cluster/v2/status` / per-PT `/v2/status`; docs must state
  this.

## Alternatives considered

- **Readiness = all tenants fully healthy.** Rejected: one tenant's DB
  outage would remove the node from service for all tenants — the exact
  cascade the isolation epic exists to prevent (kickoff doc, open question
  2).
- **Readiness = default tenant only (status quo for gateway indicators).**
  Rejected: privileges one tenant arbitrarily and hides real outages of
  others from operators who only watch probes.
- **Per-tenant readiness probes (e.g. `/actuator/health/readiness/{pt}`).**
  Rejected for 8.10: K8s cannot act per tenant on a shared pod, so nothing
  can consume it as a probe; per-tenant health is available via
  `/cluster/v2/status` instead. Can be revisited if per-tenant ingress
  gating ever becomes a requirement.
- **`/cluster/v2/status` bodyless like `/v2/status`.** Rejected: a single
  binary signal cannot express "tenant A down, tenant B fine", which is the
  entire point of the cluster-wide view.
- **A separate list-tenants endpoint.** Deferred: `/cluster/v2/status`
  already enumerates tenants; adding a dedicated endpoint is trivial later
  if a metadata-only listing is needed.

