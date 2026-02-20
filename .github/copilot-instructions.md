# Backup Range Tracking Refactor

Read `IMPLEMENTATION.md` for the full plan. This file provides the minimal context and instructions needed to implement each phase.

## What We're Doing

Replacing backup range marker files (empty files in S3/GCS/Azure/local FS) with:
1. Two RocksDB column families (`CHECKPOINTS`, `BACKUP_RANGES`) as the source of truth
2. Per-partition JSON files in the backup store for efficient restore
3. All mutations (create, confirm, delete) routed through the stream processor via the Zeebe log

## Build & Test

- Auto format: `./mvnw license:format spotless:apply -T1C`
- Build quickly: `./mvnw install -Dquickly -T1C`
- Run unit tests for a module: `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C -pl <module>`
- Run integration tests for a module: `./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C -pl <module>`

Key modules (use with `-pl`):
- `zeebe/zb-db` — ColumnFamily abstraction (Phase 0)
- `zeebe/protocol` — `ZbColumnFamilies`, `CheckpointIntent` (Phases 1, 2, 3, 6.5)
- `zeebe/protocol-impl` — `CheckpointRecord` (Phase 1)
- `zeebe/backup` — processors, state, retention, backup store (Phases 2-8)
- `zeebe/restore` — restore manager, range resolver (Phase 6)

Always format before committing: `./mvnw license:format spotless:apply -T1C`

## Architecture Context

### Stream Processor Model

Zeebe uses an event-sourced, single-writer model per partition:
- **Commands** are written to the log by API handlers or internal services (e.g., `BackupServiceImpl` writes `CONFIRM_BACKUP` commands)
- The **stream processor** reads commands from the log and processes them deterministically
- Processing produces **events** (follow-up records) that are also written to the log
- **Appliers** replay events during recovery to rebuild state
- **Async side-effects** (like calling the backup store) are scheduled during processing but executed after the transaction commits
- Only the stream processor may mutate RocksDB state — this is the single-writer guarantee

### Virtual Column Families

Zeebe multiplexes all "column families" into RocksDB's single default CF using an 8-byte big-endian prefix per logical CF. Adding a new CF is just adding an enum entry to `ZbColumnFamilies`. No schema migration needed.

### Checkpoint/Backup Lifecycle

```
CHECKPOINT:CREATE  -->  CHECKPOINT:CREATED  -->  (async backup taken)
                                                       |
                                                       v
                                             CHECKPOINT:CONFIRM_BACKUP  -->  CHECKPOINT:CONFIRMED_BACKUP
```

Three checkpoint types: `MARKER` (no backup), `SCHEDULED_BACKUP`, `MANUAL_BACKUP`.

With this refactor, we add: `CHECKPOINT:DELETE_BACKUP --> CHECKPOINT:BACKUP_DELETED`

## Key Files

### Protocol Layer

- `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/ZbColumnFamilies.java` — add new CF enum entries (140, 141)
- `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/management/CheckpointIntent.java` — add DELETE_BACKUP/BACKUP_DELETED intents
- `zeebe/protocol-impl/src/main/java/io/camunda/zeebe/protocol/impl/record/value/management/CheckpointRecord.java` — extend with numberOfPartitions, brokerVersion fields

### Stream Processor (Backup Module)

- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/processing/CheckpointRecordsProcessor.java` — main entry point; routes CHECKPOINT records to sub-processors. Wire new state classes in `init()`, add dispatch for new intents in `process()` and `replay()`
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/processing/CheckpointConfirmBackupProcessor.java` — processes CONFIRM_BACKUP; currently calls backupManager for range markers. Replace with CF-based range updates
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/processing/CheckpointCreatedEventApplier.java` — applies CREATED events to state during replay
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/processing/CheckpointBackupConfirmedApplier.java` — applies CONFIRMED_BACKUP events during replay
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/processing/state/DbCheckpointState.java` — existing checkpoint state (2 entries in DEFAULT CF). Reference for how state classes work

### Backup Management

- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/management/BackupServiceImpl.java` — backup confirmation (writes CONFIRM_BACKUP commands to log), deletion (currently direct to store — must change to write DELETE_BACKUP commands), range marker management (to be removed)
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/api/BackupStore.java` — store interface. Add metadata JSON methods, eventually remove marker methods

### Retention

- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/retention/BackupRetention.java` — runs as an Actor on a schedule. Currently calls backupStore directly. Must change to write DELETE_BACKUP commands to the log

### Restore

- `zeebe/restore/src/main/java/io/camunda/zeebe/restore/BackupRangeResolver.java` — resolves backup ranges for restore. Currently makes O(P*R + P*B) API calls via markers. Rewrite to read JSON file (1 call per partition)
- `zeebe/restore/src/main/java/io/camunda/zeebe/restore/RestoreManager.java` — restore orchestration

### ColumnFamily Abstraction (zb-db) — Phase 0 done

- `zeebe/zb-db/src/main/java/io/camunda/zeebe/db/ColumnFamily.java` — interface with forward and reverse iteration methods. Reverse: `whileEqualPrefixReverse` (2 overloads), `whileTrueReverse`
- `zeebe/zb-db/src/main/java/io/camunda/zeebe/db/impl/rocksdb/transaction/TransactionalColumnFamily.java` — implementation. `forEachInPrefixReverse()` mirrors `forEachInPrefix()` using `seekForPrev()`/`prev()`
- `zeebe/zb-db/src/main/java/io/camunda/zeebe/db/impl/rocksdb/PrefixReadOptions.java` — read options with `setPrefixSameAsStart(true)`. Works correctly for both forward and reverse iteration; no separate variant needed

### Backup Store Implementations (for JSON metadata methods)

- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/s3/S3BackupStore.java`
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/gcs/GcsBackupStore.java`
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/azure/AzureBackupStore.java`
- `zeebe/backup/src/main/java/io/camunda/zeebe/backup/filesystem/FileSystemBackupStore.java`

## Phase Implementation Notes

### Phase 0: Reverse Iteration

**Done.** Scope: `zeebe/zb-db` module only. No backup-specific code.

`forEachInPrefixReverse()` mirrors `forEachInPrefix()` exactly but uses `iterator.seekForPrev(seekTarget)` and `iterator.prev()` instead of `iterator.seek(seekTarget)` and `iterator.next()`. The `visit()` helper and prefix boundary check (`BufferUtil.startsWith()`) are reused as-is. `PrefixReadOptions` (with `setPrefixSameAsStart(true)`) works unchanged for reverse iteration.

To seek to the end of a prefix, `ColumnFamilyContext.keyWithColumnFamilyEnd(DbKey)` appends 8 `0xFF` bytes to the CF prefix + key bytes.

Test: `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C -pl zeebe/zb-db`

### Phase 1: Extend CheckpointRecord

Add `IntProperty numberOfPartitionsProperty` and `StringProperty brokerVersionProperty` to `CheckpointRecord`. Default values (-1, "") ensure backward compatibility — msgpack silently ignores unknown fields.

Populate them in `BackupServiceImpl.confirmBackup()` from `InProgressBackup.backupDescriptor()`.

Update the `CheckpointRecordValue` interface in `zeebe/protocol/src/main/java/io/camunda/zeebe/protocol/record/value/management/CheckpointRecordValue.java`.

### Phases 2 & 3: Column Families

Follow the pattern in `DbCheckpointState` for how to create a state class backed by a column family. Key steps:
1. Add enum entries to `ZbColumnFamilies` (IDs 140 and 141, both `PARTITION_LOCAL`)
2. Create state classes that take `ZeebeDb` + `TransactionContext` in their constructor
3. Instantiate them in `CheckpointRecordsProcessor.init()` and pass to processors/appliers

Phase 3's deletion logic (range maintenance) depends on Phase 0 for predecessor lookups. The range creation/extension logic does NOT need reverse iteration — only deletion does.

### Phase 4: JSON Sync

Two-file swap for atomicity: write alternately to `backups-a.json` / `backups-b.json`. Each file contains a monotonic `sequenceNumber`. On read, load both, pick higher valid one.

The `BackupStore` interface needs two new methods per slot. Keep it simple — it's just a put/get of a byte array to a known path.

### Phase 6.5: Deletion via Commands

Follow the exact pattern of `CONFIRM_BACKUP`/`CONFIRMED_BACKUP`:
- `BackupServiceImpl.confirmBackup()` shows how to write a command to the log via `logStreamWriter.tryWrite()`
- `CheckpointConfirmBackupProcessor` shows how to process a command and append a follow-up event
- `CheckpointBackupConfirmedApplier` shows how to replay an event

The `DELETE_BACKUP` processor must handle range maintenance (Phase 3's deletion scenarios). The actual `backupStore.delete()` call becomes an async side-effect, not a synchronous operation.

### Phase 7: Retention

`BackupRetention` needs a `LogStreamWriter` reference to write `DELETE_BACKUP` commands. Its pipeline simplifies to: identify deletable backups -> write one command per backup. The stream processor handles the rest.

### Phase 9: Marker Removal

Only after all other phases are complete and validated. Search for `rangeMarker`, `RangeMarker`, `storeRangeMarker`, `deleteRangeMarker`, `BackupRangeMarker`, `BackupRanges` to find all code to remove.

## Working Style

### Use Sub-Agents and Parallelism Aggressively

This is a large codebase. Use the Task tool with sub-agents to maximize throughput:

- **Explore in parallel.** When you need to understand multiple files or modules, launch several `explore` sub-agents concurrently rather than reading files one at a time. For example, to understand both the protocol layer and the backup module, launch two explore agents simultaneously.
- **Implement independent changes in parallel.** When a phase touches multiple independent modules (e.g., adding a protocol enum AND creating a state class), use `general` sub-agents to draft changes in parallel.
- **Research before coding.** Before modifying a file, launch an `explore` agent to find all usages and patterns in the codebase. This avoids missed call sites and inconsistent patterns.
- **Run builds and searches concurrently.** While a Maven build is running, use that time to explore the next piece of work with a sub-agent. Don't wait idle for builds.
- **Delegate Maven to a sub-agent.** Use a `general` sub-agent for Maven operations (formatting, building, running tests) so you can continue working on other tasks in the meantime.
- **Batch file reads.** When you need to read 3+ files, read them all in a single parallel call rather than sequentially.

Rule of thumb: if two things don't depend on each other, do them at the same time.

## Testing Guidelines

- Read `docs/testing.md` before writing tests
- Unit tests go next to the code they test
- Integration tests use the `*IT.java` naming convention
- For backup store tests, each implementation has a testkit — check existing test patterns in the store modules
- The `CheckpointRecordsProcessor` tests in `zeebe/backup/src/test/` show how to test stream processors

