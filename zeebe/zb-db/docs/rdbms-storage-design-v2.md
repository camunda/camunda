# RDBMS storage for zb-db, version 2: one database, one table per column family

**Status**: Proposed, not agreed yet. This describes how we would build the RDBMS state if we
started again, with what the proof of concept (#69157) taught us. Version 1 is
[rdbms-storage-design.md](https://github.com/camunda/camunda/blob/bcf5ecf011678bf86553c8abb08d526f60797ccd/zeebe/zb-db/docs/rdbms-storage-design.md),
with its decisions in
[ADR 0015](https://github.com/camunda/camunda/blob/bcf5ecf011678bf86553c8abb08d526f60797ccd/zeebe/docs/adr/0015-811-shared-rdbms-partition-state.md).
If we agree on this design, it replaces ADR 0015's D7 (own Liquibase and MyBatis setup) and D8
(one key-value table). Section 14 lists what changes in the code.

## 1. Goal and scope

Store a partition's runtime state in a relational database, behind the
[write-behind cache](write-behind-cache-design.md), with two changes from version 1:

1. **One database.** The state lives in the database the secondary storage (history) already uses,
   with the same settings, the same connection details and the same Liquibase schema management.
   RDBMS state therefore needs RDBMS secondary storage.
2. **One table per column family**, with a few extra columns that make the data easier to query
   and to run, instead of one key-value table for everything.

What stays from version 1: one shared state per partition; only the leader reads and writes it,
behind the write-behind cache; fencing by term; the commit gate; followers without a `ZeebeDb`,
taking marker snapshots; the recovery check (ADR D5); backups turned off for now (ADR D6 stays the
plan).

The new columns serve the engine's own reads, and operations and support work (finding data with
SQL instead of zdb). Reads of runtime state by other components, such as the REST API, are out of
scope, but the table model leaves room for them (§13).

Also out of scope: vendors other than H2 and PostgreSQL in the first version (§11), partition
scaling, backups and restore.

## 2. How it fits

```
StreamProcessor / ExporterDirector / ...        (unchanged, typed ZeebeDb API)
        │
WriteBehindZeebeDb (zeebe-db-cache)             reads: cache, then storage on a miss
        │  writer: committed transactions,      writes: after the Raft commit, as raw batches
        │  in commit order, in groups           that carry the log position (§8)
RdbmsZeebeDb (zeebe-db-rdbms)                   one shared state per partition, fenced by term
        │  one table per column family          MyBatis, our own connections
        │
State pool ──┐
             ├── the secondary storage's database, per physical tenant
History pool ┘   (same url, user, prefix; schema by one Liquibase manager)
```

## 3. Configuration

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
- The broker fails to start when the vendor isn't supported for the state yet (§11), and names the
  vendor.

## 4. Connections

The state reaches the database through the same `RdbmsDataSources` bean as the history, one per
physical tenant. Whether it gets its own pool is a decision to make:

- **A. Share the tenant's pool.** Nothing new to build or size. But busy REST queries or a slow
  exporter can starve the state writer; its cache then fills up and slows down processing.
- **B. A second pool, built from the same settings** (recommended). The writer always gets a
  connection; we can set the isolation level and driver options the state needs (§7) without
  touching the history; and separate pool metrics show who uses the connections. It costs twice
  the connections to the database.

With B, `RdbmsDataSources` creates the second pool only for tenants whose state is in the RDBMS. It
uses the same `connection-pool.*` values and adds no new settings; the benchmark decides whether
the state needs its own size.

The state's pool sets `READ COMMITTED` explicitly. The history pool leaves the database default,
which is `REPEATABLE READ` on MySQL; the `ZeebeDb` contract (`NoSnapshotIsolationTest`) needs reads
that see other transactions' commits. Auto-commit is off in both pools.

## 5. Schema

### 5.1 One Liquibase manager

- The state's changelog, `db/changelog/zeebe-state/changelog-master.xml`, lives in
  `db/rdbms-schema`, next to the history's changelog. The Zeebe team owns those files (CODEOWNERS).
- `LiquibaseSchemaManager` runs it right after the history changelog, the same way it already runs
  `schema-version-seed.xml` before it: under the same JVM-wide lock (#61009), with the same tracking
  tables (`${prefix}DATABASECHANGELOG`, `${prefix}DATABASECHANGELOGLOCK`), prefix context,
  parameters, retries, `ddl-lock-wait-timeout` and schema version check.
- It runs only for tenants whose state is in the RDBMS, so a cluster with RocksDB state doesn't get
  ~150 empty tables. Turning RDBMS state on later applies it at the next start.
- With `auto-ddl: false`, `LiquibaseScriptGenerator` writes a separate state script per vendor,
  next to the history scripts, for the database administrators to run.
- `RdbmsSchemaManagerRegistry.isInitialized(tenant)` reports a tenant as ready only once both
  changelogs have run. Partitions wait for it (§9).

This removes version 1's own `StateSchemaManager`, its own tracking tables, and the risk of two
Liquibase runs overlapping in one JVM (version 1 §11 risk 5).

### 5.2 Changesets

- One changeset per table, in one file per release (`changesets/8.11.0.xml`), using `${prefix}`,
  types per vendor through `<property dbms="…">`, and `preConditions onFail="MARK_RAN"`, as the
  history does.
- A small developer tool generates the changesets from the table registry (§6.1), and we commit its
  output so that it's reviewed like any changelog. A test fails when the committed changelog and
  the registry differ, and says what to add.

## 6. Tables

### 6.1 The table registry

`StateTables`, in `zeebe-db-rdbms`, maps each column family to its table: a short name and the
layout of its key (§6.4). It is the single source for the changelog generator, the mapper and the
writer's decoding. A test fails when a column family in `ZbColumnFamilies` is missing from the
registry, and says what to add.

### 6.2 Names

Tables are called `${prefix}ZS_<SHORT_NAME>`, with an explicit short name of at most 30 characters
per column family, for example `ZS_JOB_DEADLINES` or `ZS_DRD_KEY_BY_ID_AND_VERSION`. Constraints
follow the table: `${prefix}ZS_<SHORT_NAME>_PK`.

The column families' own names don't fit: 20 of them are longer than 40 characters, the longest
has 68 (`DMN_DECISION_REQUIREMENTS_KEY_BY_DECISION_REQUIREMENT_ID_AND_VERSION`), and the prefix
counts too. PostgreSQL silently cuts names after 63 bytes, so two long names can end up as the same
table; MySQL allows 64 characters. A test checks the length, and that the names are unique and don't
clash with `ZS_PARTITION` or `ZS_OTHER`.

`ZS_` keeps the state tables apart from the history tables in the same schema and lists them
together.

### 6.3 Which column families get a table

- **Each column family in use**: 144 today, 98 local to a partition and 46 global.
- **`DEFAULT`** gets `ZS_DEFAULT`, without decoded key columns: four state classes share it with
  different key shapes.
- **Deprecated column families** (25 today) and **any other enum** go into one fallback table,
  `ZS_OTHER`, which keeps a `COLUMN_FAMILY` column in its primary key, like version 1's single
  table. A new RDBMS state
  never holds old data, but deprecated column families are still read by migrations, and some still
  get writes: `DbJobState` drains the deprecated `JOB_ACTIVATABLE`. The contract test suites use
  enums of their own (§10). A metric counts rows written to `ZS_OTHER`.
- **Global column families** (deployments, identity, …) are stored per partition, as with RocksDB:
  every partition keeps its own copy, filled by command distribution. Sharing one copy is an idea
  for later (§13).

### 6.4 Columns

|       Column        |   Type (H2 / PostgreSQL)   |                             Notes                             |
|---------------------|----------------------------|---------------------------------------------------------------|
| `PARTITION_ID`      | `INT`                      |                                                               |
| `STATE_KEY`         | `VARBINARY` / `BYTEA`      | the serialized key, as the engine writes it                   |
| `STATE_VALUE`       | `VARBINARY` / `BYTEA`      | `NOT NULL`, may be empty                                      |
| `CREATED_POSITION`  | `BIGINT`                   | set when the row is inserted, never updated                   |
| `UPDATED_POSITION`  | `BIGINT`                   | set on every write                                            |
| decoded key columns | `BIGINT`, `INT`, `VARCHAR` | nullable, one per part of the key, set on insert only (below) |

Primary key `(PARTITION_ID, STATE_KEY)`. There's no partition group column: each physical tenant
has its own database or prefix, as for its history tables (§15 question 3).

**Positions.** A row's position is the log's last written position when the cache transaction that
wrote this version committed (`CommittedTransaction#position`). For the stream processor, that's
the last record it wrote while processing the command. It tells which part of the log created or
last changed a row, without zdb.

**Decoded key columns.** The registry describes each key as a list of parts: `LONG`, `INT`,
`SHORT`, `STRING`, a tenant id (prefix or suffix, as `DbTenantAwareKey` places it), `NIL` and
`BYTES`. Each part that holds a value gets a column. For example:

|                   Column family                    |                                 Key                                  |                            Decoded columns                             |
|----------------------------------------------------|----------------------------------------------------------------------|------------------------------------------------------------------------|
| `ELEMENT_INSTANCE_KEY`                             | element instance key                                                 | `ELEMENT_INSTANCE_KEY`                                                 |
| `JOB_DEADLINES`                                    | deadline, job key                                                    | `DEADLINE`, `JOB_KEY`                                                  |
| `TIMER_DUE_DATES`                                  | due date, element instance key, timer key                            | `DUE_DATE`, `ELEMENT_INSTANCE_KEY`, `TIMER_KEY`                        |
| `MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY` | tenant (prefix), message name, correlation key, element instance key | `TENANT_ID`, `MESSAGE_NAME`, `CORRELATION_KEY`, `ELEMENT_INSTANCE_KEY` |

- The writer fills them when it inserts a row. They come from the key, so they never change, and an
  update doesn't touch them.
- They hold the stored value, not always what it means to the engine. Where the engine changes a
  value before storing it, as with the job priority (§6.6), the registry leaves that part out.
- Strings longer than the column are cut, the way the history cuts long strings. The columns are
  only for reading.
- When a key doesn't match its layout, its decoded columns stay empty, a metric counts it, and a
  rate-limited warning names the column family. The write goes through: `STATE_KEY` and
  `STATE_VALUE` are what the engine reads, so a wrong layout must never stop a partition. The
  engine's tests over the RDBMS check that nothing failed to decode (§10).
- Why the layouts are written down instead of read from the engine's typed keys at
  `createColumnFamily`: the tables and columns have to exist before a partition starts, created by
  Liquibase or by a database administrator from a script, so the layouts must be known when we
  build. Reading them at runtime can't add columns, can't handle `DEFAULT`'s mixed keys, and the
  storage behind the cache only sees raw bytes anyway.

Values (msgpack) are not decoded in this version (§13).

### 6.5 Indexes

- Only the primary key. The engine reads single keys and ordered key ranges within one partition,
  and the primary key serves both.
- Indexes on decoded columns are added when a named query needs them, each with its own changeset.
  Each one slows down writes to its table, and the hot tables get the most writes.
- Never index `UPDATED_POSITION` or `STATE_VALUE`. Most of the engine's writes update rows that
  already exist (element instances, jobs, variables). On PostgreSQL, an update that changes no
  indexed column can stay on the same page without touching any index (a "HOT" update). Indexing a
  column that changes on every write would end that.

### 6.6 Why the key stays binary

The engine relies on keys being ordered byte by byte as unsigned numbers (cache design §4.1), and
the cache merges its own changes with the storage's scans in that order. Typed key columns would
order rows differently:

- `DbJobState` stores `Integer.MAX_VALUE - priority`, and relies on negative priorities wrapping
  around to values from `0x80000000` up, which sort last when compared as unsigned bytes. As a
  signed `INT` column they would sort first, and jobs would be activated in another order.
- `DbString` writes the length before the text, so shorter strings come first. A `VARCHAR` column
  sorts by text.
- Text order also depends on each database's collation: MySQL's `utf8mb4_bin` ignores trailing
  spaces, and SQL Server compares UTF-16 code units.

So `STATE_KEY` stays the binary primary key, and the decoded columns are extra columns that the
engine never reads. The cache's `CacheKey` and its order don't change.

**Rejected alternative:** typed columns as the primary key, with an encoding per vendor that keeps
the order, and a comparator per column family in the cache. Every column family's layout would have
to match the engine's byte order exactly, RocksDB and the RDBMS could then disagree on order, and a
mistake would change the engine's behavior instead of only leaving a diagnostic column empty.

### 6.7 The partition table

`${prefix}ZS_PARTITION` holds one row per partition: `PARTITION_ID` (primary key), `STATE_ID` and
`OWNER_TERM`.

- `STATE_ID` is a random UUID created with the row. Markers name it, so a broker never continues
  from a marker on a state that was deleted and created again. Version 1 needed `MAX + 1` numbers
  and retries to allocate it; a UUID needs neither.
- `OWNER_TERM` works as in version 1 (§7.5 there): claimed when the leader attaches, and read with
  `FOR UPDATE` before each write transaction.

## 7. Statements

- One mapper, `StateMapper.xml`, next to its interface, not under `mapper/`: the history loads every
  `classpath*:mapper/*.xml` into its own factory.
- The table name is put into the SQL as `${table}`, and only ever comes from the registry, never
  from input.
- Per table: `get`, `exists`, `upsert` and `delete` (both batched), `scan` and `scanReverse` (key
  ranges, streamed), `count` and `isEmpty`. All filter on `PARTITION_ID = ?` first. The scan
  bounds work as in version 1 (§7.4 there).
- `upsert` keeps `CREATED_POSITION` and the decoded columns of an existing row:
  - PostgreSQL: `INSERT … ON CONFLICT (PARTITION_ID, STATE_KEY) DO UPDATE SET STATE_VALUE = …,
    UPDATED_POSITION = …`
  - H2: `MERGE INTO … USING … WHEN MATCHED THEN UPDATE SET … WHEN NOT MATCHED THEN INSERT …`.
    H2's `MERGE INTO … KEY (…)` from version 1 replaces the whole row.
- The state builds its own `SqlSessionFactory` from a plain MyBatis `Configuration`, on the state's
  data source, with the tenant's vendor properties and database id. It can't use the history's
  factories: they use Spring's managed transactions, which need a `DataSource`, while the state
  opens, commits and closes its connections itself (version 1 §7.2).
- **Prepared statements.** About 150 tables with a few statements each make many different SQL
  strings. The PostgreSQL driver keeps 256 prepared statements per connection by default. The hot
  tables are far fewer, but the state's pool raises `preparedStatementCacheQueries`, which is
  easier with its own pool (§4).

## 8. Changes in the write-behind cache

The cache's keys, their order and its in-memory values don't change. What changes is how its
writer hands writes to the storage.

**Positions.** When the writer merges a group of transactions into the newest version of each key,
it keeps the position of the transaction that wrote that version next to it. `CachedValue` itself
gets no new field, so cached entries use no more memory.

**Raw batch writes.** Today the writer goes through the storage's column families with
`RawKey`/`RawValue`, which has no room for a position and hides that the writes come sorted by
column family. A storage can now take them as a batch:

```java
/** Optional: a ZeebeDb that takes the write-behind cache's writes as raw bytes. */
public interface RawBatchWriter {
  /** Writes in one transaction. Sorted by column family, then by key; one write per key. */
  void write(List<RawWrite> writes);
}

/** A null value deletes the key. */
public record RawWrite(EnumValue columnFamily, byte[] key, byte @Nullable [] value, long position) {}
```

- It lives in `zeebe-db-api`. The RDBMS DB implements it; RocksDB doesn't need to, and the cache
  keeps using column families for a storage that doesn't.
- The RDBMS turns it into one batch of deletes and one batch of upserts per table, after the fence
  check, in one database transaction, as version 1 does with its write buffer.
- The rest stays: grouping, the commit gate, waiting before a snapshot, dropping unwritten
  transactions on close, and stopping on a fenced write.

## 9. Broker wiring and startup order

- The state's data source comes from Spring: `RdbmsDataSources`, per physical tenant (§4).
  `PartitionManagerImpl` no longer builds a pool.
- The broker gets a lookup per tenant that returns the data source, vendor, prefix and a future that
  completes when the schema is ready (§5.1). It's a small interface in `zeebe-db-rdbms`, so the
  broker doesn't depend on `db/rdbms`, and it's passed in the way `BrokerModuleConfiguration`
  already passes the RDBMS exporter's position suppliers and the schema initializer lookup.
- **No Spring cycle:** the `rdbmsSchemaInitializer` bean depends on the broker, so that it starts
  after it and its recovery check reads the broker's cluster configuration. The broker must
  therefore not depend on the initializer. It only depends on `RdbmsDataSources` and the registry
  (`LazyInitializedRdbmsSchemaRegistry`), which the initializer binds itself to later. Creating
  the broker bean only starts the broker; it doesn't wait for partitions.
- **Waiting for the schema:** the ZeebeDb transition step of a leader waits for the tenant's schema
  future without blocking its actor, then opens the DB. Meanwhile the partition is reported as
  starting, waiting for the state schema. The schema initializer keeps retrying with backoff, per
  tenant, as it does today.
- The `restore` profile doesn't create the schema initializer. That's fine while RDBMS state
  doesn't support backups.

## 10. Snapshots, recovery, backups and tests

- Snapshots and recovery work as in version 1 (§7.6, §9.5, §9.6 there). The marker's `version`
  becomes `2`, with `partitionId` and `stateId` (the UUID).
- Backups stay off. ADR D6 gets simpler: state and history live in one database, so one
  point-in-time restore brings both back to the same moment. Resetting the owner terms and lining
  up with the checkpoint still need their own design.
- Tests:
  - The contract suites run against `ZS_OTHER` through their own enums, and once more with a test
    registry that gives the test enums tables of their own, created by a test changelog, so the
    per-table path is covered by the same suites.
  - The engine's tests over the RDBMS (the broker tests from version 1 §9.8) also check that every
    key decoded and that no row went to `ZS_OTHER` except from deprecated column families.
  - A test checks that the committed changelog matches the registry (§5.2), and one that every
    column family in `ZbColumnFamilies` is in the registry (§6.1).

## 11. Vendors

The history supports six vendors. The state starts with two; the others need the work below, and
until then the broker refuses to start with RDBMS state on them (§3).

|     Vendor     |    Key type    |                                               Order and limits                                               | First version |
|----------------|----------------|--------------------------------------------------------------------------------------------------------------|---------------|
| H2             | `VARBINARY`    | unsigned bytes                                                                                               | yes           |
| PostgreSQL     | `BYTEA`        | unsigned bytes; an index entry holds about 2.7 KB (version 1 §11 risk 1)                                     | yes           |
| MySQL, MariaDB | `VARBINARY(n)` | unsigned bytes, no padding; a primary key holds at most 3072 bytes, so `n` is bounded                        | later         |
| Oracle         | `RAW(n)`       | at most 2000 bytes unless extended strings are on; an empty `RAW` is `NULL`                                  | later         |
| SQL Server     | `VARBINARY(n)` | ignores trailing `0x00` when comparing, so `0x01` equals `0x0100`; needs a key encoding that keeps the order | later         |

## 12. Risks and costs

1. **A new column family needs a schema change.** Today an engine developer adds an enum entry. Now
   they also add a registry entry and generate a changeset; the tests in §10 say what's missing.
   This cost stays for as long as the engine adds column families.
2. **Processing depends on the history database.** A database outage now stops processing as well
   as the history, and a failing history migration keeps partitions from starting. The state's
   writes are much more frequent than the history's, so the database must be sized for both.
3. **Many tables.** About 150 more tables per prefix in the history's schema.
4. **Write costs.** Decoding keys costs CPU on the writer thread, and the extra columns make rows
   bigger. The benchmark measures both against version 1's single table.
5. **Prepared statements** (§7) and **pool contention** if we share the pool (§4).
6. **Key length** and **vendor order** (§11).

What we gain, besides the decoded columns: small tables with their own statistics; per-table
settings for the tables that change most, such as fill factor and autovacuum for `ZS_JOB_DEADLINES`
or `ZS_TIMER_DUE_DATES`; sizes per column family straight from the database catalog; and deleting
a partition's state with one statement per table. For the engine's own reads we expect little
difference: a primary key lookup is fast in a big table too.

## 13. Later

- **Reads by other components:** decode chosen value properties (msgpack) into columns and indexes,
  extending the layouts, once a named use case needs them.
- **One copy of the global column families**, shared by all partitions.
- **Partition scaling** by copying a partition's global rows with `INSERT … SELECT`, instead of
  copying a snapshot.
- **The RDBMS exporter** writing history and its exporter position in one transaction, now that
  both live in one database.
- **PostgreSQL table partitioning** by `PARTITION_ID`, if tables get very large.

## 14. Compared with the proof of concept

|       Area       |                         Version 1 (proof of concept)                          |                             Version 2                              |
|------------------|-------------------------------------------------------------------------------|--------------------------------------------------------------------|
| Settings         | `camunda.data.primary-storage.rdbms.*`, any database                          | the secondary storage's settings; RDBMS secondary storage required |
| Connections      | a pool per partition group, built by `PartitionManagerImpl`                   | from `RdbmsDataSources` (§4)                                       |
| Schema           | own changelog in `zeebe-db-rdbms`, own tracking tables and lock               | changelog in `db/rdbms-schema`, run by `LiquibaseSchemaManager`    |
| Tables           | `ZEEBE_STATE_PARTITION` and one `ZEEBE_STATE` table                           | `ZS_PARTITION`, one table per column family, `ZS_OTHER`            |
| State id         | `MAX + 1` with retries, partition group column                                | random UUID, no partition group                                    |
| Cache to storage | through column families, without positions                                    | raw batches with positions (§8)                                    |
| Unchanged        | transactions, scans, fencing, markers, commit gate, followers, recovery check | same                                                               |

## 15. Open questions

1. **Pool:** a second pool from the same settings (recommended) or the tenant's pool (§4)?
2. **Ownership:** are the history team fine with the state's changelog in `db/rdbms-schema` and
   with `LiquibaseSchemaManager` running it (§5)?
3. **Physical tenants:** do we already stop two tenants from using the same database and prefix?
   The state relies on it, as the history does (§6.4).
4. **API:** is `RawBatchWriter` in `zeebe-db-api` acceptable (§8)?
5. **Table names:** `ZS_` and 30 characters (§6.2)?
6. **`CREATED_POSITION`:** worth its 8 bytes per row?

## 16. Implementation plan

Starting from the proof of concept. Each step is one commit, reviewed before the next.

1. **docs:** this design; ADR 0015 updated for D7 and D8.
2. **refactor:** RDBMS state requires RDBMS secondary storage and uses its settings; the data
   source comes from `RdbmsDataSources` and reaches the broker through the new lookup. Still one
   table and version 1's own Liquibase run.
3. **feat:** the state's changelog moves to `db/rdbms-schema` and `LiquibaseSchemaManager` runs it;
   partitions wait until the schema is ready.
4. **feat:** the table registry and changelog generator; one table per column family and
   `ZS_OTHER`; the mapper per table; the UUID state id; the contract suites with the test registry.
5. **feat:** raw batch writes with positions in the cache, and the position columns.
6. **feat:** decoded key columns, with the engine tests checking that every key decodes.
7. **test:** the PostgreSQL and broker tests on the new tables.
8. Benchmark against version 1's single table and against RocksDB, then decide.

