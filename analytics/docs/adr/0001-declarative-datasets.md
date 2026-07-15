# ADR 0001 — Declarative datasets as the source of truth

- Status: Proposed
- Date: 2026-07-03
- Scope: `analytics/analytics-engine`, `analytics/event-bridge-analytics`, `analytics/analytics-webapp`

## Context

The analytics pipeline works end-to-end (source → base projection → fact stream → aggregated
dataset → report), on a genuinely generic streaming runtime (`event-bridge-streaming`:
`Task`/`Stage`/`StreamProcessor`/`StreamRuntime`, the `AggregateFunction`/`KeySelector`/`ResultSink`
seams, `DurableMaterializedAggregation`/`MergingAggregation`, RocksDB state, produce-before-commit
sharded checkpoints). The two-stage combine → shuffle → merge topology already realises the deck's
*Base Projection → Fact Stream → Aggregated Dataset* (see `~/Downloads/Optimize _aufbohren_.pdf`,
"Optimize aufbohren", R. Smirnov). Storage is already id-parameterised: `AnalyticsColumnFamilies`
`ROLLUP_CELLS`/`SLOT_CELLS` are keyed by an id prefix, with the comment *"a new metric or
user-created dataset is a new id, not a new column family."*

But the layer above the runtime is hard-coded to the specific metrics built so far:

1. **`event-bridge-analytics/.../stage/Metrics.java`** — 24 hand-assigned `AGG_*` int ids and one
   imperative `specs()` method. Adding a metric means editing Java and picking a fresh int.
2. **Per-metric bespoke types** — each metric ships its own grouping-key record, accumulator record,
   and msgpack flyweight (`DefinitionKey`/`RegionKey`/`IncidentKey`/…). No generic
   dimension/measure schema.
3. **Sinks are JDBC/H2 only** — 15 `Jdbc*Sink` classes with embedded H2 `MERGE` SQL and a per-metric
   `CREATE TABLE`. `MetricSpec.sinkFactory` is typed to the concrete `org.h2.jdbcx.JdbcDataSource`.
   No Elasticsearch/OpenSearch sink exists and there is no `Sink` SPI beyond `ResultSink`.
4. **`ProcessExecutionProjector`** hard-codes fact derivation as a `switch` producing 8 named fact
   records; the fan-out supertype is a fixed marker interface.
5. **`analytics-webapp`** — `Dataset`/`Report` are thin DTOs pinned to one fact; the declared
   `dimensions` string is stored but never used; the dashboard is one hand-written SQL method per
   tile.

The deck's target is *declarative datasets*: a user declares a dataset (`SELECT dims, metric FROM
fact WHERE … GROUP BY dims`), and from that declaration the system derives the fact stream, the
aggregated table, and the report — so reports become request/response lookups over pre-aggregated
data. Two additional requirements sharpen this:

- **The serving store must support RDBMS, Elasticsearch, and OpenSearch** (the deck's Landing Zone
  is ES/OS; the current serving store is H2).
- **Datasets must be able to group by variables and other arbitrary dimensions**, and declaring a
  dataset must **provision its own schema** — developers must not hand-write a table/index per
  metric.

The question this ADR settles: what is the durable concept model, and how do we remove the
hard-coded layer without a big-bang rewrite of the working runtime.

## Decision

Make the **`DatasetDeclaration` the single source of truth.** A compiler derives everything else
from it deterministically; the runtime instantiates the pipeline per declared dataset instead of
from a hard-coded metric list.

```
DatasetDeclaration
  ├─ sourceFact     canonical fact type (process-instance | element | incident | variable)
  ├─ filters        WHERE, on dimensions/measures
  ├─ dimensions[]   GROUP BY: definition, version, tenant, element, var[foo] …
  │                 a variable dimension carries an enrichment timing:
  │                 event-time | pi-create | pi-complete  (deck: "Enriching Facts with Variable Dimensions")
  ├─ metrics[]      meters: count, sum(f), avg(f), min/max(f), p95(f), distinct(f), topK(f), ratio(pred)
  ├─ windowing      base window + tier hierarchy (1m / 1h / 1d / total)
  └─ retention

        │ compiler derives:
        ▼
  FactBinding     source fact + projected fields + enrichment timing
  DimensionKey    ordered typed dimension tuple  — ONE generic key codec (not per-metric)
  Accumulator     per meter, ONE generic acc codec (count→long, avg→{n,Σ}, p95→KLL blob, …)
  DatasetSchema   backend-neutral physical schema: dim columns + tier/window + meter columns
  Wiring          Stage-1 combiner + Stage-2 merger, from the declaration — no code
  ReportQuery     group-by-over-partials logical view over the aggregated dataset
```

Key principle that resolves the variable-dimension/schema requirement: **a dataset's schema is
dynamic with respect to the codebase but static with respect to the dataset.** `GROUP BY var[foo]`
is not knowable at ship time, but is fully known the instant the dataset is declared. So we never
hand-write schema — **declaring a dataset provisions its own schema** through a per-backend
`SchemaManager`, and "group by an arbitrary dimension" and "users create the schema on their own"
become the same mechanism.

### The four concepts

1. **Meter (generic metric).** `(meterType, fieldRef, params)` binds an existing `AggregateFunction`
   to a field reference on a *generic fact*, not a bespoke fact class. The current aggregate-function
   impls (sum, execution-time, KLL quantile, HLL distinct, top-k, ratio, duration-bucket) are reused;
   each meter type gets **one** accumulator codec regardless of dataset, and grouping uses a generic
   `DimensionKey`. `aggId` becomes a **derived stable id** per `(datasetId, meterIndex)` recorded in
   the dataset registry — no hand-assigned int space.

   Meter kinds are organised by **merge semantics**, not observability-instrument names:
   - **additive** — `COUNT`, `SUM`, `MIN`, `MAX`, `AVG`, and `LEVEL` (a running level = `SUM` of
   signed ±1 deltas). This is where "counter" and *level-gauges* live: active-instances /
   open-incidents are `LEVEL` over a delta measure, exactly as the current code already does — no
   separate gauge type.
   - **mergeable-sketch** — `PERCENTILE` (KLL), `DISTINCT` (HLL), `TOPK` (frequent-items); the
   timer/summary/histogram family.
   - **ratio / cohort** — matched/total, a derived additive. **Implemented** (`RATIO`): a fact
   increments `total` and, when its measure satisfies a threshold comparison
   (`le`/`lt`/`ge`/`gt`/`eq`/`ne`), also `matched`; the ratio is derived on read. The classic
   cohorts are declarations, not bespoke Java — SLA-compliant share is `duration <= sla`,
   no-incident share is `incidentCount == 0`.
   - **`LAST_VALUE`** / `FIRST_VALUE` (value-as-of gauge, e.g. "latest `amount`/`status` per
   instance") is a **mergeable max-by/min-by monoid**, not a heavy non-additive kind: carry the
   value with its source position (or event-time + tiebreak) and merge keeps the larger/smaller
   position. Exact, commutative + associative, and segment-delta-safe — no sketch, no
   non-commutative shuffle. A **strong near-term candidate** for the meter set (process analytics is
   full of "value as of" semantics), not a speculative deferral.
   - **Positional / sequential state** ("Nth retry", loop count) is **per-instance base-projection
   state** (like the incident flags / element starts already persisted), not a meter; a running
   total is a read-time `SUM() OVER (ORDER BY window)` over an additive meter. **Session/gap
   windows** are deferred — BPMN gives explicit lifecycle boundaries, so gap-inference is only
   needed if analytics expands to user-behavior (Tasklist clickstreams), where the interval-merge +
   watermark cost would be justified.

2. **Fact (generic).** The base projection keeps its domain fold but emits a generic
   `Fact = (factType, dimensions: Map, measures: Map, eventTime, sourceCoordinate)` for a small set
   of canonical fact types. Variable-dimension enrichment (timing per the deck) is applied here using
   the existing `BaseProjectionStore` variable state. One fact type feeds many datasets; each dataset
   projects the columns it needs at its combiner.

   **Grouping by a variable = enrichment, and dimension ≠ value.** A variable dimension such as
   `region` is *schema* — one declared column (RDBMS) / keyword field (ES/OS) in the cube; its
   distinct values (`EU`, `US`, …) are *data* — they become part of the `DimensionKey`, i.e. one
   rollup cell per observed value. The value reaches the fact by enrichment: `VARIABLE` records are
   folded into the projection state keyed by `processInstanceKey`; when the fact is derived, the
   projector reads that PI's variable from **local** state and stamps it on the fact. Because Zeebe
   co-partitions a PI's records (including its variables) onto one source partition and Stage 1 is
   per-source-partition, this is a local stream-table lookup — **no shuffle**, deterministic,
   replay-safe. Enrichment is **demand-driven**: the projector resolves only the variables that some
   active cube declares as a dimension/filter (never the unbounded set), and a missing value maps to
   a distinguished `null`/"unknown" bucket. The declared enrichment *timing* (event-time /
   PI-create / PI-complete) each maps to a fixed source position, so the stamped value is a pure
   function of the log up to that point.

3. **Dataset.** The declaration + compiler above, replacing `Metrics.specs()`. Both stages already
   refresh a `DatasetRegistry` live; they instantiate per-dataset combiners/mergers/sinks from the
   compiled wiring.

   **Activation must be replay-deterministic.** The MVP registry refreshes a DB table every 5s and
   is forward-only by wall-clock — which is *not* replay-safe: on cold replay or rebalance handoff
   the registry is "whatever is in the table now," so Stage 1 would fold *all* historical facts into
   a dataset that, in the live run, only began accumulating at declaration time. Replay would then
   produce different (larger) results than the original run. The fix pins activation to a
   **deterministic source coordinate**, reusing the `(sourcePartitionId, sourcePosition)` coordinate
   facts already carry for dedup:

   - A dataset definition carries an **immutable per-source-partition activation position vector**
     `activation[p]`, **fixed once at admission time** and stored durably in the registry. It is
     never recomputed on replay — the live run fixed it; replay reads the same stamp.
   - Stage 1's ingest rule is a local, in-order, single-writer-per-partition check: **fact `(p,pos)`
     contributes to dataset `D` iff `pos ≥ D.activation[p]`** (and `< D.deactivation[p]` when
     dropped). Deterministic, forward-only by construction, replay-identical.
   - Activation lives **only in Stage 1** (the point a fact is first routed to a dataset); Stage 2
     merges whatever partials Stage 1 emits and needs no activation logic.
   - **Backfill** (the deck's alternative to forward-only) is the *same* mechanism with the floor
     lowered — `activation[p] = 0` (or a chosen start) + reprocess — not a separate code path.

   The discipline: activation is a **durable, immutable position vector**, never a `declared_at`
   timestamp compared at poll time. The registry that holds it must therefore be recovered
   deterministically (persisted with the pipeline checkpoint, or itself a compacted control record
   with the activation vector stamped in at admission), so a dataset id → `{aggIds, schema version,
   activation/deactivation vector}` mapping is bit-identical across restarts.

4. **Report.** A `ReportDefinition` (dataset + selected dimensions ⊆ the dataset's + metric selection

   + filters + time range/tier + viz) executes as a **generated query over the pre-aggregated
     dataset** — sum counts / merge sketches. Reports are logical views; no new storage. Replaces the
     per-tile `DashboardRepository`.

### Avoiding dataset/meter explosion: shared base-grain cubes + logical views

For process-instance analytics the *measure* is almost always the same execution-time family; what
varies between declarations is **filters** and **group-by dimensions**. If each declaration minted
its own rollup + pipeline, the same fact would fold into many combiners into many overlapping cubes.
Three moves prevent that, all resting on invariants already chosen (mergeable accumulators;
id-keyed `ROLLUP_CELLS`):

1. **One composite measure, not many meters.** Execution time is a single accumulator
   `{count, sum, min, max, KLL}` with `merge`; it serves count, total, avg (=sum/count), min, max,
   and any percentile. So min/max/avg/total/p50/p95/p99 is **one meter**, not seven. Composition
   extends across the lifecycle: a process/element *summary* meter is one accumulator holding
   per-transition counts (`activated` / `completed` / `terminated` / …) **plus** the duration
   summary, folded from a single transition-tagged fact — so "how many instances were activated /
   completed / terminated" and the duration stats come from one cell, not many meters. A composite
   meter is still just one `AggregateFunction` with a struct accumulator whose `add` dispatches on
   the fact's transition (read through `FactRow`); it needs transition-tagged generic facts, so the
   full lifecycle-summary composite lands with the generic fact/projector (Phase 2) — the
   duration-summary composite (Phase 1.4) is designed as the struct it grows into.
2. **Filters and coarser group-bys are report-time views, never new datasets.** Mergeability means a
   fine-grain cube answers any coarser query by re-aggregating. A `WHERE`/`GROUP BY` that only
   references dimensions already in the grain adds zero pipelines and zero storage — it is view logic.
3. **The compiler is a planner that consolidates onto shared cubes.** Normalize each declaration to
   `(factType, measureFamily, requiredDims = groupBy ∪ equality-filter columns, tier)` and route it:
   - a cube with `dims ⊇ requiredDims` exists → attach the declaration as a **logical view**;
   - missing dims are **low-cardinality** (version, tenant, element, enum/boolean vars) → **extend the
     cube grain** (schema-evolve, forward-only) and attach — still one pipeline;
   - missing dim is **high-cardinality** (`customerId`, free-text var) → mint a dedicated
     **filtered/projected dataset** (pre-filtered, narrow grain), or leave it to the fact-stream tier.
     This is the *only* case that spawns a new physical dataset — and the case where sharing would
     blow up cell cardinality anyway.

Consequences of this planning model:

- A **Dataset** is a *physical pre-aggregated cube at a grain*; a **Report** is a *logical slice +
  roll-up* over it — exactly the deck's "Aggregated" vs "Filtered/Projected" vs "Logical/Declared
  Views" distinction, decided by the planner.
- `aggId` and the activation vector attach to the **cube**, not the individual declaration, so
  consolidation *reduces* the number of distinct pipeline operators and rollup cells.
- The planner's **cardinality policy** (low-card dims fold into the shared grain; high-card dims go
  dedicated or ad-hoc) is the single knob that keeps cube cell-count bounded.

**How the planner decides low- vs high-cardinality.** True cardinality is a data property, unknown at
declaration time, so the decision lives in the control plane (never per-fact) and is made in three
layers:

1. **Declared intent + default.** Grouping *by* a variable is itself the signal that it is
   categorical; high-card attributes are normally filters/identifiers, not group-bys. Default a
   `GROUP BY var.x` to categorical/low-card; let the declaration override with a cardinality kind or a
   max-distinct budget.
2. **HLL guardrail.** Maintain a cheap distinct-count sketch (already available) per materialized
   dimension per cube. If a low-card-declared dimension breaches its budget, the control plane
   **re-plans**: demote it to a dedicated filtered/projected dataset, bucket it (top-N + "other"), or
   serve those queries from the ad-hoc fact-stream tier.
3. **Hard cell budget backstop.** A max-cells-per-cube limit forces demotion/spill regardless of
   declaration, bounding memory/storage unconditionally.

Two invariants keep this deterministic: (a) the chosen grain is **frozen and versioned in the
registry** (like the activation vector) — replay/rebuild use the recorded grain, never a
re-estimate; (b) adding a dimension is a **re-grain** (schema-version bump, forward-only gap +
optional backfill), a control-plane event — so the planner promotes into the *shared* cube only when
the dimension is low-card *and* enough declarations benefit, and prefers a dedicated dataset
otherwise. The pipeline folds by whatever grain the registry currently pins; it never decides
cardinality itself.
- A curated process-instance cube (standard dims + a small set of user-opted-in low-card variable
dimensions) can be the default, materialized once; the long tail of declarations are views over it.

### Serving-store abstraction — mirror OC's `search-client` (RDBMS + ES + OS)

Model the serving layer on how OC already hides ES/OS/RDBMS, rather than inventing a new pattern.
OC's design is **three separate seams**, and we adopt the same split:

- **Read**: a small, neutral client interface (OC's `DocumentBasedSearchClient`) over a
  backend-neutral request/response model, with a **query-transformer** translating the neutral model
  per backend; RDBMS is its own impl.
- **Write**: a separate write/upsert seam (OC does this in the exporter path, not the read client).
- **Schema**: a separate schema/index/mapping concern (OC's `schema-manager` module).

Backend is selected by the **existing `io.camunda.search.connect.configuration.DatabaseType`** enum
(`ELASTICSEARCH` / `OPENSEARCH` / `RDBMS` / `NONE`) and wired through the **existing
`search-client-connect`** layer (`ElasticsearchConnector` / `OpensearchConnector`,
`ConnectConfiguration`) so auth / TLS / plugins are reused, not reimplemented.

So the sink layer is three narrow interfaces behind one backend-neutral `DatasetSchema`, not a
JDBC-typed factory:

```java
// WRITE — the pipeline's ResultSink delegates here; idempotent full-value upsert by (dims, window, tier).
interface DatasetWriter extends CloseableSilently {
  void upsert(DatasetSchema schema, DimensionKey key, long windowStart, Tier tier, Row measures);
  void flush();                                             // batch boundary
}
// SCHEMA — declaring a dataset provisions its own table / index+mapping.
interface DatasetSchemaManager {
  void ensure(DatasetSchema schema);                        // create/migrate
  void evolve(DatasetSchema before, DatasetSchema after);   // add a dimension → forward-only
  void drop(long datasetId);                                // retention
}
// READ — powers reports; neutral ReportQuery in, rows out; transformed per backend (mirror DocumentBasedSearchClient).
interface DatasetQueryClient extends CloseableSilently {
  ReportResult query(DatasetSchema schema, ReportQuery query);
}
```

Per backend (selected by `DatabaseType`):

- **RDBMS** — `DatasetSchemaManager` derives `CREATE TABLE dataset_<id>(dim…, var_foo…,
  window_start, tier, <meter cols>, PRIMARY KEY(dims, window, tier))`; `DatasetWriter` does a
  dialect-aware upsert; `DatasetQueryClient` transforms `ReportQuery` to SQL. Reuse the `db/rdbms`
  dialect layer (H2 + Postgres) — no hand-written H2 `MERGE`.
- **Elasticsearch / OpenSearch** — `DatasetSchemaManager` derives an index + mapping from
  `DatasetSchema` (dimensions as keyword/long, sketches as binary); `DatasetWriter` upserts by
  deterministic doc-id `hash(datasetId, dims, window, tier)` (idempotency); `DatasetQueryClient`
  transforms `ReportQuery` to the ES/OS aggregation DSL. Reuse `search-client-connect` for the
  connection. This is also what lets the Landing Zone be ES/OS.

`MetricSpec.sinkFactory<JdbcDataSource>` and the `JdbcDataSource` in `StageBuilders.merger` are
removed; the merger takes a `DatasetWriter`, chosen once from config via `DatabaseType`.

### Build order (each phase shippable and green on its own)

- **Phase 1 — Generic meter core.** Generic `DimensionKey` + codec; `Meter` types wrapping the
  existing aggregate functions with one acc codec each; data-driven `MeterRegistry`; derive `aggId`
  from the registry. Removes the 24-id hand list and the per-metric key/acc classes.
- **Phase 2 — Generic fact + base projection.** Generic `Fact`; refactor `ProcessExecutionProjector`
  to emit it; variable-enrichment timing policy.
- **Phase 2b — Shuffle redesign** (before Phase 3 wires the generic pipeline). Three coupled changes
  to the Stage-1→Stage-2 transport:
  - **Segment-delta aggregation.** Stage 1 seals an *immutable delta* per `(aggId, key, window,
    sourcePartition, segment)`; Stage 2 merges each delta once into a single running cell (one
    accumulator per cell + a per-partition `segWatermark` for dedup), replacing per-writer full-value
    slots. Meters are already mergeable, so deltas drop in. Tradeoff: a delta emits only when its
    segment seals → latency = segment fill time (the `STRIDE` knob); strong at high throughput.
  - **Single serving sink.** Everything — including today's directly-written process-definition
    metadata — becomes a shuffled fact, so the serving write happens only in Stage 2. One write path,
    no Stage-1 sink.
  - **Versioned shuffle envelope.** Wrap the shuffled payload in a metadata header modelled on
    `ZeebeRecordCodec`'s frame (`version | producedAt | schemaVersion | producerCoordinate |
    payload`), replacing the bare `Partial`/`PartialCodec`, so the wire format can evolve and stamp
    time/version.
- **Phase 3 — Dataset declaration + compiler.** Structured declaration first (SQL-like parser
  deferred); wire both stages to instantiate per-dataset combiners/mergers from the compiled wiring.
  **Deletes the old typed path wholesale** — `ProcessExecutionProjector`, the per-metric fact
  records, `MetricSpec`/`Metrics`, and the bespoke `Jdbc*Sink`s — replacing it with the generic
  projector + declaration-driven wiring; the specialized cohort metrics (SLA, no-incident) are
  re-expressed as dataset declarations over the canonical facts. No parallel paths remain: one
  projector, one fact model, one shuffle, one sink. (The generic pieces added in Phase 2 are the
  canonical replacement, not a duplicate to maintain.) **Implemented**, including the generic
  `RATIO` meter and a **projected (raw) dataset kind** (`DatasetKind.PROJECTED`): a flat, keyed row
  list (e.g. completed instances enriched with variables) written straight from Stage 1 as an
  idempotent per-key upsert — no windowing, no shuffle, replay-safe.
- **Phase 4 — Sink/SchemaManager SPI.** SPI + RDBMS impl (`db/rdbms`) first, then ES + OS impls
  (`search-client`). Declaring a dataset calls `SchemaManager.ensure`.
- **Phase 5 — Reports.** `ReportDefinition` + per-backend query generator; the current dashboards
  become seeded dataset+report declarations rather than hard-coded tiles.
- **Phase 6 (deferred)** — dimension evolution (forward-only + background backfill) and the deck's
  tiered ad-hoc fallback (aggregated → fact stream → base projection).

Recommended defaults for the first pass, pending confirmation: **RDBMS backend first** (lowest risk,
the path that already works), **structured declaration first** (the SQL-like parser is a later
thin front-end).

## Rationale

- **The runtime and storage are already generic.** The hard-coding is a thin definition/derivation
  layer (`Metrics.java`, bespoke key/acc types, JDBC sinks, the projector switch). Deriving that
  layer from a declaration is the smallest change that makes the concept correct — not a rewrite.
- **Declaration-provisioned schema is the only design that satisfies both "arbitrary dimensions" and
  "users create the schema."** Any fixed-schema or EAV alternative either can't express `var[foo]` as
  a first-class grouping column or gives up query efficiency (defeating pre-aggregation).
- **One generic `DimensionKey`/accumulator codec collapses the per-metric type explosion** and lets a
  single generic combiner/merger serve every dataset — the id-parameterised `ROLLUP_CELLS`/`SLOT_CELLS`
  storage was already built for exactly this.
- **A `DatasetStore` SPI is the natural seam for RDBMS/ES/OS.** `ResultSink`'s idempotent
  full-value-overwrite contract was explicitly designed to also fit an ES doc-id upsert; the monorepo
  already provides `db/rdbms` and `search-client` so we adopt, not invent, the backends.
- **Reports as logical views over pre-aggregated data** is the deck's core win: the heavy lifting
  happens once in the pipeline, so a report is a cheap group-by, shared across users.

## Consequences

- `Metrics.java`, the bespoke `*Key`/`*Accumulator`/`*Value` classes, and the 15 `Jdbc*Sink` classes
  are removed or collapsed into generic mechanisms over the phases; the aggregate-function *impls* are
  kept and reused.
- `MetricSpec` loses its JDBC coupling; `aggId` becomes registry-derived, so the "never reorder"
  hazard in `Metrics.java` disappears.
- A new dataset-registry contract records, per dataset id, `{declaration, derived stable aggIds,
  schema version, immutable per-source-partition activation/deactivation vector}` — needed for
  `aggId` stability, forward-only dimension evolution, and replay-deterministic activation. The
  MVP's 5s wall-clock DB poll is replaced by a deterministically-recovered registry.
- The webapp's dashboard stops being per-tile SQL; existing dashboards are re-expressed as seeded
  declarations, which must be verified to reproduce today's numbers.
- Adding a backend is implementing `DatasetStore`/`SchemaManager`/`DatasetQueryExecutor`, not touching
  the pipeline.

## Revisit triggers

- A metric whose accumulator is genuinely not mergeable (needs the shuffle's non-commutative path
  beyond what per-writer slots/segment-deltas cover) — revisit the meter/accumulator model.
- A dataset shape that the structured declaration can't express, forcing the SQL-like parser earlier.
- A backend whose upsert can't be made idempotent by deterministic key/doc-id — revisit the
  `ResultSink` contract for that store.
- Dimension backfill demand (deck: forward-only vs backfill) — promote Phase 6 and add a
  reprocessing path over the base projection.
  </content>

