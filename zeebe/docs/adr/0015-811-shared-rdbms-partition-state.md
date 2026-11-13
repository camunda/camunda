# Keep one shared partition state in an RDBMS, behind the write-behind cache

**DRI**: Mustafa Dagher

**Status**: Proposed (8.11)

**Purpose**: Defines how a partition can keep its runtime state in a relational database instead of
RocksDB: one state per partition that every replica shares, kept correct across leader changes,
log compaction and backups.

**Audience**: Zeebe engineers working on zb-db, the stream processor, partition transitions,
snapshots or backups; operators who would run Zeebe on an RDBMS; and AI agents reasoning about where
a partition's state lives.

## Context

Each replica of a partition keeps its own copy of the state in RocksDB. The leader processes
commands and followers replay the log to keep their copy up to date. Snapshots copy the RocksDB
files and are used to compact the log, to bring a lagging follower up to date, and as the starting
point of a backup.

The write-behind cache ([design](../../zb-db/docs/write-behind-cache-design.md)) puts an in-memory
layer in front of any `ZeebeDb` and writes committed transactions to it in the background, in
commit order. This makes a slower storage possible: the stream processor no longer waits for it.
The history RDBMS (`db/rdbms`) already shows how we use Liquibase and MyBatis against H2, PostgreSQL
and other vendors.

An RDBMS is durable and replicated by itself, and is backed up by the infrastructure. Keeping one
copy of the state per replica in it would store the same data three times and still need full
copies for snapshots. So we keep one state per partition, and the replicas share it.

Today a replica's state may contain the effects of records that are not yet committed in Raft:
`ProcessingStateMachine` writes follow-up records and then commits its transaction without waiting
for the Raft commit. That is safe only because a replica that loses leadership throws its state
away. A shared state outlives the leader, so it must only ever contain committed effects.

## Decisions

**D1. The RDBMS is an optional primary storage, only behind the write-behind cache.**
- RocksDB stays the default. The RDBMS is opt-in per cluster, and always used through the cache: a
round trip per state access would be far too slow for the stream processor.
- A new module `zeebe/zb-db/rdbms` (`zeebe-db-rdbms`) implements the `ZeebeDb` API. The rest of the
engine doesn't know which storage it runs on.

**D2. A partition has one state in the RDBMS that all its replicas share.**
- The state is identified by the partition number, within the physical tenant's database and table
prefix, and has a random state id. Only the current leader reads and writes it, through its cache.
- Followers keep no state and don't replay the log. They keep their Raft log and take snapshots
(D5) so the log can be compacted.
- A replica that becomes leader attaches to the shared state, replays the log from the processed
position stored in that state, and starts processing. Its cache starts empty.

**D3. Only committed effects reach the RDBMS.**
- When the cache commits a transaction, it records the log's last written position. The writer
only writes a transaction to the RDBMS once Raft has committed that position.
- This covers processing, replay and exporter-position transactions alike, because every record
that can influence a transaction was written to the log before the transaction commits.
- The cost is that the RDBMS lags behind the leader by the Raft commit latency, on top of the
writer's delay. Uncommitted transactions stay in the cache and count toward its pending limit.

**D4. The Raft term fences writes from old leaders.**
- A row per partition holds the term of the leader that owns the state. A new leader claims it with
its term before it reads anything (`UPDATE … SET OWNER_TERM = :term WHERE OWNER_TERM <= :term`).
- Every write transaction starts with one update of that row that only matches its own term, and
records the log position the state stands at once the transaction commits
(`UPDATE … SET WRITTEN_POSITION = :position WHERE OWNER_TERM = :term`). No row updated means the
state is fenced. The update keeps the row locked until the commit, so a leader's claim waits for an
old leader's write in progress, and after the claim the old leader's writes fail.
- A fenced writer stops and fails its partition. What it didn't write is in the log and is replayed
by the new leader.

**D5. A snapshot is a marker, not a copy of the state.**
- `createSnapshot` writes one small file that names the shared state (partition, state id).
The snapshot store accepts it like any other snapshot; it only needs a non-empty directory.
- Before writing the marker, the cache waits until the writer has written everything committed up
to the snapshot. The shared state only moves forward, so its processed position stays at or above
the snapshot's from then on, and compacting the log up to the snapshot is safe.
- Followers take markers too, using the processed and exported positions they read from the shared
state, limited to what their own log contains.
- Installing a snapshot on a lagging follower transfers only the marker. Replicas never copy state.
- On recovery, the broker checks the shared state against the marker and the log: the processed
position must be at or above the marker's and within the log. Otherwise the state and log don't
belong together (for example, a database restored to the wrong point in time), and the partition
fails to start instead of replaying from the wrong place.

**D6. Backups combine an infrastructure database backup with Zeebe's log backup.**
- The infrastructure backs up the database (for example with point-in-time recovery). Zeebe's
backups keep holding the marker and the log segments, as today.
- A restore puts the database back to the time of the checkpoint and restores the log up to the
checkpoint. The recovery check from D5 validates each partition. The restore also resets the
ownership terms, because the restored log may start again at a lower term.

**D7. The state shares the history's database and schema management; MyBatis without Spring.**
- The state lives in the database of the RDBMS secondary storage and uses its settings, with a
second connection pool per physical tenant. RDBMS state therefore needs RDBMS secondary storage.
- Its changelog lives next to the history's in `db/rdbms-schema`, and the history's
`LiquibaseSchemaManager` runs it, under the same lock and tracking tables. Partitions wait until it
has run.
- MyBatis runs on a plain `Configuration`, with vendor-specific statements selected by database id
as in `db/rdbms`. The module owns its connections and transactions. A read outside a write runs in
auto-commit, so a cache miss is one round trip.
- H2 and PostgreSQL first. Other vendors need checks of key length limits and binary ordering.

**D8. One table per column family, with the binary key as primary key.**
- Each column family in use gets its own table, named from a registry of short names. Deprecated
and unknown column families share one fallback table.
- Rows are keyed by (partition, key), with the key as a binary column that the database orders byte
by byte, so scans return the same order as RocksDB. Each row also holds the log position that wrote
it.
- Nothing else is decoded when writing. Views, generated from key layouts in the registry, decode
the keys for people at read time.
- Transactions use the database's READ COMMITTED isolation, which is what `ZeebeDb` promises today.

## Alternatives considered

- **One state per replica in the RDBMS.** Keeps today's replication model, but stores every
  partition three times, and snapshots need full copies (a dump in RocksDB format, our own format,
  or `INSERT … SELECT` inside the database) that must also be sent to lagging followers.
- **The RDBMS without the cache.** Simpler, but every state access becomes a database round trip on
  the stream processor's thread.
- **The whole RocksDB state as a blob in the database.** Uses the database only as a file store; it
  doesn't remove any of the copying.
- **The history's MyBatis factories.** They use Spring's managed transactions, while the state
  opens, commits and closes its connections itself, and they load every `mapper/*.xml` on the
  classpath.
- **Own settings and Liquibase run for the state** (the proof of concept). A second set of settings
  for what is usually the same database, and two Liquibase runs that must not overlap in one JVM.
- **One key-value table for all column families** (the proof of concept). Simpler to create, but
  one huge table with mixed statistics, no size per column family, and rows nobody can read without
  zdb.
- **Decoded key columns filled by the writer.** Readable with plain SQL, but they cost every write,
  and a wrong key layout needs a backfill. Views give the same at read time.

## Consequences

- Followers need no CPU, memory or disk for state. A failover takes longer: the new leader starts
  with an empty cache and replays what the old leader had not yet written.
- The partition depends on the database. While the database is slow or down, the writer falls
  behind, the cache fills up and applies backpressure, and the log can't be compacted.
- `createSnapshot` no longer gives a point-in-time copy for this storage. Code that opens snapshot
  contents must handle or reject it: the recovery sanity check, partition scaling
  (`RocksDBSnapshotCopy`), zdb and the restore validator.
- Followers no longer replay or keep exporter positions, so the broker's partition transitions
  need a follower mode without a `ZeebeDb`.
- Under the Minimal External Dependencies principle, the RDBMS stays optional and RocksDB stays the
  default. It needs a Product Architecture review before it goes beyond a proof of concept.
- State and history share one database: one database backup covers both, and an outage stops both.
- A new column family needs a registry entry and a changeset. Until it has them, its rows go to the
  fallback table.

## Open questions

- **Restoring several partitions to one point in time.** Partitions reach a checkpoint at different
  times. One database restore time must be valid for all of them, which may need the log of the
  previous backup too.
- **Partition scaling** copies column families from partition 1's snapshot. With a shared state that
  could be an `INSERT … SELECT` inside the database. It is unsupported at first.
- **Liquibase's lock is JVM-wide** (#61009). Our migration and the history migration must not run at
  the same time in one JVM.
- **Removing the state of a deleted partition** or physical tenant.
- **Key length limits.** PostgreSQL limits a B-tree entry to about 2.7 KB, which long user-defined
  keys may exceed.

## Source

- [RDBMS storage for zb-db: design](../../zb-db/docs/rdbms-storage-design.md)
- [Write-behind state cache for zb-db: design](../../zb-db/docs/write-behind-cache-design.md)
- [Architecture principles](https://github.com/camunda/product-development/tree/main/architecture/principles)

