# Management endpoint inventory for multi-physical-tenant clusters (8.10)

**DRI**: Lena Schönburg (Zeebe Distributed Platform)

**Status**: Draft (8.10) — depends on ADR 001 (health/status semantics) and
ADR 002 (authorization model)

**Purpose**: The authoritative list of management endpoints in 8.10: which
exist per physical tenant and/or cluster-wide, on which surface (REST vs
actuator), how tenants are selected, what changes behaviorally, and what is
explicitly out of scope. Supersedes the endpoint table in kickoff doc §8.3.

**Audience**: Engineers implementing the Day 2 Operations milestone;
reviewers of the OpenAPI specs; docs writers.

Relates to: physical tenants epic (camunda/camunda#50509), kickoff doc
§8.1–8.3/§9, camunda/camunda#54898 (cluster-admin), DL task "per-PT backup
& restore for ES/OS" (per-tenant `BackupRepository`).

## Context

### Tenant selection mechanics (settled)

- **REST (application port, authorized)**: path prefix
  `/physical-tenants/{physicalTenantId}/v2/...`; the unprefixed path
  resolves to the default tenant (`PhysicalTenantFilter` +
  `PhysicalTenantRequestMappingHandlerMapping`, already on main).
  Cluster-wide operations use the `/cluster/v2/...` prefix with
  `@ClusterScoped` controllers exempt from PT prefixing.
- **Actuator (management port 9600, unauthenticated — ADR 002 D2)**:
  optional query parameter `?physicalTenant={id}` narrows an operation to
  one tenant; **no parameter keeps today's whole-cluster meaning**, i.e.
  the operation applies to all physical tenants. Query parameters are safe
  here precisely because the actuator surface carries no authorization
  (ADR 002); on the authorized REST surface tenant selection must stay in
  the path.

### Current-behavior facts the table builds on

- All custom actuators live in `dist/.../shared/management/` (Zeebe) and
  [`dist/.../webapps/controllers/BackupController.java`](https://github.com/camunda/camunda/blob/7a4156548b565eb63e4298f90688cdce6afcf7a5/dist/src/main/java/io/camunda/webapps/controllers/BackupController.java) (history backup);
  none is PT-aware today.
- Runtime backup fans out one broker request per partition
  ([`BackupRequestHandler.java:121-175`](https://github.com/camunda/camunda/blob/00fbaf49f869a9a40963f3e0d78fdbb4d5f56e50/zeebe/backup/src/main/java/io/camunda/zeebe/backup/client/api/BackupRequestHandler.java#L121-L175)) using the **no-arg** topology,
  which post-M1 resolves to the *default* group only
  ([`BrokerTopologyManager.java:28-30`](https://github.com/camunda/camunda/blob/756898902e228c0f30cb38adefe69bcbc5e962af/zeebe/broker-client/src/main/java/io/camunda/zeebe/broker/client/api/BrokerTopologyManager.java#L28-L30)) — on a multi-PT cluster, today's
  `/actuator/backupRuntime` would silently back up only the default
  tenant. The same applies to exporting control
  ([`ExportingControlService.java:53-96`](https://github.com/camunda/camunda/blob/00fbaf49f869a9a40963f3e0d78fdbb4d5f56e50/zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/admin/exporting/ExportingControlService.java#L53-L96)).
- Exporting pause/resume is already applied per partition, to all replicas
  (`ExporterDirector` is a per-partition actor;
  [`ExporterDirector.java:180,202`](https://github.com/camunda/camunda/blob/50a6796b8004acfdeca4386a20d304d758e0824c/zeebe/broker/src/main/java/io/camunda/zeebe/broker/exporter/stream/ExporterDirector.java#L180)) — per-PT scoping means restricting the
  partition set to one group, not introducing new broker state.
- Taking a runtime backup does **not** pause exporting; they are
  independent operations coordinated by the operator/backup procedure.
- History backup uses singleton `BackupRepository`/`BackupRepositoryProps`
  beans and tenant-less snapshot names
  (`camunda_webapps_{backupId}_{version}_part_{i}_of_{n}`,
  [`WebappsSnapshotNameProvider.java:16-24`](https://github.com/camunda/camunda/blob/1f5ccf1b8828a5adaa5e3e1ff9db3ed625d1a026/webapps-backup/src/main/java/io/camunda/webapps/backup/repository/WebappsSnapshotNameProvider.java#L16-L24)); the DL task makes these
  per-tenant with tenant-prefixed snapshot names.
- Purge's history deletion is hard-coded to the default tenant
  ([`ClusterChangeExecutorImpl.java:129`](https://github.com/camunda/camunda/blob/50a6796b8004acfdeca4386a20d304d758e0824c/zeebe/broker/src/main/java/io/camunda/zeebe/broker/partitioning/topology/ClusterChangeExecutorImpl.java#L129)).

## Decision

### D1. New REST endpoints (application port)

Per-PT endpoints exist in both unprefixed (default tenant) and
`/physical-tenants/{id}` forms. Auth per ADR 002 (per-PT chain /
cluster-admin). Exact resource naming (`backups/runtime` vs `backup`,
singular/plural) is finalized in OpenAPI review; the table fixes semantics.

The per-PT surface must reach **full parity** with the corresponding
actuator operations (`BackupEndpoint`, `BackupController`,
`ExportingEndpoint`): the per-PT REST endpoints are the *only* management
access a tenant principal has, so any actuator operation without a REST
sibling would silently remain operator-only. The OpenAPI review must diff
the spec against the actuator operation set as a completeness check.

Per-PT endpoints (unprefixed = default tenant):

|                            Endpoint                            |                                                                                                                                                                                                          Behavior                                                                                                                                                                                                          |
|----------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `POST /v2/backups/runtime`                                     | Trigger runtime backup of the tenant's partition group; 202 + backupId. `backupId` in the body is required in manual mode and **forbidden** when continuous backups or a backup/checkpoint schedule is enabled (the id is generated) — same validation as the actuator (`BackupEndpoint.take()`/`take(long)`). Same error mapping (400 invalid id, 409 exists / concurrent scaling, 502 incomplete topology, 504 timeout). |
| `GET /v2/backups/runtime`                                      | List backups, aggregated over the tenant's partitions; optional id-prefix filter (query param, e.g. `?prefix=12*`) replacing the actuator's `{prefix*}` path selector.                                                                                                                                                                                                                                                     |
| `GET /v2/backups/runtime/{backupId}`                           | Status of one backup, aggregated over the tenant's partitions.                                                                                                                                                                                                                                                                                                                                                             |
| `DELETE /v2/backups/runtime/{backupId}`                        | Delete backup.                                                                                                                                                                                                                                                                                                                                                                                                             |
| `GET /v2/backups/runtime/state`                                | Per-partition checkpoint states, backup states, and backup ranges for the tenant's group (actuator `GET backupRuntime/state`).                                                                                                                                                                                                                                                                                             |
| `POST /v2/backups/runtime/state/sync`                          | Re-sync checkpoint/backup metadata from the tenant's backup store (post-restore/DR recovery); returns the updated state (actuator `POST backupRuntime/state/sync`).                                                                                                                                                                                                                                                        |
| `DELETE /v2/backups/runtime/state`                             | Delete the tenant's runtime backup state (actuator `DELETE backupRuntime/state`).                                                                                                                                                                                                                                                                                                                                          |
| `POST /v2/backups/history`                                     | Trigger history backup; returns the scheduled snapshot names. ES/OS clusters only (endpoint absent on RDBMS clusters, as today via `@ConditionalOnSecondaryStorageType`). Uses the tenant's `BackupRepository` (DL task).                                                                                                                                                                                                  |
| `GET /v2/backups/history[/{backupId}]`                         | Status by id / list; the list carries the actuator's `verbose` and `pattern` query parameters. ES/OS only.                                                                                                                                                                                                                                                                                                                 |
| `DELETE /v2/backups/history/{backupId}`                        | Delete history backup. ES/OS only.                                                                                                                                                                                                                                                                                                                                                                                         |
| `POST /v2/exporting/pause?soft=` / `POST /v2/exporting/resume` | Pause/resume exporting on the tenant's partitions only. In REST because backup is in REST — a backup procedure must not straddle two surfaces.                                                                                                                                                                                                                                                                             |

Cluster-wide endpoints: **every backup and exporting operation above has a
`/cluster/v2/...` sibling** that fans out to all tenants and returns
responses grouped by `physicalTenantId` (triggers follow the D2 contract;
reads aggregate per tenant plus an overall state). Enumerated once here
rather than per row; the OpenAPI spec lists them individually. In addition:

|          Endpoint          |                                 Behavior                                  |
|----------------------------|---------------------------------------------------------------------------|
| `GET /cluster/v2/status`   | Per ADR 001 D4 (per-tenant statuses + aggregate; doubles as tenant list). |
| `GET /cluster/v2/topology` | Per ADR 001 D5 (all groups, grouped by tenant).                           |

Restore endpoints are **not** part of this milestone: the Restore API epic
(Panos) owns them, including per-PT support; this inventory reserves no
paths for it beyond noting `/v2/...` and `/cluster/v2/...` symmetry is
expected.

### D2. Cluster-wide backup contract

A cluster-wide backup is a *set of independent per-tenant backups* (kickoff
decision), not an atomic snapshot:

- The caller-supplied `backupId` is used for **every** tenant's backup; ids
  live in per-tenant namespaces (per-tenant stores / tenant-prefixed
  snapshot names), so they cannot collide.
- **Trigger is all-or-error**: if any tenant cannot be reached/triggered,
  the response is an error (502/503-class). Because fan-out is not
  transactional, the error body still lists the tenants whose backups
  *were* triggered (`{physicalTenantId, backupId, triggered|failed,
  reason}` entries) so the operator can monitor or delete them —
  a partial trigger must never be silent.
- **Success returns the per-tenant backup ids** so each backup can be
  monitored independently (backups are asynchronous). Monitoring happens
  per tenant via the per-PT endpoints or in one call via
  `GET /cluster/v2/backups/runtime/{backupId}`.
- No orchestration beyond trigger + status: no automatic rollback/cleanup
  of sibling backups when one tenant's backup later fails. (A "watch this
  set of backup ids" convenience API is a possible later addition, not
  8.10 scope.)
- **Continuous/scheduled mode caveat**: the shared caller-supplied id only
  applies to manual-mode backups. When a tenant has continuous backups or
  a backup/checkpoint schedule enabled, ids are generated and an explicit
  id is rejected (`BackupEndpoint.take()`). Since backup configuration is
  per-PT, tenants of one cluster may be in different modes. The cluster
  trigger therefore: with an explicit id, fails 400 if *any* tenant is in
  generated-id mode; without an id, fails 400 if *any* tenant is in
  manual mode, and otherwise returns the generated per-tenant ids. Mixed
  configurations get no best-effort behavior — the operator must fall back
  to per-tenant triggers.

### D3. Actuator endpoints: PT behavior in 8.10

|                                                Actuator                                                |                                               No `physicalTenant` param                                                |                       With `?physicalTenant={id}`                        |                                                                                                                                                                                     Notes                                                                                                                                                                                     |
|--------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `backupRuntime` (`BackupEndpoint`)                                                                     | All tenants (D2 contract) — **behavior fix**: today it silently covers only the default group                          | That tenant only                                                         | Response entries gain an optional `physicalTenantId` field (additive to [`backup-management-api.yaml`](https://github.com/camunda/camunda/blob/0eca734b4a60816f7b4b8a651cb45110026e7fd1/dist/src/main/resources/api/backup-management-api.yaml)).                                                                                                                             |
| `backupHistory` (`BackupController`)                                                                   | All tenants                                                                                                            | That tenant only                                                         | Per-tenant repositories from the DL task; fan-out owned here.                                                                                                                                                                                                                                                                                                                 |
| `exporting` pause/resume                                                                               | All tenants (matches today's whole-cluster meaning)                                                                    | That tenant's partitions                                                 | Scoping = filter partition set by group.                                                                                                                                                                                                                                                                                                                                      |
| `exporters` (list/enable/disable/delete)                                                               | List: grouped by tenant. Mutations: all tenants defining that exporter id                                              | That tenant only                                                         | Stays actuator-only in 8.10 (kickoff §8.3); REST exposure deferred.                                                                                                                                                                                                                                                                                                           |
| `cluster` (GET/PATCH, scaling)                                                                         | GET: full topology, all groups. Broker add/remove: cluster-wide only (has no tenant dimension)                         | Partition scaling scoped to the tenant's group                           | **Blocked on the per-PT dynamic cluster configuration work** (Deepthi, in progress).                                                                                                                                                                                                                                                                                          |
| `cluster/purge`                                                                                        | Whole cluster, all tenants                                                                                             | That tenant: leave/bootstrap its partitions, purge its exporters/history | Requires un-hard-coding the default tenant in [`ClusterChangeExecutorImpl.java:129`](https://github.com/camunda/camunda/blob/50a6796b8004acfdeca4386a20d304d758e0824c/zeebe/broker/src/main/java/io/camunda/zeebe/broker/partitioning/topology/ClusterChangeExecutorImpl.java#L129); per-PT variant also depends on the dynamic-config work.                                  |
| `rebalance`                                                                                            | All groups (extend from default-group-only)                                                                            | — (not offered)                                                          | Cluster-actuator only in 8.10 (kickoff §8.3).                                                                                                                                                                                                                                                                                                                                 |
| `flowControl`                                                                                          | Unchanged (per-partition, cluster)                                                                                     | —                                                                        | Per-PT flow control is configuration-level, out of scope here.                                                                                                                                                                                                                                                                                                                |
| `clock` (`ActorClockEndpoint`)                                                                         | Unchanged: mutates the node-global `ControlledActorClock` (all tenants + actor timers; requires controlled-clock mode) | — (not offered)                                                          | The actuator is legacy and stays as-is. Per-PT clock control is already served by the regular clock control commands (`/v2/clock`), which are per-PT like every v2 endpoint (prefix routing) and mutate the per-partition `ControllableStreamClock` overlays. No new work in this milestone; this satisfies the kickoff decision "the clock endpoint is per physical tenant". |
| `loggers`, `health/*`, `prometheus`, `configprops`, `jobstreams`, `banning`, broker-local `partitions` | Node-scoped, unchanged                                                                                                 | —                                                                        | Health groups per ADR 001; metrics carry the `physicalTenant` tag already.                                                                                                                                                                                                                                                                                                    |

### D4. Backwards compatibility

- **Single-tenant clusters see no change**: with only the default tenant,
  "all tenants" ≡ today's whole-cluster behavior for every actuator, and
  all unprefixed REST paths already resolve to the default tenant.
- Multi-tenant clusters get a **behavior fix**, not a break, in
  `backupRuntime`/`exporting`: covering all tenants instead of silently
  only the default group (nobody runs multi-PT on 8.9).
- Existing actuator request/response schemas
  ([`dist/src/main/resources/api/backup-management-api.yaml`](https://github.com/camunda/camunda/blob/0eca734b4a60816f7b4b8a651cb45110026e7fd1/dist/src/main/resources/api/backup-management-api.yaml)) are extended
  additively (optional `physicalTenant` query param, optional
  `physicalTenantId` response fields).
- `/v2/status`, `/v2/topology`, and the gRPC API are unchanged (ADR 001).
- Actuator endpoints are **kept** alongside the new REST endpoints for at
  least 8.10; no deprecation is declared in this milestone ("moving all
  management API to REST" is explicitly out of scope, kickoff task 7).

### D5. Out of scope for 8.10 (recorded to prevent scope creep)

Restore endpoints (Restore API epic); exporters management in REST;
rebalance in REST; per-tenant flow control; moving/deprecating any actuator;
fine-grained management RBAC (ADR 002 D6); tenant lifecycle APIs
(create/delete tenants, delete tenant data); a batch backup-monitoring API
beyond `GET /cluster/v2/backups/runtime/{id}`.

## Consequences

- `BackupRequestHandler`, `ExportingControlService`, and the history
  `BackupService` become group-parameterized (partition set / repository
  per tenant); the per-group topology lookup
  (`BrokerTopologyManager.getTopology(physicalTenantId)`) already exists,
  so the runtime-side work is scoping, not new protocol.
- Two OpenAPI surfaces to author: the `/v2` + `/cluster/v2` additions in
  `zeebe/gateway-protocol` (with `x-required-permissions` and the
  cluster-admin security scheme per ADR 002) and additive changes to
  [`backup-management-api.yaml`](https://github.com/camunda/camunda/blob/0eca734b4a60816f7b4b8a651cb45110026e7fd1/dist/src/main/resources/api/backup-management-api.yaml).
- QA matrix per the kickoff acceptance tests: backup/restore per tenant and
  cluster-wide, each with RDBMS, ES, and OS.
- Scaling and per-PT purge items wait on the dynamic-cluster-config work;
  everything in D1 except scaling can proceed independently.

## Alternatives considered

- **No-param actuator = default tenant** (instead of all tenants).
  Rejected: breaks the actuator's established whole-cluster meaning — an
  operator running `POST /actuator/backupRuntime` on a multi-tenant
  cluster would silently back up one tenant, which is exactly the
  data-loss trap this milestone must close.
- **Distinct backup ids per tenant in a cluster-wide backup** (gateway
  invents ids). Rejected: one logical "cluster backup at id X" is simpler
  to reason about, per-tenant namespaces already prevent collisions, and
  the caller keeps id control (required for scheduled/continuous setups).
- **Pause exporting implicitly as part of taking a backup.** Rejected:
  changes long-standing backup semantics and couples two operations that
  are deliberately independent today; the documented backup procedure
  continues to orchestrate them.
- **A dedicated list-tenants endpoint.** Deferred; `/cluster/v2/status`
  enumerates tenants (ADR 001 D4).

