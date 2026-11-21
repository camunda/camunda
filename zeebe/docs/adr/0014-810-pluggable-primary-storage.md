# Make the engine's primary storage pluggable, with SQLite as the first alternative

**DRI**: Stephan Epping

**Status**: Proposed (8.10)

**Purpose**: Defines the decision to make Zeebe's primary (engine state) storage swappable behind a
configuration flag, the module split and capability declaration that make it so, the schema mapping
for a relational backend, and the known risks. It is a proof-of-concept scope, not a performance
proposal.

**Audience**: Zeebe engineers working on `zeebe/zb-db`, snapshots, or engine state classes, and AI
agents reasoning about what the storage abstraction does and does not guarantee.

## Context

Engine state lives in RocksDB behind a key-value abstraction (`zeebe/zb-db`). The abstraction is
nearly clean already: no module outside `zeebe/zb-db` imports `org.rocksdb`. But it has never been
exercised by a second backend, so nobody knows whether it is a real seam or a RocksDB-shaped hole.

The strongest reason to try is index maintenance. 62 of the 146 live column families in
`ZbColumnFamilies` are hand-rolled secondary indexes (76 of 165 entries, counting deprecated ones).
`JOBS` alone has five (`JOBS_BY_PROCESS_INSTANCE`, `JOB_DEADLINES`, `JOB_BACKOFF`, `JOB_ACTIVATABLE`,
`JOB_ACTIVATABLE_BY_PRIORITY`), and `ELEMENT_INSTANCE_PARENT_CHILD` and
`ELEMENT_INSTANCE_CHILD_PARENT` are an index pair. Every insert, update and delete of a base record
must also write, rewrite or remove its index entries in the `Db*State` classes, and a missed update
is silent corruption. Roughly half the engine's storage layer is index maintenance written by hand.
A relational backend could let the database do that work. Operational benefits (`sqlite3`
inspectability, simpler local setup) are real but secondary.

This decision does not realise that benefit. It builds the seam that makes it reachable,
demonstrates the mechanism on a few column families, and documents what blocks the rest.

The longer-term direction of an external relational database with no Raft on the broker side is a
different engine architecture (state becomes shared and authoritative instead of derived, per-replica
and disposable), not a storage swap. This ADR does not try to accommodate it, but it names every
broker-side assumption about local, snapshottable storage so the size of that change is known.

## Decision

Make primary storage pluggable behind `camunda.data.primary-storage.type: rocksdb | sqlite`. RocksDB
stays the default. SQLite (`org.xerial:sqlite-jdbc`) is the first alternative. The setting is
broker-wide and immutable after first start; on partition open, state written by the other backend
fails fast with a clear message rather than silently creating an empty database.

### Module split

```
zeebe/zb-db            interfaces, shared Db* key types, schema/describe machinery
zeebe/zb-db-rocksdb    ZeebeTransactionDb, TransactionalColumnFamily, RocksDbInternal,
                       SnapshotOnlyDb, snapshot copy and file info provider, config, metrics
zeebe/zb-db-sqlite     SqliteZeebeDb, SqliteColumnFamily, SqliteTransaction, SchemaGenerator
```

`zeebe/zb-db` must compile with no RocksDB on its classpath. That turns "we believe this is a seam"
into a fact the compiler checks. The split forces out these leaks, in the order they will be hit:

|                                       Leak                                       |                                                       Resolution                                                        |
|----------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------|
| `ZeebeDb.getProperty(String)`, RocksDB property namespace                        | Delete; only the RocksDB metric exporter uses it                                                                        |
| `ZeebeDb.exportMetrics()`, documented as RocksDB metrics                         | Keep as a no-op default, implementation moves down                                                                      |
| `ZeebeDbFactory.createDb(File, boolean avoidFlush)`                              | Replace with `CloseMode { FLUSH, FAST }`                                                                                |
| `ZeebeDb.isEmpty(cf, ctx)`, exists because all logical CFs share one physical CF | With one table per CF it is `SELECT 1 FROM t LIMIT 1`                                                                   |
| `TransactionalColumnFamily:436` downcasts the transaction to `ZeebeTransaction`  | Each backend owns its `ColumnFamily` implementation; the downcast becomes backend-internal. This is the blocking change |
| `configuration/RocksDb.java` imports `RocksDbConfiguration`                      | Repoint to the new module                                                                                               |
| `RocksDbResourcesStep`, `BrokerStartupContext.get/setRocksDbResources`           | Generalise to `StorageResources`, provided by the backend                                                               |

### Capability declaration

`ZeebeDbFactory` declares `StorageCapabilities(fileSnapshots, ephemeralRuntime, scopedCopy)`. Both
shipped backends answer `true` to all three, so no broker behaviour changes. The point is that the
broker reads the capability and every assumption about local snapshottable storage sits behind a
named flag, at four sites: `StateControllerImpl`, `ZeebeDbPartitionTransitionStep`,
`AsyncSnapshotDirector` installation and `SnapshotApiHandlerTransitionStep`.

Documented but not fixed: processed and exported positions live in the snapshot directory name
(`FileBasedSnapshotId`) and `zeebe.metadata`. A backend without file snapshots must hold them in a
column family. This is the first thing that breaks in the external-storage direction.

### Schema: one column family, one table

`DbKey` gains `describe(KeyStructure)` (about 18 implementations; composite keys recurse,
`DbForeignKey` delegates, `DbNil` contributes nothing). `createColumnFamily` already receives a key
prototype, so DDL is generated at creation time and no schema is hand-authored. Each key component
becomes a typed column of a composite primary key, the value stays an opaque protocol blob:

```sql
CREATE TABLE IF NOT EXISTS cf_job_deadlines (
  c0 INTEGER NOT NULL, c1 INTEGER NOT NULL, v BLOB NOT NULL,
  PRIMARY KEY (c0, c1)
) WITHOUT ROWID;
```

No secondary indexes are needed for parity. `WITHOUT ROWID` clusters the table on the key columns in
declaration order, and RocksDB's byte-prefix scans are leading-prefix scans of those same columns.
`EXPLAIN QUERY PLAN` confirmed `SEARCH USING PRIMARY KEY` for `(c0,c1) >= (?,?)`,
`c0=? AND c1>=?`, `c0=?`, `COUNT(*) WHERE c0=?` and the reverse scan. One codegen constraint:
`c0=? AND (c0,c1)>=(?,?)` degrades to `c0=?` alone, so prefix-plus-cursor must be emitted as
`c0=? AND c1>=?`.

### Index collapse: why index writes cannot become no-ops

The appealing idea is that an index column family becomes a real SQLite index and the engine's
maintenance calls become no-ops. It does not work, because the indexed field is inside the opaque
value blob:

```java
// DbElementInstanceState:251-252
elementInstanceColumnFamily.insert(elementInstanceKey, instance);  // key=long, value=blob
parentChildColumnFamily.insert(parentChildKey, DbNil.INSTANCE);    // key=(parent,child)
```

The base table is `cf_element_instance_key(key INTEGER PRIMARY KEY, v BLOB)`. `parentKey` is not a
column, so SQLite cannot index it, and dropping the second write loses the information. This
generalises: an index column family indexes a non-key field of the base record, and non-key fields
are exactly what the blob contains.

What does work is promoting the index key into a real column of the base table
(`parent_key INTEGER` plus `CREATE INDEX ... (parent_key, key)`), turning the engine's index write
into `UPDATE ... SET parent_key=? WHERE key=?`. An index insert can never be a no-op, since it carries
the only copy of the value. An index delete can be a no-op only where the entry's lifetime equals the
base row's lifetime. That holds for `ELEMENT_INSTANCE_PARENT_CHILD`, and fails for every conditional
index (`JOB_DEADLINES`, `JOB_ACTIVATABLE`, `JOB_BACKOFF`).

Scope of the PoC: parity for all 146 live column families (index CFs included, one table each), plus
two or three collapsed demonstrators, `ELEMENT_INSTANCE_PARENT_CHILD` first. Collapsing the rest
needs a hand-written mapping per column family and is deferred. While RocksDB exists the engine must
keep making both calls, so no engine code is deleted; the full payoff arrives only when RocksDB is
retired.

### Transactions, durability, snapshots

- SQLite runs in `journal_mode=WAL` with one writer connection for the processing context and
  separate reader connections for scheduled-task contexts. A second-connection write returns
  `SQLITE_BUSY`, so scheduled-task contexts being read-only becomes an enforced invariant.
- `synchronous=OFF`, matching RocksDB's disabled WAL: the Raft log is the durability boundary and
  engine state is derived. An explicit `PreparedStatement` cache is required (146 tables times about
  eight statements times partitions).
- Snapshot is `VACUUM INTO '<dir>/state.db'`, a single file, so the flat-directory-of-immutable-files
  contract of the replication stack holds. Nothing in snapshot, replication or backup parses RocksDB
  file formats. There are no per-file checksums to reuse, so `SnapshotFileInfoProvider` falls back to
  CRC32C over the file.

### Ordering divergences

Two divergences from RocksDB's byte ordering must be handled, not assumed away.

- **Signed integers, confirmed divergent.** Big-endian two's-complement bytes sort negatives above
  positives, a SQLite `INTEGER` sorts them below:

  ```
  SQLite INTEGER : [MIN, -2147483648, -2, -1, 0, 1, 2, MAX]
  RocksDB BE-byte: [0, 1, 2, MAX, MIN, -2147483648, -2, -1]
  ```

  `invertedPriority = Integer.MAX_VALUE - priority` in `DbJobState` is the live suspect. Mitigation:
  audit every numeric key component for reachable negatives, bias-encode those, and require
  `describe()` to declare integer columns non-negative, asserted on write in development builds.

- **Strings.** `DbString` serialises as `[4-byte length][bytes]`, so RocksDB orders by length first
  (`"zz"` before `"aaa"`); SQLite orders by content. Only `PENDING_SECRET_REFERENCES` (cursor
  persisted across scheduler ticks) and `RESOURCES` (cursor resumed across engine records) are both
  order-sensitive and string-led. Both drain the same set in a different batch order, which is
  correct but changes the record stream. Separately, `DbString` uses the platform default charset
  (`DbString:48`); UTF-8 must be pinned at the boundary.

SQLite `BLOB` ordering is byte-identical to memcmp, so an opaque-blob primary key stays available per
column family if a key proves not safely typeable. A RocksDB snapshot can never be loaded into
SQLite, so mixed-backend clusters are impossible by construction.

### Iteration and deletion

The dominant idiom, `whileEqualPrefix` plus `deleteExisting` in one transaction (about 70 sites,
relied on by `MemoryBoundedColumnIteration`), is safe on SQLite: verified on 3.47.2, deleting the
row the cursor just visited visited 1000 of 1000 rows with 0 remaining, because B-tree cursors
restore position by key. Deleting ahead of the cursor silently skips rows (a 100-row table visited
50). The rule is: a visitor may delete keys at or behind the cursor, never ahead.

## Alternatives considered

- **Drop index writes and use real SQLite indexes.** Rejected, see above: the indexed field is in
  the value blob, so the information would be lost.
- **Decompose record values into relational columns up front.** Rejected for this scope. It is the
  route to full index collapse but is a rewrite of every state class, and the seam has to be proven
  first.
- **Opaque-blob composite keys everywhere.** Rejected as the default. It reproduces RocksDB ordering
  exactly but forgoes typed columns, so nothing is queryable or indexable. Kept as a per-column-family
  escape hatch.
- **Go straight to an external database.** Rejected. It changes the engine's architecture (shared,
  authoritative state, no Raft), not just its storage.

## Consequences

- `zeebe/zb-db` compiles without RocksDB, and the compiler enforces the boundary from then on.
- Engine-test ordering fallout from the two divergences is unsized and is the largest remaining
  unknown. Tests asserting exact record sequences will fail on SQLite.
- Cascading deletes into the same column family during iteration silently skip rows. The roughly 70
  `whileEqualPrefix` sites must be audited.
- Snapshot cost regresses. RocksDB checkpoints hard-link SST files and are nearly free; `VACUUM INTO`
  rewrites the whole database and recovery copies it back, roughly 2 s per GB. Acceptable for a PoC,
  a blocker for production.
- Write throughput will lose to RocksDB's LSM under write-heavy load. Measured on a trivial
  single-table spike with `synchronous=OFF` and one commit per record batch: 40 000 rows in 181 ms
  (about 221 000 rows/s, 11 000 commits/s), and `VACUUM INTO` of an 8.8 MB database in 16 ms. Treat
  these as an upper bound; the real workload has 146 tables and larger keys.
- `insert` gains real uniqueness enforcement on SQLite, where today the precondition is only checked
  when `enablePreconditions` is set (off by default).
- `sqlite-jdbc` bundles a native library, so escaping JNI is not a benefit this delivers.
- Testing: parameterise the `zb-db` suite over both factories, add a differential test that drives
  identical operations against both backends and asserts identical visit order, and add one CI job
  running `zeebe/engine` on SQLite. RocksDB stays the default everywhere.

## Source

- Design document: `docs/superpowers/specs/2026-10-12-pluggable-primary-storage-design.md` (local, not
  committed)

