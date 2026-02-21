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

#### Phase 7: Update Retention — DONE ✅

**Goal:** Remove retention's dependency on marker files and route all retention deletions through stream processor commands via `BrokerClient`.

**What was done:**

1. **Replaced direct store deletion with `DELETE_BACKUP` commands via `BrokerClient`** — `BackupRetention` is a cluster-level singleton (runs on the lowest-numbered broker, handles ALL partitions), so it cannot use a per-partition `LogStreamWriter`. Instead, it sends `BackupDeleteRequest` messages through `BrokerClient.sendRequestWithRetry()`, which routes each request to the correct partition leader. The leader's `BackupApiRequestHandler` invokes `backupManager.deleteBackup()`, which writes the `DELETE_BACKUP` command to the partition's log. The stream processor then handles CF updates, async store deletion, and JSON sync.
2. **Simplified `BackupRetention` pipeline** — removed all marker-related methods: `retrieveRangeMarkers()`, `enrichContextWithMarkers()`, `resetRangeStart()`, `shouldResetMarker()`, `deleteMarkers()`, `deleteBackups()`. Pipeline simplified from 6 steps to 3: `retrieveBackups -> processBackups -> writeDeleteCommands`. The `writeDeleteCommands()` method deduplicates by checkpoint ID (since a single `DELETE_BACKUP` command handles all node copies) and sends one request per unique checkpoint to the correct partition.
3. **Simplified `RetentionContext` record** — removed `previousStartMarker` (Optional<BackupRangeMarker>) and `deletableRangeMarkers` (List<BackupRangeMarker>) fields; removed `withRangeMarkerContext()` method.
4. **Cleaned up `RetentionMetrics`** — removed `RANGES_DELETED_ROUND` metric constant, its gauge registration, its cleanup in `close()`, and the `rangesDeleted` field and `setRangesDeleted()` method from `PartitionMetrics`.
5. **Updated `CheckpointSchedulingService`** — wired `brokerClient` into `BackupRetention` constructor (the service already had a `brokerClient` field).
6. **Rewrote `RetentionTest`** — removed all marker-related imports, setup, and verification. Added `@Mock BrokerClient brokerClient`, wired into `createBackupRetention()`. Replaced `verifyBackupsDeleted()` (which checked `backupStore.delete()`) with `verifyDeleteCommandsSent()` (which checks `brokerClient.sendRequestWithRetry(BackupDeleteRequest)` with correct partition and checkpoint IDs). Removed `createDefaultRangeMarkers()`, `verifyRangeMarkerStored()`, `verifyRangeMarkerDeleted()`, `verifyNoBackupStoreModifications()` helpers. Removed `actorShouldNotHangOnMarkerListingFailure` test. All 16 retention tests pass.
7. **`CheckpointSchedulerServiceTest`** — compiled and all 11 tests pass without changes (already mocked `BrokerClient`).

**Key design decision:** Using `BrokerClient` over `LogStreamWriter` is the right approach because `BackupRetention` runs as a cluster singleton and needs to send commands to leaders of arbitrary partitions, not just the local partition.

---

#### Phase 8: Update Backup Status API — DONE ✅

**Goal:** Expose range information from the new CFs instead of marker files.

**What was done:**

1. **Rewrote `BackupServiceImpl.getBackupRangeStatus()`** — reads ranges from `DbBackupRangeState.getAllRanges()` and boundary checkpoints from `DbCheckpointMetadataState`, building `BackupRangeStatus` objects entirely from CF data. No more marker-based `BackupRanges.fromMarkers()` computation.
2. **Updated `BackupApiRequestHandler.handleQueryRangesRequest()`** if needed
3. **Updated tests** to verify CF-based range status

---

#### Phase 9: Remove Marker Code — DONE ✅

**Goal:** Clean up all marker-related code now that RocksDB column families and JSON metadata manifests are the source of truth.

**What was done:**

1. **Removed from `BackupStore` interface**: `rangeMarkers()`, `storeRangeMarker()`, `deleteRangeMarker()`
2. **Removed from all 4 store implementations** (S3, GCS, Azure, Filesystem) — deleted marker methods and helper methods (`rangeMarkersPrefix()`, `rangeMarkerBlobInfo()`)
3. **Deleted `BackupRangeMarker.java`** (sealed interface for Start/End markers) and **`BackupRanges.java`** (sealed interface for range query results)
4. **Removed from `BackupServiceImpl`**: `startNewRange()`, `extendRange()`, `deleteBackupIfExists()` methods that called the store
5. **Removed from `BackupManager` interface**: `extendRange()`, `startNewRange()` (range management now happens in the stream processor)
6. **Removed from `BackupService`** and **`NoopBackupManager`**: corresponding actor-delegate methods
7. **Cleaned up tests:**
   - Deleted `BackupRangesTest.java` (323 lines) and `StoringRangeMarkers.java` testkit interface (195 lines)
   - Removed `StoringRangeMarkers` from `BackupStoreTestKit` extends list
   - Removed 4 marker-based tests from `BackupServiceImplTest`, cleaned up unused imports (`inOrder`, `CheckpointPattern` restored)
   - Removed `assertRangeMarkerHasBeenUpdated()` method and call from `BackupRetentionAcceptance`, renamed test method, removed unused imports (`AssertionsForClassTypes`, `End`, `Start`)
   - Removed marker stubs from `BackupRangeResolverTest.TestBackupStore` and `TestRestorableBackupStore`
   - Added missing `storeBackupMetadata()`/`loadBackupMetadata()` stubs to `InMemoryMockBackupStore`
8. **Net result:** 19 files changed, ~1,160 lines deleted. Build compiles cleanly, 1,794 unit tests pass.

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

---

## Post-Implementation Review

All 10 phases (0–9) are marked DONE. The following is a review of what was built, what works, what needs fixing, and what QA gaps remain.

### What Works Well

1. **Architecture is sound.** The stream processor model is correctly followed throughout: commands flow through the log, events are applied by shared appliers (used in both processing and replay), and async side-effects (store deletion, JSON sync) are post-commit tasks. The single-writer guarantee is maintained for all CF mutations.

2. **Column families are correctly implemented.** `DbCheckpointMetadataState` (19 unit tests) and `DbBackupRangeState` (22 unit tests) follow the established `DbCheckpointState` pattern. Predecessor/successor lookups use reverse/forward iteration correctly. Range maintenance covers all 4 deletion scenarios (single-entry, start, end, mid-range split).

3. **JSON sync infrastructure is robust.** The two-slot atomic swap (`BackupMetadataSyncer` / `BackupMetadataReader`) handles crash safety, corrupt files, missing files, and sequence number rollback. 12 unit tests + 6 testkit tests cover the core behaviors. All 4 store backends (S3, GCS, Azure, Filesystem) implement `storeBackupMetadata`/`loadBackupMetadata`.

4. **Marker code is fully removed.** Codebase-wide grep confirms zero references to `rangeMarker`, `BackupRangeMarker`, `storeRangeMarker`, `deleteRangeMarker`, or the old `BackupRanges` sealed interface in Java source.

5. **Retention correctly routes through BrokerClient.** `BackupRetention` sends `BackupDeleteRequest` via `brokerClient.sendRequestWithRetry()` instead of calling `backupStore.delete()` directly. Deduplication by checkpoint ID is in place.

6. **Integration tests exist.** `BackupRangeTrackingIT` (2 tests: range tracking + leader failover), `RdbmsRangeRestoreIT`, and 4 retention acceptance tests (S3, GCS, Azure, Filesystem) cover the end-to-end pipeline.

### Bugs to Fix

#### Bug 1: Stale Legacy `DbCheckpointState` After Deletion (MEDIUM)

**Location:** `CheckpointBackupDeletedApplier` / `CheckpointDeleteBackupProcessor`

**Problem:** When a backup is deleted via `DELETE_BACKUP`, neither the applier nor the processor updates `DbCheckpointState` (the legacy 2-entry state that stores `latestBackupId` / `latestBackupPosition`). If the deleted backup happens to be the latest, subsequent operations read stale values:

- `CheckpointBackupConfirmedApplier` compares the new backup's `firstLogPosition` against the deleted backup's stale `checkpointPosition` for contiguity. The contiguity answer is wrong, but it self-heals (falls back to `startNewRange` when `findRangeContaining(staleId)` returns empty).
- **More concerning:** `CheckpointCreateProcessor` reads `checkpointState.getLatestBackupPosition()` to compute the `BackupDescriptor`'s `firstLogPosition` for new backups. A stale value means the new backup's log coverage start is wrong — it could miss log entries between the true latest backup and the deleted one, producing a gap.

**Fix:** In `CheckpointBackupDeletedApplier.apply()`, after removing the checkpoint from the CHECKPOINTS CF, check if the deleted ID equals `checkpointState.getLatestBackupId()`. If so, find the predecessor backup via `checkpointMetadataState.findPredecessorBackupCheckpoint()` and call `checkpointState.setLatestBackupInfo(predecessor...)`. If no predecessor exists, add and call a `clearLatestBackupInfo()` method on `DbCheckpointState`.

#### Bug 2: Command Rejection Missing `RejectionType` (LOW)

**Location:** `CheckpointDeleteBackupProcessor.process()` lines 79–86

**Problem:** When a `DELETE_BACKUP` command is rejected (checkpoint not found), the rejection record does not set a `RejectionType` or reason string. Compare with `CheckpointCreateProcessor` which sets `RejectionType.INVALID_STATE` and a descriptive reason.

**Fix:** Set `resultBuilder.withRejectionType(RejectionType.NOT_FOUND)` and `resultBuilder.withRejectionReason("Checkpoint " + checkpointId + " not found")`.

### Code Quality Issues

| Severity |                    Location                     |                                  Issue                                   |                        Action                         |
|----------|-------------------------------------------------|--------------------------------------------------------------------------|-------------------------------------------------------|
| Low      | `CheckpointBackupConfirmedApplier`              | Class not `final` (inconsistent with other appliers)                     | Add `final`                                           |
| Low      | `CheckpointConfirmBackupProcessor`              | Class not `final` (inconsistent with other processors)                   | Add `final`                                           |
| Low      | `CheckpointCreatedEventApplier`                 | `metrics` field injected but never used in `apply()`                     | Remove dead field                                     |
| Low      | `CheckpointBackupDeletedApplier`                | `validate()` method is never called (dead code)                          | Remove or wire into `CheckpointDeleteBackupProcessor` |
| Low      | `BackupMetadataSyncer` / `BackupMetadataReader` | Duplicate `load()`/`loadSlot()` logic and duplicate `ObjectMapper` setup | Extract shared utility                                |
| Low      | `Context.java` (processing package)             | Appears to be dead code — not used by any refactored file                | Verify and remove if unused                           |

### Missing QA Tests

The following test gaps were identified by reviewing test files against the testing guidelines in `docs/testing.md` ("every public change should be verified via an automated test").

#### Unit Tests — Missing

1. **`CheckpointBackupDeletedApplier` — no dedicated unit test.** The applier has 4 range-deletion branches plus 3 warning/fallback paths. These are tested indirectly through `CheckpointRecordsProcessorTest` but not in isolation. A dedicated `CheckpointBackupDeletedApplierTest` would allow targeted testing of:
   - Each of the 4 deletion scenarios with controlled state
   - The warning paths (missing successor for start-of-range, missing predecessor for end-of-range, missing both for mid-range)
   - Order-of-operations correctness (range update before checkpoint removal)
2. **`CheckpointDeleteBackupProcessor` — no dedicated unit test.** Tested indirectly through `CheckpointRecordsProcessorTest`. A dedicated test would cover:
   - `deleteFromBackupStore` with mixed IN_PROGRESS / COMPLETED backup copies
   - `deleteFromBackupStore` when `backupStore.list()` fails
   - `deleteFromBackupStore` when `markFailed()` fails for an IN_PROGRESS backup
   - Rejection type and reason string verification (once Bug 2 is fixed)
3. **`BackupServiceImpl.getBackupRangeStatus()` — no unit test.** The test setup passes `null` for `DbBackupRangeState` and `DbCheckpointMetadataState`, so any test calling this method would NPE. Need a test with real or mocked state objects.
4. **Replay of CONFIRMED_BACKUP with range state updates — not tested.** `CheckpointRecordsProcessorTest.shouldReplayConfirmedBackupRecord` only verifies the backup state is updated but does NOT verify that `backupRangeState` is updated during replay. This is a gap — replay correctness of range state is critical.
5. **Sequential DELETE_BACKUP operations — not tested.** No test exercises multiple deletions in sequence (e.g., delete first backup, then next, verifying progressive range shrinkage). This would catch state accumulation bugs.
6. **DELETE_BACKUP idempotency — not tested.** No test verifies that deleting the same checkpoint twice results in the first succeeding and the second being rejected.
7. **CONFIRM_BACKUP creating a disjoint range — not tested.** No test for the scenario where a new backup is NOT contiguous with the latest, resulting in a new independent range.
8. **Backward compatibility — not tested.** No test processes a `CheckpointRecord` serialized without `numberOfPartitions` (default -1). While msgpack handles this by design, an explicit test would serve as a regression guard.
9. **Pre-migration state scenario — not tested.** No test verifies that CONFIRMED_BACKUP works correctly when the CHECKPOINTS and BACKUP_RANGES CFs are empty but `DbCheckpointState` has data from before the migration.
10. **`RestoreManagerTest` — limited coverage.** Only tests failure/validation paths. No happy-path test for successful restore via `restore(Map<Integer, long[]>, ...)`, `restoreRdbms(...)`, or topology file restoration.

#### Integration Tests — Missing

11. **End-to-end DELETE_BACKUP integration test.** `BackupRangeTrackingIT` tests range creation and extension during leader changes, but does NOT test backup deletion via `DELETE_BACKUP` commands or retention-driven deletion and its effect on ranges.

12. **Metadata syncer round-trip integration test.** JSON sync to backup store is only unit-tested with mocks. No IT verifies the actual S3/GCS/Azure/filesystem round-trip of the metadata manifest including the two-slot swap behavior.

13. **Restore from manifest integration test (non-RDBMS).** `RdbmsRangeRestoreIT` exists but there is no standard (non-RDBMS) variant that exercises `BackupRangeResolver` + `RestoreManager` with a real backup store.

### Suggested Phase Ordering for Next Steps

These are ordered by impact and dependency:

```
Phase 10 (Bug Fix: Stale Legacy State)           -- highest priority, correctness bug
  |
  v
Phase 11 (Bug Fix: Rejection Type)               -- low effort, correctness
  |
  v
Phase 12 (Unit Test Gap Closure)                  -- items 1-9 above
  |
  v
Phase 13 (Integration Test Gap Closure)           -- items 11-13 above
  |
  v
Phase 14 (Code Quality Cleanup)                   -- final, low risk
```

---

#### Phase 10: Fix Stale Legacy State on Deletion — DONE ✅

**Goal:** Prevent `DbCheckpointState.latestBackupId/latestBackupPosition` from going stale after a `DELETE_BACKUP`.

**What was done:**

1. **Added `clearLatestBackupInfo()` to `CheckpointState` interface and `DbCheckpointState`** — uses `deleteIfExists` to safely remove the "backup" entry from the DEFAULT CF.
2. **Updated `CheckpointBackupDeletedApplier`** — added `CheckpointState` as a constructor dependency. New `updateLegacyBackupStateOnDeletion()` method runs after range maintenance but before `removeCheckpoint()`: if the deleted checkpoint is the latest backup, finds the predecessor via `findPredecessorBackupCheckpoint()` and either rolls back to it or clears the latest backup info entirely.
3. **Updated wiring** — `CheckpointDeleteBackupProcessor` and `CheckpointRecordsProcessor.init()` both pass `checkpointState` to the applier.
4. **3 unit tests added** to `CheckpointRecordsProcessorTest`:
   - `shouldClearLatestBackupOnDeletionOfOnlyBackup` — verifies `getLatestBackupId()` returns `NO_CHECKPOINT`
   - `shouldRollBackToPredecessorOnDeletionOfLatestBackup` — verifies rollback to predecessor with all metadata fields
   - `shouldNotAffectLatestBackupOnDeletionOfOlderBackup` — verifies latest is unchanged

---

#### Phase 11: Fix Rejection Type on DELETE_BACKUP — DONE ✅

**Goal:** Consistent rejection handling across all checkpoint command processors.

**What was done:**

1. **Updated `CheckpointDeleteBackupProcessor.process()`** — added `RejectionType.NOT_FOUND` and descriptive rejection reason `"Expected to delete backup for checkpoint <id>, but no such checkpoint exists"` to the `RecordMetadata` on the rejection path. Previously the rejection record had no rejection type or reason set.
2. **Updated `shouldRejectDeleteBackupWhenCheckpointNotFound` test** in `CheckpointRecordsProcessorTest` — added assertions for `RejectionType.NOT_FOUND` and the rejection reason string.

---

#### Phase 12: Unit Test Gap Closure — DONE ✅

**Goal:** Bring unit test coverage in line with `docs/testing.md` guidelines — every public API with business logic has a test.

**What was done:**

1. **Created `CheckpointBackupDeletedApplierTest`** — 11 tests covering all 4 deletion scenarios (single-entry, advance start, shrink end, mid-range split), 3 warning/fallback paths (missing successor, missing predecessor, missing both), checkpoint not in any range, legacy state rollback (to predecessor, clear on only backup, no effect on older), and `validate()` method. Uses real ZeebeDb.

2. **Created `CheckpointDeleteBackupProcessorTest`** — 7 tests covering: rejection with NOT_FOUND, no post-commit tasks on rejection, BACKUP_DELETED event on success, post-commit task scheduling with backup store, mixed IN_PROGRESS/COMPLETED deletion from store, backup store list failure handled gracefully, no post-commit tasks without backup store. Uses real ZeebeDb + mocked BackupStore.

3. **Added `getBackupRangeStatus()` tests to `BackupServiceImplTest`** — 7 tests in a `@Nested GetBackupRangeStatusTest` class with mocked `DbBackupRangeState` and `DbCheckpointMetadataState`: empty ranges, single range with full metadata verification, multiple ranges, skip when first metadata missing, skip when last metadata missing, skip when both missing, exception propagation, single-point range (start == end).

4. **Added replay range-state test to `CheckpointRecordsProcessorTest`** — `shouldReplayConfirmedBackupWithRangeUpdate` and `shouldReplayConfirmedBackupAndExtendExistingRange`: replays CONFIRMED_BACKUP events and verifies BACKUP_RANGES CF is updated.

5. **Added sequential deletion test** — `shouldHandleSequentialDeletions`: confirms 3 backups [A,C], deletes A→[B,C], deletes C→[B,B], deletes B→empty.

6. **Added idempotency test** — `shouldRejectSecondDeleteOfSameCheckpoint`: first delete succeeds, second is rejected NOT_FOUND.

7. **Added disjoint range test** — `shouldStartNewRangeWhenBackupNotContiguous`: confirms backup with firstLogPosition > latestBackupPosition+1, verifies two separate ranges.

8. **Added backward compatibility test** — `shouldHandleCheckpointRecordWithoutNumberOfPartitions`: processes CONFIRM_BACKUP with default numberOfPartitions (-1), verifies stored as -1.

9. **Added pre-migration scenario test** — `shouldHandleConfirmBackupWithEmptyCFs`: legacy state has latest backup but CHECKPOINTS/BACKUP_RANGES CFs empty, confirms new backup creates new range.

10. **Expanded `RestoreManagerTest`** — 5 new tests: backup not found (non-existent checkpoint), time-range with no backups in range, missing manifest for partition, filtering out MARKER checkpoints in time range, validation that continuous backup ranges pass the continuity check.

**Test:** `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C -pl zeebe/backup,zeebe/restore`

---

#### Phase 13: Integration Test Gap Closure — DONE ✅

**Goal:** End-to-end validation of deletion and sync paths.

**What was done:**

1. **Extended `BackupRangeTrackingIT`** with `shouldAdvanceRangeStartOnBackupDeletion`:
   - Waits for 3+ continuous backups per partition
   - Deletes the oldest backup via `actuator.delete()`
   - Asserts range start advances past the deleted checkpoint on all 3 partitions
   - Creates a `FilesystemBackupStore` and uses `BackupMetadataCodec.load()` to verify the JSON manifest no longer contains the deleted checkpoint and range starts are updated
2. **Added manifest verification to `BackupRetentionAcceptance`**:
   - New `assertMetadataManifestReflectsRetention(deletedIds, retainedIds)` default method loads per-partition manifests via `BackupMetadataCodec.load()` and asserts checkpoint lists exclude deleted backups and include retained ones, with non-empty ranges
   - Called at the end of `shouldMaintainRollingWindowAndDeleteOldBackups()` — runs across all 4 store backends (S3, GCS, Azure, Filesystem)

**Test:** `./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C -pl zeebe/qa/integration-tests`

---

#### Phase 14: Code Quality Cleanup ✅ DONE

**Goal:** Eliminate dead code, fix style inconsistencies.

**What was done:**

1. Added `final` to `CheckpointBackupConfirmedApplier` and `CheckpointConfirmBackupProcessor` class declarations.
2. Removed unused `metrics` field and constructor parameter from `CheckpointCreatedEventApplier`; updated both call sites (`CheckpointRecordsProcessor`, `CheckpointCreateProcessor`).
3. Removed unused `validate()` method from `CheckpointBackupDeletedApplier` (the `CheckpointDeleteBackupProcessor` already performs its own existence check); removed corresponding tests from `CheckpointBackupDeletedApplierTest`.
4. Extracted shared `ObjectMapper` setup and two-slot load logic into `BackupMetadataCodec` in `zeebe/backup/common/`; refactored `BackupMetadataSyncer` and `BackupMetadataReader` to delegate to it; updated `BackupMetadataSyncerTest` to use `BackupMetadataCodec.MAPPER`.
5. Deleted unused `Context.java` from the processing package (no imports or references found).
6. Fixed `TestRestorableBackupStore.delete()` — now returns `CompletableFuture.completedFuture(null)` and removes the backup from the in-memory map.
7. Updated `TestRestorableBackupStore.storeManifest()` to use `BackupMetadataCodec.serialize()` instead of creating an inline `ObjectMapper`.

**Test:** `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C -pl zeebe/backup,zeebe/restore` — 435 tests pass.

---

#### Phase 15: Point-in-Time Restore — DONE ✅

**Goal:** When `--to` is provided, restore all partitions to the latest backup checkpoint at-or-before that timestamp, consistent across all partitions. Replaces the previous `restoreTimeRange` approach (which selected all backups in a `[from, to]` interval) with a single-checkpoint PITR resolution.

**What was done:**

1. **`BackupRangeResolver.java`** — added `resolvePointInTime(Instant target, int partitionCount, Executor executor)` returning `CompletableFuture<Long>`:
   - Loads manifests in parallel via `BackupMetadataReader`
   - Per partition: streams checkpoint entries, filters `timestamp <= target`, takes max checkpoint ID → per-partition lower bound
   - Global lower bound = `min(per-partition lower bounds)` — handles cross-partition timestamp skew
   - If global lower bound is a MARKER, walks backward to nearest backup-type checkpoint present on all partitions
   - Throws `IllegalStateException` if no checkpoint at-or-before target, or all such checkpoints are MARKERs
2. **`RestoreManager.java`**:
   - Replaced non-RDBMS path: `restore(@Nullable Instant from, @Nullable Instant to, ...)` now calls `restorePointInTime(to != null ? to : Instant.now(), ...)` instead of removed `restoreTimeRange()`
   - `from` parameter is ignored in non-RDBMS path (PITR only needs target timestamp)
   - Removed dead `restoreTimeRange()` method
   - RDBMS path (`restoreRdbms`) unchanged
3. **`RestoreApp.java`** — updated `--from`/`--to` comments: `--from` is optional, `--to` alone suffices for non-RDBMS PITR. `hasTimeRange()` already uses `||` so no logic change needed.
4. **`BackupRangeResolverTest.java`** — added `@Nested ResolvePointInTime` class with 9 tests: exact backup match, target between backups, MARKER walk-back, cross-partition timestamp skew, no checkpoint before target, all MARKERs before target, multi-partition MARKER walk-back, latest backup when target after all, missing manifest.
5. **`RestoreManagerTest.java`** — updated 4 tests for PITR semantics: split ranges no longer fail, error message updates for "no checkpoint found", CompletionException unwrapping, all-MARKERs error.

**Test:** `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C -pl zeebe/restore` — 54 tests pass.

---

#### Phase 16: Simplify BackupRangeResolver — DONE ✅

**Goal:** Eliminate unnecessary type conversions in `BackupRangeResolver`. The manifest already provides all data as simple records (`CheckpointEntry`, `RangeEntry`), but the current code converts them to heavy domain types (`BackupStatus`, `BackupDescriptor`, `BackupRange`, `Interval`, `Tuple`) only to extract the same scalar values back out. This adds ~300 lines of indirection with no value.

**Problems being fixed:**

1. **Gratuitous `BackupStatus` conversion** — `toBackupStatus()` creates fake `BackupIdentifierImpl` (sentinel node ID 0), `BackupDescriptorImpl` (synthetic snapshot IDs), `BackupStatusImpl`. The consumer only reads `checkpointId`, `checkpointPosition`, `firstLogPosition`, `checkpointTimestamp` — all already on `CheckpointEntry`.
2. **`BackupRange` / `Interval` / `Tuple` machinery** — `getInformationPerPartition` converts `RangeEntry` → `BackupRange.Complete` → `Interval<BackupStatus>` → `Tuple<Complete, Interval<BackupStatus>>`, then maps to time intervals. ~120 lines for what a 15-line method on `RangeEntry` + a timestamp map can do.
3. **`Interval.smallestCover()` overkill** — general-purpose interval arithmetic for a simple need: "include checkpoints from `from` onwards, stop after covering `to`".
4. **`CheckpointIdGenerator` parameter** — passed through `getRestoreInfoForAllPartitions` solely for one error message. Not needed for logic.
5. **Duplicate validation** — `validateRangeCoverage` checks `firstCheckpointId > safeStart` twice (lines 575 and 581-582).
6. **11 unnecessary imports** — `BackupDescriptor`, `BackupRange`, `BackupRange.Complete`, `BackupStatus`, `BackupStatusCode`, `BackupDescriptorImpl`, `BackupIdentifierImpl`, `BackupStatusImpl`, `CheckpointIdGenerator`, `Interval`, `Tuple`.

**Changes:**

1. **`BackupRangeResolver.java`** — rewrite (~653 → ~350 lines):
   - **Delete `toBackupStatus()`** — no more conversion from `CheckpointEntry` to `BackupStatus`.
   - **Delete `findBackupRangeCoveringInterval()`** — replace with private `findCoveringRange(List<RangeEntry>, Map<Long, Instant>, Instant, Instant)` that returns `Optional<RangeEntry>`. Logic: iterate ranges in reverse, look up start/end timestamps from the map, check containment. ~15 lines. Containment rule: `effectiveFrom = from != null ? from : to`, `effectiveTo = to != null ? to : from`, then `!startTime.isAfter(effectiveFrom) && !endTime.isBefore(effectiveTo)`. Both-null case returns first (latest) range.
   - **Delete `getAllBackupsFromManifest()`** — replace with private `selectCheckpoints(List<CheckpointEntry>, Instant, Instant)`. Logic: filter out entries before `from`, then include entries up to and including the first one whose timestamp >= `to`. A simple loop, ~15 lines. No `Interval.smallestCover()`.
   - **Rename `getInformationPerPartition()` → private `resolvePartition()`**:
     1. Load manifest via `loadManifest(partition)` (new helper, throws if missing)
     2. Build `Map<Long, Instant>` timestamp lookup from manifest checkpoints
     3. `findCoveringRange()` to get the `RangeEntry`
     4. Get all backup-type checkpoints in range, sorted by ID
     5. Apply `selectCheckpoints()` with `from`/`to` bounds
     6. `findSafeStartCheckpoint()` on the selected checkpoints
     7. Filter to checkpoints >= safe start
     8. Return `PartitionRestoreInfo`
   - **Move `computeGlobalCheckpointId()` into private `computeGlobalResult()`** — called from `getRestoreInfoForAllPartitions().thenApply()`.
   - **Change `PartitionRestoreInfo` record fields**: `BackupRange backupRange` → `RangeEntry range`, `List<BackupStatus> backupStatuses` → `List<CheckpointEntry> checkpoints`.
   - **Simplify `PartitionRestoreInfo.validate()`**: no more sealed-type switch on `BackupRange` subtypes (range is always a `RangeEntry`). Chain overlap check uses `curr.firstLogPosition() > prev.checkpointPosition() + 1` directly. Fix duplicate `firstCheckpointId > safeStart` check.
   - **Change `findSafeStartCheckpoint()` signature**: `Collection<BackupStatus>` → `List<CheckpointEntry>`. Filter by `e.checkpointPosition() <= exportedPosition`, return max checkpoint ID.
   - **Remove `CheckpointIdGenerator` parameter** from `getRestoreInfoForAllPartitions()`.
   - **`resolvePointInTime()` stays unchanged** — it's already clean.
   - **Remove 11 imports**: `BackupDescriptor`, `BackupRange`, `BackupRange.Complete`, `BackupStatus`, `BackupStatusCode`, `BackupDescriptorImpl`, `BackupIdentifierImpl`, `BackupStatusImpl`, `CheckpointIdGenerator`, `Interval`, `Tuple`.
   - **Also remove**: `CheckpointType` (only used in `toBackupStatus`), `OptionalLong` (same), `SequencedCollection` (use `List.reversed()`).
2. **`BackupRangeResolverTest.java`**:
   - **`FindSafeStartCheckpoint` nested class**: change test helper from `createBackupStatus(partition, checkpointId, position, firstLog)` to `new CheckpointEntry(checkpointId, position, Instant.now(), "SCHEDULED_BACKUP", firstLog, 3, "8.7.0")`. Call `BackupRangeResolver.findSafeStartCheckpoint(pos, List.of(...))` with `List<CheckpointEntry>`.
   - **Remove `createBackupStatus()` helper methods** — no longer needed.
   - **Update `resolve()` helper**: remove `checkIdGenerator` from the `getRestoreInfoForAllPartitions` call.
   - **Update error message assertions** if the error format changes (e.g., the `findBackupRangeCoveringInterval` error message that included `timeInterval=` — the new `findCoveringRange` won't include that).
   - All other tests (happy paths, error cases, `ResolvePointInTime`) go through the public API and should work without changes beyond the above.
3. **`RestoreManager.java`**:
   - Remove `checkpointIdGenerator` field and its initialization from the constructor.
   - Remove the `checkpointIdGenerator` argument from the `getRestoreInfoForAllPartitions` call in `restoreRdbms()`.
   - Remove `CheckpointIdGenerator` import.

**What stays unchanged:**

- `resolvePointInTime()` method body — already works directly with manifests
- `GlobalRestoreInfo` record shape — same fields, same public API
- `TestBackupStore` in tests — already builds manifests correctly
- `BackupMetadataReader` — untouched
- `RestoreManagerTest` — only uses `GlobalRestoreInfo.globalCheckpointId()` and `.backupsByPartitionId()`, which are unchanged

**Test:** `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C -pl zeebe/restore`

## Phase 17: Fix unsound checkpoint selection in BackupRangeResolver

**Problem:**

`BackupRangeResolver.getRestoreInfoForAllPartitions()` has an unsound algorithm. `selectCheckpoints()` filters checkpoints per-partition by timestamp *before* the cross-partition intersection in `computeGlobalResult()`. Because `checkpointTimestamp` is partition-local (each partition writes its checkpoint at a slightly different wall-clock time), the same checkpoint ID can be filtered out on one partition but not another due to timestamp skew. This breaks the subsequent set intersection.

Additional issues:
1. `selectCheckpoints()` includes a checkpoint *after* `to` (the "firstAfter"), which means restore can silently go beyond the user's requested time window.
2. `findSafeStartCheckpoint` is applied to already-timestamp-filtered checkpoints, so valid safe-start checkpoints outside `[from, to]` but inside the covering range are missed.

**Correct algorithm:**

1. Load manifests for all partitions in parallel (unchanged).
2. Per partition: find a covering range for `[from, to]` (unchanged).
3. Per partition: collect all non-MARKER checkpoints in the covering range (unchanged).
4. **Find global target**: Per partition, find highest non-MARKER checkpoint with timestamp ≤ `to` (or highest if `to` is null). Global target = `min` across partitions. This handles cross-partition timestamp skew by using the conservative lower bound.
5. Per partition: `findSafeStartCheckpoint` from exporter position against ALL range checkpoints (not pre-filtered ones).
6. Per partition: collect checkpoints in `[safeStart, globalTarget]`.
7. Validate: chain continuity, range covers `[safeStart, globalTarget]`, last checkpoint position ≥ exporterPosition.

**Key difference from before:** There is no per-partition timestamp filtering before the cross-partition merge. Instead, timestamp filtering happens once globally (step 4) to find a single target checkpoint ID, then everything works with checkpoint IDs only.

**Changes (done):**

1. **`BackupRangeResolver.java`**:
   - Added private `record PartitionData(int partition, RangeEntry range, List<CheckpointEntry> checkpoints)` to hold intermediate per-partition results (manifest loaded, range found, all non-MARKER checkpoints in range collected — but no timestamp filtering or safe start yet).
   - Renamed `resolvePartition()` → `loadAndFindRange()`: only loads manifest, finds covering range, returns `PartitionData`. Removed all `selectCheckpoints` and `findSafeStartCheckpoint` logic from this method.
   - **Deleted `selectCheckpoints()`** entirely.
   - Changed `getRestoreInfoForAllPartitions` flow to: `parTraverse(loadAndFindRange) → thenApply(computeGlobalResult(to, exportedPositions))`.
   - Rewrote `computeGlobalResult()` to accept `@Nullable Instant to` and `Map<Integer, Long> exportedPositions`:
     - Calls new `findGlobalTarget(partitionDataList, to)` to get the single global checkpoint ID.
     - Per partition: calls `findSafeStartCheckpoint(exporterPosition, allRangeCheckpoints)` against all range checkpoints.
     - Per partition: filters checkpoints to `[safeStart, globalTarget]`.
     - Builds `PartitionRestoreInfo` per partition.
     - Calls existing `validatePartitions()`.
   - Added private `findGlobalTarget(List<PartitionData>, @Nullable Instant to)`:
     - If `to` is null: returns `min` across partitions of the highest checkpoint ID.
     - If `to` is non-null: per partition, finds highest checkpoint with timestamp ≤ `to`. Global target = `min` across partitions.

2. **`BackupRangeResolverTest.java`**:
   - `optionalTimeBoundsProvider`: Added `exporterPosition` parameter. Changed `(null, min30, 300)` → `(null, min30, 200, 1500L)` — global target is now 200 (highest ≤ min30), exporter lowered so last checkpoint position (2000) ≥ exporter (1500).
   - `shouldNotReturnDuplicatedBackupsWhenMultipleNodes`: Lowered exporter to 1500 so global target 200's position (2000) ≥ exporter. Assertion changed to `{100, 200}`.
   - `shouldFailWhenFirstBackupInRangeIsAfterSafeStart` → renamed to `shouldIncludeCheckpointsBeforeTimeWindowWhenNeededForSafeStart`: now expects success because safeStart uses ALL range checkpoints (not just timestamp-filtered ones), so checkpoint 100 is found.
   - `shouldFailWhenNoCommonCheckpointExistsAcrossPartitions`: Updated assertion — now both partitions fail (P1: last pos < exporter, P2: safeStart beyond global target).
   - Added `shouldHandleCrossPartitionTimestampSkewInRdbmsPath` — 2 partitions with 4 checkpoints each, skewed timestamps, verifying global target uses conservative min across partitions.

**What stayed unchanged:**

- `resolvePointInTime()` — already uses its own global min algorithm
- `GlobalRestoreInfo` record shape
- `PartitionRestoreInfo` record and its `validate()` method
- `findCoveringRange()` — unchanged
- `findSafeStartCheckpoint()` — unchanged (just called with different input)
- `loadManifest()` — unchanged
- `RestoreManager.java` — no signature changes needed
- `BackupMetadataReader` — untouched

**Test:** `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C -pl zeebe/restore` — 55 tests, 0 failures.

