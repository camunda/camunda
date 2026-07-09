# Staged analytics aggregation — design reference

Status: **agreed target; building the per-writer-slot version now.** This is the reference for the
A staged stream-processing model: `source → Stage 1 (base projection + combiner) → facts
topic (shuffle) → Stage 2 (reduce) → serving sink`.

## Principles

- The event bridge (`zeebe-records`) is the **replayable changelog / WAL**. All state is a
  deterministic projection of it; recover by replay.
- **Single-writer**: every piece of aggregate state has exactly one writer (achieved by the shuffle
  partitioning on the aggregation key).
- **Combine before shuffle** (map-side pre-aggregation) so the shuffle carries *partials*, not
  per-instance facts — this preserves the per-source-partition parallelism OC gives us. Shipping
  per-instance facts would re-serialise every instance of a key onto one partition and throw the
  parallelism away.
- **Idempotent sink**; **O(1) reads** (one global cell per key/window/tier).
- The bridge has **no transactional/idempotent producer** → exactly-once is handled in Stage 2, not
  by the producer.

## Topology

```
zeebe-records (P source partitions)
  └─ STAGE 1  (per source partition; instances are partition-local ⇒ single-writer)
        base projection (write-back cache, one-RocksDB atomic cut)  → derive facts
        COMBINER: fold facts into windowed partials, keyed by (groupingKey, sourcePartition)
        emit each changed cell's CURRENT FULL partial (emit-after-checkpoint) to
        └─ FACTS TOPIC  (one generic topic; route by hash(aggId, groupingKey);
                         partial payload = aggId, groupingKey, windowStart, sourcePartition, acc)
              └─ STAGE 2  (per facts-partition; single-writer per key)
                    per-writer SLOTS: (aggId, key, window, sourcePartition) → acc  (overwrite)
                    global cell = merge over sourcePartition  → idempotent JDBC sink (O(1) read)
                    atomic cut { slots + facts-offset }
```

One generic facts topic, **routed by the grouping-key value** (not one topic per grouping key);
facts carry an `aggId` tag so Stage 2 routes each to the right aggregator. Different grouping keys
coexist on the topic — single-writer holds because a cell's partials all hash to one partition.

> **Superseded in part by [ADR 0009](adr/0009-single-writer-composite-cells.md):** the shuffle
> stream is now the **cube**, not the meter — one composite delta (all meters' accumulators) per
> `(cube, segment, cell)`, routed by `hash(cubeStreamId, groupingKey)`, so one Stage-2 task is the
> single writer of the entire serving row. The per-writer-slots model described below was replaced
> by segment deltas + `SegmentDedup` before this document was last revised; see the ADR for the
> current identity model.

## Exactly-once for partials — per-writer full-value slots (the model we build now)

The reduce is a **merge** of accumulators; merge is associative+commutative but **not idempotent**
(except HLL), so an at-least-once partial can't be merged twice. Solution: turn the non-idempotent
**merge** into an idempotent **overwrite**.

- Stage 1 emits, per changed `(aggId, key, window)`, its **current full cumulative** partial for that
  cell (fold of all that source partition's instances so far), tagged `sourcePartition`.
- Stage 2 keeps **one slot per writer**: `(aggId, key, window, sourcePartition) → acc`, and
  **overwrites** on each partial. Served value = **merge over a cell's slots**.
- **Idempotent + boundary-independent**: re-emitting a partial (Stage-1 crash) overwrites the same
  slot with an equal-or-newer deterministic value; Stage-2's own crash replays the facts topic from
  its committed offset and re-overwrites. **No source-position dedup needed.** Both dedup concerns
  collapse to "idempotent overwrite per writer" + the facts-offset.
- **Emit-after-checkpoint** (Stage 1): emit the partial reflecting the *checkpointed* combiner state,
  so re-emits are monotonic → slots only grow → no transient dip, converges exactly.

## Per-accumulator merge strategy (pluggable — this is the key to scaling)

Make the Stage-2 merge strategy pluggable per `aggId`:

| Accumulator | merge | strategy | large-P cost |
|---|---|---|---|
| count / sum | add | **incremental**: per-(cell,P) last value + `global += new−old` | P longs, O(1) — free |
| HLL distinct | max registers (**idempotent**) | **single cell, no slots**, merge partials directly | free |
| KLL quantiles | union (not invertible) | **per-writer slots** now → **segment deltas** at high P | see numbers |
| top-k / freq-items | add freqs (not invertible) | **per-writer slots** now → **segment deltas** at high P | see numbers |

## Slots vs segments — concrete numbers

Assumptions: **P = 128** source partitions; **KLL ≈ 3 KB**, **top-k ≈ 10 KB** (config-dependent);
**~5,000 open cells** (2,000 defs × ~2–3 open windows, single tier; ×4 with all tiers); keys widely
distributed (each cell fed by ~all P); **1,000,000 completions/sec**.

**Why P multiplies slots but not segments:** slots keep one *cumulative* accumulator **per writer**
per cell — the merge is non-invertible, so to recompute a cell's total when a writer's cumulative
grows you must **re-merge all P**, hence **keep all P** → P copies/cell. Segments merge each
*immutable delta* once into a **single running total** and discard it → **1 accumulator/cell**; the
only per-P state is a `lastMergedSegment` watermark that is **per Stage-2 task** (`P` longs, shared
across all its cells — NOT per cell), because a partition's deltas arrive in segment order so one
number covers every cell.

**Per cell:**

| | per-writer **slots** | **segment** deltas | ratio |
|---|---|---|---|
| KLL memory | 128 × 3 KB = **384 KB** | **3 KB** (+ P longs per *task*) | ~128× |
| top-k memory | 128 × 10 KB = **1,280 KB** | **10 KB** (+ P longs per *task*) | ~128× |
| merge/flush | re-merge all P slots (O(P)) | merge each delta once (O(1), running total) | — |

**Totals (5,000 cells):** KLL slots ≈ **1.9 GB** vs segments ≈ **15 MB** (+ ~1 KB/task); top-k slots
≈ **6.4 GB** vs ≈ **50 MB**. Over 32 Stage-2 consumers: KLL ~60 MB vs ~0.5 MB/consumer; top-k ~200 MB
vs ~1.5 MB (×4 for all tiers → slots hit multi-GB/consumer, segments stay tens of MB). **P is a
per-cell multiplier for slots; for segments it's only a tiny fixed per-task cost.**

**CPU/flush (1s), worst case all cells fed by all P:** slots = 5,000×128 = 640k merges/s → KLL ~2
cores, top-k ~10 cores (can't keep up). Segments merge each *arrived* delta once (typically p_avg≪P
→ ~fraction of a core), never re-merge at flush.

**Shuffle volume (the combiner):** per-instance would be 1M rec/s; combiner partials ≈
`instances-per-(cell,partition,flush)` → ~4–40× reduction at 1–5s flush, plus sparsity → tens of
thousands/s, not 1M/s.

**Crossover:** slots fine at **P ≤ ~8–16** (KLL ~24–48 KB/cell, top-k ~80–160 KB/cell). Around **P in
the tens**, sketch metrics cross to "segments or bust." Additive/HLL never need segments.

## Segment deltas — implementation (back-pocket for high-P KLL/top-k)

Two orthogonal coordinates: **segment** = source-position slice `s = pos/STRIDE` (per partition,
deterministic); **window** = event-time. Stage 1 folds the current segment into a small ephemeral
buffer and **seals** it (emit + clear) when position crosses a `STRIDE` boundary — a sealed segment
is immutable and deterministic (pure function of its position range + base state at its start).

- **Stage 1 delta** = `(aggId, key, window, P, s, acc)`; route by `hash(aggId,key)`; produce-then-checkpoint.
- **Stage 2**: `cellTotal: cell → running acc` (ONE per cell) + `segWatermark: sourcePartition →
  lastMergedSegment` (P longs/task). `onDelta`: if `s ≤ segWatermark[P]` skip (dup); else merge once
  into `cellTotal`, advance watermark (even if dropped-late by the closed-window guard).
- **Correctness**: deterministic segments + in-order per-facts-partition delivery + deterministic
  routing ⇒ each `(P,s)` merged exactly once. `factsOffset` covers Stage-2 crashes; `segWatermark`
  covers Stage-1 re-emits.
- **Cost**: 1 sketch + P longs per cell (vs P sketches); O(1) merge/delta.
- **Tradeoff**: can't emit a partial segment (would break determinism) ⇒ **latency = segment fill
  time** → great at high throughput, poor at low throughput. STRIDE trades latency vs overhead.

Why not node-local pre-aggregation (Flink's trick): our dedup needs a **stable** writer identity =
the **source partition**; a node isn't stable (rebalance). Segments keep stable per-partition dedup.
Hot single key: **salt** it into K sub-keys across the reduce fleet, merge on read.

## Finalize / replay determinism

- Window **assignment** is deterministic (by event-time); values never migrate windows by timing.
- Non-determinism is only **late-drop** (watermark depends on arrival order) → borderline-late data
  can change a window's finalized value within the lateness tolerance (bounded, self-converging).
  For bit-identical replay, use a **deterministic watermark** (per-partition punctuation / min).
- **Resurrecting a sealed window** on replay is prevented by the drop-closed-window guard backed by
  the **persisted watermark** (sealed stays sealed; late partials dropped, not re-slotted).
- **Cohorts** (SLA / no-incident) use the **`drained` predicate** — data-driven finalization (seal
  when `settled ≥ started`), which is replay-deterministic (no watermark timing race).

## Recovery / failover (deferred, but designed)

Per stage: local RocksDB (materialized) + input-offset (atomic cut) + **periodic snapshot** to
shared storage. Same-node restart → seek local offset, zero replay. Cross-node → restore snapshot +
replay input **tail** (Flink changelog-backend model; our source/facts topic *is* the changelog, so
no separate changelog to write — Flink pays +30% storage / 66–225% slower recovery for what we get
free). Both stages need snapshots (unbounded state age: Stage 1 long-running instances; Stage 2
all-time tiers). Snapshot interval = the RTO knob. No cross-stage coordination (topic is the boundary).

## Build order

1. **Now**: per-writer slots for all metrics, two stages, one facts topic (this doc). At current
   OC single-partition (P=1) slots are degenerate (1/cell) — correct and simple, scale-ready.
2. Then: bounded-LRU cache, async client, per-partition parallelism (Stage 1 sharding).
3. Then: snapshots/failover; segment-delta strategy for KLL/top-k when P grows into the tens; salting.
