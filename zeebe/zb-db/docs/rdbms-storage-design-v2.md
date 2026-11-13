# RDBMS storage for zb-db, version 2: use the database as a database

**Status**: Proposed, not agreed yet. This describes how we would build the RDBMS state if we
started again, with what the proof of concept (#69157) taught us. Version 1 is
[rdbms-storage-design.md](rdbms-storage-design.md), with its decisions in
[ADR 0015](../../docs/adr/0015-811-shared-rdbms-partition-state.md) and its load test in
[rdbms-storage-load-test-results.md](rdbms-storage-load-test-results.md). Where the two designs
differ, this one wins; ADR 0015's D4, D7 and D8 are updated to match. Section 17 lists the
differences.

## 1. Goal and scope

Store a partition's runtime state in a relational database, behind the
[write-behind cache](write-behind-cache-design.md), and use what a database does well instead of
treating it as a remote key-value store. Compared with version 1:

1. **One database.** The state lives in the database the secondary storage (history) already uses,
   with the same settings, connection details and Liquibase schema management. RDBMS state
   therefore needs RDBMS secondary storage.
2. **One table per column family**, so each kind of state has its own table, statistics and size.
3. **Readable without zdb.** Views decode the keys for people. Nothing is decoded on the write path.
4. **The database records how far it got.** The writer's transaction stores the log position the
   tables now stand at, in the same statement that fences old leaders.
5. **Fewer round trips** where the engine waits for them: a cache miss is one statement.

What stays from version 1: one shared state per partition; only the leader reads and writes it,
behind the write-behind cache; fencing by term; the commit gate; followers without a `ZeebeDb`,
taking marker snapshots; the recovery check (ADR D5); backups turned off for now (ADR D6 stays the
plan).

Out of scope: reads of runtime state by other components such as the REST API (the table model
leaves room for them, §16), vendors other than H2 and PostgreSQL in the first version (§14),
partition scaling, backups and restore.

## 2. What shapes the design

### 2.1 What the cache allows, and what it asks for

The storage sits behind the cache, so the cache decides what the storage sees:

- **Raw bytes, in unsigned byte order.** The cache only hands over serialized keys and values. It
  merges its own changes into the storage's scans, comparing keys byte by byte as unsigned numbers
  (cache design §4.1). The storage must return keys in exactly that order, so the binary key stays
  the primary key (§7.6).
- **Late, merged, whole transactions, in order.** The writer writes Raft-committed transactions in
  commit order, in groups, keeping only the newest version of each key, from one thread per
  partition. Every group ends on a transaction boundary. So after each database commit, the tables
  hold exactly the state after one log position. Version 1 didn't record which one; version 2 does
  (§9.2).
- **Misses are synchronous.** A read that misses the cache waits for the database on the stream
  processor's thread. In version 1's load test, PostgreSQL state caused 84–93% backpressure and a
  median processing latency near 195 ms, against 3 ms with RocksDB. The likely main cause is
  misses: the cache's own benchmarks measured 61–72% of them. So read round trips matter more than
  anything on the write side, which runs in the background.
- **Scans always reach the database.** The cache merges a scan from the storage with its unwritten
  changes, and doesn't keep what the scan returns. Every scan is a query.
- **Missing keys are cached.** A miss for a key that doesn't exist is cached as absent, so it costs
  one read until the key is written or evicted.
- **The database holds the only full copy.** A new leader starts with an empty cache, so right after
  a failover nearly every read is a miss.

### 2.2 What the database offers

|           Feature           |           Version 1 uses it for           |                    Version 2 adds                     |
|-----------------------------|-------------------------------------------|-------------------------------------------------------|
| One copy that all can reach | no replication of state, marker snapshots |                                                       |
| Transactions and row locks  | fencing old leaders                       | the written position, in the same statement (§9.2)    |
| SQL                         |                                           | views that decode keys (§8)                           |
| Tables and catalog          |                                           | statistics, size and settings per column family (§7)  |
| Statements over many rows   |                                           | later: deleting or copying a partition's state (§16)  |
| One backup with the history |                                           | later: state and history restored to one moment (§13) |

### 2.3 Principles

1. **The write path carries only what the engine needs.** Making data readable for people happens
   when they read it, not when the engine writes it.
2. **A cache miss costs one statement and one round trip.**
3. **The writer's transaction does its own bookkeeping.** Fencing and progress are one statement on
   a row the transaction locks anyway.
4. **A wrong description of a key never stops a partition.** At worst, a view shows wrong values.

## 3. How it fits

```
StreamProcessor / ExporterDirector / ...        (unchanged, typed ZeebeDb API)
        │
WriteBehindZeebeDb (zeebe-db-cache)             reads: cache, then storage on a miss
        │  writer: committed transactions,      writes: after the Raft commit, as raw batches
        │  in commit order, in groups           with their log positions (§9)
RdbmsZeebeDb (zeebe-db-rdbms)                   one shared state per partition, fenced by term
        │  one table per column family          MyBatis, our own connections
        │  views for people (§8)
State pool ──┐
             ├── the secondary storage's database, per physical tenant
History pool ┘   (same url, user, prefix; schema by one Liquibase manager)
```

## 4. Configuration

- `camunda.data.primary-storage.type=rdbms` needs `camunda.data.secondary-storage.type=rdbms`.
  Otherwise the broker fails to start and names both settings. Physical tenants follow the same
  rule, each with its own settings.
- There is no `camunda.data.primary-storage.rdbms.*` block any more. The state uses the secondary
  storage's `url`, `username`, `password`, `prefix`, `database-vendor-id`, `auto-ddl`,
  `ddl-lock-wait-timeout`, `retry` and `connection-pool.*`.
- The history writer's settings (`flush-interval`, `queue-size`, `queue-memory-limit`, `history.*`,
  `insert-batching`, …) don't apply to the state. The write-behind cache has its own
  (`camunda.data.primary-storage.write-behind-cache.*`).
- The version 1 rules stay: the write-behind cache must be on, and backups must be off.
- The broker fails to start when the vendor isn't supported for the state yet (§14), and names the
  vendor.

## 5. Connections

The state gets **a second pool per physical tenant, built from the same settings** as the history's
pool, by the same `RdbmsDataSources` bean:

- The writer and the stream processor's misses always get a connection, even when REST queries or a
  slow exporter use all of the history's.
- The state's pool sets what the state needs without touching the history: `READ COMMITTED`
  explicitly (the history pool keeps the database default, which is `REPEATABLE READ` on MySQL,
  while `NoSnapshotIsolationTest` needs reads that see other transactions' commits), and a bigger
  prepared statement cache (§10.3).
- Separate pool metrics show who uses the connections.
- It costs twice the connections. The pool uses the same `connection-pool.*` values and adds no new
  settings; the benchmark decides whether the state needs its own size.

The pool is only created for tenants whose state is in the RDBMS. Auto-commit is off by default in
both pools; the state turns it on for single reads (§10.1).

Sharing the history's pool was the other option. It needs nothing new, but lets the history starve
the state, and then the cache fills up and slows down processing.

## 6. Schema

### 6.1 One Liquibase manager

- The state's changelog, `db/changelog/zeebe-state/changelog-master.xml`, lives in
  `db/rdbms-schema`, next to the history's changelog. The Zeebe team owns those files (CODEOWNERS).
- `LiquibaseSchemaManager` runs it right after the history changelog, the same way it already runs
  `schema-version-seed.xml` before it: under the same JVM-wide lock (#61009), with the same tracking
  tables (`${prefix}DATABASECHANGELOG`, `${prefix}DATABASECHANGELOGLOCK`), prefix, parameters,
  retries, `ddl-lock-wait-timeout` and schema version check.
- It runs only for tenants whose state is in the RDBMS, so a cluster with RocksDB state doesn't get
  ~145 empty tables. Turning RDBMS state on later applies it at the next start.
- With `auto-ddl: false`, the database administrators run the scripts instead. Next to the
  history's `<vendor>_master.sql`, `LiquibaseScriptGenerator` writes a separate
  `liquibase/sql/create/<vendor>/<vendor>_zeebe_state.sql`, for H2 and PostgreSQL (§14).
- `RdbmsStorage` only opens the tables; it no longer creates them.
- `RdbmsSchemaManagerRegistry.isInitialized(tenant)` reports a tenant as ready only once both
  changelogs have run. Partitions wait for it (§12).

This removes version 1's own `StateSchemaManager`, its own tracking tables, and the risk of two
Liquibase runs overlapping in one JVM (version 1 §11 risk 5). Liquibase keeps its lock bookkeeping
for the whole JVM, so when two runs with different locks overlap, one of them can leave its
database lock taken, and the next start waits for it.

On a database where version 1's manager already created the state's tables, its
`ZEEBE_STATE_DATABASECHANGELOG` tracking tables stay behind. The changesets find the tables and mark
themselves as run (`MARK_RAN`), and nothing reads the old tracking tables any more. A proof of
concept has no installations to upgrade, so we leave them.

### 6.2 Generated changesets

- The tables (§7) and views (§8) come from one registry. A developer tool generates their
  changesets, and we commit its output so that it's reviewed like any changelog.
- Tables: one changeset per table, in one file per release (`changesets/8.11.0.xml`), using
  `${prefix}`, types per vendor through properties, and `preConditions onFail="MARK_RAN"`, as the
  history does. A table, once released, never changes.
- Views: one changeset per view with `runOnChange="true"`, so fixing or adding a key layout replaces
  the view at the next start. Views hold no data, so replacing them is cheap and safe.
- A test fails when the committed changelog and the registry differ, and says how to regenerate it.

## 7. Tables

### 7.1 The table registry

`StateTables`, in `zeebe-db-rdbms`, maps each column family number to its table: a short name and,
optionally, the layout of its key (§8). It is the single source for the changelog generator, the
mapper and the views.

- It's keyed by the column family's number (`EnumValue#getValue`), as RocksDB's keys and the cache's
  `CacheKey` are. The enum class plays no part. Only `ZbColumnFamilies` is used in the broker.
- A test fails when a live column family in `ZbColumnFamilies` is missing, and says what to add.

### 7.2 Names

Tables are called `${prefix}ZS_<SHORT_NAME>`, with an explicit short name of at most 24 characters
per column family, for example `ZS_JOB_DEADLINES` or `ZS_DMN_DRD_KEY_BY_ID_VER`. Constraints
and views follow the table: `${prefix}ZS_<SHORT_NAME>_PK` and `${prefix}ZSV_<SHORT_NAME>`. All of
them fit in 30 characters before the prefix.

- The column families' own names don't fit: 65 of the 142 live ones are longer than 24 characters,
  and the longest has 68 (`DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION`).
  PostgreSQL silently cuts names after 63 bytes, so two long names could end up as the same table.
- Short names are written down once and never derived, because a column family can be renamed (the
  deprecated ones were) and its table must not change.
- A test checks the length, and that the names are unique and don't clash with `ZS_PARTITION` or
  `ZS_OTHER`.
- `ZS_` keeps the state tables apart from the history tables in the same schema and lists them
  together; `ZSV_` does the same for the views.

### 7.3 Which column families get a table

- **Each live column family**: 142 today, 96 local to a partition and 46 global.
- **`DEFAULT`** gets `ZS_DEFAULT`, without a key layout: several state classes share it with
  different key shapes.
- **Deprecated column families** (27 today) and **any number not in the registry** go into one
  fallback table, `ZS_OTHER`, with a `COLUMN_FAMILY` column in its primary key, like version 1's
  single table. A new RDBMS state never holds old data, but deprecated column families are still
  read by migrations, and some still get writes: `DbJobState` drains the deprecated
  `JOB_ACTIVATABLE`.
- **Global column families** (deployments, identity, …) are stored per partition, as with RocksDB:
  every partition keeps its own copy, filled by command distribution. Sharing one copy is an idea
  for later (§16).

### 7.4 Columns

|       Column       | Type (H2 / PostgreSQL) |                            Notes                             |
|--------------------|------------------------|--------------------------------------------------------------|
| `PARTITION_ID`     | `INT`                  |                                                              |
| `STATE_KEY`        | `VARBINARY` / `BYTEA`  | the serialized key, as the engine writes it                  |
| `STATE_VALUE`      | `VARBINARY` / `BYTEA`  | `NOT NULL`, may be empty                                     |
| `UPDATED_POSITION` | `BIGINT`               | `NOT NULL`, the log position that wrote this version (below) |

Primary key `(PARTITION_ID, STATE_KEY)`; `ZS_OTHER` adds `COLUMN_FAMILY INT` between the two. There's
no partition group column: each physical tenant has its own database or prefix, as for its history
tables (§18 question 2).

- **`UPDATED_POSITION`** is the position of the cache transaction that wrote this version: the log's
  last written position when it committed (`CommittedTransaction#position`). For the stream
  processor, that's the last record it wrote while processing the command. It tells which part of
  the log last changed a row, without zdb. Writes that don't come from the cache, which only tests
  make, store `-1`.
- **No `CREATED_POSITION`.** Keeping a column from the first insert means an upsert must update
  only some columns, which H2 can only do with a longer `MERGE … USING` statement, and it costs 8
  bytes on every row for a question support rarely asks.
- **No decoded key columns.** They'd cost CPU on the writer and space on every row, and a wrong
  layout would need a backfill. Views do the same at read time (§8).

### 7.5 Indexes

- Only the primary key. The engine reads single keys and ordered key ranges within one partition,
  and the primary key serves both.
- Never index `UPDATED_POSITION` or `STATE_VALUE`. Most of the engine's writes update rows that
  already exist (element instances, jobs, variables). On PostgreSQL, an update that changes no
  indexed column can stay on the same page without touching any index (a "HOT" update). Indexing a
  column that changes on every write would end that.
- An index for people's queries (§8) is added only when a named query needs it, in its own
  changeset, knowing that it slows down every write to its table.

### 7.6 Why the key stays binary

The engine relies on keys being ordered byte by byte as unsigned numbers, and the cache merges its
own changes with the storage's scans in that order. Typed key columns would order rows differently:

- `DbJobState` stores `Integer.MAX_VALUE - priority`, and relies on negative priorities wrapping
  around to values from `0x80000000` up, which sort last when compared as unsigned bytes. As a
  signed `INT` column they would sort first, and jobs would be activated in another order.
- `DbString` writes the length before the text, so shorter strings come first. A `VARCHAR` column
  sorts by text.
- Text order also depends on each database's collation: MySQL's `utf8mb4_bin` ignores trailing
  spaces, and SQL Server compares UTF-16 code units.

So `STATE_KEY` stays the binary primary key. The cache's `CacheKey` and its order don't change.

**Rejected alternative:** typed columns as the primary key, with an encoding per vendor that keeps
the order, and a comparator per column family in the cache. Every column family's layout would have
to match the engine's byte order exactly, RocksDB and the RDBMS could then disagree on order, and a
mistake would change the engine's behavior instead of only showing a wrong value in a view.

### 7.7 The partition table

`${prefix}ZS_PARTITION` holds one row per partition:

|       Column       |     Type      |                                 Notes                                  |
|--------------------|---------------|------------------------------------------------------------------------|
| `PARTITION_ID`     | `INT`         | primary key                                                            |
| `STATE_ID`         | `VARCHAR(36)` | a random UUID, created with the row                                    |
| `OWNER_TERM`       | `BIGINT`      | the term of the leader that owns the state                             |
| `WRITTEN_POSITION` | `BIGINT`      | the log position the tables stand at, `-1` until the first cache write |

- Markers name the `STATE_ID`, so a broker never continues from a marker on a state that was deleted
  and created again. Version 1 needed `MAX + 1` numbers and retries to allocate it; a UUID needs
  neither.
- `OWNER_TERM` is claimed when a leader attaches, as in version 1 (§7.5 there).
- `WRITTEN_POSITION` is set by every write transaction from the cache (§9.2).
- Creating a state inserts its row first, then deletes any rows the partition still has in the
  state tables, for example after someone deleted only its `ZS_PARTITION` row. Inserting first
  means that of two replicas creating the state at once, the second fails on the primary key
  before it deletes anything.

## 8. Views: decoding keys when people read

People and support tools read the state through one view per column family that has a key layout.
The engine never reads them.

```sql
SELECT DEADLINE, JOB_KEY, UPDATED_POSITION
FROM ZSV_JOB_DEADLINES
WHERE PARTITION_ID = 1 AND DEADLINE < 1767225600000;
```

- **Layouts.** The registry describes a key as a list of parts: `LONG`, `INT`, `SHORT`, `BYTE`,
  `STRING`, the tenant id (as `DbTenantAwareKey` places it) and `BYTES`. Each part becomes a column.
  The layout is optional, so column families get one at a time, the hot ones first.
- **Columns.** `PARTITION_ID`, the decoded parts, `UPDATED_POSITION`, then `STATE_KEY` and
  `STATE_VALUE` as they are.
- **Decoding** uses each database's built-in functions, generated per vendor: substrings of the key,
  casts of 2, 4 and 8 bytes to numbers (big-endian, as `ZeebeDbConstants.ZB_DB_BYTE_ORDER`), and
  UTF-8 conversion for strings, whose offsets follow the lengths before them. There are no
  user-defined functions to create, grant or keep in step. A part outside the key gives `NULL`, and
  a string's length is kept within the key, so a key that doesn't match its layout shows wrong
  values instead of failing the query.
- **Changelog.** The views live in their own generated `views.xml`, included after every release's
  tables. Each view's change set drops and creates it again whenever it changes
  (`runOnChange`), because PostgreSQL can't rename or drop a view's columns in place.
- **Values that mean something else.** A view shows what's stored. Where the engine changes a value
  before storing it, as with the job priority (§7.6), the layout leaves that part as `BYTES`.
- **Values** (msgpack) aren't decoded in this version; `STATE_VALUE` is there for tools (§16).

Compared with decoded columns filled by the writer:

|                           |    Columns filled when writing     |                           Views                            |
|---------------------------|------------------------------------|------------------------------------------------------------|
| Cost for each write       | CPU on the writer, bigger rows     | none                                                       |
| A wrong layout            | empty or wrong columns, a backfill | a wrong view, replaced at the next start                   |
| Adding a layout later     | a new column and a backfill        | a changed view                                             |
| Indexes for a named query | on the column                      | on the expression (PostgreSQL), or a generated column then |
| Reading                   | plain columns                      | decoding on every row read                                 |

Reading costs more with views, but people read rarely, and the engine never reads them.

## 9. Writes

### 9.1 Raw batch writes from the cache

Today the cache's writer writes through the storage's column families with `RawKey` and
`RawValue`. That has no room for a position, and hides that the writes arrive as one sorted batch.
A storage can now take them directly:

```java
/** Optional: a ZeebeDb that takes the write-behind cache's writes as raw bytes. */
public interface RawBatchWriter {
  /**
   * Writes in one transaction, then records that the state now stands at the given position.
   * Sorted by column family number, then by key; one write per key.
   */
  void write(List<RawWrite> writes, long position);
}

/** A null value deletes the key. The position is that of the transaction that wrote it. */
public record RawWrite(EnumValue columnFamily, byte[] key, byte @Nullable [] value, long position) {}
```

- It lives in `zeebe-db-api`. The RDBMS DB implements it; RocksDB doesn't need to, and the cache
  keeps using column families for a storage that doesn't.
- When the writer merges a group into the newest version of each key, it keeps the position of the
  transaction that wrote that version next to it, only for the group being written. `CachedValue`
  gets no new field, so cached entries use no more memory.
- The group's position is that of its last transaction.
- The rest stays: grouping, the commit gate, waiting before a snapshot, retrying recoverable
  errors, dropping unwritten transactions on close, and stopping on a fenced write.

### 9.2 One write transaction

For each group, on one connection with auto-commit off:

1. **Fence and record progress**, in one statement:

   ```sql
   UPDATE ZS_PARTITION SET WRITTEN_POSITION = :position
   WHERE PARTITION_ID = :partition AND STATE_ID = :stateId AND OWNER_TERM = :term
   ```

   No row updated means another leader owns the state, or the state is gone: the write throws
   `StateFencedException`, which the writer doesn't retry. The update locks the row until the
   commit, so a new leader's claim waits for a write in progress, and after the claim the old
   leader's writes fail. That's what version 1's `SELECT … FOR UPDATE` did, without a separate
   statement for the progress.

2. **Deletes, then upserts, per table**, each as one JDBC batch. MyBatis' batch executor starts a
   new batch when the statement changes, and the writes come sorted by column family, so a table
   gets at most two batches.

   - PostgreSQL: `INSERT … ON CONFLICT (PARTITION_ID, STATE_KEY) DO UPDATE SET STATE_VALUE =
     EXCLUDED.STATE_VALUE, UPDATED_POSITION = EXCLUDED.UPDATED_POSITION`
   - H2: `MERGE INTO … KEY (PARTITION_ID, STATE_KEY) VALUES (…)`, which replaces the whole row; with
     no `CREATED_POSITION`, that's exactly an upsert.
3. **Commit.**

Version 1's write buffer isn't involved: the cache already merged the writes.

**Round trips.** About `2 + 2 × tables touched` per group, against 4 in version 1, where one batch
held every table's rows. A busy group may touch 15–20 tables, so about 40 round trips. This happens
on the writer's thread, not the stream processor's, once per group, and groups grow up to
`batch-size` (1,000 keys) when the writer falls behind, so the cost per key falls as load rises. The
benchmark measures it (§15 risk 2). If it's too slow, PostgreSQL can take each table's rows in one
statement through arrays (`unnest`).

**Writes not from the cache**, through column families, keep version 1's write buffer and fence with
`SET OWNER_TERM = OWNER_TERM` instead, as they have no position. Only tests do this.

### 9.3 What `WRITTEN_POSITION` gives

- **A consistent point for SQL readers.** The tables always hold the state after `WRITTEN_POSITION`,
  because every group ends on a transaction boundary and its rows commit with the position. A
  support query can say how old its answer is.
- **The writer's lag**, as the log's commit position minus `WRITTEN_POSITION`, readable from the
  database and as a metric.
- **Later:** the recovery check (ADR D5) could compare the marker with `WRITTEN_POSITION` instead
  of reading the processed position from a column family, and the RDBMS exporter could know how far
  the state is (§16).

## 10. Reads

### 10.1 Point reads in one round trip

Version 1 ran every read outside a write transaction in a short transaction of its own: borrow a
connection, turn auto-commit off, `SELECT`, `COMMIT`. That's two round trips for each cache miss.

Version 2 runs `get`, `exists`, `count` and `isEmpty` outside a write transaction with auto-commit
on: one statement, one round trip. Under `READ COMMITTED` a single statement sees the same
committed data either way. The cache only reads its storage outside transactions, so every miss
takes this path.

### 10.2 Scans

Scans keep a transaction, as in version 1: auto-commit off and a fetch size of 256, so PostgreSQL
streams the rows through a cursor instead of loading them all. The scan bounds work as in version 1
(§7.4 there). Most of the engine's scans stop after a few rows, so if scans turn out to matter, a
first page in auto-commit with `LIMIT` is an option (§16).

### 10.3 Statements

- One mapper, `StateMapper.xml`, next to its interface, not under `mapper/`: the history loads every
  `classpath*:mapper/*.xml` into its own factory.
- The table name is put into the SQL as `${table}`, and only ever comes from the registry, never
  from input. `ZS_OTHER`'s statements add the `COLUMN_FAMILY` condition.
- The state builds its own `SqlSessionFactory` from a plain MyBatis `Configuration`, on the state's
  data source, with the tenant's vendor properties and database id. It can't use the history's
  factories: they use Spring's managed transactions, while the state opens, commits and closes its
  connections itself (version 1 §7.2).
- **Prepared statements.** About 145 tables with a few statements each make many different SQL
  strings. The PostgreSQL driver keeps 256 prepared statements per connection by default. The hot
  tables are far fewer, but the state's pool raises `preparedStatementCacheQueries` (§5).

## 11. Changes in the write-behind cache

Only what §9.1 describes: `RawBatchWriter` in `zeebe-db-api`, and the writer keeping each key's
position for the group it writes. The cache's keys, their order and its in-memory values don't
change.

## 12. Broker wiring and startup order

- The state's data source comes from Spring: `RdbmsDataSources`, per physical tenant (§5).
  `PartitionManagerImpl` no longer builds a pool.
- The broker gets an `RdbmsStateDatabase` per tenant: the data source, the prefix and a check that
  tells whether the tenant's schema is ready (§6.1). It's a record in the broker, built by
  `BrokerModuleConfiguration` the way it already builds the RDBMS exporter's position suppliers, so
  the broker doesn't depend on `db/rdbms`. The vendor comes from the data source's metadata.
- **No Spring cycle:** the `rdbmsSchemaInitializer` bean depends on the broker, so that it starts
  after it and its recovery check reads the broker's cluster configuration. The broker must
  therefore not depend on the initializer. It only depends on `RdbmsDataSources` and the registry
  (`LazyInitializedRdbmsSchemaRegistry`), which the initializer binds itself to later. Creating
  the broker bean only starts the broker; it doesn't wait for partitions.
- **Waiting for the schema:** before it opens the state and starts the tenant's partitions, the
  partition manager waits until the registry reports the tenant's schema as ready. It checks again
  every 250 ms on its actor instead of blocking it. Until then the tenant hasn't registered its
  partitions with the health check, so the broker isn't reported ready. The schema initializer
  keeps retrying with backoff, per tenant, as it does today. Waiting in each leader's ZeebeDb
  transition step instead would let followers replicate meanwhile, but the state's storage is
  opened once per partition group, and the wait is short: the initializer starts right after the
  broker bean is created.
- A restore runs in recovery mode, whose partition manager brings the schema up to date itself, so
  it never waits for it. The `restore` profile doesn't create the schema initializer either.

## 13. Snapshots, recovery, backups and tests

- Snapshots and recovery work as in version 1 (§7.6, §9.5, §9.6 there). The marker's `version`
  becomes `2`, with `partitionId` and `stateId` (the UUID); the partition group goes.
- Backups stay off. ADR D6 gets simpler: state and history live in one database, so one
  point-in-time restore brings both back to the same moment. Resetting the owner terms and lining
  up with the checkpoint still need their own design.
- Tests:
  - The contract suites run as in version 1. Their enums use numbers 0 to 3, so they write to
    `ZS_DEFAULT`, `ZS_KEY` and, for the deprecated numbers 2 and 3, `ZS_OTHER`: both paths are
    covered without a test registry.
  - The cache's suites over the RDBMS check the positions, `WRITTEN_POSITION` and the fencing
    through `RawBatchWriter`.
  - A test checks that every live column family is in the registry, the names (§7.2), and that the
    committed changelog matches the registry (§6.2).
  - A test checks that the state scripts for `auto-ddl: false` create the same tables, views,
    columns and primary keys as the changelog, on H2 and PostgreSQL (§6.1).
  - For every layout, a test writes keys with the engine's own key types and reads them back through
    the view, on H2 and PostgreSQL.

## 14. Vendors

The history supports six vendors. The state starts with two; the others need the work below, and
until then the broker refuses to start with RDBMS state on them (§4).

|     Vendor     |    Key type    |                                               Order and limits                                               | First version |
|----------------|----------------|--------------------------------------------------------------------------------------------------------------|---------------|
| H2             | `VARBINARY`    | unsigned bytes                                                                                               | yes           |
| PostgreSQL     | `BYTEA`        | unsigned bytes; an index entry holds about 2.7 KB (version 1 §11 risk 1)                                     | yes           |
| MySQL, MariaDB | `VARBINARY(n)` | unsigned bytes, no padding; a primary key holds at most 3072 bytes, so `n` is bounded                        | later         |
| Oracle         | `RAW(n)`       | at most 2000 bytes unless extended strings are on; an empty `RAW` is `NULL`                                  | later         |
| SQL Server     | `VARBINARY(n)` | ignores trailing `0x00` when comparing, so `0x01` equals `0x0100`; needs a key encoding that keeps the order | later         |

## 15. Risks and costs

1. **Read latency on misses** stays the main risk, as version 1's load test showed. This design
   halves the round trips of a miss (§10.1), but each miss still waits for the network. If that's
   not enough, the next steps are in §16: loading small, hot tables into the cache when a leader
   attaches, and letting scans fill the cache.
2. **Write round trips** grow with the number of tables a group touches (§9.2). Measured by the
   benchmark; `unnest` statements are the fallback.
3. **A new column family needs a schema change.** Today an engine developer adds an enum entry. Now
   they also add a registry entry and generate the changesets; the tests in §13 say what's missing.
   Until they do, its rows go to `ZS_OTHER`, so nothing breaks.
4. **Processing depends on the history database.** A database outage now stops processing as well
   as the history, and a failing history migration keeps partitions from starting. The state's
   writes are much more frequent than the history's, so the database must be sized for both.
5. **Many tables:** about 145 more per prefix in the history's schema, and many prepared statements
   (§10.3). Creating them makes a fresh migration slower: measured at 1 to 2 seconds on H2, instead
   of well under one, and more on PostgreSQL. That's paid once per database at startup, but in
   every test that starts from an empty database. About half of it is the `tableExists`
   preconditions; if it matters, the generator can drop them, or group tables into fewer
   changesets.
6. **Key length** and **vendor order** (§14).

What we gain: small tables with their own statistics; per-table settings for the tables that change
most, such as fill factor and autovacuum for `ZS_JOB_DEADLINES` or `ZS_TIMER_DUE_DATES`; sizes per
column family straight from the database catalog; deleting a partition's state with one statement
per table; readable state without zdb, at no cost to the engine; and a recorded position for the
whole state.

## 16. Later

- **Loading hot tables when a leader attaches**, for small tables that every command reads
  (deployments, identity), to soften the cold cache after a failover.
- **Scans that fill the cache.** Today they can't: a value from a scan could overwrite a newer one,
  or come back after its key was evicted. It needs a check against the cache's unwritten changes.
- **A first page of a scan in auto-commit**, with `LIMIT` (§10.2).
- **A writer that prepares the next group** while the database commits the previous one.
- **Reads by other components:** decode chosen value properties (msgpack) in the views, with
  indexes, once a named use case needs them.
- **One copy of the global column families**, shared by all partitions.
- **Partition scaling** by copying a partition's global rows with `INSERT … SELECT`, instead of
  copying a snapshot.
- **The RDBMS exporter** writing history and its exporter position in one transaction, now that
  both live in one database.
- **PostgreSQL table partitioning** by `PARTITION_ID`, if tables get very large.

## 17. Compared with version 1

|       Area       |                  Version 1 (proof of concept)                   |                                 Version 2                                 |
|------------------|-----------------------------------------------------------------|---------------------------------------------------------------------------|
| Settings         | `camunda.data.primary-storage.rdbms.*`, any database            | the secondary storage's settings; RDBMS secondary storage required        |
| Connections      | a pool per partition group, built by `PartitionManagerImpl`     | a second pool per tenant from `RdbmsDataSources` (§5)                     |
| Schema           | own changelog in `zeebe-db-rdbms`, own tracking tables and lock | generated changelog in `db/rdbms-schema`, run by `LiquibaseSchemaManager` |
| Tables           | `ZEEBE_STATE_PARTITION` and one `ZEEBE_STATE` table             | `ZS_PARTITION`, one table per column family, `ZS_OTHER`                   |
| Readable state   | zdb only                                                        | views that decode keys (§8), `UPDATED_POSITION` per row                   |
| State id         | `MAX + 1` with retries, partition group column                  | random UUID, no partition group                                           |
| Fencing          | `SELECT … FOR UPDATE` before the first write                    | one `UPDATE` that fences and records `WRITTEN_POSITION` (§9.2)            |
| Cache to storage | through column families and a write buffer                      | raw batches with positions (§9.1)                                         |
| A cache miss     | two round trips (`SELECT`, `COMMIT`)                            | one (§10.1)                                                               |
| Unchanged        | scans, markers, commit gate, followers, recovery check          | same                                                                      |

## 18. Open questions

The first draft of this design asked about the pool (now a second pool, §5), the table names (`ZS_`,
24 characters, §7.2) and `CREATED_POSITION` (dropped, §7.4). Still open:

1. **Ownership:** are the history team fine with the state's changelog in `db/rdbms-schema` and
   with `LiquibaseSchemaManager` running it (§6)?
2. **Physical tenants:** do we already stop two tenants from using the same database and prefix?
   The state relies on it, as the history does (§7.4).
3. **API:** is `RawBatchWriter` in `zeebe-db-api` acceptable (§9.1)?

## 19. Implementation plan

Starting from the proof of concept, with the storage first: it decides the benchmark, and only
needs `zb-db`. The wiring into the broker and the shared schema come after. Each step is one or two
commits.

1. **docs:** this design; ADR 0015 updated for D4, D7 and D8.
2. **feat:** the table registry and the changelog generator; one table per column family,
   `ZS_OTHER`, and `ZS_PARTITION` with a UUID state id; the mapper per table; marker version 2.
   Still run by version 1's `StateSchemaManager`.
3. **feat:** one statement that fences and records `WRITTEN_POSITION`, replacing `SELECT … FOR
   UPDATE`.
4. **feat:** raw batch writes with positions from the cache, and `UPDATED_POSITION`.
5. **perf:** point reads in auto-commit.
6. **feat:** key layouts and views, the hot column families first.
7. **refactor:** RDBMS state requires RDBMS secondary storage and uses its settings; the data
   source comes from `RdbmsDataSources` and reaches the broker as an `RdbmsStateDatabase`; the
   `primary-storage.rdbms` settings go.
8. **feat:** partitions wait until the schema is ready; the state's changelog moves to
   `db/rdbms-schema`, `LiquibaseSchemaManager` runs it, and the generator writes the state scripts
   for `auto-ddl: false`.
9. **test:** the PostgreSQL and broker tests on the new tables.
10. Benchmark against version 1's single table and against RocksDB, then decide.

Status: steps 1 to 9 are done. The storage's PostgreSQL tests and the broker tests, on H2 and
PostgreSQL, pass on the new tables. The first benchmark (step 10) is in
[its results](rdbms-storage-load-test-results-v2.md); the decision is still open.

