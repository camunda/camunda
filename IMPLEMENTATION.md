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

#### Phase 2: Checkpoints Column Family — DONE ✅

Implemented together with Phase 3 in `a31efed3898` (feat: add CHECKPOINTS and BACKUP_RANGES column families).

**Goal:** Store every checkpoint event in a dedicated column family with full metadata.

**What was done:**

1. **Added `CHECKPOINTS(140, PARTITION_LOCAL)` to `ZbColumnFamilies`**
2. **Created `CheckpointMetadataValue`** — `UnpackedObject implements DbValue` with 6 properties (checkpointPosition, checkpointTimestamp, checkpointType as int, firstLogPosition, numberOfPartitions, brokerVersion as StringProperty)
3. **Created `DbCheckpointMetadataState`** — CRUD + `findPredecessorBackupCheckpoint` (reverse iteration) + `findSuccessorBackupCheckpoint` (forward iteration) + `getAllCheckpoints()` returning `List<CheckpointEntry>` record snapshots + `isEmpty()`. Note: `getCheckpointsInRange()` was dropped — not needed since `getAllCheckpoints()` suffices for JSON sync and range lookups use the BACKUP_RANGES CF.
4. **Modified `CheckpointCreatedEventApplier`** — writes to CHECKPOINTS CF on every CREATED event (all checkpoint types)
5. **Modified `CheckpointBackupConfirmedApplier`** — calls `enrichWithBackupInfo()` on CONFIRMED_BACKUP. Broker version sourced from `record.getBrokerVersion()` (record metadata), not CheckpointRecord.
6. **Wired in `CheckpointRecordsProcessor.init()`** — instantiates state, passes to appliers
7. **19 unit tests** in `DbCheckpointMetadataStateTest`

---

#### Phase 3: Backup Ranges Column Family — DONE ✅

Implemented together with Phase 2 in `a31efed3898`.

**Goal:** Maintain pre-computed contiguous ranges that can be queried without scanning all checkpoints.

**What was done:**

1. **Added `BACKUP_RANGES(141, PARTITION_LOCAL)` to `ZbColumnFamilies`**
2. **Created `DbBackupRangeState`** — all methods as planned. Deviation: `splitRange` takes 4 params `(oldStart, oldEnd, predecessorId, successorId)` instead of 5 — `deletedCheckpointId` is not needed since the caller already determined predecessor and successor.
3. **Range maintenance on backup deletion** — implemented as described. Uses `whileTrueReverse` for predecessor lookups in `findRangeContaining()` and `findPredecessorBackupCheckpoint()`.
4. **Moved range logic into the applier** — range creation/extension logic lives in `CheckpointBackupConfirmedApplier`, not the processor. This ensures both processing and replay use the same code path, avoiding duplication. The applier checks contiguity (`firstLogPosition <= latestBackupCheckpointPosition + 1`) and calls `extendRange()` or `startNewRange()` accordingly.
5. **Wired in `CheckpointRecordsProcessor.init()`**
6. **22 unit tests** in `DbBackupRangeStateTest`, updated `CheckpointRecordsProcessorTest`

---

#### Phase 4: JSON Sync Infrastructure — DONE ✅

Implemented in `921f1c93751` (feat: add JSON metadata sync infrastructure for backup ranges).

**Goal:** Define serialization format and BackupStore integration for syncing the per-partition JSON file.

**What was done:**

1. **Created `BackupMetadataManifest`** record in `zeebe/backup/.../common/` — with `partitionId`, `sequenceNumber` (monotonic counter for two-file swap), `lastUpdated` (Instant), `checkpoints` (List<CheckpointEntry>), `ranges` (List<RangeEntry>). Uses `@JsonCreator`/`@JsonProperty` annotations. Inner records: `CheckpointEntry` (7 fields) and `RangeEntry` (start, end).
2. **Added Jackson time dependencies** to `zeebe/backup/pom.xml` — `jackson-datatype-jdk8` and `jackson-datatype-jsr310`
3. **Added to `BackupStore` interface** — `storeBackupMetadata(int partitionId, String slot, byte[] content)` and `loadBackupMetadata(int partitionId, String slot)` returning `CompletableFuture<Optional<byte[]>>`
4. **Implemented in all 4 store backends:**
   - S3: `putObject`/`getObject` at `{basePath}/metadata/{partitionId}/backups-{slot}.json`
   - GCS: `client.create`/`client.get` at `{basePath}metadata/{partitionId}/backups-{slot}.json`
   - Azure: `BlobClient.upload`/`downloadContent` at `metadata/{partitionId}/backups-{slot}.json`
   - Filesystem: `Files.write`/`readAllBytes` with `FileUtil.flushDirectory()` for durability
5. **Created `BackupMetadataSyncer`** — two-slot atomic swap writer/reader. Alternates between slots "a"/"b", maintains monotonic sequence number, rolls back on failure. `sync()` reads both CFs and writes JSON; `load()` reads both slots and picks higher valid sequence number. Handles missing/corrupt slots gracefully.
6. **Created `StoringBackupMetadata` testkit interface** — 6 test cases (store/load both slots, empty when missing, overwrite, partition isolation, slot isolation). Added to `BackupStoreTestKit` extends list.
7. **Created `BackupMetadataSyncerTest`** — 12 unit tests covering slot alternation, sequence numbers, serialization, failure rollback, retry semantics, load from various slot combinations, corrupt data handling, sequence continuation after load.

---

#### Phase 5: Trigger JSON Sync — DONE ✅

**Goal:** Keep the JSON file up-to-date after every mutation.

**What was done:**

1. **Sync after backup confirmation** — implemented as a `PostCommitTask` in `CheckpointConfirmBackupProcessor.process()`. After the `CONFIRMED_BACKUP` event is appended and state is mutated, a post-commit task fires `syncer.sync()` (fire-and-forget). This is more correct than triggering from `BackupServiceImpl` because the CFs are guaranteed to be updated at post-commit time.
   - `CheckpointRecordsProcessor` now takes a `BackupStore` constructor parameter (nullable). If non-null, creates `BackupMetadataSyncer` in `init()`.
   - `CheckpointConfirmBackupProcessor` receives the syncer, `partitionId`, `checkpointMetadataState`, and `backupRangeState` via its constructor.
   - Post-commit tasks only execute on leaders (not during replay), so sync naturally only happens on the leader.
2. **Sync on leader election** — implemented in `CheckpointRecordsProcessor.onRecovered()`. After `failInProgressBackup()`, calls `syncer.sync()` to catch up any missed syncs from leader failovers.
3. **Sync after backup deletion** — deferred to Phase 6.5 (`CheckpointDeleteBackupProcessor` will schedule a similar post-commit task).
4. **Sync on errors/recovery** — covered by points 1 and 2. If any sync fails, the syncer rolls back its sequence number and the next mutation or recovery will re-sync.
5. **Wiring changes:**
   - `BackupServiceTransitionStep` now passes `context.getBackupStore()` to the processor constructor
   - `checkpointMetadataState` and `backupRangeState` promoted from local variables to fields in `CheckpointRecordsProcessor` (needed for `onRecovered()` sync)
   - `partitionId` stored as a field (was previously a constructor param but unused)
6. **Test infrastructure:** `MockProcessingResultBuilder` now stores post-commit tasks and `MockProcessingResult` exposes them via `postCommitTasks()` and executes them in `executePostCommitTasks()`.
7. **4 new tests** in `CheckpointRecordsProcessorTest`: sync post-commit task on confirm, no sync without backup store, no sync on rejection, sync on recovery.

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

