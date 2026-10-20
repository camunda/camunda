# Event Plane in the bucket: contracts

The contracts that every module of the prototype builds against. The plan is
[prototype-plan.md](prototype-plan.md). If something here is wrong or missing, fix it here first,
then in the code.

## Modules and packages

| Module | Package | Depends on |
|---|---|---|
| `zeebe/event-plane/segment` | `io.camunda.zeebe.eventplane.segment` | zstd-jni |
| `zeebe/event-plane/common` | `io.camunda.zeebe.eventplane.store`, `.history`, `.notification`, `.snapshot` | Jackson, AWS SDK v2 (S3) |
| `zeebe/event-plane/publisher` | `io.camunda.zeebe.eventplane.publisher` | segment, common |
| `zeebe/event-plane/consumer` | `io.camunda.zeebe.eventplane.consumer` | segment, common, zeebe-protocol-impl, zeebe-logstreams |
| `zeebe/event-plane/maintenance` | `io.camunda.zeebe.eventplane.maintenance` | segment, common |
| `zeebe/event-plane/service` | `io.camunda.zeebe.eventplane.service` | common, consumer (for the group objects) |
| `zeebe/event-plane/qa` | `io.camunda.zeebe.eventplane.qa` | everything, `zeebe-qa-util` |
| `zeebe/broker` | `io.camunda.zeebe.broker.eventplane` | publisher |

A log is a partition of a physical tenant of a cluster: the segment module's
`Identity(clusterId, tenantId, partition)`. The tenant is the partition group's name, `default` for
the default one.

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
| A consumer group | `_groups/{group}/group.json` |
| A worker's heartbeat | `_groups/{group}/workers/{worker}.json` |
| A cursor | `_groups/{group}/cursors/{cluster}/{tenant}/{partition}.json` |

`level` is 0 for a publisher's segment and 1 for a rollup. Names sort by first index, so a listing
of a history's directory is in log order. `ObjectNames.parseSegment(key)` gives
`SegmentName(history, firstIndex, lastIndex, level, ulid)`. `ObjectNames` takes cluster, tenant and
partition as plain values, so that `common` doesn't depend on `segment`.

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

The floor is an entry in the broker's exporter state, under the exporter id `event-plane`, which the
exporter director treats as configured whenever the Event Plane is on. Its position is the last
uploaded record's position, or -1 before any upload. Its metadata is JSON:
`{"history":"…","index":1096704}`. The exporter director persists it, sends it to followers, and
compaction stops at it.

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

With `zeebe.broker.experimental.eventPlane.snapshotInterval` above zero, the leader's publisher
thread uploads the partition's latest snapshot at most once per interval, unless its descriptor is
in the store already. It reserves the snapshot while it uploads, and PUTs every file of
`PersistedSnapshot.files()` (the checksum file `{snapshotId}.checksum` among them, as backups carry
it), then the descriptor last. `io.camunda.zeebe.eventplane.snapshot.SnapshotDescriptor`, at
`{cluster}/{tenant}/{partition}/snapshots/{snapshotId}/descriptor.json`:

```json
{"version":1,"snapshotId":"…","index":1096000,"term":3,"processedPosition":1051900,
 "exportedPosition":1051800,"maxExportedPosition":1052032,"lastFollowupEventPosition":1052010,
 "history":"…","files":[{"name":"000123.sst","size":4096}],"createdAt":"2026-10-20T12:00:00Z"}
```

- `exportedPosition` is the lowest exporter position, `maxExportedPosition` the highest, the upload
  floor's included. `history` is the one the uploading leader publishes in. Readers skip fields they
  don't know.
- **A descriptor means every file is there:** it is written after the files and deleted before
  them. Files without a descriptor are an upload in progress, or what a stopped upload or deletion
  left; maintenance deletes them once all are older than the retention.
- **What a snapshot relies on:** its required position is the highest of its four positions. A
  journal restored from it must reach that position, or the state would hold what the journal
  doesn't. A snapshot serves a path if its history is on it and, for a history that isn't the
  path's own, its required position is at or before the checkpoint where the next history took
  over.
- **Retention** deletes snapshots older than the retention, but always keeps the newest one that
  serves the current path, and keeps every segment of the path from the oldest kept snapshot's
  index + 1 on: the cutoff is the first index of the earliest segment that is young or holds an
  entry at or after that bound. Snapshots go before segments.

## Restore from the bucket

`io.camunda.zeebe.restore.BucketRestoreService` restores one partition, and `RestoreManager` uses it
for every local partition when `zeebe.broker.experimental.eventPlane.restoreFromBucket` is true,
without a backup store. `restoreTo` is the position to restore to, unset for everything the bucket
holds; `restoreHistory` names the history that goes on, as for a backup. The cluster id is
`clusterId` if set, else the bucket's only cluster.

- It follows the tenant's current history's path, and takes the newest snapshot that serves it and
  whose required position is at or before the target.
- It resets the journal to the snapshot's index + 1, the first entry Raft needs, and appends the
  path's entries from there with `Journal.append(checksum, serializedRecord)`, up to the first entry
  with a record past the target: entries without records before it are restored too. Each entry
  comes from a segment that serves it, the highest level, then the highest last index.
- If the restored journal doesn't reach the snapshot's required position, it takes the next older
  snapshot; without any, the journal from index 1. A target past the bucket's last record fails.
- It leaves the history file with the current history as `source`, the last restored entry as
  `index`, and the last record's position at or before it as `position`.

## Service

A plain JDK `HttpServer` with JSON bodies. This deviates from the plan's Spring Boot: the service
needs no configuration machinery.

| Endpoint | What |
|---|---|
| `POST /v1/uploads` | An `UploadNotice`. Passes it on to the workers subscribed to its log, and to the configured peers unless the request has the header `X-Event-Plane-Forwarded: true`. Answers 202. |
| `GET /v1/notifications?log={c}/{t}/{p}&log=…` | Server-sent events: `event: upload`, with the notice's JSON on one `data:` line. A comment line `: keep-alive` every 15 seconds. Each `log` value is URL-encoded (`c%2Fdefault%2F1`), and the service decodes it. |
| `GET /v1/groups/{group}` | `{"group":"g","workers":[{"worker":"w-…","beat":42}],"cursors":[{"log":"c/t/p","history":"…","index":1,"position":1,"worker":"w-…","newestIndex":40,"lag":39}]}`. `newestIndex` is the last index of the newest segment of the cursor's history that the service knows of, from notices or a listing, or null, with a lag of 0, if it knows of none. |
| `GET /v1/credentials` | A stub: the store's configuration, as a worker would use it. |

## Consumer group objects

Written by workers with plain PUTs, read by workers and the service:

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
  partition, and else there is none (`Base.NOTHING`). A restore from the bucket knows the source,
  and names it.
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
