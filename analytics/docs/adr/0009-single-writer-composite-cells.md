# ADR 0009 — The cube is the unit: single-writer composite cells

- Status: Accepted
- Date: 2026-07-09
- Scope: `analytics/analytics-model` (composite aggregate + codec, compiled model),
  `analytics/analytics-engine` + `analytics/analytics-pipeline` (both stage wirings),
  `analytics/analytics-serving` (writer SPI), `analytics/analytics-store-rdbms` +
  `analytics/analytics-store-document` (cell upsert)
- Builds on: the staged-aggregation design (Stage 1 → facts topic → Stage 2), ADR 0007
  (pre-fold dedup), `SegmentDedup` (per-stream `(segment, chunk)` admission)
- Supersedes: the per-meter stream/routing portions of `staged-aggregation-design.md`

## Context

A dataset's meters were compiled into **independent shuffle streams**: one
`CubeMeterProcessor` + `SegmentSealingAggregation` per `(meter, finest tier)`, each with its own
`aggId`, routed by `hash(aggId, dimensionKey)`, merged by its own per-tier
`SegmentMergingAggregation`s, and upserted **column-wise** into the shared serving row. Yet all
meters of a cube share the same fact type, activation gate, filters, and grain — they are one
logical aggregation wired as N physical streams, and the wiring is paid for everywhere:

- the gate (fact type + `admits` + filters) and the dimension-key encoding run **once per meter**
  per fact, not once per dataset;
- one serving row (the cell users read) has **multiple writers** — one Stage-2 task per meter —
  so rows can be torn (count from one state cut, p95 from another), concurrent writers contend on
  the same row lock, and any future write-fencing needs one version per column-set instead of one
  per row;
- `SegmentDedup` must be per-stream precisely because sparse and dense meters of the same source
  partition seal the same segment index in different flushes;
- the word *cell* means two things: a `CellDelta` carries one meter's value, a serving cell holds
  all of them.

The rule this violates: **the shuffle key must be the serving row's ownership prefix** —
`(dataset, dimension grain)`, with the rolled-up axes (window, tier) local to the owner and the
measured values (meters) as columns, never keys. This is how Flink (`AggregateFunction` with a
composite accumulator per query, keyed by the grain alone) and Spark (`groupBy(window,
key).agg(...)`, one reducer emits the whole row) place aggregation; Kafka Streams reaches the same
end by making each aggregation its own store and joining explicitly when a merged record is
wanted.

## Decision

### 1. One aggregation per cube, composite accumulator

Stage 1 wires **one** gate + fold per cube. The accumulator is a **composite**: an `Object[]` with
one slot per declared meter, in declaration order. `CompositeAggregateFunction` delegates
`add`/`merge`/`mergeInto` slot-wise to each meter's bound aggregate; `CompositeAccumulatorValue`
encodes `slotCount ++ [len ++ slotBytes]*`, delegating to each meter's accumulator codec. A
decoder tolerates fewer slots than declared (empty = identity), leaving room for the parked
meter-evolution work.

### 2. The shuffle stream is the cube

A sealed segment delta carries the **cube's** `streamId` and the composite payload; routing
`hash(streamId, dimensionKey)` is unchanged in code but now keys by the ownership prefix. All
windows and tiers of a `(cube, key)` therefore converge on one Stage-2 task — the **single
writer** of every serving row derived from them. `SegmentDedup` keying is unchanged
(`(sourcePartition, streamId)` → `(segment, chunk)`), but all meters of a cube now seal together
by construction, so the per-meter sparse/dense divergence it defended against no longer exists.

### 3. Tiers stay local to the owner

Stage 1 folds and ships only the cube's **finest** window; Stage 2 rolls each composite delta into
every tier (a coarser cell is the exact slot-wise merge of its finer deltas). Each `(cube, tier)`
keeps its own durable cell group in the shared cell store.

### 4. The serving write is one row, all columns

`DatasetWriter.upsertCell` takes the cell's **composite accumulator bytes** instead of one meter's
column; a backend writes all meter columns (pushdown-decomposed natives, or blob + finalized
scalar) in **one** idempotent upsert. Rows can no longer be torn, and a future write fence is one
monotone version per row. The document backend keeps its one-document-per-meter layout for now
(the writer fans the composite out into per-meter documents); collapsing to one document per cell
— where an external-version fence becomes a native ES/OS primitive — is deliberate follow-up work
on the document read path.

### Identity model after this change

`(datasetId, dimensionKey)` answers every question in the pipeline: which gate admits a fact, the
fold key, the delta route, the dedup stream, the merging owner, and the serving rows it may touch.
Meters are demoted to what they are conceptually — the arithmetic inside a cell and its serving
columns (`CompiledMeter` loses its `aggId`); the cube gains `streamId` and compiled `tiers` (each
with a stable durable-cell group id from the same `MeterIdRegistry`).

## Considered and rejected

- **Keep per-meter streams, split serving records per meter** (the Kafka-Streams shape): fixes
  fencing granularity but keeps N× gating/keying, per-meter dedup streams, torn multi-record
  cells on the read path, and N row/doc writes per cell per flush.
- **Route by `(dataset, key, window)`**: also single-writer per finest row, but tier rollup is a
  cross-window merge — the coarser tiers would regain multiple writers, recreating the problem
  one level up. Time axes never belong in the shuffle key.

## Consequences

- One gate evaluation and one key encoding per fact per **dataset** (was: per meter); one envelope
  per `(cube, segment)`; dimension keys encoded once per cell (was: once per meter); one serving
  upsert per cell (was: one per meter, with row-lock contention between writers).
- **Low-cardinality cubes lose meter-level spread**: a global-totals cube now merges on one
  Stage-2 task for all its meters. Bounded by the combiner (≤ one delta per source partition per
  flush per cell); the designed escape hatch for a genuinely hot key is salting it into K
  sub-keys and merging on read.
- **Meter-set evolution is a breaking layout change** (parked): adding a meter changes the
  composite layout. The codec's tolerant decode supports the forward-only path (Option B:
  append-only slots + per-slot activation); a generation cut with replay from the event bridge
  (Option A) is always available because the source is retained long-term. Neither is built yet;
  until one is, a meter-set change requires re-provisioning the dataset.
- The facts-topic payload, `CUBE_CELLS`/`OPEN_SEGMENT` layouts, and allocated `aggId` semantics
  change incompatibly — a one-time re-seed cut (fresh topics + state dirs), same precedent as the
  ADR 0007 frame change. Pre-GA, topics re-seedable, no dual-format support.
