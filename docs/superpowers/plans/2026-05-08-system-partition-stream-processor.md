# System Partition Stream Processor — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert the system partition from a hand-rolled state machine to a full Stream Processor that hosts the Camunda engine, models `ClusterConfiguration` as a first-class record, executes BPMN-driven cluster modifications via per-operation `CC.OPERATION_APPLIED` commits, and runs the backup control plane.

**Architecture:** A `StreamProcessor` is mounted on a `LogStream` over the existing system-partition `RaftPartition`. The processor chain is `EngineProcessors` (reused as-is) plus two new groups — `ClusterConfigurationProcessors` and `BackupControlPlaneProcessors`. Cluster-management BPMNs (scale, exporter, modification, checkpoint, retention) auto-deploy on the system partition; broker-side job workers (ported from `origin/dd-hackday-dynamic-config-system`) drive each operation.

**Tech Stack:** Java 21, Spring Boot, Atomix Raft, Zeebe `stream-platform` + `logstreams` + `engine`, ZeebeDb (RocksDB), msgpack/SBE protocol, JUnit 5 + AssertJ, Awaitility, Camunda Java client.

**Spec:** `docs/superpowers/specs/2026-05-08-system-partition-stream-processor-design.md`

**Testing posture:** Per spec §7, the MVP relies on a minimum smoke set of 3 ITs at the end of the plan. Most tasks deliberately do NOT include unit tests — the user requested minimum tests. Run the smoke ITs after each phase to catch regressions early.

**Branch posture:**
- Working branch: `dd-hackday-system-partition`.
- The BPMN workers, BPMN files, and `CheckpointSchedulingService` updates live on `origin/dd-hackday-dynamic-config-system`. When a task says "port from `origin/dd-hackday-dynamic-config-system`", check out the file with `git show origin/dd-hackday-dynamic-config-system:<path>` and apply it on this branch with the modifications described in the task.

---

## File Structure

### New files (created in this plan)

| Path | Responsibility |
|---|---|
| `zeebe/protocol/src/main/resources/protocol.xml` (edit) | Register two new `ValueType` ids: `CLUSTER_CONFIGURATION=70`, `BACKUP_METADATA=71`. |
| `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/ClusterConfigurationIntent.java` | New intent enum: STAMP_CHANGE_PLAN, CHANGE_PLAN_STAMPED, APPLY_OPERATION, OPERATION_APPLIED, COMPLETE_CHANGE, CHANGE_COMPLETED, REJECT. |
| `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/BackupMetadataIntent.java` | New intent enum: RECORD, RECORDED, MARK_FAILED, MARKED_FAILED, DELETE, DELETED. |
| `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/clusterconfiguration/ClusterConfigurationRecord.java` | Msgpack POJO carrying `expectedPreviousVersion: long`, `requestId: String`, `configuration: byte[]` (proto-encoded `ClusterConfiguration`), and an optional `appliedOperation: byte[]` for OPERATION_APPLIED events. |
| `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/backupmetadata/BackupMetadataRecord.java` | Msgpack POJO: `checkpointId: long`, `partitionId: int`, `status: enum`, `descriptor: byte[]`. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionLogStream.java` | `LogStream` impl over the system-partition `RaftPartition` (uses the `AtomixLogStorage` pattern). |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionStreamProcessorFactory.java` | Builds the stream processor with the engine + cluster-config + backup processor sets. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionFacadeImpl.java` | Implements the refactored `SystemPartition` interface (query + listeners + submitCommand). |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationProcessors.java` | Static factory wiring the three processors below into `TypedRecordProcessors`. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationStampProcessor.java` | Stamps a `ClusterChangePlan`. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationApplyOperationProcessor.java` | Applies one `ClusterConfigurationChangeOperation`, bumps version. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationCompleteChangeProcessor.java` | Moves active plan to `lastChange`. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationStateApplier.java` | Deterministic event applier; writes to `cluster_configuration` column family. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/state/ClusterConfigurationState.java` | RocksDB-backed state interface + DbClusterConfigurationState impl. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/BackupControlPlaneProcessors.java` | Static factory wiring the backup metadata processors. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/BackupMetadataRecordProcessor.java` | Processes RECORD/MARK_FAILED/DELETE commands, emits *ED events. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/BackupMetadataStateApplier.java` | Applies events to `backup_metadata` column family. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/state/BackupMetadataState.java` | RocksDB column family + DbBackupMetadataState impl. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/backup/BackupOrchestrator.java` | Actor on the leader; fans `CheckpointRecord` out to data partitions via `BrokerClient`. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionBpmnAutoDeployer.java` | Deploys cluster-mgmt BPMNs on leader transition. |
| `zeebe/qa/integration-tests/src/test/java/io/camunda/it/SystemPartitionStreamProcessorIT.java` | Smoke IT 1. |
| `zeebe/qa/integration-tests/src/test/java/io/camunda/it/ScaleUpViaSystemPartitionIT.java` | Smoke IT 2. |
| `zeebe/qa/integration-tests/src/test/java/io/camunda/it/BackupViaSystemPartitionIT.java` | Smoke IT 3. |

### Modified files

| Path | Change |
|---|---|
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartition.java` | Drop `update()`; add `query()`, `addLeaderListener`, `addClusterConfigListener`, `submitCommand`. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionFactory.java` | Hand off to `SystemPartitionStreamProcessorFactory` after `RaftPartition` bootstrap. |
| `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionMirror.java` | Subscribe to `CC.OPERATION_APPLIED` / `CC.CHANGE_PLAN_STAMPED` / `CC.CHANGE_COMPLETED` commit listener instead of `RaftCommitListener`. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/bootstrap/SystemPartitionStep.java` | Build `LogStream`, open `ZeebeDb`, start `StreamProcessor` instead of `SystemPartitionStateMachine`. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/configuration/SystemPartitionCfg.java` | Add `dataDirectory` + `rocksDb` block. |
| `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/changes/ConfigurationChangeCoordinatorImpl.java` | `applyOperations` now stamps via `submitCommand(STAMP_CHANGE_PLAN, …)` on the system partition. |
| `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ClusterConfigurationManagerImpl.java` | Op-applied flow: after local apply ack, submit `APPLY_OPERATION`. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/transport/backupapi/BackupApiRequestHandler.java` | Forward TAKE_BACKUP to system-partition leader (submit `BM.RECORD`); same for LIST/DELETE. |
| `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/management/CheckpointSchedulingService.java` | BPMN deployment moved to `SystemPartitionBpmnAutoDeployer`. |
| `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/BpmnConfigurationChangeJobWorker.java` (ported) | Drop ClusterVariable read/write; submit `CC.APPLY_OPERATION` after local op ack. |
| `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/RedistributionCalculationJobWorker.java` (ported) | Read initial config via `systemPartition.query()`. |
| `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ExporterCalculationJobWorker.java` (ported) | Read initial config via `systemPartition.query()`. |
| `zeebe/dynamic-config/src/main/resources/bpmn/scale-operation.bpmn` (ported) | Remove `update-cluster-variable` task and ClusterVariable I/O mappings; add terminal `commit-change` task that emits `CC.COMPLETE_CHANGE`. |
| `zeebe/dynamic-config/src/main/resources/bpmn/exporter-operation.bpmn` (ported) | Same. |
| `zeebe/dynamic-config/src/main/resources/bpmn/modification_starter.bpmn` (ported) | Drop ClusterVariable input expression; receive config snapshot from initial process variables. |
| `zeebe/backup/src/main/java/io/camunda/zeebe/backup/schedule/CheckpointTriggerJobWorker.java` (ported) | Subscribes on system-partition gateway; submits `BM.RECORD`. |
| `zeebe/backup/src/main/java/io/camunda/zeebe/backup/schedule/RetentionTriggerJobWorker.java` (ported) | Subscribes on system-partition gateway; submits `BM.DELETE` per retention policy. |

### Removed files

- `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionStateMachine.java`
- `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionRecord.java`
- `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ClusterVariableUpdateJobWorker.java` (do NOT port from the other branch)

---

## Phase 1 — Protocol & state foundations

### Task 1: Register new SBE ValueTypes

**Files:**
- Modify: `zeebe/protocol/src/main/resources/protocol.xml:80-81` (insert after `RESOURCE_REEXPORT`)

- [ ] **Step 1: Add the two new validValues**

In `protocol.xml`, locate the `<enum name="valueType" …>` block (the one containing `RESOURCE_REEXPORT`). After the `RESOURCE_REEXPORT` line, insert:

```xml
            <validValue name="CLUSTER_CONFIGURATION">70</validValue>
            <validValue name="BACKUP_METADATA">71</validValue>
```

- [ ] **Step 2: Regenerate SBE classes**

Run: `./mvnw install -pl zeebe/protocol -Dquickly -T1C`
Expected: BUILD SUCCESS. Confirm `zeebe/protocol/target/generated-sources/sbe/io/camunda/zeebe/protocol/record/ValueType.java` now contains `CLUSTER_CONFIGURATION` and `BACKUP_METADATA`.

- [ ] **Step 3: Commit**

```bash
git add zeebe/protocol/src/main/resources/protocol.xml
git commit -m "feat: add CLUSTER_CONFIGURATION and BACKUP_METADATA value types"
```

### Task 2: ClusterConfigurationIntent enum

**Files:**
- Create: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/ClusterConfigurationIntent.java`

- [ ] **Step 1: Create the intent enum**

Model on `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/ClusterVariableIntent.java`. Content:

```java
package io.camunda.zeebe.protocol.record.intent;

public enum ClusterConfigurationIntent implements Intent {
  STAMP_CHANGE_PLAN((short) 0),
  CHANGE_PLAN_STAMPED((short) 1),
  APPLY_OPERATION((short) 2),
  OPERATION_APPLIED((short) 3),
  COMPLETE_CHANGE((short) 4),
  CHANGE_COMPLETED((short) 5),
  REJECT((short) 6);

  private final short value;

  ClusterConfigurationIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return switch (this) {
      case CHANGE_PLAN_STAMPED, OPERATION_APPLIED, CHANGE_COMPLETED, REJECT -> true;
      default -> false;
    };
  }

  public static Intent from(final short value) {
    return switch (value) {
      case 0 -> STAMP_CHANGE_PLAN;
      case 1 -> CHANGE_PLAN_STAMPED;
      case 2 -> APPLY_OPERATION;
      case 3 -> OPERATION_APPLIED;
      case 4 -> COMPLETE_CHANGE;
      case 5 -> CHANGE_COMPLETED;
      case 6 -> REJECT;
      default -> Intent.UNKNOWN;
    };
  }
}
```

- [ ] **Step 2: Wire into the Intent dispatcher**

Open `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/Intent.java`. Locate the `fromProtocolValue(ValueType, short)` switch. Add a branch:

```java
case CLUSTER_CONFIGURATION -> ClusterConfigurationIntent.from(intent);
```

- [ ] **Step 3: Build the protocol module**

Run: `./mvnw install -pl zeebe/protocol -Dquickly -T1C`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/ClusterConfigurationIntent.java \
        zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/Intent.java
git commit -m "feat: add ClusterConfigurationIntent enum"
```

### Task 3: BackupMetadataIntent enum

**Files:**
- Create: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/BackupMetadataIntent.java`

- [ ] **Step 1: Create the enum**

```java
package io.camunda.zeebe.protocol.record.intent;

public enum BackupMetadataIntent implements Intent {
  RECORD((short) 0),
  RECORDED((short) 1),
  MARK_FAILED((short) 2),
  MARKED_FAILED((short) 3),
  DELETE((short) 4),
  DELETED((short) 5);

  private final short value;

  BackupMetadataIntent(final short value) {
    this.value = value;
  }

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return switch (this) {
      case RECORDED, MARKED_FAILED, DELETED -> true;
      default -> false;
    };
  }

  public static Intent from(final short value) {
    return switch (value) {
      case 0 -> RECORD;
      case 1 -> RECORDED;
      case 2 -> MARK_FAILED;
      case 3 -> MARKED_FAILED;
      case 4 -> DELETE;
      case 5 -> DELETED;
      default -> Intent.UNKNOWN;
    };
  }
}
```

- [ ] **Step 2: Wire into Intent dispatcher**

Add `case BACKUP_METADATA -> BackupMetadataIntent.from(intent);` next to the previous addition in `Intent.java`.

- [ ] **Step 3: Build & commit**

```bash
./mvnw install -pl zeebe/protocol -Dquickly -T1C
git add zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/BackupMetadataIntent.java \
        zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/Intent.java
git commit -m "feat: add BackupMetadataIntent enum"
```

### Task 4: ClusterConfigurationRecord POJO

**Files:**
- Create: `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/clusterconfiguration/ClusterConfigurationRecord.java`

- [ ] **Step 1: Implement the POJO**

Model on `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/clustervariable/ClusterVariableRecord.java`. Use `UnifiedRecordValue` base + `BinaryProperty` for the encoded configuration. Content:

```java
package io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ClusterConfigurationRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ClusterConfigurationRecord extends UnifiedRecordValue
    implements ClusterConfigurationRecordValue {

  private final LongProperty expectedPreviousVersionProp =
      new LongProperty("expectedPreviousVersion", -1L);
  private final StringProperty requestIdProp = new StringProperty("requestId", "");
  private final BinaryProperty configurationProp =
      new BinaryProperty("configuration", new UnsafeBuffer(new byte[0]));
  private final BinaryProperty appliedOperationProp =
      new BinaryProperty("appliedOperation", new UnsafeBuffer(new byte[0]));
  private final StringProperty rejectionReasonProp = new StringProperty("rejectionReason", "");

  public ClusterConfigurationRecord() {
    super(5);
    declareProperty(expectedPreviousVersionProp)
        .declareProperty(requestIdProp)
        .declareProperty(configurationProp)
        .declareProperty(appliedOperationProp)
        .declareProperty(rejectionReasonProp);
  }

  @Override
  public long getExpectedPreviousVersion() {
    return expectedPreviousVersionProp.getValue();
  }

  public ClusterConfigurationRecord setExpectedPreviousVersion(final long v) {
    expectedPreviousVersionProp.setValue(v);
    return this;
  }

  @Override
  public String getRequestId() {
    return BufferUtil.bufferAsString(requestIdProp.getValue());
  }

  public ClusterConfigurationRecord setRequestId(final String id) {
    requestIdProp.setValue(id);
    return this;
  }

  @Override
  public byte[] getConfiguration() {
    return BufferUtil.bufferAsArray(configurationProp.getValue());
  }

  public ClusterConfigurationRecord setConfiguration(final byte[] bytes) {
    configurationProp.setValue(new UnsafeBuffer(bytes));
    return this;
  }

  @Override
  public byte[] getAppliedOperation() {
    return BufferUtil.bufferAsArray(appliedOperationProp.getValue());
  }

  public ClusterConfigurationRecord setAppliedOperation(final byte[] bytes) {
    appliedOperationProp.setValue(new UnsafeBuffer(bytes));
    return this;
  }

  @Override
  public String getRejectionReason() {
    return BufferUtil.bufferAsString(rejectionReasonProp.getValue());
  }

  public ClusterConfigurationRecord setRejectionReason(final String r) {
    rejectionReasonProp.setValue(r);
    return this;
  }
}
```

- [ ] **Step 2: Add the value-type interface**

Create `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/ClusterConfigurationRecordValue.java`:

```java
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.RecordValue;

public interface ClusterConfigurationRecordValue extends RecordValue {
  long getExpectedPreviousVersion();

  String getRequestId();

  byte[] getConfiguration();

  byte[] getAppliedOperation();

  String getRejectionReason();
}
```

- [ ] **Step 3: Register in ValueType→class map**

Open `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/RecordType.java` (or the existing dispatch class — search for where `CLUSTER_VARIABLE` is registered to find the file). Add a branch returning `new ClusterConfigurationRecord()` for `CLUSTER_CONFIGURATION`.

- [ ] **Step 4: Build**

Run: `./mvnw install -pl zeebe/protocol-impl -am -Dquickly -T1C`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/clusterconfiguration/ \
        zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/ClusterConfigurationRecordValue.java \
        zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/RecordType.java
git commit -m "feat: add ClusterConfigurationRecord value type"
```

### Task 5: BackupMetadataRecord POJO

**Files:**
- Create: `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/backupmetadata/BackupMetadataRecord.java`
- Create: `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/BackupMetadataRecordValue.java`

- [ ] **Step 1: Create the value-type interface**

```java
package io.camunda.zeebe.protocol.record.value;

import io.camunda.zeebe.protocol.record.RecordValue;

public interface BackupMetadataRecordValue extends RecordValue {
  long getCheckpointId();

  int getPartitionId();

  String getStatus(); // PENDING | COMPLETED | FAILED | CONFIRMED

  byte[] getDescriptor();

  String getFailureReason();
}
```

- [ ] **Step 2: Create the POJO**

Use the same property pattern as Task 4. Add `LongProperty` for `checkpointId`, `IntegerProperty` for `partitionId`, `StringProperty` for `status` and `failureReason`, `BinaryProperty` for `descriptor`. Implement getters/setters.

- [ ] **Step 3: Register in the value-type→class dispatch**

Same file as Task 4 step 3. Add a branch for `BACKUP_METADATA`.

- [ ] **Step 4: Build & commit**

```bash
./mvnw install -pl zeebe/protocol-impl -am -Dquickly -T1C
git add zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/backupmetadata/ \
        zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/BackupMetadataRecordValue.java \
        zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/RecordType.java
git commit -m "feat: add BackupMetadataRecord value type"
```

### Task 6: Add column-family enum entries for cluster_configuration and backup_metadata

**Files:**
- Modify: `zeebe/zb-db/src/main/java/io/camunda/zeebe/protocol/ZbColumnFamilies.java`

- [ ] **Step 1: Append two new column families**

Open `ZbColumnFamilies.java`. At the end of the enum (just before the close), add:

```java
  CLUSTER_CONFIGURATION,
  BACKUP_METADATA,
```

(Each enum value implicitly assigned the next ordinal; if the file uses explicit ordering, follow that pattern.)

- [ ] **Step 2: Build**

Run: `./mvnw install -pl zeebe/zb-db -am -Dquickly -T1C`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add zeebe/zb-db/src/main/java/io/camunda/zeebe/protocol/ZbColumnFamilies.java
git commit -m "feat: add cluster_configuration and backup_metadata column families"
```

---

## Phase 2 — System partition stream-platform wiring

### Task 7: Refactor SystemPartition interface

**Files:**
- Modify: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartition.java`

- [ ] **Step 1: Replace the interface contents**

```java
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.Consumer;

public interface SystemPartition {
  /** Returns the latest committed ClusterConfiguration. Always non-null after first commit. */
  ClusterConfiguration query();

  /** Returns true on the broker that is currently the system-partition Raft leader. */
  boolean isLeader();

  /** Notifies the listener on every leader-role change (true = became leader). */
  void addLeaderListener(Consumer<Boolean> listener);

  /** Notifies the listener on every applied CC.* event with the new ClusterConfiguration. */
  void addClusterConfigListener(Consumer<ClusterConfiguration> listener);

  /**
   * Submits a cluster-configuration command on the system partition.
   * On a non-leader broker, forwards via BrokerClient to the leader.
   * Future completes when the corresponding CC.*_APPLIED / *_STAMPED / REJECT event commits.
   */
  ActorFuture<ClusterConfigurationRecord> submitCommand(
      ClusterConfigurationIntent intent, ClusterConfigurationRecord record);

  class NotLeaderException extends RuntimeException {
    public NotLeaderException(final String msg) { super(msg); }
  }

  class ConcurrentModificationException extends RuntimeException {
    public ConcurrentModificationException(final String msg) { super(msg); }
  }
}
```

- [ ] **Step 2: Build to find compile errors**

Run: `./mvnw install -pl zeebe/system-partition -am -Dquickly -T1C`
Expected: BUILD FAILURE — multiple call sites depend on the removed `update()` method. Note them; later tasks fix them.

- [ ] **Step 3: Commit (deferred — interface compiles standalone but downstream not yet)**

Defer the commit to Task 13 once the downstream compiles again.

### Task 8: SystemPartitionLogStream — LogStream over Raft

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionLogStream.java`

**Pattern:** Data partitions wire `LogStream.builder()` with an `AtomixLogStorage` over a `RaftPartitionServer` in `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/partitions/impl/steps/LogStreamPartitionTransitionStep.java`. Reuse the same composition.

- [ ] **Step 1: Create the builder helper**

```java
package io.camunda.zeebe.systempartition;

import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamBuilder;
import io.camunda.zeebe.logstreams.storage.atomix.AtomixLogStorage;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;

public final class SystemPartitionLogStream {

  private SystemPartitionLogStream() {}

  /**
   * Build a LogStream backed by the system-partition Raft server.
   * Mirrors the data-partition path in LogStreamPartitionTransitionStep but without
   * the data-partition-specific wiring (FlowControl, exporter director, etc.).
   */
  public static ActorFuture<LogStream> build(
      final RaftPartition raftPartition,
      final ActorSchedulingService scheduler,
      final MeterRegistry meterRegistry) {

    final var server = raftPartition.getServer();
    final var storage = AtomixLogStorage.ofPartition(server::openReader, server.getAppender().orElseThrow());

    return LogStream.builder()
        .withPartitionId(raftPartition.id().id())
        .withMaxFragmentSize(server.getMaxFragmentSize())
        .withLogStorage(storage)
        .withActorSchedulingService(scheduler)
        .withMeterRegistry(meterRegistry)
        .withLogName("system-1")
        .buildAsync();
  }
}
```

(Field/method names — `getMaxFragmentSize`, `openReader`, `getAppender` — should already exist on `RaftPartitionServer`. If signatures differ, follow the data-partition step closely; do NOT invent.)

- [ ] **Step 2: Build**

Run: `./mvnw install -pl zeebe/system-partition -am -Dquickly -T1C`
Expected: any errors are missing imports or signature mismatches with `RaftPartitionServer`. Adjust to match the actual API.

- [ ] **Step 3: Commit (deferred — compiles standalone). Defer to Task 13.**

### Task 9: ClusterConfigurationState (RocksDB)

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/state/ClusterConfigurationState.java`

**Pattern:** `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/clustervariable/DbClusterVariableState.java` shows the column-family + DbValue idiom.

- [ ] **Step 1: Create the state interface and impl**

```java
package io.camunda.zeebe.systempartition.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public final class ClusterConfigurationState {

  private static final String CURRENT_KEY = "current";

  private final ColumnFamily<DbString, DbBytes> column;
  private final DbString key = new DbString();
  private final DbBytes value = new DbBytes();
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  private ClusterConfiguration cached = ClusterConfiguration.uninitialized();

  public ClusterConfigurationState(final ZeebeDb<ZbColumnFamilies> db, final TransactionContext ctx) {
    column = db.createColumnFamily(ZbColumnFamilies.CLUSTER_CONFIGURATION, ctx, key, value);
    key.wrapString(CURRENT_KEY);
    final DbBytes existing = column.get(key);
    if (existing != null) {
      cached = serializer.decode(existing.toBytes());
    }
  }

  public ClusterConfiguration get() {
    return cached;
  }

  public void put(final ClusterConfiguration config) {
    key.wrapString(CURRENT_KEY);
    value.wrapBytes(serializer.encode(config));
    column.upsert(key, value);
    cached = config;
  }
}
```

(`DbBytes` may already exist; if not, `DbBuffer` or a small wrapper is fine. Follow existing patterns in `zeebe/zb-db/src/main/java/io/camunda/zeebe/db/impl/`.)

- [ ] **Step 2: Build**

Run: `./mvnw install -pl zeebe/system-partition -am -Dquickly -T1C`
Expected: any errors → adjust import paths.

- [ ] **Step 3: Commit (deferred — compiles standalone). Defer to Task 13.**

### Task 10: ClusterConfigurationStateApplier

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationStateApplier.java`

- [ ] **Step 1: Implement the applier**

```java
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.engine.state.appliers.TypedEventApplier;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

public final class ClusterConfigurationStateApplier
    implements TypedEventApplier<ClusterConfigurationIntent, ClusterConfigurationRecord> {

  private final ClusterConfigurationState state;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  public ClusterConfigurationStateApplier(final ClusterConfigurationState state) {
    this.state = state;
  }

  @Override
  public void applyState(final long key, final ClusterConfigurationRecord record) {
    final byte[] encoded = record.getConfiguration();
    if (encoded.length == 0) {
      return; // REJECT events carry no configuration
    }
    state.put(serializer.decode(encoded));
  }
}
```

- [ ] **Step 2: Defer commit to Task 13.**

### Task 11: ClusterConfigurationStampProcessor

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationStampProcessor.java`

**Reference:** Look at `zeebe/engine/src/main/java/io/camunda/zeebe/engine/processing/clustervariable/ClusterVariableUpdateProcessor.java` for the `TypedRecordProcessor` shape.

- [ ] **Step 1: Implement**

```java
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.impl.processing.TypedRecordProcessor;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

public final class ClusterConfigurationStampProcessor
    implements TypedRecordProcessor<ClusterConfigurationRecord> {

  private final ClusterConfigurationState state;
  private final Writers writers;
  private final KeyGenerator keys;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  public ClusterConfigurationStampProcessor(
      final ClusterConfigurationState state, final Writers writers, final KeyGenerator keys) {
    this.state = state;
    this.writers = writers;
    this.keys = keys;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterConfigurationRecord> command) {
    final ClusterConfigurationRecord record = command.getValue();
    final ClusterConfiguration current = state.get();

    if (current.hasPendingChanges()) {
      writers.rejection().appendRejection(command, RejectionType.INVALID_STATE,
          "Cannot stamp change plan: another plan is active");
      return;
    }

    if (record.getExpectedPreviousVersion() != current.version()) {
      final ClusterConfigurationRecord rejected = new ClusterConfigurationRecord()
          .setRequestId(record.getRequestId())
          .setRejectionReason("CAS miss: expected="
              + record.getExpectedPreviousVersion() + " actual=" + current.version());
      writers.state().appendFollowUpEvent(keys.nextKey(),
          ClusterConfigurationIntent.REJECT, rejected);
      return;
    }

    // Decode the proposed plan, install on the configuration, bump version.
    final ClusterConfiguration proposed = serializer.decode(record.getConfiguration());
    final ClusterConfiguration next = proposed.merge(current).advanceVersion();

    final ClusterConfigurationRecord stamped = new ClusterConfigurationRecord()
        .setRequestId(record.getRequestId())
        .setExpectedPreviousVersion(current.version())
        .setConfiguration(serializer.encode(next));

    writers.state().appendFollowUpEvent(keys.nextKey(),
        ClusterConfigurationIntent.CHANGE_PLAN_STAMPED, stamped);

    // Auto-start the modification BPMN by emitting a ProcessInstanceCreation command.
    BpmnInstanceStarter.requestStart(writers, keys, "modification_starter", next);
  }
}
```

(`BpmnInstanceStarter` is a small helper added in Task 14. `ClusterConfiguration.hasPendingChanges()`, `merge`, `advanceVersion` already exist on the existing record.)

- [ ] **Step 2: Defer commit to Task 13.**

### Task 12: ClusterConfigurationApplyOperationProcessor + CompleteChangeProcessor

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationApplyOperationProcessor.java`
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationCompleteChangeProcessor.java`

- [ ] **Step 1: ApplyOperation processor**

```java
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.impl.processing.TypedRecordProcessor;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

public final class ClusterConfigurationApplyOperationProcessor
    implements TypedRecordProcessor<ClusterConfigurationRecord> {

  private final ClusterConfigurationState state;
  private final Writers writers;
  private final KeyGenerator keys;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();
  private final ConfigurationChangeAppliers appliers;

  public ClusterConfigurationApplyOperationProcessor(
      final ClusterConfigurationState state,
      final Writers writers,
      final KeyGenerator keys,
      final ConfigurationChangeAppliers appliers) {
    this.state = state;
    this.writers = writers;
    this.keys = keys;
    this.appliers = appliers;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterConfigurationRecord> command) {
    final ClusterConfigurationRecord cmd = command.getValue();
    final ClusterConfiguration current = state.get();

    if (cmd.getExpectedPreviousVersion() != current.version()) {
      final ClusterConfigurationRecord rejected = new ClusterConfigurationRecord()
          .setRequestId(cmd.getRequestId())
          .setRejectionReason("CAS miss: expected="
              + cmd.getExpectedPreviousVersion() + " actual=" + current.version());
      writers.state().appendFollowUpEvent(keys.nextKey(),
          ClusterConfigurationIntent.REJECT, rejected);
      return;
    }

    final ClusterConfigurationChangeOperation op =
        serializer.decodeOperation(cmd.getAppliedOperation());

    final ClusterConfiguration next = appliers.apply(op, current).advanceVersion();

    final ClusterConfigurationRecord applied = new ClusterConfigurationRecord()
        .setRequestId(cmd.getRequestId())
        .setExpectedPreviousVersion(current.version())
        .setConfiguration(serializer.encode(next))
        .setAppliedOperation(cmd.getAppliedOperation());

    writers.state().appendFollowUpEvent(keys.nextKey(),
        ClusterConfigurationIntent.OPERATION_APPLIED, applied);
  }
}
```

(`ProtoBufSerializer.decodeOperation` already exists in `zeebe/dynamic-config/`; if not, add a small helper there.)

- [ ] **Step 2: CompleteChange processor**

```java
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.stream.impl.processing.TypedRecordProcessor;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

public final class ClusterConfigurationCompleteChangeProcessor
    implements TypedRecordProcessor<ClusterConfigurationRecord> {

  private final ClusterConfigurationState state;
  private final Writers writers;
  private final KeyGenerator keys;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  public ClusterConfigurationCompleteChangeProcessor(
      final ClusterConfigurationState state, final Writers writers, final KeyGenerator keys) {
    this.state = state;
    this.writers = writers;
    this.keys = keys;
  }

  @Override
  public void processRecord(final TypedRecord<ClusterConfigurationRecord> command) {
    final ClusterConfiguration current = state.get();
    final ClusterConfiguration completed = current.completeChange().advanceVersion();
    final ClusterConfigurationRecord event = new ClusterConfigurationRecord()
        .setRequestId(command.getValue().getRequestId())
        .setExpectedPreviousVersion(current.version())
        .setConfiguration(serializer.encode(completed));
    writers.state().appendFollowUpEvent(keys.nextKey(),
        ClusterConfigurationIntent.CHANGE_COMPLETED, event);
  }
}
```

- [ ] **Step 3: Defer commit to Task 13.**

### Task 13: ClusterConfigurationProcessors factory + SystemPartitionStreamProcessorFactory

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/ClusterConfigurationProcessors.java`
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/BpmnInstanceStarter.java` (small helper)
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionStreamProcessorFactory.java`

- [ ] **Step 1: BpmnInstanceStarter helper**

```java
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import org.agrona.io.DirectBufferOutputStream;
import org.msgpack.core.MessagePack;

public final class BpmnInstanceStarter {

  private BpmnInstanceStarter() {}

  public static void requestStart(
      final Writers writers,
      final KeyGenerator keys,
      final String bpmnProcessId,
      final ClusterConfiguration initialConfig) {
    final ProcessInstanceCreationRecord create = new ProcessInstanceCreationRecord();
    create.setBpmnProcessId(bpmnProcessId);
    create.setVariables(packInitialVariables(initialConfig));
    writers.command().appendNewCommand(ProcessInstanceCreationIntent.CREATE, create);
  }

  private static org.agrona.DirectBuffer packInitialVariables(final ClusterConfiguration cfg) {
    // msgpack-encoded {"clusterConfiguration": <proto-encoded bytes base64>}
    final ProtoBufSerializer serializer = new ProtoBufSerializer();
    final byte[] proto = serializer.encode(cfg);
    final var buffer = new org.agrona.concurrent.UnsafeBuffer(new byte[1024 + proto.length]);
    final var out = new DirectBufferOutputStream(buffer);
    try (final var packer = MessagePack.newDefaultPacker(out)) {
      packer.packMapHeader(1);
      packer.packString("clusterConfiguration");
      packer.packBinaryHeader(proto.length);
      packer.writePayload(proto);
    } catch (final java.io.IOException e) {
      throw new RuntimeException(e);
    }
    return new org.agrona.concurrent.UnsafeBuffer(buffer, 0, (int) out.position());
  }
}
```

- [ ] **Step 2: ClusterConfigurationProcessors factory**

```java
package io.camunda.zeebe.systempartition.processors;

import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;

public final class ClusterConfigurationProcessors {

  private ClusterConfigurationProcessors() {}

  public static void register(
      final TypedRecordProcessors processors,
      final ClusterConfigurationState state,
      final KeyGenerator keys,
      final ConfigurationChangeAppliers appliers) {
    final var writers = processors.getWriters();
    processors.onCommand(ValueType.CLUSTER_CONFIGURATION,
        ClusterConfigurationIntent.STAMP_CHANGE_PLAN,
        new ClusterConfigurationStampProcessor(state, writers, keys));
    processors.onCommand(ValueType.CLUSTER_CONFIGURATION,
        ClusterConfigurationIntent.APPLY_OPERATION,
        new ClusterConfigurationApplyOperationProcessor(state, writers, keys, appliers));
    processors.onCommand(ValueType.CLUSTER_CONFIGURATION,
        ClusterConfigurationIntent.COMPLETE_CHANGE,
        new ClusterConfigurationCompleteChangeProcessor(state, writers, keys));
    final var applier = new ClusterConfigurationStateApplier(state);
    processors.withListener(ClusterConfigurationIntent.CHANGE_PLAN_STAMPED, applier);
    processors.withListener(ClusterConfigurationIntent.OPERATION_APPLIED, applier);
    processors.withListener(ClusterConfigurationIntent.CHANGE_COMPLETED, applier);
  }
}
```

(If the local `TypedRecordProcessors` API uses different method names — `onCommand`, `withListener` — adjust to match what the engine uses in `EngineProcessors.createEngineProcessors`. Don't invent.)

- [ ] **Step 3: SystemPartitionStreamProcessorFactory**

```java
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliers;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessorContext;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessorBuilder;
import io.camunda.zeebe.systempartition.processors.BackupControlPlaneProcessors;
import io.camunda.zeebe.systempartition.processors.ClusterConfigurationProcessors;
import io.camunda.zeebe.systempartition.state.BackupMetadataState;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;
import io.micrometer.core.instrument.MeterRegistry;

public final class SystemPartitionStreamProcessorFactory {

  private SystemPartitionStreamProcessorFactory() {}

  public static StreamProcessor build(
      final LogStream logStream,
      final ZeebeDb<ZbColumnFamilies> db,
      final ActorSchedulingService scheduler,
      final ConfigurationChangeAppliers appliers,
      final MeterRegistry meterRegistry) {

    return new StreamProcessorBuilder()
        .logStream(logStream)
        .zeebeDb(db)
        .actorSchedulingService(scheduler)
        .meterRegistry(meterRegistry)
        .recordProcessorsFactory(ctx -> {
          final TypedRecordProcessors processors = TypedRecordProcessors.processors();
          // Engine processors (deployment, process, job, variable, incident, timer…)
          EngineProcessors.createEngineProcessors(ctx, processors);
          // Cluster configuration processors
          final var ccState = new ClusterConfigurationState(db, ctx.getTransactionContext());
          ClusterConfigurationProcessors.register(processors, ccState, ctx.getKeyGenerator(), appliers);
          // Backup control plane processors
          final var bmState = new BackupMetadataState(db, ctx.getTransactionContext());
          BackupControlPlaneProcessors.register(processors, bmState, ctx.getKeyGenerator());
          return processors;
        })
        .build();
  }
}
```

(Method names on `StreamProcessorBuilder` may differ; cross-check against the data-partition wiring in `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/partitions/impl/steps/StreamProcessorTransitionStep.java`.)

- [ ] **Step 4: Build**

Run: `./mvnw install -pl zeebe/system-partition -am -Dquickly -T1C`
Expected: BUILD FAILURE — `BackupControlPlaneProcessors` and `BackupMetadataState` don't exist yet (Task 17–18). For now stub them as empty placeholders to unblock the build:

Stub `BackupControlPlaneProcessors`:
```java
package io.camunda.zeebe.systempartition.processors;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessors;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.systempartition.state.BackupMetadataState;
public final class BackupControlPlaneProcessors {
  private BackupControlPlaneProcessors() {}
  public static void register(TypedRecordProcessors p, BackupMetadataState s, KeyGenerator k) {}
}
```

Stub `BackupMetadataState`:
```java
package io.camunda.zeebe.systempartition.state;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
public final class BackupMetadataState {
  public BackupMetadataState(ZeebeDb<ZbColumnFamilies> db, TransactionContext ctx) {}
}
```

Re-run: `./mvnw install -pl zeebe/system-partition -am -Dquickly -T1C`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit Phase 2 work**

```bash
./mvnw license:format spotless:apply -T1C
git add zeebe/system-partition/
git commit -m "feat: stream-processor scaffolding for system partition"
```

---

## Phase 3 — System partition bootstrap refactor

### Task 14: SystemPartitionFacadeImpl

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionFacadeImpl.java`

- [ ] **Step 1: Implement**

```java
package io.camunda.zeebe.systempartition;

import io.atomix.cluster.MemberId;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.systempartition.state.ClusterConfigurationState;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class SystemPartitionFacadeImpl implements SystemPartition {

  private final RaftPartition raftPartition;
  private final BrokerClient brokerClient;
  private final LogStreamWriter writer;
  private final ClusterConfigurationState state;
  private final CopyOnWriteArrayList<Consumer<Boolean>> leaderListeners = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Consumer<ClusterConfiguration>> configListeners = new CopyOnWriteArrayList<>();
  private volatile ClusterConfiguration cached = ClusterConfiguration.uninitialized();

  public SystemPartitionFacadeImpl(
      final RaftPartition raftPartition,
      final BrokerClient brokerClient,
      final LogStream logStream,
      final ClusterConfigurationState state) {
    this.raftPartition = raftPartition;
    this.brokerClient = brokerClient;
    this.writer = logStream.newLogStreamWriter();
    this.state = state;
  }

  @Override
  public ClusterConfiguration query() {
    return cached;
  }

  @Override
  public boolean isLeader() {
    return raftPartition.getRole() == io.atomix.raft.RaftServer.Role.LEADER;
  }

  @Override
  public void addLeaderListener(final Consumer<Boolean> listener) { leaderListeners.add(listener); }

  @Override
  public void addClusterConfigListener(final Consumer<ClusterConfiguration> listener) {
    configListeners.add(listener);
  }

  @Override
  public ActorFuture<ClusterConfigurationRecord> submitCommand(
      final ClusterConfigurationIntent intent, final ClusterConfigurationRecord record) {
    if (!isLeader()) {
      // Forward to leader via BrokerClient. For hackday MVP: simple loopback over the
      // existing ClusterConfigurationRequestServer with a new intent-aware envelope.
      return forwardToLeader(intent, record);
    }
    final var future = new CompletableActorFuture<ClusterConfigurationRecord>();
    final var meta = new RecordMetadata().recordType(RecordType.COMMAND)
        .valueType(io.camunda.zeebe.protocol.record.ValueType.CLUSTER_CONFIGURATION)
        .intent(intent);
    writer.tryWrite(io.camunda.zeebe.logstreams.log.WriteContext.internal(),
        io.camunda.zeebe.logstreams.log.LogAppendEntry.of(meta, record));
    // Correlate by record.requestId; resolve future via configListeners hook installed
    // by SystemPartitionMirror.
    PendingRequests.register(record.getRequestId(), future);
    return future;
  }

  // Internals: applyCommit(), notifyLeaderChange() invoked from SystemPartitionMirror.
  public void applyCommit(final ClusterConfiguration newConfig) {
    cached = newConfig;
    configListeners.forEach(l -> l.accept(newConfig));
  }

  public void notifyLeaderChange(final boolean isLeader) {
    leaderListeners.forEach(l -> l.accept(isLeader));
  }

  private ActorFuture<ClusterConfigurationRecord> forwardToLeader(
      final ClusterConfigurationIntent intent, final ClusterConfigurationRecord record) {
    // Hackday MVP: send via BrokerClient using a new "system-partition-command" RPC.
    // For the first cut, throw NotLeaderException — the only callers running off-leader
    // are job workers, which we route to the leader explicitly below in their setup.
    throw new NotLeaderException("submitCommand off-leader not yet supported");
  }
}
```

(`PendingRequests` is a tiny static map keyed by requestId. Add it as a private static class inside the facade, or as a separate file in the same package.)

- [ ] **Step 2: PendingRequests static helper**

Inside `SystemPartitionFacadeImpl.java` add:

```java
  private static final class PendingRequests {
    private static final java.util.concurrent.ConcurrentHashMap<String,
        CompletableActorFuture<ClusterConfigurationRecord>> MAP =
        new java.util.concurrent.ConcurrentHashMap<>();
    static void register(String id, CompletableActorFuture<ClusterConfigurationRecord> f) {
      MAP.put(id, f);
    }
    static void resolve(String id, ClusterConfigurationRecord record) {
      final var f = MAP.remove(id);
      if (f != null) f.complete(record);
    }
  }
```

- [ ] **Step 3: Build & defer commit to Task 16.**

### Task 15: SystemPartitionMirror refactor

**Files:**
- Modify: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionMirror.java`

- [ ] **Step 1: Replace contents**

```java
package io.camunda.zeebe.systempartition;

import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiper;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.logstreams.log.LogStream;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.scheduler.Actor;

public final class SystemPartitionMirror extends Actor {

  private final SystemPartitionFacadeImpl facade;
  private final LogStream logStream;
  private final ClusterConfigurationGossiper gossiper;
  private final ProtoBufSerializer serializer = new ProtoBufSerializer();

  public SystemPartitionMirror(
      final SystemPartitionFacadeImpl facade,
      final LogStream logStream,
      final ClusterConfigurationGossiper gossiper) {
    this.facade = facade;
    this.logStream = logStream;
    this.gossiper = gossiper;
  }

  @Override
  protected void onActorStarted() {
    logStream.registerCommitListener(this::onCommit);
  }

  private void onCommit(final long position) {
    // Drain newly committed records, filter for CC.* events, decode payload,
    // update facade snapshot, gossip, resolve pending requests.
    actor.run(() -> {
      try (var reader = logStream.newLogStreamReader()) {
        reader.seekToNextEvent(position);
        while (reader.hasNext()) {
          final var event = reader.next();
          if (event.getValueType() != io.camunda.zeebe.protocol.record.ValueType.CLUSTER_CONFIGURATION) {
            continue;
          }
          final var record = new ClusterConfigurationRecord();
          event.readValue(record);
          if (event.getIntent() == ClusterConfigurationIntent.OPERATION_APPLIED
              || event.getIntent() == ClusterConfigurationIntent.CHANGE_PLAN_STAMPED
              || event.getIntent() == ClusterConfigurationIntent.CHANGE_COMPLETED) {
            final ClusterConfiguration cfg = serializer.decode(record.getConfiguration());
            facade.applyCommit(cfg);
            gossiper.gossipClusterConfiguration(cfg);
          }
          if (event.getIntent() == ClusterConfigurationIntent.OPERATION_APPLIED
              || event.getIntent() == ClusterConfigurationIntent.CHANGE_PLAN_STAMPED
              || event.getIntent() == ClusterConfigurationIntent.REJECT) {
            SystemPartitionFacadeImpl.PendingRequests.resolve(record.getRequestId(), record);
          }
        }
      }
    });
  }
}
```

(API names `seekToNextEvent`, `getValueType`, `getIntent`, `readValue` should match `LogStreamReader` / `LoggedEvent` — adjust to match the actual surface.)

- [ ] **Step 2: Build & defer commit to Task 16.**

### Task 16: SystemPartitionStep — wire stream processor in bootstrap

**Files:**
- Modify: `zeebe/broker/src/main/java/io/camunda/zeebe/broker/bootstrap/SystemPartitionStep.java`
- Delete: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionStateMachine.java`
- Delete: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionRecord.java`

- [ ] **Step 1: Replace startup sequence**

In `SystemPartitionStep.java`, the current startup builds the meter registry, snapshot store, and `RaftPartition`, then submits `SystemPartitionStateMachine`. Replace the actor submission with:

```java
// 1. Build LogStream over the system-partition Raft.
final LogStream logStream = SystemPartitionLogStream.build(
    raftPartition, scheduler, partitionMeterRegistry).join();

// 2. Open ZeebeDb at {dataDir}/system/partitions/1/state/.
final ZeebeDb<ZbColumnFamilies> db = openZeebeDb(systemDataDir.resolve("state"), cfg.getRocksDb());

// 3. Build the configuration-change appliers (reuse from dynamic-config).
final ConfigurationChangeAppliers appliers =
    new io.camunda.zeebe.dynamic.config.changes.ConfigurationChangeAppliersImpl();

// 4. Build & start the StreamProcessor.
final StreamProcessor processor = SystemPartitionStreamProcessorFactory.build(
    logStream, db, scheduler, appliers, partitionMeterRegistry);
scheduler.submitActor(processor).join();

// 5. Build the facade.
final ClusterConfigurationState state = ...; // read from the processor context once it's open.
final SystemPartitionFacadeImpl facade = new SystemPartitionFacadeImpl(
    raftPartition, brokerClient, logStream, state);

// 6. Wire the mirror.
final SystemPartitionMirror mirror = new SystemPartitionMirror(facade, logStream, gossiper);
scheduler.submitActor(mirror).join();

// 7. Wire the role-change listener.
raftPartition.addRoleChangeListener((role, term) ->
    facade.notifyLeaderChange(role == io.atomix.raft.RaftServer.Role.LEADER));

// 8. Publish the facade onto the BrokerStartupContext.
context.setSystemPartition(facade);
```

(Exposing the `ClusterConfigurationState` to the facade after the processor opens its `ZeebeDb` is the trickiest plumbing. Hackday-shaped fix: have `SystemPartitionStreamProcessorFactory.build` return a small container `(StreamProcessor, ClusterConfigurationState, BackupMetadataState)` and pass them along.)

- [ ] **Step 2: Delete the old state machine**

```bash
git rm zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionStateMachine.java \
       zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionRecord.java
```

- [ ] **Step 3: Build the broker module**

Run: `./mvnw install -pl zeebe/broker -am -Dquickly -T1C`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Format + commit**

```bash
./mvnw license:format spotless:apply -T1C
git add zeebe/broker/src/main/java/io/camunda/zeebe/broker/bootstrap/SystemPartitionStep.java \
        zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/
git commit -m "feat: bootstrap system partition with full StreamProcessor"
```

---

## Phase 4 — Coordinator + ops integration

### Task 17: Refactor ConfigurationChangeCoordinatorImpl

**Files:**
- Modify: `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/changes/ConfigurationChangeCoordinatorImpl.java`

- [ ] **Step 1: Inject the SystemPartition facade**

Add a constructor parameter `SystemPartition systemPartition`. Replace the existing direct mutation path in `applyOperations(req)`:

```java
@Override
public ActorFuture<ClusterConfiguration> applyOperations(
    final ConfigurationChangeRequest req) {
  if (!isCoordinator()) {
    return forwardToCoordinator(req); // existing behaviour
  }
  final ClusterConfiguration current = systemPartition.query();
  final var validation = validate(current, req);
  if (validation.isLeft()) {
    return CompletableActorFuture.completedExceptionally(validation.getLeft());
  }
  final ClusterConfiguration proposed = current.withChangePlan(validation.get());
  final var record = new ClusterConfigurationRecord()
      .setRequestId(java.util.UUID.randomUUID().toString())
      .setExpectedPreviousVersion(current.version())
      .setConfiguration(new ProtoBufSerializer().encode(proposed));
  return systemPartition.submitCommand(
      ClusterConfigurationIntent.STAMP_CHANGE_PLAN, record)
    .thenApply(reply ->
        reply.getRejectionReason().isEmpty()
            ? new ProtoBufSerializer().decode(reply.getConfiguration())
            : Either.left(new SystemPartition.ConcurrentModificationException(reply.getRejectionReason())).get());
}
```

(Adapt to the file's actual `Either` and async style — do not invent.)

- [ ] **Step 2: Build**

Run: `./mvnw install -pl zeebe/dynamic-config -am -Dquickly -T1C`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Format + commit**

```bash
./mvnw license:format spotless:apply -T1C
git add zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/changes/ConfigurationChangeCoordinatorImpl.java
git commit -m "refactor: stamp cluster change plan via system partition command"
```

### Task 18: Refactor ClusterConfigurationManagerImpl op-applied flow (two-phase write)

**Files:**
- Modify: `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ClusterConfigurationManagerImpl.java`

The current `applyOperationDirectly(providedConfig, operation, appliers)` mutates locally and gossips. Replace with a two-phase pattern:

- [ ] **Step 1: Replace the body**

```java
public ActorFuture<ClusterConfiguration> applyOperationDirectly(
    final ClusterConfiguration providedConfig,
    final ClusterConfigurationChangeOperation operation,
    final ConfigurationChangeAppliers appliers) {

  // Phase 1: apply locally (idempotent — Raft join, exporter toggle).
  final ActorFuture<Void> localApply = appliers.localApply(operation, providedConfig);

  // Phase 2: on local-apply success, submit CC.APPLY_OPERATION on the system partition.
  return localApply.thenCompose(unused -> {
    final var record = new ClusterConfigurationRecord()
        .setRequestId(java.util.UUID.randomUUID().toString())
        .setExpectedPreviousVersion(providedConfig.version())
        .setAppliedOperation(new ProtoBufSerializer().encodeOperation(operation));
    return systemPartition.submitCommand(
        ClusterConfigurationIntent.APPLY_OPERATION, record);
  }).thenApply(reply -> {
    if (!reply.getRejectionReason().isEmpty()) {
      throw new SystemPartition.ConcurrentModificationException(reply.getRejectionReason());
    }
    return new ProtoBufSerializer().decode(reply.getConfiguration());
  });
}
```

- [ ] **Step 2: Remove the local persist + gossip from this path**

`updateLocalConfiguration` / `gossiper.accept(cfg)` should now happen via the `SystemPartitionMirror` commit listener — not from inside this method. Delete those calls from this code path.

- [ ] **Step 3: Build & commit**

```bash
./mvnw install -pl zeebe/dynamic-config -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ClusterConfigurationManagerImpl.java
git commit -m "refactor: two-phase write via system partition for op-applied flow"
```

---

## Phase 5 — BPMN cluster operations port

### Task 19: Port BpmnAutoDeployer → SystemPartitionBpmnAutoDeployer

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionBpmnAutoDeployer.java`

Source: `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/BpmnAutoDeployer.java` on `origin/dd-hackday-dynamic-config-system`.

- [ ] **Step 1: Pull the file from the other branch**

```bash
git show origin/dd-hackday-dynamic-config-system:zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/BpmnAutoDeployer.java > /tmp/BpmnAutoDeployer.java
```

- [ ] **Step 2: Adapt the deployer**

Save the adapted version to `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/SystemPartitionBpmnAutoDeployer.java` with:

- Class name → `SystemPartitionBpmnAutoDeployer`
- Trigger → fire on leader-role transition (subscribe via `facade.addLeaderListener`).
- Deployment target → submit deployment commands as `DeploymentRecord` directly on the system partition's `LogStreamWriter` (not via the broker gateway), to avoid bootstrapping a `CamundaClient` inside the broker.
- Resources to deploy → ported BPMN files (added in Task 22) plus `checkpoint_scheduler.bpmn` and `retention_scheduler.bpmn`.

- [ ] **Step 3: Wire into SystemPartitionStep**

Add to `SystemPartitionStep.java` after the mirror is started:

```java
final var deployer = new SystemPartitionBpmnAutoDeployer(facade, logStreamWriter);
facade.addLeaderListener(isLeader -> { if (isLeader) deployer.deployAll(); });
```

- [ ] **Step 4: Build & commit**

```bash
./mvnw install -pl zeebe/system-partition,zeebe/broker -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/system-partition/ zeebe/broker/
git commit -m "feat: auto-deploy cluster-mgmt BPMNs on system partition leader"
```

### Task 20: Port BpmnConfigurationChangeJobWorker

**Files:**
- Create: `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/BpmnConfigurationChangeJobWorker.java`

Source: same path on `origin/dd-hackday-dynamic-config-system`.

- [ ] **Step 1: Pull and adapt**

```bash
git show origin/dd-hackday-dynamic-config-system:zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/BpmnConfigurationChangeJobWorker.java > zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/BpmnConfigurationChangeJobWorker.java
```

- [ ] **Step 2: Modify the worker**

Find the section that reads `configuration` from job variables and applies it locally, then writes the result back. Change the worker to:

1. Read the working `ClusterConfiguration` from job variables (BPMN passes it from process variables).
2. Call `clusterConfigurationManager.applyOperationDirectly(workingConfig, operation, appliers)` (which now does two-phase write — see Task 18).
3. On the returned future's completion (i.e. CC.OPERATION_APPLIED on the system partition has committed), complete the job with output `{configuration: <new>}` (so BPMN process variables advance for the next iteration).

Remove any code that publishes to the `camunda.vars.cluster.clusterConfiguration` cluster variable.

- [ ] **Step 3: Build & commit**

```bash
./mvnw install -pl zeebe/dynamic-config -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/BpmnConfigurationChangeJobWorker.java
git commit -m "feat: port config-change job worker; emit CC.APPLY_OPERATION via two-phase write"
```

### Task 21: Port RedistributionCalculationJobWorker + ExporterCalculationJobWorker

- [ ] **Step 1: Pull both files from the other branch**

```bash
for f in RedistributionCalculationJobWorker ExporterCalculationJobWorker; do
  git show origin/dd-hackday-dynamic-config-system:zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/${f}.java \
    > zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/${f}.java
done
```

- [ ] **Step 2: Adjust the initial-config read**

In each worker, replace the cluster-variable read with `systemPartition.query()`:

```java
final ClusterConfiguration current = systemPartition.query();
```

(Inject `SystemPartition` in the constructor; wire the dependency in `ClusterConfigurationManagerService` once Task 25 is done.)

- [ ] **Step 3: Build & commit**

```bash
./mvnw install -pl zeebe/dynamic-config -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/RedistributionCalculationJobWorker.java \
        zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ExporterCalculationJobWorker.java
git commit -m "feat: port redistribution and exporter calculation workers"
```

### Task 22: Port BPMN process files (drop ClusterVariable usage)

**Files:**
- Create: `zeebe/dynamic-config/src/main/resources/bpmn/scale-operation.bpmn`
- Create: `zeebe/dynamic-config/src/main/resources/bpmn/exporter-operation.bpmn`
- Create: `zeebe/dynamic-config/src/main/resources/bpmn/modification_starter.bpmn`

- [ ] **Step 1: Pull from the other branch**

```bash
mkdir -p zeebe/dynamic-config/src/main/resources/bpmn
for b in scale-operation exporter-operation modification_starter; do
  git show origin/dd-hackday-dynamic-config-system:zeebe/dynamic-config/src/main/resources/bpmn/${b}.bpmn \
    > zeebe/dynamic-config/src/main/resources/bpmn/${b}.bpmn
done
```

- [ ] **Step 2: Edit each BPMN**

For each file, remove these elements via search-and-replace in your editor:

- The service task with `zeebe:taskDefinition type="update-cluster-variable"` (entire `<bpmn:serviceTask>` block plus surrounding sequence flows, then re-link upstream/downstream).
- Input expressions referencing `=camunda.vars.cluster.clusterConfiguration` — replace with `=clusterConfiguration` (which BPMN now receives as an initial process variable seeded by `BpmnInstanceStarter`).
- Output mappings writing into `camunda.vars.cluster.*` — drop entirely.

For `scale-operation.bpmn` and `exporter-operation.bpmn`, add a terminal service task before the end event:

```xml
<bpmn:serviceTask id="commit-change" name="Commit change">
  <bpmn:extensionElements>
    <zeebe:taskDefinition type="cc-complete-change" />
  </bpmn:extensionElements>
</bpmn:serviceTask>
```

- [ ] **Step 3: Add a `cc-complete-change` job worker**

Create `zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/CcCompleteChangeJobWorker.java`:

```java
package io.camunda.zeebe.dynamic.config;

import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.client.api.worker.JobHandler;
import io.camunda.zeebe.protocol.impl.record.value.clusterconfiguration.ClusterConfigurationRecord;
import io.camunda.zeebe.protocol.record.intent.ClusterConfigurationIntent;
import io.camunda.zeebe.systempartition.SystemPartition;

public final class CcCompleteChangeJobWorker implements JobHandler {

  private final SystemPartition systemPartition;

  public CcCompleteChangeJobWorker(final SystemPartition systemPartition) {
    this.systemPartition = systemPartition;
  }

  @Override
  public void handle(final JobClient client, final ActivatedJob job) {
    final long version = systemPartition.query().version();
    final var record = new ClusterConfigurationRecord()
        .setRequestId(java.util.UUID.randomUUID().toString())
        .setExpectedPreviousVersion(version);
    systemPartition.submitCommand(ClusterConfigurationIntent.COMPLETE_CHANGE, record)
        .onComplete((reply, err) -> {
          if (err != null) {
            client.newFailCommand(job).retries(job.getRetries() - 1).errorMessage(err.getMessage()).send();
          } else {
            client.newCompleteCommand(job).send();
          }
        });
  }
}
```

- [ ] **Step 4: Wire the worker** in `ClusterConfigurationManagerService` (search for where `BpmnConfigurationChangeJobWorker` is registered; add a sibling registration for `cc-complete-change`).

- [ ] **Step 5: Build & commit**

```bash
./mvnw install -pl zeebe/dynamic-config -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/dynamic-config/src/main/resources/bpmn/ \
        zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/CcCompleteChangeJobWorker.java \
        zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ClusterConfigurationManagerService.java
git commit -m "feat: port cluster-mgmt BPMNs without cluster-variable dependency"
```

### Task 23: Skip ClusterVariableUpdateJobWorker (do NOT port)

- [ ] **Step 1: Confirm absence**

Verify the file does not exist on the working branch:

```bash
ls zeebe/dynamic-config/src/main/java/io/camunda/zeebe/dynamic/config/ClusterVariableUpdateJobWorker.java 2>&1
```

Expected: file not found. (The original `dd-hackday-dynamic-config-system` branch has it; this plan deliberately drops it.)

- [ ] **Step 2:** No commit needed.

---

## Phase 6 — Backup control plane

### Task 24: BackupMetadataState (real impl, replace stub)

**Files:**
- Modify: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/state/BackupMetadataState.java`

- [ ] **Step 1: Replace the stub**

Pattern: same as `ClusterConfigurationState` (Task 9), but keyed by `(checkpointId, partitionId)`. Use `DbCompositeKey<DbLong, DbInt>` as the key. Methods: `put(BackupMetadataRecord)`, `get(checkpointId, partitionId)`, `iterateByCheckpoint(checkpointId, Consumer<BackupMetadataRecord>)`, `delete(checkpointId, partitionId)`.

- [ ] **Step 2: Build & defer commit to Task 27.**

### Task 25: BackupMetadataRecordProcessor + StateApplier

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/BackupMetadataRecordProcessor.java`
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/BackupMetadataStateApplier.java`

- [ ] **Step 1: RecordProcessor**

Mirror the cluster-config update processor: validate (e.g. status transitions PENDING→COMPLETED→CONFIRMED, FAILED is terminal), emit `*ED` event with the new status. No CAS — backup metadata is per-partition + per-checkpoint, so writes don't race.

- [ ] **Step 2: StateApplier**

```java
public final class BackupMetadataStateApplier
    implements TypedEventApplier<BackupMetadataIntent, BackupMetadataRecord> {
  private final BackupMetadataState state;
  public BackupMetadataStateApplier(BackupMetadataState s) { state = s; }
  @Override public void applyState(long key, BackupMetadataRecord record) {
    switch (Intent.fromValue(record)) {
      case RECORDED, MARKED_FAILED -> state.put(record);
      case DELETED -> state.delete(record.getCheckpointId(), record.getPartitionId());
      default -> {}
    }
  }
}
```

- [ ] **Step 3: Defer commit to Task 27.**

### Task 26: BackupControlPlaneProcessors factory (real impl, replace stub)

**Files:**
- Modify: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/processors/BackupControlPlaneProcessors.java`

- [ ] **Step 1: Replace stub**

```java
public static void register(
    final TypedRecordProcessors processors,
    final BackupMetadataState state,
    final KeyGenerator keys) {
  final var writers = processors.getWriters();
  final var processor = new BackupMetadataRecordProcessor(state, writers, keys);
  processors.onCommand(ValueType.BACKUP_METADATA, BackupMetadataIntent.RECORD, processor);
  processors.onCommand(ValueType.BACKUP_METADATA, BackupMetadataIntent.MARK_FAILED, processor);
  processors.onCommand(ValueType.BACKUP_METADATA, BackupMetadataIntent.DELETE, processor);
  final var applier = new BackupMetadataStateApplier(state);
  processors.withListener(BackupMetadataIntent.RECORDED, applier);
  processors.withListener(BackupMetadataIntent.MARKED_FAILED, applier);
  processors.withListener(BackupMetadataIntent.DELETED, applier);
}
```

- [ ] **Step 2: Defer commit to Task 27.**

### Task 27: BackupOrchestrator actor

**Files:**
- Create: `zeebe/system-partition/src/main/java/io/camunda/zeebe/systempartition/backup/BackupOrchestrator.java`

- [ ] **Step 1: Implement**

```java
package io.camunda.zeebe.systempartition.backup;

import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerExecuteCommand;
import io.camunda.zeebe.protocol.impl.record.value.management.CheckpointRecord;
import io.camunda.zeebe.protocol.record.intent.BackupMetadataIntent;
import io.camunda.zeebe.protocol.record.intent.management.CheckpointIntent;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.systempartition.SystemPartition;

public final class BackupOrchestrator extends Actor {

  private final SystemPartition systemPartition;
  private final BrokerClient brokerClient;

  public BackupOrchestrator(final SystemPartition sp, final BrokerClient bc) {
    this.systemPartition = sp;
    this.brokerClient = bc;
  }

  @Override
  protected void onActorStarted() {
    systemPartition.addClusterConfigListener(cfg -> {
      // Re-evaluate fan-out targets when the topology changes.
    });
    // Subscribe to BM.RECORDED via the system-partition log stream;
    // the SystemPartitionMirror exposes a registerBackupListener API.
  }

  /** Called by the mirror on every BM.RECORDED event. */
  public void onBackupRecorded(final long checkpointId, final int partitionId, final String status) {
    if (!"PENDING".equals(status)) return;
    final var cmd = new CheckpointRecord().setCheckpointId(checkpointId);
    brokerClient.sendRequestWithRetry(
        new BrokerExecuteCommand<>(partitionId, CheckpointIntent.CREATE, cmd))
        .whenComplete((reply, err) -> {
          final var update = new io.camunda.zeebe.protocol.impl.record.value.backupmetadata.BackupMetadataRecord()
              .setCheckpointId(checkpointId)
              .setPartitionId(partitionId)
              .setStatus(err == null ? "COMPLETED" : "FAILED");
          systemPartition.submitCommand(BackupMetadataIntent.RECORD, /* convert as record */ null);
          // (Wire conversion: SystemPartition needs an overloaded submitCommand for BM
          // records, OR a dedicated facade method submitBackupCommand. Add it in this task.)
        });
  }
}
```

- [ ] **Step 2: Add `submitBackupCommand` method to the facade**

Mirror the `submitCommand` shape but typed for `BackupMetadataRecord`. This is mechanical.

- [ ] **Step 3: Wire BackupOrchestrator in SystemPartitionStep**

Submit the actor on leader transition:

```java
facade.addLeaderListener(isLeader -> {
  if (isLeader) {
    final var orchestrator = new BackupOrchestrator(facade, brokerClient);
    scheduler.submitActor(orchestrator);
  }
});
```

- [ ] **Step 4: Build & commit (Phase 6 backup core)**

```bash
./mvnw install -pl zeebe/system-partition -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/system-partition/
git commit -m "feat: backup control plane on system partition"
```

### Task 28: BackupApiRequestHandler — forward TAKE_BACKUP to system partition

**Files:**
- Modify: `zeebe/broker/src/main/java/io/camunda/zeebe/broker/transport/backupapi/BackupApiRequestHandler.java`

- [ ] **Step 1: Edit handle()**

Replace the existing `BackupRequestType.TAKE_BACKUP` branch (which writes a `CheckpointRecord` on the data partition) with:

```java
case TAKE_BACKUP -> {
  final long checkpointId = request.getBackupId();
  // For each partition, emit BM.RECORD with PENDING status on the system partition.
  final var partitions = systemPartition.query().partitions();
  for (final int p : partitions) {
    final var record = new BackupMetadataRecord()
        .setCheckpointId(checkpointId)
        .setPartitionId(p)
        .setStatus("PENDING");
    systemPartition.submitBackupCommand(BackupMetadataIntent.RECORD, record);
  }
  yield ack(request);
}
```

(`submitBackupCommand` from Task 27 step 2.)

- [ ] **Step 2: Build & commit**

```bash
./mvnw install -pl zeebe/broker -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/broker/src/main/java/io/camunda/zeebe/broker/transport/backupapi/BackupApiRequestHandler.java
git commit -m "feat: route BackupApi TAKE_BACKUP through system partition"
```

### Task 29: Port CheckpointTriggerJobWorker + RetentionTriggerJobWorker + CheckpointSchedulingService

**Files:**
- Create: `zeebe/backup/src/main/java/io/camunda/zeebe/backup/schedule/CheckpointTriggerJobWorker.java`
- Create: `zeebe/backup/src/main/java/io/camunda/zeebe/backup/schedule/RetentionTriggerJobWorker.java`
- Modify: `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/management/CheckpointSchedulingService.java`

- [ ] **Step 1: Pull workers**

```bash
for f in CheckpointTriggerJobWorker RetentionTriggerJobWorker; do
  git show origin/dd-hackday-dynamic-config-system:zeebe/backup/src/main/java/io/camunda/zeebe/backup/schedule/${f}.java \
    > zeebe/backup/src/main/java/io/camunda/zeebe/backup/schedule/${f}.java
done
```

- [ ] **Step 2: Adapt the workers**

Both workers should subscribe via the broker's local job-worker registration mechanism (same as `BpmnConfigurationChangeJobWorker`) — NOT via a separate `CamundaClient`. They are broker-side actors.

- `CheckpointTriggerJobWorker`: instead of calling `BackupRequestHandler.checkpoint(...)` directly, submit a `BackupMetadataIntent.RECORD` command per partition on the system partition (same code path as Task 28).
- `RetentionTriggerJobWorker`: read the backup-metadata snapshot from the system partition (add `systemPartition.queryBackupMetadata(): Stream<BackupMetadataRecord>` if missing — small facade addition); compute deletable IDs by retention window (read from `BackupCfg`); submit `BackupMetadataIntent.DELETE` per id.

- [ ] **Step 3: Pull CheckpointSchedulingService changes**

```bash
git show origin/dd-hackday-dynamic-config-system:zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/management/CheckpointSchedulingService.java \
  > zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/management/CheckpointSchedulingService.java
```

- [ ] **Step 4: Adapt the service**

In the ported file, remove the `CamundaClient` deployment block. Replace with: register the scheduling BPMNs (`checkpoint_scheduler.bpmn`, `retention_scheduler.bpmn`) on `SystemPartitionBpmnAutoDeployer`'s deployment list. Service still owns the `BackupCfg` parsing and the activation guard ("only on coordinator").

- [ ] **Step 5: Add the scheduler BPMNs**

```bash
git show origin/dd-hackday-dynamic-config-system:zeebe/dynamic-config/src/main/resources/bpmn/checkpoint_scheduler.bpmn \
  > zeebe/system-partition/src/main/resources/bpmn/checkpoint_scheduler.bpmn 2>/dev/null || echo "Source absent — skip"
# Same for retention_scheduler.bpmn. If neither exists on the source branch, write minimal
# BPMN files that fire a timer and emit a single job (checkpoint-trigger / retention-trigger).
```

If the source branch only has them embedded inside `CheckpointSchedulingService.deploySchedulerBpmn` as Java-built BPMN models, port that builder logic into `SystemPartitionBpmnAutoDeployer` — same effect.

- [ ] **Step 6: Build & commit**

```bash
./mvnw install -pl zeebe/backup,zeebe/broker,zeebe/system-partition -am -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add zeebe/backup/src/main/java/io/camunda/zeebe/backup/schedule/ \
        zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/management/CheckpointSchedulingService.java \
        zeebe/system-partition/
git commit -m "feat: backup scheduler BPMNs on system partition"
```

---

## Phase 7 — Smoke tests

### Task 30: SystemPartitionStreamProcessorIT

**Files:**
- Create: `zeebe/qa/integration-tests/src/test/java/io/camunda/it/SystemPartitionStreamProcessorIT.java`

- [ ] **Step 1: Implement the IT**

Pattern: copy the harness from `zeebe/qa/integration-tests/src/test/java/io/camunda/it/StandaloneCamundaTest.java`. Test method:

```java
@Test
void shouldStartSystemPartitionAndAutoDeployBpmns() {
  // given: a 1-broker cluster with experimental.systemPartition.enabled = true
  // when: the broker has reached a steady state
  Awaitility.await().untilAsserted(() -> {
    final var systemPartition = brokerContext.getSystemPartition();
    assertThat(systemPartition.isLeader()).isTrue();
    assertThat(systemPartition.query()).isNotNull();
    // and: the cluster-mgmt BPMNs are deployed
    assertThat(deployments(systemPartitionId))
        .extracting(d -> d.getProcessId())
        .contains("scale-operation", "exporter-operation",
            "modification_starter", "checkpoint_scheduler", "retention_scheduler");
  });
}
```

- [ ] **Step 2: Run**

```bash
./mvnw verify -pl zeebe/qa/integration-tests \
  -DskipUTs -DskipTests=false \
  -Dit.test='SystemPartitionStreamProcessorIT' -Dquickly -T1C
```

Expected: PASS (5/0/0/0). If FAIL, fix and re-run.

- [ ] **Step 3: Commit**

```bash
git add zeebe/qa/integration-tests/src/test/java/io/camunda/it/SystemPartitionStreamProcessorIT.java
git commit -m "test: smoke IT for system-partition stream processor"
```

### Task 31: ScaleUpViaSystemPartitionIT

**Files:**
- Create: `zeebe/qa/integration-tests/src/test/java/io/camunda/it/ScaleUpViaSystemPartitionIT.java`

- [ ] **Step 1: Implement**

Reuse the rig from `zeebe/qa/integration-tests/src/test/java/io/camunda/it/clustering/ScaleUpBrokersTest.java`. Test method:

```java
@Test
void shouldScaleUpViaSystemPartition() {
  // given: 3-broker cluster, system partition enabled, RF=3, partitions=3
  // when: scale up by 1 broker via the actuator REST endpoint
  startBroker(4);
  final var actuator = ActuatorClient.connectTo(broker(0));
  actuator.scaleUp(4);

  // then: cluster reaches CHANGE_COMPLETED
  Awaitility.await()
      .atMost(Duration.ofMinutes(2))
      .untilAsserted(() -> {
        final ClusterConfiguration cfg = systemPartition().query();
        assertThat(cfg.activeChangePlan()).isEmpty();
        assertThat(cfg.lastChange()).isPresent();
        assertThat(cfg.members()).hasSize(4);
      });

  // and: per-operation OPERATION_APPLIED events committed on system partition
  final var events = readSystemPartitionEvents(ClusterConfigurationIntent.OPERATION_APPLIED);
  assertThat(events).hasSizeGreaterThan(1); // at least Join + Reconfigure ops
}
```

- [ ] **Step 2: Run**

```bash
./mvnw verify -pl zeebe/qa/integration-tests \
  -DskipUTs -DskipTests=false \
  -Dit.test='ScaleUpViaSystemPartitionIT' -Dquickly -T1C
```

Expected: PASS. Total runtime ~2 min.

- [ ] **Step 3: Commit**

```bash
git add zeebe/qa/integration-tests/src/test/java/io/camunda/it/ScaleUpViaSystemPartitionIT.java
git commit -m "test: smoke IT for BPMN-driven scale-up via system partition"
```

### Task 32: BackupViaSystemPartitionIT

**Files:**
- Create: `zeebe/qa/integration-tests/src/test/java/io/camunda/it/BackupViaSystemPartitionIT.java`

- [ ] **Step 1: Implement**

```java
@Test
void shouldTakeBackupViaSystemPartition() {
  // given: 3-broker cluster, system partition enabled, in-memory BackupStore
  final long checkpointId = 1L;

  // when: take a backup via REST
  actuator.takeBackup(checkpointId);

  // then: BM.RECORDED CONFIRMED appears for every partition on the system partition
  Awaitility.await()
      .atMost(Duration.ofMinutes(2))
      .untilAsserted(() -> {
        final var rows = readBackupMetadata(checkpointId);
        assertThat(rows).hasSize(3); // 3 partitions
        assertThat(rows).allMatch(r -> "CONFIRMED".equals(r.getStatus()));
      });

  // and: the backup is retrievable from the BackupStore
  assertThat(backupStore.list(checkpointId).join()).hasSize(3);
}
```

- [ ] **Step 2: Run**

```bash
./mvnw verify -pl zeebe/qa/integration-tests \
  -DskipUTs -DskipTests=false \
  -Dit.test='BackupViaSystemPartitionIT' -Dquickly -T1C
```

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add zeebe/qa/integration-tests/src/test/java/io/camunda/it/BackupViaSystemPartitionIT.java
git commit -m "test: smoke IT for backup via system partition"
```

### Task 33: Adjust ConfigurationChangeCoordinatorImplTest

**Files:**
- Modify: `zeebe/dynamic-config/src/test/java/io/camunda/zeebe/dynamic/config/changes/ConfigurationChangeCoordinatorImplTest.java`

- [ ] **Step 1: Mock the SystemPartition facade in existing tests**

Replace any setup that mocked direct local-state mutation with a `SystemPartition` mock whose `submitCommand(STAMP_CHANGE_PLAN, …)` returns a completed future carrying the stamped configuration. The 9 existing test cases should otherwise be unchanged.

- [ ] **Step 2: Run**

```bash
./mvnw verify -pl zeebe/dynamic-config -DskipITs -Dquickly -T1C
```

Expected: 9/0/0/0 PASS.

- [ ] **Step 3: Commit**

```bash
git add zeebe/dynamic-config/src/test/java/io/camunda/zeebe/dynamic/config/changes/ConfigurationChangeCoordinatorImplTest.java
git commit -m "test: adjust coordinator unit tests for system-partition command path"
```

### Task 34: Final verification

- [ ] **Step 1: Run the full smoke set + the legacy regression guard**

```bash
./mvnw verify -pl zeebe/qa/integration-tests \
  -DskipUTs -DskipTests=false \
  -Dit.test='ScaleUpBrokersTest,ScaleUpViaSystemPartitionIT,BackupViaSystemPartitionIT,SystemPartitionStreamProcessorIT' \
  -Dquickly -T1C
```

Expected: all PASS, both flag-on and flag-off paths green.

- [ ] **Step 2: Format final commit**

```bash
./mvnw license:format spotless:apply -T1C
git status # should be clean
```

- [ ] **Step 3: If any drift: amend or follow-up commit**

If `spotless:apply` produced changes:

```bash
git add -u
git commit -m "style: spotless format pass"
```

---

## Self-review summary

- **Spec coverage.** Each spec section maps to one or more tasks: §3 architecture (Tasks 8, 13, 14, 16); §4.1 new components (Tasks 4, 5, 9, 10, 11, 12, 13, 14, 15, 19, 24, 25, 26, 27); §4.2 modifications (Tasks 7, 16, 17, 18, 20, 21, 22, 28, 29); §4.3 deletions (Tasks 16, 23); §5 data flow (Tasks 17, 18, 20, 27); §6 error handling (CAS in Tasks 11, 12, 25; leader stepdown in Task 27 step 3; quorum loss documented but unimplemented per MVP scope); §7 minimum tests (Tasks 30, 31, 32, 33).
- **Placeholder scan.** Two intentional simplifications carry forward from the spec: `forwardToLeader` in `SystemPartitionFacadeImpl` throws on off-leader callers (Task 14 step 1) — fine because all current callers run on the leader after `SystemPartitionStep` resolves it; `BackupOrchestrator` registration uses a sketched `addClusterConfigListener` hook (Task 27) — full wiring is fleshed out in step 3 of the same task.
- **Type consistency.** `ClusterConfigurationRecord` setter/getter names used in Tasks 4, 11, 12, 14, 17, 18 line up. `submitCommand` signature used in Task 7 matches usage in Tasks 14, 17, 18, 22.
