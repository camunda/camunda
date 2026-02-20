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

#### Phase 6: Update Restore to Use JSON Files — DONE ✅

**Goal:** Rewrite restore to read the per-partition JSON file instead of querying markers + individual backups.

**Summary:** Created `BackupMetadataReader` in `zeebe/restore` that loads per-partition manifests from the backup store using the two-slot atomic swap protocol. Rewrote `BackupRangeResolver.getInformationPerPartition()` to load the manifest once per partition and do all range/checkpoint lookups locally. Changed `findBackupRangeCoveringInterval()` to accept a `Function<Long, BackupStatus>` instead of a partition ID, eliminating API calls. Replaced `getAllBackups()` with `getAllBackupsFromManifest()` that filters checkpoints from a local map. Rewrote `RestoreManager.verifyBackupIdsAreContinuous()` and `restoreTimeRange()` to use `BackupMetadataReader` instead of range markers and wildcard store queries. Promoted `zeebe-protocol` dependency from test to compile scope. Updated all tests (`BackupRangeResolverTest`, `RestoreManagerTest`) to set up manifests instead of range markers.

---

#### Phase 6.5: Backup Deletion via Commands — DONE ✅

**Goal:** Route all backup deletion (user-initiated and retention) through the stream processor via new `DELETE_BACKUP`/`BACKUP_DELETED` command-event intents. This ensures deletions are replicated to followers, replayed after restart, and have deterministic ordering relative to other stream processor operations.

**What was done:**

1. **Added `DELETE_BACKUP(5)` and `BACKUP_DELETED(6)` intents to `CheckpointIntent.java`** — updated `isEvent()` and `from(short)` accordingly.
2. **Created `CheckpointBackupDeletedApplier`** — shared by both the processor (during processing) and the replay path. The `apply()` method: finds the range containing the checkpoint via `DbBackupRangeState`, handles all 5 deletion scenarios (only entry → delete range, start → advance start, end → shrink end via predecessor lookup, middle → split range, not in range → skip), then removes the checkpoint from `DbCheckpointMetadataState`. Also provides a `validate()` method for the processor to check existence before processing.
3. **Created `CheckpointDeleteBackupProcessor`** — processes `DELETE_BACKUP` commands following the pattern of `CheckpointConfirmBackupProcessor`. Validates checkpoint exists, delegates state mutations to the applier, appends `BACKUP_DELETED` follow-up event, and schedules two post-commit tasks: (a) async backup store deletion (lists all copies via wildcard, marks in-progress as failed, then deletes) and (b) JSON metadata sync via `BackupMetadataSyncer`.
4. **Wired into `CheckpointRecordsProcessor`** — added `DELETE_BACKUP` dispatch to `process()` and `BACKUP_DELETED` dispatch to `replay()`. State classes instantiated in `init()`.
5. **Updated `BackupServiceImpl.deleteBackup()`** — replaced direct `backupStore.list()` + `deleteBackupIfExists()` calls with writing a `DELETE_BACKUP` command to the log via `logStreamWriter.tryWrite()`, following the exact pattern of `confirmBackup()`. The old `deleteBackupIfExists()` method is now unused (left for Phase 9 cleanup).
6. **9 new tests in `CheckpointRecordsProcessorTest`** — covering successful deletion, rejection, range scenarios (start/end/middle deletion, range splitting), replay, post-commit task scheduling (with/without backup store, on rejection).
7. **Updated `BackupServiceImplTest`** — replaced 4 old tests (that verified direct `backupStore.delete()` calls) with 3 new tests: `shouldWriteDeleteBackupCommandToLog` (verifies log write with correct intent and checkpoint ID), `shouldFailDeleteBackupWhenLogWriteFails` (verifies error propagation), and `shouldNotInteractWithBackupStoreOnDeleteBackup` (verifies no direct store calls).

**Key design decisions:**
- The applier handles range maintenance BEFORE removing the checkpoint from the CF, because `findPredecessorBackupCheckpoint`/`findSuccessorBackupCheckpoint` need the checkpoint to still exist to iterate past it.
- Async backup store deletion uses the same wildcard pattern as the old code (`Optional.empty()` for nodeId) to handle multiple backup copies across broker nodes.
- Post-commit tasks are fire-and-forget — if they fail, the sync-on-leader-election mechanism in `onRecovered()` will catch up.

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
  |
  v  (all of 6, 6.5, 7, 8 complete)
Phase 9 (Remove Markers)
```

**Notes on parallelism:**
- Phase 0 can be done first, independently — it's a pure infrastructure change to `zeebe/zb-db`
- Phase 1 is independent of Phase 0 but must precede Phase 2
- Phases 2 and 3 can be done in parallel after Phase 1 (Phase 3 also needs Phase 0 for deletion logic, but the initial creation/extension logic doesn't require reverse iteration)
- Phases 6, 6.5, and 8 can all start in parallel after Phase 5, except Phase 7 which depends on Phase 6.5
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

