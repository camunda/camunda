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

**D4. Raft keeps leader election; its term fences writes.** Inserts are conditional on the current
term, and the primary key rejects duplicate positions, so a deposed leader cannot fork the log.

**D5. Committed and uncommitted reads are the same.** Data is visible once the transaction commits.
Followers are not kept warm initially: a new leader restores its snapshot and replays from the
database.

## Consequences

- `PartitionTransitionContext` must use `LogStorage` instead of `AtomixLogStorage`.
- Snapshot labeling and log compaction are tied to Raft indexes (`AtomixRecordEntrySupplier`,
  `StateControllerImpl`) and need an RDBMS equivalent, e.g. deleting rows below the snapshot and
  exported positions.
- Failover is slower than with warm followers. Notifying followers (polling or `LISTEN/NOTIFY`) can
  be added later.
- `maxFragmentSize` bounds the row size and must fit the database's limits.
