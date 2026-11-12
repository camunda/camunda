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
- **Part B, broker**: configuration, wiring, leader and follower roles, and snapshots (§9).

Out of scope: vendors other than H2 and PostgreSQL, partition scaling, zdb, and for now backups
and restore (ADR D6 stays the plan for later).

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
Spring and no connection pool: the caller provides a `DataSource`. In the broker that's a HikariCP
pool (§9.2).

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
- `openReadOnlyDb()` finds the partition's state without claiming it, so the leader keeps writing.
  It is empty if the partition has no state yet. Reads see what the leader has committed, and never
  wait for its writes in progress. Writes throw `UnsupportedOperationException` right away, before
  they reach the database. Its `createSnapshot` writes a marker naming the state, like the leader's.
  Followers use it to take snapshots (§9.5).

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

**Commit gate.** `WriteBehindZeebeDbFactory` optionally takes a supplier of `CommitGate`s, and
asks it for a new gate for each DB it opens, since the broker opens one DB per leader term:

```java
public interface CommitGate {
  long lastWrittenPosition();  // the log's last written position
  long committedPosition();    // the log's committed position
  boolean stopped();           // this replica no longer leads; never goes back to false
  void addCommitListener(Runnable listener);    // called after the committed position moves,
                                                // and once when the gate stops
  void removeCommitListener(Runnable listener);
}
```

- When a cache transaction commits, `CommittedTransaction` stores `lastWrittenPosition()`.
- The writer holds a transaction back until `committedPosition()` reaches its position, and keeps
  the commit order: a transaction that waits also holds back the ones after it. The batch size
  counts only released transactions. The write delay runs from the cache commit, so a transaction
  held back longer than the delay is written as soon as it's released.
- Held-back transactions count towards `maxPendingBytes`, so when the log stops committing, cache
  commits eventually wait too.
- `createSnapshot` waits for the commit as well, up to `snapshotCatchUpTimeout`.
- The committed position may only move while this replica is the leader that opened the DB. A
  follower hears committed positions from the new leader, whose records may sit where this
  replica's uncommitted records were.
- Once the gate stops, the cache stops waiting for the log: a commit waiting for
  `maxPendingBytes` goes through, and a snapshot waiting for the log fails. The held-back
  transactions stay held back until the DB closes and drops them. A commit must not fail instead:
  the stream processor retries a failed commit, and once retries run out the partition dies. A
  snapshot waiting the full timeout would block the snapshot store's actor for that long.
- Without a gate (RocksDB today) nothing changes.

**Fenced writes.** A `StateFencedException` stops the writer and fails the partition, like any
other write failure.

**Close.** Closing drops the transactions the writer hasn't written yet, held back or not; the new
leader replays them from the log. Writing them on close could shorten the new leader's replay, but
it delays the step-down; the benchmark decides (§11 risk 2, §12 step 12).

## 9. Broker changes (Part B)

One replica comes first, so each step leaves a broker that works; followers come after (§12).

### 9.1 Configuration

- `camunda.data.primary-storage.type`: `rocksdb` (default) or `rdbms`.
- `camunda.data.primary-storage.rdbms.*`: `url`, `username`, `password`, `prefix` and
  `connection-pool.*`. These are separate from the secondary storage's settings: the partition
  state may live in another database, and its pool is sized for the partitions' writers.
- Both map onto `DataCfg` in `BrokerBasedPropertiesOverride`, like the cache's settings.
- In RDBMS mode, the broker fails to start, naming the setting, unless:
  - the write-behind cache is turned on;
  - backups are turned off: `camunda.data.primary-storage.backup.store` is `NONE` and
    `continuous` is `false` (§9.7);
  - the replication factor is 1, until followers are supported (§9.5).

### 9.2 Wiring

- `PartitionManagerImpl`, which exists once per partition group, builds one HikariCP pool from its
  `BrokerCfg`, with auto-commit off. Before its partitions start, it opens one `RdbmsStorage`,
  which runs the Liquibase changelog. It closes the pool after its partitions have stopped.
- `ZeebePartitionFactory` creates one `RdbmsZeebeDbFactory` per partition, with
  `StateIdentity(partitionGroup, partitionId)`, and `stateDbFactory` puts it inside a
  `WriteBehindZeebeDbFactory` with the commit gate (§9.4).
- `zeebe-broker` gets two new dependencies: `zeebe-db-rdbms` and `HikariCP`.
- The snapshot store reads the checksums and sizes of a marker snapshot from its files, because
  they aren't RocksDB files.
- Partition scaling copies a RocksDB snapshot (`RocksDBSnapshotCopy`), so it's rejected in RDBMS
  mode: the partition's snapshot copy throws.

### 9.3 Term and leader

- `ZeebeDbPartitionTransitionStep#transitionTo` receives the new term, but
  `context.getCurrentTerm()` is only updated once the whole transition is done. The step hands its
  term to a small holder that the factory's term supplier reads, before `StateController.recover()`
  opens the DB. The term must be the one this node leads in. The Raft server's current term may
  already be a later one, led by another node.
- Only the leader opens the DB (§9.5), so every move to leader opens a new DB with the term the
  node leads in. A leader that moves straight to a later term closes its DB and opens a new one
  too, as the ZeebeDb step already does with RocksDB.
- The leader claims the shared state and replays from the shared processed position, which
  `StreamProcessor` already reads from the state. Exporters read their positions from the shared
  state too.

### 9.4 Commit gate

- **One gate per term:** right before the ZeebeDb step opens a DB, the factory creates a
  `LeaderCommitGate` for the transition's term and the partition's current `LogStream`, and closes
  the previous term's gate. The cache's gate supplier hands that gate to the DB.
- **Committed position:** the gate listens to Raft for roles and for committed entries, so every
  change reaches it on Raft's thread, in order. Once Raft reports that this replica leads in the
  gate's term, the gate starts where a committed log reader ends: Raft only reports a new leader
  once everything before its term is committed. From then on, Raft reports each record of this
  leader that the log commits. Raft and `LogStream`'s committed-position listener only report
  records this leader wrote; starting from the end of the committed log covers the records before
  them. Until the gate starts, the committed position doesn't move.
- **Stop:** once Raft reports any other role or term, the gate stops for good and stops listening
  to Raft. That releases a commit waiting for `maxPendingBytes` and fails a snapshot waiting for
  the log. The transition closes the stream processor and the snapshot director before the DB, so
  closing the DB would come too late. If the gate can't read the end of the committed log, it
  logs a warning and waits for the next commit instead of failing Raft's listeners.
- **Last written position:** a new `LogStream#getLastWrittenPosition` returns the `Sequencer`'s
  position, which moves as soon as a record goes to the log storage. `FlowControl`'s last written
  position is only updated after the journal write, so it can lag behind.
- Followers and candidates don't open a DB (§9.5), so every gate belongs to a term this node
  leads in.

### 9.5 Followers and candidates

- They have no `ZeebeDb`. In RDBMS mode, `ZeebePartitionFactory` wraps each step that uses the DB
  in a `LeaderOnlyTransitionStep`, which passes every role except leader to the step as
  `INACTIVE`. The wrapped steps are ZeebeDb, Migration, QueryService, StreamProcessor,
  SnapshotDirector, SnapshotAfterMigration and ExporterDirector.
  - The steps still see the real current role, so a follower that becomes leader opens them, and a
    leader that steps down closes them.
  - InterPartitionCommandService, SnapshotApiHandler, BackupApiRequestHandler and CommandApi
    already run only on the leader. BackupService installs a backup manager that does nothing on
    followers, and backups are off (§9.7).
  - Followers keep the log storage and the log stream.
  - The admin "take snapshot" request does nothing on followers, because they have no snapshot
    director. Their marker snapshots are taken on a schedule (below).
- A `FollowerSnapshotDirector` takes marker snapshots, so followers can compact their log:
  - In RDBMS mode only, `FollowerSnapshotDirectorStep` starts it when the node becomes a follower,
    and stops it when the node becomes leader or inactive. It doesn't use the log stream, so it
    keeps running when the follower moves to a new term.
  - Once per snapshot period, it opens the state through `openReadOnlyDb()` (§7.1), which never
    claims the state, so it never fences the leader. It closes it again after each attempt.
  - It limits the shared processed position to the end of its own committed log, read from the
    Raft server, so the snapshot index is always in its own log.
  - From there it works like the leader's director: `SnapshotIdFinder` picks the index from the
    processed and exported positions, it takes a marker snapshot, flushes the log and persists the
    snapshot, which compacts the log.
  - It skips quietly while there is no state, nothing is processed yet, or the snapshot already
    exists. A failed attempt marks it unhealthy until the next attempt succeeds.
- The replication factor limit from §9.1 goes away.

### 9.6 Recovery check and RocksDB-only paths

- After the leader opens the DB, the shared processed position must be at or above the marker's
  and within the log (ADR D5). Otherwise the partition fails to start.
- `RecoveryPartitionManager#verifyRestoredRocksDb` and the `debug-cli` snapshot tools expect
  RocksDB files. They skip or clearly reject markers.

### 9.7 Backups

Turned off in RDBMS mode for now (§9.1). ADR D6 stays the plan: restore the database to the time
of the checkpoint, then reset the owner terms.

### 9.8 Tests

- **One replica:** a broker on H2 is restarted, and a process instance created before the restart
  continues from the shared state.
- **Followers:** three brokers in one JVM share one H2 database. The tests cover a leader change,
  follower restarts and log compaction on followers, then the same on PostgreSQL.

## 10. Testing

- **`zeebe-db-rdbms` unit tests (H2):** the migration runs once and is repeatable; `next(prefix)`
  edge cases; reading and writing the marker; state ids allocated concurrently; fencing with two
  factories on one state (claim, fenced write, claim waiting for a write in progress). Waiting for
  a row lock is tested with a short H2 `LOCK_TIMEOUT`: the waiting side fails with
  `ZeebeDbException`, so the tests need no threads.
- **Contract suites in `zeebe/zb-db/tests`:** an `RdbmsTestFactory` creating a fresh state per DB
  in one shared in-memory H2 database, and `Rdbms*Test` subclasses of every contract suite, the same
  way `RocksDb*Test` and `WriteBehind*Test` do, plus `RdbmsVsRocksDbDifferentialTest`.
- **The cache over the database:** `WriteBehindOverRdbms*Test` runs every contract suite through
  the write-behind cache on top of the shared H2 state, and
  `WriteBehindOverRdbmsVsRocksDbDifferentialTest` compares it with RocksDB.
  `WriteBehindOverRdbmsFailoverTest` holds writes in the cache and checks a failover: a new leader
  recovering from a snapshot sees every transaction committed before it, and once it claims the
  state, the old leader's snapshot and commits fail with `StateFencedException` and none of its
  writes reach the state.
- **PostgreSQL:** `Postgres*IT` subclasses run the same tests against one Testcontainers
  PostgreSQL server per JVM, started by the first test that needs it. In `zeebe-db-rdbms`, every
  suite that talks to the database gets a fresh database through a `createDatabase` hook, and the
  lock timeout becomes PostgreSQL's `lock_timeout`. `RdbmsZeebeDbTest` stays on H2, as it needs
  H2's connection pool and a data source that fails on purpose. In `zeebe/zb-db/tests`, every
  contract suite and the failover test run on PostgreSQL, directly and through the cache. The
  randomized suites don't: they pass on PostgreSQL, but take minutes, and as `*IT` they would never
  run, as Failsafe leaves out the `randomized` tag.
- **Cache:** the writer holds back transactions until their position is committed, keeps their
  order, and counts only the released ones towards the batch size; the cache records the position
  when a transaction commits, and a snapshot waits for the commit.

Builds: `./mvnw install -pl zeebe/zb-db/rdbms -am -Dquickly -T1C`, then
`./mvnw verify -pl zeebe/zb-db/rdbms,zeebe/zb-db/tests -DskipTests=false -Dquickly`. The
differential and property suites are tagged `randomized`; they only run with
`-Pinclude-random-tests`. The `*IT` classes need Docker; `-DskipITs` leaves them out.

## 11. Risks

1. **Key length.** PostgreSQL limits a B-tree entry to about 2.7 KB. Keys with long user strings may
   exceed it. Measure the largest keys the engine writes before choosing a fix.
2. **Failover time.** The new leader replays the old leader's unwritten backlog with an empty cache.
   The cache's pending limit bounds it; the benchmark must measure it.
3. **Scan latency.** A scan holds back cache publications until the first row arrives (cache design
   §8 risk 9). That's a query round trip here.
4. **Commit gate and snapshots.** A partition that can't reach the Raft commit can't write, so its
   cache fills up and it can't snapshot. That's the same as today's commit wait in
   `AsyncSnapshotDirector`, but it now also blocks the writer and, once `maxPendingBytes` is
   reached, the stream processor. Part B must release both on step-down (§9.4).
5. **Liquibase lock overlap** with the history migration in the same JVM (§5).
6. **Binary ordering on other vendors.** SQL Server compares trailing zeros as equal; MySQL and
   Oracle limit key lengths. Each new vendor needs the contract tests.

## 12. Implementation plan

Each step is one commit, reviewed before the next one. Part A ends with a PR.

**Part A: zb-db**
1. **docs:** this design and ADR 0015.
2. **build:** module skeleton, Liquibase changelog and `StateSchemaManager`, MyBatis setup, tests on
H2.
3. **feat:** transactions, write buffer, point operations and scans; every contract suite and the
differential test against RocksDB on H2. One step, because the point and transaction suites
already need scans, `count` and `isEmpty`.
4. **feat:** state rows, fencing by term, marker snapshots and the snapshot-only DB.
5. **test:** the write-behind cache over the RDBMS: contract suites, differential test and a
failover test.
6. **feat:** the commit gate in the cache's writer.
7. **test:** the suites on PostgreSQL with Testcontainers (§10).

**Part B: broker** (§9)
8. **feat:** configuration, connection pool, wiring and the term (§9.1–9.3), with one replica
only; the one-replica broker test (§9.8). Until step 9, the writer doesn't wait for the commit, so a
crash can leave the state ahead of the log.
9. **feat:** the commit gate fed by Raft and the log stream (§9.4).
10. **feat:** followers without a `ZeebeDb`, with marker snapshots; more than one replica; the
follower tests (§9.5, §9.8).
11. **feat:** the recovery check and the RocksDB-only paths (§9.6).
12. Benchmark against RocksDB, with and without the cache, then decide.
