# ADR 0007 — A single durability concept: every partition is a self-contained shard

- Status: Accepted
- Date: 2026-07-09
- Scope: `event-bridge-streaming` runtime (`Task` SPI, commit protocol, `StreamRuntime` builder)
- Supersedes: the runtime-managed durability semantics of
  [ADR 0004](0004-actor-per-partition-processing.md) and
  [ADR 0005](0005-asynchronous-checkpointing.md) (their processing model, commit
  protocol and freeze/persist/complete phases are kept; the shared
  offset-store/transaction path and its persister thread are removed)

## Context

The runtime has carried **two** durability semantics, selected per task by
`Task.ownsDurability()`:

- **Owns** — each partition task is a self-contained shard: its own state backend, its
  own stored offset, its own atomic cut (`restore()` + `commit(long)` / `freezeCut`).
  Partitions commit fully in parallel on the sink IO pool; a shard with no local state
  rebuilds from the source start, which is what makes shards mobile across members
  (ADR 0002).
- **Runtime-managed** — tasks defer durability to one shared `OffsetStore`,
  `TransactionRunner` and set of pre-commit flushes owned by the runtime. Because those
  resources are shared across partitions, every durable write — frozen cuts, legacy
  suspended commits, final stop commits — had to be serialized through the
  `CutPersister`'s single writer thread, with coalescing bolted on to win back some of
  the lost parallelism.

Every production task — both analytics stages — owns its durability. The managed mode
has only ever had test users. Its costs are real and recurring: a second code path
through `Task`, `PartitionCommitter`, `PartitionActor` and the builder; a flag every
reader must branch on; a dedicated writer thread with its own lifecycle, coalescing
rules and shutdown ordering; and a second test matrix exercising machinery nothing
ships on.

The comparison that settles it is Kafka Streams. KS has **per-task state only** — there
is no cross-task shared store — and what looks like "managed" durability there is the
broker itself: compacted changelog topics plus committed consumer offsets, restored by
replay. Our pipeline is event-sourced on Zeebe's model instead: a causes-log
(`zeebe-records`) with **bounded retention**, authoritative **per-shard snapshots**, and
a deterministic fold from snapshot + log suffix. With bounded retention the snapshot is
not an optimization — it is the authoritative state, so it must have an owner. One
owner concept suffices, and the task is that owner.

## Decision

Delete the runtime-managed durability semantics entirely. **The single remaining
concept: every partition is a self-contained shard that commits itself; cuts always run
in parallel on the sink IO pool.**

Concretely removed:

- `Task.ownsDurability()`, `Task.checkpoint()`, `Task.preCommitFlush()`. `restore()`
  and `commit(long)` lose their "only when owning" caveats — they are now *the*
  contract: `commit(long)` is the synchronous cut (the stop path, and the whole commit
  for tasks without frozen-cut support), `freezeCut(long)` the asynchronous one.
- `StreamRuntime.Builder.offsetStore(...)`, `.transactionRunner(...)` and
  `.preCommitFlush(...)`, and the runtime-side `OffsetStore` restore/seek at startup.
  Baseline and resume offsets come exclusively from each task's `restore()` when its
  partition is materialized; a task that restored nothing rebuilds from the source
  start.
- The `OffsetStore` interface (nothing but the managed machinery used it).
- `CutPersister` — the writer thread, the coalescing, the exclusive-job segmentation,
  the late-submit fallback — and the `eb.streaming.cut.coalesced` meter.
- The managed branches in `PartitionCommitter` (`commitSuspended`, the shared-resource
  commit sequence) and `PartitionActor` (`beginLegacyCommit` / `finalizeStop` routing).

`TransactionRunner` stays: it is genuinely used at the store level — the aggregation
operators (`SegmentSealingAggregation`, `SegmentMergingAggregation`) run their shard's
checkpoint writes through it — and by the `StreamProcessor` durability seam below. Its
owner is the task's own state backend (`RocksDbStateStoreProvider::runInTransaction`),
never the runtime.

`StreamProcessor` remains the generic stage-composition task, but under the single
concept it must own its shard like everything else. It receives its per-partition
durability at construction through one small seam, `ShardDurability` — run a block in
the shard's transaction, read the restored offset, persist the offset — and implements
`restore()`, `commit(long)` and `freezeCut(long)` with it: the frozen cut's `persist()`
runs inside the injected transaction and lands the offset atomically with every stage's
frozen delta. Stages and their `checkpoint()`/freeze-persist-complete trio are
unchanged. This is the provisioning seam a future simple application uses; it is
deliberately tiny — no framework, no registry, no default implementation.

## Consequences

- One durability concept, one code path, one test matrix. The `ownsDurability` flag,
  both branches everywhere it was consulted, the persister thread and its shutdown
  ordering are gone.
- Untouched and unchanged: the freeze/persist/complete cut protocol (ADR 0005), the
  layered overlay stores, the read-only read context, the frozen outbox, pipelined
  publish, the chained (non-joining) source-offset ack, the cut metrics minus the
  coalesced counter, and merge-back retry semantics.
- Startup simplifies: the runtime no longer seeks to restored offsets before the source
  loop runs. Resume happens per shard at materialization — restore the baseline from
  the task, dedup the resume gap, or rebuild from the source start when the shard has
  no local state. A stateless task (default `restore()`/`commit`) therefore replays
  from the source start on every start, which is exactly what "no durable shard" means.
- **Named re-entry path.** If a many-tiny-partitions workload ever materializes where
  per-partition stores are too expensive, physical store sharing returns as an
  implementation detail *under* the single concept: one physical DB, per-partition
  column families, still one `ShardDurability` and one cut per partition — never as a
  second semantics, never re-serializing commits through a shared writer.

## Alternatives considered

- **Keep both modes.** Rejected: two durability semantics mean two test matrices and
  double reasoning at every commit-path change, while the managed path has no
  production user to keep it honest — an unused path only rots.
- **Make everything runtime-managed instead.** Rejected: a shared store and transaction
  force serialized commits (the `CutPersister` exists only to make that safe), kill
  shard mobility (state must move with the partition, per ADR 0002's
  rebuild-from-source handoff), and are incompatible with the standby-task design
  (ADR 0006), which promotes a per-shard replica — there is no per-shard replica of a
  shared store.
