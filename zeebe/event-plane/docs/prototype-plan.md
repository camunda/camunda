# Prototype plan: the Event Plane in the bucket

An end-to-end prototype of
[the Event Plane in the bucket](https://github.com/camunda/event-plane/blob/a45247e5e1fb128e0de841a1268c97125f2644af/docs/15-event-plane-in-the-bucket.md),
in camunda/camunda, on the branch `poc/event-plane-in-the-bucket`. It is written from scratch, not
on top of the log archive proof of concept
([camunda#66483](https://github.com/camunda/camunda/pull/66483)). It takes over the Event Plane's
own designs: the [segment format](https://github.com/camunda/event-plane/blob/main/docs/12-segment-format.md)
with zstd, [history manifests](https://github.com/camunda/event-plane/blob/main/docs/14-history-manifests.md)
and the upload floor.

## What it shows

1. A broker publishes each partition's committed journal as segments into a bucket, on the
   filesystem and on S3 (MinIO). The upload floor stops compaction. Leader changes and restarts lose
   nothing, and repeat little.
2. The workers of a consumer group read every record at least once, and coordinate only through the
   bucket: they divide the partitions, take over a dead worker's, and give some to a new one.
3. A maintenance job merges segments into rollups and applies retention while workers read.
4. A stateless service passes upload notifications on to workers, and shows each group's lag. A
   restart of it loses nothing.
5. A restore starts a new history, and workers that read past the restore point are told where the
   histories part.
6. Stretch: the restore comes from the bucket itself. Archived snapshots and segments rebuild the
   journal byte for byte, in place of a backup.
7. Added after the plan: one copy of the log. A backup keeps its snapshot in the backup store, but
   its log only in the bucket, and a restore from it rebuilds every partition's journal from there,
   to the checkpoint. See [Backups whose log is in the bucket](contracts.md#backups-whose-log-is-in-the-bucket).

## What it leaves out

Production readiness, Hub, authorization (the service hands out the store's own credentials), GCS
and Azure, physical tenants other than the default one, purges, online restores, SDKs in other
languages, metrics beyond logs, and any promise of compatibility.

## Where the code goes

New modules under `zeebe/event-plane/`. They need no new third-party dependency: zstd-jni and the
AWS SDK are in the parent POM already, for the S3 backup store.

| Module | What it holds | Starts from |
|---|---|---|
| `segment` | Writing and reading segments: entries as the journal writes them, zstd frames, metadata, footer | [event-plane#90](https://github.com/camunda/event-plane/pull/90), ported |
| `store` | `ObjectStore`: PUT, GET, ranged GET, LIST after a key, DELETE. Filesystem and S3. | New. The S3 client is set up like `zeebe/backup-stores/s3`'s. |
| `history` | Manifests, and how histories relate | event-plane's `api/history`, ported |
| `publisher` | Runs on each partition's leader in the broker | The ports of [event-plane#91](https://github.com/camunda/event-plane/pull/91) (entry source, store, floor, announcer), with broker adapters |
| `consumer` | The consumer SDK: reading and consumer groups | New |
| `maintenance` | Rollups and retention, as a library and a main | New |
| `service` | The stateless service | New |

The broker changes in `zeebe/broker`: its configuration, a transition step, and the floor in the
exporter state.

## The parts

### Publisher

- Configured under `zeebe.broker.experimental.eventPlane`: on or off, the store, how often to cut a
  segment (5 seconds), its target size, and the service's address.
- A transition step starts it when the partition becomes leader, and stops it otherwise.
- It reads committed entries with `RaftPartitionServer.openReader()`, from the entry after the
  floor, entries without records included.
- Before its first upload, it writes its history's manifest. Then it cuts a segment every 5 seconds,
  or when the segment reaches its target size, and PUTs it as
  `{cluster}/{tenant}/{partition}/{history}/{firstIndex}-{ULID}.seg`, the index zero-padded to 20
  digits. Then it moves the floor and notifies the service.
- **The floor is an entry in the exporter state,** under the id `event-plane`, configured on every
  broker. Its position is the last uploaded record's, and its metadata holds the history and the
  index. The exporter director already persists it, sends it to followers every 15 seconds, and
  compaction already stops at the lowest exporter position (`DbPositionSupplier`,
  `StateControllerImpl`). Spike 1 checks that this works without a real exporter.
- A partition starts a history with its first upload. After a restore, the leader takes the new
  history that the restore left in the partition's data directory (see [Restore](#restore)).

### Consumer SDK

- A builder takes the store, the group, its subscription (clusters and tenants, with `*`), the
  start (earliest or latest), and three callbacks: records, a gap, and a changed history.
- **Reading.** For each log, it works out the current history and its path from the manifests,
  lists the segments after the last name it read, and reads them with ranged GETs and the frame
  index. It decodes entries into Zeebe records, skips entries without records and records it has,
  takes retention markers into account, and reports a history change before it reads on.
- **Groups,** as in the proposal: a heartbeat object per worker every 5 seconds, a lease of 30
  seconds, assignment by rendezvous hashing, the handover rules, and the cursor written with a plain
  PUT every 10 seconds.
- **Notifications.** It subscribes to the service for the logs it holds, and lists every 60
  seconds anyway.

### Maintenance

- One instance per cluster, by deployment. For each log, it merges the level-0 segments older than
  a set age (a minute in the demo, a day by default) into level-1 segments with frames of about 32
  MiB, and then deletes the inputs.
- If two rollups cover the same segments, it deletes the later one by ULID.
- Retention deletes segments older than the retention period, after it has written its marker.

### Service

A small Spring Boot application:

| Endpoint | What |
|---|---|
| `POST /v1/uploads` | From publishers. Passes the upload on to the workers that subscribed to its log, and to its peers. |
| `GET /v1/notifications?logs=…` | Server-sent events, for workers |
| `GET /v1/groups/{group}` | The group's workers, cursors, and their lag behind each log's newest segment |
| `GET /v1/credentials` | A stub: the store's own configuration |

It finds its peers in a configured list. It keeps the newest segment of each log in memory, and
reads the rest from the bucket.

### Restore

- The offline restore app (`RestoreApp`, `PartitionRestoreService`) leaves a file in each restored
  partition's data directory. It names the new history, which the operator passes so that every
  broker uses the same one, and the history the restored floor names, and the checkpoint's index and
  position.
- On its first leadership, the publisher writes that history's manifest and starts it.
- **Deviation from the manifest design:** each partition writes its own part,
  `histories/{history}/{partition}.json`, because an offline restore has no place that knows every
  partition. Readers combine the parts. A history's creation time is the latest of its parts'.

### Restore from the bucket (stretch)

- The publisher also uploads the partition's snapshots, at most every N minutes, under
  `{log}/snapshots/{snapshotId}/`, with a descriptor.
- `RestoreApp --from-event-plane --to <checkpoint>` takes, for each partition, the newest snapshot
  before the checkpoint, and the segments from the snapshot's index to the checkpoint. It appends
  their entries to the journal with `Journal.append(checksum, serializedRecord)`: segment entries
  are the journal's own bytes. Then it leaves the history file, as above.
- Retention keeps what a restore needs: every segment after the oldest snapshot that it keeps.

## Spikes, first

1. **The floor in the exporter state.** Can the publisher set a position and metadata for an
   always-configured id that no exporter feeds, so that `clearExporterState` and
   `isExporterConfigured` keep it, followers store it, and compaction stops at it? If not, a bound
   of its own in `StatePositionSupplier`, and its own distribution message.
2. **Records outside the broker.** Decode an application entry into Zeebe records with the protocol
   classes alone.
3. **Reading on a new leader.** Resume the committed reader from the floor after a leader change,
   and see what happens at the edge of compaction.
4. **Snapshots (stretch).** Read a snapshot's files while it is in use, with the snapshot store's
   reservations.

### What the spikes found

1. **Yes, and then not needed.** An id that no exporter feeds worked as an "external position" of
   the exporter director. On request, the floor then got a column family of its own
   (`EVENT_PLANE_FLOOR`), its own distribution to followers (`FloorDistribution`), and a place next
   to the exporters in `DbPositionSupplier`; the exporter director is untouched. Followers hold
   compaction until the leader's first floor arrives. `PublishIT` checks that a snapshot's
   exported position is the last uploaded record's.
2. **Yes, with protocol-impl and logstreams**, no broker: the application entry's data, read by hand
   (`Entry.applicationData()`), is a run of `LoggedEventImpl`s; `RecordMetadata`,
   `UnifiedRecordValue.fromValueType` and `CopiedRecord` make records of them (`RecordDecoder`). The
   catch: Zeebe's buffers (Agrona) need `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED`.
3. **Yes.** The committed reader seeks to the floor's index + 1; if the journal starts later, the
   publisher warns and starts at its first entry. A new leader repeats what the old one uploaded
   after the floor it last sent. `LeaderChangeIT` shows it resumes; with followers that ignore the
   floor, every entry came twice.
4. **Yes.** `SnapshotUploader` reserves the snapshot while it uploads its files, then releases it.
   A restore must choose its snapshot by every position the snapshot holds, not only the processed
   one, or the restored state points past the end of the restored journal.

## Order

| Phase | What | Rough time |
|---|---|---|
| 0 | The spikes, the module skeletons, and the contracts: segment format, object names, the store interface, the JSON of manifests and group objects | Half a day |
| 1 | In parallel: publisher and broker (A), consumer SDK against segments that a test writes (B), maintenance (C), service (D) | One to two days |
| 2 | End-to-end tests and a demo, and the restore | A day |
| 3 | Stretch: snapshots, and the restore from the bucket | One to two days |

The contracts of phase 0 let the four workstreams of phase 1 build without waiting on each other.

## Tests

- Unit tests per module, and the segment property tests, ported.
- End to end with `TestStandaloneBroker`, on the filesystem store and on MinIO:
  - a group of three workers reads every record of a running process load at least once, and the
    first arrival of each record comes in position order;
  - a worker is killed, and another one joins, while the others read;
  - a leader change, and a broker restart, while the publisher uploads;
  - maintenance runs while workers read;
  - the service restarts while workers wait for notifications;
  - a restore, with workers that had read past its checkpoint;
  - stretch: a restore from the bucket gives the same journal as the one that was archived;
  - added: a backup of two partitions keeps no journal segment, and a restore from it gives every
    partition's state at the checkpoint (`BackupLogInBucketIT`).

## Open points

- Physical tenants: the prototype covers the default one. The object names have a tenant level
  already.
- Manifest parts per partition, which the manifest design doesn't have. To reconcile once a restore
  can install a history on every broker.
- Where the code lives after the prototype: camunda/camunda or the event-plane repository.
- Purges and online restores.
- How the service authenticates publishers and workers. In the prototype, it doesn't.
- One copy of the log, as backups keep it now, makes the bucket's retention a backup's too: a
  backup log holds its segments however old. Snapshots are still kept twice, in the backup store
  and in the bucket; the backup could name the bucket's snapshot instead.
- A backup's log starts where the local journal segment that holds its snapshot starts, as a backup
  copies whole segments: its descriptor's first log position. At low load, that segment can be
  older than the bucket's retention. Then the backup fails (`BackupLogException.NotInBucket`).
  Starting at the snapshot's index + 1 would do for a restore, but the backup confirms its first
  log position, and backup ranges are built from it.
- A retention run that lists the backup logs just before a new one is written, and deletes after
  the backup checked its segments, can delete what that backup needs; the restore then fails. It
  needs a segment at the edge of retention, as above. Conditional writes, or a second check after
  the backup log is written, would close it.
- `restoreTo` names one position for every partition: it suits one partition. A consistent restore
  of several goes through a checkpoint, and so through a backup.
