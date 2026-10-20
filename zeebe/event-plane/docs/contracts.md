# Event Plane in the bucket: contracts

The contracts that every module of the prototype builds against. The plan is
[prototype-plan.md](prototype-plan.md). If something here is wrong or missing, fix it here first,
then in the code.

## Modules and packages

| Module | Package | Depends on |
|---|---|---|
| `zeebe/event-plane/segment` | `io.camunda.zeebe.eventplane.segment` | zstd-jni |
| `zeebe/event-plane/common` | `io.camunda.zeebe.eventplane.store`, `.history`, `.notification` | Jackson, AWS SDK v2 (S3) |
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
 "partitions":{"3":{"sourceHistoryId":null,"index":0,"position":0}}}
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
  reader's own clock. No reader compares timestamps written by someone else.
- Readers skip fields they don't know.

## The history file after a restore

`{partition data directory}/event-plane-history.json`, written by the restore, read by the leader's
publisher on its first start:

```json
{"history":"…","createdAt":"2026-10-20T12:00:00Z","source":"…","index":1096704,"position":1052032}
```

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
