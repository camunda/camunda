---
architecture_md: 1
component: camunda/camunda/zeebe/engine
system: { id: orchestration-cluster, map: "camunda/camunda/SYSTEM.md" }
lifecycle: production
kind: [library]
summary: The Zeebe workflow engine, a deterministic state machine over a partition's record log that executes BPMN processes, evaluates DMN decisions and holds the runtime and identity state of the Orchestration Cluster.
team: { name: camunda/core-features, contact: unknown }
intake: { how: issue, template: "2. feature_request.yml", labels: [component/zeebe-engine] }
owns:
  - "BPMN process execution: process and element instance lifecycle, token flow, incidents, timers, compensation, ad-hoc subprocesses, process instance creation, modification, migration and suspension"
  - DMN decision evaluation inside the cluster (business rule tasks and standalone evaluation)
  - "Job lifecycle: creation, activation and hand-out (poll and push), completion, failure, retries, timeouts, job lease, secret references injected at hand-out"
  - "Message, signal and conditional event semantics: subscriptions, correlation (including Business ID), buffering and TTL"
  - "Camunda user task lifecycle and listeners: task listeners, execution listeners and global listeners"
  - "Deployment of BPMN, DMN, forms and resources: validation, transformation, versioning and distribution to all partitions"
  - "Variables: scoping, propagation, input/output mappings and cluster variables"
  - "Runtime state of identity entities (users, groups, roles, tenants, mapping rules, authorizations) and the authorization check on every externally triggered command"
  - "Batch operations (setup, chunking, execution) and history deletion commands"
  - "Agent instance and agent history records for agent execution (zeebe ADRs 0010-0013)"
  - "Engine state in RocksDB: column family layout, event appliers, state migrations and replay determinism"
  - "Inter-partition command semantics: deployment distribution, message subscriptions, generalized command distribution, partition scaling routing"
  - Usage metric records and engine processing metrics
does_not_own:
  - { concept: "Record schemas, value types, intents and the SBE protocol", owner: camunda/camunda/zeebe/protocol }
  - { concept: "Stream processor runtime, log replication, Raft, snapshots, partitioning and the RocksDB access layer (zb-db)", owner: camunda/camunda/zeebe/broker }
  - { concept: "Job push transport to workers (job streams)", owner: camunda/camunda/zeebe/broker }
  - { concept: "REST and gRPC endpoints, request validation and mapping", owner: camunda/camunda/zeebe/gateway-rest }
  - { concept: "Read queries over historical and runtime data (search, listing, filters)", owner: camunda/camunda/search }
  - { concept: "Domain services between gateways and the engine", owner: camunda/camunda/service }
  - { concept: "Exporting records to secondary storage", owner: camunda/camunda/zeebe/exporters }
  - { concept: "Authentication and the permission model", owner: camunda/camunda/security }
  - { concept: "Secret store backends and their caching", owner: camunda/camunda/secret-store }
  - { concept: "Binding of engine configuration properties (camunda.* unified configuration)", owner: camunda/camunda/configuration }
depends_on:
  - id: stream-platform
    component: camunda/camunda/zeebe/broker
    kind: library
    contract: "io.camunda:zeebe-stream-platform: RecordProcessor, ProcessingResultBuilder, InterPartitionCommandSender, scheduled task API (io.camunda.zeebe.stream.api)"
    versions: same monorepo release
    workaround_policy: never
  - id: zb-db
    component: camunda/camunda/zeebe/broker
    kind: library
    contract: "io.camunda:zeebe-db: ZeebeDb and ColumnFamily over RocksDB (rocksdbjni, version.rocksdbjni in parent/pom.xml)"
    versions: same monorepo release
    workaround_policy: never
  - id: protocol
    component: camunda/camunda/zeebe/protocol
    kind: schema
    contract: "io.camunda:zeebe-protocol and zeebe-protocol-impl: record value types, intents, ValueType, ZbColumnFamilies"
    versions: "same monorepo release; SBE changes checked by revapi (zeebe/protocol/revapi.json)"
    workaround_policy: never
  - id: security
    component: camunda/camunda/security
    kind: library
    contract: "io.camunda:camunda-security-core, camunda-security-protocol: authorization resource and permission types, tenant access"
    versions: same monorepo release
    workaround_policy: never
  - id: security-library
    component: camunda/camunda-security-library
    kind: library
    contract: "io.camunda:camunda-security-library-api, -core, -validation (TenantAccessProvider, validation)"
    versions: "pinned by version.camunda-security-library in parent/pom.xml"
    workaround_policy: never
  - id: secret-store
    component: camunda/camunda/secret-store
    kind: library
    contract: "io.camunda:camunda-secret-store-api: SecretStore SPI and per-tenant SecretStoreRegistry"
    versions: same monorepo release
    workaround_policy: never
  - id: search
    component: camunda/camunda/search
    kind: library
    contract: "io.camunda:camunda-search-client, camunda-search-domain: secondary-storage reads for batch operation item providers and history deletion"
    versions: same monorepo release
    workaround_policy: never
  - id: feel-scala
    component: camunda/feel-scala
    kind: external
    contract: "org.camunda.feel:feel-engine, wrapped by zeebe/feel and zeebe/expression-language"
    versions: "pinned by version.feel-scala in parent/pom.xml"
    workaround_policy: adapter-boundary
  - id: dmn-scala
    component: camunda/dmn-scala
    kind: external
    contract: "org.camunda.bpm.extension.dmn.scala:dmn-engine, wrapped by zeebe/dmn"
    versions: "pinned by version.dmn-scala in parent/pom.xml"
    workaround_policy: adapter-boundary
consumers:
  - { who: camunda/camunda/zeebe/broker, via: "Engine (RecordProcessor), EngineProcessors, EngineConfiguration, QueryService, JobStreamer, identity initialize configurers", promise: "internal Java API; changes land with the broker in the same PR" }
  - { who: camunda/camunda/configuration, via: "EngineConfiguration, GlobalListenersConfiguration", promise: "internal Java API; same release" }
  - { who: "camunda/camunda/zeebe/exporters and every exporter (Camunda, RDBMS, ES/OS, custom), and through them Operate, Tasklist, Admin and Optimize", via: "records the engine writes to the log", promise: "event semantics never change; new behavior gets new events or a new event version" }
  - { who: "Gateways and services (zeebe/gateway-grpc, zeebe/gateway-rest, service) and every API client behind them", via: "command responses and rejections over the broker command API", promise: "compatible within rolling updates from the previous minor" }
  - { who: "debug-cli, microbenchmarks, zeebe/qa (integration and update tests)", via: "engine classes and RocksDB state", promise: none }
exposes:
  - { contract: "Engine record processor (Engine, EngineProcessors)", spec: "src/main/java/io/camunda/zeebe/engine/Engine.java", policy: "internal; broker only" }
  - { contract: "Record semantics: which commands are accepted, which events and rejections they produce", spec: "../protocol", policy: "events and event appliers never change after release; add a new event or applier version (docs/zeebe/event-applier-golden-files.md)" }
  - { contract: "Persistent state in snapshots (RocksDB column families)", spec: "../../docs/zeebe/rolling_updates.md", policy: "readable by every later minor; avoid data migrations; migrations through DbMigrator" }
  - { contract: "EngineConfiguration and feature flags", spec: "src/main/java/io/camunda/zeebe/engine/EngineConfiguration.java", policy: "update config templates; behavior changes may ship behind a kill-switch flag" }
  - { contract: "Extension point JobStreamer: push hand-out of activated jobs, implemented by the broker (RemoteJobStreamer)", spec: "src/main/java/io/camunda/zeebe/engine/processing/streamprocessor/JobStreamer.java", policy: "internal; implementer may push or decline a job, the engine owns the job state" }
  - { contract: "Extension point QueryService: read-only state queries for the broker's command and query API", spec: "src/main/java/io/camunda/zeebe/engine/state/QueryService.java", policy: internal }
  - { contract: "Global listeners: configured task and execution listeners applied to every process", spec: "src/main/java/io/camunda/zeebe/engine/GlobalListenersConfiguration.java", policy: "user-facing configuration; compatibility promise not documented" }
constraints:
  - { id: C1, name: Replay determinism and event applier immutability, hard: true, ref: ../../docs/zeebe/event-applier-golden-files.md }
  - { id: C2, name: Rolling-update compatibility of records and processing, hard: true, ref: ../../docs/zeebe/rolling_updates.md }
  - { id: C3, name: No full data migrations, hard: false, ref: "../../docs/zeebe/rolling_updates.md#data-migrations" }
  - { id: C4, name: Single-threaded non-blocking processing, hard: true, ref: "README.md#dos-and-donts" }
  - { id: C5, name: Authorization of externally triggered commands, hard: true, ref: "../../docs/zeebe/developer_handbook.md#authorization-checks-in-the-engine" }
  - { id: C6, name: Tenant isolation, hard: true, ref: ../../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md }
  - { id: C7, name: Idempotent inter-partition commands, hard: true, ref: "../../docs/zeebe/developer_handbook.md#how-to-do-inter-partition-communication" }
  - { id: C8, name: No secondary storage from processors, hard: true, ref: "README.md#processors-do-not-have-access-to-the-secondary-storage" }
  - { id: C9, name: Secret values never on the log, hard: true, ref: ../../docs/adr/secrets/001-central-secret-resolution-architecture.md }
  - { id: C10, name: New records reach exporters and test tooling, hard: false, ref: "../../docs/zeebe/developer_handbook.md#how-to-create-a-new-record" }
  - { id: C11, name: Event applier versions aligned across minors, hard: true, ref: ../../docs/zeebe/event-applier-golden-files.md }
decisions: ../docs/adr/README.md
planning: { issue_templates: { increment: "3. task.yml", epic: "4. epic breakdown.yml" } }
---

# Architecture — camunda/camunda/zeebe/engine

> Draft from the repository; not reviewed by its team. Facts marked `TODO(confirm)` are inferred.

## 1. Purpose

The Zeebe workflow engine executes BPMN processes and evaluates DMN decisions for the Orchestration
Cluster, and keeps the state of every runtime and identity entity (users, roles, authorizations,
user tasks) that Operate, Tasklist and Admin show. It runs inside the broker, one instance per
partition, as a stream processor over that partition's log ([README](README.md)). Part of the
[Orchestration Cluster system](../../SYSTEM.md) (role `engine`).

## 2. Ownership boundary

**Owns:** the meaning of every command and event on the log: which commands it accepts, which
events and rejections they produce, and how events change the state in RocksDB. That covers BPMN
execution, DMN evaluation, jobs, messages and signals, user tasks and listeners, deployments,
variables, identity entities and their authorization checks on the write path, batch operations
and history deletion, agent records, and the inter-partition protocols built on commands. The full
list is in the front matter.

Owner: no line in [CODEOWNERS](../../CODEOWNERS) covers `zeebe/engine/`. TODO(confirm): the team
is read as `@camunda/core-features` from the `engine-expert` skill's CODEOWNERS line
(`/.claude/skills/engine-expert/ @korthout @camunda/core-features`) and the "Identity / Core
Features team" DRI of [security ADR 002](../../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md).
Contact channel: TODO(confirm).

TODO(confirm): [SYSTEM.md](../../SYSTEM.md) § 1 counts `zeebe/bpmn-model`, `zeebe/feel`,
`zeebe/dmn`, `zeebe/expression-language`, `zeebe/feel-tagged-parameters` and `zeebe/msgpack-*` as
part of this component. Whether this team owns them (including the `zeebe:*` BPMN extension
elements users model against) is not stated anywhere in the repo.

**Does not own (route here instead):**

| If you need… | It belongs to | How to ask |
|---|---|---|
| A new record value type, intent or field in the record schema | `camunda/camunda/zeebe/protocol` (the engine then implements it) | TODO(confirm): same team? |
| A change to the processing loop, replay scheduling, Raft, snapshots, partitioning, `zb-db` | `camunda/camunda/zeebe/broker` (stream-platform, zb-db; `@camunda/zeebe-distributed-platform` for `zeebe/stream-platform`) | issue, `component/zeebe-platform` |
| Job push transport to workers | `camunda/camunda/zeebe/broker` (`RemoteJobStreamer`) | issue, `component/zeebe-platform` |
| A REST endpoint or gRPC method | `camunda/camunda/zeebe/gateway-rest`, `zeebe/gateway-grpc`; OpenAPI spec `@camunda/c8-api-team` | issue |
| Listing, filtering or searching data | `camunda/camunda/search`, `camunda/camunda/service` (reads never go through the engine) | issue, `component/data-layer` |
| Getting records into Elasticsearch, OpenSearch or RDBMS | `camunda/camunda/zeebe/exporters` | issue |
| The permission model, authentication, tenant-access provider | `camunda/camunda/security`, Camunda Security Library | issue |
| A secret store backend | `camunda/camunda/secret-store` | issue |
| A configuration property name or binding | `camunda/camunda/configuration` | issue |

## 3. Structure

Paths below are under `src/main/java/io/camunda/zeebe/engine/`.

| Path | What it holds |
|---|---|
| `Engine.java`, `EngineConfiguration.java` | Entry point the broker runs (implements `RecordProcessor` from stream-platform) and its configuration |
| `processing/` | Command processors per domain (`bpmn`, `job`, `message`, `usertask`, `deployment`, `identity`, `batchoperation`, …), `Behavior` classes for shared logic, scheduled tasks ("checkers"), `EngineProcessors` / `BpmnProcessors` registration |
| `processing/streamprocessor/` | Typed processor API and writers (state, command, rejection, response, side effects); `JobStreamer` |
| `state/immutable/`, `state/mutable/` | Read-only and mutable state interfaces |
| `state/<domain>/` | `Db*State` implementations over `zb-db` column families |
| `state/appliers/` | Event appliers, registered with versions in `EventAppliers` |
| `state/migration/` | Snapshot migrations (`DbMigrator`, `to_8_x`) |
| `metrics/` | Processing metrics |

Direction inside the component, enforced in [`qa/archunit-tests`](../../qa/archunit-tests)
(`StateModificationArchTest`, `ProcessorNamingArchTest`):

- Processors read `state/immutable` only; only event appliers (and migrations) write through
  `state/mutable`.
- Processors write events through the state writer, and events are applied by appliers, so every
  state change is on the log.
- Shared logic goes into `Behavior` classes (composition), not processor subclasses.
- Secondary storage (`search`) is read only from scheduled tasks and batch-operation item providers,
  never from a processor.

There is no variant-specific code: SaaS and Self-Managed run the same engine; behavior differences
come from `EngineConfiguration` and feature flags (`zeebe/util` `FeatureFlags`).

Extension points the engine defines and others implement or configure: `JobStreamer` (the broker
pushes activated jobs to streams), `QueryService` (the broker's query API), the identity
initialize configurers (initial tenants, roles, groups, authorizations from configuration), and
global listeners (task and execution listeners configured for every process). TODO(confirm): what
an implementer may do beyond the current broker implementation, and the compatibility promise for
global listener configuration.

## 4. Binding decisions

ADR index for Zeebe: [`zeebe/docs/adr/`](../docs/adr/README.md); cross-cutting:
[`docs/adr/`](../../docs/adr/README.md). Those that most often shape engine features:

- [Central secret resolution](../../docs/adr/secrets/001-central-secret-resolution-architecture.md):
  placeholders on the log, values injected only at job hand-out, resolution off the processing
  thread.
- [Tenant-access provider seam](../../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md):
  the engine keeps its own tenant resolver over the CSL `TenantAccessProvider` interface.
- [Business ID message correlation](../docs/adr/0001-810-message-correlation-business-id-cross-partition.md)
  and [late assignment](../docs/adr/0006-810-late-business-id-assignment.md).
- [Job lease](../docs/adr/0005-810-job-lease.md), [suspended job state](../docs/adr/0008-810-suspended-job-state.md),
  [suspended timer buffering](../docs/adr/0009-810-suspended-timer-buffering.md).
- [Agent execution as engine records](../docs/adr/0010-810-agent-execution-in-engine-records.md)
  (and ADRs 0011–0013).
- Physical tenants: [orchestration-cluster ADRs 0003–0009](../../docs/adr/orchestration-cluster/README.md).

Long-standing rules without an ADR live in the [README § Do's and Don'ts](README.md#dos-and-donts),
the [developer handbook](../../docs/zeebe/developer_handbook.md) and the `engine-expert` skill's
iron rules (`.claude/skills/engine-expert/SKILL.md`).

## 5. Planning constraints

Every plan must answer each of these (applies / n/a + decision or reason).

### C1 — Replay determinism and event applier immutability
- **Question:** Does the change touch an event applier, a `Mutable*State` method or anything an
  applier calls? If so, which new event or applier version does it add instead of changing a
  released one, and does `NoChangesTest` (golden files) pass?
- **Hard:** yes
- **Detail:** [Event applier golden files](../../docs/zeebe/event-applier-golden-files.md),
  [README](README.md#event-appliers-are-not-allowed-to-be-changed-after-having-been-released)

### C2 — Rolling-update compatibility of records and processing
- **Question:** Which new commands, events or intents does it add, and what happens while brokers
  of the previous minor's patches still process or replay the log? Does it change the network
  protocol or SBE layout?
- **Hard:** yes
- **Detail:** [Rolling updates](../../docs/zeebe/rolling_updates.md)

### C3 — No full data migrations
- **Question:** Does the change need existing state in a new shape? Which of the alternatives
  (new column family, mixed data, migrate on access, secondary column family) does it use instead
  of a migration of a whole column family?
- **Hard:** no (strongly avoided)
- **Detail:** [Data migrations](../../docs/zeebe/rolling_updates.md#data-migrations)

### C4 — Single-threaded non-blocking processing
- **Question:** Can any step block or run long on the stream processor thread (I/O, large loops,
  unbounded recursion over user input)? How is large work split into follow-up commands, and how
  do scheduled tasks yield? Do hot paths log at trace level only?
- **Hard:** yes
- **Detail:** [README](README.md#dos-and-donts), `engine-expert` iron rules

### C5 — Authorization of externally triggered commands
- **Question:** Which `AuthorizationResourceType` and `PermissionType` does each new
  externally triggered command check? Does it need a new permission (PM sign-off, REST enum in
  `rest-api.yaml`, `x-required-permissions`)?
- **Hard:** yes
- **Detail:** [Developer handbook](../../docs/zeebe/developer_handbook.md#authorization-checks-in-the-engine),
  [security ADR 001](../../docs/adr/security/001-endpoint-required-permission-mapping.md)

### C6 — Tenant isolation
- **Question:** How does the change carry and check the tenant ID, and does it behave with
  multi-tenancy off and with several physical tenants?
- **Hard:** yes
- **Detail:** [security ADR 002](../../docs/adr/security/002-tenant-access-provider-ownership-and-seam.md),
  [orchestration-cluster ADRs](../../docs/adr/orchestration-cluster/README.md)

### C7 — Idempotent inter-partition commands
- **Question:** Does the change send commands to other partitions? What retries them, and how does
  the receiver reject redundant commands?
- **Hard:** yes
- **Detail:** [Developer handbook](../../docs/zeebe/developer_handbook.md#how-to-do-inter-partition-communication)

### C8 — No secondary storage from processors
- **Question:** Does the change need data from secondary storage? Is it read from a scheduled task
  (not a processor), and what happens when secondary storage is absent or lagging?
- **Hard:** yes
- **Detail:** [README](README.md#processors-do-not-have-access-to-the-secondary-storage)

### C9 — Secret values never on the log
- **Question:** Can a secret value, or a reference taken from runtime data, reach a record, the
  state or a snapshot?
- **Hard:** yes
- **Detail:** [Central secret resolution ADR](../../docs/adr/secrets/001-central-secret-resolution-architecture.md)

### C10 — New records reach exporters and test tooling
- **Question:** For a new or extended record: which exporters (Camunda, RDBMS, ES/OS), docs and
  Camunda Process Test need to support it, and who does that work?
- **Hard:** no
- **Detail:** [Developer handbook](../../docs/zeebe/developer_handbook.md#how-to-create-a-new-record)

### C11 — Event applier versions aligned across minors
- **Question:** If the change is backported, does every new applier version also reach every newer
  minor's initial release (watch the minor code freeze)?
- **Hard:** yes
- **Detail:** [Event applier golden files](../../docs/zeebe/event-applier-golden-files.md)

## 6. Data and persistence

- Primary state: RocksDB through `zeebe/zb-db`, one database per partition, column families
  declared in `ZbColumnFamilies` (`zeebe/protocol`). State is rebuilt from snapshots plus replay of
  events; it is part of every snapshot and backup.
- Snapshot migrations run when a new version opens an old snapshot (`state/migration/`); they are
  avoided (C3).
- State reads return values backed by shared, reused buffers: copy before keeping them.
- Secondary storage (Elasticsearch, OpenSearch, RDBMS) is read-only for the engine, through
  `search`, and only from scheduled tasks (C8). The engine never writes to it; exporters do.
- Data volume: TODO(confirm) — no documented limits on state size per partition beyond the
  migration cost notes in [rolling updates](../../docs/zeebe/rolling_updates.md#data-migrations).

## 7. Cross-cutting qualities

- **Security:** write-path authorization is checked in the engine for every command from outside
  it; internal follow-up commands are not checked ([handbook](../../docs/zeebe/developer_handbook.md#authorization-checks-in-the-engine)).
  Read-path authorization is in `service`/`search`, not here.
- **Tenancy:** tenant ID on records and state; tenant checks through the engine's own resolver
  over the CSL interface (C6).
- **Performance:** thousands of commands per second on one thread per partition; records are
  reused rather than constructed; no INFO/DEBUG logging on hot paths; new features should consider
  new metrics (`metrics/`).
- **Reliability:** side effects are not guaranteed to run (no must-happen work in them);
  exceptions roll back state but not already-executed side effects; generated keys must appear on
  an appended record.
- **Observability:** Micrometer metrics in `metrics/`; `CompactRecordLogger` for test debugging.

## 8. Delivery

As [Orchestration Cluster SYSTEM.md](../../SYSTEM.md) § 6 (monorepo release, backports through
the backport action), plus:

- Rolling updates are supported from all patches of the previous minor (since 8.5); new events
  and intents are a transient incompatibility, changed event behavior is not allowed (C2).
- Backports that add event applier versions must also reach every newer minor's initial release
  (C11).
- Behavior changes that may need reverting during an upgrade ship behind a feature flag used as a
  kill-switch (example: `evaluateBoundaryEventCorrelationKeyInActivityScope`,
  [rolling updates § Feature flags](../../docs/zeebe/rolling_updates.md#feature-flags)).
- TODO(confirm): whether there is a rule for when a feature flag is required and when it is
  removed.

## 9. Testing expectations

- **State and appliers:** JUnit 5 with `ProcessingStateExtension`; assert on state.
- **Processors:** `EngineRule` (JUnit 4) with `RecordingExporter`; assert on appended records,
  never on state; always short-circuit `RecordingExporter` (`limit`, `getFirst`, `exists`).
- **Applier changes:** `NoChangesTest` in `EventAppliersTest` (golden files) must pass.
- **Architecture:** `StateModificationArchTest`, `ProcessorNamingArchTest` in `qa/archunit-tests`.
- **Integration and upgrades:** `zeebe/qa/integration-tests`; update and rolling-update tests in
  `zeebe/qa/update-tests` ([acceptance testing](../../docs/testing/acceptance.md)); cross-component
  acceptance tests in `qa/` for features visible through the API.
- Re-run new or changed tests at least three times. Detail: [README § Testing guidelines](README.md#testing-guidelines),
  `.claude/skills/engine-expert/testing.md`.

## 10. Planning conventions

- Issues: `component/zeebe-engine` label, templates `2. feature_request.yml`, `3. task.yml`,
  `4. epic breakdown.yml` in `.github/ISSUE_TEMPLATE/` (labels per the `create-issue` skill).
- ADRs for engine decisions go to [`zeebe/docs/adr/`](../docs/adr/README.md), numbered with the
  minor (`NNNN-810-…`).
- TODO(confirm): plans directory, ID prefix for plan refs, and whether engine plans use a spec
  format beyond ADRs.

## 11. Glossary

| Term | Meaning here |
|---|---|
| Command / event / rejection | The three record types: a request, the resulting state change, a refused request |
| Processing vs. replay | Processing handles commands and writes events; replay re-applies events to rebuild state |
| Event applier | Code that applies one event (one version) to state; frozen once released |
| Behavior | Shared processor logic, used by composition |
| Scheduled task / checker | Periodic engine task that appends commands (timeouts, timers, retries) |
| Side effect | Work after commit (responses, caches), not guaranteed to run |
| Partition | A shard with its own log, engine and state; partitions talk via inter-partition commands |
| Physical tenant | An isolated tenant of the cluster with its own configuration and stores; not the same as the `tenantId` of multi-tenancy |
| Business ID | User-assigned process instance identifier used for uniqueness and message correlation |
