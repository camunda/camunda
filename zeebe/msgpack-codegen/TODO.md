# Msgpack codegen: things to do

Status: `ElementInstance`, `IndexedRecord` and `TaskListenerIndicesRecord` are migrated. Everything
below is open.

## Design

- [ ] **Codec at the `ColumnFamily` level.** Create a column family with a value codec, so `get`
      returns the POJO and the POJOs no longer implement `DbValue`. Then `MsgpackBufferValue` and the
      thread-local `MsgpackScratch` can be deleted. Touches the `zb-db` API (additive overload of
      `ZeebeDb.createColumnFamily`, `ValueType` no longer bounded by `DbValue`, every `ColumnFamily`
      implementation, `KeyValuePairVisitor`). Needs a decision on API compatibility first. Until then
      the bridge stays.
- [ ] **Bridge cost.** `MsgpackBufferValue` does a `ClassValue` lookup per `write`/`wrap`/`reset`.
      It disappears with the column-family codec above; measure it in the benchmark first.
- [ ] **Unknown properties by default.** Decide whether every `@MsgpackPojo` carries
      `UnknownProperties` implicitly (rollback safety) instead of an explicit field in each class.
- [ ] **Convert `ProcessInstanceRecord`** (and other records written to the log) into a
      `@MsgpackPojo`, or keep them as nested `BaseValue`s. Nested `BaseValue`s are read by reference
      to the input buffer, which is why `IndexedRecord.wrap` copies the input first.
- [ ] **String handling.** Decoding allocates a `byte[]` and an `UnsafeBuffer` for every non-empty
      string. Look at shared constants for repeated values (process id, element id, tenant id) and
      at reusing buffers owned by the target.
- [ ] **`toString`/JSON for the POJOs.** `UnpackedObject` gave every class a readable
      `toString()`. Nothing in the engine needs it yet; add a generated one if logs or assertions do.
- [ ] **Property types the generator rejects:** `DocumentProperty`,
      `PackedProperty`, `ArrayProperty` of anything but strings (`LongValue`, nested objects), `PropertyGroup`, sanitized properties.
      Extend the generator when a class to migrate needs one.
- [ ] **Registry lookup.** `Codecs.of` finds `<Name>Codec` by naming convention. Consider a
      generated `ServiceLoader` registration if a missing codec should fail earlier.

## Verification

- [ ] **Benchmark.** JMH comparison of the `*V1` classes and the new ones for `get` and `update`
      through a column family, with the GC profiler. This decides whether to continue migrating.
- [ ] **Maven build.** The Maven wiring (`msgpack-codegen/pom.xml`, `parent/pom.xml`,
      `zeebe/pom.xml`, `zeebe/engine/pom.xml` `annotationProcessorPaths`) was written but never run,
      because the snapshot modules could not be resolved offline. Build `zeebe/engine -am` once.
- [ ] **Nullness.** Add `@NullMarked`/`@Nullable` to the new module in a separate `refactor:` commit.

## Migration of the remaining state classes

Each one follows the `migrate-state-to-msgpack-pojo` skill, one commit per class.
All paths are under `zeebe/engine/src/main/java/io/camunda/zeebe/engine/state/`.

### Wave 1: the state written by a process with one service task (load test)

Derived from the column families of `DbElementInstanceState`, `DbJobState`, `DbVariableState`,
`DbEventScopeInstanceState`, `DbProcessState`, `DbDeploymentState` and the processing position.
`ElementInstance`, `IndexedRecord` and `TaskListenerIndicesRecord` are already done.

Supported by the generator today:

- [x] `processing/LastProcessedPosition` (written after every processed record)
- [x] `instance/ParentScopeKey` (variable scopes)
- [x] `instance/JobStateValue` (job state column family)
- [ ] `instance/JobRecordValue` (nests `JobRecord`, a `BaseValue`)
- [x] `instance/EventScopeInstance` (`List<DirectBuffer>` fields)
- [x] `deployment/LatestProcessVersion`
- [ ] `deployment/DeploymentRaw` (nests `DeploymentRecord`, a `BaseValue`)

Need `@MsgpackProperty(binary = true)`, supported now:

- [x] `variable/VariableInstance` (hot: every variable write)
- [x] `instance/EventTrigger`
- [x] `deployment/Digest`

Deferred, the generator lacks the property types (deployment time only, not on the hot path):

- [ ] `deployment/PersistedProcess`: binary plus `ArrayProperty` of a nested object, and it
      implements `ProjectionOf<Tuple<ProcessRecord, Long>, PersistedProcess>`.
- [ ] `deployment/VersionInfo`: `ArrayProperty<LongValue>`, a `List<Long>` field type.

### Cost to measure in wave 1

- [ ] `VariableInstance.wrap` and `IndexedRecord.wrap` copy the whole input because the nested
      `VariableMetadataRecord` / `ProcessInstanceRecord` are still `UnpackedObject`s that decode by
      reference. The binary value is copied a second time on decode. Both go away when the nested
      records are migrated (they live in protocol-impl); until then, check them in the benchmark.
- [ ] Migrated classes lost the content-based `equals`/`hashCode` of `UnpackedObject`. No test or
      caller needed it, but a generated `equals` would be cheap if one shows up.

### Wave 2: everything else

- [ ] Classes that extend `UnpackedObject` and are stored as `DbValue` (engine unless noted):
      `ClusterVariableInstance`, `CompensationSubscription`, `ConditionalSubscription`,
      `Incident`, `IncidentKey`, `MessageStartEventSubscription`, `MessageSubscription`,
      `ProcessMessageSubscription`, `PersistedDecision`, `PersistedForm`, `PersistedResource`,
      `PersistedUser`, `PersistedRole`, `PersistedTenant`, `PersistedGroup`,
      `PersistedAuthorization`, `PersistedMappingRule`, `PersistedBatchOperation`,
      `PersistedBatchOperationChunk`, `PersistedCommandDistribution`, `SignalSubscription`,
      `StoredMessage`, `TimerInstance`, `UserTaskRecordValue`, `DbAgent*`, ...
      The full list: `grep -rl "extends UnpackedObject" zeebe --include=*.java | xargs grep -l DbValue`.
      Outside the engine: `NextValue` (stream-platform), `ExporterStateEntry`
      (broker), `CheckpointInfo`, `CheckpointMetadataValue` (backup). Those modules need the
      dependency and the annotation processor wiring first.
- [ ] Try the skill with a small model first and refine it from what goes wrong (wave 1 does this).
