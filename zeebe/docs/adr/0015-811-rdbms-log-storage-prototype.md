# RDBMS log storage prototype: plug in below `LogStream` at `LogStorage`

**DRI**: John Montgomery

**Status**: Proposed (prototype)

**Purpose**: Record how we plan to prototype an RDBMS log storage backend for Zeebe.

**Audience**: Single Storage Workshop

## Context

We want to prototype writing the log stream to an RDBMS instead of Atomix/Raft.  The goal is to
evaluate how feasible this is.  We want to avoid having to make too many wide-ranging change at this
stage.

## Decision

**D1. Implement `LogStorage` and `LogStorageReader`.** `RdbmsLogStorage` and
`RdbmsLogStorageReader` would be replacements for Atomix classes.  Implementing at the `LogStream`
level (i.e. `Sequencer` et al) might be cleaner (conceptually) but would require more changes.

**D2. One row per appended batch, stored as opaque bytes.** A table keyed on
`(partition_id, low_position)` with `high_position`, `term`, and the serialized `SequencedBatch`.
This mirrors one Raft application entry, so the existing block decoding works as-is. Positions stay
assigned in memory by the `Sequencer`; the database never generates them.

**D3. Non-blocking, group-committed appends.** `append()` runs under the `Sequencer` lock, so it
copies the bytes and enqueues them. A single writer thread inserts many batches per transaction,
then fires `onWrite`/`onCommit` and the storage listeners in position order.

**D4. Raft keeps leader election; its term fences writes.** A new leader claims the partition by
raising its term in `LOG_PARTITION`. Each append transaction first locks that row and checks the
term, so a deposed leader's appends fail, and the `LOG_BLOCK` primary key rejects duplicate
positions.

**D5. Appended blocks are readable before they commit, as with Raft.** The leader keeps appended
blocks in memory until their transaction commits and serves them to uncommitted readers, so the
stream processor does not wait for each commit. Followers are not kept warm initially: a new leader
restores its snapshot and replays from the database.

**D6. Database failures make the leader step down.** Unlike with Atomix, Raft stays healthy when the
database fails, so the storage reports failures itself and the partition steps down: at once on a
term mismatch, and after retrying for up to 60 seconds for any other error, long enough to ride out
a database restart (e.g. its pod being rescheduled). A failed read counts as "nothing to read yet",
so log consumers wait rather than fail; a read failing while a partition prepared a transition
marked the partition dead.

**D7. The log lives in the primary storage database, next to the engine state.** It is enabled
by `camunda.data.primary-storage.type=rdbms`, which also moves the engine state to the RDBMS, and
shares that connection pool. Its tables (`CAM_RU_LOG_BLOCK`, `CAM_RU_LOG_PARTITION`) come from a
changelog of their own in the broker, migrated before the broker starts together with the engine
state's and tracked in the same Liquibase tables. Partitions therefore never wait for the schema. A
leader's claim that fails on the database is retried, without blocking the partition, rather than
failing the transition.

An earlier version stored the log in the RDBMS secondary storage, created by the exporter's
`LiquibaseSchemaManager` after the broker started. It moved once the engine state prototype brought
a primary storage database: the log is primary storage, it should not depend on the exporter's
configuration or schema, and one pool lets log and state later share a transaction.

**D8. SQL runs through MyBatis, in a session factory of its own on the primary storage pool.** The
log mapper lives in the broker, which the engine state module does not depend on, so it is not
loaded into the engine state's factory. Its statements are standard SQL, as the primary storage
supports only PostgreSQL and H2 for now. Committing state and log in one transaction would need a
shared session.

**D9. The writer lingers before committing.** After taking the first queued block, it waits up to
10 ms for more, trading that much latency for fewer, larger transactions. In load tests this cut the
transactions substantially (though still more than the Atomix baseline) and brought throughput in
line with the baseline, at 10 and 20 ms alike. Until the storage gets proper configuration, it is
set through `CAMUNDA_EXPERIMENTAL_RDBMSLOGSTORAGE_COMMITLINGERMS` (0 turns it off).

## Consequences

- Enabled with `camunda.data.primary-storage.type=rdbms` (plus `primary-storage.rdbms.url` etc.),
  which always moves the engine state too; otherwise partitions keep the Atomix log storage.
- Restarts work with log and engine state both in the RDBMS: the engine state keeps its rows and
  the position of the last command they contain, and a restarted partition replays the RDBMS log
  from its RocksDB snapshot without applying those changes to the database again.
  `RdbmsEngineStateRestartIT` covers this.
- A crash can still leave the two inconsistent. The stream processor reads log blocks before they
  commit (D5), while the engine state commits in its own transaction, so the database may record
  changes of commands whose log blocks never became durable. Committing a block and its state
  changes in one transaction would close this gap.
- The engine state refuses replicated partitions, as every replica would write into the one
  database, so with both enabled only replication factor 1 runs; the notes on followers below
  apply to the log storage on its own.
- One pool per broker, with rows keyed by partition id only, so physical tenants sharing partition
  ids would collide.
- Snapshots are taken, but with Raft index and term `0-0`, as the Raft log holds no records; a
  "No log entry for next snapshot position" warning accompanies every snapshot, and Raft cannot send
  snapshots to followers. Deleting `LOG_BLOCK` rows below the snapshot and exported positions is
  therefore unsafe until state or snapshots are shared between nodes, e.g. once state is in the
  RDBMS too.
- Zeebe backups do not contain the RDBMS log, so backup and restore do not work with the RDBMS
  primary storage.
- Without a commit linger, the writer commits about once per transaction time (~4.7 ms in load
  tests), so Postgres ran 10-100x more transactions than with Atomix, at much higher CPU; command
  responses and flow control slots wait for those commits.
- Failover is slower than with warm followers, and followers are not notified of new commits, so a
  follower's replay stops after what the database held when it started. Notifying followers
  (polling or `LISTEN/NOTIFY`) can be added later.
- `maxFragmentSize` bounds the row size and must fit the database's limits.
- Repository queries have no statement timeout yet, so a hung database call can still stall a
  partition.
- Only H2 and PostgreSQL are supported, as for the engine state.

