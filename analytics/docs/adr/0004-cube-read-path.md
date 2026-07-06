# ADR 0004 — Cube read path: prune, stream, and push down aggregation

- Status: Accepted
- Date: 2026-07-06
- Scope: `analytics/analytics-serving` (query planner/executor + read SPI),
  `analytics/analytics-model` (meter pushdown contract), `analytics/analytics-store-rdbms`,
  `analytics/analytics-store-document`, `analytics/analytics-webapp`
- Builds on: ADR 0001 (declarative datasets), ADR 0003 (engine layering / operator model)

## Context

A dashboard read runs through the serving layer: `DatasetQueryPlanner` picks a tier per meter and
emits one grain-level `DatasetFetch` per tier; `DatasetQueryClient` filters + fetches the raw
pre-aggregated **cells**; `DatasetQueryExecutor` rolls them up in the application — projects each
cell to the requested group-by, buckets its window at the requested granularity, `merge`s the
meter accumulators, and finalizes with `getResult`. Aggregation runs in **two places**: the
pipeline pre-aggregates facts into cells at write time (the heavy reduction), and the executor does
a final merge at read time.

This is correct and portable — the uniform app-merge is exact for every meter class (additive
counters and mergeable sketches alike), which is why the backend need only filter and fetch. But it
has three problems at scale:

1. **Unbounded fetch, capped or materialized.** The read fetches every cell in range (bounded by
   grain cardinality × number of windows, *not* by fact volume) into a `List` and merges it. On
   Elasticsearch/OpenSearch that fetch **caps at 10 000 hits and silently truncates** — a wide cube
   over a long range at a fine tier returns wrong numbers. On RDBMS it materializes the whole set.
2. **No range pruning.** The cell primary key is `cell_key = dims ++ window` — **dimensions first**.
   A time-range filter (`[fromMs, toMs)`, the common dashboard predicate) is therefore scattered
   across the whole key space, so RDBMS does a full table scan + filter; there is no index leading
   with time.
3. **No pushdown.** Even for additive meters the DB *could* `SUM`, we fetch every partial and merge
   in-app, because the accumulator is stored as one opaque blob the DB cannot aggregate.

Every mature analytical store solves the large aggregated read with the same four levers, in
descending impact:

- **Prune before you scan** — sort/partition by the query key + per-block min/max metadata, so a
  range query touches only relevant blocks (ClickHouse sort-key + sparse granule index; Druid
  time-partitioned segments; BigQuery partition + clustering + zone maps).
- **Push the reduction into the store** — the engine does `GROUP BY`/`SUM`/sketch-merge (ClickHouse
  `AggregateFunction` + `-Merge`, Druid mergeable sketch columns, BigQuery), or you pre-materialize
  the shape (Pinot star-tree, BigQuery materialized views, our cubes).
- **Mergeable partial state** — store partials the engine can merge. *We already have this* (encoded
  accumulators); the only difference is we merge in the app, because our backends are commodity
  stores, not OLAP engines that own the merge.
- **Bounded-memory paging** — keyset / `search_after` / composite-`after` cursors.

Streaming (the fourth lever) fixes the *cap and memory* but not the *scan volume*; pruning and
pushdown are where the speed is. All three are achievable on commodity RDBMS + ES/OS.

## Decision

Adopt a three-layer cube read architecture. Each layer is independently shippable and green; later
layers build on earlier ones.

### Layer A — Prune + stream (all meters, portable)

**Prune.** Add a time-leading secondary index to the cell table so range queries seek instead of
scanning:

```
INDEX dataset_<id> (window_size, window_start, <grain dims…>)
```

The index leads with the tier and the time range — the selective, always-present predicates — so
`(tier, [fromMs,toMs), filters)` becomes an index range scan. ES/OS already prune via the inverted
index on the filter terms, so this is primarily an RDBMS change.

**Stream.** Replace the materialized fetch with a keyset cursor on the read seam:

```
interface DatasetQueryClient {
  void streamCells(DatasetFetch fetch, Consumer<Cell> sink);   // key order, paged internally
  // fetchRows(TableFetch) stays; fetch(DatasetFetch)->List is dropped once the executor streams
}
```

- **RDBMS:** page by keyset over the index columns — `… WHERE window_size=? AND window_start ∈ [..]
  AND (window_start, cell_key) > (:lastWindow, :lastKey) ORDER BY window_start, cell_key LIMIT
  :page` — looping until a short page. Bounded memory, no cap, stable under concurrent writes
  (keyset, not `OFFSET`).
- **ES/OS:** `sort` + `search_after` (stateless — *not* `scroll`, which holds a server-side
  context). This requires the cell's sort key stored as an explicit **`keyword` field** (today it
  is only the document `_id`, which sorts poorly); add it to `DocumentDatasetWriter.upsertCell` and
  the index mapping.

**Streaming is an application-level concern behind the seam.** The `streamCells` contract takes a
`Consumer<Cell>` (not a returned `List`), so both adapters must emit and release one batch/page at a
time — never buffer the whole result. The *transport* differs: RDBMS is one query with a forward
JDBC cursor (`fetchSize`, MyBatis `Cursor<Cell>`); ES/OS is a client-driven `search_after` loop (N
stateless requests). The `DatasetQueryExecutor` consumes the stream identically in both cases,
merging each cell into its accumulator map as it arrives. Memory = one page + the merge map (sized
by *output* cardinality — group-by keys × buckets — not by input cells). Correct for any range size.

**ES/OS goes through the `search-client` abstraction.** All document-backend query needs —
`search_after`/`sort`, and the Layer-B `composite` aggregation with `date_histogram` sources and
`sum`/`min`/`max` sub-aggregations — are expressed via `io.camunda.search.clients` (`SearchQueryRequest`,
`SearchQueryBuilders`, the aggregator API). Where a capability is missing, it is **added to the
`search-client` modules** (`search-client-query-transformer` + the ES/OS transformers), never worked
around inside `analytics-store-document`. The document store composes the abstraction; it does not
bypass it with raw client calls.

### Layer B — Additive pushdown (per-meter strategy)

Split meters by whether their partial state is engine-aggregatable, and push the reduction down for
the ones that are.

**Meter contract.** A meter declares an optional pushdown capability:

```
// null  → not decomposable: store as a blob, stream + app-merge (Layer A)
// present → store as native numeric columns; the store can GROUP BY + aggregate them
record PushdownSpec(List<PushdownColumn> columns) {}   // e.g. count -> [count_ SUM]
record PushdownColumn(String suffix, DimensionType type, Agg agg) {}   // Agg = SUM | MIN | MAX
```

| Meter | Pushdown |
|---|---|
| count, sum, level | one column, `SUM` |
| max / min | one column, `MAX` / `MIN` |
| avg | two columns `sum_`,`count_`, both `SUM`; result = `SUM(sum_)/SUM(count_)` |
| percentile, distinct (HLL), top-k | **none** — sketch state, stays blob + app-merge |

**Storage.** A pushable meter is stored as its `PushdownColumn`s (native numeric columns). A sketch
meter is stored as its binary **blob** *plus* a finalized **`<meter>_value`** column (a cheap
denormalized `getResult` of that cell's own sketch — ~8 bytes). The schema manager and writer branch
on the spec. Blob placement: **same table + strict column projection** by default — Postgres TOASTs
values > ~2 KB out-of-line, and reads that don't need the sketch never select it; split to a
`dataset_<id>_sketch(cell_key FK, meter, blob)` side table only when sketch-state **retention
diverges** from the finalized values (keep `_value` long, expire blobs sooner). Total size is
governed by Layer C (partition drop) + tier downsampling, not by the blob's location.

**Three read strategies.** The planner already chooses a *tier* per meter; it now also chooses a
*strategy* per meter, from the query's granularity and group-by:

- **`DIRECT`** — when the read is **1:1 with cells** (granularity == the tier's window *and*
  group-by == the full grain): each cell is exactly one output row, so no aggregation is needed.
  Fetch the stored column — the additive column *or* the sketch's `<meter>_value`. One query, no
  merge, no blob on the wire.
- **`PUSH_DOWN`** — additive meter that rolls up (coarser granularity or dropped dimensions): the DB
  aggregates. The output bucket is a *derived* group key `bucket = window_start − (window_start mod
  granularity)`, so an arbitrary granularity (e.g. 5m over a 1m tier) is just another `GROUP BY`.
- **`STREAM_MERGE`** — sketch meter that rolls up: the only path that streams (Layer A). Cannot use
  `<meter>_value` (finalized percentiles are not combinable) — it streams the blobs and merges.

Meters sharing a (tier, strategy) share one fetch.

**Backends.**
- **RDBMS:** `SELECT dims, window_start − MOD(window_start, :g) AS bucket, SUM(count_), MAX(max_) …
  WHERE window_size=? AND window_start ∈ [..] AND filters GROUP BY dims, bucket` → **O(result)**;
  `DIRECT` is the same `SELECT` without the aggregation. Rollup granularity is the derived `bucket`.
- **ES/OS:** a **`composite`** aggregation (terms on the group-by dims + a `date_histogram` source
  at the granularity, paged via `after`) with `sum`/`min`/`max` sub-aggregations → in-engine
  reduction, paged.

**Executor.** Runs each strategy's fetch, then unions on `(group-by, bucket)`: `DIRECT`/`PUSH_DOWN`
rows come finalized from the store; `STREAM_MERGE` rows are the streamed-and-merged sketches (Layer
A). A cube mixing additive and sketch meters is one union of the two.

**Making a granularity `DIRECT`** is a storage choice: materialize that granularity as its own tier
at write time (an extra rollup tier). Otherwise a granularity that does not equal a stored tier
always rolls up (additive → `PUSH_DOWN`, sketch → `STREAM_MERGE`).

### Layer C — Time partitioning (scale + retention)

Partition the cell store by time so a range query prunes whole partitions and retention is a drop,
not a delete scan.

- **RDBMS (Postgres):** declarative range partitioning of `dataset_<id>` by `window_start` (e.g.
  monthly). H2 (dev/test) has no declarative partitioning, so it keeps the Layer-A index only — the
  partitioning is a Postgres-production concern behind the same `DatasetSchemaManager` seam.
- **ES/OS:** time-based backing indices (`dataset_<id>_<yyyyMM>`) behind a read alias; the query
  hits the alias and the cluster prunes by index; retention drops old indices.

## Consequences

- **Good:** large cube reads become *correct* (no silent 10k truncation) and *bounded-memory*
  (Layer A); additive meters read in **O(result)** with the DB doing the work (Layer B); time-range
  reads prune to the relevant blocks/partitions (A index, C partitions); retention becomes a
  partition drop (C).
- **Cost:** the meter model, cell schema, writer, planner, executor, and both backends all grow;
  two storage encodings coexist (numeric columns for additive, blobs for sketches); pushdown SQL/ES
  aggregation is backend-specific; partitioning adds schema lifecycle.
- **Accepted limit:** sketches (percentile/distinct/top-k) **stay app-merged** — commodity RDBMS/ES
  cannot merge our sketch states (ES `percentiles`/`cardinality` operate on raw values, not
  pre-merged sketches). Streaming (Layer A) is what keeps that path bounded. Graduating sketch
  pushdown would mean a purpose-built OLAP engine (ClickHouse/Druid) — out of scope.

## Alternatives considered

- **Push everything down.** Requires an engine with native mergeable sketch types; would fork the
  path per meter class or drop sketch datasets. Rejected — conflicts with the commodity-backend
  design.
- **Just raise the page size / `max_result_window`.** Doesn't scale and reintroduces the memory
  problem. Rejected.
- **Pre-materialize more group-by shapes (Pinot star-tree style).** Storage blow-up; our cubes are
  already the chosen rollups. Deferred.

## Implementation breakdown

Each numbered item is a self-contained, green, formatted commit. Layers are sequential; within a
layer, backends can land independently.

**Layer A — prune + stream**

1. `analytics-serving`: add `streamCells(DatasetFetch, Consumer<Cell>)` to `DatasetQueryClient`;
   rework `DatasetQueryExecutor` to merge from the stream (drop the `fetch → List` path once both
   backends implement streaming).
2. `analytics-store-rdbms`: `RdbmsDatasetQueryClient.streamCells` (keyset SQL via a
   `CellScanSqlProvider`), and add the `(window_size, window_start, dims)` index in
   `RdbmsDatasetSchemaManager.ensure`. Test: seed > page-size cells, assert full merge with no
   truncation and correct totals.
3. `analytics-store-document`: store the cell sort key as a `keyword` field
   (`DocumentDatasetWriter.upsertCell` + `ensure` mapping); `streamCells` via `sort` +
   `search_after` **through the `search-client` abstraction** — extend `search-client` if it lacks a
   needed capability, do not bypass it. Compile-verified (no ES fixture yet).

**Layer B — additive pushdown**

4. `analytics-model`: `PushdownSpec`/`PushdownColumn`/`Agg`; declare specs in `MeterCatalog`
   (count/sum/min/max/avg/level), leave sketches unset; carry the spec on `CompiledMeter`. Unit
   tests on the catalog.
5. `analytics-store-rdbms`: schema + writer branch on the spec (numeric columns vs blob); a
   pushdown `GROUP BY` provider. Round-trip test: write additive cells, read via `GROUP BY`.
6. `analytics-store-document`: `composite` terms + `date_histogram` source + `sum`/`min`/`max`
   sub-aggs, `after`-paged; numeric fields for additive meters. Expressed **through the
   `search-client` aggregator abstraction** — if a source/sub-agg type is missing, add it to
   `search-client-query-transformer` + the ES/OS transformers, never work around it in the store.
   Compile-verified.
7. `analytics-serving`: planner picks strategy per meter; executor unions pushed-down rows with
   streamed sketch rows. Tests: mixed cube (additive + sketch) returns identical results to the
   pure app-merge baseline.

**Layer C — partitioning**

8. `analytics-store-rdbms`: Postgres range partitioning by `window_start` behind
   `DatasetSchemaManager` (H2 keeps the index); partition creation on `ensure`, retention drop.
9. `analytics-store-document`: time-based indices + read alias; retention by dropping indices.

**Cross-cutting**

10. `analytics-webapp`: no API change for cubes (the executor contract is stable); the table
    streaming/export endpoint (row cursor) reuses the Layer-A cursor keyed by `row_key`.

## Resolved

- **Cursor API:** push `Consumer<Cell>` (owns lifecycle, forces page-at-a-time, simplest correct).
- **Blob placement:** same table + strict column projection by default; a `_sketch` side table only
  when sketch-state retention diverges from the finalized `_value`s (see Layer B, Storage).
- **`DIRECT` fast path + `<meter>_value`:** matching-granularity reads fetch a stored value and skip
  the merge for *all* meter classes (see Layer B, Three read strategies).

## Notes for Layer B (from the meter model)

- The pushdown capability attaches to **`MeterType`** (which already knows its merge semantics —
  additive / mergeable-sketch / ratio). A type is pushable only if its *entire* accumulator
  decomposes into numeric columns, so the contract is a pair: **decompose** `ACC → column values`
  (for the writer) and **recompose** `SQL-aggregated columns → OUT` (for the read).
- Accumulators are not all single-column: `count`/`sum`/`level` → one column; `ratio` → two
  (`matched_`, `total_`), both `SUM`; `execution_time` → `count_`/`total_`/`max_` (SUM/SUM/MAX).
- **Summary meters** (`execution_time_summary`, `lifecycle_summary`) bundle sub-measures; if one is
  a sketch (percentiles) the whole meter is **not** pushable and stays `STREAM_MERGE`. Only wholly
  additive types get a `PushdownSpec`; everything else keeps the blob + `_value` path.

## Open questions

- **H2 in tests vs Postgres partitioning:** Layer C is exercised only against Postgres; H2 tests
  cover the index path. Decide whether to add a Postgres Testcontainers suite for C.

## Follow-on (out of scope): parallel cell read

A *latency* optimization to layer on **after** A–C — orthogonal to them (streaming bounds memory,
fan-out bounds latency; it works for free because accumulators are already mergeable). Gate on
evidence: with the Layer-A index + Layer-B pushdown doing most of the reduction, and ES/OS already
parallel across shards, single-threaded reads may be fast enough.

- A `ParallelCellReader` fans one read into `P = min(poolSize, ceil(range / minSliceWidth))` bounded
  sub-reads (target pool 8–16), each folding into its own accumulator map, then merges with the
  existing `merge`. **Decoupled from the 128 ingest partitions** (those are the write/shuffle axis;
  cells are keyed by time+grain). Cap `P` at the grain-bucket count in range so small ranges don't
  over-shard.
- v1: **time-band slicing only** — disjoint `window_start ∈ band_i` sub-queries aligned to the
  coarsest grain; rides the Layer-A index, no double-read. Merge is a union for time-series output,
  a real `merge` for full-range rollups (bounded by result cardinality).
- ES/OS: no client fan-out (shard parallelism is intrinsic); only relevant across Layer-C
  time-partitioned indices.
- Deferred: grain-hash slicing (skew escape hatch), built only when a real skewed query shows the
  densest band ≫ average.
