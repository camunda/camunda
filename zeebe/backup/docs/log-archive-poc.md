# Log archive PoC: implementation spec

This is the contract for the log archive proof of concept. Every workstream builds against it. If
something here is wrong or missing, stop and report it instead of inventing a different contract.

Design background: "Log Archive Design" (part 2 of the data pipeline series). In short: the
partition leader uploads committed log entries continuously as immutable objects, uploads RocksDB
snapshots on its own schedule, and commits every upload through the partition log. After commit it
publishes numbered commit entries, index chunks and roots to the bucket, so the bucket describes
itself.

## 1. Scope

In scope:

- Log archiver, snapshot uploader and publisher on the partition leader.
- A new protocol value type `LOG_ARCHIVE` with its own intents and record, processed by a new
  `LogArchiveRecordsProcessor` with replicated state in new column families.
- Local compaction capped by the archived position F.
- Restore of a partition from the log archive to a checkpoint (marker or backup).
- Stores: S3 (MinIO in tests) and filesystem.
- Runs next to the existing backup. Enabling the log archive does not change the existing backup
  behaviour, except that compaction also respects F.

Out of scope: event plane, holds, retention and deletion of any kind (including the orphan sweeper),
GCS and Azure, incremental snapshots, merging small objects, migrating existing backups, scaling the
partition count, metrics beyond basic counters.

Naming: always "log archive" (classes `LogArchive*`), never just "archive".

## 2. Code placement

| What | Where |
|------|-------|
| Protocol: `ValueType.LOG_ARCHIVE`, `LogArchiveIntent`, record value interface | `zeebe/protocol` |
| Record implementation `LogArchiveRecord` | `zeebe/protocol-impl` |
| State, processor, appliers, leader components, codec, JSON model, store SPI | `zeebe/backup`, package `io.camunda.zeebe.backup.logarchive` and sub-packages |
| S3 and filesystem implementations of the store SPI | `zeebe/backup-stores/s3`, `zeebe/backup-stores/filesystem` |
| Broker wiring: config, transition step, processor registration, compaction cap | `zeebe/broker` |
| Restore from the log archive | `zeebe/restore`, package `io.camunda.zeebe.restore.logarchive` |

Sub-packages of `io.camunda.zeebe.backup.logarchive`:

- `state` — column family access (`DbLogArchiveState`)
- `processing` — `LogArchiveRecordsProcessor` and appliers
- `format` — log object binary codec
- `model` — JSON model of bucket objects and its deterministic serializer
- `store` — `LogArchiveStore` SPI
- `leader` — archiver, snapshot uploader, publisher

No new Maven modules and no new third-party dependencies.

## 3. Configuration

Nested in the existing backup config, `zeebe.broker.data.backup.logArchive.*`:

| Property | Default | Meaning |
|----------|---------|---------|
| `enabled` | `false` | Turns the log archive on. |
| `timeline` | `tl-0` | Timeline id used when no timeline file exists in the partition directory. |
| `cutMaxBytes` | `16MB` | Cut an object when it reaches this size. |
| `cutInterval` | `PT2S` | Cut an object at least this often while there are committed, unarchived entries. |
| `snapshotUploadInterval` | `PT30M` | How often the newest local snapshot is uploaded. |
| `chunkSize` | `1000` | Objects per sealed index chunk. |
| `rootEvery` | `100` | Write a root every this many commit entries (and on every seal). |

The store type and its connection settings come from the existing backup config (`store`, `s3`,
`filesystem`). All log archive keys are prefixed with `log-archive/` below the store's base path.

When `logArchive.enabled` is true, checkpoint metadata tracking (the `CHECKPOINTS` column family) is
enabled too, the same as for `continuous`: `trackBackupMetadata = continuous || logArchive.enabled`.

## 4. Protocol

New value type `LOG_ARCHIVE`, new intent enum `LogArchiveIntent`, new record `LogArchiveRecord`.
See section 14 for the full list of registration points.

### 4.1 Intents

| Value | Intent | Kind | Written by |
|-------|--------|------|-----------|
| 0 | `START_TIMELINE` | command | archiver, on leader start when the timeline differs |
| 1 | `TIMELINE_STARTED` | event | processor |
| 2 | `ARCHIVE_LOG` | command | archiver, after uploading objects |
| 3 | `LOG_ARCHIVED` | event | processor |
| 4 | `ARCHIVE_SNAPSHOT` | command | snapshot uploader, after uploading a snapshot |
| 5 | `SNAPSHOT_ARCHIVED` | event | processor |
| 6 | `SEAL_INDEX` | command | publisher, after uploading an index chunk |
| 7 | `INDEX_SEALED` | event | processor |
| 8 | `CONFIRM_PUBLISHED` | command | publisher, after writing a root |
| 9 | `PUBLISHED` | event | processor |

Invalid commands are rejected with `RecordType.COMMAND_REJECTION` and `RejectionType.INVALID_STATE`
(or `INVALID_ARGUMENT` for malformed input). Rejections never change state.

### 4.2 Record fields

`LogArchiveRecord` is an `UnifiedRecordValue` (msgpack). All fields have defaults so that each intent
only sets what it needs.

| Field | Type | Used by |
|-------|------|---------|
| `timelineId` | string | all |
| `seq` | long, default -1 | events with a commit entry: set by the processor |
| `startIndex` | long, default -1 | `START_TIMELINE`, `TIMELINE_STARTED` |
| `parentTimelineId` | string, default "" | `START_TIMELINE`, `TIMELINE_STARTED` |
| `objects` | array of `LogObject` | `ARCHIVE_LOG`, `LOG_ARCHIVED` |
| `snapshot` | `ArchivedSnapshot` | `ARCHIVE_SNAPSHOT`, `SNAPSHOT_ARCHIVED` |
| `chunk` | `IndexChunk` | `SEAL_INDEX`, `INDEX_SEALED` |
| `publishedSeq` | long, default -1 | `CONFIRM_PUBLISHED`, `PUBLISHED` |
| `archivedIndex` | long, default -1 | events: F after applying, set by the processor |
| `archivedPosition` | long, default -1 | events: F after applying, set by the processor |
| `checkpoints` | array of `CoveredCheckpoint` | `LOG_ARCHIVED`: set by the processor |

`LogObject`: `key` (string, relative to the partition prefix, e.g. `data/…​.rec`), `term`,
`firstIndex`, `lastIndex`, `firstPosition`, `lastPosition` (both -1 if the object holds no
application entry), `entryCount`, `sizeBytes`, `sha256` (lowercase hex of the whole object).

`ArchivedSnapshot`: `id`, `index`, `term`, `processedPosition`, `exportedPosition`,
`lastFollowupEventPosition`, `maxExportedPosition`, `fileCount`, `sizeBytes`.

`IndexChunk`: `chunkNo` (starts at 1), `key` (`index/…​.json`), `firstIndex`, `lastIndex`,
`firstPosition`, `lastPosition`, `objectCount`, `firstCheckpointId`, `lastCheckpointId` (-1 if none),
`sha256`.

`CoveredCheckpoint`: `checkpointId`, `checkpointPosition`, `checkpointType` (as in `CheckpointType`).

## 5. Replicated state

New partition-local column families in `ZbColumnFamilies` (next free numbers):

| Column family | Key | Value |
|---------------|-----|-------|
| `LOG_ARCHIVE_META` | constant key | `LogArchiveMeta` |
| `LOG_ARCHIVE_OPEN_OBJECTS` | `firstIndex` (long) | `LogObject` |
| `LOG_ARCHIVE_CHUNKS` | `chunkNo` (long) | `IndexChunk` |
| `LOG_ARCHIVE_SNAPSHOTS` | archive `seq` (long) | `ArchivedSnapshot` |
| `LOG_ARCHIVE_OUTBOX` | `seq` (long) | commit entry JSON bytes (section 9.2) |

`LogArchiveMeta` fields: `timelineId`, `parentTimelineId`, `startIndex`, `archivedIndex` (F index,
`startIndex - 1` initially), `archivedPosition` (F position, -1 until the first application entry is
archived), `nextSeq` (starts at 1), `lastPublishedSeq` (0), `nextChunkNo` (1),
`lastCoveredCheckpointId` (-1).

`DbLogArchiveState` exposes read methods (used by appliers, the processor, the compaction cap) and
mutation methods (used only by appliers). The compaction cap opens its own read-only instance on the
same `ZeebeDb`, as `DbPositionSupplier` already does for `DbCheckpointState`.

Note: config must be identical on all replicas. This is the same rule that already applies to
`continuous`.

## 6. Processing rules

`LogArchiveRecordsProcessor` implements `RecordProcessor` for `ValueType.LOG_ARCHIVE`, registered next
to `CheckpointRecordsProcessor`. Every event is applied through an applier, both when processing and
when replaying, so followers end up with identical state.

Seq rule: `TIMELINE_STARTED`, `LOG_ARCHIVED`, `SNAPSHOT_ARCHIVED` and `INDEX_SEALED` each take
`seq = meta.nextSeq` and increment `nextSeq`. `PUBLISHED` takes no seq. The processor writes the seq
into the event record. The applier also stores the commit entry JSON for that seq in the outbox.

### `START_TIMELINE`

- Valid when `timelineId` is non-empty and differs from `meta.timelineId`, and `startIndex > 0`.
- Applier: clear all log archive column families, then set meta to the new timeline: `startIndex`,
  `archivedIndex = startIndex - 1`, `archivedPosition = -1`, `nextSeq = 1`, `lastPublishedSeq = 0`,
  `nextChunkNo = 1`. `lastCoveredCheckpointId` = the newest checkpoint id at or before the start (or -1).
  Then take seq 1 for this event.

### `ARCHIVE_LOG`

- Valid when: `timelineId == meta.timelineId`; at least one object; objects sorted and contiguous:
  `objects[0].firstIndex == meta.archivedIndex + 1` and `objects[i+1].firstIndex ==
  objects[i].lastIndex + 1`; for each object `firstIndex <= lastIndex`, `entryCount ==
  lastIndex - firstIndex + 1`.
- Applier: insert each object into `LOG_ARCHIVE_OPEN_OBJECTS`; set `archivedIndex` to the last
  object's `lastIndex` and, if any object has application entries, `archivedPosition` to the highest
  `lastPosition`; collect covered checkpoints: all entries of the `CHECKPOINTS` column family with
  `checkpointId > lastCoveredCheckpointId` and `checkpointPosition <= archivedPosition`, in id order;
  write them into the event and advance `lastCoveredCheckpointId`.
- Sealing trigger: if the open chunk now holds at least `chunkSize` objects, the processor's
  post-commit task also hands the chunk to the publisher (section 8.3).

### `ARCHIVE_SNAPSHOT`

- Valid when: timeline matches; `snapshot.index <= meta.archivedIndex`;
  `snapshot.index >= meta.startIndex - 1`; no archived snapshot with the same id.
- Applier: insert into `LOG_ARCHIVE_SNAPSHOTS` with the event's seq as key.

### `SEAL_INDEX`

- Valid when: timeline matches; `chunk.chunkNo == meta.nextChunkNo`; the open chunk holds at least
  `chunk.objectCount` objects; `chunk.firstIndex` and `chunk.lastIndex` equal the first index of the
  first and the last index of the `objectCount`-th open object.
- Applier: delete those objects from `LOG_ARCHIVE_OPEN_OBJECTS`; insert the chunk into
  `LOG_ARCHIVE_CHUNKS`; increment `nextChunkNo`.

### `CONFIRM_PUBLISHED`

- Valid when: timeline matches; `meta.lastPublishedSeq < publishedSeq < meta.nextSeq`.
- Applier: delete outbox rows with `seq <= publishedSeq`; set `lastPublishedSeq`.
- Stale or duplicate confirmations are rejected and are harmless.

### Leader notifications

When processing (not replaying), the processor notifies an in-memory `LogArchiveListener` registered by
the leader components: `onTimelineStarted`, `onLogArchived(archivedIndex, archivedPosition)`,
`onArchiveLogRejected(archivedIndex)`, `onSnapshotArchived(id)`, `onIndexSealed(chunkNo)`. The
listener hops to its own actor. On `onRecovered`, the processor delivers the current meta and the
outbox to the leader components (section 8.4).

## 7. Invariants

Every one of these has at least one test.

- **I1** F only moves forward, and archived index ranges are contiguous from `startIndex`.
- **I2** Only committed journal entries are archived (the archiver uses the committed reader).
- **I3** Seq starts at 1 per timeline and increases by exactly 1 per event that has a commit entry.
- **I4** The commit entry, index chunk and root for a given seq are pure functions of replicated
  state: two leaders writing the same key write identical bytes.
- **I5** An archived snapshot's index is never above F.
- **I6** A sealed chunk is never modified; sealing removes its objects from state.
- **I7** Nothing in the bucket is ever deleted by this PoC.
- **I8** With the log archive enabled, local compaction never removes an entry above F.

## 8. Leader components

All three run only on the partition leader, start in a new transition step next to
`BackupServiceTransitionStep`, and stop on any role change. Each is an `Actor` with
`SchedulingHints.ioBound()` and holds its state machine in plain fields on its own actor thread.

### 8.1 Log archiver

States: `IDLE → READING → UPLOADING → AWAIT_COMMIT → IDLE`. One object in flight at a time.

- Inputs: committed reader from `RaftPartitionServer.openReader()`; wake-ups from
  `AtomixLogStorage.addCommitListener` and a `cutInterval` timer.
- Cursor `A` (in memory) starts at `meta.archivedIndex` on leader start.
- On start: if `meta.timelineId` differs from the effective timeline (timeline file, else config),
  write `START_TIMELINE` with `startIndex` = the reader's first index, then wait for
  `onTimelineStarted`.
- `READING`: read entries `A+1 …` from the committed reader until `cutMaxBytes` or no more committed
  entries. Skip the cut if nothing is read. If `cutInterval` has not passed and the size limit was not
  reached, go back to `IDLE` and wait.
- `UPLOADING`: encode with the codec (section 10), PUT `data/<firstIndex>-<lastIndex>-t<term>.rec`,
  where `term` is the leader's current term.
- `AWAIT_COMMIT`: write `ARCHIVE_LOG` with the object. On `onLogArchived`, set `A` to the new F and go
  to `IDLE`. On `onArchiveLogRejected`, reset `A` to the delivered F and go to `IDLE`.
- Failures (store error, write rejected by backpressure): retry with backoff from the same `A`.
  Re-uploading the same key is fine: same bytes.

### 8.2 Snapshot uploader

States: `IDLE → RESERVED → UPLOADING → AWAIT_COMMIT → IDLE`.

- Every `snapshotUploadInterval` (and once shortly after leader start), reserve the newest local
  snapshot via the `PersistedSnapshotStore` reservation API.
- Skip if it is already archived, or if its index is above the last known F (try again next tick).
- PUT each snapshot file to `state/<snapshotId>/<fileName>`, the checksum file included, then PUT
  `state/<snapshotId>/descriptor.json` (section 9.5).
- Write `ARCHIVE_SNAPSHOT`, wait for `onSnapshotArchived` or rejection, release the reservation.

### 8.3 Publisher

A single actor that writes to the bucket strictly in seq order.

- Fed by processor post-commit tasks with immutable payloads: `(seq, commitEntryJson)`, optionally
  `rootJson` for that seq, optionally `(chunkNo, chunkJson)` to seal.
- For each commit entry: PUT `commits/<seq>.json`. Then, if a root is attached: PUT
  `root/<seq>.json`, overwrite `_latest.json`, write `CONFIRM_PUBLISHED(seq)`.
- Root cadence: a root is attached when `seq % rootEvery == 0`, on every `INDEX_SEALED`, and once on
  recovery (8.4).
- Sealing: PUT `index/<firstIndex>-<lastIndex>.json`, then write `SEAL_INDEX`.
- A root for seq N is computed by the processor while applying seq N, from current state. A root is
  never computed for an older seq, which keeps I4.
- Failures: retry the same PUT with backoff. Never skip a seq.

### 8.4 Recovery on a new leader

On `onRecovered` the processor hands the publisher every outbox row (`seq > lastPublishedSeq`) in
order, plus a root for the current `nextSeq - 1`, plus a seal request if the open chunk is full. The
archiver resumes from F and the uploader from scratch. Republishing an existing key is harmless (I4).

## 9. Bucket layout and JSON

Prefix per partition: `<basePath>/log-archive/<timelineId>/p<partitionId>/`. Numbers in keys are
zero-padded to 20 digits.

```
log-archive/tl-0/p1/
  data/00000000000002731001-00000000000002731480-t7.rec
  state/<snapshotId>/<files…>
  state/<snapshotId>/descriptor.json
  commits/00000000000000004127.json
  index/00000000000002600001-00000000000002731000.json
  root/00000000000000004100.json
  _latest.json
```

### 9.1 Deterministic JSON

One shared serializer (`LogArchiveJson`): Jackson, properties in declared order
(`@JsonPropertyOrder`), no pretty printing, UTF-8, no wall-clock values, numbers as JSON numbers,
absent optionals omitted. Golden-file tests pin the bytes.

### 9.2 Commit entry `commits/<seq>.json`

```json
{"formatVersion":1,"timelineId":"tl-0","partitionId":1,"seq":4127,"type":"LOG_ARCHIVED",
 "recordPosition":4931990,"recordTimestamp":1792137602113,
 "archivedIndex":2731480,"archivedPosition":4931877,
 "objects":[{"key":"data/…-t7.rec","term":7,"firstIndex":2731001,"lastIndex":2731480,
   "firstPosition":4930950,"lastPosition":4931877,"entryCount":480,"sizeBytes":3412880,
   "sha256":"9c1f…"}],
 "checkpoints":[{"checkpointId":1792137600000,"checkpointPosition":4931877,"checkpointType":"MARKER"}]}
```

`type` is one of `TIMELINE_STARTED`, `LOG_ARCHIVED`, `SNAPSHOT_ARCHIVED`, `INDEX_SEALED`.
`TIMELINE_STARTED` carries `startIndex` and `parentTimelineId`. `SNAPSHOT_ARCHIVED` carries
`snapshot`. `INDEX_SEALED` carries `chunk`. `recordPosition` and `recordTimestamp` are those of the
event record.

### 9.3 Index chunk `index/<firstIndex>-<lastIndex>.json`

`{"formatVersion":1,"timelineId":…,"partitionId":…,"chunkNo":…,"objects":[…LogObject…],"checkpoints":[…covered checkpoints in the chunk's position range…]}`

### 9.4 Root `root/<seq>.json` and `_latest.json`

Root: `formatVersion`, `timelineId`, `parentTimelineId`, `partitionId`, `seq`, `startIndex`,
`archivedIndex`, `archivedPosition`, `chunks` (all sealed `IndexChunk`s, in order), `openObjects` (all
open `LogObject`s, in order), `openCheckpoints` (covered checkpoints newer than the last sealed chunk),
`snapshots` (all archived snapshots, in seq order).

`_latest.json`: `{"formatVersion":1,"timelineId":…,"partitionId":…,"rootSeq":…}`. It is a hint and the
only object that is ever overwritten with different content.

### 9.5 Snapshot descriptor `state/<snapshotId>/descriptor.json`

The `ArchivedSnapshot` fields plus `files`: the sorted list of file names.

## 10. Log object format v1

Big-endian.

```
header   magic "ZLA1" (4) | headerLength u32 | partitionId u32 | term u64 | firstIndex u64
         | lastIndex u64 | firstPosition i64 | lastPosition i64 | entryCount u32
         | timelineId: length u16 + UTF-8
frames   per journal entry: index u64 | term u64 | asqn i64 | lowestPosition i64
         | highestPosition i64 | checksum u64 | length u32 | serialized journal record (length bytes)
footer   sparse index, every 64th frame and the first frame: count u32, then per entry
         index u64 | lowestPosition i64 | frameOffset u64
trailer  footerOffset u64 | magic "ZLAE" (4)
```

- The serialized journal record and checksum come from
  `IndexedRaftLogEntry.getReplicatableJournalRecord()`. Restore appends them with
  `Journal.append(checksum, serializedRecord)`, so the rebuilt journal is byte-identical.
- `asqn` is the journal's application sequence number. For application entries it equals the
  entry's lowest position; for other entries it is -1. `lowestPosition` and `highestPosition` come
  from the application entry, or are -1.
- The object's `sha256` (in `LogObject`) covers all bytes.
- The codec offers: encode from a list of entries; decode all frames; find the frame offset for a
  position using the footer (for ranged reads).

## 11. Store SPI

```java
public interface LogArchiveStore {
  CompletableFuture<Void> put(String key, byte[] content);
  CompletableFuture<Void> putFile(String key, Path file);
  CompletableFuture<Optional<byte[]>> getIfExists(String key);
  CompletableFuture<byte[]> getRange(String key, long offset, int length);
  CompletableFuture<Void> getToFile(String key, Path target);
  CompletableFuture<List<String>> list(String prefix);
  CompletableFuture<Void> closeAsync();
}
```

Keys are relative to the store's base path; the `log-archive/` prefix is part of the key. Implementations
in the S3 and filesystem backup store modules, created from the existing store config. No conditional
writes in the PoC.

## 12. Compaction cap

`DbPositionSupplier.getHighestBackupPosition()` returns the minimum of:

- the latest backup position, if `continuous` (as today), otherwise `Long.MAX_VALUE`;
- `meta.archivedPosition`, if the log archive is enabled (-1 while unset, which already makes the
  snapshot logic keep the previous snapshot's index), otherwise `Long.MAX_VALUE`.

## 13. Restore from the log archive

`LogArchiveRestore` in `zeebe/restore`, per partition, on every broker:

1. Read `_latest.json`, then `root/<rootSeq>.json`, then `commits/<seq>.json` from `rootSeq + 1`
   until one is missing. Fold them into one view: chunks, open objects, snapshots, F, recent
   checkpoints. If `_latest.json` is missing, list `root/` and take the highest.
2. Target checkpoint: the given checkpoint id, or the newest checkpoint id present on all partitions
   with position `<=` F of each partition. For an older id, find it via the chunks' checkpoint id
   ranges.
3. Snapshot: the newest archived snapshot with `processedPosition`, `lastFollowupEventPosition` and
   `maxExportedPosition` all below the checkpoint position.
4. Objects: from the object containing `snapshot.index` (or the first object, if the snapshot index
   is below it, as long as `firstIndex <= snapshot.index + 1`) up to the object containing the
   checkpoint position. Use the chunk files to find them when they are sealed.
5. Rebuild: install the snapshot like `PartitionRestoreService` does; reset the journal to the first
   object's first index; append each frame with `Journal.append(checksum, serializedRecord)`; stop
   after the frame whose `asqn` equals the checkpoint position. Fail if that frame is not found.
   Verify the consistency checks from the TODO in `PartitionRestoreService`: journal first index
   `<= snapshot index + 1` and the last entry's asqn equals the checkpoint position.
6. Write the new timeline id `<sourceTimeline>.r<checkpointId>` into
   `<partition data dir>/log-archive-timeline`. On first leadership, the archiver sees the mismatch and
   starts the new timeline with `START_TIMELINE` (parent = source timeline), re-archiving from the
   restored journal's first index.

The new timeline id is deterministic so that all brokers restoring independently agree on it.

## 14. Protocol registration checklist

Modelled on commit `dd2258a2ba2` ("add suspension batch traversal records"), which added a value
type end to end. Paths are relative to `zeebe/`.

**Value number and position.** `ValueType` is generated by SBE from
`protocol/src/main/resources/protocol.xml`. Add `<validValue name="LOG_ARCHIVE">251</validValue>`
**after** `CHECKPOINT` (254). The Engine accepts `EnumSet.range(JOB, SCALE)` by ordinal
(`engine/.../Engine.java:59`), and ordinals follow XML order. Placed after `CHECKPOINT`, the new type
stays outside that range, so the Engine never accepts it. 252 is retired, 253 is `SCALE`, 255 is the
SBE null value.

| Module | File | Change |
|--------|------|--------|
| protocol | `protocol.xml` | `LOG_ARCHIVE` = 251, after `CHECKPOINT` |
| protocol | `record/intent/management/LogArchiveIntent.java` | new enum, template `CheckpointIntent` (`from(short)`, `value()`, `isEvent()`) |
| protocol | `record/value/management/LogArchiveRecordValue.java` | public interface, `@Value.Immutable` + `@ImmutableProtocol`, template `CheckpointRecordValue`; nested value interfaces for `LogObject`, `ArchivedSnapshot`, `IndexChunk`, `CoveredCheckpoint` |
| protocol | `record/ValueTypeMapping.java` | `ValueType.LOG_ARCHIVE → Mapping(LogArchiveRecordValue, LogArchiveIntent)` next to `CHECKPOINT` |
| protocol | `record/intent/Intent.java` | register `LogArchiveIntent` next to `CheckpointIntent` |
| protocol | `record/ValueTypes.java` | no change: not a user command |
| protocol | `ZbColumnFamilies.java` | (W5) the five column families from section 5, next free ids after 165, `PARTITION_LOCAL` |
| protocol tests | `IntentEncodingDecodingTest` | add `LogArchiveIntent` parameter set |
| protocol tests | `ValueTypeMappingTest` | mapping test for `LOG_ARCHIVE` |
| protocol-impl | `record/value/management/LogArchiveRecord.java` | `extends UnifiedRecordValue implements LogArchiveRecordValue`, template `CheckpointRecord` |
| protocol-impl | `record/UnifiedRecordValue.java` | `case LOG_ARCHIVE -> new LogArchiveRecord()` |
| protocol-impl tests | `golden/LogArchiveRecord.golden`, `UnifiedRecordValueTest`, `JsonSerializableToJsonTest` | golden file and cases |
| test-util | `CompactRecordLogger` | abbreviation (`LAR`), value logger, summarize method (+ its test) |
| test-util | `RecordingExporter` + `LogArchiveRecordStream` | `logArchiveRecords()` |
| exporters | ES and OS `TestSupport.provideValueTypes()` | add `LOG_ARCHIVE` to the excluded (internal) types; not exported |
| engine tests | `EventAppliersTest` | filter `LogArchiveIntent` like `CheckpointIntent`, only if the test requires it |
| broker | `PartitionTransitionContext` + impl | (W6) getter/setter for the log archive processor |
| broker | `StreamProcessorTransitionStep` | (W6) add the processor to `List.of(engine, checkpointProcessor, …)` |

Unknown value types are not exported (`shouldIndexValueType` defaults to false), so the exporters need
no configuration change.

## 15. Test plan

Unit tests (each workstream):

- Codec: round trip, footer lookup, empty and non-application entries, corrupt trailer detection.
- JSON: golden files for every object type; serialize twice gives identical bytes.
- State and processor: every intent, every rejection rule, seq rule, outbox, sealing, replay gives
  identical state (process a sequence, then replay the events on a fresh state and compare).
- Store: put, get, range, list, missing keys, on filesystem and MinIO.
- Leader components: state machine transitions with a fake store and fake writer, including
  rejection and retry paths.

Integration tests (broker, filesystem store first, then MinIO):

- Archive progresses: F follows the commit index; commit entries are gapless from 1.
- Leader change: kill the leader, archiving resumes from F, ranges stay contiguous, an orphaned
  object of the old term is listed nowhere.
- Snapshots: at least one archived snapshot with index `<=` F.
- Compaction: with a stuck store, the journal is not compacted above F.
- Restore: restore to a marker on a fresh cluster; the restored partition's state matches the source
  at that marker (e.g. process instance and variable counts); the new timeline archives from the
  restored journal.
- Side by side: with `continuous` and the log archive both enabled, existing backup tests still pass.

## 16. Working rules for workstreams

- Work only in the files and packages your workstream owns (section 17). Need a change elsewhere?
  Report it instead of making it.
- Maven: use the worktree-local repository with the shared cache as a read-only tail, as set up in
  your worktree's `.mvn/maven.config`. Never write to `~/.m2/repository`.
- Before each commit: `./mvnw license:format spotless:apply -pl <your modules>`, then build and test
  your modules. Never use `-am` with `verify`.
- Conventional Commits, no scope, a body explaining why, and the co-author line.
- Tests: JUnit 5, AssertJ, `should…` names, `// given // when // then`.

## 17. Workstreams and file ownership

| WS | Owns | Depends on |
|----|------|-----------|
| W1 protocol | everything in section 14 except the rows marked (W5) and (W6): `zeebe/protocol`, `zeebe/protocol-impl`, `zeebe/test-util`, exporter `TestSupport` files, `EventAppliersTest` | spec |
| W2 codec | `logarchive/format` | spec |
| W3 store | `logarchive/store`, store implementations in `backup-stores/s3` and `backup-stores/filesystem` | spec |
| W4 JSON model | `logarchive/model` | spec |
| W5 state machine | `logarchive/state`, `logarchive/processing`, `ZbColumnFamilies` additions | W1, W4 |
| W6 archiver + wiring | `logarchive/leader/LogArchiver*`, broker config, transition step, processor registration, compaction cap | W2, W3, W5 |
| W7 snapshot uploader | `logarchive/leader/SnapshotUploader*` | W3, W5 |
| W8 publisher | `logarchive/leader/LogArchivePublisher*` | W3, W4, W5 |
| W9 restore | `zeebe/restore` `logarchive` package | W2, W3, W4 |
| W10 integration tests | new ITs in `zeebe/qa/integration-tests` | all |
