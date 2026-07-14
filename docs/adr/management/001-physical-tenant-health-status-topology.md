# Health, readiness, and status semantics for multi-physical-tenant clusters

**DRI**: Lena Schönburg

**Status**: Accepted (8.10)

**Deciders**
- Lena Schönburg
- Deepthi Akkoorath
- Houssain Barouni

**Purpose**: Define what health, readiness, and liveness mean when one orchestration cluster hosts multiple physical tenants, specify the new cluster-wide `/cluster/v2/status` and `/cluster/v2/topology` endpoints, and derive the code-level consequences (probe groups, schema initialization, gateway health indicators).

**Audience**: Engineers working on the broker/gateway distribution, the v2 REST API, the data layer, and operators of multi-tenant clusters.

## Context

A physical tenant (PT) is an independent engine inside one orchestration cluster: its own Raft partition group, its own secondary storage, sharing broker and gateway nodes with other PTs. This breaks the single-tenant assumption behind today's health surface in three places.

### What is already per-PT

- **Broker readiness** requires every expected physical tenant to have registered its partitions and every local bootstrap partition across all tenants to be installed. Partition install is a Raft-internal condition and does not depend on secondary storage.
- **`GET /v2/status`** is already resolved per PT: it returns 204 when at least one partition of the resolved tenant's group has a healthy leader, else 503. It is deliberately unauthenticated. The unprefixed path resolves to the default tenant; the `/physical-tenants/{physicalTenantId}/v2/status` form reaches any tenant.
- **`GET /v2/topology`** and the gRPC `Topology` RPC are per-PT and include per-partition, per-broker role and health for the resolved group.

### What is not

- **Gateway health and liveness indicators.** They only reflect the default tenant.
- **Schema initialization is all-or-nothing.** We initialize every tenant's schema. Whenever an HTTP gateway is enabled, this initialization is synchronous and blocks the startup of other tenants.
- **`schemaReadinessCheck` aggregates over all tenants.** One tenant's uninitialized schema marks the whole node not-ready
- **There is no cluster-wide status or topology endpoint.** `/v2/status` and `/v2/topology` are group-scoped by design.

Kubernetes probes hit `/actuator/health/startup`, `/actuator/health/readiness`, and `/actuator/health/liveness` on the management port (9600). Some operators might use `/v2/status`.

## Decision

**D1. Three distinct health surfaces, with distinct questions.**

|                              Surface                               |                Question it answers                 |     Subject     | Sourced from |
|--------------------------------------------------------------------|----------------------------------------------------|-----------------|--------------|
| Actuator probes (`/actuator/health/{startup,readiness,liveness}`)  | Should K8s restart this pod / route traffic to it? | Node            | Node         |
| Per-PT topology (`/v2/topology`)                                   | Can this tenant process work right now?            | Physical Tenant | Cluster      |
| Cluster-wide status (`/cluster/v2/status`, `/cluster/v2/topology`) | What is the state of the whole cluster?            | Cluster         | Cluster      |

Actuator probes stay node-scoped and must not become "all physical tenants fully healthy", because K8s acting on a probe (restarting a pod, withholding traffic) always affects *all* physical tenants on that node.
Cluster-wide status and topology endpoints show all physical tenants and all nodes, while per-PT status and topology endpoints narrow that scope to a single physical tenant.

**D2. One tenant's secondary-storage failure degrades that tenant, not the node.**

- **Liveness**: Unchanged, node-scoped, never depends on secondary storage.
- **Readiness**: The secondary-storage-dependent contributors change from "all tenants" to "at least one serviceable tenant": a node is ready when it can correctly serve *some* tenant.
- **Startup**: Schema initialization must not abort the Spring context when a single tenant's storage is unavailable. Per-tenant initialization runs isolated, failures mark the tenant degraded, and initialization is retried in the background until it succeeds.
- A tenant is **degraded** when its schema is not initialized or its secondary storage is unusable; degraded state is surfaced per tenant, in logs, and in metrics but not via node probes.

Single-tenant clusters keep today's operational behavior.

**D3. Existing `/v2/status`: Scoped to the default physical tenant only**

This endpoint is only exposed under the prefix-less `/v2/status` and `/physical-tenants/default/v2/status`.
It only reports the status of the default physical tenant.

This _could_ be considered a breaking change because it is currently documented to report the overall cluster status.

This is the only REST API endpoint that will not exist for non-default physical tenants.
Users are encouraged to use the new `/cluster/v2/status` endpoint or the `/physical-tenants/{id}/v2/topology` endpoint, depending on their use case.
Client libraries will be updated to use `/cluster/v2/status` instead, matching the documented intent.

Because `/v2/status` is unauthenticated, we don't want to expose it for non-default physical tenants to prevent leaking ids of physical tenants.
To prevent enumeration, `/physical-tenants/{id}/v2/status` returns the same 404 for every non-default id, regardless of whether a physical tenant with that id exists.
This check must happen before the per-PT security chain is selected, so that existing and unknown physical tenants are indistinguishable to unauthenticated callers.

**D4. New `GET /cluster/v2/status`: Aggregated status over all physical tenants.**

An aggregated status is reported:
- `HEALTHY` when all tenants are healthy.
- `DOWN` when no tenant can process work.
- `DEGRADED` in every other case, i.e. some tenants are degraded or down.

The wire contract stays the same as the existing `/v2/status` so that clients can migrate transparently: unauthenticated, 204 with an empty body while the cluster can process work (`HEALTHY` or `DEGRADED`), 503 when it cannot (`DOWN`).

**D5. New `GET /cluster/v2/topology`: Aggregated topology over all physical tenants.**

The response contains the cluster-level fields (`clusterId`, broker list with host/port/version) once, and a map of PT-level information (`partitionsCount`, `replicationFactor`, and per-partition role/health per broker).

`/v2/topology` itself stays PT-scoped and unchanged.

**D6. Gateway health indicators become group-aware.**

`ClusterAwarenessHealthIndicator` and `PartitionLeaderAwarenessHealthIndicator` (and delayed liveness variants) aggregate across all known partition groups instead of the default group:
- **Cluster awareness:** Cluster topology is known and at least one broker is present.
- **Partition-leader awareness:** At least one partition in *any* physical tenant has a leader.

## Consequences

- K8s never takes down a node because one physical tenant's database is broken.
- Secondary storage schema initialization needs rework to not block startup when one physical tenant's database is uninitialized.
- New `/cluster/v2/status` and `/cluster/v2/topology` endpoints.
- `v2/status` semantics are changed slightly to only report status for the requested physical tenant.
  - Client libraries should be updated to use `/cluster/v2/status` instead.

## Alternatives considered

- **Readiness = all tenants fully healthy.** Rejected: one tenant's DB outage would remove the node from service for all tenants.
- **Readiness = default tenant only (status quo for gateway indicators).** Rejected: privileges one tenant arbitrarily and hides real outages of others from operators who only watch probes.
- **Per-tenant readiness probes (e.g. `/actuator/health/readiness/{pt}`).** Rejected: K8s cannot act per tenant on a shared pod, aggregated health is available via `/cluster/v2/status` instead.

