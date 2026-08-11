# Solution proposal: upgrade-readiness actuator endpoint

**DRI**: TBD

**Status**: Proposed — Define-phase solution proposal for
[camunda/product-hub#3067](https://github.com/camunda/product-hub/issues/3067), not yet an
accepted decision. Once the team commits to a concrete direction, promote the accepted parts into a
numbered, `Accepted` ADR (and add it to [`docs/adr/README.md`](../README.md)'s index) rather than
flipping this file's status in place.

**Purpose**: Name the components relevant to computing "is this cluster ready for the next minor
upgrade," propose a concrete API and provider abstraction for it, and break the work into
independently shippable sub-issues. The API shape and the provider abstraction are both still open
for discussion — nothing here is final until the team accepts a direction.

**Audience**: Engineers scoping the Define → Implement transition for
camunda/product-hub#3067 (broker/engine, gateway, schema-manager/db, and Console/DL teams).

## Context

[camunda/product-hub#3067](https://github.com/camunda/product-hub/issues/3067) exists because
Console can trigger a minor-version upgrade immediately after the previous one completes, with no
check that the previous upgrade's background work has actually finished:

- RocksDB state migration (and the snapshot that persists it) on every partition
- Exporters catching up on every record written by the previous version, on every partition
- Secondary-storage schema migration (Elasticsearch/OpenSearch indices, or the RDBMS schema)

A back-to-back upgrade triggered before this work finishes risks record loss (a new version's
exporters replacing the previous version's before it finished exporting) and inconsistent secondary
storage. The epic's three-step plan is: (1) a quick-fix — a blanket 24h cooldown before the next
upgrade is allowed (already implemented, see the epic's
[Slack thread](https://camunda.slack.com/archives/C09AGA8FHCL/p1770212975849389)); (2) a proper
fix; (3) remove the quick-fix once the proper fix ships.

[RomanJRW's comment](https://github.com/camunda/product-hub/issues/3067#issuecomment-4346493387)
proposes the proper fix: replace the fixed cooldown with a polled readiness signal, with three
constraints that shape every option below:

- The overall answer is `true` only when **every** condition is met.
- Conditions start unmet at application startup and, once met, must never be reported as unmet
  again for that version (monotonic).
- Determining each condition is inherently **per-partition** — a cluster-wide answer requires
  aggregating across partitions and brokers.

The comment also flags open challenges this proposal addresses directly: RDBMS schema state may be
managed outside the running application; snapshot/export state must be known per partition, not
just per broker; and background data migrations don't run on every release, so their "completed"
signal needs a source of truth that exists independently of whether a migration ran at all.

## Proposed API (open for discussion)

RomanJRW's original sketch used a plain boolean per condition (`met: true/false`). Working through
the distributed-aggregation problem below surfaced a real third state: a condition can be
definitely unmet, or it can be **unknown** — some part of the cluster didn't answer the fan-out
request in time, which is not the same thing as "not migrated" and must not be treated as either a
green or a red light without distinction. Proposed shape:

```
GET /cluster/v2/upgrade-readiness   (exact path TBD — see Open questions)

{
  "upgradeable": false,
  "conditions": {
    "rdbmsSchemaMigrated": {
      "state": "MIGRATION_IN_PROGRESS",
      "detail": "RDBMS_SCHEMA_VERSION reports 8.9.0, expected 8.10.0"
    },
    "elasticsearchSchemaMigrated": {
      "state": "MIGRATED",
      "detail": "schema-version metadata doc reports 8.10.0"
    },
    "rocksDbMigrated": {
      "state": "UNKNOWN",
      "detail": "partition 3 (broker 2, follower) did not respond within the fan-out timeout"
    },
    "exportersFlushed": {
      "state": "MIGRATED",
      "detail": "all exporters on all partitions have flushed all 8.9.x records"
    }
  }
}
```

`state` is a 3-value enum per condition:

| State | Meaning |
|---|---|
| `MIGRATED` | Condition fully satisfied for the current app version, computed with complete information. |
| `MIGRATION_IN_PROGRESS` | Condition is confidently **not yet** satisfied (e.g. the last exported record is still on the old version, or the RDBMS version table hasn't reached the target version). A normal, expected, transient state right after an upgrade. |
| `UNKNOWN` | No confident answer — typically because part of a distributed fan-out (broker, partition, replica) didn't respond within the timeout. Distinct from `MIGRATION_IN_PROGRESS`: it means "we don't know," not "we know it's not done," and may warrant operator attention if it persists. |

`upgradeable` is `true` iff every condition's `state == MIGRATED`.

**Monotonicity, restated for the enum**: once a condition reaches `MIGRATED` for a given target
version, no later poll may report `MIGRATION_IN_PROGRESS` or `UNKNOWN` for that same version again.
This is precisely why the caching behavior described below matters — a transient fan-out timeout on
an already-confirmed-migrated partition must surface as "still `MIGRATED`, from the last successful
poll," not regress to `UNKNOWN`.

Open variants worth discussing before committing: whether `upgradeable` should itself become a
3-value field (`READY`/`NOT_READY`/`UNKNOWN`) mirroring the per-condition states, and whether
per-partition detail belongs in `detail` (free text, as sketched above) or as a structured
breakdown (`{"partitions": {"1": "MIGRATED", "3": "UNKNOWN"}}`).

## Relevant components

Three rounds of codebase exploration turned up the following reusable primitives. None of this is
new code — everything below already exists and already computes something close to what each MVP
condition needs; the design work is in exposing and combining it, not building it from scratch.

### Actuator/endpoint plumbing

| Component | Role |
|---|---|
| `dist/src/main/java/io/camunda/zeebe/shared/management/` | Where every hand-written actuator endpoint lives (`@Endpoint`, `@WebEndpoint`, `@RestControllerEndpoint`). Access is opt-in per endpoint via `management.endpoint.<id>.access=unrestricted` in `dist/src/main/resources/application.properties`. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/management/BrokerAdminServiceEndpoint.java` | `GET /actuator/partitions` — the closest existing per-partition status surface. Its `PartitionStatus` DTO already carries `snapshotId`, `processedPositionInSnapshot`, `exportedPositionInSnapshot`, `exporterPhase`, `exportedPosition`, `health`. Broker-node-local today. |
| `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/ClusterStatusController.java` + `service/src/main/java/io/camunda/service/ClusterStatusServices.java` / `TopologyServices.java` | The existing cluster-wide aggregation pattern: fold per-partition/per-tenant state into a 3-valued status (`HEALTHY`/`DEGRADED`/`DOWN`), exposed at `/cluster/v2/status`, unauthenticated. Modeled directly on the 3-surface design in [ADR 001](001-physical-tenant-health-status-topology.md). |
| `zeebe/backup/src/main/java/io/camunda/zeebe/backup/client/api/BackupRequestHandler.java` (`broadcastRequest`), `zeebe/gateway/src/main/java/io/camunda/zeebe/gateway/admin/ExportingRequestBroadcaster.java` | Existing "send one request per partition/broker, `CompletableFuture.allOf`, aggregate" helpers, sent over `BrokerClient` — the internal RPC pattern to reuse for the distributed conditions below, rather than inventing a new transport. |

### RocksDB migration / snapshot

| Component | Role |
|---|---|
| `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/migration/DbMigrationState.java` (interface: `state/immutable/MigrationState.java`) | Persists `migrated-by-version` per partition in RocksDB — the last broker software version that ran the engine's migrator against this partition's state. Confirmed to run on **every replica**, not just the leader (`MigrationTransitionStep.transitionTo` runs for any non-`INACTIVE` role) — a follower independently migrates its own local state on role transition. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/partitions/impl/steps/SnapshotAfterMigrationTransitionStep.java` + `impl/MigrationSnapshotDirector.java` | Already force-retries a snapshot after a migration until it succeeds — this machinery runs automatically today; the condition just needs to expose its completion state. |
| `zeebe/util/src/main/java/io/camunda/zeebe/util/VersionUtil.java`, `zeebe/util/src/main/java/io/camunda/zeebe/util/migration/VersionCompatibilityCheck.java` | Canonical "current app version" accessor and the shared same/minor/major upgrade-vs-downgrade classifier, already reused by both the engine and the RDBMS schema path. |

### Exporter flush

| Component | Role |
|---|---|
| `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/RecordMetadata.java` / `VersionInfo.java`, public accessor `Record.getBrokerVersion()` (`zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/Record.java`) | Every record durably carries the broker software version that wrote it. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/exporter/stream/ExportersState.java` | RocksDB-backed per-exporter, per-partition position (`getPosition`, `getLowestPosition`) — the durable "how far has this exporter flushed" watermark. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/exporter/stream/ExporterDirector.java` | Per-partition, per-exporter actor. Confirmed to run in `ACTIVE` mode on the leader (invokes real exporter side effects) and `PASSIVE` mode on followers (still updates `ExportersState`, per the `// PASSIVE, we consume the messages and set it in our state` comment at line 421 — followers passively mirror the same watermark, converging with normal replication lag). |

No existing code compares an exporter's position against the record version at that position — this
is genuinely new logic, but every input it needs is already durable and already per-partition, and
it can be read from **any** reachable replica, not only the leader.

### Secondary-storage schema

| Component | Role |
|---|---|
| `db/rdbms/src/main/java/io/camunda/db/rdbms/RdbmsSchemaVersionStore.java` (table `RDBMS_SCHEMA_VERSION`), `LiquibaseSchemaManager.java` | RDBMS: own version-tracking table (independent of Liquibase's own `DATABASECHANGELOG`), compared via `VersionCompatibilityCheck`. Because this is a plain table read, it works whether the migration ran inside this app process or via an external tool — directly addressing RomanJRW's "schema management can happen externally" concern. **This is the first provider to implement** (see Implementation breakdown), since it needs no fan-out at all. |
| `schema-manager/src/main/java/io/camunda/search/schema/SchemaManager.java` (`isSchemaReadyForUse()`), `SchemaMetadataStore.java` | ES/OS: computes exactly "schema matches what this app version expects" and persists the last-successfully-applied app version in a metadata document (id `schema-version`). |
| `dist/src/main/java/io/camunda/application/commons/search/SchemaReadinessCheck.java`, `cluster/src/main/java/io/camunda/cluster/SecondaryStorageReadiness.java` | Existing K8s-probe-facing readiness: answers "is storage *usable*" (`anyReady()` across tenants), not "is storage on the *latest* schema version" — a different question the new providers must not conflate with this existing one. |
| — | **Gap**: no actuator/health wiring exists today for `RdbmsSchemaVersionStore`; the only RDBMS health contributor is a generic `DataSourceHealthIndicator`. |

### Async/decoupled migration tasks (optional condition, not in this breakdown)

No framework for this exists today — confirmed gap, and deliberately **out of scope** for the
sub-issues below (see Implementation breakdown). Closest precedents, for when this becomes needed:
engine's synchronous `MigrationTaskState` (RocksDB-persisted per-task state, but blocks startup —
the opposite of decoupled); `schema-manager/src/main/java/io/camunda/search/schema/SchemaCleanup.java`
(fire-and-forget async, but no persisted progress); Optimize's standalone `UpgradeProcedure`/
`UpgradeStep` (resumable, step-tracked, but a separate CLI tool); `zeebe/exporters/camunda-exporter/src/main/java/io/camunda/exporter/tasks/CamundaBackgroundTaskManager.java`
(existing periodic-task scheduler a future async migration runner could plug into).

## Provider abstraction and the two-tier architecture

The natural implementation shape is a Spring `List<MigrationStatusProvider>` — the endpoint
collects every registered provider bean and merges their answers, the same idiom this codebase
already uses for `List<HealthIndicator>` (see `HealthConfigurationInitializer`):

```java
interface MigrationStatusProvider {
  String conditionName();               // e.g. "rdbmsSchemaMigrated"
  ConditionStatus getMigrationStatus();  // { state: MIGRATED|MIGRATION_IN_PROGRESS|UNKNOWN, detail }
}
```

The four conditions are not all the same *kind* of thing, though, and the interface needs to stay
honest about that:

| | RDBMS / ES-OS schema check | RocksDB snapshot / Exporter flush |
|---|---|---|
| Where does the truth live? | Centralized, shared secondary storage | Per-partition, per-replica, on local disk |
| Can any single node answer alone? | **Yes** — any node with a DB/ES client sees the whole schema state | **No** — a node only has the partitions it currently hosts |
| Needs cluster fan-out? | No | Yes, unavoidably |

So the RDBMS and ES/OS providers are single, simple, local beans wherever `schema-manager`/
`db/rdbms` clients already run — no further design needed there. The RocksDB and exporter providers
need a two-tier split so the distributed resolution happens exactly once per poll, not once per
node:

```
Cluster-wide endpoint (one process — gateway / combined app)
 └─ List<MigrationStatusProvider>
     ├─ RdbmsSchemaMigrationStatusProvider          (local, no fan-out)
     ├─ ElasticsearchSchemaMigrationStatusProvider  (local, no fan-out)
     ├─ ClusterRocksDbMigrationStatusProvider   ──broadcast──▶ every replica's local bean
     └─ ClusterExporterMigrationStatusProvider  ──broadcast──▶ any reachable replica's local bean

Every broker
 └─ local, no-network beans answering only for partitions this node hosts
```

Key points, from working through the distributed-aggregation question:

- **`ClusterRocksDbMigrationStatusProvider` and `ClusterExporterMigrationStatusProvider` each exist
  exactly once**, in the process hosting the cluster-wide endpoint — not replicated onto every
  broker, which would turn a single poll into an O(N²) burst of inter-broker requests.
- They use `BrokerClusterState`/`BrokerTopologyManager` for **routing only** (which broker hosts
  which partition) — gossiped topology carries role/health/broker-version, not migration-by-version
  or exporter watermarks, so it cannot answer the actual question. The question-and-answer itself
  needs a new internal request/response type sent via `BrokerClient`, following
  `BackupRequestHandler.broadcastRequest`/`ExportingRequestBroadcaster` — an existing internal
  channel, not an HTTP hop to another node's actuator port.
- **The fan-out target differs per condition**: `ClusterRocksDbMigrationStatusProvider` must reach
  **every replica** of every partition (leader and all followers), since a follower migrates
  independently and can become leader after an unmigrated boot. `ClusterExporterMigrationStatusProvider`
  only needs **one reachable replica** per partition, since followers passively mirror the same
  exported watermark.
- **Caching**: the aggregator must remember the last confirmed `MIGRATED` state per partition/
  condition and never let a fan-out timeout downgrade it — a timeout produces `UNKNOWN` only for
  partitions that have never yet been confirmed `MIGRATED`; a previously-confirmed partition stays
  `MIGRATED` even if unreachable on a later poll. This is what makes the monotonicity constraint
  hold under a flaky network, and it only needs to be built once, in the shared aggregator.

## Implementation breakdown (sub-issues)

Structured for direct use with the [`create-issue`](../../../.claude/skills/create-issue/SKILL.md)
skill — each item below is scoped to be independently shippable and reviewable. Suggested epic
task-list block (per the epic's own "Tasks breakdown" convention):

```
### Upgrade-readiness actuator endpoint
- [ ] Actuator endpoint + MigrationStatusProvider SPI + RdbmsSchemaMigrationStatusProvider
- [ ] ElasticsearchSchemaMigrationStatusProvider (and OpenSearch)
- [ ] RocksDbMigrationStatusProvider (local bean + cluster-wide broadcast)
- [ ] ExporterMigrationStatusProvider (local bean + cluster-wide broadcast)
```

### 1. Actuator endpoint + SPI + `RdbmsSchemaMigrationStatusProvider` (first, foundational)

**Depends on**: nothing — this is the base all other issues build on.

**Scope**:
- Define the `MigrationStatusProvider` SPI and the `ConditionStatus`/`MigrationState` (`MIGRATED`/
  `MIGRATION_IN_PROGRESS`/`UNKNOWN`) types.
- Add the new endpoint (path per the Proposed API section, subject to the Open questions below)
  that collects `List<MigrationStatusProvider>` and merges into the response shape above.
- Build the last-known-good caching behavior in the aggregator generically, even though only one
  local (non-distributed) provider exists yet — the later distributed providers depend on it.
- Implement `RdbmsSchemaMigrationStatusProvider`, backed by `RdbmsSchemaVersionStore` — no fan-out
  needed, so this is the smallest possible end-to-end slice through the whole design.
- The response only contains conditions for which a provider bean is registered; `upgradeable` is
  intentionally not meaningful until all four conditions exist. Document this as expected, staged
  rollout behavior, not a defect.

**Acceptance criteria**: endpoint reachable and returns a valid response containing the
`rdbmsSchemaMigrated` condition with a correct `state`, backed by a test that exercises a real
version mismatch (e.g. via an integration test against `RdbmsSchemaVersionStore`).

### 2. `ElasticsearchSchemaMigrationStatusProvider`

**Depends on**: 1 (SPI must exist).

**Scope**: same shape as the RDBMS provider, backed by `SchemaManager.isSchemaReadyForUse()`/
`SchemaMetadataStore`, covering both Elasticsearch and OpenSearch. No fan-out needed.

### 3. `RocksDbMigrationStatusProvider` (local bean + cluster-wide broadcast)

**Depends on**: 1 (SPI + aggregator caching).

**Scope**:
- Local, no-network bean per broker reporting `migrated-by-version` + snapshot-includes-migration
  state for every partition **replica** (leader and follower) hosted on that node.
- A new internal `BrokerClient` request/response type to query this from every replica of every
  partition, following the `BackupRequestHandler.broadcastRequest` pattern.
- `ClusterRocksDbMigrationStatusProvider`, living once in the cluster-endpoint process, using
  `BrokerClusterState` for routing and the new internal request for the actual answer.

**Note**: shares the new internal broadcast primitive with issue 4 — whichever of the two lands
first should build it generically enough for the other to reuse rather than duplicate.

### 4. `ExporterMigrationStatusProvider` (local bean + cluster-wide broadcast)

**Depends on**: 1 (SPI + aggregator caching); shares broadcast infrastructure with 3 (see note above).

**Scope**:
- Local bean per broker comparing the exporter's `ExportersState` position against
  `Record.getBrokerVersion()` at that position, for every partition replica hosted on that node.
- Reuses (or builds, if issue 3 hasn't landed yet) the internal broadcast request.
- `ClusterExporterMigrationStatusProvider`, targeting **any one reachable replica** per partition
  (not all replicas, unlike issue 3).

### Explicitly not an issue yet

Async/decoupled migration task tracking (the epic's optional condition) is deferred — no framework
exists today, and per RomanJRW's own comment the team's policy is to avoid such migrations wherever
possible. Revisit only once a concrete async migration needs a readiness signal.

## Open questions for the team

- **Ownership**: this spans broker/engine (conditions 1–2 in the original numbering, now issues 3–4
  above), schema-manager/db (issue 1–2), and gateway (aggregation, issue 1) — RomanJRW's comment
  itself notes uncertainty about whether this sits with DL, DS, or a combination. Needs an Eng DRI
  decision before Implement, and likely different DRIs per sub-issue.
- **Endpoint path and per-tenant scope**: cluster-wide only (like `/cluster/v2/status`) vs. exposing
  per-physical-tenant detail in the body (like `/v2/topology`'s per-partition detail) — recommend
  the latter, in the body, not as separate per-tenant endpoints.
- **`state` enum details**: whether `upgradeable` itself should become a 3-value field mirroring
  per-condition states, and whether per-partition detail belongs in free-text `detail` or a
  structured breakdown — both still open, see Proposed API.
- **Expected polling cadence from Console**: determines the fan-out timeout used for `UNKNOWN`, and
  whether a cached/gossiped alternative to on-demand fan-out is ever justified.
- **Quick-fix cutover criteria**: the epic's step 3 is to remove the 24h cooldown once this ships —
  worth agreeing up front on what "stable enough to remove the fallback" means (e.g. N releases in
  production without a regression). This is a Console-side follow-up, not a sub-issue here.

## References

- Epic: [camunda/product-hub#3067](https://github.com/camunda/product-hub/issues/3067)
- Solution idea comment: [camunda/product-hub#3067 (comment)](https://github.com/camunda/product-hub/issues/3067#issuecomment-4346493387)
- [`docs/adr/management/001-physical-tenant-health-status-topology.md`](001-physical-tenant-health-status-topology.md) — 3-surface health/readiness/status model this proposal reuses.
- [`docs/adr/management/002-management-endpoint-authorization.md`](002-management-endpoint-authorization.md) — authorization tiers for management endpoints, relevant once auth for this endpoint is decided.
- [`docs/adr/management/003-physical-tenant-management-endpoint-inventory.md`](003-physical-tenant-management-endpoint-inventory.md) — current inventory of management endpoints, for consistency when adding a new one.
