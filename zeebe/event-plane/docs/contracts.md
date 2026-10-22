# Event Plane in the bucket: contracts

The contracts that every module of the prototype builds against. The plan is
[prototype-plan.md](prototype-plan.md). If something here is wrong or missing, fix it here first,
then in the code.

## Modules and packages

| Module | Package | Depends on |
|---|---|---|
| `zeebe/event-plane/segment` | `io.camunda.zeebe.eventplane.segment` | zstd-jni |
| `zeebe/event-plane/common` | `io.camunda.zeebe.eventplane.store`, `.history`, `.notification`, `.snapshot`, `.backup` | segment (for `LogId`), Jackson, AWS SDK v2 (S3) |
| `zeebe/event-plane/publisher` | `io.camunda.zeebe.eventplane.publisher` | segment, common |
| `zeebe/event-plane/consumer` | `io.camunda.zeebe.eventplane.consumer` | segment, common, zeebe-protocol-impl, zeebe-logstreams |
| `zeebe/event-plane/maintenance` | `io.camunda.zeebe.eventplane.maintenance` | segment, common |
| `zeebe/event-plane/service` | `io.camunda.zeebe.eventplane.service` | segment, common, consumer (for the group objects) |
| `zeebe/event-plane/qa` | `io.camunda.zeebe.eventplane.qa` | everything, `zeebe-qa-util` |
| `zeebe/broker` | `io.camunda.zeebe.broker.eventplane` | publisher |

A log is a partition of a physical tenant of a cluster: the segment module's
`LogId(cluster, tenant, partition)`, in text `{cluster}/{tenant}/{partition}` (`toString`,
`LogId.parse`). The tenant is the partition group's name, `default` for the default one.

## Object store

`io.camunda.zeebe.eventplane.store.ObjectStore`, synchronous, thread-safe:

```java
public interface ObjectStore extends AutoCloseable {
  URI root();                                   // ends in "/": file:///…/ or s3://bucket/prefix/
  default URI uri(String key) { return URI.create(root() + key); }
  void put(String key, byte[] content) throws IOException;            // replaces
  Optional<byte[]> get(String key) throws IOException;                // empty if missing
  byte[] getRange(String key, long offset, int length) throws IOException; // clipped at the end
  Optional<StoredObject> head(String key) throws IOException;
  List<StoredObject> list(String prefix, @Nullable String startAfter, int limit) throws IOException;
  List<String> listPrefixes(String prefix) throws IOException;        // "directories" below prefix
  void delete(String key) throws IOException;                         // fine if missing
  @Override default void close() throws IOException {}
}
record StoredObject(String key, long size, Instant lastModified) {}
```

- `list` returns full keys that start with `prefix` and sort after `startAfter`, in key order (byte
  order of the UTF-8 key), at most `limit`.
- `listPrefixes(prefix)` returns full keys of the "directories" directly below `prefix`, each ending
  in `/`, sorted. `prefix` ends in `/`.
- `getRange` of a missing object throws `NoSuchObjectException` (an `IOException`).
- Nothing is conditional. A listing may show an object late.
- Implementations: `FileSystemStore(Path root)` (writes to a temporary file and renames it), and
  `S3Store(S3StoreConfig)` on the AWS SDK v2, which also works with MinIO and LocalStack
  (`forcePathStyle`). `ObjectStores.open(StoreConfig)` creates either from a configuration with a
  `type` of `filesystem` or `s3`.

## Object names

`io.camunda.zeebe.eventplane.store.ObjectNames`. Cluster and tenant are percent-encoded: every byte
outside `A-Za-z0-9-._~` as `%XX`, and `.` and `..` as `%2E`. Numbers are zero-padded to 20 digits.
Cluster ids must not start with `_`.

| Object | Key |
|---|---|
| A history's manifest part for one partition | `{cluster}/{tenant}/histories/{history}/{partition}.json` |
| A segment | `{cluster}/{tenant}/{partition}/{history}/{firstIndex}-{lastIndex}-L{level}-{ulid}.seg` |
| A retention marker | `{cluster}/{tenant}/{partition}/retention/{ulid}.json` |
| A snapshot's files and descriptor | `{cluster}/{tenant}/{partition}/snapshots/{snapshotId}/…`, `…/descriptor.json` |
| A backup's log | `{cluster}/{tenant}/{partition}/backups/{checkpointId}.json` |
| A consumer group | `_groups/{group}/group.json` |
| A worker's heartbeat | `_groups/{group}/workers/{worker}.json` |
| A cursor | `_groups/{group}/cursors/{cluster}/{tenant}/{partition}.json` |

`level` is 0 for a publisher's segment and 1 for a rollup. Names sort by first index, so a listing
of a history's directory is in log order. `ObjectNames.parseSegment(key)` gives
`SegmentName(history, firstIndex, lastIndex, level, ulid)`. `ObjectNames` takes a `LogId` for what
belongs to a log, and plain cluster and tenant names for the directories above it.

## Histories

Package `io.camunda.zeebe.eventplane.history`: `HistoryManifest`, `HistoryManifest.Base`,
`HistoryLineage`, as in camunda/event-plane `api/history`, with Jackson 2.

- **Parts.** Each partition writes its own part of a history's manifest: a `HistoryManifest` with
  only its partition. `Histories.load(store, cluster, tenant)` reads every part and merges the parts
  of each history: the union of their partitions, and the latest `createdAt`.
- **The first history** of a tenant is `HistoryIds.initial(clusterKey, tenant)`, a name-based UUID
  of `event-plane/initial/{clusterKey}/{tenant}`, so every partition agrees on it without talking.
  `clusterKey` is the cluster configuration's generated cluster id, or the cluster name.
- **Paths.** `HistoryLineage.path(history, partition)` is public and returns `List<Stretch>`, oldest
  first: `Stretch(UUID history, Base start, @Nullable Base end)`. A history's segments hold its
  journal, including entries it took over. A segment of history `H` serves the current path for
  every index up to `lastShared(H, current).index`, or every index if `H` is the current history.

JSON of a part, as in docs/14-history-manifests.md:

```json
{"version":1,"clusterId":"c","physicalTenantId":"default","historyId":"…","createdAt":"2026-10-20T12:00:00Z",
 "partitions":{"3":{"sourceHistoryId":null,"index":0,"position":-1}}}
```

## The upload floor

The floor is the partition's entry in the column family `EVENT_PLANE_FLOOR` (`FloorState`): the
history, the last uploaded index, and the last uploaded record's position, or -1 if no upload held
a record yet. Every replica keeps one while the Event Plane is on, held at -1 until the first
upload, and snapshots carry it; while the Event Plane is off, the broker deletes it. Its position
never goes back, and compaction stops at it as at an exporter's (`DbPositionSupplier`). The leader
sends each floor it stores, and the latest again every exporting distribution interval, on the
subject `event-plane-floor-{tenant}-{partition}` (history, index, position: four longs); a
follower stores those ahead of its own.

## Upload notifications

`io.camunda.zeebe.eventplane.notification.UploadNotice`, as JSON:

```json
{"clusterId":"c","tenantId":"default","partition":3,"history":"…","key":"c/default/3/…seg",
 "firstIndex":1,"lastIndex":40,"firstPosition":1,"lastPosition":38,"size":18432}
```

- Publishers `POST` it to the service's `/v1/uploads`, best effort.
- Workers read `GET /v1/notifications?log={cluster}/{tenant}/{partition}&log=…` as server-sent events:
  `event: upload` with the notice as `data`.

## Segments in the store

- A publisher's segments of one history don't overlap, but two leaders can upload the same
  entries: a new leader resumes after the floor it knows, which can lag the old leader's uploads.
  Both start at the same index, as the floor only ever stands at a segment's last index, and hold
  the same bytes for the same index, as both read committed entries.
- A rollup (level 1) holds the entries of consecutive level-0 segments, from the first one's first
  index to the last one's last index. Maintenance writes it, then deletes every level-0 segment of
  that history within its range. If a rollup lies within another's range, it deletes the one with the later ULID; two that only overlap both stay, as each may hold entries no level-0 segment holds any more.
- So a reader looking for the entry at index `n` takes, among the segments that contain `n`, the
  one with the highest level, then the highest last index. A segment can disappear while a reader
  reads it; the reader lists again.
- The ULID in a name holds the upload time, which ages a segment.

## Retention markers

`{cluster}/{tenant}/{partition}/retention/{ulid}.json`, written by maintenance before it deletes
anything; the latest by ULID applies:

```json
{"version":1,"history":"…","firstIndex":1096704,"firstPosition":1052032,"createdAt":"2026-10-20T12:00:00Z"}
```

It names the first entry that is kept, in the then current history. A reader that needs an earlier
entry on the same path reports a gap up to it, and goes on from it.

## Snapshots

Backups put their snapshots into the bucket (see [Backups](#backups)); nothing else does.
`io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor`, at
`{cluster}/{tenant}/{partition}/snapshots/{snapshotId}/descriptor.json`:

```json
{"version":1,"snapshotId":"…","index":1096000,"term":3,"processedPosition":1051900,
 "exportedPosition":1051800,"maxExportedPosition":1052032,"lastFollowupEventPosition":1052010,
 "history":"…","files":[{"name":"000123.sst","size":4096}],"createdAt":"2026-10-20T12:00:00Z"}
```

- Index, term, processed and exported position come from the snapshot id
  (`FileBasedSnapshotId`), the other two positions from the snapshot's `zeebe.metadata`
  (`FileBasedSnapshotMetadata`). `exportedPosition` is the lowest exporter position,
  `maxExportedPosition` the highest, the upload floor's included. `history` is the backup's.
  Readers skip fields they don't know.
- The files are the backup's snapshot files, the checksum file `{snapshotId}.checksum` among them.
- **A descriptor means every file is there:** it is written after the files and deleted before
  them. Files without a descriptor are an upload in progress, or what a stopped upload or deletion
  left; maintenance deletes them once all are older than the retention.
- **Retention** deletes a snapshot that no backup log names, once it is older than the retention.
  It never deletes one that a backup log names.

## Backups

With `zeebe.broker.experimental.eventPlane.backupLog` true, a backup keeps only its status and
descriptor in the backup store: its snapshot and its log are in the bucket, once.
`io.camunda.zeebe.restore.EventPlaneLogBackupStore` wraps the configured `BackupStore`:

```java
public static BackupStore wrap(
    BackupStore delegate,
    ObjectStore store,
    String cluster,
    String tenant,
    Duration uploadTimeout,
    PublishingHistory publishing)

/** The history a partition's leader publishes in; empty until its publisher has started one. */
@FunctionalInterface
public interface PublishingHistory {
  Optional<UUID> of(int partition);
}
```

It owns the `ObjectStore`, and closes it with the delegate. Cluster and tenant are the publisher's.
`uploadTimeout` is `zeebe.broker.experimental.eventPlane.backupLogTimeout`, 5 minutes by default. In
the broker, `publishing` reads the partition's `PublisherService` when a backup is saved. The
restore app saves no backups, and passes `PublishingHistory.NONE`.
A backup's log, `io.camunda.zeebe.eventplane.backup.BackupLog`, at
`{cluster}/{tenant}/{partition}/backups/{checkpointId}.json`:

```json
{"version":1,"checkpointId":17,"checkpointPosition":1052032,"history":"…",
 "firstIndex":1090001,"lastIndex":1096704,"snapshotId":"…","createdAt":"2026-10-21T12:00:00Z"}
```

- `history` is the one the partition's leader publishes in when it saves the backup, from
  `publishing`. Its path through the partition holds the backup's log, also after a later restore
  starts another history. Not the bucket's current history: right after a restore, that is still
  the old one until the leader writes the new one's part, and the old one's segments past the
  restore point hold the same positions with other records.
- `firstIndex` is the first index of the segment on the path that holds the backup descriptor's
  `firstLogPosition`, as a backup copies whole journal segments too; the path's first index if the
  descriptor has none. `lastIndex` is the last index the path serves of the segment that holds
  `checkpointPosition`. The backup needs the path's entries from `firstIndex` to `lastIndex`.
- `snapshotId` is the backup's snapshot, in the bucket's `snapshots/`, or null for a backup without
  one.
- **Save:** waits, up to `uploadTimeout`, until the publisher has a history, the bucket has that
  history's part, and the path's segments hold `checkpointPosition`, and fails the backup after
  that. It finds the segments by their footers, walking back from the newest. Then it PUTs the
  backup's log, then the snapshot, and saves the backup through the delegate without snapshot and
  segment files. If the bucket has the snapshot's descriptor already, from an earlier backup of the
  same snapshot, it PUTs only the descriptor again, with a new `createdAt`: a retention run that
  read the backup logs before this one was written would otherwise take an old snapshot that no
  other backup names for one to delete. The backup's log comes first, so that retention keeps the
  snapshot from its first file on. A completed backup has its snapshot and its log in the bucket,
  unless a retention run was deleting the snapshot it takes over at that moment: closing that
  needs conditional writes.
- **Restore:** restores through the delegate. A backup with segment files, taken without
  `backupLog`, is returned as it is. For one without, it reads the backup's log; downloads the
  snapshot into `{target folder}/snapshot/` if the backup has one and the delegate gave no snapshot
  files; and writes a journal into the target folder, named as the partition's
  (`{tenant}-partition-{partition}`), with the path's entries from `firstIndex` up to the first
  entry with a record past `checkpointPosition`. `PartitionRestoreService` then installs the
  snapshot and reads that journal as a backup's.
- **Delete** and **markFailed:** through the delegate first, then the backup's log is deleted. Its
  snapshot stays until retention finds no backup log naming it.
  Everything else goes to the delegate.
- **Retention** keeps every backup's log: a segment that serves the path of a backup's history at
  an index from `firstIndex` to `lastIndex` is never deleted, whatever its age, and nor is the
  backup's snapshot. The retention marker still moves on, so consumers don't see what only backups
  keep. Retention keeps nothing else for restores: there is no restore from the bucket without a
  backup.
- `BackupLog.fromJson` skips fields it doesn't know, and throws `InvalidBackupLogException` for
  JSON that isn't a valid backup log.

## Service

A plain JDK `HttpServer` with JSON bodies. This deviates from the plan's Spring Boot: the service
needs no configuration machinery.

| Endpoint | What |
|---|---|
| `POST /v1/uploads` | An `UploadNotice`. Passes it on to the workers subscribed to its log, and to the configured peers unless the request has the header `X-Event-Plane-Forwarded: true`. Answers 202. |
| `GET /v1/notifications?log={c}/{t}/{p}&log=…` | Server-sent events: `event: upload`, with the notice's JSON on one `data:` line. A comment line `: keep-alive` every 15 seconds. Each `log` value is URL-encoded (`c%2Fdefault%2F1`), and the service decodes it. |
| `GET /v1/groups/{group}` | `{"group":"g","workers":[{"worker":"w-…","beat":42}],"cursors":[{"log":"c/t/p","history":"…","index":1,"position":1,"worker":"w-…","newestIndex":40,"lag":39}]}`. `newestIndex` is the last index of the newest segment of the cursor's history that the service knows of, from notices or a listing, or null, with a lag of 0, if it knows of none. |
| `GET /v1/clusters` | `{"clusters":{"c":{…}}}`: the store each cluster writes to, with `StoreConfig`'s field names, the secret as `***`. From `eventplane.service.clusters`, a JSON object of cluster id to store, empty by default. |
| `GET /v1/credentials` | A stub: the store's configuration, as a worker would use it. With `?cluster=c`, the store of that cluster, secret included, or 404 if unknown. The consumer's `ServiceClusters.fetch(service)` reads both. |

## Consumer group objects

Written by workers with plain PUTs, read by workers and the service. They live in the group's home
store. The logs a group reads can live elsewhere: clusters that write to stores of their own are
read from there, and only the group's objects need one place every worker writes to.

```json
// _groups/{group}/group.json, written by an admin or the service
{"version":1,"name":"g","subscription":[{"cluster":"c","tenant":"*"}],"start":"earliest"}
// _groups/{group}/workers/{worker}.json, rewritten every heartbeat
{"version":1,"worker":"w-01JD…","beat":42}
// _groups/{group}/cursors/{cluster}/{tenant}/{partition}.json, rewritten by the holder
{"version":1,"history":"…","index":1096704,"position":1052032,"worker":"w-01JD…"}
```

- A worker is live while its heartbeat object keeps changing (`beat` counts up), timed on the
  reader's own clock. No reader compares timestamps written by someone else. A worker whose
  heartbeat object is gone is dead at once: a closed worker deletes its own.
- A log's owner is the live worker with the highest rendezvous hash: the first 8 bytes of SHA-256
  of `"{worker}\n{c}/{t}/{p}"`, compared as unsigned; a tie goes to the larger worker id. Every
  worker must hash alike, so this is part of the contract.
- Readers skip fields they don't know. The consumer module holds the codecs of these objects and
  of the retention marker.

## The history file after a restore

`{partition data directory}/event-plane-history.json`, written by the restore, read by the leader's
publisher on its first start:

```json
{"history":"…","createdAt":"2026-10-20T12:00:00Z","source":null,"index":1096704,"position":1052032}
```

- The operator names the new history (`zeebe.broker.experimental.eventPlane.restoreHistory`), so
  that every broker's restore agrees on it. Index and position are the checkpoint's: the last
  restored entry, which holds the checkpoint record alone.
- A restore from a backup doesn't know the source, the history the backup was taken in, and leaves
  it null. The publisher then takes it from the restored floor, the upload floor in the backup's
  snapshot. Without a floor, the source is the tenant's first history if it has a part for the
  partition, and else there is none (`Base.NOTHING`). A backup whose log is in the bucket names
  its history in its backup log, but the restore doesn't take the source from there yet.
- Once the floor names the new history, the publisher reads the history's part from the store, as
  it does without a file.

## Conventions for whoever writes code

- Work in your module, and in the files your task names. No git commands.
- Build from the worktree root, with `JAVA_HOME=/home/lena/.local/share/mise/installs/java/25`:
  - format: `./mvnw -B -o -pl <module> spotless:apply`
  - check: `./mvnw -B -o -pl <module> verify -P '!autoFormat,checkFormat,spotbugs'`, which runs the
    tests, the license header check, checkstyle, spotbugs and the dependency analysis. Each
    dependency used must be declared, and each declared one used.
- Every file starts with the Camunda License 1.0 header. Every package has a `package-info.java`
  with `@NullMarked`.
- Code like the rest of Zeebe: `final` locals, parameters and fields; small classes; Javadoc that
  says what, briefly.
- Exception messages say what was expected and what was found, with the values: "Expected X, but
  got Y". Use custom exception types for failure kinds, carrying the values as fields.
- Tests: JUnit 5 and AssertJ, `should…` names, `// given`, `// when`, `// then`.
- No third-party dependency that the parent POM doesn't manage.
