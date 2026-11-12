# RDBMS storage for zb-db

**Status**: Proposed (8.11). The decisions and their reasons are in
[ADR 0015](../../docs/adr/0015-811-shared-rdbms-partition-state.md). This document describes how to
build them.

## 1. Goal and scope

Store a partition's runtime state in a relational database, behind the
[write-behind cache](write-behind-cache-design.md). There's one shared state per partition, and
only the leader reads and writes it.

This document covers:
- **Part A, zb-db**: the new `zeebe-db-rdbms` module and the changes the cache needs. This is built
first.
- **Part B, broker**: wiring, leader and follower roles, snapshots and backups. This is only
outlined here and will be designed in detail after Part A.

Out of scope: vendors other than H2 and PostgreSQL, partition scaling, zdb.

## 2. How it fits

```
StreamProcessor / ExporterDirector / ...        (unchanged, typed ZeebeDb API)
        │
WriteBehindZeebeDb (zeebe-db-cache)             reads: cache, then storage on a miss
        │  writer: committed transactions,      writes: only after the Raft commit (commit gate)
        │  in commit order, in groups
RdbmsZeebeDb (zeebe-db-rdbms)                   one shared state per partition, fenced by term
        │  MyBatis statements, our own connections
DataSource ── H2 / PostgreSQL                   schema managed by Liquibase
```

The RDBMS `ZeebeDb` meets the full `ZeebeDb` contract on its own, so the same contract tests run
against it with and without the cache. In production it's only used behind the cache.

## 3. Module

`zeebe/zb-db/rdbms`, artifact `zeebe-db-rdbms`, package `io.camunda.zeebe.db.rdbms`. It's registered
in `zeebe/zb-db/pom.xml`, `settings.gradle.kts` and its own `build.gradle.kts`, like
`zeebe-db-cache`.

| Package  |                       Contents                        |
|----------|-------------------------------------------------------|
| (root)   | `RdbmsZeebeDbFactory` and `RdbmsStorage`, both public |
| `schema` | `StateSchemaManager`: runs the Liquibase changelog    |

Everything else sits in the root package too, so that it can stay package-private: the DB, its
transactions and write buffer, the column family, key ranges, `StateMapper` and its row types, and
later the state rows, fencing and the marker.

Dependencies (all versions already managed in `parent/pom.xml`): `zeebe-db-api`, `liquibase-core`
and `mybatis` at compile scope; `h2`, `postgresql` and `testcontainers-postgresql` at test scope. No
Spring and no connection pool: the caller provides a `DataSource`. In the broker that will be a
HikariCP pool (Part B).

Resources:
- `db/changelog/zeebe-state/changelog-master.xml` and `changesets/8.11.0.xml`
- `io/camunda/zeebe/db/rdbms/StateMapper.xml`, next to the interface, so `addMapper` loads it. It
must not live under `mapper/`: the history RDBMS loads every `classpath*:mapper/*.xml`.

## 4. Schema

All names start with the configured prefix (`${prefix}`), as in the history schema, so several
clusters can share a database schema.

**`${prefix}ZEEBE_STATE_PARTITION`**: one row per shared state.

|      Column       |      Type      |                         Notes                          |
|-------------------|----------------|--------------------------------------------------------|
| `STATE_ID`        | `BIGINT`       | primary key, used by every state row                   |
| `PARTITION_GROUP` | `VARCHAR(255)` | unique together with `PARTITION_ID`                    |
| `PARTITION_ID`    | `INT`          |                                                        |
| `OWNER_TERM`      | `BIGINT`       | term of the leader that owns the state (fencing, §7.5) |

**`${prefix}ZEEBE_STATE`**: all column families in one table.

|     Column      |     H2      | PostgreSQL |                     Notes                      |
|-----------------|-------------|------------|------------------------------------------------|
| `STATE_ID`      | `BIGINT`    | `BIGINT`   |                                                |
| `COLUMN_FAMILY` | `INT`       | `INT`      | `EnumValue#getValue()` of the column family    |
| `STATE_KEY`     | `VARBINARY` | `BYTEA`    | serialized key, without the column family part |
| `STATE_VALUE`   | `VARBINARY` | `BYTEA`    | `NOT NULL`, may be empty                       |

Primary key `(STATE_ID, COLUMN_FAMILY, STATE_KEY)`; there are no other indexes. Both databases
compare binary values byte by byte as unsigned numbers, so the primary key order within a column
family is the same as RocksDB's. The contract tests check this.

The changelog follows `db/rdbms-schema`: `${prefix}` in names, types defined per database with
`<property dbms="…">`, and `preConditions onFail="MARK_RAN"` on each change set.

## 5. Schema migration

`StateSchemaManager` runs the changelog with Liquibase's `Liquibase` class, the same way
`SpringLiquibase` does, but without Spring. Its tracking tables are
`${prefix}ZEEBE_STATE_DATABASECHANGELOG` and `${prefix}ZEEBE_STATE_DATABASECHANGELOGLOCK`, so they
never mix with the history schema's.

Like the history, it sets a Liquibase context per prefix. Liquibase remembers for the whole JVM
which changelogs are up to date per database URL, but not per table, so without it a prefix that's
behind could be skipped. It also turns off Liquibase's usage analytics, which otherwise loads its
settings from Liquibase's servers.

`RdbmsStorage` runs it once, when it's created. One `RdbmsStorage` exists per data source and is
shared by all partitions of a broker.

Liquibase's lock service is JVM-wide (#61009, see the comment in `LiquibaseSchemaManager`). Our
migrations take a lock of their own so they never run at the same time. The history migration runs
at startup too, so the two can still overlap. Sharing one lock with `LiquibaseSchemaManager` would
change `db/rdbms`, so it's an open question in the ADR.

## 6. MyBatis

`RdbmsStorage` builds a `SqlSessionFactory` from a plain `Configuration`:
- `Environment` with a `ManagedTransactionFactory` that doesn't close connections. We open, commit,
roll back and close connections ourselves (§7.2), and every `SqlSession` runs on our connection.
- MyBatis's `VendorDatabaseIdProvider` to pick statements per database, mapping product names to
ids as `RdbmsVendorIdProvider` does for history. Also `cacheEnabled = false`, `localCacheScope =
STATEMENT`, `jdbcTypeForNull = NULL`, and the `prefix` variable.

Statements in `StateMapper.xml`:

|            Statement             |                                 SQL                                  |
|----------------------------------|----------------------------------------------------------------------|
| `get`                            | `SELECT STATE_VALUE … WHERE STATE_ID, COLUMN_FAMILY, STATE_KEY = ?`  |
| `exists`                         | same, `SELECT 1`                                                     |
| `upsert` (batched)               | PG `INSERT … ON CONFLICT … DO UPDATE`; H2 `MERGE INTO … KEY (…)`     |
| `delete` (batched)               | `DELETE … WHERE` primary key                                         |
| `scan`, `scanReverse`            | key range, `ORDER BY STATE_KEY [DESC]`, streamed with a fetch size   |
| `count`, `countRange`, `isEmpty` | `COUNT(*)` / `FETCH FIRST 1 ROWS ONLY` on the column family or range |
| `findStateId`, `maxStateId`      | the partition's state id; `COALESCE(MAX(STATE_ID), 0)` (§7.1)        |
| `createState`                    | `INSERT` of a state row with the claiming term (§7.1)                |
| `claim`, `lockOwnerTerm`         | claim the state, and read and lock the owner term (§7.5)             |

Plain JDBC would mean fewer layers on the hot path, but we would rebuild the per-vendor statement
selection that MyBatis gives us, as the history RDBMS uses. We'll compare in the benchmark.

## 7. The `ZeebeDb` implementation

### 7.1 Factory and attaching to a state

`RdbmsZeebeDbFactory` is created per partition with the shared `RdbmsStorage`, the partition's
`StateIdentity` (group, partition id), a term supplier (`LongSupplier`), the consistency check
settings and a meter registry supplier.

- `createDb(dir)` reads the term once, then attaches in one short transaction of its own: it finds
  or creates the partition's state row and claims it with the term (§7.5). The directory is only
  used for the marker (§7.6):
  - **Without a marker** (a partition that starts without a snapshot), it uses the partition's
    state, or creates one.
  - **With a marker**, the marker must name this partition, and the database must hold the state it
    names as the partition's state. It never creates a state then: the log was compacted up to the
    snapshot, so an empty state could not be rebuilt from it.
- A new state id is `MAX(STATE_ID) + 1`. If another partition takes the same id at the same time,
  the insert fails on the primary key and the attach starts over, up to 1,000 times. Every failure
  means another partition created its state, so the number of partitions bounds the attempts. This
  works the same on every database, unlike sequences.
- `openSnapshotOnlyDb(dir)` reads the marker and checks that it names this partition. It doesn't
  touch the database: its `createSnapshot` copies the marker and everything else throws
  `UnsupportedOperationException`. That's all `StateControllerImpl`'s recovery needs (open snapshot,
  copy it to the runtime directory, open the runtime DB).
- `createDb(dir, avoidFlush)` is the same as `createDb(dir)`: flushing is a RocksDB setting.

### 7.2 Transactions

- A transaction borrows a connection from the data source at its first statement, a read or the
  flush at commit, so a transaction that only writes holds no connection until it commits. It turns
  auto-commit off and keeps the connection until it commits or rolls back.
- We don't set the isolation level per connection: `READ COMMITTED` is the default on PostgreSQL
  and H2, and the broker's pool will set it (Part B). That matches `ZeebeDb` today:
  `NoSnapshotIsolationTest` expects reads to see other transactions' commits.
- `runInTransaction` inside an open transaction runs in it. Column family calls outside a
  transaction run in a short transaction of their own, as with RocksDB.
- **Write buffer.** Writes go into a per-transaction map keyed by (column family, key), where the
  last write wins. It's written as one batch of deletes and one batch of upserts before a scan, a
  count or an `isEmpty` in the same transaction, and at commit. Point reads check the buffer first,
  so the transaction always sees its own writes.
- `close()` rolls back open transactions and returns their connections. An error while rolling back
  is logged, not thrown: the database discards the work either way.

### 7.3 Point operations

`get`, `exists`, `upsert`, `deleteIfExists` map directly onto the statements above. `insert`,
`update`, `deleteExisting` and the foreign key checks read first in the same transaction and throw
the same exceptions as RocksDB. Consistency checks follow `ConsistencyChecksSettings`, like RocksDB.
The cache only calls `get`, `upsert`, `deleteIfExists` and scans on its storage, so the checks cost
nothing in production.

### 7.4 Scans

- One streamed query per scan, read with a MyBatis `ResultHandler` that stops when the visitor
  returns `false`. The database fixes the query's view when it starts, before the first row
  reaches us. That's what the cache's scan needs (cache design §4.5).
- Forward with a prefix: `STATE_KEY >= start AND STATE_KEY < next(prefix)`, where `next(prefix)` is
  the prefix with its last byte below `0xFF` incremented and the bytes after it dropped. If the
  prefix is all `0xFF`, there's no upper bound. The scan stops at the first key without the prefix,
  as RocksDB does when `start` is before the prefix.
- Reverse: `STATE_KEY >= prefix AND STATE_KEY < start + 0x00 ORDER BY STATE_KEY DESC`, which is
  `STATE_KEY <= start`, with the same stop rule. Today only `whileTrueReverse` uses it, without a
  prefix.
- **Difference from RocksDB:** a scan doesn't see writes its own transaction makes while it's
  running. The cache never writes to its storage during a scan, and no contract test relies on it.
- `RdbmsStringColumnFamilyTest` skips `shouldThrowExceptionOnMultipleNestedWhileEqualPrefix`: it
  expects RocksDB's limit of two nested prefix scans, which a database doesn't have.
- Nested scans run as separate queries on the same connection; both databases allow several open
  result sets in one transaction.

### 7.5 Fencing

- Claim, when attaching: `UPDATE … SET OWNER_TERM = :term WHERE STATE_ID = :id AND OWNER_TERM <=
  :term`. No row updated means a newer leader owns the state: attaching throws
  `StateFencedException`.
- Check, before the first writes of every transaction reach the database: `SELECT OWNER_TERM …
  WHERE STATE_ID = :id FOR UPDATE`. This locks the row until the transaction ends, so a claim waits
  for a write in progress. A missing row or another term throws `StateFencedException`, which is not
  recoverable: the cache's writer doesn't retry it.
  - It's a `SELECT` instead of an `UPDATE` because writes are sent as a JDBC batch, and MyBatis'
    batch executor doesn't report update counts. The select takes the same row lock and returns the
    owner term, which also makes the error message clearer.
  - Every write transaction of a partition locks its partition row, so they run one after the
    other. That's fine: the cache has a single writer.
- Read-only transactions don't check. A fenced leader can still read, but can't write.
- The equal term in the claim lets a partition reopen its DB within the same term, for example after
  a failed transition. Within one broker the old DB is always closed before the new one opens.

### 7.6 Snapshots

`createSnapshot(dir)` creates `dir` and writes `zeebe-state.properties` in it, with the keys
`version` (now `1`), `stateId`, `partitionGroup` and `partitionId`. Reading a marker with a missing
key, a value that isn't a number or another version fails. It reads nothing from the state and
takes no lock. Behind the cache,
`WriteBehindZeebeDb#createSnapshot` first waits until the writer has caught up, so the marker is only
written once the state contains everything the snapshot stands for.

### 7.7 Errors, metrics, properties

- `SQLTransientException`, connection failures and lock timeouts become `ZeebeDbException`, which
  is recoverable; the cache's writer already retries those. Other `SQLException`s are not.
- Metrics under `zeebe.db.rdbms.*`: latency of point reads, scans, flushes and commits, rows
  written, and fenced writes. They come with the benchmark (Part B); until then the DB only closes
  the meter registry it's given.
- `getProperty` returns empty; `exportMetrics` does nothing.

## 8. Changes in the cache

**Commit gate.** `WriteBehindZeebeDbFactory` takes an optional `CommitGate`:

```java
interface CommitGate {
  long positionAtCommit();                         // the log's last written position
  void onCommittedPosition(LongConsumer listener); // Raft commit position updates
}
```

- When a cache transaction commits, `CommittedTransaction` stores `positionAtCommit()`.
- The writer only takes transactions whose stored position is committed. Group size and delay
  limits apply to those transactions only.
- Without a gate (RocksDB today) nothing changes.
- `createSnapshot` and close wait for the commit too, and give up when the DB closes, so a partition
  that can't commit doesn't block forever.

**Fenced writes.** A `StateFencedException` stops the writer and fails the partition, like any
other write failure. On close, the writer writes what it may, and stops quietly when it's fenced:
the new leader replays the rest from the log.

## 9. Broker changes (Part B, outline)

1. **Configuration and wiring.** `camunda.data.primary-storage.rdbms.*` (data source, prefix, pool),
   one `RdbmsStorage` per data source, `RdbmsZeebeDbFactory` per partition in
   `ZeebePartitionFactory#stateDbFactory`. The write-behind cache is required.
2. **Term.** `ZeebeDbPartitionTransitionStep` passes its term to the factory's term supplier before
   `StateController.recover()`.
3. **Leader.** Attaches, replays from the shared processed position, and feeds the commit gate from
   the log stream's written and committed positions.
4. **Followers and candidates.** No `ZeebeDb`, no replay, no passive exporter state. A snapshot
   director that writes markers from the processed and exported positions read from the shared
   state, limited to their own log. Each transition step that uses the DB today needs a decision.
5. **Recovery check.** The shared processed position must be at or above the marker's and within
   the log (ADR D5).
6. **RocksDB-only paths.** Skip the recovery sanity check for markers; reject partition scaling and
   unsupported restores clearly.
7. **Backups.** Restore with a database restore to the checkpoint time, then reset the owner terms
   (ADR D6).

## 10. Testing

- **`zeebe-db-rdbms` unit tests (H2):** the migration runs once and is repeatable; `next(prefix)`
  edge cases; reading and writing the marker; state ids allocated concurrently; fencing with two
  factories on one state (claim, fenced write, claim waiting for a write in progress). Waiting for
  a row lock is tested with a short H2 `LOCK_TIMEOUT`: the waiting side fails with
  `ZeebeDbException`, so the tests need no threads.
- **Contract suites in `zeebe/zb-db/tests`:** an `RdbmsTestFactory` creating a fresh state per DB
  in one shared in-memory H2 database, and `Rdbms*Test` subclasses of every contract suite, the same
  way `RocksDb*Test` and `WriteBehind*Test` do. Then `WriteBehindOverRdbms*Test` and
  `RdbmsVsRocksDbDifferentialTest`.
- **PostgreSQL:** the same suites as `*IT` against Testcontainers.
- **Cache:** the writer holds back transactions until their position is committed, `createSnapshot`
  waits for the commit, and close gives up when the DB closes.

Builds: `./mvnw install -pl zeebe/zb-db/rdbms -am -Dquickly -T1C`, then
`./mvnw verify -pl zeebe/zb-db/rdbms,zeebe/zb-db/tests -DskipTests=false -Dquickly`. The
differential and property suites are tagged `randomized`; they only run with
`-Pinclude-random-tests`.

## 11. Risks

1. **Key length.** PostgreSQL limits a B-tree entry to about 2.7 KB. Keys with long user strings may
   exceed it. Measure the largest keys the engine writes before choosing a fix.
2. **Failover time.** The new leader replays the old leader's unwritten backlog with an empty cache.
   The cache's pending limit bounds it; the benchmark must measure it.
3. **Scan latency.** A scan holds back cache publications until the first row arrives (cache design
   §8 risk 9). That's a query round trip here.
4. **Commit gate and snapshots.** A partition that can't reach the Raft commit can't write, so its
   cache fills up and it can't snapshot. That's the same as today's commit wait in
   `AsyncSnapshotDirector`, but it now also blocks the writer.
5. **Liquibase lock overlap** with the history migration in the same JVM (§5).
6. **Binary ordering on other vendors.** SQL Server compares trailing zeros as equal; MySQL and
   Oracle limit key lengths. Each new vendor needs the contract tests.

## 12. Implementation plan

Each step is one commit, reviewed before the next one. Part A ends with a PR; Part B is planned in
detail afterwards.

**Part A: zb-db**
1. **docs:** this design and ADR 0015.
2. **build:** module skeleton, Liquibase changelog and `StateSchemaManager`, MyBatis setup, tests on
H2.
3. **feat:** transactions, write buffer, point operations and scans; every contract suite and the
differential test against RocksDB on H2. One step, because the point and transaction suites
already need scans, `count` and `isEmpty`.
4. **feat:** state rows, fencing by term, marker snapshots and the snapshot-only DB.
5. **test:** the write-behind cache over the RDBMS: contract suites and differential test.
6. **feat:** the commit gate in the cache's writer.
7. **test:** all suites on PostgreSQL with Testcontainers.

**Part B: broker** (outline in §9)
8. Configuration and wiring.
9. Term, leader attach and replay, commit gate from the log stream.
10. Followers without a `ZeebeDb`, marker snapshots on followers.
11. Recovery check and RocksDB-only paths.
12. Backup and restore.
13. Benchmark against RocksDB, with and without the cache, then decide.
