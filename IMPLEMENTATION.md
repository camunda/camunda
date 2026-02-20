# Backup Range Tracking Refactor

**Context:** `CheckpointRecord` only carries `checkpointId`, `checkpointPosition`, `checkpointType`, and `firstLogPosition`. It does not carry `numberOfPartitions` or `brokerVersion`, which are in the `BackupDescriptor`. The record will need extending (Phase 1). Checkpoint ids are opaque, they are monotonically increasing but not contiguous.
Backups that are contiguous when previousBackup.checkpointPosition <= thisBackup.firstLogPosition. Multiple contiguous backups form a contiguous range.

**Current state:** Backup ranges are tracked via empty marker files in the backup store (S3/GCS/Azure/Filesystem). Both user-initiated deletion and retention deletion bypass the Zeebe log entirely, calling `backupStore.delete()` directly. The restore application makes O(P*R + P*B) API calls to resolve backup ranges.

**Goal:** Replace marker files with RocksDB column families as the source of truth, route all mutations through the stream processor for consistency, and sync per-partition JSON files to the backup store for efficient restore.

---

## Refined Refactor Plan: Two Column Families (Checkpoints + Ranges)

### Data Model

**Column Family 1: `CHECKPOINTS`** — full history of all checkpoints (PARTITION_LOCAL)

|          Key           |                                                                                           Value                                                                                            |
|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `DbLong(checkpointId)` | `CheckpointMetadataValue`: checkpointPosition, checkpointTimestamp, checkpointType, firstLogPosition (-1 for markers), numberOfPartitions (-1 for markers), brokerVersion ("" for markers) |

- Written on every `CHECKPOINT:CREATED` event (all types including MARKER)
- Enriched on `CHECKPOINT:CONFIRMED_BACKUP` (adds firstLogPosition, numberOfPartitions, brokerVersion)
- Deleted when retention removes a backup

**Column Family 2: `BACKUP_RANGES`** — pre-computed contiguous backup ranges (PARTITION_LOCAL)

|             Key             |           Value           |
|-----------------------------|---------------------------|
| `DbLong(startCheckpointId)` | `DbLong(endCheckpointId)` |

- Ranges only track backup-type checkpoints (not MARKERs)
- Updated incrementally: extend end on backup confirmation, or insert new range when a gap is detected
- Updated on retention (advance start or delete range) and on user-initiated deletion (split or shrink)

**Per-partition JSON file in backup store** — synced on every backup confirmation and deletion:

```json
{
  "partitionId": 1,
  "lastUpdated": "2026-02-20T10:00:00Z",
  "checkpoints": [
    {
      "checkpointId": 1234,
      "checkpointPosition": 5678,
      "checkpointTimestamp": "2026-02-20T09:00:00Z",
      "checkpointType": "SCHEDULED_BACKUP",
      "firstLogPosition": 5000,
      "numberOfPartitions": 3,
      "brokerVersion": "8.9.0"
    }
  ],
  "ranges": [
    { "start": 1234, "end": 5678 }
  ]
}
```

### Phase Breakdown

---

#### Phase 0: Reverse Iteration Support (Prerequisite) — DONE ✅

Implemented in `ede1b8f5d0e` (feat: add reverse iteration support to ColumnFamily abstraction).

**Goal:** Add `seekForPrev`/`prev` reverse iteration to the `ColumnFamily` abstraction. This is a prerequisite for Phase 3's range maintenance during backup deletion, where finding the predecessor checkpoint requires backward iteration.

**What was done:**

1. **Added 3 reverse iteration methods to `ColumnFamily.java` interface:**
   - `whileEqualPrefixReverse(DbKey keyPrefix, KeyValuePairVisitor)` — iterate backward within a prefix from the last entry
   - `whileEqualPrefixReverse(DbKey keyPrefix, KeyType startAtKey, KeyValuePairVisitor)` — iterate backward within a prefix from a given key
   - `whileTrueReverse(KeyType startAtKey, KeyValuePairVisitor)` — iterate backward across the whole CF (the plan listed this as optional; implemented it since it was trivial and useful)
2. **Implemented `forEachInPrefixReverse()` in `TransactionalColumnFamily.java`** — mirrors `forEachInPrefix()` using `seekForPrev()`/`prev()`. Reuses the existing `visit()` helper and `startsWith()` prefix check.
3. **Added `keyWithColumnFamilyEnd(DbKey)` to `ColumnFamilyContext.java`** — computes a seek target past the last key in a prefix by appending 8 `0xFF` bytes. Used when no explicit `startAtKey` is provided.
4. **`PrefixReadOptions` unchanged** — the existing `setPrefixSameAsStart(true)` works correctly with `seekForPrev()`. No separate `ReversePrefixReadOptions` was needed. Validated by 11 passing tests.
5. **11 unit tests added** across two existing test files:
   - `ColumnFamilyTest.java` (4 tests): reverse with early stop, missing key, full reverse, empty CF
   - `DbCompositeKeyColumnFamilyTest.java` (7 tests): reverse within prefix (full, start-at, missing start-at, early stop, missing prefix, single entry, prefix boundary correctness)

---

#### Phase 1: Extend CheckpointRecord — DONE ✅

**Goal:** Carry `numberOfPartitions` and `brokerVersion` through the log so the stream processor has all metadata at confirmation time.

**Note on broker version:** `RecordMetadata` already carries `brokerVersion` (the version of the broker writing the record). Since the broker that writes the `CONFIRMED_BACKUP` event is the same broker that took the backup, we use `Record.getBrokerVersion()` from record metadata rather than adding a separate field to `CheckpointRecord`. Later phases that need the broker version (e.g., Phase 2's `CheckpointMetadataValue`) should source it from the record's metadata via `TypedRecord.getBrokerVersion()`.

**What was done:**

1. **Added `numberOfPartitions` field to `CheckpointRecord`** (`zeebe/protocol-impl/.../CheckpointRecord.java`)
   - Added `IntegerProperty numberOfPartitionsProperty` (key: `"numberOfPartitions"`, default: -1)
   - Updated `super(4)` to `super(5)`, added `declareProperty()` call
   - Added getter/setter: `getNumberOfPartitions()`/`setNumberOfPartitions()`
2. **Updated `CheckpointRecordValue` interface** (`zeebe/protocol/.../CheckpointRecordValue.java`)
   - Added `int getNumberOfPartitions()` with Javadoc
3. **Populated field in `BackupServiceImpl.confirmBackup()`** (`zeebe/backup/.../BackupServiceImpl.java`)
   - Set `.setNumberOfPartitions(inProgressBackup.backupDescriptor().numberOfPartitions())`
4. **Backward compatibility** — Default value (-1 for numberOfPartitions) ensures old records deserialize safely. Msgpack ignores unknown fields in older records. The `ImmutableCheckpointRecordValue` (auto-generated by Immutables annotation processor) picks up the new method automatically.
5. **Updated golden file** (`CheckpointRecord.golden`) and **JSON serialization tests** (`JsonSerializableToJsonTest.java`) — both test cases updated with the new field.

---

#### Phase 2: Checkpoints Column Family

**Goal:** Store every checkpoint event in a dedicated column family with full metadata.

**Tasks:**

1. **Add `CHECKPOINTS(140, PARTITION_LOCAL)` to `ZbColumnFamilies`** (`zeebe/protocol/.../ZbColumnFamilies.java`)

2. **Create `CheckpointMetadataValue`** (new class in `zeebe/backup/.../processing/state/`)

   - Extends `UnpackedObject` implements `DbValue`
   - Fields: `checkpointPosition` (long), `checkpointTimestamp` (long, epoch millis), `checkpointType` (enum ordinal as int), `firstLogPosition` (long, -1 if not set), `numberOfPartitions` (int, -1 if not set), `brokerVersion` (String, "" if not set)
3. **Create `DbCheckpointMetadataState`** (new class in `zeebe/backup/.../processing/state/`)
   - Uses `ColumnFamily<DbLong, CheckpointMetadataValue>` on `CHECKPOINTS`
   - Methods:
     - `addCheckpoint(checkpointId, position, timestamp, type)` — inserts entry for CREATED events
     - `enrichWithBackupInfo(checkpointId, firstLogPosition, numberOfPartitions, brokerVersion)` — updates entry on CONFIRMED_BACKUP
     - `removeCheckpoint(checkpointId)` — deletes entry
     - `getCheckpoint(checkpointId)` — point lookup
     - `getAllCheckpoints()` — iterate all entries (for JSON sync)
     - `getCheckpointsInRange(startId, endId)` — prefix/range scan
     - `isEmpty()` — for migration detection
4. **Modify `CheckpointCreatedEventApplier`** (`zeebe/backup/.../CheckpointCreatedEventApplier.java:13`)
   - After existing state update, call `checkpointMetadataState.addCheckpoint(...)` with data from the `CheckpointRecord` and `record.getTimestamp()`
   - This runs for ALL checkpoint types (MARKER, SCHEDULED_BACKUP, MANUAL_BACKUP)
5. **Modify `CheckpointBackupConfirmedApplier`** (`zeebe/backup/.../CheckpointBackupConfirmedApplier.java:13`)
   - After existing state update, call `checkpointMetadataState.enrichWithBackupInfo(...)` with data from the `CheckpointRecord`
   - This only runs for confirmed backups
6. **Wire into `CheckpointRecordsProcessor.init()`** (`zeebe/backup/.../CheckpointRecordsProcessor.java:76`)
   - Instantiate `DbCheckpointMetadataState` from `zeebeDb` + `transactionContext`
   - Pass it to both appliers

---

#### Phase 3: Backup Ranges Column Family

**Goal:** Maintain pre-computed contiguous ranges that can be queried without scanning all checkpoints.

**Tasks:**

1. **Add `BACKUP_RANGES(141, PARTITION_LOCAL)` to `ZbColumnFamilies`**

2. **Create `DbBackupRangeState`** (new class in `zeebe/backup/.../processing/state/`)

   - Uses `ColumnFamily<DbLong, DbLong>` on `BACKUP_RANGES` (key = startCheckpointId, value = endCheckpointId)
   - Methods:
     - `startNewRange(checkpointId)` — insert `(checkpointId, checkpointId)`
     - `extendRange(startCheckpointId, newEndCheckpointId)` — update value to newEndCheckpointId
     - `findRangeContaining(checkpointId)` — iterate ranges to find the one where `start <= checkpointId <= end`
     - `getAllRanges()` — iterate all entries (for JSON sync and API)
     - `deleteRange(startCheckpointId)` — remove an entry
     - `advanceRangeStart(oldStart, newStart, endCheckpointId)` — delete old entry, insert (newStart, endCheckpointId) for retention
     - `shrinkRangeEnd(startCheckpointId, newEndCheckpointId)` — update value to newEndCheckpointId when deleting the last backup in a range
     - `splitRange(oldStart, oldEnd, deletedCheckpointId, predecessorId, successorId)` — delete old entry, insert two sub-ranges: (oldStart, predecessorId) and (successorId, oldEnd)
3. **Range maintenance on backup deletion** — requires Phase 0's reverse iteration support

   When a backup is deleted, the range containing it must be updated. The behavior depends on where in the range the deleted checkpoint falls:

   |                       Scenario                        | Predecessor needed? |                                                                Action                                                                |
   |-------------------------------------------------------|---------------------|--------------------------------------------------------------------------------------------------------------------------------------|
   | Only checkpoint in range                              | No                  | `deleteRange(start)`                                                                                                                 |
   | Start of range (start == deletedId, end != deletedId) | No                  | Forward-iterate CHECKPOINTS CF to find successor → `advanceRangeStart(oldStart, successor, end)`                                     |
   | End of range (end == deletedId, start != deletedId)   | **Yes**             | Reverse-iterate CHECKPOINTS CF via `seekForPrev(deletedId - 1)` to find predecessor → `shrinkRangeEnd(start, predecessor)`           |
   | Middle of range                                       | **Yes**             | Forward-iterate to find successor, reverse-iterate to find predecessor → `splitRange(start, end, deletedId, predecessor, successor)` |

   The predecessor lookup uses `DbCheckpointMetadataState.findPredecessorBackupCheckpoint(checkpointId)`:
   - Seeks backward from `checkpointId - 1` in the CHECKPOINTS CF using `whileEqualPrefixReverse`
   - Skips MARKER-type checkpoints (only backup-type checkpoints form ranges)
   - Returns the first backup-type checkpoint found, or empty if none exists

   The successor lookup uses the existing forward iteration:
   - Seeks forward from `checkpointId + 1` in the CHECKPOINTS CF using `whileEqualPrefix`
   - Skips MARKER-type checkpoints
   - Returns the first backup-type checkpoint found, or empty if none exists

4. **Move range logic from `CheckpointConfirmBackupProcessor` into the state layer**

   - Currently, the processor calls `backupManager.extendRange()` / `backupManager.startNewRange()` which update marker files asynchronously
   - Replace with: call `dbBackupRangeState.extendRange()` / `dbBackupRangeState.startNewRange()` synchronously within the same transaction as the CONFIRMED_BACKUP event application
   - The contiguity condition remains the same: `firstLogPosition <= latestBackupPosition + 1`
5. **Wire into `CheckpointRecordsProcessor.init()`**
   - Instantiate `DbBackupRangeState`
   - Pass to `CheckpointConfirmBackupProcessor`

---

#### Phase 4: JSON Sync Infrastructure

**Goal:** Define serialization format and BackupStore integration for syncing the per-partition JSON file.

**Tasks:**

1. **Define `BackupMetadataManifest` model** (new record in `zeebe/backup/.../common/`)
   - `int partitionId`
   - `Instant lastUpdated`
   - `List<CheckpointEntry> checkpoints` — sorted by checkpointId
   - `List<RangeEntry> ranges` — sorted by startCheckpointId
   - Inner records: `CheckpointEntry(long checkpointId, long checkpointPosition, Instant checkpointTimestamp, String checkpointType, long firstLogPosition, int numberOfPartitions, String brokerVersion)`, `RangeEntry(long start, long end)`
2. **Jackson serialization** — use ObjectMapper (already used in existing manifest code in backup stores)
3. **Add to `BackupStore` interface** (`zeebe/backup/.../api/BackupStore.java`)
   - `CompletableFuture<Void> storeBackupMetadata(int partitionId, String slot, byte[] content)` — write to a specific slot ("a" or "b")
   - `CompletableFuture<Optional<byte[]>> loadBackupMetadata(int partitionId, String slot)` — read from a specific slot
   - Paths: `{basePath}/metadata/{partitionId}/backups-a.json` and `{basePath}/metadata/{partitionId}/backups-b.json`
4. **Implement in all 4 store backends** (S3, GCS, Azure, Filesystem)
   - Simple put/get operations — a single object/file write and read per slot
5. **Create `BackupMetadataSyncer`** (new class)
   - Takes `DbCheckpointMetadataState`, `DbBackupRangeState`, and `BackupStore`
   - Maintains a monotonic sequence number (persisted in the JSON content)
   - `sync(partitionId)`:
     1. Reads both CFs, serializes to JSON with the next sequence number
     2. Determines which slot to write to (alternates: if last write was "a", write to "b" and vice versa; tracks via internal state)
     3. Writes to the target slot
   - `load(partitionId)`:
     1. Reads both `backups-a.json` and `backups-b.json`
     2. Parses both, picks the one with the higher valid sequence number
     3. If one file is missing or corrupt, uses the other
     4. If both are missing, returns empty (fresh deployment or pre-migration)
   - Handles errors with logging + retry on next sync opportunity

---

#### Phase 5: Trigger JSON Sync

**Goal:** Keep the JSON file up-to-date after every mutation.

**Tasks:**

1. **Sync after backup confirmation** — in `BackupServiceImpl`, after the `CONFIRM_BACKUP` command is written and processed, trigger `syncer.sync(partitionId)` asynchronously
   - This is the primary sync point — happens on every successful backup
2. **Sync on leader election** — in `CheckpointRecordsProcessor.onRecovered()`, trigger a sync
   - Catches up any missed syncs from leader failovers
3. **Sync after backup deletion** — triggered as an async side-effect by `CheckpointDeleteBackupProcessor` after processing each `DELETE_BACKUP` command (covers both retention and user-initiated deletion; see Phase 6.5)
4. **Sync on errors/recovery** — if any sync fails, the next sync opportunity (leader election or next mutation) will re-sync from the authoritative CF state

---

#### Phase 6: Update Restore to Use JSON Files

**Goal:** Rewrite restore to read the per-partition JSON file instead of querying markers + individual backups.

**Tasks:**

1. **Create `BackupMetadataReader`** (new class in `zeebe/restore/`)
   - Reads per-partition JSON files from the backup store via `syncer.load(partitionId)` (uses the two-file swap reader)
   - Deserializes into `BackupMetadataManifest`
2. **Rewrite `BackupRangeResolver.getInformationPerPartition()`** (`zeebe/restore/.../BackupRangeResolver.java:108`)
   - Replace the current flow (list markers -> query boundaries -> list all backups) with:
     1. Read JSON file (1 API call per partition)
     2. Look up ranges directly from `manifest.ranges()` — no computation needed
     3. For each range, look up boundary checkpoint timestamps from `manifest.checkpoints()` (local lookup)
     4. Find range covering the requested time interval (local computation)
     5. Extract all checkpoints within that range from `manifest.checkpoints()` (local filter)
     6. Find safe start checkpoint using `checkpointPosition <= exportedPosition` (local computation)
     7. Validate log position chain (local computation)
   - **Reduction: from O(P * R + P * B) API calls to exactly P API calls**
3. **Rewrite `RestoreManager.verifyBackupIdsAreContinuous()`** (`zeebe/restore/.../RestoreManager.java:355`)
   - Use ranges from the JSON file instead of querying markers
   - Remove the redundant `store.rangeMarkers()` call
4. **Update `RestoreManager.restoreTimeRange()`** (`zeebe/restore/.../RestoreManager.java:156`)
   - Use JSON file for backup discovery instead of wildcard `store.list()`
5. **Update tests** — rewrite `BackupRangeResolverTest`, `RestoreManagerTest`

---

#### Phase 6.5: Backup Deletion via Commands

**Goal:** Route all backup deletion (user-initiated and retention) through the stream processor via new `DELETE_BACKUP`/`BACKUP_DELETED` command-event intents. This ensures deletions are replicated to followers, replayed after restart, and have deterministic ordering relative to other stream processor operations.

**Context:** Currently, both user-initiated deletion (`BackupServiceImpl.deleteBackup()`, lines 321-352) and retention (`BackupRetention.deleteBackups()`, lines 398-430) call `backupStore.delete()` directly, completely bypassing the Zeebe log. Deletions are not replicated, not replayed, and have no ordering guarantees.

**Tasks:**

1. **Add new intents to `CheckpointIntent.java`** (`zeebe/protocol/.../intent/management/CheckpointIntent.java`)
   - `DELETE_BACKUP(5)` — command intent (add to existing enum, value 5)
   - `BACKUP_DELETED(6)` — event intent (add to existing enum, value 6)
   - Update `isEvent()` to return `true` for `BACKUP_DELETED`
2. **Create `CheckpointDeleteBackupProcessor`** (new class in `zeebe/backup/.../processing/`)
   - Follows the pattern of `CheckpointConfirmBackupProcessor`
   - Receives `TypedRecord<CheckpointRecord>` with `DELETE_BACKUP` intent
   - Processing logic:
     1. Look up the checkpoint in `DbCheckpointMetadataState` — reject if not found
     2. Determine which range contains this checkpoint via `DbBackupRangeState.findRangeContaining()`
     3. Update the range using the scenario-based logic from Phase 3 (advance start / shrink end / split / delete range) — this is where Phase 0's reverse iteration is needed for predecessor lookups
     4. Remove the checkpoint entry from `DbCheckpointMetadataState`
     5. Append `BACKUP_DELETED` follow-up event to the result
     6. Schedule async side-effects: `backupStore.delete(backupId)` and `syncer.sync(partitionId)` — these happen after the transaction commits, similar to how `CheckpointConfirmBackupProcessor` schedules async backup operations
3. **Create `CheckpointBackupDeletedApplier`** (new class in `zeebe/backup/.../processing/`)
   - Applied during replay of `BACKUP_DELETED` events
   - Performs the same state mutations as the processor: remove checkpoint from CF, update ranges
   - Does NOT re-trigger the async side-effects (backup store deletion and JSON sync) — these are idempotent but should be handled by the sync-on-leader-election mechanism
4. **Wire into `CheckpointRecordsProcessor`** (`zeebe/backup/.../CheckpointRecordsProcessor.java`)
   - Add `DELETE_BACKUP` to `process()` dispatch (line 140-155) — route to `CheckpointDeleteBackupProcessor`
   - Add `BACKUP_DELETED` to `replay()` dispatch (line 121-138) — route to `CheckpointBackupDeletedApplier`
5. **Update `BackupServiceImpl.deleteBackup()`** (`zeebe/backup/.../BackupServiceImpl.java:321`)
   - Replace the direct `backupStore.delete()` call with writing a `DELETE_BACKUP` command to the log
   - Follow the same pattern as `confirmBackup()` (lines 203-237): use `logStreamWriter.tryWrite()` with `RecordMetadata(COMMAND, CHECKPOINT, DELETE_BACKUP)` + `CheckpointRecord` carrying the checkpoint ID
   - The `CheckpointRecord` for deletion only needs `checkpointId` populated — other fields can use defaults
6. **Handle in-progress backup deletion** — if the backup being deleted is `IN_PROGRESS`, the processor should first mark it as failed via `backupStore.markFailed()` before proceeding with deletion (preserving current behavior from `BackupServiceImpl.deleteBackupIfExists()`)
7. **Update tests** — create `CheckpointDeleteBackupProcessorTest`, `CheckpointBackupDeletedApplierTest`, update `BackupServiceImplTest`

---

#### Phase 7: Update Retention

**Goal:** Remove retention's dependency on marker files and route all retention deletions through stream processor commands.

**Tasks:**

1. **Route retention deletions through `DELETE_BACKUP` commands** (depends on Phase 6.5)
   - Currently, retention deletes backups directly via `BackupStore.delete()` and manipulates markers
   - New approach: retention identifies backups to delete, then writes a `DELETE_BACKUP` command to the log for each
   - The stream processor processes each command: removes the checkpoint entry from CF, updates ranges, triggers async backup store deletion and JSON sync
   - Retention no longer directly calls `backupStore.delete()`, `backupStore.storeRangeMarker()`, or `backupStore.deleteRangeMarker()`
2. **Simplify `BackupRetention` pipeline** (`zeebe/backup/.../retention/BackupRetention.java`)
   - Remove `enrichContextWithMarkers` step entirely
   - Remove `resetRangeStart` step (no more Start markers)
   - Remove `deleteMarkers` step
   - Remove `deleteBackups` step (no more direct store deletion)
   - Pipeline becomes: `retrieveBackups -> processBackups -> writeDeleteCommands`
   - Each `writeDeleteCommands` step writes one `DELETE_BACKUP` command per backup to be deleted
3. **Provide `logStreamWriter` to `BackupRetention`**
   - Retention needs access to the `LogStreamWriter` to write commands
   - Follow the same pattern as `BackupServiceImpl` which already has a `logStreamWriter` reference
   - Wire this in `BackupServiceImpl` or the actor that creates `BackupRetention`
4. **Update `RetentionContext`** — remove marker-related fields (`rangeMarkers`, `deletableMarkers`, etc.)
5. **Update `RetentionTest` and acceptance tests**

---

#### Phase 8: Update Backup Status API

**Goal:** Expose range information from the new CFs instead of marker files.

**Tasks:**

1. **Rewrite `BackupServiceImpl.getBackupRangeStatus()`** (`zeebe/backup/.../BackupServiceImpl.java:394`)
   - Read ranges from `DbBackupRangeState.getAllRanges()`
   - For each range, look up boundary checkpoints from `DbCheckpointMetadataState`
   - Build `BackupRangeStatus` objects from the CF data
   - No more marker-based `BackupRanges.fromMarkers()` computation
2. **Update `BackupApiRequestHandler.handleQueryRangesRequest()`** if needed

---

#### Phase 9: Remove Marker Code

**Goal:** Clean up all marker-related code.

**Tasks:**

1. **Remove from `BackupStore` interface**: `rangeMarkers()`, `storeRangeMarker()`, `deleteRangeMarker()`
2. **Remove from all 4 store implementations** (S3, GCS, Azure, Filesystem)
3. **Delete `BackupRangeMarker`**, `BackupRanges`
4. **Remove from `BackupServiceImpl`**: `startNewRange()`, `extendRange()` methods that call the store
5. **Remove from `BackupManager` interface**: `extendRange()`, `startNewRange()` (range management now happens in the stream processor)
6. **Remove from `BackupService`**: corresponding actor-delegate methods
7. **Clean up tests**: delete `BackupRangesTest`, `StoringRangeMarkers` testkit, update `BackupServiceImplTest`, `BackupRangeTrackingIT`

---

#### Phase 10: Migration

**Goal:** Handle upgrades from existing deployments.

**Tasks:**

1. **On-startup migration check** — in `CheckpointRecordsProcessor.init()` or `onRecovered()`:
   - If `DbCheckpointMetadataState.isEmpty()` and other CFs have data (existing deployment):
     - Query `store.list(wildcardForPartition)` to get all completed backups
     - For each, write an entry to the checkpoints CF
     - Query `store.rangeMarkers(partitionId)` to get existing ranges
     - Convert markers to range CF entries via `BackupRanges.fromMarkers()`
     - Sync JSON file
2. **One-time cost is acceptable** — runs once per partition on upgrade
3. **Marker cleanup** — after migration, optionally delete old marker files from the store (or leave as orphans)
4. **Integration test** — start with markers, upgrade, verify CFs populated and JSON synced

---

### Dependency Graph

```
Phase 0 (Reverse Iteration)
  |
  v
Phase 1 (Extend CheckpointRecord)
  |
  v
Phase 2 (Checkpoints CF)  +  Phase 3 (Ranges CF) -- Phase 3 depends on Phase 0
  |                            |
  +----------------------------+
  |
  v
Phase 4 (JSON Sync Infra)
  |
  v
Phase 5 (Trigger Sync)
  |
  +----> Phase 6 (Update Restore)         -- can start after Phase 5
  +----> Phase 6.5 (Deletion via Commands) -- depends on Phase 3 + Phase 5
  |        |
  |        +----> Phase 7 (Update Retention) -- depends on Phase 6.5
  +----> Phase 8 (Update Status API)       -- can start after Phase 3
  +----> Phase 10 (Migration)              -- can start after Phase 5
  |
  v  (all of 6, 6.5, 7, 8, 10 complete)
Phase 9 (Remove Markers)
```

**Notes on parallelism:**
- Phase 0 can be done first, independently — it's a pure infrastructure change to `zeebe/zb-db`
- Phase 1 is independent of Phase 0 but must precede Phase 2
- Phases 2 and 3 can be done in parallel after Phase 1 (Phase 3 also needs Phase 0 for deletion logic, but the initial creation/extension logic doesn't require reverse iteration)
- Phases 6, 6.5, 8, and 10 can all start in parallel after Phase 5, except Phase 7 which depends on Phase 6.5
- Phase 9 is the final cleanup phase — only after everything else is complete and validated

### Key Risks

1. **Retention and the single-writer model** (Phase 7) — **Resolved.** All deletion (including retention) goes through stream processor commands (`DELETE_BACKUP`/`BACKUP_DELETED`). Retention writes `DELETE_BACKUP` commands to the log instead of calling `backupStore.delete()` directly. The stream processor processes each command: removes the checkpoint entry from the CF, updates ranges, triggers async backup store deletion, and syncs the JSON file. This maintains the single-writer guarantee for all RocksDB CF mutations. The trade-off is added latency (command must flow through the log before deletion happens), but this is acceptable for retention which is not latency-sensitive.

2. **JSON sync atomicity** — **Resolved.** Two-file swap approach ensures at least one valid JSON file always exists. The system writes alternately to `backups-a.json` and `backups-b.json`, each carrying a monotonic sequence number. Readers load both files and pick the one with the higher valid sequence number. If a crash occurs mid-write, the other file remains intact with the last valid state. The sync-on-leader-election mechanism (Phase 5) provides additional protection by re-syncing the JSON from the authoritative CF state on failover.

3. **Predecessor lookup during backup deletion** (Phases 0, 3, 6.5) — Deleting a backup from anywhere except the start of a range requires finding the predecessor checkpoint. This applies to three scenarios:

   - **Delete from end of range**: Need predecessor to shrink `rangeEnd`. Requires reverse iteration.
   - **Delete from middle of range**: Need predecessor to determine split boundary. Requires reverse iteration.
   - **Delete the only checkpoint in a range**: No predecessor needed — just delete the range entry.
   - **Delete from start of range**: No predecessor needed — advance `rangeStart` to the next checkpoint via forward iteration.

   The current `ColumnFamily` abstraction has **no reverse iteration support** — all iteration uses `seek()` + `next()` (forward only). RocksDB 10.2.1's Java API fully supports `seekForPrev()`/`prev()` and is compatible with the existing `setPrefixSameAsStart(true)` configuration. The solution is Phase 0: add reverse iteration methods to the `ColumnFamily` interface and implement them in `TransactionalColumnFamily` (~40-50 lines mirroring the existing forward pattern). This is a prerequisite for Phase 3's range maintenance on deletion.

4. **CheckpointRecord wire format change** (Phase 1) — Adding fields to `CheckpointRecord` is backward-compatible (msgpack handles unknown fields), but should be validated with mixed-version cluster tests.

5. **Current deletion bypasses the log entirely** — Both user-initiated deletion (`BackupServiceImpl.deleteBackup()`) and retention (`BackupRetention.deleteBackups()`) currently call `backupStore.delete()` directly, bypassing the Zeebe log and stream processor. This means deletions are not replicated to followers, not replayed after restart, and have no ordering guarantees relative to other stream processor operations. Phase 6.5 addresses this by introducing `DELETE_BACKUP`/`BACKUP_DELETED` command-event intents that route all deletion through the log, bringing it into the same consistency model as backup creation and confirmation.

