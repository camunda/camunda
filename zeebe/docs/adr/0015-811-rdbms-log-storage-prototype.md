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
term mismatch, and after a bounded retry (for brief network blips) for any other error.

**D7. Partitions wait for the database before installing the storage.** The RDBMS schema is created
after the broker starts, so the log storage transition step waits, without blocking the partition,
until the tenant's schema is ready (as the RDBMS exporter does). A leader's claim that fails on the
database is retried rather than failing the transition.

## Consequences

- Enabled with `camunda.experimental.rdbms-log-storage.enabled=true` alongside an RDBMS secondary
  storage; otherwise partitions keep the Atomix log storage.
- Snapshot labeling and log compaction are tied to Raft indexes (`AtomixRecordEntrySupplier`,
  `StateControllerImpl`) and need an RDBMS equivalent, e.g. deleting rows below the snapshot and
  exported positions.
- Failover is slower than with warm followers. Notifying followers (polling or `LISTEN/NOTIFY`) can
  be added later.
- `maxFragmentSize` bounds the row size and must fit the database's limits.
- Repository queries have no statement timeout yet, so a hung database call can still stall a
  partition.

