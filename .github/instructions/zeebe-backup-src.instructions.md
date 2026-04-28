```yaml
---
applyTo: "zeebe/backup/src/**"
---
```
# Zeebe Backup Module

## Purpose

This module implements per-partition backup and restore for the Zeebe engine. It handles checkpoint creation, backup lifecycle management (take/confirm/delete), scheduled checkpoints, backup retention, and metadata synchronization to external backup stores. The module is consumed by the broker to persist RocksDB snapshots and journal segments to pluggable storage backends.

## Architecture

The module is organized into six packages, each with a distinct role:

### `api/` — Public Interfaces
Contracts consumed by the broker and backup store implementations. Key types:
- `BackupManager` — partition-scoped backup orchestration (take, status, list, delete, sync)
- `BackupStore` — pluggable storage backend (save, restore, delete, metadata sync)
- `Backup` — composite of `BackupIdentifier` + `BackupDescriptor` + snapshot `NamedFileSet` + segment `NamedFileSet`
- `BackupIdentifier` — unique triple: `(nodeId, partitionId, checkpointId)`
- `BackupIdentifierWildcard` — sealed `CheckpointPattern` hierarchy (`Any`, `Prefix`, `Exact`) for flexible querying
- `CheckpointListener` — observer for checkpoint creation events
- `BackupRange` / `BackupRangeStatus` — contiguous backup range tracking

### `management/` — Backup Service Layer
`BackupService` extends `Actor` and implements `BackupManager`. It delegates to `BackupServiceImpl` for:
1. Snapshot reservation (with retry up to 3 attempts across available snapshots)
2. Snapshot file collection from `PersistedSnapshotStore`
3. Journal segment collection via `JournalInfoProvider.getTailSegments()`
4. Saving to `BackupStore` and writing `CONFIRM_BACKUP` command to the log
5. `BackupMetadataSyncer` — serializes checkpoint metadata + ranges to JSON, persists to backup store

`NoopBackupManager` is installed when no backup store is configured; all operations fail with a descriptive error.

### `processing/` — Stream Processor Integration
`CheckpointRecordsProcessor` implements `RecordProcessor` for `ValueType.CHECKPOINT`. It follows the Zeebe processor/applier pattern:

**Processors** (handle COMMANDs, produce EVENTs):
- `CheckpointCreateProcessor` — processes `CREATE` commands; creates checkpoint, triggers backup (or marks failed if scaling in progress)
- `CheckpointConfirmBackupProcessor` — processes `CONFIRM_BACKUP`; updates range state and triggers metadata sync
- `CheckpointDeleteBackupProcessor` — processes `DELETE_BACKUP`; removes checkpoint, maintains ranges, triggers async deletion

**Appliers** (apply EVENTs to state, used in both processing and replay):
- `CheckpointCreatedEventApplier` — updates `DbCheckpointState`, adds marker metadata, notifies listeners
- `CheckpointBackupConfirmedApplier` — updates backup info, extends/creates ranges based on log position contiguity
- `CheckpointBackupDeletedApplier` — removes checkpoint metadata, handles range split/shrink/delete scenarios

### `processing/state/` — RocksDB State
Three column families back the checkpoint state:
- `DbCheckpointState` — `DEFAULT` CF with two keys (`"checkpoint"`, `"backup"`) storing latest checkpoint/backup info as `CheckpointInfo` (MsgPack)
- `DbCheckpointMetadataState` — `CHECKPOINTS` CF keyed by checkpoint ID, storing `CheckpointMetadataValue` (position, timestamp, type, firstLogPosition)
- `DbBackupRangeState` — `BACKUP_RANGES` CF keyed by range start, value is range end. Supports `findRangeContaining()`, `splitRange()`, `updateRangeStart/End()`

### `client/api/` — Gateway-Side Client
`BackupRequestHandler` implements `BackupApi`, broadcasting requests to all partitions via `BrokerClient`:
- Aggregates per-partition responses into cluster-wide `BackupStatus` with `State` enum (COMPLETED, FAILED, IN_PROGRESS, INCOMPLETE, DELETED, DOES_NOT_EXIST)
- Uses `CheckpointIdGenerator` for timestamp-based checkpoint IDs
- Request types: `BrokerBackupRequest`, `BackupStatusRequest`, `BackupListRequest`, `BackupDeleteRequest`, `BrokerCheckpointStateRequest`, `BrokerBackupRangesRequest`, `BrokerMetadataSyncRequest`

### `schedule/` — Checkpoint Scheduling
`CheckpointScheduler` extends `Actor` and supports dual schedules:
- `Schedule` is a sealed interface with `CronSchedule` (Spring CRON), `IntervalSchedule` (ISO8601 duration), `NoneSchedule`
- When both checkpoint and backup schedules are configured, it merges them: if a backup is due within 2 seconds of a checkpoint, only the backup is triggered (since backups also create checkpoints)
- Uses `ExponentialBackoff` for error recovery

### `retention/` — Backup Retention
`BackupRetention` extends `Actor` and periodically deletes old backups:
- Runs on a configurable `Schedule`, identifies backups outside the retention window
- Routes deletion through the stream processor via `DELETE_BACKUP` broker requests (not direct store deletion)
- Deduplicates by checkpoint ID since one command handles all node copies

### `common/` — Shared Implementations
Record implementations of API interfaces: `BackupImpl`, `BackupIdentifierImpl`, `BackupDescriptorImpl`, `BackupStatusImpl`, `NamedFileSetImpl`, `BackupIdentifierWildcardImpl`.
- `BackupMetadata` — Jackson-serializable JSON manifest (`version`, `partitionId`, `lastUpdated`, `checkpoints[]`, `ranges[]`)
- `CheckpointIdGenerator` — generates checkpoint IDs from `epoch millis + offset`
- `BackupDescriptorImpl` has a `CheckpointTypeDeserializer` for backwards compatibility with pre-8.9 backups

### `metrics/` — Micrometer Instrumentation
- `BackupManagerMetrics` — operation counters, latency timers, in-progress gauges (take/status/list/delete)
- `CheckpointMetrics` — checkpoint creation/ignore counters, position/ID gauges
- `BackupMetadataSyncerMetrics` — sync duration, success/failure counters, serialized size gauge
- `SchedulerMetrics` — last/next execution timestamps, checkpoint IDs
- `RetentionMetrics` — per-partition earliest backup ID, backups deleted count

## Key Design Patterns

- **Actor model**: `BackupService`, `CheckpointScheduler`, `BackupRetention` all extend `Actor` for single-threaded cooperative execution. Use `ActorFuture` for async chaining, never block.
- **Processor/Applier separation**: Processors handle COMMANDs and produce EVENTs; appliers mutate state. Appliers are shared between processing and replay paths. Never mutate state from a processor directly.
- **Post-commit tasks**: Side effects (metadata sync, store deletion) are appended via `resultBuilder.appendPostCommitTask()` to execute after the transaction commits.
- **Sealed interfaces**: `Schedule`, `CheckpointPattern`, `SnapshotValidation` use sealed hierarchies for exhaustive matching.
- **Wildcard querying**: `BackupIdentifierWildcard` with `CheckpointPattern` (Any/Prefix/Exact) enables flexible backup lookups.
- **Range contiguity**: `CheckpointBackupConfirmedApplier` extends ranges when `firstLogPosition <= latestBackupPosition + 1`, otherwise starts a new range.

## Extension Points

- **New backup store**: Implement `BackupStore` interface. Implementations exist in sibling modules (`zeebe/backup-stores/`).
- **New checkpoint intent**: Add intent to `CheckpointIntent` enum in `zeebe/protocol`, add processor in `processing/`, add applier, register in `CheckpointRecordsProcessor.process()` and `replay()`.
- **New metrics**: Follow `BackupManagerMetricsDoc` pattern — create an enum implementing `ExtendedMeterDocumentation`, use `MeterRegistry` for registration.
- **New schedule type**: Add a new record implementing sealed `Schedule` interface.

## Invariants

- Appliers must be idempotent and replayable — they execute during both processing and log replay.
- Never delete a backup directly from the store in processing code; always route through `DELETE_BACKUP` commands so state (CHECKPOINTS CF, BACKUP_RANGES CF) stays consistent.
- `CheckpointState.NO_CHECKPOINT` (`-1L`) is the sentinel for "no checkpoint exists" — always check against it.
- Snapshot validation requires all positions (`processedPosition`, `lastFollowupEventPosition`, `maxExportedPosition`) to be strictly less than `checkpointPosition`.
- On leader change, `onRecovered()` marks in-progress backups as failed — the new leader does not continue previous leader's backup.
- `BackupServiceImpl` is package-private; `BackupService` (the `Actor`) is the public entry point.

## Common Pitfalls

- Do not call `BackupStore` methods from the stream processor thread — use `CompletableFuture.whenCompleteAsync(callback, concurrencyControl)` to marshal back to the actor thread.
- When modifying range logic in `CheckpointBackupConfirmedApplier` or `CheckpointBackupDeletedApplier`, ensure all edge cases are covered: single-entry range, start deletion, end deletion, middle split.
- `DbCheckpointMetadataState` uses `whileTrue`/`whileTrueReverse` iteration with `DbLong` keys — always set the key before iteration and remember iteration starts at the provided key (inclusive).
- The `BrokerBackupRequest.getRequestWriter()` returns `null` — the SBE-encoded `BackupRequest` is written directly via `write()`. Do not add a `BufferWriter`.

## Key Files

| File | Role |
|------|------|
| `api/BackupStore.java` | Storage backend contract — all store implementations must implement this |
| `management/BackupService.java` | Public `BackupManager` implementation, Actor-based entry point |
| `processing/CheckpointRecordsProcessor.java` | Stream processor for CHECKPOINT records, wires processors and appliers |
| `processing/state/DbBackupRangeState.java` | BACKUP_RANGES column family — contiguous range tracking |
| `client/api/BackupRequestHandler.java` | Gateway-side client, broadcasts to all partitions and aggregates responses |
| `schedule/CheckpointScheduler.java` | Dual-schedule checkpoint/backup triggering with CRON and interval support |
| `retention/BackupRetention.java` | Periodic backup cleanup with retention window |
| `common/BackupMetadata.java` | JSON manifest synced to backup store |

## Testing

Run scoped tests:
```
./mvnw -pl zeebe/backup -am test -DskipITs -DskipChecks -Dtest=<TestClass> -T1C
```

Tests use `@ExtendWith(MockitoExtension.class)` with mocked `BackupStore`, `ConcurrencyControl`, and state classes. `ProcessingStateExtension` from `zeebe/engine` can be used for state tests against an in-memory RocksDB.