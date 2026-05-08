# System Partition as a Full Stream Processor — Design

**Status:** Brainstorm-validated design. Hackday MVP scope. Greenfield only, behind feature flag `experimental.systemPartition.enabled`.
**Date:** 2026-05-08
**Branch:** `dd-hackday-system-partition` (continues from the existing system-partition MVP merged in commit `0d931c7fd14`).
**Audience:** Engineers implementing the next iteration of the system partition.

---

## 1. Problem & goal

The current system partition (`zeebe/system-partition/`, branch `dd-hackday-system-partition`) is a hand-rolled `Actor` that listens on `RaftCommitListener`, holds a single in-memory `ClusterConfiguration`, and writes log entries via Atomix `ZeebeLogAppender`. It is **not** a stream processor. There is no `LogStream`, no `ZeebeDb`/RocksDB state, no `RecordProcessor` chain, no engine.

A separate hackday branch (`origin/dd-hackday-dynamic-config-system`) added BPMN-orchestrated cluster operations (scale, exporter toggle, modification) and BPMN-driven backup scheduling (checkpoint, retention). On that branch:

- Cluster configuration is persisted as a global cluster variable (`camunda.vars.cluster.clusterConfiguration`) detected via prefix in `VariableBehavior.extractAndEmitClusterVariableUpdates`.
- Cluster-management BPMNs run on a regular data partition.
- Job workers (`BpmnConfigurationChangeJobWorker`, `RedistributionCalculationJobWorker`, `ExporterCalculationJobWorker`, `ClusterVariableUpdateJobWorker`) read the working configuration from process variables, call `ClusterConfigurationManagerImpl.applyOperationDirectly`, and write the new configuration back as a cluster variable.
- Backup scheduling BPMNs are deployed by `CheckpointSchedulingService`. Job workers (`CheckpointTriggerJobWorker`, `RetentionTriggerJobWorker`) trigger `BackupRequestHandler.checkpoint(...)` and `BackupRetention.triggerRetention()` on the broker side.

**Goal of this work.** Combine the two: turn the system partition into a full stream processor, model `ClusterConfiguration` as a first-class record on it, run the cluster-management BPMNs there, and execute the backup control plane there. The cluster-variable mechanism is no longer used to carry the cluster configuration.

---

## 2. Scope

In scope (hackday MVP):

- Replace the hand-rolled state machine with a real `StreamProcessor` running on a `LogStream` backed by the existing system-partition `RaftPartition`.
- Mount the existing `EngineProcessors` factory wholesale (Approach A from the brainstorm) plus two new processor groups: `ClusterConfigurationProcessors` and `BackupControlPlaneProcessors`.
- Introduce two first-class record types in `zeebe/protocol/`: `ClusterConfigurationRecord` and `BackupMetadataRecord`.
- Auto-deploy cluster-management BPMNs (scale-operation, exporter-operation, modification_starter) and backup-scheduling BPMNs (checkpoint, retention) onto the system partition.
- Port the job workers from `dd-hackday-dynamic-config-system` so they read the current configuration from the system-partition facade and emit cluster-configuration commands instead of writing cluster variables.
- Run the backup control plane (orchestration of `CheckpointRecord` fan-out to data partitions, backup metadata index, retention sweep) on the system partition.

Explicitly out of scope:

- Migration from existing 8.x clusters (greenfield only).
- Disaster-recovery escape hatch when the system partition loses quorum.
- Production-grade observability (custom metrics, dashboards, runbooks).
- Per-partition snapshot work — data partitions still produce their own snapshots; the control plane lives on the system partition.
- `VariableBehavior`'s cluster-variable prefix detection: untouched. The cluster-variable mechanism remains for any non-cluster-config use; it simply no longer carries the cluster configuration.
- Multi-cluster federation.

---

## 3. Architecture

### 3.1 Topology

```
┌──────────────────────────────────────────────────────────────────────────┐
│                System Partition (Raft + LogStream)                       │
│   Members: lowest-nodeId brokers, count = systemRF                       │
│   Storage: {dataDir}/system/partitions/1/                                │
│                                                                          │
│   ┌────────────────────────────────────────────────────────────────┐    │
│   │   StreamProcessor (Actor)                                      │    │
│   │     RecordProcessor chain:                                     │    │
│   │       • EngineProcessors (Deployment, Process, Job, Variable,  │    │
│   │         Incident, Timer, etc. — reused as-is)                  │    │
│   │       • ClusterConfigurationProcessors (NEW)                   │    │
│   │       • BackupControlPlaneProcessors (NEW)                     │    │
│   │                                                                │    │
│   │   ZeebeDb (RocksDB):                                           │    │
│   │     • engine state (process instances, jobs, vars, …)          │    │
│   │     • cluster_configuration column family (NEW)                │    │
│   │     • backup_metadata column family (NEW)                      │    │
│   └────────────────────────────────────────────────────────────────┘    │
│                              │                                           │
│                              │ leader → coordinator                      │
└──────────────────────────────┼───────────────────────────────────────────┘
                               │
       ┌───────────────────────┼───────────────────────────────────────┐
       │                       │                                       │
  cluster-mgmt BPMN     coordinator path                       backup fan-out
  job workers           (ConfigurationChangeCoordinator        (BackupOrchestrator
  (broker-side actors,   stamps change plan via                actor on leader,
   subscribed via        CC.STAMP_CHANGE_PLAN command          fans CheckpointRecord
   gateway):             on system partition)                   commands to data
   • config-change-{m}                                          partition leaders)
   • redistribution-calc
   • exporter-calc
   • checkpoint-trigger
   • retention-trigger

  ClusterConfigUpdateListener (downstream of CC.UPDATED commits) →
      persists .topology.meta on every broker, gossips downstream.
```

### 3.2 Properties

- **Single source of truth.** The authoritative `ClusterConfiguration` lives in the `cluster_configuration` RocksDB column family on the system partition. Every authoritative mutation is a committed record on the system partition's log.
- **Coordinator role.** `ConfigurationChangeCoordinatorImpl.isCoordinator()` continues to return `systemPartition.isLeader()`. The static `COORDINATOR_NODE_ID = 0` and lowest-member-id fallback remain inactive when the feature flag is on.
- **Linearizable read-modify-write.** Raft term fencing (already provided by `StreamProcessor`'s leader-only processing) plus an application-level CAS on `expectedPreviousVersion` carried in `ClusterConfigurationRecord`.
- **BPMN drives sequence; configuration updates per acknowledged operation.** A change is stamped once (`CC.STAMP_CHANGE_PLAN`); a BPMN process loops over `pendingOperations`; each successfully applied Join/Leave/Reconfigure (or exporter enable/disable) emits one `CC.APPLY_OPERATION` command, producing one `CC.OPERATION_APPLIED` event.
- **Gossip unchanged.** Non-system-partition members still receive the cluster configuration via gossip. The `SystemPartitionMirror` now subscribes to commit events on the system partition's stream rather than to `RaftCommitListener`.
- **Backup control plane on the system partition; data plane on data partitions.** Per-partition snapshots and uploads still happen on data partitions via the existing `CheckpointRecordsProcessor` / `BackupService`. The system partition orchestrates: it holds the backup metadata index, fans out `CheckpointRecord` commands, and runs retention.

---

## 4. Component inventory

### 4.1 New components

#### `zeebe/system-partition/` evolution

| Class | Role |
|---|---|
| `SystemPartitionStreamProcessorFactory` | Builds a `LogStream` on top of the system-partition `RaftPartition` (reuses the `AtomixLogStorage` pattern from data partitions), then `StreamProcessor.builder()` configured with the engine + cluster-config + backup processor sets. Replaces `SystemPartitionStateMachine`. |
| `SystemPartitionLogStream` | `LogStream` implementation over the system-partition `RaftPartition`. |
| `SystemPartition` (interface, refactored) | `query(): ClusterConfiguration`, `isLeader(): boolean`, `addLeaderListener(Consumer<Boolean>)`, `addClusterConfigListener(Consumer<ClusterConfiguration>)`. The previous `update(ClusterConfiguration)` API is removed; writes go through stream-processor commands. |
| `SystemPartitionFacadeImpl` | Reads `current` from an in-memory snapshot updated by a `CommitListener`. Exposes `submitCommand(Intent, ClusterConfigurationRecord)` for non-member brokers' RPC bridge. |
| `SystemPartitionMirror` (refactored) | `ClusterConfigurationUpdateListener` adapter: subscribes to commit events on the system-partition stream, persists `.topology.meta`, and gossips. |
| `SystemPartitionBpmnAutoDeployer` | On leader transition: deploys cluster-management BPMNs (lifted from `dd-hackday-dynamic-config-system`'s `BpmnAutoDeployer`) into the system partition via `BrokerClient`. Idempotent — engine deduplicates by `processId + version`. |

#### `zeebe/protocol/` and `zeebe/protocol-impl/`

| Type | Intents |
|---|---|
| `ClusterConfigurationRecord` (msgpack, fields: proto-encoded `ClusterConfiguration`, `expectedPreviousVersion: long`, `requestId: String`) | `STAMP_CHANGE_PLAN`, `CHANGE_PLAN_STAMPED`, `APPLY_OPERATION`, `OPERATION_APPLIED`, `COMPLETE_CHANGE`, `CHANGE_COMPLETED`, `REJECT` |
| `BackupMetadataRecord` (msgpack, fields: `checkpointId: long`, `partitionId: int`, `status: enum {PENDING, COMPLETED, FAILED, CONFIRMED}`, `descriptor: bytes`) | `RECORD`, `RECORDED`, `MARK_FAILED`, `MARKED_FAILED`, `DELETE`, `DELETED` |

`ValueType` enum extended with `CLUSTER_CONFIGURATION` and `BACKUP_METADATA`. `protocol-impl` provides POJOs and msgpack readers/writers consistent with existing record types.

#### `zeebe/system-partition/.../processors/`

| Processor | Triggers on | Action |
|---|---|---|
| `ClusterConfigurationStampProcessor` | `CC.STAMP_CHANGE_PLAN` command | Reject if an active change plan exists. Otherwise CAS-check and write `CC.CHANGE_PLAN_STAMPED` event with the new `ClusterConfiguration` carrying `pendingOperations`. |
| `ClusterConfigurationApplyOperationProcessor` | `CC.APPLY_OPERATION` command | CAS-check; reuse the existing `ClusterConfigurationChangeOperationApplier` chain to compute the next configuration with the operation removed from `pendingOperations` and the version bumped; emit `CC.OPERATION_APPLIED`. On CAS miss, emit `CC.REJECT`. |
| `ClusterConfigurationCompleteChangeProcessor` | `CC.COMPLETE_CHANGE` command | Move active plan to `lastChange`, bump version, emit `CC.CHANGE_COMPLETED`. |
| `ClusterConfigurationStateApplier` (event handler) | `CC.CHANGE_PLAN_STAMPED`, `CC.OPERATION_APPLIED`, `CC.CHANGE_COMPLETED` | Deterministic write to the `cluster_configuration` column family on every replica. |
| `BackupMetadataRecordProcessor` | `BM.RECORD` / `BM.MARK_FAILED` / `BM.DELETE` commands | Validate, emit corresponding `*ED` event. |
| `BackupMetadataStateApplier` | `BM.RECORDED` / `BM.MARKED_FAILED` / `BM.DELETED` events | Deterministic write to the `backup_metadata` column family. |

#### Backup control plane

| Class | Role |
|---|---|
| `BackupOrchestrator` (Actor) | Started on the system-partition leader. Subscribes to `BM.RECORDED` events via `CommitListener`. Fans out `CheckpointRecord(CREATE / CONFIRM_BACKUP / DELETE_BACKUP)` to each data-partition leader via `BrokerClient`. On reply, submits `BM.RECORD` / `BM.MARK_FAILED` commands back to the system partition. Idempotent: duplicate `CREATE` for a known checkpointId is a no-op on data partitions. |
| `BackupRetentionService` (job-worker logic, ported from `dd-hackday-dynamic-config-system`) | Driven by the `retention-trigger` job. Reads `backup_metadata` snapshot via the facade; computes deletable checkpoint IDs by retention policy; emits `BM.DELETE` commands. |

### 4.2 Modified components

| File | Change |
|---|---|
| `SystemPartitionStep` (broker bootstrap) | Build `LogStream` → open `ZeebeDb` → start `StreamProcessor` instead of starting `SystemPartitionStateMachine`. Wire engine + cluster-config + backup processors. Submit `BackupOrchestrator` and `SystemPartitionBpmnAutoDeployer` actors on leader transition. |
| `SystemPartitionCfg` | Add `dataDirectory` (default `${dataDir}/system`) and a `rocksDb` block mirroring `BrokerCfg.experimental.rocksdb`. |
| `BrokerStartupContext{,Impl}` | Replace `getSystemPartition()` with the new facade signature. |
| `ConfigurationChangeCoordinatorImpl` | `applyOperations(req)` now stamps the change plan via a `CC.STAMP_CHANGE_PLAN` command on the system partition (instead of mutating local state then gossiping). Awaits `CC.CHANGE_PLAN_STAMPED` correlation. `isCoordinator()` continues to delegate to `systemPartition::isLeader`. |
| `ClusterConfigurationManagerImpl` (op-applied flow) | Two-phase write: after a local op-apply succeeds (e.g. join Raft data partition), submit a `CC.APPLY_OPERATION` command on the system partition; the existing `updateLocalConfiguration` is now triggered by the `CC.OPERATION_APPLIED` commit listener. |
| `BpmnConfigurationChangeJobWorker` (ported) | Reads the working configuration from BPMN process variables. After the local op-apply ack, submits `CC.APPLY_OPERATION` and completes the job once `CC.OPERATION_APPLIED` is observed. No longer writes a cluster variable. |
| `RedistributionCalculationJobWorker`, `ExporterCalculationJobWorker` (ported) | Read the current cluster configuration from `systemPartition.query()` for initial input; thereafter read the working configuration from BPMN process variables. No cluster-variable writes. |
| `ClusterVariableUpdateJobWorker` | **Deleted.** Replaced by first-class `ClusterConfigurationRecord` events. |
| `scale-operation.bpmn`, `exporter-operation.bpmn`, `modification_starter.bpmn` | Drop the `update-cluster-variable` task and the `=camunda.vars.cluster.clusterConfiguration` input expressions / output mappings. Add a terminal `commit-change` service task that emits `CC.COMPLETE_CHANGE`. |
| `CheckpointSchedulingService` | Move BPMN deployment + scheduler activation to the system-partition leader (auto-deploy via `SystemPartitionBpmnAutoDeployer` instead of via `CamundaClient`). |
| `CheckpointTriggerJobWorker` (ported) | Subscribes on the system partition's gateway. Submits a `BM.RECORD` command on the system partition; `BackupOrchestrator` performs the fan-out. |
| `RetentionTriggerJobWorker` (ported) | Subscribes on the system partition's gateway. Delegates to `BackupRetentionService` which emits `BM.DELETE` commands. |
| `BackupApiRequestHandler` | Forward "take backup" REST requests to the system-partition leader (which submits `BM.RECORD`) instead of writing a `CheckpointRecord` directly on a data partition. The data-partition `CheckpointRecord` path remains internal, originated by the orchestrator's fan-out. |
| `DynamicClusterConfigurationService` | Wires the refactored `SystemPartitionMirror`; removes any direct `update()` call sites. |

### 4.3 Removed components

- `SystemPartitionStateMachine` (replaced by the stream processor).
- `SystemPartitionRecord` (replaced by the first-class `ClusterConfigurationRecord` in the protocol module).
- The direct `SystemPartition.update(ClusterConfiguration)` API.
- `ClusterVariableUpdateJobWorker`.

---

## 5. Data flow

### 5.1 Cluster modification (scale-up example)

```
Operator → REST/CLI → broker N
                                 │
                                 ▼
   ConfigurationChangeCoordinatorImpl.applyOperations(req) on broker N:
     not leader → forward via BrokerClient to system-partition leader L.
                                 │
                                 ▼
   On L: validate request against current cluster configuration
        (read-only systemPartition.query()).
        Stamp the change plan: emit CC.STAMP_CHANGE_PLAN command
        with payload (operations[], requestId,
        expectedPreviousVersion).
                                 │
                                 ▼
   StreamProcessor: ClusterConfigurationStampProcessor
     • reject if state.activeChangePlan() != empty
     • CAS-check (expectedPreviousVersion == state.version())
     • on hit: write CC.CHANGE_PLAN_STAMPED event with new
       ClusterConfiguration carrying pendingOperations and
       version V0+1
                                 │
                                 ▼
   CC.CHANGE_PLAN_STAMPED commit → CommitListener →
     SystemPartitionMirror gossips configuration with the change
     plan downstream.
                                 │
                                 ▼
   ClusterConfigurationStampProcessor also emits a follow-up
   ProcessInstanceCreationIntent.CREATE command for
   modification_starter on the same stream, with process variables
   seeded from the new configuration. The engine processes the
   creation command on the next iteration, starting the BPMN.
                                 │
                                 ▼
   ┌─ Loop over pendingOperations ────────────────────────────────┐
   │                                                              │
   │  Service task: "apply next operation" (config-change-{mId})  │
   │    broker-side worker on target broker:                      │
   │      1. apply op locally (Join / Leave / Reconfigure / etc.) │
   │      2. wait for ack (Raft join confirmed, exporter          │
   │         enabled, etc.)                                       │
   │      3. on ack → submit CC.APPLY_OPERATION command on the    │
   │         system partition with                                │
   │         (operationId, expectedPreviousVersion)               │
   │      4. await CC.OPERATION_APPLIED correlation               │
   │      5. complete the job                                     │
   │                                                              │
   │  StreamProcessor: ClusterConfigurationApplyOperationProcessor│
   │    • CAS-check                                               │
   │    • on hit: reuse existing applier chain → newConfig with   │
   │      operation removed from pendingOperations, version       │
   │      bumped; emit CC.OPERATION_APPLIED                       │
   │    • on miss: emit CC.REJECT (worker retries via existing    │
   │      ExponentialBackoffRetryDelay)                           │
   │                                                              │
   │  CC.OPERATION_APPLIED commit on every replica:               │
   │    • StateApplier writes new cluster configuration to        │
   │      cluster_configuration column family                     │
   │    • CommitListener → in-memory snapshot, .topology.meta,    │
   │      gossip                                                  │
   │  ← BPMN advances to next operation                           │
   └──────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
   Plan empty: terminal service task → submit CC.COMPLETE_CHANGE
   command → ClusterConfigurationCompleteChangeProcessor moves
   the active plan to lastChange, bumps version → CC.CHANGE_COMPLETED.
```

The authoritative `ClusterConfiguration` is updated on every acknowledged Join/Leave/Reconfigure (and on every exporter enable/disable). `systemPartition.query()` reflects the cluster as observed: brokers that have completed their op are visible in the new state; brokers that haven't are visible in the old state. This matches today's gossip-based semantics, but durably with CAS, on every step, on the system-partition Raft log.

The BPMN process state itself lives in the engine's RocksDB on the system partition. A coordinator failover re-elects a new system-partition leader; the stream processor is already running on it as a follower, so all committed records (including the BPMN process state) are present. The new leader resumes the BPMN: in-flight job activations time out and re-fire on workers; operations are idempotent (Raft join, exporter toggle) so re-applying produces the same `CC.APPLY_OPERATION` and CAS catches duplicates.

### 5.2 Take backup

```
Operator → REST → BackupApiRequestHandler
                                 │
                                 ▼
   Routed to system-partition leader L: emit BM.RECORD command
   (checkpointId, status=PENDING, one row per data partition).
                                 │
                                 ▼
   StreamProcessor: BackupMetadataRecordProcessor
     emit BM.RECORDED → StateApplier writes rows to backup_metadata
     column family.
                                 │
                                 ▼
   BackupOrchestrator on L observes BM.RECORDED via CommitListener
   → fans out CheckpointRecord(CREATE, checkpointId) to each
   data-partition leader via BrokerClient.
                                 │
                                 ▼
   Each data partition: existing CheckpointRecordsProcessor takes
   per-partition snapshot, BackupService uploads to BackupStore,
   replies via BrokerClient.
                                 │
                                 ▼
   BackupOrchestrator: per reply, submits BM.RECORD command per
   partition (status=COMPLETED|FAILED).
                                 │
                                 ▼
   When all partitions COMPLETED: BackupOrchestrator emits
   CONFIRM_BACKUP fan-out; on all confirmations submits BM.RECORD
   with status=CONFIRMED.
```

The BPMN-driven `checkpoint-trigger` job worker reaches the same code path: the worker calls `BackupApi` "take backup" against the system-partition leader; everything else is identical.

### 5.3 Retention sweep

```
retention_scheduler BPMN (timer on system partition) triggers a
retention-trigger job on the gateway.
                                 │
                                 ▼
   Worker on any broker:
     1. systemPartition.query() current config + read backup_metadata
        snapshot via the facade.
     2. compute deletable checkpoint IDs by retention policy.
     3. submit BM.DELETE commands per id on the system partition.
                                 │
                                 ▼
   BackupOrchestrator fans out CheckpointRecord(DELETE_BACKUP) to
   data-partition leaders → existing per-partition delete path →
   reply → BM.DELETED on completion → state row removed.
```

### 5.4 CAS contention

Two writers race (e.g. concurrent op-applied submissions from two brokers, or an admin path racing the BPMN):

- Both submit `CC.APPLY_OPERATION` with `expectedPreviousVersion = V`.
- Raft serialises both into the log at indices `i` and `i+1`.
- Apply at `i`: CAS hits, version becomes `V+1`, `CC.OPERATION_APPLIED` written.
- Apply at `i+1`: CAS misses (state.version is now `V+1`, record.expectedPreviousVersion is `V`). Processor emits `CC.REJECT` with reason `CONCURRENT_MODIFICATION`. Loser observes the rejection (correlated by `requestId`), refetches the configuration, regenerates the command, retries.
- All replicas decide identically (deterministic).

### 5.5 Read path

`systemPartition.query()`:

- On a system-partition member: returns the in-memory snapshot updated by the `CommitListener`.
- On a non-member broker: returns the gossip-propagated configuration (same path as today).
- Linearizability for read-modify-write is provided by the subsequent `CC.APPLY_OPERATION` CAS, which catches stale reads.

---

## 6. Error handling & failure modes

| Case | Behaviour |
|---|---|
| **CAS conflict** | Processor emits `CC.REJECT` with reason code (`CONCURRENT_MODIFICATION`, `STALE_VERSION`, `INVALID_TRANSITION`). Job-worker callers retry via `ExponentialBackoffRetryDelay`; bounded retries → BPMN incident. Coordinator-stamping callers return rejection to REST. |
| **Stream-processor commit failure** | Standard engine machinery: `process()` throws → rejection record + incident on the originating command. Cluster-config processors have total `process()` paths; a throw indicates serialization corruption. |
| **Leader stepdown** | `StreamProcessor` cancels in-flight processing, replays from snapshot. Pending RPC `submitCommand` calls fail with `NotLeader`; the broker-side caller resolves the new leader via `RaftRoleChangeListener` and retries. `BackupOrchestrator` and `SystemPartitionBpmnAutoDeployer` stop and restart on the new leader; both are idempotent. |
| **Quorum loss on the system partition** | No new commits possible. All `submitCommand` paths fail with timeout / `NotLeader`. BPMNs in flight stall — jobs activate but cannot complete. Broker-local op-applied state may be ahead of the authoritative configuration; on quorum recovery the next `CC.APPLY_OPERATION` retry succeeds (idempotent local apply). DR escape hatch is out of scope for the MVP. |
| **Local op-apply failure on a broker** | Job worker's local apply throws → retryable BPMN job error → standard retry, then incident if exhausted. The cluster configuration stays at the last successfully-applied version with the pending operation still in `pendingOperations`. |
| **Backup partition snapshot failure** | Existing `CheckpointRecordsProcessor` rejection path → `BackupOrchestrator` receives the FAILED reply via `BrokerClient` → submits `BM.MARK_FAILED` → state row marked FAILED. BPMN-driven retry policy decides whether to re-attempt or abort. |
| **Retention DELETE failure on a partition** | `BM.MARK_FAILED` on the metadata row; the next retention sweep re-attempts. |
| **BPMN engine errors on the system partition** | Standard Camunda incident model. Incidents queryable via the system-partition gateway and resolvable via REST. |
| **Snapshot / replay** | `StreamProcessor` + `ZeebeDb` provide periodic snapshots and replication via the existing `PersistedSnapshotStore` plumbing. On a follower receiving a snapshot, RocksDB is restored and the in-memory `current` configuration is rehydrated from the `cluster_configuration` column family on the next commit-listener fire. |

### Ordering invariants

| Invariant | Enforced by |
|---|---|
| Local op-apply precedes cluster-configuration update | Job-worker code: `apply()` → `await ack` → `submit CC.APPLY_OPERATION` |
| All cluster-configuration mutations serialise | Single Raft log; `StreamProcessor` is single-threaded per partition |
| Change-plan operations apply in order | BPMN sequence flow — calculation jobs set the order, scale-operation loops over `pendingOperations` |
| `.topology.meta` lags but never leads | Persisted only via the `CC.OPERATION_APPLIED` (and `CC.CHANGE_PLAN_STAMPED` / `CC.CHANGE_COMPLETED`) commit listener — never written speculatively |
| Gossip never advertises uncommitted state | Same listener-driven path |

---

## 7. Testing — minimum smoke set

| Test | Scenario | Type |
|---|---|---|
| `SystemPartitionStreamProcessorIT` | Flag on → system partition starts → stream processor reaches ready state → cluster-management BPMNs auto-deployed → `query()` returns the initial configuration. | IT |
| `ScaleUpViaSystemPartitionIT` | One happy-path scale-up: request → BPMN runs → per-op `CC.OPERATION_APPLIED` commits → final `CC.CHANGE_COMPLETED` → topology consistent across brokers. | IT |
| `BackupViaSystemPartitionIT` | One happy-path backup: trigger → fan-out → all partitions confirm → backup retrievable from `BackupStore`. | IT |
| `ScaleUpBrokersTest` (existing, kept) | Flag off → legacy gossip-only path unchanged. | regression guard |

Adjust mocks in `ConfigurationChangeCoordinatorImplTest` for the `submitCommand` path. No additional unit tests are required beyond what is needed to debug during implementation. No CAS contention, failover, or chaos tests in the MVP.

### Verification command

```bash
./mvnw verify -pl zeebe/qa/integration-tests \
  -DskipUTs -DskipTests=false \
  -Dit.test='ScaleUpBrokersTest,ScaleUpViaSystemPartitionIT,BackupViaSystemPartitionIT,SystemPartitionStreamProcessorIT' \
  -Dquickly -T1C
```

---

## 8. Open questions / known gaps

- **Singleton enforcement.** The design relies on `ClusterConfigurationStampProcessor` rejecting a new stamp while an active plan exists. If the BPMN engine on the system partition also enforces a singleton-correlation-key on `modification_starter`, this is double-protection; either alone suffices.
- **Gateway exposure.** Cluster-management BPMNs need a gateway endpoint they can reach. The simplest option is to extend the existing broker gateway to route process-instance / job commands to the system partition when the target is a cluster-management process. The exact wiring (own gateway port vs. an internal route on the existing one) is left for the implementation plan.
- **Backup metadata schema.** `BackupMetadataRecord.descriptor` is opaque bytes in this design. The exact encoding (e.g. proto-encoded `BackupDescriptor`) is left to the implementation plan.
- **Auto-deploy idempotency.** `SystemPartitionBpmnAutoDeployer` re-runs on every leader transition. The existing engine deduplicates by `processId + version`, so this is safe; the implementation plan should still verify no incident is raised on re-deploy.

---

## 9. References

- Existing system-partition MVP design: `system-partition-design.md`
- Detailed proposal: `system-partition-proposal.md`
- Current MVP commit: `0d931c7fd14` on `dd-hackday-system-partition`
- BPMN-orchestrated cluster operations: `origin/dd-hackday-dynamic-config-system`
- Stream platform: `zeebe/stream-platform/`, `zeebe/logstreams/`
- Engine processors: `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/EngineProcessors.java:101`
- Backup flow today: `zeebe/backup/`, `BackupApiRequestHandler`, `CheckpointRecordsProcessor`
