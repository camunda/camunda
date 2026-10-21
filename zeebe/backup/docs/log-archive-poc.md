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

Out of scope (except sections 19 to 23, the event plane integration): event plane, holds, retention and deletion of any kind (except sections 20 and 21),
GCS and Azure, incremental snapshots, merging small objects (except section 20), migrating existing backups, scaling the
partition count (except for partitions added after the handover, section 18.4a), metrics beyond
basic counters.

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
| `timeline` | `tl-0` | Timeline id used when no timeline file exists in the partition directory. A fork of it (`<timeline>.f<seq>…`, section 8.4) counts as the same timeline. |
| `cutMaxBytes` | `16MB` | Cut an object when it reaches this size. |
| `cutInterval` | `PT2S` | Cut an object at least this often while there are committed, unarchived entries. |
| `idleCutInterval` | `PT5M` | Cut an object at least this often while every committed, unarchived entry holds only `LOG_ARCHIVE` records or is a non-application Raft entry (section 8.1). |
| `snapshotUploadInterval` | `PT30M` | How often the newest local snapshot is uploaded. |
| `chunkSize` | `1000` | Objects per sealed index chunk. |
| `rootEvery` | `100` | Write a root every this many commit entries (and on every seal). |

The store type and its connection settings come from the existing backup config (`store`, `s3`,
`filesystem`). All log archive keys are prefixed with `log-archive/` below the store's base path.

The log archive does not depend on the continuous backup's bookkeeping. The `CHECKPOINTS` column
family is tracked only when `continuous` is on, as before. The log archive keeps its own list of
checkpoints in `LOG_ARCHIVE_PENDING_CHECKPOINTS` (`trackLogArchiveCheckpoints = logArchive.enabled`,
section 5).

## 4. Protocol

New value type `LOG_ARCHIVE`, new intent enum `LogArchiveIntent`, new record `LogArchiveRecord`.
See section 14 for the full list of registration points.

### 4.1 Intents

| Value | Intent | Kind | Written by |
|-------|--------|------|-----------|
| 0 | `START_TIMELINE` | command | archiver, on leader start when the timeline differs or the bucket is ahead of the state |
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
| `LOG_ARCHIVE_PENDING_CHECKPOINTS` | `checkpointId` (long) | `checkpointPosition`, `checkpointType` |
| `LOG_ARCHIVE_BACKUP_CHECKPOINTS` | `checkpointId` (long) | `checkpointPosition`, `checkpointType`, `checkpointTimestamp` |

`LogArchiveMeta` fields: `timelineId`, `parentTimelineId`, `startIndex`, `archivedIndex` (F index,
`startIndex - 1` initially), `archivedPosition` (F position, -1 until the first application entry is
archived), `nextSeq` (starts at 1), `lastPublishedSeq` (0), `nextChunkNo` (1),
`firstArchivedPosition` (-1).

`LOG_ARCHIVE_PENDING_CHECKPOINTS` holds every checkpoint that was created but is not yet covered by a
`LOG_ARCHIVED` event. With the log archive enabled, the checkpoint `CREATED` applier
(`CheckpointCreatedEventApplier`, flag `trackLogArchiveCheckpoints`) inserts every created
checkpoint, of any type, when processing and when replaying. It does not depend on the
`CHECKPOINTS` column family of the old backup mechanism, which holds a backup checkpoint only once
its backup is confirmed.

`LOG_ARCHIVE_BACKUP_CHECKPOINTS` holds every covered checkpoint whose type creates a backup
(`CheckpointType.shouldCreateBackup()`: `SCHEDULED_BACKUP`, `MANUAL_BACKUP`, `HANDOVER`; markers are
not stored). The `LOG_ARCHIVED` applier inserts a row when it covers a pending checkpoint, whether or
not a handover exists. Open objects keep their covered checkpoints only until they are sealed into a
chunk, whose state row is a summary, so this column family is what keeps a backup visible to status
and listing (section 18.6) after its chunk is sealed. Restore does not read it: it reads the chunk
files in the bucket.

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
- Applier: clear all log archive column families except `LOG_ARCHIVE_PENDING_CHECKPOINTS` (a checkpoint
  created before the start can still lie in the archived range; older ones are dropped later) and
  `LOG_ARCHIVE_BACKUP_CHECKPOINTS` (the backups belong to the branch's history, which the new
  timeline continues; after a restore the state only holds those up to the restore point), then
  set meta to the new timeline: `startIndex`, `archivedIndex = startIndex - 1`,
  `archivedPosition = -1`, `nextSeq = 1`, `lastPublishedSeq = 0`, `nextChunkNo = 1`,
  `firstArchivedPosition = -1`. Then take seq 1 for this event.

### `ARCHIVE_LOG`

- Valid when: `timelineId == meta.timelineId`; at least one object; objects sorted and contiguous:
  `objects[0].firstIndex == meta.archivedIndex + 1` and `objects[i+1].firstIndex ==
  objects[i].lastIndex + 1`; for each object `firstIndex <= lastIndex`, `entryCount ==
  lastIndex - firstIndex + 1`.
- Applier: insert each object into `LOG_ARCHIVE_OPEN_OBJECTS`; set `archivedIndex` to the last
  object's `lastIndex` and, if any object has application entries, `archivedPosition` to the highest
  `lastPosition`; set `firstArchivedPosition` from the first object with application entries if it
  is still -1; collect covered checkpoints: take every entry of `LOG_ARCHIVE_PENDING_CHECKPOINTS`
  with `checkpointPosition <= archivedPosition`, in id order, and delete it. An entry with
  `checkpointPosition < firstArchivedPosition` was taken before the archive started and is dropped.
  Every other entry is covered: it is written into the event and attached to the object of this
  event that contains its position (`firstPosition <= checkpointPosition <= lastPosition`). Commands
  are processed in log order, so a checkpoint's `CREATED` event always precedes the `LOG_ARCHIVED`
  event that archives its position, and such an object always exists. If none does, an invariant is
  broken: the applier throws instead of silently not listing the checkpoint. A covered checkpoint
  whose type creates a backup is also inserted into `LOG_ARCHIVE_BACKUP_CHECKPOINTS`.
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
- **I7** Nothing in the bucket is ever deleted by this PoC (changed by section 20.4).
- **I8** With the log archive enabled, local compaction never removes an entry above F (above M
  since section 20.3).

## 8. Leader components

All three run only on the partition leader, start in a new transition step next to
`BackupServiceTransitionStep`, and stop on any role change. Each is an `Actor` with
`SchedulingHints.ioBound()` and holds its state machine in plain fields on its own actor thread.

### 8.1 Log archiver

States: `IDLE → READING → UPLOADING → AWAIT_COMMIT → IDLE`. One object in flight at a time.

- Inputs: committed reader from `RaftPartitionServer.openReader()`; wake-ups from
  `AtomixLogStorage.addCommitListener` and a `cutInterval` timer.
- Cursor `A` (in memory) starts at `meta.archivedIndex` on leader start.
- On start the archiver waits for the publisher's recovery check (section 8.4), then:
  - if `meta.timelineId` is neither the effective timeline (timeline file, else config) nor a fork
    of it, start the effective timeline;
  - else, if the bucket is ahead of the state, start a fork `<meta.timelineId>.f<meta.nextSeq>`;
  - else resume `meta.timelineId` from F.
- Starting a timeline: first make sure its partition prefix is empty in the bucket (`list`). If it
  is not, another history lives there (the state was rolled back behind its start, or the same
  restore ran twice), so take the next candidate instead: for the effective timeline
  `<timeline>.f1`, for a fork `<fork>-2`, `<fork>-3`, …. Then write `START_TIMELINE` with
  `startIndex` = the reader's first index and `parentTimelineId = meta.timelineId`, and wait for
  `onTimelineStarted`. Nothing is uploaded before that.
- `READING`: read entries `A+1 …` from the committed reader until `cutMaxBytes` or no more committed
  entries. Skip the cut if nothing is read. If the cut interval has not passed since the last cut and
  the size limit was not reached, go back to `IDLE` and wait.
- Cut interval: `cutInterval` as soon as one pending entry (committed, not yet archived) holds a
  record of another value type than `LOG_ARCHIVE`, otherwise `idleCutInterval`. Every cut is
  followed by `ARCHIVE_LOG`/`LOG_ARCHIVED` (and now and then `CONFIRM_PUBLISHED`/`PUBLISHED`,
  `SEAL_INDEX`/`INDEX_SEALED`, rejections), which are committed entries themselves; cutting them on
  `cutInterval` would make an idle partition archive its own bookkeeping forever. Such entries are
  still archived, contiguously, by the next cut. The Raft adapter reads the value type of every
  record of an application entry (record metadata, as the log stream reader does) and passes the
  result next to the frame; the object format does not change. Non-application entries (e.g. a
  leader's initial entry) count as the archive's own.
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
- Nothing is written before the recovery check of section 8.4 succeeded. Requests that arrive
  earlier queue up behind it.

### 8.3a Processor-to-leader interfaces

Defined by W5 in `logarchive/processing`, implemented by W6 to W8:

```java
/** Receives bucket writes the processor decided on; implemented by the publisher (W8). */
public interface LogArchivePublication {
  void publish(PublishRequest request);
}

/** Immutable. root and seal are null when not due. */
public record PublishRequest(
    String timelineId, long seq, byte[] commitEntryJson,
    @Nullable byte[] rootJson,          // root/<seq>.json + _latest.json, then CONFIRM_PUBLISHED(seq)
    @Nullable SealRequest seal) {}

/** chunk carries all IndexChunk fields incl. sha256 of chunkJson; publisher PUTs chunkJson to
 *  chunk.key, then writes SEAL_INDEX with exactly these chunk fields. */
public record SealRequest(IndexChunk chunk, byte[] chunkJson) {}
```

A publish request without a commit entry (`seq = -1`, `commitEntryJson = null`) carries only a root
or a seal, for recovery. The `LogArchiveListener` from section 6 is the other direction: processor
to archiver and snapshot uploader. Both are registered on the processor by the transition step and
called only while processing, never during replay.

### 8.4 Recovery on a new leader

On `onRecovered` the processor hands the publisher every outbox row (`seq > lastPublishedSeq`) in
order, plus a root for the current `nextSeq - 1`, plus a seal request if the open chunk is full. The
archiver resumes from F and the uploader from scratch. Republishing an existing key is harmless (I4).

**Recovery check (rolled-back state).** A classic restore, or any other rollback of the broker
state, brings back log archive state from the past (lower F, lower `nextSeq`) while the bucket
already holds the later history of the same timeline. Republishing the outbox and continuing the
timeline would then overwrite `commits/<seq>.json` and `_latest.json` with a diverging history.
So before anything is written after recovery, the publisher checks whether
`commits/<meta.nextSeq>.json` of `meta.timelineId` exists:

- The listener's `onRecovered` reaches the publisher before the outbox (both hop onto its actor in
  call order), so the check starts before any republish; requests queue up behind it. A store
  error is retried with backoff; nothing is published before the check succeeded.
- Not found: publish as usual.
- Found, i.e. the bucket is ahead of the state: drop every queued and later request for
  `meta.timelineId`; nothing is ever published to it again by this leader. The archiver forks
  `<meta.timelineId>.f<meta.nextSeq>` (section 8.1), with `parentTimelineId = meta.timelineId` and
  `startIndex` = the first index on disk. After `TIMELINE_STARTED` everything continues on the fork
  as usual (seq restarts at 1). The snapshot uploader waits for the fork as well.
- The outcome reaches the archiver and the uploader through an in-memory callback
  (`LogArchiveRecoveryListener`); neither writes to the bucket before it.

A normal leader change never triggers the fork: a commit entry is published only by a post-commit
task, after the event that took its seq is committed. A new leader holds every committed entry and
announces leadership only after committing its initial entry, so it has replayed that event, and its
`nextSeq` is above every published seq.

The fork id is derived from the replicated state, names the point of divergence, and keeps the
source timeline as prefix, so the archiver recognizes a fork of the effective timeline as "the same
timeline" (it lives only in the replicated state, not in the timeline file or configuration).
Restore reads a fork like any other timeline; its id has to be given explicitly.

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
  timeline.json
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

`type` is one of `TIMELINE_STARTED`, `LOG_ARCHIVED`, `SNAPSHOT_ARCHIVED`, `INDEX_SEALED`, and since
step 2 `LOG_MERGED` (fields in 20.1) and `LOG_RELEASED` (fields in 21.2).
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

### 9.6 Timeline registry `timeline.json`

`{"formatVersion":1,"timelineId":"tl-0.r5","partitionId":1,"parentTimelineId":"tl-0","startIndex":2731001,"startedSeq":1}`

One small file per timeline and partition, at `log-archive/<timeline>/p<partition>/timeline.json`. It
holds what the `TIMELINE_STARTED` commit entry holds (`startedSeq` is its seq), so the bytes are a pure
function of that event and a republish writes the same bytes. It exists so that restore can find the
timelines and their parents with one delimiter listing of `log-archive/` and one GET per timeline,
instead of listing every data object (section 18.5).

The publisher writes it in the same request as the `TIMELINE_STARTED` commit entry, right after
`commits/<seq>.json` and before the root: a `timeline.json` therefore only exists for a timeline whose
`TIMELINE_STARTED` entry exists. The outbox row of that entry stays until `PUBLISHED`, so a crash or a
leader change between the two PUTs is repaired by the republish on recovery (8.4), which writes the same
bytes. It is never overwritten with different content. Timelines written before the registry existed
have no such file (18.5 describes how restore treats them).

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

**Decision (confirmed):** log objects hold the journal entries as they are, byte for byte, including
Raft-internal entries and the log archive's own records. Restore appends them unchanged. Consumers
that want individual Zeebe records unwrap frame → Raft entry → record batch through a reader
library, which becomes a public contract with a version-support promise.

## 11. Store SPI

```java
public interface LogArchiveStore {
  CompletableFuture<Void> put(String key, byte[] content);
  CompletableFuture<Void> putFile(String key, Path file);
  CompletableFuture<Optional<byte[]>> getIfExists(String key);
  CompletableFuture<byte[]> getRange(String key, long offset, int length);
  CompletableFuture<Void> getToFile(String key, Path target);
  CompletableFuture<List<String>> list(String prefix);
  CompletableFuture<List<String>> listChildPrefixes(String prefix);
  CompletableFuture<Void> closeAsync();
}
```

`listChildPrefixes("log-archive/")` returns the names of the folders directly below the prefix
(`["tl-0", "tl-0.r123", …]`), sorted, without the prefix and the trailing `/`; the prefix must end with
`/`. S3 lists with delimiter `/` and reads `CommonPrefixes`, the filesystem store lists directories; neither
enumerates the objects below the folders.

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

## 13a. What restore relies on (writer guarantees)

Restore (W9, merged) validates everything it reads and fails loudly. Writers must guarantee:

- **Root N** is the state *after* applying seq N. Restore replays commits from `rootSeq + 1`, and each
  object must start at F + 1.
- **Seq 1** of a timeline is `TIMELINE_STARTED`.
- **Chunk checkpoints:** a chunk file lists every covered checkpoint with position in
  `[firstPosition, lastPosition]`; covered are all checkpoints, of any type, created on this
  timeline at or after the first archived position, taken from their `CREATED` events
  independently of whether an old-style backup of them was confirmed (section 6, `ARCHIVE_LOG`); `firstCheckpointId`/`lastCheckpointId` are the min/max of exactly
  those (-1 if none). Root `openCheckpoints` holds every covered checkpoint not in a sealed chunk.
- **Checksums:** `IndexChunk.sha256` is the hash of exactly the bytes stored at `chunk.key`;
  `LogObject.sizeBytes`/`sha256` describe exactly the bytes stored at the object key.
- **Frames** carry the unchanged bytes and checksum of `getReplicatableJournalRecord()`; the object
  header's partition id and first/last index match the `LogObject`.
- **Snapshot descriptor:** `files` lists every key under `state/<id>/` except `descriptor.json`,
  including `<id>.checksum`; `fileCount == files.size()`; the id is the local snapshot's id.
- **Timeline file** `log-archive-timeline` is written without a trailing newline; readers `strip()`
  it.
- **Checkpoint position** is the asqn of a journal entry (the checkpoint record starts its batch), as
  for existing backups.
- **After a restore**, the new timeline starts at the restored journal's first index, so a later
  restore from it needs an archived snapshot with index ≥ `startIndex - 1` (unless `startIndex` is 1).

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

**Known red tests on the PoC branch:** the ES/OS exporter value-type tests and the engine's
`EventAppliersTest` enumerate every value type and intent and fail on `LOG_ARCHIVE`. Fix them before
anything leaves the PoC branch.

**PoC rule: only what the build and the protocol tests need.** Of the rows above, implement only the
protocol, protocol-impl and broker rows plus tests that would otherwise fail. Skip the test-util,
exporter and engine-test rows unless a module does not compile without them. The integration tests
read log archive records generically instead of through a dedicated `RecordingExporter` stream.

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

## 15a. Known limits of the PoC

- A rolled-back state forks a new timeline (section 8.4). The forked-from timeline is left as it
  was; the fork's `parentTimelineId` (and its `timeline.json`) links it back. The handover restore
  follows forks and parents from its seeds (section 18.5); the log archive only restore mode restores
  from the timeline the operator names.

- The root lists every archived snapshot and the state keeps every snapshot descriptor. Without
  retention both grow without bound (about 17,500 snapshots a year at one every 30 minutes).
  Retention, or chunking snapshots like log objects, must cover this.
- `LOG_ARCHIVE_BACKUP_CHECKPOINTS` grows by one row per backup checkpoint and is never pruned, as
  retention is out of scope.
- A new leader learns only the newest archived snapshot on recovery
  (`onNewestArchivedSnapshotRecovered`), which is all the uploader needs.
- Scale-up after the handover (section 18.4a): the new partition uploads old-style backups until
  its handover is recorded (before activation, and for a few seconds after). Until its first cut it
  has no first archived position, so the scheduler stops backups for those seconds (row 3a). A
  checkpoint taken while scaling is in progress is marked failed on the old side but is covered by
  the archive like any other; neither side guards a restore to it. The handover restore takes the
  partitions to restore from the configured partition count: it must hold every partition of the
  target (after a dynamic scale-up, configure the scaled count); higher partitions are not looked
  at. The log archive only restore mode (`--logArchive.*`) does not leave out partitions added later.
- The bridged restore (18.5a) only bridges to a timeline that records the handover itself. A
  timeline that descends from it (restored `.r<T>` or forked) and starts after E + 1 is not bridged,
  even though its log up to the fork equals its parent's; a time range that resolves to a checkpoint
  of it fails.
  The overlap check holds the asqn and checksum of every old entry from A on in memory (16 bytes per
  entry up to the old chain's last checkpoint).
- Inherited from continuous backup, on purpose (18.5a): a time range with `--from` and RDBMS has a
  narrow window. `--from` drops the backups before it, and the first remaining backup must be at or
  below E (`RestorePointResolver.discardUnnecessaryBackupsBeforeTimestamp`), even when its log starts
  before E; with the archive, an archived snapshot's backup counts the same way. So `--from` must lie
  between the range start and the last backup at or below E; restoring with `--to` only avoids it.
  Likewise `--from` before the first backup of every range is rejected, and a time range needs T on
  every partition the RDBMS holds a position for (no partition is left out, unlike a restore by
  backup id after a scale-up).
- A time range sees only the current branch (18.5a), as continuous backup sees only the branch that
  wrote `metadata.json` last; a checkpoint of another branch is restored by backup id or with
  `--logArchive.timeline`. Covered checkpoints without `checkpointTimestamp` (archived by an older
  PoC version) cannot be placed in time and are left out. In messages, a backup mapped from the
  archive shows its checkpoint's type, or `SCHEDULED_BACKUP` for a marker.

## 16. Working rules for workstreams

- Work only in the files and packages your workstream owns (section 17). Need a change elsewhere?
  Report it instead of making it.
- Maven: the repo's branch-scoped local repository extension installs into
  `~/.m2/repository/installed/<branch>/`. Each worker branch gets a pre-seeded copy of the integration
  branch's area. Build offline (`-o`) and never override `maven.repo.local`.
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

## 18. Handover from continuous backup to the log archive

Background: part 3 of the design series ("From Continuous Backup to Log Archive"). Each partition's
history gets one dividing line, the handover checkpoint H. Before H, the old continuous backups are
the source of truth; after H, the log archive. The handover is recorded on both sides and lets a
restore cross from the old backups into the archive.

**Rule: the old backups are never modified.** The handover adds files next to them but never
changes, moves, rewrites or deletes anything of the old backups (manifests, contents, or
`metadata.json`). Old backups keep their own retention. A restore to a point at or before H runs
the unchanged old restore code on unchanged data.

### 18.1 Upgrade gate and activation (H1)

- **Gate:** the log archive may write its first record only once every broker in the cluster
  topology reports a version ≥ the current minor version. Reuse the logic of
  `ClusterBrokerVersionMigrationStatusProvider` (extract a shared helper if practical). Brokers
  missing from the topology count as not upgraded.
- **Activation:** the checkpoint scheduler checks the gate. When it is open and the log archive is
  enabled, it sends a new management request `ACTIVATE_LOG_ARCHIVE` to every partition. The partition
  leader's archiver then writes `START_TIMELINE` (configured timeline, start index = first index on
  disk) if the timeline is still empty. The request is idempotent. Once every partition reports a
  started timeline, the activation loop only watches the topology (a local check every 10 s, no
  request) and starts another round when it holds a partition that is not activated yet, i.e. one
  added by a scale-up (section 18.4a). The activated partitions are kept in memory for this watch
  only; a restarted or moved scheduler starts over with a round.
- **Archiver change:** on recovery, the archiver no longer starts the first timeline by itself. It
  waits for activation. Starting a *new* timeline after a restore (timeline file differs from state)
  stays automatic, because a restore always runs on upgraded brokers.
- **Test hook:** a test-only way to make a broker advertise a different version (e.g. a test-only
  config property), so an integration test can prove the gate stays closed with one old broker.

### 18.2 Handover type and state (H2)

- **`CheckpointType.HANDOVER`** (value 3), `shouldCreateBackup() == true`. A HANDOVER checkpoint is
  taken like a scheduled backup and uploads an old-style backup.
- **Two phases, so partial failures never leave partitions with different dividers:**
  1. The scheduler takes a HANDOVER checkpoint H. Nothing changes yet; old backups and the archive
     both keep running. If H fails on any partition, the scheduler retries later with a new id.
  2. Once H is confirmed on every partition and F has passed H everywhere, the scheduler sends
     `COMPLETE_HANDOVER(H)` to every partition. The leader writes the new log archive command
     `COMPLETE_HANDOVER` → event `HANDOVER_COMPLETED` (gets a seq, so it is a commit entry of type
     `HANDOVER` with H's id and position).
- **Validation of `COMPLETE_HANDOVER`:** timeline started; no handover recorded yet (a repeat for
  the same H is rejected and harmless); then either H is the latest confirmed backup of type
  HANDOVER in the checkpoint state and `archivedPosition ≥ H.position`, or the partition has no
  confirmed HANDOVER backup at all and its timeline holds its whole history (section 18.4a), in
  which case the handover is recorded with `handoverPosition = 0`.
- **Applier of `HANDOVER_COMPLETED`:** records `handoverCheckpointId` and `handoverPosition` in the log
  archive meta and in the checkpoint state, and adds the old-side file write to the outbox.
- **Old side: `metadata/<partition>/handover/<H>.json`**, a new file next to `metadata.json`:
  `{formatVersion, partitionId, checkpointId, checkpointPosition, logArchiveTimeline}`. Keyed by H
  because a restore to a point before H leads to a second handover on the new branch, and the first
  record must stay. `metadata.json` itself keeps exactly today's format and content, so any existing
  restore tool reads it as before. The outbox retries the write after a leader change until it
  exists; the write is idempotent (same bytes for the same H).
- **New side:** the `HANDOVER` commit entry in the archive (above), repeated in every later root.
  It is the source of truth; the old-side file is a pointer and may briefly be missing.
- **Compaction pin release:** once a handover is recorded on the partition, `DbPositionSupplier`
  ignores the old backup position (treats it as `Long.MAX_VALUE`) and caps at F only.

### 18.3 Checkpoint state query (H2)

`CheckpointStateResponse` (cluster-management-protocol.xml) gets a new group `logArchiveStates`
with `sinceVersion="3"` (schema version 2 → 3): `partitionId`, `timelineStarted` (uint8),
`archivedPosition` (int64), `newestArchivedSnapshotProcessedPosition` (int64, -1 if none),
`handoverCheckpointId` (int64, -1 if none), `firstArchivedPosition` (int64, -1 if nothing is
archived yet; added later in the same schema version, PoC only). The partition's `BackupApiRequestHandler` fills it from
`DbLogArchiveState`; `BackupRequestHandler` aggregates it like the existing groups.

### 18.4 Handover decision (H3)

The checkpoint scheduler, per physical tenant, on its backup tick:

1. If no partition has a timeline yet: check the gate, send `ACTIVATE_LOG_ARCHIVE`, done.
2. If a handover is recorded on every partition: keep taking `SCHEDULED_BACKUP` checkpoints on
   schedule. They remain restore points; the partitions skip the old-style upload for them and
   report their status from the archive (18.6). After a scheduler failover this is re-derived from
   the query.
3. If a HANDOVER checkpoint H is the last confirmed backup on every partition (same id) and F ≥ H on
   every partition, or H is already recorded on some partitions: send `COMPLETE_HANDOVER(H)` to every
   partition right away, and repeat it with backoff until it is recorded everywhere (a repeat is
   rejected harmlessly). No backup is taken meanwhile, since a newer confirmed backup would hide H
   from this check; except when every partition without H was added after the handover (18.4a),
   which never waits for H to be its last backup. Partitions that recorded different handovers (not expected without a restore)
   fall through to normal scheduled backups.
4. Else, if on every partition the timeline is started, `archivedPosition ≥` the last confirmed old
   backup's position, and an archived snapshot exists: take the next backup as type `HANDOVER`
   instead of `SCHEDULED_BACKUP`. The snapshot precondition is what makes every target after H
   restorable from the archive alone (18.5).
5. Else: take a normal scheduled backup, as today.

All decisions are derived from the query, never from scheduler memory, so they survive the
scheduler moving to another broker.

Partitions added after the handover (scale-up) are handled by section 18.4a: while H is recorded on
the other partitions only, a partition whose archive starts at its first record gets
`COMPLETE_HANDOVER(H)` and backups go on meanwhile (`HandoverDecision` row 3s).

The decision is `HandoverDecision`, a pure function of the query result; its javadoc has the
decision table. The `COMPLETE_HANDOVER` of step 3 waits for the first backup tick after H is
confirmed and archived, so it lags H by up to one backup interval.

### 18.4a Partitions added after the handover (scale-up)

A partition added by a scale-up after the handover has no history before H, so it should be
archive-only from birth.

**How a new partition starts.** Dynamic scaling bootstraps the partition from a *bootstrap snapshot*
of partition 1 (`SnapshotInitializationUtil`, `SnapshotApiHandlerTransitionStep`): a copy of partition
1's newest snapshot holding only the `GLOBAL` column families (deployments and other global state),
with id `1-1-0-0` (index 1, term 1, processed position 0). Every partition-local column family starts
empty, including `CHECKPOINTS` and all `LOG_ARCHIVE_*` ones, so the new partition has no checkpoint
state, no backups and no timeline. Its journal and its record positions start at 1. Its first
records are the scaling commands, then the checkpoints the scheduler keeps broadcasting (taken while
scaling is in progress they are marked failed on the old side, as before), with old-style backups
of the later ones until a handover is recorded on it.

**Activation.** The activation loop notices the new partition in the topology (18.1). Nothing has
compacted the journal before (the compaction cap holds at F = -1 until the first archived entry), so
`START_TIMELINE` starts the configured timeline at index 1: the timeline holds the partition's whole
log. The snapshot uploader archives the newest local snapshot shortly after (the bootstrap snapshot
or a later one).

**Rule.** `COMPLETE_HANDOVER(H)` is also accepted by a partition that has no confirmed HANDOVER backup
at all (it took no part in the handover) if its timeline holds its whole history:

- `firstArchivedPosition == 1`: every record of the partition is in this timeline, contiguous from
  its start;
- at least one archived snapshot: the partition did not start from an empty state but from the
  copied global state, which is not in its log; the archived snapshot is the only way to bring it
  back. It plays the role of the snapshot precondition of step 4.

The event records H's id with `handoverPosition = 0` ("from the start": the position before the
partition's first record). It is the same `HANDOVER_COMPLETED` event, with a commit entry, a root and
the old-side `handover/<H>.json` (position 0) as usual. Sound because the partition never needs an
old backup: every restore point after its handover lies after the archived snapshot and inside the
archived log, and old backups it took before (at most a short window, see below) stay valid old
restore points that the restore prefers where they exist. A partition that holds a HANDOVER backup
keeps the original rule only (H must be that backup), so the two rules never compete; a partition
whose archive starts after its first record and that misses H keeps being rejected (today's
behaviour for a genuine partial failure). The position check uses replicated state only, so leader
and followers decide alike.

**Scheduler** (`HandoverDecision`): with H recorded on some partitions, if every partition without a
handover has `firstArchivedPosition == 1` (row 3s), the scheduler sends `COMPLETE_HANDOVER(H)` and
keeps taking scheduled backups on schedule; the tick repeats with backoff until H is recorded
everywhere. Otherwise row 3a holds as before: COMPLETE_HANDOVER and no backups. A partition of the
handover that was activated before anything was compacted also has `firstArchivedPosition == 1`;
it still records H by its HANDOVER backup, and the backups taken meanwhile do not hide H from it,
since the processor checks the latest HANDOVER backup, not the latest backup. Before the new
partition is activated, row 1 holds and backups are taken as scheduled; the new partition uploads
old-style backups for them (the handed-over partitions skip theirs). Until its handover is recorded
the new partition keeps uploading old-style backups; after that, the skip of 18.6 applies.

**Restore.** A target after the scale-up exists on the new partition either as an old backup (taken
before its handover was recorded; old path) or in its archive (archive path), as on any partition.
The planner needs no change: the timeline starts at index 1 and the archived snapshot is selected as
usual. A target before the scale-up does not exist on the new partition. The old restore takes the
partition count from the backup and leaves the newer partitions out; the handover restore does the
same for a restore by backup id: a partition that cannot restore the target is left out if one of its timelines starts at
position 1 and every checkpoint it knows, on both sides, is newer than the target (checkpoint ids only
grow on a partition, and a partition that existed at the target holds it in its log). Only the
highest partitions can be left out; a missing partition below one that holds the target fails the
restore. The restored topology then holds the restored partitions only, as for old backups.

### 18.5 Restore across the handover (H5)

Per target checkpoint T and partition, using `metadata/<partition>/handover/` and the archive view
(`HANDOVER` commit entry); the old side is read only through the existing old restore code:

| Target | Path |
|---|---|
| an old backup T exists on the partition | old restore, unchanged |
| no old backup T | archive only (existing `LogArchiveRestore`) in the timeline that covers T, searched among the timelines named by `handover/<H>.json` records and their forks |
| T covered by more than one timeline | the operator names the timeline (manual ids can repeat across branches) |
| T nowhere | fail, naming the partition and the newest restorable checkpoint on each side |

**Discovering the timelines.** The seeds are the timelines named by the handover records and the
configured timeline (an explicit `--logArchive.timeline` names the one timeline to use and is read
directly, without discovery). Per partition, restore lists the timelines with one delimiter listing
(`listChildPrefixes("log-archive/")`), reads `log-archive/<timeline>/p<partition>/timeline.json` of each
(section 9.6, in parallel) and builds the parent graph from `parentTimelineId`. It selects the seeds and
every timeline whose parent chain leads to one, or whose id is derived from one (`.r<checkpoint>`,
`.b<backupId>`, `.f<seq>`, for a timeline that started without a parent). Only then it reads the views of
the selected timelines. The ids of descendants do not matter: a timeline that does not start with a seed
(e.g. after the configured timeline name changed) is found through its parent. Nothing below the
timeline level is listed to find timelines, so the cost does not grow with the amount of archived data.

Timelines written before the registry existed have no `timeline.json`. For those only, restore falls
back to the id rule: a timeline without the file is a candidate if it is a seed or its id is derived
from one, and it exists if it holds `commits/00000000000000000001.json`; its parent is not read, so such
a timeline is found only through its id. One that is neither registered nor named by a seed's id is not
found.

The decision is by where T exists, not by comparing T with H: after a restore to a point before H,
the new branch (`<tl>.b<T>`) takes old backups again until its own handover H2, and those come after
H in time.

The restore app takes this path when the log archive is enabled in its configuration, when a
timeline is named, or when the old store has handover files for any partition: the bucket, not
the configuration, says whether the cluster handed over.

A restore by checkpoint id never combines the sides on one partition: the snapshot precondition in
18.4 step 4 guarantees an archived snapshot ≤ H for every T > H, so the archive alone restores every
T > H. Only an RDBMS time range whose exported position lies before the archive combines them (the
bridged restore, 18.5a). If the old-side file is missing (leader died
before the outbox wrote it), the restore also searches the configured archive timeline. A restore without backup id ("latest")
is a time range without bounds and follows 18.5a. After a restore from old backups the new timeline's parent is
`legacy:<backupId>`.

Restore entry point: `restore-standalone` gets a log archive mode (timeline, target checkpoint id or
"latest") that picks the path automatically.

### 18.5a Time-range restore (RDBMS) across the handover (H4)

**Same rules as continuous backup.** `--from`/`--to` (and a restore without backup id) work exactly
as with continuous backup, whether the cluster uses continuous backup only, has handed over to the
log archive, or uses only the log archive: the same inputs are accepted or rejected with the same
messages, and resolve to the same restore point by the same rules. Only how a partition's data is
assembled differs: old backups, the archive, or both (bridged).

The rules, unchanged from continuous backup (`RestoreValidator`, `RestorePointResolver`):

1. **Validation.** A time range, and a restore without backup id, needs the RDBMS exported positions
   E: without RDBMS it is rejected with "Cannot resolve a restore point: no backupId was specified
   and no exported-position data is available …" ("Time range restore (from/to) is only supported
   for continuous backups" comes first if continuous backup is off; Elasticsearch/OpenSearch reject
   time ranges). The partitions restored are those the RDBMS holds a position for (1 to k; a gap or
   none at all fails).
2. **Range.** Per partition, the first backup range whose start backup is at or before `--from` and
   that covers E (its first log position ≤ E, its end ≥ E). None: "No usable range found for
   partition …".
3. **Candidates.** All checkpoints of that range, markers and backups, also those before `--from`,
   that exist on every partition with position ≥ E.
4. **T.** The candidate closest to `--to`, before or after; the latest without `--to`.
5. **Start.** The backups up to the first one at or after T. `--from` drops those before it (keeping
   one), and the first remaining one must still be at or below E, else "After discarding backups
   before …, the exporter position … is not covered by the first remaining backup …". Without
   `--from`, E trims: the restore starts at the last backup at or below E.

**Mapping.** `ContinuousBackupView` makes each partition look like continuous backup metadata, and
`HandoverRestoreResolver` runs the unchanged `RestorePointResolver.resolve` on it, so every rule and
message above applies as is. Without any archive or handover, `RestorePointResolver.resolve` runs
on the old `metadata.json` directly, as before.

| Continuous backup | Log archive |
|---|---|
| backup of checkpoint C: a snapshot below C and the log up to C; a possible start | an archived snapshot and the archived log up to the first covered checkpoint C after it (processed, last follow-up event and max exported position below C's position, as the planner selects snapshots). A timeline that starts at index 1 also starts its first checkpoint. Its first log position is the previous backup's position + 1, the first one's the first archived position |
| marker | every other covered checkpoint (`checkpointTimestamp`) |
| range | the archive of the current branch, contiguous by construction: from its first backup to its newest covered checkpoint, which ends the range as a backup does. Covered checkpoints before the first backup are left out: no archived snapshot restores them |
| one range up to and past H | the old range that holds H (same id, same position) up to H, continued by the archive after H on the same branch |
| `metadata.json` holds the branch that wrote it last | the current branch of the archive: a leaf timeline with its ancestors, each up to its child's first covered checkpoint; the one whose newest checkpoint is newest. With old backups, only a branch that records a handover of them (or whose archive starts at the partition's first record, 18.4a) continues them; otherwise the old backups are the current branch |

**Assembly.** Per partition, for T and the backups the rules chose:

- T is an old checkpoint: those old backups, restored by the old restore, unchanged.
- T is archived: the archive of the current branch (`FromArchive`); the journal starts at
  min(E + 1, snapshot index), which needs the archive to reach back to E + 1. Otherwise bridged
  (below). If neither can restore T, the restore fails and says why. T is fixed by the rules: there is
  no fallback to another restore point, as a continuous-backup restore fails on a backup it cannot
  read.

The archive normally reaches back to E + 1: compaction waits for exporters, and the archive starts
at the first index on disk at activation. If the RDBMS was reset to an older state, or its exporter
was disabled and stopped holding compaction before the log archive was enabled, E + 1 lies before
the archive start A. Such a partition is restored **bridged**: one journal from two sources of the
same branch, the old backups for the indexes up to A − 1 and the archive from A up to T.

**Plan** (`BridgedFromArchive`: old backup ids, timeline, T, E, switch index = A). The resolver
plans it for an archived T whose plain plan fails only because the archive starts after
E + 1 (`startIndex > 1` and `firstArchivedPosition > max(E + 1, 1)`), if:

- the timeline records a handover H (its `HANDOVER` commit entry), and H is an old checkpoint at
  the same position among the old checkpoints restorable for E (`RestorePointResolver`'s range that
  covers E). This is the same-branch check: a range of old backups is one continuous log, and H
  links it to this timeline. A timeline without a handover (e.g. one restored from the archive,
  `.r<T>`) or whose H lies in another range is never bridged;
- the archive part plans: an archived snapshot ≤ T and contiguous objects from the first object of
  the timeline to T (`LogArchiveRestorePlanner.planBridged`); T ≥ E;
- the old chain is `RestorePointResolver.resolveBackups` up to the first backup (≤ H) whose position
  is ≥ the archive's first position, so the chain reaches into the archive and the two overlap; it
  starts at the last backup at or below E, and its first log position is ≤ E + 1. Every backup of it
  is completed.

Sound because compaction was pinned to the old backups until H, so their chain has no gaps up to H,
and the archive started before H (A ≤ index of H), so the chain covers E + 1 … A − 1. In the
continuous-backup view this is the range across H: its start (the last backup at or below E, or the
first remaining one after `--from`) is an old backup, and T lies after H. Bridging comes after the
plain archive path; where even the bridge is impossible, the restore fails naming E and both sides'
coverage.

**Restore** (`HandoverClusterRestore`, then `LogArchiveRestore.restoreBridgedPartition`):

1. `PartitionRestoreService.restoreLog` restores the old chain's segments into the partition's
   journal as the old restore does, without installing any old snapshot.
2. Seam checks on the opened journal, before anything is appended: it starts before A and holds at
   least index A (overlap); its first application entry before A has asqn ≤ E + 1. The asqns and
   checksums of the old entries from A on are recorded.
3. Truncate after A − 1 (`deleteAfter`), then append the archived frames from A up to the checkpoint
   frame of T with `Journal.append(checksum, serializedRecord)`, as the archive restore does. Every
   frame the old journal also held must have the same asqn and checksum (the checksum covers index,
   asqn and data, so equal checksums mean equal entries); the first archived application entry's
   asqn must lie after the last old asqn before A. Any mismatch fails the restore.
4. Install the archived snapshot, verify as for archive restores (first index ≤ snapshot index + 1,
   last asqn = T's position), and write the timeline file `<source>.r<T>`.

Processing replays from the archived snapshot; the RDBMS exporter re-exports from E + 1. The restore
app needs no flag: the time-range resolution picks the bridge, and the restore logs "Restoring
partition N bridged: old backups … up to index A − 1, log archive timeline … from switch index A".

### 18.6 Backups after the handover (H6)

Once a handover is recorded on a partition:

- `SCHEDULED_BACKUP` and `MANUAL_BACKUP` checkpoints are still created (they are restore points), but
  no old-style backup is uploaded for them.
- Backup status (`GET /actuator/backups/{id}`) for such a checkpoint is `COMPLETED` once the
  partition's `archivedPosition ≥` the checkpoint position, `IN_PROGRESS` before. Status and listing
  come from the pending checkpoints (`IN_PROGRESS`) and `LOG_ARCHIVE_BACKUP_CHECKPOINTS` (covered,
  with position and timestamp), so a backup stays visible after its index chunk is sealed.
- Listing backups returns old backups and archive checkpoints together, ordered by id.
- Old backups keep their own retention; only restore points ≤ H depend on them.
- Disabling the log archive after a handover is refused at startup (continuous backup would need a
  fresh full chain; out of scope).

This keeps `POST /actuator/backups` + `GET /actuator/backups/{id}` usable as "give me a restore
point and tell me when it is safe" in tests and locally.

### 18.6a What operators notice: fresher restore points, same rules

The restore rules do not change: restore points are checkpoints (markers and backups),
`--backupId`/`--from`/`--to` and the RDBMS rules select them as with continuous backup, and a
checkpoint both approaches can restore gives the same result. What changes is where the restorable
range ends:

| | Continuous backup | Log archive |
|---|---|---|
| A marker becomes restorable | when the next backup completes (its segments contain the marker) | when the archive has covered it, seconds later |
| Newest restore point | last completed backup | newest archived checkpoint |
| RPO bounded by | backup interval + backup duration | marker interval + archive lag (cut interval, upload, commit) |

Visible effects: "latest" and a `--to` close to now can give a newer checkpoint than before; a
manual backup reports `COMPLETED` once archived instead of after an upload. Markers become the
fine-grained restore points, so with the log archive `checkpointInterval` should always be set (no
default; 5–15 s is cheap: one record per partition, no upload). Without it, only backups are
restore points and the RPO stays at the backup interval.

### 18.7 Tests and local runbook (H7, H8)

- Integration tests: upgrade simulation (start with continuous backup only, enable the log archive,
  observe activation, H, `HANDOVER_COMPLETED`, old backups stop, pin released); gate stays closed
  with one broker advertising an older version; restore before, at and after H; restore before H
  followed by a second handover; missing old-side file; `COMPLETE_HANDOVER` reaching only some
  partitions; manual backup after handover reports `COMPLETED` once archived; a scale-up after the
  handover (`LogArchiveScaleUpIT`: the new partition is activated and records H from its start,
  scheduled backups go on without old-style uploads, restores after and before the scale-up); an
  RDBMS behind the archive start (`LogArchiveBridgedRdbmsRestoreIT`: compaction past E + 1 before
  the log archive is enabled, bridged restore to a checkpoint after H, gap-free re-export from
  E + 1). Time ranges: `TimeRangeParityTest` runs the same `--from`/`--to`/E against
  continuous-backup-only metadata and equivalent handed-over and archive-only clusters and expects the
  same T or error; `LogArchiveHandoverRestoreIT` expects a time range without RDBMS to be rejected
  as with continuous backup, `LogArchiveHandoverRdbmsRestoreIT` restores `--from` inside the old
  range before H to `--to` after H.
- Manual test scenario G (brownfield upgrade) in `log-archive-manual-test.md` and its scripts.

### 18.8 Workstreams

| WS | Owns | Model | Depends on |
|----|------|-------|-----------|
| H1 gate + activation | scheduler gate, `ACTIVATE_LOG_ARCHIVE` request + handler, archiver waits for activation, version test hook | Opus | spec |
| H2 handover state + query | `CheckpointType.HANDOVER`, `COMPLETE_HANDOVER`/`HANDOVER_COMPLETED` intents + applier + commit entry type (also in roots), checkpoint state + meta fields, outbox write of `handover/<H>.json` on the old side, pin release, `logArchiveStates` query group | Sonnet | spec |
| H3 handover decision | scheduler steps 1–5, `COMPLETE_HANDOVER` request, scale-up partitions count as handed over | Opus | H1, H2 |
| H4 restore | per-partition path choice, handover lookup on both sides, archive fallback for a missing file, "latest" across both sides, restore app wiring | Sonnet | H2 |
| H5 backups after handover | skip old upload, status from F, list merges both sides, refuse disabling the archive | Sonnet | H2 |
| H6 integration tests | scenarios in 18.7 | Sonnet | all |
| H7 manual scenario G | brownfield upgrade in the manual test scripts | Haiku | H5 |

Critical path: H2 → H3 → H6. Journal stitching is limited to the bridged RDBMS restore (18.5a).

## 19. Event plane integration, step 1: announcing commit entries

Background: the event plane (repository `camunda/event-plane`) serves consumers the log of every
partition. Agreed direction: one copy of the data, the cluster is the authority for everything
restore relies on, the event plane is bookkeeping that can be rebuilt from the bucket, and the
cluster never waits for it. Step 1 is level 1 ("catalog"): the cluster tells the event plane, in a
deterministic way, what is in the bucket, and the event plane serves readers the objects of a
partition after a position. Release below the restore floor (level 2) is step 2 and not part of this
section.

### 19.1 Protocol: the cluster announces, in seq order, until confirmed

Per timeline and partition, the cluster sends every commit entry (section 9.2) to the event plane, in
seq order, at least once, and resends until the event plane confirms it. The event plane applies
an entry only if it is the next one it expects, so it ends up with exactly the bucket's sequence,
gap-free.

```
POST /v1/log-archive/{clusterId}/{physicalTenantId}/partitions/{partitionId}/timelines/{timelineId}/commits
Content-Type: application/json

{"entries":[ <commit entry JSON, byte for byte as in commits/<seq>.json>, … ]}   // consecutive seqs, ascending
```

| Answer | When | Body |
|---|---|---|
| `200` | the first entry's seq is the next expected one, or entries overlap what the event plane has (those are skipped, the rest applied) | `{"nextSeq": <the seq the event plane expects next>}` |
| `409` | the first entry's seq is above the next expected one (a gap), or the timeline is unknown and the batch does not start with seq 1 | `{"nextSeq": <expected>}` |
| `400` | malformed: entries not consecutive, an entry's `timelineId`/`partitionId` differs from the path, seq 1 is not `TIMELINE_STARTED` | problem detail |

- `nextSeq` of an unknown timeline is 1. The event plane applies a batch in one transaction.
- Entries are matched by `(clusterId, physicalTenantId, partitionId, timelineId, seq)`. An entry the
  event plane already has is skipped without comparing bytes (I4: same seq, same bytes).
- Unknown commit entry types are applied as "seq consumed, nothing else", so new types need no
  coordinated release.
- Authentication: whatever the event plane's security chain accepts; the PoC uses basic auth.

### 19.2 Cluster side: `LogArchiveAnnouncer`

A leader-only actor per partition, next to the publisher, started by `LogArchiveTransitionStep` when
`zeebe.broker.data.backup.logArchive.eventPlane.url` is set.

| Property (`logArchive.eventPlane.*`) | Default | Meaning |
|---|---|---|
| `url` | none (off) | Base URL of the event plane, e.g. `http://localhost:8080`. |
| `clusterId` | `cluster.clusterId`, else `"default"` | The cluster id the event plane knows the cluster by. |
| `username`, `password` | none | Basic auth; no header if unset. |
| `maxBatchEntries` | `100` | Entries per request. |
| `requestTimeout` | `PT10S` | Per request. |

Behaviour:

- **Input.** The publisher calls `announcer.published(timelineId, seq, commitEntryJson)` after the PUT
  of `commits/<seq>.json` succeeded (and after `timeline.json` for `TIMELINE_STARTED`). The announcer
  only ever sends seqs that are in the bucket: it tracks the highest published seq per timeline; on
  leader start that is `meta.lastPublishedSeq` (rows up to it are confirmed in the bucket), raised by
  every `published` call.
- **Resume point from the event plane.** The announcer keeps no replicated state. On leader start
  (and after any error) it does not know the event plane's `nextSeq`; it sends the lowest seq it holds
  in memory, or `meta.lastPublishedSeq` read from the bucket, and learns `nextSeq` from the answer
  (`200` or `409`). From then on it sends `nextSeq …` up to the highest published seq, at most
  `maxBatchEntries` per request, one request in flight.
- **Bytes.** From a bounded in-memory buffer of recently published entries (1000); a seq not in the
  buffer is read from the bucket (`commits/<seq>.json`). The bucket is the durable outbox.
- **Failures.** Connection errors, timeouts and `5xx`: retry with backoff (100 ms doubling to 10 s),
  same request. `400`: log an error and retry with backoff (a bug; never skip a seq). `409`: continue
  from its `nextSeq`, immediately; a `nextSeq` above the highest published seq means the event plane
  is ahead of this leader's view (e.g. a rolled-back state): log a warning and wait for more
  `published` calls.
- **Never blocks the cluster.** Nothing waits for the announcer: archiving, compaction, snapshots,
  publishing and restore go on while the event plane is down; the announcer only lags.
- **Timelines.** It announces the timeline the publisher publishes. A diverged timeline (section 8.4)
  is not announced; its fork starts at seq 1 like any timeline. After a timeline change the announcer
  stops announcing the old timeline (known limit: an unannounced tail of the old timeline reaches the
  event plane only through a rebuild).
- **HTTP client:** `java.net.http.HttpClient`, JSON through the existing Jackson mapper. No new
  dependency.

### 19.3 Timeline start: when and where a timeline takes over its parent

The event plane builds each partition's current path from the timelines: a timeline's own entries,
before them its parent's up to the takeover point. The commit entry `TIMELINE_STARTED` and
`timeline.json` (section 9.6) get three fields:

| Field | Value |
|---|---|
| `startedAt` | the archiver's clock (epoch millis) when it wrote `START_TIMELINE`, carried by the command and copied into the event, so it is replicated and the bytes stay a pure function of the event (I4); the event's own record timestamp cannot be used, as the processor does not know it (`recordTimestamp` of commit entries is -1) |
| `parentIndex` | the Raft index of the last entry this timeline shares with its parent, `-1` if unknown or no parent |
| `parentPosition` | the position of the last record it shares with its parent (the takeover checkpoint's position), `-1` if unknown or no parent |

- **Restore from the log archive** (section 13, also bridged, 18.5a) knows its target T: it writes,
  next to `log-archive-timeline`, a file `log-archive-takeover` with
  `{"parentTimelineId":…,"parentIndex":<index of T's entry>,"parentPosition":<T's position>}`
  (deterministic JSON, no trailing newline). On `START_TIMELINE` the archiver reads it and sets
  `parentIndex`/`parentPosition` on the command when the file's `parentTimelineId` equals the command's
  `parentTimelineId`, otherwise -1.
- **The first timeline, a fork (`.f<seq>`) and a classic restore (`.b<backupId>`):** -1/-1. The event
  plane treats a fork as taking over its parent up to the record before the fork's first archived
  position (known limit: a rolled-back state may have diverged earlier).
- `LogArchiveRecord` gets `startedAt`, `parentIndex`, `parentPosition` (long, default -1) for
  `START_TIMELINE`/`TIMELINE_STARTED`; the meta does not store them. A command without `startedAt`
  (-1) stays valid. Commit entry: `startedAt`, `parentIndex`,
  `parentPosition` only for `TIMELINE_STARTED`, after `parentTimelineId`. `timeline.json`:
  `formatVersion, timelineId, partitionId, parentTimelineId, startIndex, startedSeq, startedAt,
  parentIndex, parentPosition`. Readers accept files without the new fields (written before).

### 19.4 Event plane side (`camunda/event-plane`, branch `poc/log-archive`)

Code only, no docs in that repository. Fits its conventions (module layout, Liquibase + MyBatis
catalog, `Catalog` transactions, problem details, security chain, tests on H2).

**Catalog tables** (one Liquibase changeset `log-archive`):

| Table | Key | Columns |
|---|---|---|
| `la_timeline` | `cluster_id, physical_tenant_id, partition_id, timeline_id` | `parent_timeline_id` (nullable), `start_index`, `started_at` (timestamp), `parent_index`, `parent_position`, `next_seq` |
| `la_object` | `cluster_id, physical_tenant_id, partition_id, timeline_id, first_index` | `object_key` (bucket-relative, `log-archive/<tl>/p<n>/data/…`), `last_index`, `first_position`, `last_position` (-1 if no records), `term`, `entry_count`, `size_bytes`, `sha256`, `seq` |
| `la_checkpoint` | `cluster_id, physical_tenant_id, partition_id, timeline_id, checkpoint_id` | `checkpoint_position`, `checkpoint_type`, `checkpoint_timestamp` (nullable), `seq` |
| `la_snapshot` | `cluster_id, physical_tenant_id, partition_id, timeline_id, snapshot_id` | `snapshot_index`, `processed_position`, `seq` |

**Applying an entry** (`TIMELINE_STARTED` creates the timeline row with `next_seq = 2`; for the others
the row is locked first, `select … for update`, and `next_seq` incremented):

- `LOG_ARCHIVED`: insert its objects and checkpoints.
- `SNAPSHOT_ARCHIVED`: insert the snapshot (step 2 needs it for the restore floor).
- `INDEX_SEALED`, `HANDOVER`, unknown types: nothing but the seq.

**Read API** — `GET /v1/log-archive/{clusterId}/{physicalTenantId}/partitions/{partitionId}/objects?after={position}`

- Current timeline of a partition: the one with the latest `started_at`, ties broken by the larger
  `timeline_id` as text (as the event plane's histories).
- Current path: the current timeline's objects, and before its first archived position its parent's
  up to the takeover position (`parent_position`, or the record before the child's first archived
  position if -1), recursively.
- Answer: `{"objects":[{"timelineId","key","firstIndex","lastIndex","firstPosition","lastPosition","objectLastPosition","sizeBytes","sha256"}…],"end":{"timelineId","position"}}`:
  `lastPosition` is the object's last position on the current path, earlier than its own end
  (`objectLastPosition`) where the path leaves a parent inside the object; readers stop there;
  the objects on the path whose `last_position > after` in position order (objects without records
  are left out), at most 1000; `end` is the last position on the path. Overlaps between a parent's
  and a child's objects are left in; readers skip positions they have. No objects and `end > after`
  cannot happen in step 1 (nothing is deleted).
- Readers fetch the bytes from the bucket themselves (keys, not signed URLs, in step 1).

### 19.5 Tests

- Cluster unit tests: announcer state machine against a fake event plane (in order, batching, `409`
  resume, retry, bucket fallback for seqs outside the buffer, never beyond the highest published
  seq, timeline switch); timeline takeover fields (record golden file, JSON golden files, restore
  writes the takeover file, archiver reads it).
- Cluster integration test `LogArchiveEventPlaneIT`: brokers with a stub event plane (JDK
  `HttpServer`, in the test) that implements the seq check; it receives every commit entry from 1,
  gap-free, through a leader change and an event plane outage (stub down, then up: resumes from its
  `nextSeq`), and after a restore the new timeline with its takeover point.
- Event plane tests (H2): apply batches (in order, overlap, gap → 409, unknown timeline, malformed →
  400, unknown type), current path across a restored timeline, the read API; fixtures are real commit
  entries produced by the cluster PoC.
- Manual end to end: local brokers from `log-archive-manual/` with `eventPlane.url` pointing at the
  event plane server (demo profile, H2), then `curl` the read API; restore and see the path switch.

### 19.6 One object format: the event plane's segment format v1

The cluster writes new log objects in the event plane's segment format v1 (`camunda/event-plane`
`docs/12-segment-format.md` at commit `5b80c38`), so that one format serves restore, the event
plane's readers and later its rollups. It replaces the `ZLA1` format of section 10 for new objects;
readers (restore, the event plane) read both.

**Bytes** (little-endian throughout, as in the event plane's document):

```
body      zstd frames; a log object written by the cluster is ONE frame holding all its entries
          entry = the journal's bytes for one entry:
                  frame version (1 byte, 1)
                | RecordMetadata: SBE header (8) | CRC32C checksum (8) | RecordData length (4)
                | RecordData:     SBE header (8) | Raft index (8) | asqn (8) | Raft entry: length (4) + bytes
metadata  key-value entries, then a directory: per entry key (u16) | offset from metadata start (u32)
          | length (u32), then the entry count (u32)
            key 1 identity:    clusterId (u16 length + UTF-8) | physicalTenantId (u16 length + UTF-8)
                               | partitionId (u32)
            key 2 frame index: per frame: first position in the frame or -1 (i64) | frame offset (u64)
footer    66 bytes: history (16) | firstIndex (8) | lastIndex (8) | firstPosition (8) | lastPosition (8)
          | latest record timestamp, epoch ms, -1 if no records (8) | body length (8) | level (1, 0)
          | format version (1, 1)
```

- **Entry bytes = what the journal writes.** The checksum and the `RecordData` message are exactly
  `IndexedRaftLogEntry.getReplicatableJournalRecord()`'s checksum and serialized record; the
  `RecordMetadata` header is rebuilt with the journal's own SBE serializer. Restore appends them
  unchanged with `Journal.append(checksum, serializedRecord)`, as before (byte-identical journal).
- **History** (16 bytes): `UUID.nameUUIDFromBytes(("log-archive:" + timelineId).getBytes(UTF_8))`,
  written as the UUID's 16 bytes in RFC 4122 order (most significant byte first). Readers take the
  timeline from the commit entry or the key, not from this field; whether timelines and the event
  plane's histories become one concept is open.
- **Identity:** the cluster id as the announcer resolves it (`logArchive.eventPlane.clusterId`, else
  `cluster.clusterId`, else `"default"`), the physical tenant id and the partition id. Resolved once
  and passed to the archiver, whether or not the event plane is configured.
- **Determinism (I4):** zstd-jni (`com.github.luben:zstd-jni`, already a managed dependency), level 3,
  window log 23 (8 MiB, the event plane's decoder bound), content size in the frame header, no zstd
  checksum, no dictionary. The same entries give the same bytes with the same library version. Keys
  hold the term, so only the same leader re-uploads a key.
- **Latest record timestamp:** the highest record timestamp of the object's application entries (the
  Raft adapter reads it next to the value types), -1 without records.
- **Key and model:** `data/<firstIndex>-<lastIndex>-t<term>.seg` (20-digit zero padding as before).
  `LogObject` (record, JSON) gets `bodyLength` (long, -1 for `ZLA1` objects; omitted in JSON when -1),
  needed by readers that read the tail first. `sizeBytes` and `sha256` cover the whole object as
  before.
- **Reading:** the format follows from the key's extension: `.rec` is `ZLA1` (section 10), `.seg` is
  segment v1. Restore decompresses the body (one frame) and appends every entry; ranged reads are not
  needed, objects are at most `cutMaxBytes` before compression.
- **Not used yet:** level 1 (rollups) is the event plane's (level 2); the cluster always writes
  level 0, and one frame per cut object (merged objects have several frames, section 20.2).

**Event plane side:** its decoder reads both formats (by extension); `la_object` gets `body_length`
(nullable), the objects and timeline APIs return `bodyLength` when known; the UI shows the format.

## 20. Event plane integration, step 2a: merging small objects on journal rollover

Background: the archiver cuts a small object every few seconds (section 8.1) to keep the restore point
fresh. Restores and the event plane's readers then fetch thousands of small objects. The leader
replaces them with one bigger object per journal segment, once that segment is complete and
archived. Above the restore floor (section 21) this is the cluster's work, below it the event
plane's (section 22).

Replicas cut journal segments at different indexes, and every leader cuts objects at its own
boundaries. A merge is therefore a proposal, as a cut is: the leader picks the range, the processor
accepts it only if it starts at the merged cursor M + 1 and ends at an object boundary, in log order.
The merged bytes are a pure function of the range (Raft entries are identical on every replica, the
encoding is deterministic), so the boundaries a leader picks do not matter for correctness.

### 20.1 Protocol and state

| Value | Intent | Kind | Written by |
|-------|--------|------|-----------|
| 12 | `MERGE_LOG` | command | merger, after uploading the merged object |
| 13 | `LOG_MERGED` | event | processor, takes a seq (commit entry type `LOG_MERGED`) |

Record fields: `MERGE_LOG` carries `objects` with exactly one `LogObject`, the merged object.
`LOG_MERGED` carries the same object, and the processor sets `mergedIndex` and `mergedPosition`
(new fields, long, default -1: M after applying) plus `archivedIndex`/`archivedPosition` as usual.

`LogObject` gets `lastRecordTimestamp` (long, default -1; omitted in JSON when -1): the highest
record timestamp of its application entries, the same value as the segment footer's latest record
timestamp. The archiver sets it on every new object; the event plane needs it for its daily rollups
and its retention (section 22).

Meta gets `mergedIndex` (`startIndex - 1` initially, and reset to it by `START_TIMELINE`) and
`mergedPosition` (-1).

New column families (partition-local, next free ids):

| Column family | Key | Value |
|---|---|---|
| `LOG_ARCHIVE_UNMERGED_OBJECTS` (173) | `firstIndex` | `lastIndex`, `lastPosition`: every archived object above M |
| `LOG_ARCHIVE_MERGED_OBJECTS` (174) | `firstIndex` | `LogObject`: every merged object above the released index R (section 21) |

The `LOG_ARCHIVED` applier inserts each object into `LOG_ARCHIVE_UNMERGED_OBJECTS` as well (next to
`LOG_ARCHIVE_OPEN_OBJECTS`; open objects and sealed chunks stay as they are, I6).

**`MERGE_LOG`** is valid when:
- `timelineId == meta.timelineId`, exactly one object;
- `object.firstIndex == meta.mergedIndex + 1`;
- `object.lastIndex <= meta.archivedIndex` and `object.lastIndex` is the `lastIndex` of a row in
  `LOG_ARCHIVE_UNMERGED_OBJECTS` (the run ends at an object boundary);
- `entryCount == lastIndex - firstIndex + 1`;
- `object.lastPosition` equals the highest `lastPosition` of the run's rows (-1 if none has records).

Applier: delete the unmerged rows with `firstIndex <= object.lastIndex`; insert the object into
`LOG_ARCHIVE_MERGED_OBJECTS`; set `mergedIndex = object.lastIndex` and, if the object has
application entries, `mergedPosition = object.lastPosition`. A stale proposal (a merge of an old
leader after a new one merged) fails the first-index check and is rejected; its object becomes an
orphan (20.4).

**A run of one object** is merged without re-uploading: the merger proposes the archived object
itself (same key). Nothing is replaced, M moves on.

**Commit entry** `LOG_MERGED`: `objects` (the one merged object), `mergedIndex`, `mergedPosition`,
`archivedIndex`, `archivedPosition`. It replaces every object of the timeline in
`[object.firstIndex, object.lastIndex]`, whichever leader cut them; readers that hold the replaced
objects (the event plane's catalog, restore) drop them.

**Root** (section 9.4) gets `mergedIndex`, `mergedPosition` and `mergedObjects` (all rows of
`LOG_ARCHIVE_MERGED_OBJECTS`, in index order), so that a restore that starts from a root sees merges
older than it.

### 20.2 Merger (leader)

`LogArchiveMerger`, a leader-only actor per partition next to the archiver.

- **Trigger.** After every `onLogArchived` and every `onLogMerged`: a merge is due when a journal
  segment that holds entries above M has been completed (the journal rolled over to a newer
  segment) and F has passed its last index, or when `LOG_ARCHIVE_UNMERGED_OBJECTS` holds at least
  `mergeMaxObjects` rows. The journal exposes the first index of each of its segments (new read-only
  method on `Journal`, delegated through `RaftLog` and `RaftPartitionServer`; the segment map is a
  concurrent map).
- **Range.** From M + 1 to L, where L is the last object boundary at or below the completed segment's
  last index, and the run holds at most `mergeMaxObjects` objects (then the next merge follows
  right after). Nothing is due if that run is empty.
- **Bytes.** Entries M + 1 … L from a committed reader of the local journal, encoded as segment v1
  (section 19.6) with several zstd frames: a new frame starts once a frame holds `mergeFrameBytes` of
  entries; the frame index (metadata key 2) lists every frame; level 0 (the cluster writes level 0,
  the event plane's rollups are level 1). Encoded frame by frame into a temporary file, uploaded with
  `putFile` to `data/<M+1>-<L>-t<term>.seg`, `sha256` and `sizeBytes` computed while writing. If the
  local journal no longer holds M + 1 (it can't, see 20.3, but a node may be behind), the merger
  reads the run's small objects from the bucket instead: same entries, same bytes.
- **Commit.** Write `MERGE_LOG`, wait for `onLogMerged` or the rejection, then look for the next
  merge. One merge in flight. Failures: retry with backoff, as the archiver.
- **Listener:** `onLogMerged(mergedIndex, mergedPosition)` and `onMergeLogRejected(mergedIndex)` on
  `LogArchiveListener`.

### 20.3 Compaction cap

`DbPositionSupplier.getHighestBackupPosition()` also takes the minimum with `meta.mergedPosition`
(-1 until the first merge with records, which keeps the previous snapshot as today). Every replica
therefore keeps the entries above M, and a new leader can merge from its own journal. Costs at most
about one journal segment of disk, plus up to `mergeMaxObjects` objects' worth.

**I8 becomes:** with the log archive enabled, local compaction never removes an entry above M (and
M <= F).

### 20.4 Deleting replaced and orphaned objects

**I7 becomes:** the cluster deletes only objects of its current timeline above R (section 21), and
only replaced or orphaned ones; the event plane deletes only released objects (section 22). Nothing
else is ever deleted.

`LogArchiveCleaner`, a leader-only actor per partition, every `cleanupInterval`:

1. List `data/` of the current timeline's partition prefix. Skip keys it cannot parse.
2. Garbage is every key with `firstIndex > R`, `lastIndex <= M_safe` that is not the key of a row in
   `LOG_ARCHIVE_MERGED_OBJECTS`. That covers the small objects a merge replaced, objects of old terms
   that were never committed, and merged objects of a merge that lost. Keys above `M_safe` are left
   alone: an upload or a merge may be in flight there.
3. Delete a garbage key once it has been garbage for at least `cleanupGracePeriod` (first seen, in
   memory; a new leader starts counting again). The grace period lets readers that resolved a
   replaced object before the merge finish.

`M_safe` is M, or, when the event plane is configured, the `mergedIndex` of the newest `LOG_MERGED`
entry the event plane has confirmed (the announcer exposes its confirmed `nextSeq`; the merger
remembers `(seq, mergedIndex)` of its merges; a new leader knows only `meta.mergedIndex` as of
`meta.nextSeq - 1`, so it deletes nothing until the event plane has confirmed that seq). The event
plane then never lists an object the cluster has deleted.

The cleaner exposes `cleanedIndex` C: after each sweep, the highest index such that no garbage key
with `firstIndex <= C` is left, pending in its grace period or undeleted (the first index, not the
last: an object R reached into would no longer be the cluster's to delete). It never goes down within
a term; before a leader's first completed sweep it is `meta.releasedIndex`. The releaser never
releases past C (section 21.1), so the cluster alone deletes what its merges replaced.

Known limits: listing `data/` grows with the timeline until the event plane removes released
objects; restore folds the bucket once and fails if a merge deletes an object it resolved more than
`cleanupGracePeriod` earlier (run it again).

### 20.5 Restore

Restore folds roots and commit entries as before (section 13), plus `mergedObjects` from the root
and every `LOG_MERGED` entry after it. Objects for an index range up to M come from the merged
objects, above M from the chunks and open objects as before. Decoders read every frame of a
segment's body.

### 20.6 Configuration (`logArchive.*`)

| Property | Default | Meaning |
|---|---|---|
| `mergeMaxObjects` | `1000` | Merge at most this many objects at once, and merge as soon as this many are unmerged. |
| `mergeFrameBytes` | `32MB` | Entries per zstd frame of a merged object (as the event plane's rollups). |
| `cleanupInterval` | `PT1M` | How often the cleaner lists the bucket. |
| `cleanupGracePeriod` | `PT5M` | How long an object stays after it became garbage. |

## 21. Event plane integration, step 2b: release below the restore floor (level 2)

The cluster keeps what its restores need and hands the rest to the event plane. The handover is a
commit entry, so it is replicated, in the bucket, announced like every other entry, and the event
plane can rebuild it.

### 21.1 Restore floor

With `retentionWindow` set (default: none, nothing is ever released):

- **Protected restore point C:** the oldest checkpoint in `LOG_ARCHIVE_BACKUP_CHECKPOINTS` (backup
  types only) whose `checkpointTimestamp` is at or after `now - retentionWindow`; if there is none,
  the newest one (the newest backup stays restorable). No backup checkpoint at all: nothing is
  released.
- **Floor snapshot S:** the newest archived snapshot that restore would pick for C (section 13 step
  3: `processedPosition`, `lastFollowupEventPosition` and `maxExportedPosition` all below C's
  position).
- **Released index R:** the `lastIndex` of the newest merged object with `lastIndex < S.index`.
  Everything up to R is released. Only merged objects are released, so the event plane only ever
  takes over objects of one journal segment's size or larger. R also never exceeds the cleaner's
  cleaned index (section 20.4), so the objects a merge replaced are deleted by the cluster before R
  passes them. The event plane's queueing of replaced objects on `LOG_RELEASED` (section 22.1)
  remains only as a backstop, e.g. for objects of crashed sweeps.

The window and the clock are the leader's choice; the processor only checks that the proposal is
safe.

### 21.2 Protocol and state

| Value | Intent | Kind | Written by |
|-------|--------|------|-----------|
| 14 | `RELEASE_LOG` | command | releaser |
| 15 | `LOG_RELEASED` | event | processor, takes a seq (commit entry type `LOG_RELEASED`) |

New record fields `releasedIndex`, `releasedPosition` (long, default -1). Meta gets `releasedIndex`
(`startIndex - 1`) and `releasedPosition` (-1), reset by `START_TIMELINE`.

**`RELEASE_LOG`** (carries `releasedIndex`) is valid when the timeline matches,
`releasedIndex > meta.releasedIndex`, `releasedIndex` is the `lastIndex` of a row in
`LOG_ARCHIVE_MERGED_OBJECTS`, and an archived snapshot with `index > releasedIndex` exists.

Applier: delete the merged rows with `lastIndex <= releasedIndex`; set `releasedIndex` and
`releasedPosition` (the `lastPosition` of the newest released object with records, else unchanged).
Snapshots, chunks and backup checkpoints stay (known limit: snapshot retention is a separate topic).

**Commit entry** `LOG_RELEASED`: `releasedIndex`, `releasedPosition`. From this entry on, every object
of the timeline with `lastIndex <= releasedIndex` belongs to the event plane: the cluster never reads,
rewrites or deletes it again. **Root** gets `releasedIndex`, `releasedPosition`.

### 21.3 Releaser (leader)

`LogArchiveReleaser`, a leader-only actor per partition, every `releaseInterval` (and after
`onLogMerged`): compute R (21.1) from the replicated state; if R > `meta.releasedIndex`, write
`RELEASE_LOG(R)` and wait for `onLogReleased` or the rejection. The releaser does not wait for the
event plane: the bucket holds the commit entry, the announcer delivers it.

### 21.4 Restore

A restore point whose snapshot (section 13 step 3) has `index <= R` is below the floor: restore
refuses it with a clear message ("restore point … is below the restore floor of partition …"),
before downloading anything. Restore points above stay unchanged.

Known limit: parent timelines (the timeline a restore or a fork left) are never released; their
objects stay the cluster's until a later step decides who cleans up timelines nobody writes to.

### 21.5 Configuration (`logArchive.*`)

| Property | Default | Meaning |
|---|---|---|
| `retentionWindow` | none | Restore points younger than this stay restorable; older log is released. |
| `releaseInterval` | `PT1M` | How often the releaser checks the floor. |

## 22. Event plane side of step 2

Code in `camunda/event-plane`, branch `poc/log-archive`, as in 19.4. It follows the event plane's
own rollup, retention and deletion design (its `docs/11-storage.md`), applied to log archive objects.

### 22.1 Catalog

- `la_object` gets `level` (0 or 1) and `last_record_timestamp` (nullable). `la_timeline` gets
  `merged_index`, `released_index`, `released_position` (-1). New table `la_replaced`
  (`cluster_id, physical_tenant_id, partition_id, timeline_id, object_key`, `first_index`,
  `last_index`) and a deletion queue `la_delete_queue` (`store`, `object_key`, `queued_at`).
- **`LOG_MERGED`:** in the entry's transaction, move the timeline's `la_object` rows in
  `[firstIndex, lastIndex]` to `la_replaced`, insert the merged object, set `merged_index`. The event
  plane does not delete replaced objects above `released_index`: they are the cluster's.
- **`LOG_RELEASED`:** set `released_index`/`released_position`, and queue every `la_replaced` row
  with `last_index <= released_index` for deletion (released replaced objects the cluster has not
  deleted yet; deleting twice is harmless).
- Rows for objects released and removed stay out of the read API; the objects API answers a new
  field `start` (the first position still on the path) so readers see when retention removed records.

### 22.2 Rollup of released objects (level 0 → 1)

As the event plane's rollup: take the oldest run of consecutive released level-0 objects of a
timeline on the current path whose `last_record_timestamp` lies in the same period
(`rollup.period`, default `P1D`, aligned to UTC), once that period has been over for `rollup.delay`
(`PT1H`). Objects without records join the run of their neighbours. An object that the path leaves
early (a takeover inside it) is never rolled up.

Write one segment v1 object, level 1, frames of about 32 MiB, to
`log-archive/<tl>/p<n>/data/<first>-<last>-l1.seg` (create-only). Then the swap, in one transaction:
if every input row is still there, delete them, insert the rollup's row and queue the inputs for
deletion; otherwise give up and leave the new object. After the transaction, delete the queued
objects from the store and remove them from the queue.

### 22.3 Retention of released objects

Per cluster and tenant: the environment's retention if an environment row exists, else
`camunda.event-plane.log-archive.retention` (default none: keep). Periodically, per timeline, in one
transaction: remove the longest run of released objects from the start of the timeline whose
`last_record_timestamp` is older than `now - retention` (objects without records go with the run
they are in), and queue them for deletion. Only released objects are ever removed.

### 22.4 Maintenance

Rollup, retention and deletion run on one scheduled worker (`rollup.interval`, default `PT1M`), under
the `maintenance` lease if the catalog has one, else on one replica (the PoC runs one). Writing and
deleting needs the store configuration of 19.4 to allow writes.

### 22.5 UI

The timeline view shows `merged_index` and `released_index`, and per object its level and whether it
is released; replaced objects are not shown.

## 23. Step 2: tests and waves

Tests:

- Cluster unit tests: `MERGE_LOG` and `RELEASE_LOG` rules and rejections, replay gives identical
  state, golden files for the record and the commit entries; merger (trigger on rollover and on
  `mergeMaxObjects`, single-object run, rejection, bucket fallback); multi-frame encoding round trip;
  cleaner (garbage rule, grace period, `M_safe` with and without the event plane); releaser (floor
  computation with and without backups in the window).
- Integration tests: small journal segments; merges replace small objects and restore uses them; a
  leader change in the middle of a merge (the orphan is deleted later, ranges stay contiguous); a
  release below the floor, restore below it is refused and above it works; with the stub event plane,
  nothing is deleted before the event plane confirmed the merge.
- Event plane tests (H2): apply `LOG_MERGED`/`LOG_RELEASED`, rollup swap (including a lost swap),
  retention, deletion queue; fixtures from the cluster.
- Manual end to end (scenario J): small journal segments, a short window, short rollup period; see
  objects merged, released, rolled up and removed in the UI.

| Wave | Repo | Scope | Depends on |
|---|---|---|---|
| W8 | camunda | protocol, record, meta, column families, processor, appliers, commit entries, root, goldens | spec |
| W9 | camunda | journal segment API, merger, multi-frame encoder, compaction cap, cleaner, announcer's confirmed seq | W8 |
| W10 | camunda | releaser, restore (merged objects, floor), decoder reads frames | W8 |
| W11 | event-plane | catalog for `LOG_MERGED`/`LOG_RELEASED`, `la_replaced`, read API `start`, UI | spec |
| W12 | event-plane | rollup, retention, deletion queue, maintenance worker | W11 |
| W13 | camunda | integration tests | W9, W10, W12 |
| W14 | both | manual scenario J | W13 |
