# ADR 0005 — Asynchronous checkpointing: freeze on the processing thread, persist in the background

- Status: Proposed (accepted design; implementation in progress). Where this ADR describes
  persisting a cut in the runtime's shared transaction alongside a runtime-owned offset store,
  that path is superseded by [ADR 0007](0007-single-durability-concept.md): every cut persists
  through its own task's transaction. The freeze/persist/complete protocol itself is unchanged.
  Where this ADR describes the freeze as serializing the whole open buffer, that is amended by
  [ADR 0009](0009-state-changelog-on-compacted-topics.md): the freeze serializes only the cells
  folded into since the last completed cut, so a cut's delta is exact.
- Date: 2026-07-08
- Scope: `event-bridge-streaming` runtime (commit protocol, state stores, aggregation operators)

## Context

A partition's commit is one atomic cut: emit output, persist changed state and the consumed
offset in one transaction, then advance the source offset (ADR 0004). The commit already runs
on an IO executor, but the partition's actor is **suspended** for its entire duration
(`committing` gates folding), because the commit reads the task's live mutable state — the
dirty cache entries and the heap accumulators. Single-writer is preserved by stopping the
writer.

The pause therefore covers the serving-store flush (network), the state transaction
(`db.write`), and the source-offset advance. Under nominal load the dirty sets are small and
the pause is invisible; under backpressure the dirty working set grows (observed: tens of MB),
commits stretch, and every commit stalls the partition exactly when it can least afford it.
Records pile up in the partition queue until it backpressures the source.

## Decision

Split every checkpointable's `checkpoint()` into three phases, and shrink the actor suspension
to the first one:

1. **Freeze** — on the actor thread, at the commit barrier. Each checkpointable swaps its
   dirty working set into an immutable *frozen* snapshot and installs a fresh empty one. The
   offset reached at that instant and the dedup admission snapshot are captured at the same
   barrier — the frozen snapshots together with that offset are one consistent cut. Freezing
   is pointer swaps plus at most O(delta) serialization; folding resumes immediately after.
2. **Persist** — on the IO thread, which now exclusively owns the frozen snapshots: perform
   the produce-before-commit publishes, then write every frozen delta and the frozen offset in
   one transaction, then advance the source offset. The live state is never touched.
3. **Complete** — back on the actor thread. On success the frozen snapshot is *retired* (it is
   now redundant with the durable store); on failure it is *merged back* underneath the
   current dirty set (newer writes win), so the next freeze re-includes it and the cut is
   retried. Frozen state is never swapped back to active on success — that would re-persist
   already-durable entries on every future commit.

### Per-store representation

- **Byte-valued write-back caches** (`CachingKeyValueStore`) become layered like RocksDB's
  active/immutable memtable pair: an active overlay (dirty writes since the last freeze), a
  frozen overlay (read-only, being persisted), the clean LRU, then the delegate. Reads try the
  layers top-down; writes go only to the active overlay — a write to a frozen key shadows it
  rather than mutating it, so the IO thread always persists exactly the values the frozen
  offset covers. On retirement, frozen values demote to clean (delegate-backed, evictable) LRU
  entries, keeping hot keys warm.
- **Heap-authoritative aggregations** (`SegmentMergingAggregation`) need no read-path change:
  reads never touch the durable store at runtime. Their freeze steals the already-serialized
  changed-cell bytes (`flush()` runs at every commit barrier and maintains that map) plus the
  changed/evicted delta sets. Post-freeze folds mutate the live accumulators freely; the
  frozen bytes were captured before the barrier.

### Concurrency rules

- **At most one persist in flight per partition.** A due commit while one is persisting waits.
  Two frozen generations could contain the same key and would require strictly ordered
  application; the complexity is not worth it.
- **Backpressure**: active and frozen entries are both pinned (unevictable), so the byte budget
  spans both. If the active overlay exceeds budget while a persist is still running, processing
  enters a write stall until the persist completes — the same role `overCapacity()` plays
  today, now triggered only by a genuinely slow persist instead of by every commit.
- **Reads below the overlays must not share the write transaction context.** The actor
  thread's cache-miss reads fall through to the durable store while the IO thread holds the
  commit transaction open. Store reads therefore go through a dedicated read-only transaction
  context with committed-only visibility — which is exactly correct, because anything
  uncommitted lives in the heap overlays above the store.
- Offset bookkeeping decouples: `pending` keeps advancing while a persist is in flight; the cut
  commits the *frozen* offset, and the partition records it as committed only when the
  transaction lands.

## Consequences

- The processing pause per commit drops from the full durable write to a barrier of pointer
  swaps and O(delta) serialization.
- Crash semantics are unchanged: the durable store always holds a consistent cut
  (state + dedup + offset frozen at one barrier), and replay from the committed offset rebuilds
  everything after it. A crash mid-persist loses nothing that yesterday's design would have kept.
- Memory ceiling roughly doubles worst-case: active and frozen generations can each approach
  the budget.
- A failed persist retries as part of the next, larger cut (merge-back), rather than blocking in
  place.
- Graceful shutdown still ends with one synchronous freeze-persist-complete cycle, so the
  `checkpoint()` composition remains as the final-commit and test-facing path. (Since
  [ADR 0007](0007-single-durability-concept.md) that synchronous composition is the task's own
  `commit(long)`; the runtime-side `checkpoint()`/offset-store variant no longer exists.)

## Alternatives considered

- **Keep suspending, just batch better.** The state transaction already batches (the zeebe-db
  transaction is a write batch committed in one `db.write`); the pause is dominated by the
  serving flush and transaction commit as a whole, not by per-record round-trips. Batching
  cannot remove the suspension.
- **Copy-on-write accumulators instead of freeze-time serialization.** Deep-copying live
  accumulators on first post-freeze touch avoids the O(delta) serialization at the barrier,
  but the bytes are already produced by the commit-barrier `flush()` for the serving view, so
  freeze-time serialization is effectively free; copy-on-write adds complexity for no
  measured gain.
- **Multiple in-flight frozen generations.** Amortizes a slow store further, but requires
  ordered application of overlapping keys and multiplies the pinned-memory ceiling; rejected
  until evidence demands it.
