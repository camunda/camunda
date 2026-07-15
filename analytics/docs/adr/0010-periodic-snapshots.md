# ADR 0010 — Periodic snapshots: balance-over-time for additive cubes

- Status: Accepted
- Date: 2026-07-10
- Scope: `analytics-model` (declaration + compiled model), `event-bridge-streaming`
  (finalization listener), `analytics-engine`/`analytics-pipeline` (SnapshotSampler),
  `analytics-serving` + both store backends (snapshot table, fenced writes, baseline/range
  reads), `analytics-query` (snapshot executor)
- Builds on: ADR 0009 (single-writer composite cells), the write fence (WriteVersion), frozen
  commit cuts

## Context

Level-style meters (active instances, open incidents — sums of signed deltas) serve two reads:
the current value (the total-style gauge) and the **value over time**. The chart is inherently
cumulative: every point depends on all history before it, so windowed net-change cells alone
answer it only by summing from the beginning of time — and deleting any old cell silently shifts
every later point. Counts tolerate cell retention (local damage); levels do not (global damage).

The industry's two answers: store **absolute samples** (Prometheus gauges, Kimball's periodic
snapshot fact table — self-contained points, retention is plain deletion) or **absorb before
deleting** (ClickHouse TTL GROUP BY). We adopt the first, as a first-class declared concept.

## Decision

1. **A sample is a fold of finalized finest windows** — the value at boundary `S` of the declared
   event-time grid is the cumulative merge of exactly the finest-window cells ending at-or-before
   `S`. Never a wall-clock snapshot of a live cell: commit timing is nondeterministic under
   replay, finalization is not. Replay re-derives identical rows (bounded late-drop caveat shared
   with all finalization); rows are idempotent keyed upserts.
2. **Fed by finalization, released by proof of completeness.** A new `FinalizationListener` on
   the merging aggregation delivers finalized cells (ascending window end) and the watermark. The
   per-cube `SnapshotSampler` folds them into a durable cumulative accumulator per key and holds
   one pending boundary, released when a later window of the key finalizes or the watermark
   passes it. Emission is **sparse** — only boundaries where the value changed; reads carry the
   last point forward.
3. **Snapshots are first-class in the model, absent from the wire.** Declared
   (`snapshots(everyMs)`), compiled (`CompiledSnapshots(everyMs, cellGroup)`), one runtime
   component — but no stream id, no shuffle presence, no dedup: a sample is a Stage-2-local
   derivation over already-deduped, already-finalized cells. Like tiers.
4. **A dedicated `dataset_<id>_snapshots` table** (per backend), not sentinel rows and not extra
   columns on the cells: transaction facts and periodic snapshots are different fact-table types
   (Kimball) — a column whose additivity depends on the row is the modeling smell this avoids.
   Same grain and meter columns as the cells table, values are cumulative absolutes, keyed
   `(key, sample_time)`, fenced with the same `WriteVersion` predicate, written through the same
   staged/frozen-cut writer.
5. **Additive meters only** (count/sum/level/execution_time/histogram/ratio — the pushdown
   family), rejected at compile time otherwise: the product need is balance-shaped meters, and a
   cumulative all-time sketch per key is unbounded in meaning and memory. Liftable later. The
   grid must be a multiple of the finest window.
6. **Reads are baseline + range + carry-forward.** A SNAPSHOT query (vs. the aggregate executor's
   flow queries) fetches per key the newest row at-or-before the range start (the opening
   balance) plus the sparse in-range points, and materialises a dense series — O(points +
   buckets×keys), never O(history). A key is absent before its first point; a baseline-only key
   is a flat line. Cross-key roll-up of these semi-additive values is a follow-up (sum across
   keys per aligned bucket only, never across time).

## Consequences

- Level charts need no cumulative summation at read, and snapshot retention/thinning is plain
  deletion of self-contained rows — **except each key's newest row, which must always survive**:
  it is the key's current value for every future baseline lookup. Any retention statement must
  carry that exception.
- Durable sampler state (accumulator + pending boundary per key) joins the Stage-2 atomic cut;
  a crash between finalization and cut replays the fold and re-emits identical rows.
- The document backend writes snapshot documents (per meter, own `_snapshots` index); its
  baseline/range reads are not implemented yet (clear `UnsupportedOperationException`) — v1
  reads are RDBMS.
- Write amplification: one row per changed key per sample boundary — opt-in per dataset, and
  sparse emission keeps silent keys free.

