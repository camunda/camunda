```yaml
---
applyTo: "zeebe/stream-platform/src/**"
---
```
# Zeebe Stream Platform — Module Instructions

## Purpose

The stream-platform module is the core stream processing framework that drives Zeebe's event-sourced engine. It reads records (commands/events) from the append-only log, dispatches them to `RecordProcessor` implementations for processing or replay, manages transactional state changes via RocksDB, and writes follow-up records back to the log. It provides the lifecycle, scheduling, error handling, and metrics infrastructure that sits between the raw log (`zeebe/logstreams`) and the domain-specific engine logic (`zeebe/engine`).

## Architecture

The module is split into two packages:
- **`stream.api`** — Public interfaces consumed by the engine and broker. This is the contract.
- **`stream.impl`** — Internal implementation. Not meant for external consumption.

### Core State Machines

**`StreamProcessor`** (`impl/StreamProcessor.java`) is an `Actor` that orchestrates the full lifecycle through phases: `INITIAL → REPLAY → PROCESSING → PAUSED/FAILED`. It owns the `ReplayStateMachine` and `ProcessingStateMachine`, and is the entry point for the broker to start/stop/pause stream processing on a partition.

**`ReplayStateMachine`** (`impl/ReplayStateMachine.java`) rebuilds state from the log after a snapshot restore. It reads all records, filters for EVENTs only via `MetadataEventFilter`, and calls `RecordProcessor.replay()` on each. Replay runs within a RocksDB transaction per batch. On followers (`StreamProcessorMode.REPLAY`), it runs continuously; on leaders, it completes and hands off to processing.

**`ProcessingStateMachine`** (`impl/ProcessingStateMachine.java`) processes new COMMANDs from the log. Its cycle is: `tryToReadNextRecord → processCommand → batchProcessing → writeRecords → updateState → executeSideEffects → tryToReadNextRecord`. It supports batch processing of follow-up commands (up to `maxCommandsInBatch`, default 100) within a single transaction.

### Key Abstractions

| Interface | Role | File |
|-----------|------|------|
| `RecordProcessor` | Engine-facing contract: `init`, `accepts`, `process`, `replay`, `onProcessingError` | `api/RecordProcessor.java` |
| `ProcessingResultBuilder` | Fluent builder for follow-up records, responses, and post-commit tasks | `api/ProcessingResultBuilder.java` |
| `ProcessingResult` | Immutable result containing record batch, response, and post-commit tasks | `api/ProcessingResult.java` |
| `TypedRecord<T>` | Typed wrapper around `LoggedEvent` with deserialized value and metadata | `api/records/TypedRecord.java` |
| `StreamProcessorLifecycleAware` | Callbacks for `onRecovered`, `onClose`, `onFailed`, `onPaused`, `onResumed` | `api/StreamProcessorLifecycleAware.java` |
| `ProcessingScheduleService` | Schedule async/sync tasks that write commands to the log | `api/scheduling/ProcessingScheduleService.java` |
| `Task` / `TaskResultBuilder` | Interface for scheduled tasks that produce command records | `api/scheduling/Task.java` |
| `KeyGenerator` / `KeyGeneratorControls` | Partition-scoped unique key generation backed by RocksDB state | `api/state/KeyGenerator.java` |
| `StreamClock` | Controllable clock (pin, offset) for time-dependent processing and testing | `api/StreamClock.java` |
| `ScheduledCommandCache` | Deduplication cache for scheduled commands with staging/persist/rollback semantics | `api/scheduling/ScheduledCommandCache.java` |
| `InterPartitionCommandSender` | Fire-and-forget command sending to other partitions | `api/InterPartitionCommandSender.java` |

### Data Flow

1. `StreamProcessor.onActorStarted()` → recovers state from snapshot via `DbKeyGenerator` and `StreamProcessorDbState`
2. `ReplayStateMachine.startRecover()` → reads log from snapshot position, replays EVENT records through `RecordProcessor.replay()`, updates key generator and last-processed position
3. On replay completion (leader only) → `StreamProcessor.onRecovered()` → creates `LogStreamWriter`, transitions to PROCESSING phase, opens schedule services
4. `ProcessingStateMachine.tryToReadNextRecord()` → reads COMMAND from log → `processCommand()` → dispatches to matching `RecordProcessor.process()` via `accepts(ValueType)`
5. Follow-up records are buffered in `BufferedProcessingResultBuilder` → written to log via `LogStreamWriter.tryWrite()` → state committed via `ZeebeDbTransaction.commit()` → responses sent via `CommandResponseWriter` → post-commit tasks executed

### Scheduling Infrastructure

- **`ProcessingScheduleServiceImpl`** — Runs scheduled `Task`s on the stream processor actor, writing results to the log. Uses a `PriorityQueue` of timed tasks, checked at a configurable interval.
- **`ExtendedProcessingScheduleServiceImpl`** — Delegates between sync (processor actor) and async (`AsyncProcessingScheduleServiceActor`) execution based on `AsyncTaskGroup`.
- **`AsyncTaskGroup`** — Enum defining actor groups: `ASYNC_PROCESSING` (CPU-bound) and `BATCH_OPERATIONS` (IO-bound).
- Scheduled tasks only execute during the `PROCESSING` phase — they are silently deferred during `REPLAY` or `PAUSED`.

### State Management

- **`DbKeyGenerator`** — Partition-scoped key generation stored in `ZbColumnFamilies.KEY`. Encodes partition ID in high bits via `Protocol.encodePartitionId()`. Validates keys don't exceed `maxKeyValue` to detect key space exhaustion.
- **`DbLastProcessedPositionState`** — Tracks the last successfully processed record position in RocksDB, used for replay recovery.
- **`RecordValues`** — Cache of `UnifiedRecordValue` instances indexed by `ValueType`, used to deserialize log events without allocation.

## Error Handling

`ProcessingStateMachine` implements a multi-phase error escalation:
1. `RecoverableException` → retry after 250ms delay
2. `ExceededBatchRecordSizeException` → retry with reduced batch or call `onProcessingError`
3. General exceptions → roll back transaction, call `RecordProcessor.onProcessingError()`
4. User command errors escalate through: `PROCESSING_FAILED → PROCESSING_ERROR_FAILED → REJECT_FAILED → REJECT_SIMPLE_FAILED → ENDLESS_ERROR_LOOP`
5. User commands get rejected with `PROCESSING_ERROR` rejection type if error handling itself fails
6. `UncommittedStateException` is thrown (and causes partition death) when the DB transaction cannot be committed after records were already written to the log

## Extension Points

- **Add a new `RecordProcessor`**: Implement `RecordProcessor`, register it via `StreamProcessorBuilder.recordProcessors()`. The processor must implement `accepts(ValueType)` to claim record types.
- **Add lifecycle hooks**: Implement `StreamProcessorLifecycleAware`, register via `RecordProcessorContext.addLifecycleListeners()`.
- **Add scheduled tasks**: Use `ProcessingScheduleService.runDelayed/runAt/runAtFixedRate` or their async variants. Tasks produce commands via `TaskResultBuilder`.
- **Add metrics**: Follow the pattern in `impl/metrics/` — use `StreamMetricsDoc` for meter documentation, Micrometer `MeterRegistry` from context.

## Invariants

- Replay only applies EVENTs, never COMMANDs. Processing only processes COMMANDs, never EVENTs.
- State mutations during processing MUST happen within the same RocksDB transaction that writes follow-up records. If the transaction commit fails after log writes, the partition is killed (`UncommittedStateException`).
- `RecordProcessor.replay()` must NOT write to the log or schedule post-commit tasks.
- Follow-up commands within a batch are processed in-transaction before writing; the batch limit (`maxCommandsInBatch`) caps this.
- Keys from other partitions (detected via `Protocol.decodePartitionId`) must not update the local key generator.
- Scheduled tasks only execute in the `PROCESSING` phase — never during `REPLAY`, `PAUSED`, or `INITIAL`.
- `StreamProcessor` runs as a single-threaded `Actor` — all state machine transitions happen on the actor thread. Async schedule services run on separate actors.

## Common Pitfalls

- Forgetting to handle `ExceededBatchRecordSizeException` in `RecordProcessor.onProcessingError()` — the platform retries with a smaller batch, but the processor must produce a valid result.
- Writing to the log from `replay()` — this violates the replay contract and will cause undefined behavior.
- Assuming scheduled tasks run immediately — they are deferred to the next check interval and only run during `PROCESSING`.
- Modifying `StreamProcessorContext` state from outside the actor thread — the `phase` field is volatile, but other fields are not thread-safe.
- Returning `EmptyProcessingResult.INSTANCE` from `process()` causes the record to be skipped silently — use this intentionally.

## Testing

Tests use `StreamPlatform` (`test/.../StreamPlatform.java`) and `StreamPlatformExtension` (JUnit 5) to set up an in-memory `TestLogStream`, `ActorScheduler`, RocksDB-backed `ZeebeDb`, and mock `RecordProcessor`. Write records via `RecordToWrite` helpers and assert processing outcomes. Some tests still use JUnit 4 (`@Rule`) with `junit-vintage-engine`.

Run scoped tests: `./mvnw -pl zeebe/stream-platform -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C`

## Key Files

- `impl/StreamProcessor.java` — Lifecycle orchestrator, Actor, health monitoring
- `impl/ProcessingStateMachine.java` — Command processing loop with batch processing and error escalation
- `impl/ReplayStateMachine.java` — Event replay for state recovery
- `api/RecordProcessor.java` — Engine-facing processing contract
- `impl/StreamProcessorBuilder.java` — Builder pattern for constructing `StreamProcessor` instances