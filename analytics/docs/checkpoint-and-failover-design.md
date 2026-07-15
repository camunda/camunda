# Checkpoint & failover design

Status: **design — to implement next.** Baseline commit: `3ba7ed6ecbc`.

This describes two increments for the streaming analytics pipeline:

- **Part A — single-backend atomic checkpoint** (the "Option 1" we agreed on): one RocksDB
  backend, one transaction per batch, one consumed offset. Gives a consistent recovery point and
  **same-node restart** with no replay.
- **Part B — cross-consumer failover**: a *different* consumer rebuilds state from the source and
  takes over, changelog-free.

Implement Part A first (it is the foundation for everything); Part B builds on it.

## Principles (recap)

- The source `zeebe-records` topic is Raft-replicated and retained by the bridge. **It is our
  recovery log** — analytics state is a deterministic projection of it, so we recover by replay,
  not by a changelog (no write amplification). See [[analytics-exactly-once-tier2]].
- The serving sink is an **idempotent full-value upsert by key** (`MERGE`/index-by-id). Re-emitting
  a cell converges; it never double-counts.
- A checkpoint is **per stage**, not global. We have one stage today (source → base projection →
  rollups, no shuffle). If a shuffle is ever added for an exact-holistic metric, the post-shuffle
  reducer is a second stage with its own checkpoint on the repartition topic — keep
  `SourceCoordinate` for that boundary even though Part A makes it unused intra-stage.

---

## Part A — single-backend atomic checkpoint

### Goal

Base-projection writes, rollup cell writes, and the consumed offset for a batch all commit in **one
RocksDB transaction**, so state and offset can never diverge. On crash/restart on the **same node**,
reopen the DB and resume from the stored offset — no replay, no dedup.

### One backend, column-family layout

Collapse the two providers (base projection + rollup state) into **one**
`RocksDbStateStoreProvider<AnalyticsColumnFamilies>`. Delete `RollupColumnFamilies`.

```
AnalyticsColumnFamilies:
  DEFAULT(0)
  INSTANCE_VARIABLES(1)   processInstanceKey            -> PersistedVariables
  CONSUMED_POSITION(2)    partitionId (DbInt)           -> position (DbLong)   // the single offset
  ELEMENT_START(3)        "instanceKey:elementId"       -> activationTime (+ source position, Part B)
  ROLLUP_CELLS(4)         rollupId ++ windowStart ++ codec(key) -> codec(acc)  // ALL rollups share this
```

**One shared cell CF for all rollups** (not one per metric — that cannot grow to user-created
datasets). Key = `rollupId(4B, big-endian) ++ windowStart(8B) ++ keyCodec(groupingKey)`. Each rollup
scans only its own cells via `prefixScan(rollupId)`. A new metric/dataset = a new `rollupId`, zero
schema change.

### Per-batch transaction (driver orchestration)

`WindowedAnalyticsPipeline.run()` wraps each poll batch in one transaction on the shared provider:

```java
final List<ZeebeRecord> batch = consumer.poll(MAX_RECORDS, POLL_TIMEOUT);
store.runInTransaction(() -> {
  for (ZeebeRecord r : batch) processor.process(r); // base proj reads+writes (read-your-writes in txn);
                                                     // rollups fold into heap `pending`
  processor.flush();                                 // rollups merge pending -> ROLLUP_CELLS (joins txn);
                                                     // then idempotent upsert to the JDBC sink
  checkpointConsumedPositions(batch);                // CONSUMED_POSITION = max offset per partition
});                                                  // <-- atomic commit of {base proj, cells, offset}
for (ZeebeRecord r : batch) consumer.commit(r).join(); // advance the coordinator AFTER local commit
```

Notes:
- ZeebeDb `runInTransaction` is reentrant: each `KeyValueStore.put` (which wraps its own
`runInTransaction`) joins the ambient batch transaction. Reads inside the txn see prior writes in
the same txn (read-your-writes) — required because record N+1 in a batch may read what record N
wrote (e.g. activate then complete the same element).
- **Sink upserts happen inside the txn block** but are JDBC, not part of the RocksDB txn. This is
intentional: if a sink upsert throws, the RocksDB txn aborts → the whole batch (incl. offset)
rolls back → clean retry. If the RocksDB commit fails after a sink upsert, replay re-upserts
(idempotent). Net: effectively all-or-nothing.

### Simplify the rollup

`DurableMaterializedRollup<F, K, ACC>` loses the offset/dedup/own-transaction it carries today
(those move to the single shared checkpoint). New constructor:

```java
DurableMaterializedRollup(
    int rollupId,
    AggregateFunction<F, ACC, ?> aggregate,
    KeySelector<F, K> keySelector,
    ToLongFunction<F> eventTime,
    TumblingWindows windows,
    long allowedLatenessMs,
    ResultSink<Windowed<K>, ACC> sink,
    KeyValueStore<DbBytes, DbBytes> cells,   // the shared ROLLUP_CELLS store
    Codec<K> keyCodec,
    Codec<ACC> accCodec)
```

Behavior:
- `accept(fact)`: `pending.merge(windowedKey, add(fact), merge)`; advance heap `maxEventTime`. **No
dedup** (single cut → no replay → not needed).
- `flush()`: for each `pending` cell → `mergeIntoDurableCell` (get+merge+put in `cells`, prefixed by
`rollupId`) → `sink.upsert(fullValue)`; clear pending; `finalizeClosedWindows()`.
- `finalizeClosedWindows()`: `cells.prefixScan(rollupId)`; for cells with `windowEnd <= maxEventTime
- lateness` → final `sink.upsert` + `cells.delete`.
- **No own transaction** — cell puts join the driver's ambient txn. **No offset/watermark
persistence**: `maxEventTime` is heap-only; after a restart it rebuilds from new facts. The sink
already holds each cell's latest value, so a not-yet-finalized window is still correct in the
sink; it just isn't evicted until the watermark advances again (bounded leak on idle partitions
only).

Remove the `SourceCoordinate` constructor arg and `appliedPosition`/offset code. **Keep
`SourceCoordinate.java`** in the library (cross-shuffle idempotency key, Part-B-of-the-future).

### Store changes (`StateBackedProjectionStore`)

Add, both delegating to the shared provider:

```java
KeyValueStore<DbBytes, DbBytes> rollupCells();          // provider.keyValueStore(ROLLUP_CELLS, new DbBytes(), new DbBytes())
void runInTransaction(Runnable operations);             // provider.runInTransaction(...)
```

Add `runInTransaction(Runnable)` to the `BaseProjectionStore` interface (in-memory provider
implements it too).

### RocksDB durability

Lean **WAL on** for the standalone consumer so a process crash replays minimally. WAL-off is also
correct (state+offset roll back together → replay from the source), but replays back to the last
memtable flush. (This is the knob Zeebe sets WAL-off + snapshots; we don't need snapshots — see
Part B / the snapshot note.)

### Same-node recovery

Reopen the DB → `consumedPositions()` is intact → `consumer.seek(position + 1)` → resume. Zero
replay, zero dedup, because the last committed batch's state and offset are the same atomic cut.

### Part A file checklist

- `AnalyticsColumnFamilies`: replace rollup CFs with single `ROLLUP_CELLS(4)`.
- delete `RollupColumnFamilies.java`.
- `DurableMaterializedRollup`: simplify (above); `DurableMaterializedRollupTest`: drop
  dedup/offset-recovery cases, keep materialize + finalize/evict + **cell durability across reopen**;
  add a `rollupId`-isolation case (two rollupIds in one CF don't collide).
- `StateBackedProjectionStore`: `rollupCells()` + `runInTransaction()`; `BaseProjectionStore`:
  `runInTransaction`.
- `StandaloneAnalyticsPipeline`: one provider; open `rollupCells()`; build region (`rollupId=1`) and
  heatmap (`rollupId=2`) rollups sharing it; drop the separate `rollupState`.
- `WindowedAnalyticsPipeline`: wrap the batch in `store.runInTransaction`; write the offset inside.
- delete `TransactionRunner.java` (unused once the rollup stops owning its txn).

### Part A tests

- rollup: materialize full value; finalize+evict; survive reopen (cells durable); two rollupIds
  isolated in one CF.
- store: `rollupCells` round-trip; `runInTransaction` atomicity (a throw rolls back all writes).
- (existing `ProcessExecutionProjectorTest` stays green — behavior unchanged.)

---

## Part B — cross-consumer failover

### Why Part A alone isn't enough

Local RocksDB is node-local; a *different* consumer can't read it. And two traps make naive takeover
wrong:

1. **Overwrite clobber** — a fresh consumer resuming from the *high* watermark with empty state
   rebuilds **partial** open windows and overwrites the complete values already in the sink.
2. **Base-projection in-flight state** — element starts / variables aren't in the sink, so a fresh
   consumer can't derive facts for instances that were mid-flight.

Both ⇒ a fresh consumer must **replay the source far enough back to rebuild every in-flight thing**.

### The low watermark

Define, **per source partition P**, the **low watermark** = the oldest position on P still feeding
any in-flight state (an open window cell, or an open element start). Replaying from there rebuilds
the base projection *and* the rollup cells; the idempotent sink reconciles.

**v1 (recommended) — no restore mode, accept transient eventual-consistency during failover.** Since
the sink is full-value-overwrite and the new consumer replays *all* of an open window's records, each
cell converges to the correct value as catch-up completes — it just dips transiently while
replaying (e.g. count 3 → 1 → 2 → 3). For analytics dashboards a brief "numbers recovering after
failover" window is acceptable. **Finalized windows are never clobbered**: their records are older
than the low watermark, so they are not replayed and their sink values are left untouched.

This means v1 needs **only the low watermark** — no high-watermark "start-emitting" gate, no
restore mode. (The strict, no-dip version adds: commit `(low, high)`, replay `low→high` in
**restore mode** suppressing sink writes, then go live at `high`. Defer unless a transient dip is
unacceptable.)

### Computing the low watermark — simple, conservative approximation

Exact per-cell/per-partition birth tracking is fiddly (a cell aggregates facts from many
partitions). Use a **time-based approximation** instead:

> `low[P]` = the source position on P whose event time is `currentMaxEventTime − (windowSize +
> allowedLateness + slack)`.

Any record in an open window is younger than `windowSize + lateness`, so its position is `≥ low[P]`
→ replaying from `low[P]` covers all open windows. Maintain a small per-partition ring of recent
`(eventTime, position)` samples and pick the position at/just-below the cutoff. Assumes in-flight
*element* durations also fit within that horizon (true for normal flows; document the caveat, and if
violated, fall back to exact birth tracking).

Exact alternative (if the approximation proves too coarse): store each element start's source
position in `ELEMENT_START`, and each cell's per-partition min contributing position; maintain a
per-partition min-heap of open birth positions; `low[P]` = heap min. More precise, more bookkeeping.

### Where the offsets live

- **Coordinator committed offset = the low watermark.** Any consumer taking over the partition
  resumes from `low` and rebuilds. No protocol change (one committed offset). The coordinator sees
  the consumer as lagging by ~one window-lifetime — fine, the source retains it; the epoch fence is
  independent.
- **Local high watermark stays in local `CONSUMED_POSITION`** (the consumer's real progress).
- **Seek logic on startup:**
  - local RocksDB present with a `CONSUMED_POSITION` → **same-node fast path**: seek to local high,
    no replay.
  - empty local state → **cold/failover path**: seek to the coordinator's committed `low`, replay,
    let the sink converge.

### Part B file checklist

- low-watermark tracker: per-partition `(eventTime → position)` ring + cutoff lookup (new small
  class, lives in the pipeline/driver).
- `WindowedAnalyticsPipeline`: feed each record's `(partition, eventTime, position)` to the tracker;
  on commit, commit `low[P]` to the coordinator (instead of the batch-max high); keep writing the
  **local** high to `CONSUMED_POSITION` in the txn (Part A).
- startup: detect empty-vs-present local state; choose seek source accordingly.
- (no rollup change; no restore mode in v1.)

### Part B tests

- low-watermark tracker: cutoff returns the right position; advances as event time grows.
- failover sim (single JVM, two store dirs): consumer A processes to offset X (sink has full open
  windows), "dies"; consumer B starts with **empty** local state, resumes from committed `low`,
  replays, and the sink **converges** to A's values; finalized windows untouched.

---

## Sink dependency (transactional vs. eventually-consistent)

The whole Part B above is **changelog-free replay**, which works for any sink. Two refinements by
sink type, for later:

- **Transactional RDBMS sink:** could instead co-commit `(low, high)` per partition *into the serving
  DB* in the same JDBC transaction as the cells, making the serving DB a shared checkpoint a fresh
  consumer reads directly — less replay. Optional optimization.
- **Elasticsearch (eventually consistent):** can't co-commit; replay-from-low (v1) is the fit. For
  strict no-dip, thread the member epoch into the index as an external version (zombie fence).

## Snapshots (explicitly out of scope for now)

We do **not** take Zeebe-style snapshots (transient dir → atomic move → checksum → named by offset).
We don't own/compact the source log, and replay-from-low is bounded by one window-lifetime. Snapshots
become worth it only if (a) warm failover must avoid replay, or (b) the replay horizon grows too
large — at which point reuse ZeebeDb's snapshot support and the pending→move install pattern.

## Sequencing

1. Part A (single-backend atomic checkpoint) — ~most of the work; fully testable on H2.
2. Part B (low-watermark + cold-start seek) — smaller; adds the failover story.
3. Defer: strict no-dip restore mode; transactional-sink co-commit; ES external-version fence;
   snapshots; per-dataset offsets (parked) — which reuse `rollupId` and per-dataset start offsets.

