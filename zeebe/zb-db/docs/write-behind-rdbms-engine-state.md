# Write-behind engine state on a relational database

Status: workshop branch `cs/write-behind-generic-core`. Date: 2026-11-12.
This document describes what the code does now. It also lists the open problems.
Measurements come from load tests on the benchmark cluster. A claim without a measurement is marked "not measured".

## 1. Goal

The engine keeps its state in a relational database (PostgreSQL) instead of RocksDB.
A synchronous write per transaction is too slow. The stream processor would wait for the database on every command.

Write-behind ends that wait. A commit goes to memory first. A background writer, the flusher, writes the data to the database later.
The processor reads its own writes from memory. It reads older data from a cache or from the database.

The generic part lives in `zeebe/zb-db/writebehind`. The relational part lives in `zeebe/engine-state-rdbms`.

## 2. Components

| Component | Module | Job |
|---|---|---|
| `EntitySession` | engine-state-rdbms | Stages the writes of one transaction. Merges them with database results. |
| `WriteBehindStore<K, V>` | zb-db/writebehind | Holds the unflushed commits and the cache. Serves reads. |
| `AsyncFlusher` | zb-db/writebehind | One thread. Writes sealed batches to the database in commit order. |
| `JdbcBackend` | engine-state-rdbms | Reads and writes rows with MyBatis. Answers "never written" without a query. |
| `GroupCache` | engine-state-rdbms | Remembers which rows a scope has, for group queries. |
| `VariableKeyFilter` | engine-state-rdbms | Bloom filter of variables written in this run. |

A key is an `EntityKey(type, key)`. Keys sort by type, then by the key itself. A value is an immutable row.

## 3. Write path

1. The stream processor stages its changes in the `EntitySession`. Nothing reaches the store yet.
2. On commit, the session calls `WriteBehindStore.commit(keys, values)`.
3. The store takes a sequence number. It writes a new `Version` for each key into the **active batch**. A `null` value is a delete.
4. The store queues the transaction for the flusher. The commit returns.
5. The flusher waits for the flush delay. It then **seals** the active batch and starts a new one.
6. The flusher writes the sealed batch to the database in one JDBC batch.
7. The flusher puts the flushed values into the cache. It then drops the sealed batch.

Commits wait only when the unflushed data is larger than `maxPendingBytes`. This is the only back pressure.

### Flush delay

`flushDelay` is how long the flusher waits after the oldest unflushed commit.
State that a later commit deletes in this time never reaches the database.
This matters for short-lived state such as variables of a process instance that ends quickly.
A flush starts earlier in three cases: the unflushed data reaches `flushThresholdBytes`, the store takes a snapshot, or the store closes.

## 4. Read path

A point read checks these places in order:

1. The active batch.
2. The sealed batch, if a flush is running.
3. The cache.
4. The database. The store asks `backend.knownAbsent(key)` first. If the answer is true, the read returns "absent" without a query.

A batch only leaves memory after the database holds its data and the cache has it.
So a key that is in no batch has its latest value in the cache or in the database.

## 5. Interesting solutions

### 5.1 Generational dirty index

The first design copied all unflushed writes for every read. At `flushDelay=30s` this was 24.5% of the CPU.
The current design has two sorted maps. This is the `Batches` record: `(active, sealed)`.

- Commits write to `active` under a short lock (`publishLock`).
- The flusher seals under the same lock. It also drains its queue in the same step. This keeps the queue and the batches consistent.
- A reader takes a **snapshot**. A snapshot is only the two map references and the commit sequence. It copies nothing and takes no lock.
- After the database write and the cache update, the flusher sets `sealed = null`. This frees the whole batch in one step.

### 5.2 Version chains hide partial commits

A reader must not see half of a commit. The reader's snapshot has a sequence number.
Each `Version` keeps a `previous` link to the older version of the same key.
`Version.asOf(seq)` follows the links until it finds a version that is not newer than the snapshot.
The reader reads the sequence number before it reads the batch references.
A test with a reader and a committer running at the same time caught the torn reads that led to this design. A reader saw 5 of 20 rows of one commit.

### 5.3 Only the flusher writes the cache

The processing thread never writes the cache on commit. Only the flusher does, with values that the database already holds.
The flusher is one thread and flushes in commit order. Its puts only move a key forward.
So the cache never holds a value that is newer than the database. It also never needs a copy of a value that a batch already holds.

A reader that misses the cache loads from the database and puts the value in the cache.
A flush might run at the same time and cache a newer value. To prevent an old value from overwriting it, the store keeps a `flushEpoch`.
The epoch is odd while a flush runs. A reader skips its cache put when the epoch is odd or changed during the load.

### 5.4 Evict on delete

The flusher used to put a tombstone in the cache for every deleted key.
Every finished process instance deletes its rows and variables. The cache then filled with tombstones that nobody reads.
In one load test the cache held about 1.5 million entries for about 16,000 live variable rows. This used about 450 MB of a 1 GB heap.
Now the flusher evicts a deleted key. A later read finds nothing in the batches or the cache and asks the database once.

The backend also weighed each entry at 64 bytes. The real cost of a key, its name and the cache node is about 200 bytes.
The weight is now 200 bytes plus the row estimate. A budget of 64 MB now means about 64 MB.

### 5.5 Group queries read one group

Variables and event triggers are grouped by scope. A group query needs the unflushed rows of one scope.
The first version walked the unflushed rows of the whole type for every group query.
This cost about 7% of the CPU and 15% of the allocated bytes in the profile.

Keys of a grouped type sort by group first, so the rows of one group are next to each other.
`EntityKey` now has range bounds for a group. The session reads only that range.
The compare method also checks for bounds once instead of ranking both keys up to three times.

### 5.6 Answer "never written" without the database

On job completion the engine looks up each variable before it merges a document into the scope.
99.95% of these lookups found nothing. This was about 900 database reads per second on the processing thread.
The cache cannot help because each key is new. Nobody asks for the same key twice.

Engine keys only grow. At start, the backend reads the highest scope key that has a variable in the database.
A scope above that key is new. A variable of a new scope can only exist if this run wrote it.
The backend records each variable that it flushes in a Bloom filter. It records the variable before it writes the row.
A lookup of a variable of a new scope that the filter has not seen is answered as "absent". The filter has no false negatives.
A false positive costs one database read, as before.

The filter uses 4 MB for each partition. It allocates the memory on the first variable write.
With 3 hash functions, the false positive rate reaches about 20% after about 10 million variables.

### 5.7 No session setup statements

Each transaction paid two extra statements: one to set the isolation level and one to switch auto-commit on when the session closed.
This was about 2.75% of the CPU.
The engine state connection pool now delivers connections with auto-commit off and the isolation level `READ_COMMITTED`.
The sessions open without a level and do not reset auto-commit on close (`skipSetAutoCommitOnClose`).

## 6. Configuration

| Property | Default | Meaning |
|---|---|---|
| `zeebe.engine.rdbms.writeBehind` | `false` | Turns write-behind on. |
| `zeebe.engine.rdbms.writeBehind.flushDelay` | `0` | Wait before a flush. Runs used `10s` and `30s`. |
| `camunda.data.primary-storage.type` | `rocksdb` | Must be `rdbms`. |
| `camunda.data.primary-storage.rdbms.pool-size` | `4` | Connections of the engine state pool for the broker. |

Fixed in code (`WriteBehindConfiguration.defaults()`) for each partition: `maxPendingBytes` 32 MB, `cacheMaxBytes` 64 MB, `flushThresholdBytes` half of `maxPendingBytes`, 10 write retries, 30 s flush timeout.
With 3 partitions on a broker, the cache budget is 3 × 64 MB. The pending data adds up to 3 × 32 MB.

The relational engine state supports replication factor 1 only. Each broker owns its partitions alone.
`defaults.yaml` says the engine deletes the rows of a partition when it starts. Check this text before you rely on a restart.

## 7. Metrics

The store exports these meters with the prefix `zeebe.writebehind.`:

- `pending.transactions`, `pending.bytes`, `pending.age`: what the flusher has not written yet.
- `cache.entries`: size of the cache.
- Counters for each group: storage gets and found gets, cache hits, flushed puts and deletes, skipped deletes.
- `keyIndexAbsent`: reads that returned "absent" without the database. This includes reads that `knownAbsent` answered.

Hikari exports `hikaricp_*` for both pools: `camunda-engine-state` and `camunda-rdbms-default` (exporter).

## 8. Current challenges

### 8.1 Memory

- The tombstone leak (section 5.4) is fixed in the code and in the image `...-i-dab53059`. **Not measured yet** on that image: the live heap after a Full GC, and the class histogram.
- With a 512 MB heap (25% of 2 GiB) the broker ran 178 Full GCs in 13 minutes, each about 420 ms. The live set grew from 335 MB to 474 MB in 5 minutes.
- A 1 GB heap had RSS at 1.75 GB of a 2 GiB limit. That is close to an out-of-memory kill. The current runs use a 4 GiB limit and a 2 GB heap.
- The weights of cache entries are estimates. Entries with long variable names or large rows may cost more than the estimate. Not measured.

### 8.2 Tail latency

- In an earlier run, the p50 of job complete was good. The p99 was worse than without the cache.
- Candidates: Full GCs, synchronous database reads on the processing thread, the iteration of unflushed rows, and database stalls.
- The first three have fixes in the code. The effect on the p99 is **not measured**.
- There is no comparison yet of throughput with and without write-behind on the same cluster setup.

### 8.3 Database

- One PostgreSQL serves all partitions of all brokers. With 9 or 12 partitions, the flush load on this database grows. The effect is not measured.
- The benchmark database was shared with a long-running read workload (`k6-data-read`). It was throttled during 7.7% of the observed periods.
- The engine state pool is 12 connections for each broker. At most 3 were active in the profile, with no waits. A pool of 4 to 6 may be enough.
- The exporter pool (`camunda-rdbms-default`) opens and closes connections. The reasons are a low minimum idle and `maxLifetime` recycling. Not changed.

### 8.4 Reads that still reach the database

- The filter covers variables only. Lookups of other types that find nothing still cost a database read. The amount is not measured.
- A flushed value that the cache evicted costs one read when it is needed again.
- The filter's false positive rate grows during a long run (section 5.6).

### 8.5 Remaining cost of unflushed reads

- A group query now reads one range. Queries that read a whole type (`effective(type)` in `EntitySession`) still iterate all unflushed rows of the type.
- Each entry of a view allocates wrapper objects. Not changed.

### 8.6 Threads and CPU

- A run with 12 partitions on 3 brokers uses 8 processing threads and 4 IO threads on a pod with a 3 CPU limit. The effect of this oversubscription is not measured.

### 8.7 Start-up and topology

- A broker that starts before PostgreSQL is ready fails in Liquibase and restarts. This happened twice in one run. The pod recovered by itself.
- The relational engine state supports one replica of each partition only.
- Tests that create many stores used much heap when the Bloom filter allocated 8 MB at once. The filter now allocates on first write, and its size is 4 MB.

## 9. How to check the state of a running broker

- GC log: `/usr/local/camunda/data/gc.log` in the pod. Look for `Pause Full` and the heap after each one.
- Class histogram: `kubectl exec camunda-0 -- sh -c 'jattach 1 jcmd "GC.class_histogram"'`. Many `Version`, `EntityKey` and `VariableKey` objects with few `VariableRow` objects point to dead entries in the cache.
- Native memory: `jattach 1 jcmd "VM.native_memory summary scale=MB"`. The image starts with `-XX:NativeMemoryTracking=summary`.
- Profiles: the workflow `profile-load-test.yml` makes CPU and alloc flamegraphs.

## 10. Commits on the branch

| Commit | Change |
|---|---|
| `bbab50eb80e` | Serve flushed values from the cache. Tests. |
| `80a6f5c8138` | Configure the flush delay. |
| `69b5a60e772` | Export the meters. |
| `438d6ae44de` | Batch design: no copy of unflushed writes for reads. |
| `3bc7bca9440` | Read only one group's unflushed rows for group queries. |
| `3df64e663ed` | No auto-commit and isolation statements for each session. |
| `66947b284ca` | Skip the database lookup of variables that were never written. |
| `dab5305938d` | Evict deleted keys from the cache. |

## 11. Next steps

1. Check the heap and the histogram of the image with the eviction fix.
2. Compare p99, throughput and CPU with and without write-behind on the same cluster setup.
3. Find the p99 cause with one time axis for `pending.bytes`, flush time, GC pauses and the p99.
4. Decide the pool size of the engine state and the exporter pool settings.
5. Measure database reads that find nothing for types other than variables.
