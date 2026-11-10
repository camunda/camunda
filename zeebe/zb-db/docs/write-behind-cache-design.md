# Write-behind state cache for zb-db (EhCache 3)

Solution design and implementation plan. Status: draft for the
`poc-single-storage-workshop-zbdb-cache` exploration.

## 1. Goal

Committed state goes into an in-memory cache first. The backing store is updated asynchronously
("write-behind") with batching and coalescing. The backing store is RocksDB today, and may be an
RDBMS later, so the cache must work with any `ZeebeDb` and depend on `zeebe-db-api` only.
Hypotheses to validate, with RocksDB as the backend:

- Hot point reads are served from the heap instead of JNI + memtable/block-cache lookups.
- Short-lived state (keys inserted and deleted within the write window) never reaches RocksDB as
  a put. That means less memtable churn and fewer flushes and compactions.
- The write-behind queue absorbs RocksDB write stalls, up to its capacity, so the processing thread
  doesn't see them.

Non-goals: changing how callers use `ZeebeDb`/`ColumnFamily`, changing the snapshot or replication
format, or using the cache as durable storage.

## 2. How zb-db works today

|   Concern    |                                                                                                          Today                                                                                                           |                                                   Where                                                    |
|--------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| Layout       | One RocksDB column family. Each "virtual" column family is an 8-byte key prefix.                                                                                                                                         | `ColumnFamilyContext#writeKey`                                                                             |
| Transactions | One `ZeebeTransaction` per `TransactionContext`, backed by a `WriteBatchWithIndex` (WBWI). Commit runs `db.write(batch)`; rollback runs `batch.clear()`.                                                                 | `ZeebeTransaction`, `DefaultTransactionContext`                                                            |
| Point ops    | `put`/`get`/`delete` on the WBWI through `MethodHandle`s. `getFromBatchAndDB` gives read-your-writes.                                                                                                                    | `RocksDbInternal`                                                                                          |
| Iteration    | `WBWI.newIteratorWithBase(db.newIterator(prefixReadOptions))`. Callers use seek/next or seekForPrev/prev and stop on prefix mismatch.                                                                                    | `TransactionalColumnFamily#forEachInPrefix*`, `RawTransactionalColumnFamily`, `ForeignKeyChecker`          |
| Snapshots    | RocksDB `Checkpoint` of the live DB, taken while processing continues. The StateController picks the position; the checkpoint runs on the snapshot store's actor, on the broker's I/O-bound pool (2 threads by default). | `ZeebeTransactionDb#createSnapshot`, `StateControllerImpl#takeSnapshot`, `FileBasedTransientSnapshot#take` |
| Durability   | WAL disabled, `avoid_flush_during_shutdown`. On (re)start, the runtime dir is deleted and restored from the latest snapshot, then the log is replayed.                                                                   | `RocksDbConfiguration#DEFAULT_WAL_DISABLED`, `ZeebeRocksDbFactory`, `StateControllerImpl#recoverInternal`  |

### Facts that shape the design

1. **The runtime DB is already disposable.** Recovery is the latest snapshot plus log replay. A
   crash that loses unflushed writes costs nothing, so write-behind is viable at all.
2. **The snapshot is the only durability point, and it has a strict invariant.** The checkpoint
   must hold a transaction-consistent state that includes everything up to
   `lowerBoundSnapshotPosition`. Today every checkpoint meets this, because each transaction is a
   single atomic `db.write`. Two things can go wrong under write-behind:
   - RocksDB lags behind (its processed position is below the lower bound). The log can then be
     compacted past records that replay still needs.
   - The checkpoint captures half a transaction. The restored state is then corrupt.
3. **Iteration is everywhere.** The engine iterates column families at 137 call sites in 47 state
   classes, next to 626 point reads and writes (`get`, `exists`, `insert`, `update`, `upsert`,
   `delete…`). 79 of the 137 run per command, 19 of those also during replay, and only 12 never run
   in production; [engine-state-iteration.md](engine-state-iteration.md) lists them all. Ordered
   prefix scans are core: due timers, job deadlines, children of an element instance, variables of
   a scope. A hash cache can't serve them, and RocksDB alone is stale under write-behind.
4. **Several actors share the DB.** Contexts are created by StreamProcessor, ExporterDirector,
   StateQueryService, backup/checkpoint state, DbPositionSupplier and migrations. Each context is
   single-threaded, but the store must be thread-safe across contexts.
5. **Tests already encode the contracts.**
   - Read-your-writes, including iteration over your own uncommitted writes.
   - Delete-while-iterating (`ColumnFamilyTest#shouldDeleteOnForeachPair`, `#shouldDeleteWhileTrue`).
   - Explicitly *no* snapshot isolation between transactions
     (`ZeebeTransactionIsolationTest.NoSnapshotIsolationTest`): a transaction may read commits made
     after it started. That doesn't allow seeing *part* of another transaction. Today a commit is
     one atomic `WriteBatch`, so readers see all of it or none of it, and a RocksDB iterator reads a
     single point in time. The design keeps both properties.

## 3. What EhCache 3 write-behind gives, and what it doesn't

Verified against the ehcache3 `v3.11.1` and `v3.12.0` sources. 3.12.0 is the latest release, and
`BatchingLocalHeapWriteBehindQueue` is identical in both.

|        Property         |                                                                                                Behaviour                                                                                                 |                                                               Impact                                                               |
|-------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Read of a pending write | `AbstractWriteBehind#load` returns the pending op for a key before it calls our loader.                                                                                                                  | Good: read-your-writes still works after eviction, apart from the next row.                                                        |
| Pending-op bookkeeping  | After a batch is written, `latest.remove(key)` runs unconditionally. It also drops a *newer* pending op for the same key in a later batch.                                                               | If the entry is evicted in that window, a read falls through to the backend and gets the old value. We need our own pending index. |
| Ordering                | `concurrencyLevel` stripes by `key.hashCode()`. Order is kept per key, not across keys. A batch splits into alternating `writeAll`/`deleteAll` calls, and batch boundaries don't match our transactions. | The backend never holds a transaction-consistent state on its own, so snapshots need a barrier.                                    |
| Writer failure          | No retry. EhCache logs a WARN and removes the batch's keys from the cache (`store.remove`).                                                                                                              | Committed state silently disappears from the live view. Our writer must never throw.                                               |
| Back-pressure           | A full queue blocks the committing thread in `BlockingQueue#put`. EhCache enqueues inside its per-key `compute` (`LocalLoaderWriterStore#put`), so that map bin stays locked while it waits.             | Blocks an actor thread, as RocksDB write stalls do today. Cache reads of other keys in the same map bin wait too.                  |
| Load vs. put            | On a miss, `cache.get` runs the loader inside the heap store's per-key `compute` (`OnHeapStore#computeIfAbsent`). A concurrent `put` for that key waits, then wins.                                      | A load can't overwrite a newer committed value.                                                                                    |
| Flush/drain             | No public API (`WriteBehind#getQueueSize` is internal).                                                                                                                                                  | We need our own "everything flushed" signal.                                                                                       |
| Iteration               | None: unordered, and only resident entries.                                                                                                                                                              | We need our own ordered overlay.                                                                                                   |
| Dependencies            | `org.ehcache:ehcache` pulls in `javax.cache:cache-api` (compile) and `jaxb-runtime` (runtime). Apache-2.0 license.                                                                                       | Exclude JAXB, since we configure the cache in code.                                                                                |

**Consequence:** EhCache gives us a hot point-read cache and an async writer that batches,
coalesces and applies back-pressure. We still have to build the correctness pieces around it:
ordered reads of unflushed state, snapshot consistency and error handling.

## 4. Design

### 4.1 Overview

The cache is a `ZeebeDb` that wraps another `ZeebeDb`, through the public API only. It doesn't know
which backend it wraps: RocksDB today, possibly an RDBMS later.

|         Module         |       Directory       |                                                                Holds                                                                |
|------------------------|-----------------------|-------------------------------------------------------------------------------------------------------------------------------------|
| `zeebe-db-api`         | `zeebe/zb-db/api`     | The interfaces, plus the backend-neutral types that Phase 1b moves in from `zeebe-db-rocksdb` (below).                              |
| `zeebe-db-rocksdb`     | `zeebe/zb-db/rocksdb` | The RocksDB implementation (`impl.rocksdb`).                                                                                        |
| `zeebe-db-cache` (new) | `zeebe/zb-db/cache`   | The write-behind cache (package `io.camunda.zeebe.db.cache`). Depends on `zeebe-db-api` and EhCache only.                           |
| `zeebe-db-tests`       | `zeebe/zb-db/tests`   | Test-only. Runs the contract tests against every implementation, and binds the cache to RocksDB for that. The only place they meet. |

**Backend-neutral types move into the API (Phase 1b).** The cache has to throw the same exceptions
and enforce the same consistency checks as RocksDB, and an RDBMS backend would need the same key
types. These live in `zeebe-db-rocksdb` today and move to `zeebe-db-api`, with package names
unchanged:

- `ZeebeDbException` and `ZeebeDbInconsistentException`
- `ConsistencyChecksSettings`, `ContainsForeignKeys`, and `AccessMetricsConfiguration` with the
  column family metrics (`ColumnFamilyMetricsDoc`, `FineGrainedColumnFamilyMetrics`,
  `NoopColumnFamilyMetrics`)
- the key and value types in `io.camunda.zeebe.db.impl`: `DbLong`, `DbString`, `DbCompositeKey`,
  `DbForeignKey`, `DbTenantAwareKey` and the rest, with `ZeebeDbConstants` and `DefaultColumnFamily`

None of them uses RocksDB. `zeebe-db-api` stops being interfaces-only and gains `agrona` and
`zeebe-micrometer` as dependencies.

**How the cache reaches the backend.** Only through `ZeebeDb`, `TransactionContext` and
`ColumnFamily`:

- `WriteBehindZeebeDbFactory` wraps any `ZeebeDbFactory`. `WriteBehindZeebeDb` wraps the
  `ZeebeDb` it creates.
- The engine gets the cache's own `CachedColumnFamily`, `CachedTransactionContext` and
  `CachedTransaction`. They serialize keys and values with `DbKey#write`/`DbValue#write`, and wrap
  results back into the caller's instances.
- Toward the backend, the cache uses **pass-through column families**: for each column family, it
  creates a backend `ColumnFamily<RawKey, RawValue>`. `RawKey` and `RawValue` are cache-internal
  `DbKey`/`DbValue` implementations that write and read raw bytes unchanged. Through them:
  - the writer applies a batch as one backend transaction (`runInTransaction` with `upsert` and
    `deleteIfExists`)
  - a cache miss loads with `get`
  - iteration uses the backend's `whileEqualPrefix`, `whileTrue` and `whileTrueReverse`
- Snapshots go through `ZeebeDb#createSnapshot`, after a barrier (§4.7).

**Two contracts the cache relies on**, which the contract tests pin down for every backend:

- **Iteration order.** A column family iterates in the unsigned byte order of the serialized key.
  The cache merges its overlay with the backend's iteration, so the two orders must match. RocksDB
  does this; an RDBMS would need a binary key column with byte-wise ordering. `ColumnFamily`'s
  javadoc says so explicitly from Phase 1b on.
- **Atomic, consistent backend.** A backend transaction commits all of its writes or none, and
  `createSnapshot` captures one consistent point, even while other transactions commit.

```mermaid
flowchart LR
  subgraph ctx["CachedTransactionContext (one per actor)"]
    TX["CachedTransaction<br/>tx buffer (ConcurrentSkipListMap)"]
  end
  subgraph store["WriteBehindStore (one per WriteBehindZeebeDb)"]
    LOCKS["commitLock (one committer at a time)<br/>publishLock (StampedLock)"]
    DIRTY["DirtyIndex<br/>one ConcurrentSkipListMap per column family<br/>unflushed versions only"]
    CACHE["EhCache heap tier<br/>CacheKey → CachedValue"]
    WB["EhCache write-behind<br/>(batching, coalescing, queue)"]
    LW["BackendLoaderWriter"]
  end
  BACKEND[("backend ZeebeDb<br/>(RocksDB today)")]
  TX -- "1. publish" --> LOCKS --> DIRTY
  LOCKS -- "2. enqueue" --> CACHE --> WB --> LW -- "one backend transaction" --> BACKEND
  LW -- "remove flushed versions" --> DIRTY
  CACHE -. "miss: load" .-> LW -. "DirtyIndex, then backend get" .-> BACKEND
```

Read paths:

- **Point read:** tx buffer → DirtyIndex → EhCache → on a miss, the loader (DirtyIndex again, then
  the backend). The lookup is validated against concurrent publications.
- **Iteration:** merge the live tx buffer and a copy of the DirtyIndex range with the backend's own
  iteration, taken at the same instant. On equal keys, the first one in that order wins.

### 4.2 Data model

- `CacheKey` holds the column family and the serialized key, as an immutable `byte[]` written by
  `DbKey#write`. `equals`/`hashCode` use both. Within a column family, keys compare with
  `Arrays.compareUnsigned`, the order that the backend iterates in (§4.1), so the overlay sorts like
  the backend.
- The DirtyIndex is one sorted map per column family, so a scan copies from a single map.
- `CachedValue` has two fields:
  - `byte @Nullable [] bytes` (`null` means tombstone)
  - `long seq` (global commit sequence; all writes of one transaction share it)
- **A delete is a tombstone put. We never call `cache.remove()`.** This has three benefits:
  - The writer gets the value, and so its `seq`, for deletes too. EhCache's `deleteAll` only hands us
    keys.
  - `exists()` gets negative caching.
  - Coalescing folds an insert plus a delete into a single op.
- The arrays in the cache and the DirtyIndex are shared and never handed out:
  - Commit copies them in, because the cache serializes keys and values into reused buffers.
  - Every read copies them out: `get`, and an iterator's `key()`/`value()`. Callers can keep or
    mutate what they get without touching committed state. This matters because msgpack's
    `StringValue#read` and `BinaryValue#read` keep views into the source buffer.
  - With RocksDB it costs nothing extra: every JNI `get` and iterator call already returns a fresh
    array.
  - Copying into a reusable per-context buffer instead would break `get(key, valueSupplier)`, which
    promises an instance the caller can keep.

### 4.3 Transactions: `CachedTransaction`

The cache implements the API's `TransactionContext` and `ZeebeDbTransaction` itself.
`CachedTransactionContext` follows the same rules as RocksDB's `DefaultTransactionContext`: nested
`runInTransaction` calls share one transaction, the outermost call commits, and an error rolls back.
Each context also holds a backend `TransactionContext` of its own, for the loads and scans it makes
(§4.4, §4.5).

The cached implementation:

- A per-context `ConcurrentSkipListMap<CacheKey, Op>` is the transaction buffer. The backend's own
  transaction can't be the buffer, because its reads see the backend, which lags behind the cache.
- **Consistency checks run in the cache.** The `insert`, `update` and `deleteExisting`
  preconditions and the foreign-key checks (`ConsistencyChecksSettings`, `ContainsForeignKeys`) read
  the cached view, through the same path as `get` and scans. The backend lags behind, so its
  checks would give wrong answers. They also don't fire: the pass-through column families only use
  `upsert` and `deleteIfExists`, and `RawKey` carries no foreign keys.
- A skip list (rather than a `TreeMap`) keeps iteration weakly consistent, so delete-while-iterating
  keeps working.
- `put`/`delete` copy the key and value bytes into the buffer.
- `get` probes the buffer with a reusable lookup key, then falls back to the store.
- `rollback` is `clear()`.
- `commit`:

  ```text
  if txBuffer.isEmpty(): failIfStoreFailed(); return    // read-only: no lock
  commitLock.lock()                               // one committer at a time, start to finish
  try
    failIfStoreFailed()
    wait while pendingBytes > maxPendingBytes     // byte back-pressure; wakes on cleanup or failure
    if txBuffer exceeds writeThroughMaxKeys or writeThroughMaxBytes:
      commitWriteThrough(); txBuffer.clear(); return   // see below
    seq = nextSeq()
    values = [(key, new CachedValue(op.bytes, seq)) for (key, op) in txBuffer]
    stamp = publishLock.writeLock()               // short; nothing inside it blocks
      for (key, v) in values: dirtyIndex.put(key, v); pendingBytes += size(key, v)
    publishLock.unlockWrite(stamp)
    try
      for (key, v) in values: cache.put(key, v)   // enqueue; may block on back-pressure
    catch e: failStore(e); throw e                // published versions can't drain any more
    failIfStoreFailed()                           // the writer may have failed while we waited
    txBuffer.clear()                              // only after success
  finally
    commitLock.unlock()
  ```
- **Read-only transactions take no lock.** `DefaultTransactionContext` commits every standalone
  operation, reads included. Without the fast path, a read on another actor would wait behind a
  committer that is blocked on a full queue. One such read is the exporter-position read that the
  StateController makes before each snapshot.
- **The buffer is cleared only after success.** The stream processor retries a commit that throws
  a recoverable `ZeebeDbException` (`RecoverableRetryStrategy`). RocksDB's commit keeps its batch
  until the write succeeds, so the retry writes the same changes; clearing in `finally` would let
  the retry "succeed" on an empty buffer. A store failure throws a non-recoverable exception, so it
  isn't retried.
- `publishLock` is a `StampedLock`. Its write side covers the DirtyIndex update for the whole
  transaction, and readers validate against it (§4.4, §4.5). So a reader sees all of a transaction
  or none of it, as with an atomic backend commit today.
- `commitLock` serializes committers end to end, so the enqueue order matches the publication
  order. Without it, two contexts writing the same key could leave the cache on one version and the
  DirtyIndex on the other. Coalescing could then drop the newer version from the queue, so that it
  is never flushed. Serializing costs little, because the stream processor does almost all commits.
- Only two places change the DirtyIndex: publication (above) and cleanup after a flush (§4.6). Both
  hold `publishLock`'s write side. So no bookkeeping has to run as a side effect inside
  `ConcurrentSkipListMap#compute`, whose function may run more than once.
- Back-pressure blocks outside `publishLock`, so a full queue doesn't stall readers' validation.
  Cache reads of keys in the same map bin can still wait (§3).
- **Byte back-pressure.** The queue and the cache count entries, not bytes, and one value can be
  megabytes (a variable document, for example). So the store also tracks `pendingBytes`, the key and
  value bytes in the DirtyIndex. A commit waits while it exceeds `maxPendingBytes`, before it
  publishes anything. Cleanup lowers it and wakes the waiter.
- **Enqueue failure.** `cache.put` can throw, for example a `RejectedExecutionException` once the
  write-behind executor has shut down. The versions already published can then never drain, so
  the store fails through the same path as a writer failure (§4.6): it reports to partition health,
  and later commits, reads and snapshots fail fast. `finally` releases `commitLock`.
- **Oversized transactions commit write-through**, so that one transaction can't swell the
  DirtyIndex and the queue past their budget (§4.11). A transaction above `writeThroughMaxKeys` keys
  or `writeThroughMaxBytes` bytes, still under `commitLock`:
  1. waits, up to `drainTimeout`, until the DirtyIndex is empty. A timeout throws a recoverable
     `ZeebeDbException`, and a store failure aborts the wait at once (§4.6).
  2. under `publishLock`'s write side: sets `lastWriteThroughSeq` to a new seq, writes its buffer
     to the backend in one backend transaction, then empties the cache with `Cache#clear`. That
     call doesn't reach the loader-writer.

  Details:
  - **Errors map as they do today.** A failed backend transaction applies nothing, and the cache
  isn't cleared yet. A recoverable backend error arrives as a `ZeebeDbException`, and the processor
  retries with the buffer intact.
  - **Readers wait for it.** Holding `publishLock`'s write side across the whole backend write,
  including any write stall, plus the clear is what makes the transaction visible all at once.
  Optimistic reads fail validation meanwhile, and the read-lock fallback and iterator creation
  wait. This is the only place where a large write blocks readers; its duration is measured
  (§4.10).
  - **EhCache can still serve a version that the write-through replaced.** Cleanup empties the
  DirtyIndex inside `writeAll`, but EhCache drops the batch's keys from its pending-op map
  (`latest`) only after `writeAll` returns. A cache miss in that window loads the old pending
  version without calling our loader. So a point read doesn't trust a cached value whose `seq` is
  below `lastWriteThroughSeq`, and reads the backend instead (§4.4). Cache entries that our loader
  installs can't be older than the write-through: the clear runs after the backend write, and a
  load holds its key's bin from the backend read until it installs the value.
  - **It's the only place that calls the cache under `publishLock`.** That's safe because holding
  `commitLock` keeps out every committer, and committers are the only threads that can block
  inside the cache.
  - **The main case is the migration step.** `MigrationTransitionStep` runs every migration in one
  transaction, and a migration can rewrite whole column families.

### 4.4 Point reads

```text
stamp = publishLock.tryOptimisticRead()
v = txBuffer.get(key) ?: dirtyIndex.get(key) ?: cache.get(key)   // cache miss: the loader runs
if v came from the cache and v.seq < lastWriteThroughSeq: v = backendGet(key)   // §4.3
if publishLock.validate(stamp): return copy of v.bytes

// after a few failed tries: read under the lock, without the cache
stamp = publishLock.readLock()
  v = txBuffer.get(key) ?: dirtyIndex.get(key) ?: backendGet(key)
publishLock.unlockRead(stamp)
return copy of v.bytes                                           // null for a tombstone or a miss
```

- The DirtyIndex comes before the cache, for two reasons:

  - The cache can briefly hold an older version, between publication and enqueue.
  - EhCache's `latest.remove(key)` can drop a pending op.

  The DirtyIndex holds every unflushed version, so checking it first covers both.

- **`backendGet` uses the calling context's pass-through column family.** Contexts are
  single-threaded, so each reads the backend through its own backend `TransactionContext` (§4.3).
  EhCache runs the loader on the thread that called `cache.get`, so the cached column family hands
  its backend column family to the loader through a thread-local for the duration of that call.

- **The loader checks the DirtyIndex again, then reads the backend.** Checking the backend alone
  isn't enough. Take this sequence:

  1. A reader misses the DirtyIndex just before a commit publishes.
  2. The entry is evicted, and EhCache has dropped its pending op.
  3. The reader misses the cache, and the loader reads the old value from the backend.

  Validation retries the read, but the old value would stay cached. It would then be served once
  the new version flushes and leaves the DirtyIndex. The load runs inside EhCache's per-key
  `compute`, so a DirtyIndex check there installs either the newest committed version or a value
  that the pending enqueue overwrites. The loader stamps what it reads from the backend with the
  latest commit `seq`.

- **No reader calls the cache while holding `publishLock`.** A committer blocked on a full queue
  holds the cache's map bin (§3), and the writer needs `publishLock`'s write side for cleanup
  before it can take the next batch. A reader that held the read lock while waiting for that bin
  would close the cycle. So the read-lock fallback goes to the backend directly. That's correct
  because, while publication and cleanup are excluded, anything not in the DirtyIndex has been
  flushed.

- Validation makes the lookup atomic with respect to publications. Two reads can still straddle a
  commit and see an older K1 with a newer K2, as they can today. They can never see a newer K1 with
  an older K2 from the same transaction.

- EhCache may keep the key object when it loads an entry, so `cache.get` needs an immutable key
  copy. That's one more allocation per cache read; measure it.

### 4.5 Iteration: merging with the backend's scan

The backend only iterates through `ColumnFamily`'s visitor methods, so it pushes entries to a
callback; it doesn't hand out an iterator. `CachedColumnFamily` merges two pull sources into that
push:

1. the live tx buffer range (`tailMap`, or `headMap(..).descendingMap()` for reverse)
2. a copy of the DirtyIndex range

and the backend's scan of the same range, through the pass-through column family
(`whileEqualPrefix`, `whileTrue` or `whileTrueReverse`, with the same start key).

For each entry the backend visits:

1. emit the overlay entries that come before it, skipping tombstones
2. if the overlay has the same key, emit the overlay's version, or nothing for a tombstone;
   otherwise emit the backend's entry
3. stop the backend scan as soon as the caller's visitor returns `false`

When the backend scan ends, emit the rest of the overlay range. Rules:

- On equal keys, the tx buffer wins over the DirtyIndex copy, which wins over the backend.
- The DirtyIndex copy covers only the prefix being scanned.
- Key-only scans use the backend's key-only visitors in the same way; `count`, `isEmpty` and
  `exists` reuse the merge.

**Point-in-time view.** The overlay copy and the backend's view must come from one instant:

```text
stamp = publishLock.readLock()
overlay = copy of dirtyIndex[columnFamily].subMap(prefixStart, prefixEnd)
backendColumnFamily.whileEqualPrefix(prefix, startAt, entry -> {
  if stamp is held: publishLock.unlockRead(stamp)   // the backend has fixed its view by now
  ...merge as above...
})
if stamp is still held: publishLock.unlockRead(stamp)   // the scan found nothing
```

- The read lock keeps publication and cleanup out from the copy until the backend has fixed its
  view. A backend fixes its view before its first callback: RocksDB creates its iterator first, and
  an RDBMS runs its query first.
- Every unflushed version is in the copy. Every version flushed and cleaned up before the copy is
  in the backend's view, since the view comes later.
- A flush can land between the copy and the backend's view. Its entries are then in both, and the
  copy wins with the same or a newer version.
- No transaction is published in that window, so none can be partly flushed into the backend's
  view while missing from the copy.

So the merge shows the committed state at one instant, like a RocksDB iterator today, even though
the writer's batches cut across transactions. Only the tx buffer stays live, which keeps
delete-while-iterating working. After the first callback the scan never reads the live DirtyIndex,
so cleanup doesn't wait for open scans, and a long scan doesn't hold back memory.

Costs:

- **Publications wait for the backend's first entry.** With RocksDB that's one seek; with an RDBMS
  it's a query round trip. Measure it in Phase 3 (§4.10).
- **The copy** costs O(dirty entries in the scanned prefix). A prefix scan touches a handful of
  entries. A scan over a whole column family that stops early (due timers, job deadlines) copies
  every dirty entry of that column family, which is bounded by the DirtyIndex budget (§4.11).

### 4.6 Writer: `BackendLoaderWriter`

- `writeAll(entries)` applies the batch as one backend transaction, on a backend
  `TransactionContext` that only the writer uses: `runInTransaction` with `upsert` for a value and
  `deleteIfExists` for a tombstone, on the pass-through column family of each entry's column
  family. `deleteAll` is never called, because we never call `cache.remove`.
- **Cleanup** runs right after a successful write, under `publishLock`'s write side:
  - For each entry of the `writeAll`, it removes the DirtyIndex entry if its `seq` equals the one in
    the batch. A newer version stays.
  - Matching on `seq` rather than object identity means it doesn't matter whether EhCache hands us
    the same object we passed to `put`.
  - It lowers `pendingBytes`, then wakes everything waiting on the writer (§4.3, §4.7).
- It never throws to EhCache:
  - On a recoverable error, which the backend reports as a `ZeebeDbException`, it retries with
    backoff, up to a limit. This stalls the queue, which turns into back-pressure.
  - On a non-recoverable error, or once retries are exhausted, it sets `store.failure`, logs an
    ERROR and reports the failure right away (next bullet). From then on, commits, reads and
    snapshots fail fast with a non-recoverable exception.
  - The failure also wakes every waiter at once: the snapshot drain, a write-through's drain, and
    commits waiting on `pendingBytes`. Each re-checks `store.failure` and aborts instead of waiting
    for its timeout.
  - After a failure, the writer keeps taking queued batches but doesn't write them. A committer
    blocked on a full queue then returns, sees the failure and throws.
- **Failure reporting and recovery.** A partition with little traffic may not read or commit for a
  long time, so the failure can't wait for the next DB call to surface. Instead:
  - `WriteBehindZeebeDb` implements `HealthMonitorable` from `zeebe-util`.
  - `ZeebeDbPartitionTransitionStep` registers the DB with the partition's component health monitor
    when it is `HealthMonitorable`, the same way `StreamProcessorTransitionStep` registers the
    stream processor.
  - On failure, the writer reports a dead `HealthReport` through
    `FailureListener#onUnrecoverableFailure`.

  The outcome matches a failed state commit today (`ProcessingStateMachine#updateState` →
  `UncommittedStateException` → dead partition). The partition stops, and a broker restart rebuilds
  the runtime from the latest snapshot and replays the log. Losing the unflushed writes is fine
  (fact 1). Recovering in-process instead, by stepping down and reinstalling, would need a new
  recoverable path in `ZeebePartition` (§8).

- After the store is closed, it drops writes and never touches the closed backend.

- **Dropped: skipping tombstones for keys the backend never had.** Coalescing alone still writes a
  tombstone for an insert followed by a delete. RocksDB could tell cheaply that a key was never
  written (`keyMayExist`), but the API has no such call, and checking with `exists` costs a read per
  tombstone. If Phase 6 shows the tombstones matter, the API can gain an optional
  `ColumnFamily#mayExist` (§8).

### 4.7 Snapshot barrier

The barrier is taken before the snapshot store runs the checkpoint, and off the I/O pool.
`prepareSnapshot()` is a new method on `ZeebeDb` in `zeebe-db-api`. Its default completes at once,
so a plain backend needs no change; `WriteBehindZeebeDb` overrides it:

```text
// StateControllerImpl#takeSnapshot, before handing over to the snapshot store
zeebeDb.prepareSnapshot()                    // a future; no actor thread waits for the barrier
  .onComplete(ok -> transientSnapshot.take(dir -> zeebeDb.createSnapshot(dir)))

// prepareSnapshot, on the store's own barrier thread
deadline = now + drainTimeout
if !commitLock.tryLock(until deadline): fail the future
try
  wait until dirtyIndex.isEmpty() or the store fails, until deadline, else fail the future
  failIfStoreFailed()
  pauseWriter()                              // writeAll waits before it touches the backend
finally
  commitLock.unlock()                        // commits resume and fill the queue
complete the future

// createSnapshot, on the snapshot store's actor, as today
try backend.createSnapshot(dir) finally resumeWriter()
```

- **One deadline covers both taking the lock and draining.** A committer can hold `commitLock` while
  it's blocked on a full queue. With a stalled writer, a plain `lock()` would wait forever before
  the drain timeout even started.
- **No thread from the I/O pool waits for the barrier.** The checkpoint callback runs on the
  snapshot store's actor, which is on the broker's I/O-bound pool (2 threads by default). The
  ExporterDirector, which commits exporter positions, runs on the same pool. If the callback waited
  for the barrier while an exporter waited on `commitLock`, both threads would block, and snapshots,
  exports and backups would stall for every partition on the broker. So the barrier runs on the
  store's own thread, and the I/O thread only runs the checkpoint, as it does today.
- **Commits wait only for the drain.** During the checkpoint the writer is paused instead: commits
  keep publishing and filling the queue, but the backend doesn't change, so the snapshot captures
  the drained state. If the snapshot is abandoned after the barrier, the controller resumes the writer.
- **A store failure** wakes the barrier, which fails the snapshot at once (§4.6).
- Holding `commitLock` means no commit is in progress, including one that is still enqueuing, and
  none can start. Read-only transactions don't take it (§4.3), so the StateController's
  exporter-position read doesn't wait behind a stalled committer.
- **The DirtyIndex holds exactly the unflushed versions**, because cleanup removes a version in the
  same critical section that confirms its write. So "drained" simply means `dirtyIndex.isEmpty()`,
  and there's no separate counter to keep in sync.
- After the drain, the backend holds the state after the last committed transaction. That state is
  transaction-consistent and at or past `lowerBoundSnapshotPosition`.
- **Pause:**
  - Commits wait for about `maxWriteDelay` plus the backlog write, and never longer than
    `drainTimeout`. An exporter commit that waits does hold an I/O thread for that long.
  - The checkpoint pauses only the writer, and has no timeout, as today. If it runs long, the
    queue fills and back-pressure reaches the committers.
  - With RocksDB and the WAL disabled, the checkpoint also flushes the memtables.
  - This happens once per snapshot, every 5 minutes by default.
- A write-through (§4.3) can land during the snapshot. It commits as one backend transaction, and
  `createSnapshot` captures one consistent point (§4.1), so the snapshot holds either all of that
  transaction or none of it.

### 4.8 Lifecycle and bypass

- Each `WriteBehindZeebeDb` (one per partition) gets its own `CacheManager` and write-behind thread
  pool.
- `WriteBehindZeebeDb#close()` closes the `CacheManager` first, so the writer stops, and only then
  closes the backend. In the other order, the writer would use a closed backend; with RocksDB, that
  means JNI calls on a freed handle.
- In runtime mode, close drops pending writes; the runtime directory is deleted afterwards anyway.
- Only the runtime DB is wrapped. `WriteBehindZeebeDbFactory#createDb(path)` (used by
  `StateControllerImpl#openDb`) returns a `WriteBehindZeebeDb`. Everything else stays on the plain
  backend, because it either delegates or never sees the wrapping factory:
  - `createDb(path, false)` and `openSnapshotOnlyDb` delegate.
  - `RocksDBSnapshotCopy`, which copies a whole snapshot in one transaction, and the sanity-check
    factory in `RecoveryPartitionManager` are built with `ZeebeRocksDbFactory` directly.

### 4.9 Configuration

Add a `WriteBehindConfiguration` record in `zeebe-db-cache`. The cache isn't RocksDB-specific, so
its broker settings get their own experimental section rather than living in `RocksdbCfg`.

All sizes are per partition, so the broker-wide cost is these values times the partitions a broker
hosts. Byte limits are what bound memory; the entry counts only shape batching.

|        Property        | Default |                                                                                                       Notes                                                                                                        |
|------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `writeBehind.enabled`  | `false` |                                                                                                                                                                                                                    |
| `cacheMaxBytes`        | 64 MB   | Resident cache. EhCache's heap tier sized in `MemoryUnit.MB`, which estimates each entry's size on insert; measure that cost.                                                                                      |
| `maxPendingBytes`      | 32 MB   | Key and value bytes in the DirtyIndex before commits wait (§4.3).                                                                                                                                                  |
| `batchSize`            | 1 000   |                                                                                                                                                                                                                    |
| `maxWriteDelay`        | 100 ms  | Longer gives more coalescing; shorter gives a shorter snapshot pause.                                                                                                                                              |
| `queueSize`            | 10 000  | Pending ops per stripe before back-pressure kicks in.                                                                                                                                                              |
| `concurrencyLevel`     | 1       | One writer thread.                                                                                                                                                                                                 |
| `coalescing`           | `true`  |                                                                                                                                                                                                                    |
| `writeThroughMaxKeys`  | 10 000  | Transactions with more keys commit write-through (§4.3).                                                                                                                                                           |
| `writeThroughMaxBytes` | 16 MB   | Transactions with more key and value bytes commit write-through (§4.3).                                                                                                                                            |
| `drainTimeout`         | 30 s    | Bounds the snapshot barrier (taking the commit lock and draining, not the checkpoint) and a write-through's drain. If exceeded, the snapshot fails and is retried, and a write-through throws a recoverable error. |

### 4.10 Metrics

Registered on the DB meter registry, so they go away when the DB closes:

- cache hits, misses and loads
- committed ops vs. written puts and deletes (coalescing ratio)
- DirtyIndex size (the unflushed keys), `pendingBytes`, and the cache's estimated bytes
- batch size and write latency
- time blocked on back-pressure (shows up as commit latency), time waiting on `pendingBytes`, and
  commit-lock wait time
- optimistic read retries, cached values bypassed because of a write-through, and how long scans
  hold publications back (§4.5)
- write-through commits and their duration (readers wait for it)
- snapshot drain duration (commits wait for it) and checkpoint duration (the writer waits for it)
- writer retries and failures

### 4.11 Memory

With RocksDB, the design moves hot state from RocksDB's off-heap budget to the JVM heap. Per
partition, the heap now holds:

- the cache: up to `cacheMaxBytes`
- the DirtyIndex: only unflushed versions, since cleanup follows every write. It's bounded by
  `maxPendingBytes`, which a commit checks before it publishes. So the one transaction that is
  publishing can push it past the limit by at most its own size. Above `writeThroughMaxBytes` (or
  `writeThroughMaxKeys`), a transaction bypasses the DirtyIndex altogether (§4.3).
- per-iterator copies of DirtyIndex ranges: short-lived and bounded by the DirtyIndex
- the tx buffers. RocksDB's `WriteBatchWithIndex` kept these in native memory. A transaction is
  buffered in full until it commits, whatever path it then takes.

Multiply by the partitions per broker. For a fair benchmark, lower RocksDB's `memoryLimit` by the
same amount, and watch GC.

## 5. Alternatives considered

- **Build the cache on RocksDB's internals** (the plan before Phase 1b): reuse
  `TransactionalColumnFamily` over a `ZeebeTransaction`, a `DbIterator` and `RawDbAccess`. Rejected:
  the cache would depend on `zeebe-db-rocksdb` and couldn't sit in front of another backend.
- **A byte-level backend SPI in `zeebe-db-api`.** Move `ZeebeTransaction`, `DbIterator` and
  `RawDbAccess` into the API, and the typed layer into a shared module that every backend uses.
  Rejected: it only fits backends stored as an ordered key-value table, and the cache would also
  depend on the shared module. Wrapping `ZeebeDb` works with any backend.
- **Cache only the column families that see point access.** Rejected: almost every column family is
  iterated somewhere. Splitting one transaction across two write paths would also still leave the
  snapshot problem.
- **EhCache write-behind alone, without our own overlay.** Incorrect: iteration is stale, the
  `latest.remove` race stays open, and there's no drain signal.
- **Our own ordered write-behind on Caffeine.** Caffeine is already managed in `parent/pom.xml` and
  used by the engine. The writer would apply whole transactions in commit order, so RocksDB is
  always a consistent prefix and snapshots need no commit pause. It adds no new dependency. It needs
  the same overlay and merge-iterator work. Keep it as the comparison point if EhCache's barrier
  pause or queue semantics get in the way.
- **Tune RocksDB instead** (bigger memtables or block cache). This is the baseline to beat: with
  the WAL off, a RocksDB write is already an in-memory memtable insert.

## 6. Implementation plan

### Phase 0: split zb-db into API and RocksDB modules (done)

This has landed (`refactor: split zb-db into api and rocksdb modules`). `zeebe-db` is now a parent of
two modules, with package names unchanged (§4.1):

- **`zeebe-db-api`** (`zeebe/zb-db/api`) holds only interfaces.
- **`zeebe-db-rocksdb`** (`zeebe/zb-db/rocksdb`) holds everything else, including the `Db*` key and
  value types and `ContainsForeignKeys`, whose `DbForeignKey` return type is a concrete record.
- **Consumers** depend on `zeebe-db-rocksdb`, plus `zeebe-db-api` where they use the interfaces.

The cache module adds a third child to `zeebe-db` (Phase 2).

### Phase 1: structural refactor in `zeebe-db-rocksdb`, no behaviour change (done)

This has landed. Since the cache now wraps any `ZeebeDb` through the API (§4.1), it no longer
builds on `ZeebeTransaction`, `DbIterator` or `RawDbAccess`. The first two remain useful structure
inside `zeebe-db-rocksdb`. `RawDbAccess` was added for the cache and has no other user: Phase 1b
removes it.

Where the code settled differently from the steps below:

- `ZeebeTransaction` is the interface; today's class is `WriteBatchTransaction`. `newIterator`
  takes the scanned prefix (the WBWI implementation ignores it).
- The raw-access interface is `RawDbAccess`, reached through `ZeebeTransactionDb#rawAccess()`. Its
  batch is a `RawBatch`, and recoverable errors surface as `ZeebeDbException`.
- The contract tests are abstract `*ContractTest` classes with an abstract `createFactory()`. They
  live in their own module, `zeebe-db-tests` (`zeebe/zb-db/tests`), which runs the one suite against
  every implementation through `RocksDb*Test` subclasses. Phase 2 adds `zeebe-db-cache` as a
  dependency and one subclass per contract class for the cache. Tests that need RocksDB internals
  stay in `zeebe-db-rocksdb`.

1. Add `DbIterator`, including the buffer-filling `key(byte[])`/`value(byte[])` overloads, and a
   `RocksIterator` adapter. Switch `TransactionalColumnFamily`, `RawTransactionalColumnFamily`
   (including the static `forEach*` helpers) and `ForeignKeyChecker` to it. `SnapshotOnlyDb`
   wraps its native iterator in the adapter.
2. Extract the `ZeebeTransaction` interface without the native handles, and make today's
   class its `WriteBatchWithIndex` implementation. `DefaultTransactionContext` and the column
   families use the interface, and `DefaultTransactionContext` gets a public constructor so the
   cache module can build one. `ZeebeDbTransaction` in `zeebe-db-api` doesn't change.
3. Add the raw-access interface on `ZeebeTransactionDb` that the cache module will use (§4.1).
4. Turn the column family and transaction tests into contract tests over a `ZeebeDbFactory`, in a
   separate `zeebe-db-tests` module that depends on every implementation, so that one module runs
   the same suite against each.

- **Exit:** `./mvnw verify -pl zeebe/zb-db/rocksdb,zeebe/zb-db/tests -DskipTests=false -Dquickly` is
  green, and
  `EngineLargeStatePerformanceTest` stays within its tolerance.

### Phase 1b: move the backend-neutral types into the API (`refactor:`)

- **Ask first** (public API change): move the types listed in §4.1 from `zeebe-db-rocksdb` to
  `zeebe-db-api`, with package names unchanged, so no import changes. `zeebe-db-api` gains
  `agrona` and `zeebe-micrometer`. Keep Maven and Gradle in parity.
- Restore `ZeebeDbException` in the API javadoc, which the split had replaced with
  `RuntimeException`.
- State the iteration-order contract (§4.1) in `ColumnFamily`'s javadoc.
- Remove `RawDbAccess` and `ZeebeTransactionDb#rawAccess()`.
- Check the contract tests for RocksDB quirks that a second implementation shouldn't have to copy.
  For example, `DbStringColumnFamilyContractTest` expects "nested prefix iterations are not
  supported", which comes from `ColumnFamilyContext`'s two prefix buffers. Move such cases to the
  RocksDB-only tests.
- **Exit:** `./mvnw install -Dquickly -T1C` is green, `zeebe-db-api` has no dependency on
  `zeebe-db-rocksdb` or RocksDB, and the contract suite is green.

### Phase 2: write-behind core for point ops (`feat:`)

- **Ask first:** add `org.ehcache:ehcache` to the `parent/pom.xml` dependency management, excluding
  `jaxb-runtime`.
- Create `zeebe/zb-db/cache` (artifact `zeebe-db-cache`) as a third child of `zeebe-db`.
  - Its only compile dependencies are `zeebe-db-api` and EhCache. Add a dependency check (for
    example `maven-dependency-plugin:analyze` or a banned-dependency rule) so that a
    `zeebe-db-rocksdb` dependency fails the build.
  - Register it in `zeebe/zb-db/pom.xml` and `settings.gradle.kts` the way `api` and `rocksdb` are,
    and keep Maven and Gradle in parity.
  - Add it as a dependency of `zeebe-db-tests`, with one subclass per contract test that returns
    the cached factory around `ZeebeRocksDbFactory`. Make the cache the candidate of the
    differential test.
- In package `io.camunda.zeebe.db.cache`, add:
  - `CacheKey`, `CachedValue`, and the pass-through `RawKey`/`RawValue`
  - `DirtyIndex`
  - `BackendLoaderWriter`
  - `WriteBehindStore`
  - `WriteBehindConfiguration`
  - `CachedTransactionContext`, `CachedTransaction` and `CachedColumnFamily`, with the consistency
    checks
  - `WriteBehindZeebeDb` and `WriteBehindZeebeDbFactory`, which wraps any `ZeebeDbFactory`. Only
    its `createDb(path)` wraps; everything else delegates (§4.8).
- Commit protocol with `commitLock` and `publishLock`: the read-only fast path, byte back-pressure,
  clearing the buffer only after success, and enqueue-failure handling. Validated point reads with
  a cache-free fallback, a loader that checks the DirtyIndex, and copy-on-read.
- Until Phase 3, iterating on a cached DB throws `UnsupportedOperationException`.
- **Exit:** the point-op contract tests are green against the cached factory, plus the new
  point-read and commit tests from §7.

### Phase 3: merged iteration (`feat:`)

- Merge the overlay into the backend's scans in `CachedColumnFamily` (§4.5), holding
  `publishLock`'s read side from the copy until the backend's first callback.
- **Exit:** the whole contract suite is green against both factories, including
  `ColumnFamilyRandomizedPropertyContractTest` with more tries and the differential test, and the
  iteration stress test passes.

### Phase 4: snapshot barrier, lifecycle and failures (`feat:`)

- Add the snapshot barrier: `ZeebeDb#prepareSnapshot()` takes `commitLock` and drains on the
  store's own thread under one deadline, then pauses the writer for the checkpoint.
  `StateControllerImpl#takeSnapshot` waits for it before handing over to the snapshot store.
- Close the cache before the backend.
- Commit transactions above `writeThroughMaxKeys` or `writeThroughMaxBytes` write-through, with
  the `lastWriteThroughSeq` check on cached reads.
- Add writer retry, the failure latch that wakes every waiter, fail-fast, and draining without
  writing after a failure. Make `WriteBehindZeebeDb` a `HealthMonitorable`.
- **Exit:** the snapshot, write-through and failure tests from §7 are green, plus the
  close-with-pending test.

### Phase 5: wiring and engine-level validation (`feat:` and `test:`)

- **Ask first** (configuration is a public contract): add an experimental broker config section for
  the cache, separate from `RocksdbCfg`, and map it to `WriteBehindConfiguration`.
- In `ZeebePartitionFactory`, wrap the partition's `ZeebeDbFactory` in a
  `WriteBehindZeebeDbFactory` when write-behind is enabled. The broker gets a dependency on
  `zeebe-db-cache`; it's the place that picks both the backend and whether to cache it.
- Register the DB with the partition's component health monitor in
  `ZeebeDbPartitionTransitionStep`.
- Add the metrics.
- Make the engine's test copy of `DefaultZeebeDbFactory` (used by 10 engine test classes) wrap
  its factory when a system property is set, for example `-Dzeebe.db.writeBehind.enabled=true`.
  The engine gets a test dependency on `zeebe-db-cache`, so the `zeebe/engine` tests can run
  against the cached DB locally.
- Add broker ITs:
  - run process instances, take a snapshot, restart, and check the state is intact
  - have a follower install a snapshot that a cached leader took
- **Exit:** the engine suite is green with the toggle on, and the broker restart IT is green.

### Phase 6: benchmark, tune, decide

- Run `EngineLargeStatePerformanceTest` (engine), `LargeStateControllerPerformanceTest` (broker) and
  a realistic load test twice: with write-behind off as the baseline, and on. With it on, lower
  RocksDB memory by the cache budget. Turn on `accessMetrics: FINE` and `enableStatistics` to get
  per-column-family latency and RocksDB stall counters.
- Sweep `maxWriteDelay` (10, 100 and 1000 ms) against `batchSize`, and record the snapshot pause.
- Run a large-value scenario (variables of about 1 MiB) on a broker with several partitions, and
  check that heap use stays within the byte budgets.
- Optional optimizations:
  - an off-heap tier (needs serializers for `CacheKey` and `CachedValue`)
  - if tombstones for never-written keys turn out to matter, an optional `ColumnFamily#mayExist` in
    the API (§4.6)
- **Exit:** a go/no-go write-up. If it's a go, an ADR under `zeebe/docs/adr/`.

## 7. Test plan (new tests, against the cached factory)

These come on top of the contract suite and the differential test in `zeebe-db-tests`, which run
against the cache wrapped around RocksDB.

|                                                                                    Test                                                                                    |                                                      Proves                                                      |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------|
| Read after eviction with a pending write (cache size 1, `maxWriteDelay` 10 s)                                                                                              | Point reads never return a stale backend value                                                                   |
| Overwrite while the previous batch is in flight, plus eviction                                                                                                             | The DirtyIndex covers EhCache's `latest.remove` race                                                             |
| A reader misses the DirtyIndex, a commit publishes, the entry is evicted and EhCache drops its pending op, the reader loads; then drain and read again                     | No stale value stays cached                                                                                      |
| Force the read-lock fallback, stall the writer until the queue is full, block a committer in `cache.put`, then read a clean key in the same map bin; let the writer resume | Reads don't deadlock with back-pressure                                                                          |
| `cache.put` throws during a commit (test seam)                                                                                                                             | The store fails, `commitLock` is released, and snapshots fail fast                                               |
| Mutate an array returned by `get` or by an iterator, then read again and drain                                                                                             | Callers can't change committed or persisted state                                                                |
| Pause a commit mid-publication (test hook); another thread reads two of its keys and scans its range                                                                       | Readers see all of a transaction or none of it                                                                   |
| Two contexts commit the same key concurrently in a loop                                                                                                                    | Point reads, scans and the backend agree after the drain, and the drain completes                                |
| A transaction with more keys than the queue holds, and one above `writeThroughMaxKeys`                                                                                     | Both commit and drain; the write-through leaves no older version cached                                          |
| A write-through while a batch's callback is paused after cleanup, before EhCache drops its pending op; then a cache miss on one of the batch's keys                        | The read returns the write-through's version, not the replaced one                                               |
| A write-through fails with a recoverable backend error, and the processor retries the commit                                                                               | The retry applies the writes; nothing commits on an empty buffer                                                 |
| Commits of large values (about 1 MiB each) beyond `maxPendingBytes`                                                                                                        | Commits wait instead of growing the heap past the budget                                                         |
| Iteration sees pending puts and hides pending deletes (forward, reverse, startAt, prefix, key-only)                                                                        | The merge with the backend's scan (§4.5)                                                                         |
| Writer flushes concurrently while long scans run on another thread (stress)                                                                                                | Scans see one point in time; the DirtyIndex stays within its budget (§4.11)                                      |
| A test backend that pauses before its first scan callback, while another context commits                                                                                   | The merged scan sees that transaction whole or not at all, and publications resume after the first callback      |
| Snapshot after N commits, opened with the plain factory                                                                                                                    | The barrier drains everything                                                                                    |
| Snapshot loop against a concurrent committer, where each tx writes K keys plus a marker                                                                                    | Snapshots are transaction-consistent                                                                             |
| Through `StateControllerImpl#takeTransientSnapshot`, with the writer stalled, a committer blocked on a full queue and an exporter committing                               | The position reads don't wait; the snapshot fails within `drainTimeout`; no I/O-pool thread waits on the barrier |
| The writer fails after a snapshot barrier and a write-through have started waiting on the drain                                                                            | Both abort at once instead of waiting for `drainTimeout`                                                         |
| A backend write fails (test seam in the writer) while no reads or commits happen                                                                                           | The failure is reported at once; commits and reads fail fast after it                                            |
| Close with pending writes; `createDb(path, false)`                                                                                                                         | No crash on close; bulk copy is unaffected                                                                       |

## 8. Risks and open questions

1. **Is zb-db on the critical path at all?** With the WAL off, RocksDB writes already stay in
   memory. There's no up-front baseline any more, so only Phase 6 answers this, after the cache is
   built.
2. **Snapshot pause.** The stream processor and exporter director can't commit for about
   `maxWriteDelay` plus the backlog write, once per snapshot, and never longer than `drainTimeout`.
   During the snapshot only the writer is paused. A snapshot that runs long fills the queue and
   back-pressure reaches the committers; today a slow checkpoint doesn't affect processing at all.
3. **Blocked threads under back-pressure.** A full queue blocks the committer, and also cache reads
   of keys that share its map bin (§3). That's the same class of problem as RocksDB write stalls
   today, but now configurable and measured.
4. **The broker's shared I/O pool.** The ExporterDirector and the snapshot store run on the I/O-bound
   pool (2 threads by default), shared by every partition on the broker. The barrier stays off that
   pool (§4.7), but an exporter commit still waits out a drain on an I/O thread, up to
   `drainTimeout`. Watch export and snapshot latency across partitions while snapshots run.
5. **Heap and GC pressure.** State moves on-heap, every read copies (as today), and each iterator
   copies its DirtyIndex range. The byte budgets (§4.9, §4.11) are per partition, so multiply them by
   the partitions per broker. Large transactions are buffered in full on the heap until they commit;
   RocksDB's `WriteBatchWithIndex` used native memory for this. The migration step, which runs every
   migration in one transaction, is the largest.
6. **A write-through blocks readers** for the length of its backend write (§4.3). It's rare, mostly
   the migration step before processing starts, but it's measured.
7. **Serialized commits.** This is fine while the stream processor does almost all commits. Watch the
   commit-lock wait time for the exporter director.
8. **Writer failure stops the partition**, on followers too. In-process recovery would need a new
   step-down path in `ZeebePartition`. Decide whether the POC needs it.
9. **Going through the typed API costs copies.** The cache serializes a key or value once, and the
   pass-through column family copies those bytes again into the backend's own buffers. RocksDB's
   column families used to serialize straight into theirs. Measure it in Phase 6.
10. **Scans hold publications back** until the backend's first callback (§4.5). That's one seek with
    RocksDB, but a query round trip with an RDBMS. Watch it before an RDBMS backend is used.
11. **The backend contracts the cache relies on** (§4.1): byte-ordered iteration, atomic
    transactions, a consistent `createSnapshot`, and fixing a scan's view before its first callback.
    An RDBMS backend has to meet all four. The contract tests check the first three.
12. **Approvals needed:** the API change in Phase 1b, the new dependency and the experimental config
    keys (all "ask first" in `AGENTS.md`).

