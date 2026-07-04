# Analytics glossary

The vocabulary of the analytics pipeline and how the terms relate. Read top-to-bottom: it follows
a record's journey from the source log to a dashboard read.

## The pipeline in one line

```
source record → base projection → fact → (per dataset) subscribe + aggregate → shuffle → merge → serving store → dashboard
```

## Ingest

- **Source record** — a decoded Zeebe `Record` consumed from the event bridge, wrapped with the
  source coordinate it was read at (`SourceRecord`). The input to Stage 1.
- **Source coordinate** — the origin a fact is stamped with, used to deduplicate replays/re-emits
  downstream. (Today this is the event-bridge partition+offset; see the open note in
  `analytics-cutover` about moving it to the stable Zeebe `(partitionId, position)` under
  at-least-once delivery.)

## Base projection (Stage 1, first operator) — analytics-engine

- **Base projection** — the Model-A materialized read-model: current **entity rows** folded from
  source records, from which facts are derived (`AnalyticsBaseProjection`, a `Processor`). "A fact
  is a projection of the rows."
- **Entity row** — a materialized row queryable in its own right:
  - **ElementEntity** `{start, end, status, durationMs, isProcess, hadIncident, parentScope}` — one
    per element instance (the process instance is the root element).
  - **IncidentEntity** `{createMs, resolveMs, errorType}` — one per open incident.
  - **Variable instance** — one entry per `(scopeKey, name)` (zeebe-style, not a map blob).
- **Applier (`EventApplier`)** — folds one `(ValueType, Intent)` transition into the rows. The
  *sole* mutator of the projection; one class per transition.
- **Deriver (`FactDeriver`)** — reads the updated rows and forwards facts. Read-only.
- **apply → derive → evict** — the three ordered steps per record: apply (mutate the row), derive
  (read the row, emit facts), evict (drop terminal rows). Evict-after-emit bounds the rows.
- **Fact** — the base projection's uniform output: a `FactType`, a bag of named `fields`
  (dimensions + measures), an `eventTime`, and the source coordinate. The single unit every dataset
  consumes; variable dimensions resolve lazily (`var.*`).

## Datasets (what is declared) — analytics-model / analytics-serving

- **Dataset** — a declared analytics artifact. Two kinds:
  - **Cube** — a windowed, multi-dimensional **aggregation** over facts (`ActiveCube` /
    `CompiledDataset`): a grain + meters + windows.
  - **Table** — a **raw** (unaggregated) dataset (`ActiveTable` / `CompiledTable`): one row per
    fact, upserted by key (e.g. process-definition metadata). *Not* to be confused with the base
    projection.
- **Dimension** — a grouping attribute (a column read off a fact, e.g. `bpmnProcessId`,
  `var.region`).
- **Grain** — a cube's ordered set of dimensions — its group-by (`DimensionSchema`). A fact's grain
  value is a `DimensionKey`.
- **Meter** — a measure within a cube: an `AggregateFunction` (count / sum / avg / percentile /
  distinct / top-k …) over a **window**. Identified by an **aggId**.
- **Window** — the event-time bucket a meter aggregates within (e.g. tumbling 1m/1h/1d).
- **Subscription** — how a dataset consumes the fact stream: a *gate* of `factType` + forward-only
  activation (`admits(partition, position)`) + declared filters. Adding a dataset never touches the
  fold — every dataset is offered every fact and keeps the ones its gate passes.

## Aggregation & shuffle (Stage 1 → Stage 2) — analytics-engine / event-bridge-streaming

- **Cube-meter node (`CubeMeterProcessor`)** — the Stage-1 aggregate `Processor` node: gates the
  fact stream for one meter and folds passing facts into a **segment-sealing aggregation**.
- **Cell** — one aggregated bucket: `(aggId, grain key, window)`. One accumulator per cell.
- **Segment** — a fixed range of source positions. Stage 1 pre-aggregates a partition's stream into
  per-segment partials and **seals** a segment (emits its cells as immutable deltas) once it is
  complete. The `(sourcePartition, segment, chunk)` tuple is the shuffle dedup unit.
- **Shuffle** — repartitioning the sealed partial-aggregate deltas from Stage 1 to Stage 2 through
  the **facts topic**, keyed so every contribution to a cell converges on one Stage-2 owner.
- **CellDelta / ShuffleEnvelope / ShuffleEnvelopeCodec** — the substrate wire form of a sealed
  segment's deltas (`event-bridge-streaming`, domain-neutral).
- **aggId ↔ streamId** — the same id under two names at the L2/L1 boundary: **aggId** is the meter's
  id (analytics); **streamId** is the substrate's generic shuffle stream id. Mapped at the shuffle.
- **Merge node (`CubeMergeProcessor`)** — the Stage-2 `Processor` node: dedups each envelope,
  dispatches its deltas by `streamId` to the matching meter's **merging aggregation**, and converges
  the serving sink.

## Serving (read side) — analytics-serving + stores

- **Serving store** — the backend-neutral read store (RDBMS / Elasticsearch / OpenSearch) the merge
  node upserts final cells into, and the dashboard queries. Reached via `DatasetWriter` (write) and
  the query planner/executor (read).

## Runtime & durability — event-bridge-streaming

- **Processor / ProcessorTopology** — the operator SPI + the DAG of operators (the single `Stage`).
  The base projection, cube-meter, merge, and table-row nodes are all `Processor`s wired into a
  topology.
- **Owning task** — one per source/facts partition (`ProjectionStageTask`,
  `AggregationStageTask`): owns a per-partition RocksDB and drives a `ProcessorTopology`, committing
  its state and consumed offset as one atomic cut.
- **Model F** — the consistent-cut durability model: per partition, the topology's state (base
  projection rows, open segments, merged cells) and the *full* consumed offset commit atomically;
  recovery replays from the committed offset onto matching state (no `safeOffset`, no reconcile).
- **Produce-before-commit** — sealed shuffle deltas / serving rows are published *before* the offset
  commits; downstream origin-dedup makes a re-publish idempotent.

## How the layers map

| Layer | Module | Holds |
|---|---|---|
| L1 substrate | `event-bridge-streaming` | Processor/ProcessorTopology, windows, segment/dedup, shuffle codec, StreamRuntime, state stores, owning-Task durability |
| L2 engine | `analytics-engine` | base projection (appliers/derivers), meters, the cube-meter aggregate node — the domain operators |
| L2 model / serving | `analytics-model`, `analytics-serving` | facts, datasets (cube/table), dimensions, meters; the serving store SPIs + query planner |
| L3 app | `event-bridge-analytics` | declares the topologies, owns IO (publisher/transport, backends), runs the runtime |
