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

**D7. Partitions wait for the database before installing the storage.** The RDBMS schema is created
after the broker starts, so the log storage transition step waits, without blocking the partition,
until the tenant's schema is ready (as the RDBMS exporter does). A leader's claim that fails on the
database is retried rather than failing the transition.

**D8. SQL runs through MyBatis, in the tenant's existing session factory.** The log mapper is loaded
with the other RDBMS mappers, so the log and the RDBMS state storage being built alongside it can
later share sessions, and commit state and log in one transaction.

**D9. The writer lingers before committing.** After taking the first queued block, it waits up to
10 ms for more, trading that much latency for fewer, larger transactions. In load tests this cut the
transactions substantially (though still more than the Atomix baseline) and brought throughput in
line with the baseline, at 10 and 20 ms alike. Until the storage gets proper configuration, it is
set through `CAMUNDA_EXPERIMENTAL_RDBMSLOGSTORAGE_COMMITLINGERMS` (0 turns it off).

## Consequences

- Enabled with `camunda.experimental.rdbms-log-storage.enabled=true` alongside an RDBMS secondary
  storage; otherwise partitions keep the Atomix log storage.
- Snapshots are taken, but with Raft index and term `0-0`, as the Raft log holds no records; a
  "No log entry for next snapshot position" warning accompanies every snapshot, and Raft cannot send
  snapshots to followers. Deleting `LOG_BLOCK` rows below the snapshot and exported positions is
  therefore unsafe until state or snapshots are shared between nodes, e.g. once state is in the
  RDBMS too.
- Zeebe backups do not contain the RDBMS log, so backup and restore do not work with the flag on.
- Without a commit linger, the writer commits about once per transaction time (~4.7 ms in load
  tests), so Postgres ran 10-100x more transactions than with Atomix, at much higher CPU; command
  responses and flow control slots wait for those commits.
- Failover is slower than with warm followers, and followers are not notified of new commits, so a
  follower's replay stops after what the database held when it started. Notifying followers
  (polling or `LISTEN/NOTIFY`) can be added later.
- `maxFragmentSize` bounds the row size and must fit the database's limits.
- Repository queries have no statement timeout yet, so a hung database call can still stall a
  partition.
- Only H2 and PostgreSQL have been run; MySQL/MariaDB, MSSQL and Oracle have not.

