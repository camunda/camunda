# Layered state store: log-first buffering of RocksDB writes

**DRI**: Roman Smirnov

**Status**: Proposed

**Purpose**: Defines the layered state store — an in-memory, log-first buffering layer in front of
RocksDB that decouples state persistence from the per-batch processing cadence while preserving the
stream processor's commit/rollback and recovery contracts.

**Audience**: Zeebe engineers working on `zb-db`, the stream platform, or state/snapshot handling,
and AI agents reasoning about state durability, recovery, or reader isolation.

## Context

Zeebe commits a RocksDB `WriteBatch` at the end of every processing batch. This is redundant work:
the log stream is already the write-ahead log, and crash recovery replays it from
`lastProcessedPosition`. RocksDB durability per batch buys nothing that the log does not already
guarantee — it only puts a synchronous storage write on the hot path.

The write workload makes this doubly wasteful. State writes are dominated by short-lived put/delete
churn: element instances, jobs, variables, and timers are created and deleted within a small window
of log positions. Most of what is written per batch is deleted again shortly after, yet each
intermediate version is pushed through the full LSM-style write path (memtable, WAL-equivalent
bookkeeping, compaction debt).

An additive implementation exists in `zeebe/zb-db` (`io.camunda.zeebe.db.layered`) that buffers
writes in memory and persists rarely; it is not yet wired into the broker. This ADR records the
architecture of that layer and the integration plan.

## Decision

**D1. A five-layer model: staging, active overlay, segment pipeline, read cache, RocksDB
delegate.** Writes land in a *staging* layer that is the per-batch rollback unit: commit promotes
staging into the *active* overlay, rollback discards it — whole-batch rollback semantics are
preserved exactly as with today's per-batch `WriteBatch`. The active overlay is periodically
*frozen*: a pointer swap plus flatten turns it into an immutable, watermark-stamped flat segment
appended to a *pipeline* of such segments. Segments are compacted in memory with newest-wins and
delete-absorption semantics (a delete that meets an in-memory put annihilates both), bounded by a
segment-count limit so read amplification stays bounded. Below the pipeline sits a clean
read-through LRU cache, and at the bottom the RocksDB *delegate* — the only durable layer. The
watermark stamped on each segment ties its contents to a log position, which is what makes the log
the sole recovery authority.

**D2. Persist rounds are single-flight and three-step; the recovery anchor drains with the data,
anchor-last.** A persist round moves drained segments to RocksDB in three steps: *prepare* on the
owner thread (select segments, build the drain set), *persist* on an IO thread, and *complete* on
the owner thread (drop drained segments, rotate reader views per D3). At most one round is in
flight. The recovery anchor (`lastProcessedPosition`, stored in the DEFAULT column family) is a
normal key in the drain set, not a separately-written marker. The persist step commits either one
atomic `WriteBatch` (inline drains) or a *paced* sequence of sub-batch slices, each its own
commit, spread across a configurable pacing window (Postgres checkpoint-spreading style); the
pacing deadline only shapes disk amplitude — correctness never depends on when a drained cut
lands. Slicing relaxes the all-or-nothing cut to the **anchor-last
invariant**: the anchor — and any entry designated as its carrier — rides only in the final slice,
so durable state may transiently run *ahead* of the anchor (re-drained idempotently, newest-wins),
but the anchor can never run ahead of the state it describes. The torn recovery states — replay
double-applying events already persisted, or holes where state persisted ahead of its anchor —
remain unrepresentable: replay from the persisted anchor always reconstructs the rest.

**D3. The owner thread reads live layers; every other reader gets an immutable ReadOnlyView.**
The stream processor's owner thread reads through staging → active → pipeline → delegate and sees
its own writes immediately. All other readers (async checkers, snapshot-adjacent consumers, future
secondary domains) receive a `ReadOnlyView`: the current pipeline segments plus a pinned RocksDB
snapshot. Views are rotated only when a persist round completes, under the invariant that a view's
pinned snapshot must predate the persist of every segment the view is missing — so a view never
observes a key both from a segment and from its persisted copy, and never misses a key that fell
between the two. Snapshots are reference-counted so one pinned snapshot can be distributed to many
concurrent readers and released when the last one closes.

**D4. Exact flushed flags: an unknown key costs one delegate point read at write time.** When a
write arrives for a key unknown to all in-memory layers, the store performs a single point read
against the delegate to learn whether the key exists there, and records the result as a *flushed*
flag on the entry. This makes delete-absorption sound without imposing a read-before-delete
contract on callers: a delete whose key was never flushed can annihilate entirely in memory,
while a delete of a flushed key must survive as a tombstone until drained. Without the flag, an
absorbed tombstone could silently skip the delete of an on-disk key and resurrect it on the next
read-through.

**D5. Scans are point-in-time: staging/active selections are copied at scan start.** An iteration
snapshots the relevant staging and active entries when the scan begins, so a visitor may mutate
state (including keys in the scanned range) without affecting the ongoing iteration. This is
*stricter* than the current `WriteBatchWithIndex` behavior, where writes made during iteration may
or may not become visible to the same scan. The divergence is deliberate and documented:
point-in-time semantics are easier to reason about, and no engine code depends on observing
mid-scan writes.

**D6. Integration is a decorator with ownership domains, rolled out in phases A→B→C.**
`LayeredZeebeDb` wraps the existing transactional `ZeebeDb`; nothing in the engine or platform is
rewritten against a new API. Write access is partitioned into *ownership domains*: a domain
registers its transaction context and gets the layered write path; every unregistered context
passes through to the delegate unchanged. The first domain is the engine (the stream processor's
context); exporters keep writing their runtime state through their own pass-through context.
Rollout:

- **Phase A** — engine domain only. Secondary consumers keep their current read path against the
  delegate; only the stream processor's writes are buffered.
- **Phase B** — all secondary readers consume `ReadOnlyView`s (D3). Their freshness changes from
  persist-cadence staleness to freeze-cadence staleness — strictly fresher, since freezes are more
  frequent than persist rounds.
- **Phase C** — a proper ownership-registry API, and the exporter runtime state as a second domain
  with an independent persist cadence. Independent cadences equal today's semantics, where engine
  and exporter state are already not atomically coupled.

Each phase flips one consumer class and gets its own green gate; no consumer changes behavior
implicitly.

## Alternatives considered

- **Per-entry MVCC sequence stamping.** Stamp every write with a sequence number and let readers
  pin a sequence. Rejected: it taxes every write to solve a many-concurrent-readers problem Zeebe
  does not have — there is exactly one slightly-stale reader class, which D3's view rotation
  serves without per-entry cost.
- **RocksDB `WriteBatch` savepoints for rollback.** Solves only the rollback half; every write
  still reaches RocksDB each batch, so there is no write elision and no churn annihilation.
- **Write-through for scanned column families only.** Buffer point-read CFs, write scanned CFs
  straight through, avoiding merged iteration. Kept as a fallback: it is expressible as a subset
  of the domain model (a domain whose CFs pass through), but it forfeits churn absorption exactly
  where churn is heaviest (element instances, timers).
- **Big-bang consolidation.** Wire all consumers onto the layered store at once. Rejected in
  favor of A→B→C: each consumer flip is separately verifiable, and a regression bisects to one
  phase instead of one release.

## Consequences

- No RocksDB write on the hot path; the processing batch commits by promoting an in-memory layer.
- Short-lived put/delete churn annihilates in memory and never reaches RocksDB, shrinking
  compaction debt in the delegate.
- Read amplification is bounded by the segment-count limit (D1); the read cache absorbs repeated
  delegate reads.
- The stream-processor contract is unchanged: per-batch commit/rollback semantics are preserved
  via the staging layer (D1), and recovery semantics via the atomic anchor cut (D2).
- **Log retention** must cover the un-persisted window: segments not yet persisted are
  recoverable only from the log, so retention is gated on the *persisted* anchor. The existing
  exported-position bound is already more conservative than this, so no new retention risk is
  introduced — but the coupling becomes load-bearing and must be asserted, not assumed.
- **Zeebe snapshots** must run a persist round first (hooked into the `StateControllerImpl` take
  callback), so the checkpointed RocksDB directory contains a prefix-consistent cut.
- The recovery replay window grows from one batch to the interval between spills. Crash recovery
  restores from snapshots (intermediate persists write a runtime directory a crash discards), so
  no *age*-based bound is needed — there is deliberately no periodic persist cadence. The window
  that matters is the in-place takeover replay window on a reused database, and it is bounded by
  *memory*: the buffer-pressure ladder over `maxBufferedBytes` (a paced round starts early at the
  start rung, an unpaced one at the flat-out rung) plus the pre-snapshot flush.
- Buffered state needs a **memory budget**; the ladder rungs and the per-store `overCapacity()`
  act as forced-persist triggers so the pipeline cannot grow unboundedly under sustained load.
  Reaching the full budget is surfaced (admission-pressure meter and warning); an admission
  slow-down into the log stream's flow control is a documented follow-up — no clean seam exists
  there yet.
- Async checker freshness becomes freeze-cadence (Phase B), decoupled from persist cadence.

## Reference implementation

Phases 1 and 2 of the store itself are complete in `zeebe/zb-db`
(`io.camunda.zeebe.db.layered`: layer model, segment pipeline, persist rounds, views, decorator
in `layered.zdb`), with the module's tests green. Broker wiring (Phase A of D6) is in place behind
the experimental `layeredState` flag. Phase B is partially rolled out: the timer due-date,
message-TTL and job-timeout checkers consume `ReadOnlyView`s pinned by a real RocksDB snapshot
source (`io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbPinnedSnapshotSource`), with views frozen
on demand right before each checker execution so no committed wake-up can be missed. There is
deliberately no periodic freeze cadence: the pre-execution barrier is the only freshness anyone
consumes, and freezing earlier forfeits the active overlay's free in-place overwrite absorption,
turning it into pipeline-merge work. The remaining secondary readers (query service,
position supplier, other engine schedulers) still read pass-through contexts — the scheduled ones
behind the sync drain barrier, the rest at persist-round freshness — correctness-safe, flipped in
a later step. All remaining drain-barrier consumers run at multi-second cadences, so
barrier-forced persist rounds are rare and the effective persist window is governed by memory
(the buffer-pressure ladder over `maxBufferedBytes`) and the snapshot cadence (the pre-snapshot
flush) — there is no periodic persist trigger; an age-based cadence bounds nothing an invariant
needs, because crash recovery restores from snapshots.

The persist step of D2 runs asynchronously: rounds are prepared and completed on the stream
processor's actor while the drain-and-commit runs on a per-partition io-bound actor, with the
pre-snapshot flush and the scheduled-task barrier awaiting in-flight rounds by chaining futures
(never blocking the actor). Round preparation is O(1) per store: the active overlay is swapped out
whole as a raw *captured tip* the drain walks directly on the IO actor — nothing is flattened on
the processing thread; the tip is materialized into a real segment only when a freeze barrier
needs a view-visible segment mid-round, or when the round fails. Captured bytes stay accounted in
`bufferedBytes` until the round completes — capacity signals keep seeing pinned heap while a drain
is in flight. The drain itself is paced per D2's anchor-last invariant: sub-batch slices of a
configurable minimum size spread across the configured `persistPacingWindow` (only ladder
start-rung rounds pace — flat-out-rung, pre-snapshot and scheduled-task rounds have a consumer
waiting and drain unpaced), with queued pipeline merges interleaving between slices, and a rung
observed mid-round expediting the pacer so the drain finishes flat-out under memory pressure. Two consequences of
slicing are deliberate: (1) a *direct* reader of committed RocksDB (pass-through contexts such as
the deprecated query path) can observe a torn intermediate cut while a round's slices land —
layered readers never can (the captured state keeps shadowing the delegate until completion), and
the pre-snapshot flush awaits full round completion so snapshots never checkpoint a torn cut; (2)
a successor taking over a reused database no longer aborts a predecessor's outstanding round — it
*completes it forward* (re-drains it from scratch, anchor in the final slice) during recovery,
restoring the full-cut invariant that partial slices may have suspended; aborting remains correct
only for single-batch drains (the exporter domain's inline rounds). The last-processed position
entry is designated as the anchor carrier at recovery, which is what pins it to a round's final
slice. Phase C is in place: the exporter director registers an `exporter`
ownership domain and drains its buffered positions on demand, on its own actor, mirroring the
engine domain's cadence-free design — the exporter domain participates in the same RocksDB
snapshot and recovery resumes exporting from the snapshotted positions (at-least-once re-export),
so intermediate persists buy nothing a crash can use. Two drains remain, matching exactly the
moments committed-state readers consume the positions: the initial entries drain immediately so
snapshot selection and log compaction never see an empty exporter column family while positions
are buffered, and the snapshot director flushes the exporter domain right before every transient
snapshot, so snapshot-index selection reads the current exporter reality instead of lagging one
snapshot behind (over-retaining log). The exporter flush is best-effort by design: a failed drain
leaves the committed positions lagging conservatively — retention only keeps more log — and must
not block the snapshot.
