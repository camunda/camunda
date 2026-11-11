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
- [ ] **Property types the generator rejects:** `BinaryProperty`, `DocumentProperty`,
      `PackedProperty`, `ArrayProperty` of non-strings, `PropertyGroup`, sanitized properties.
      Extend the generator when a class to migrate needs one.
- [ ] **Registry lookup.** `Codecs.of` finds `<Name>Codec` by naming convention. Consider a
      generated `ServiceLoader` registration if a missing codec should fail earlier.

## Verification

- [ ] **Benchmark.** JMH comparison of the `*V1` classes and the new ones for `get` and `update`
      through a column family, with the GC profiler. This decides whether to continue migrating.
- [ ] **Maven build.** The Maven wiring (`msgpack-codegen/pom.xml`, `parent/pom.xml`,
      `zeebe/pom.xml`, `zeebe/engine/pom.xml` `annotationProcessorPaths`) was written but never run,
      because the snapshot modules could not be resolved offline. Build `zeebe/engine -am` once.
- [ ] **Processor tests.** The processor has no tests of its own (error cases: private field, final
      scalar, unsupported type, nested type without a codec).
- [ ] **Nullness.** Add `@NullMarked`/`@Nullable` to the new module in a separate `refactor:` commit.
- [ ] **Formatting.** Gradle has no `spotlessApply`; the new files were formatted with
      google-java-format directly. Run `./mvnw license:format spotless:apply` on them.

## Migration of the remaining state classes

Each one follows the `migrate-state-to-msgpack-pojo` skill, one commit per class.

- [ ] Try the skill with a small model on a small class first and refine it from what goes wrong.
- [ ] Classes that extend `UnpackedObject` and are stored as `DbValue` (engine unless noted):
      `ClusterVariableInstance`, `CompensationSubscription`, `ConditionalSubscription`,
      `EventScopeInstance`, `EventTrigger`, `Incident`, `IncidentKey`, `JobRecordValue`,
      `JobStateValue`, `MessageStartEventSubscription`, `MessageSubscription`,
      `ProcessMessageSubscription`, `PersistedProcess`, `PersistedDecision`, `PersistedForm`,
      `PersistedResource`, `PersistedUser`, `PersistedRole`, `PersistedTenant`, `PersistedGroup`,
      `PersistedAuthorization`, `PersistedMappingRule`, `PersistedBatchOperation`,
      `PersistedBatchOperationChunk`, `PersistedCommandDistribution`, `SignalSubscription`,
      `StoredMessage`, `TimerInstance`, `UserTaskRecordValue`, `VariableInstance`, `DbAgent*`, ...
      The full list: `grep -rl "extends UnpackedObject" zeebe --include=*.java | xargs grep -l DbValue`.
      Outside the engine: `LastProcessedPosition`, `NextValue` (stream-platform), `ExporterStateEntry`
      (broker), `CheckpointInfo`, `CheckpointMetadataValue` (backup). Those modules need the
      dependency and the annotation processor wiring first.
