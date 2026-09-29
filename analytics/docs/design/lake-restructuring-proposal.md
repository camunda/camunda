# Analytics Lake — Structure Review & Restructuring Proposal

Status: proposal (2026-07-24). Scope: `analytics/analytics-lake` (~21k LOC, 100 files) and
`analytics/analytics-lake-serving` (~6k LOC Java, 59 files). Based on a full read of both modules.

## Verdict in one paragraph

The correctness engineering is strong — the invariants (dedup gates, commit atomicity,
fixed-memory batching, snapshot-consistent reads) were all thought through and are documented.
The structure is not: the module grew feature-by-feature on PoC scaffolding, so responsibilities
piled into a few god classes, every new table/metric was added by shotgun surgery, and the
contracts between layers live in javadoc cross-references, positional int constants, and string
naming conventions instead of code. Nothing here needs a rewrite; it needs *extraction along
seams the code already draws for itself* plus five genuinely new abstractions (listed below).

## The five structural diseases

### 1. Every table schema is spelled out 5–10 times

For `instances` alone the column list/order/types are independently encoded in:
`IcebergLakeWriter`'s static `Schema` constants (`write/IcebergLakeWriter.java:233-415`), its
DuckDB staging DDL (`:163-175`) and staging SELECTs (`:184-199`), its positional row appenders
(`:1061-1089`), `RawTableSchemas`' `TableSchema` builders (`translate/RawTableSchemas.java:55-336`),
the hand-maintained ordinal inner classes (`:391-559`), `LakeCompactor`'s sort clauses, the gauge
writer's DDL, and — read-side — serving's column-name literals and `MetricRegistry`'s suffix
sniffing. **The drift already happened**: the compactor sorts `instances` by
`process_id, started_at` (`write/LakeCompactor.java:109-110`) while the sink's declared sort keys
are `(process_id, key)` (`RawTableSchemas.java:40-54`) — compacted files are sorted differently
from fresh files today.

### 2. The commit machinery exists four to five times

Five commit paths (legacy buffered, `DirectCommitSink`, `CoordinatedDescriptorSink`, compactor,
gauge writer) each carry their own copy of the pieces whose entire point is "never diverge":

- carry-forward stamping of the 3 offset/frontier/zbpos property prefixes ×4
  (`IcebergLakeWriter.java:701-709`, `DirectCommitSink.java:128-135`,
  `CoordinatedDescriptorSink.java:131-138`, `LakeCompactor.java:381-399`)
- `offsetOf(summary, partition)` ×3, `DataFiles.builder(...)` registration ×5,
  the DuckDB staging-table→COPY→register idiom ×3
- idempotence replay-skip implemented twice with *different* semantics — the direct path leaks
  replay orphans the coordinated path sweeps (`DirectCommitSink.java:111-121` vs
  `CoordinatedDescriptorSink.java:84-93`)
- two concurrency regimes (JVM `commitLock` vs H2 CAS), with which-table-uses-which recorded
  only in accessor javadoc, validated only by runtime exception (`DirectCommitSink.java:92-98`)

### 3. God classes at every layer

- **`LakeTranslator` (2,246 lines)** holds 17 responsibilities: dedup gate, intent dispatch,
  fold logic, row building for 8 tables, ms→µs conversion, a hand-rolled JSON codec, variant
  hashing, variable profiling, object-fabric capture, lifecycle protocol, BPMN parsing, two
  duplicate LRU caches, and backpressure policy encoded in comments. 7 telescoping constructors
  (3→14 args). The same `guard→seen-cache→begin→unmark→put*→endRow` dictionary-emit block is
  copy-pasted 5×.
- **`IcebergLakeWriter` (1,096 lines)** holds 8 jobs: catalog bootstrap, schema authority,
  table-creation policy, a legacy write path kept for tests, exactly-once property-key ownership,
  durable-offset reads, the shared DuckDB connection, and the commit-lock registry. Half its
  lines are javadoc explaining how *other* classes must coordinate through it.
- **`TranslatorState`**: one 25-method store interface over 9 unrelated key spaces, which also
  hosts the entire domain model as nested records — every consumer of a domain value imports the
  storage interface.
- **`LakeUiServer` (3,093 lines)**: ~70% HTML/CSS/JS in Java string constants (including an
  `asIs()` identity method to dodge the 64KB constant-pool limit), 2 hand-rolled JSON writers,
  15 embedded SQL statements — and near-verbatim duplicates of serving's `LakeViewRegistry`
  catalog/view logic. Almost fully superseded by `analytics-lake-serving`.
- **`LakePocApp`**: sound lifecycle design, but `SinkWiring` is a 34-field positional record
  (`commitSink` passed 8× in a row), `PartitionPipelines` hand-unrolls 8 tables across 5 methods,
  and adding one table touches ~8 sites. The 9 metric declarations + virtual schemas + demo
  object types (config-as-code) sit inline between threading code.

### 4. The metrics plane: duplicated engine, dead SQL, convention-coupled reader

- `MetricsRider` vs `PollFedRider`: ~330 of 769 lines near-verbatim duplicated (drain targets,
  writers, column resolution, empty-default writing). Only the fold *trigger* differs
  (row-fed at flush vs record-fed at poll). They also drifted: LONG-only vs DOUBLE-only measure
  support, and *silently different null-dim semantics*.
- `CompiledEntityMetrics` bundles declaration accessors + partials schema generation +
  fingerprint + merge/finalize SQL generation. The generated merge/finalize SQL has **zero
  production callers** — meanwhile `analytics-lake-serving` re-implements the same arithmetic by
  hand (`SeriesService`'s weighted averages, `HistBinWalk`'s percentile bin-walk) and re-derives
  the declarations by *sniffing column-name suffixes* (`MetricRegistry`, `EntityCatalog`). The
  algebra that interprets stored partials therefore lives twice, connected only by naming
  conventions — every stored-shape change must be caught in three places.
- Cross-file positional contracts with no enforcement: `LakeTranslator`'s `FLOW_DIM_*` /
  `PROFILE_COUNTER_*` int constants must match, by declaration order, the `.dims(...)` calls in
  `LakePocApp` and the virtual schema column order — three files, one implicit contract, checked
  nowhere.
- In `sink/algebra/`: a genuinely good SPI, but `ExpHistogramAlgebra` vs
  `SignedDoubleExpHistogramAlgebra` finalize-SQL generators are textually identical (~20 lines,
  admitted in javadoc), and `ScalarStatsAlgebra` vs `DoubleScalarStatsAlgebra` are near-clones
  differing in primitive type.

### 5. Contracts by convention instead of by type

- ms vs µs is caller discipline at ~16 sites (`millisToMicros` exists but `RowAppender.putLong`
  accepts either unit; `TIMESTAMPTZ` logical type is not enforced at the append boundary).
- Backpressure policy (propagate for fact tables, absorb+unmark for dictionary tables) exists
  only as per-call-site comments.
- Serving builds ~30 SQL statements by concatenation with the same time-window predicate spelled
  6×, `time_bucket` 3×, weighted-average 2×, and a *diverged* row-count expression
  (`SeriesService.countExpr` handles counters; `InvestigateService.estimateRows` silently
  doesn't). Controllers pass `Map<String,Object>` with the same `response()` helper and
  exception-handler pair copy-pasted across 7 controllers.

## What is already right (keep, and use as the pattern)

- `objects/` (declare→compile with validation, sealed vocabulary) — the model for declarations.
- `catalog/` (`LakeCommitCoordinator`, `DeferredCommitTableOperations`) — clean, focused.
- The sink data path: ring, `SegmentSorter`, `DayRouter`, `IcebergParquetEncoder`, `Interner`,
  `BatchRowView` (field-id-driven), `GroupTable` — well-shaped and allocation-disciplined.
- The `Algebra` SPI's core decision — hot Java accumulators + *SQL as the merge operator*, no
  Java `merge()` — is architecturally sound (see reference section).
- Serving's escaping choke points (`SqlText`, `FilterClause`, `CohortPredicate`), snapshot-
  consistent view derivation from `planFiles()`, row caps/timeouts, graceful degradation rules.
- `ChangepointService`/`ScreenService`'s pure-algorithm split — the model for tools services.
- `LakePocApp.start(LakeConfig)` as an embeddable seam, and the documented close ordering.

## Target architecture

### Module layout

```
analytics/
├── analytics-lake-schema      NEW — the contract module (no Iceberg/DuckDB deps beyond API types)
│   ├── LakeTableDef / LakeTables      one declarative definition per table; the ONLY place a
│   │                                  column list/order/type/sort-key/family-day is spelled
│   ├── partials naming contract       _metrics/_hist, window_start, suffix rules, bin triple
│   ├── MetricSpec (+ compiled form)   the declaration DSL, moved out of the engine
│   ├── PartialsSql                    merge/finalize/percentile SQL generators (dialect-aware)
│   └── vocabulary                     qualifiers, states, link types as enums, not strings
├── analytics-lake             ingest/write (depends on -schema)
└── analytics-lake-serving     read (depends on -schema; drops its dependency on analytics-lake
                               once the ingest-hosting seam is a small launcher interface)
```

The writer/reader convention-coupling disease is only curable by a shared contract artifact.
Serving already depends on `analytics-lake` (for `LocalFileIO` and `LakePocApp`), so the "no code
dependency" rationale for suffix-sniffing has lapsed — replace sniffing with reading the spec.

### analytics-lake internal packages

```
lake/
├── schema/          SchemaProjections: derive Iceberg Schema, sink TableSchema, typed column
│                    handles, and the compactor ORDER BY from each LakeTableDef (kills the drift)
├── catalog/         LakeCatalog (JdbcCatalog bootstrap + tableOrCreate + partials fingerprint
│                    guard + close) · TableRegistry (typed handles) · LakeCommitCoordinator (as-is)
├── commit/          CommitStamps (the ONE carry-forward + offsetOf + stamp builder)
│                    DataFileRegistration (the ONE DataFiles.builder adapter)
│                    DescriptorCommitter (single DescriptorSink, always via coordinator —
│                    a one-table batch IS a direct commit; retire DirectCommitSink)
│                    ReplayGuard (one idempotence behavior: skip + orphan-enqueue)
├── sink/            keep batch/ encode/ ring as-is; split SinkPipeline into assembly vs
│                    BoundaryTracker if it keeps growing
├── algebra/         keep SPI; extract shared HistogramPercentileSql + a small SqlProjection
│                    helper; typed LongAccumulator/DoubleAccumulator sub-interfaces
├── metrics/         ONE MetricRider over a FactSource seam:
│                    RowFactSource (flush-thread, SortedRun) | RecordFactSource (poll-thread,
│                    staging API + ActiveWindow freeze/swap) → GroupedAccumulators → PartialsDrain
├── translate/       thin LakeTranslator = OriginPositionDedupGate + dispatch to RecordFold
│                    implementations: InstanceLifecycleFold, VariantCapture, VariableProfiler,
│                    ObjectFabricCapture, ObjectLifecycleTracker, ProcessDefinitionCapture,
│                    InstanceLinkCapture — each a class the current banner comments already outline
│                    · FactEmitter / DictionaryEmitter: backpressure policy as a type (kills the
│                      5× emit block and both LRU cache clones)
│                    · per-table typed RowWriters bound to schema/ handles (kills ordinal classes)
│                    · one JsonScalars util (kills both hand-rolled JSON codecs)
├── state/           TranslatorState split into per-domain slices (OpenEntityStore, VariantStore,
│                    ObjectStore, DefinitionStore) over one RocksDB; domain records move to a
│                    model package; keep flyweights/codecs as-is
├── maintenance/     LakeCompactor (stamps + sort from schema/) · GaugeWriter · snapshot dumper
│                    (moves out of state/ — it is an export concern)
├── duckdb/          DuckDbParquetWriter: the one staging/COPY owner (shrinks as encode/ takes over)
└── app/             LakePocApp slimmed: Map<String, TablePlumbing> instead of the 34-field
                     record; PartitionPipelines as a list + loops; declarations (metrics, object
                     types, virtual schemas) extracted to a LakeDeclarations class; LakeConfig
                     builder, dead fields dropped
```

Two cross-cutting type introductions:

- **Units as types**: either `RowAppender.putTimestamptz(col, millis)` keyed off the declared
  logical type, or a `Micros` wrapper — make the ×1000 unforgeable instead of 16-site discipline.
- **`RecordFold` interface** (`boolean onRecord(...)`): one contract for backpressure, ending the
  void-vs-boolean handler mix.

### analytics-lake-serving

1. Grow `sql/` into a small expression layer (no external dep): `TimeWindow`, `TimeBucket`,
   `WeightedAvg`, `CountExpr`, a `Select` composer, one `RowMapper` for the `row.get(i)` casts.
   Deletes every listed duplication; `SqlText`/`FilterClause`/`CohortPredicate` become its leaves.
2. Replace `MetricRegistry`/`EntityCatalog` suffix-sniffing with reading compiled `MetricSpec`s
   from the shared schema module (published by the writer alongside the fingerprint), and replace
   `HistBinWalk`/hand-written weighted averages with `PartialsSql` from the same module.
3. One `ToolResponse<T>` envelope + one `@RestControllerAdvice` (kills 7× copy-pasted handlers);
   merge the gauge controller into tools, the three objects controllers into one; map SQL
   failures to 500, validation to 400.
4. Honor `state-dir`: register `open_instances`/`open_elements` views in `LakeViewRegistry`.
5. **Delete `LakeUiServer` + `JsonSupport` + `BpmnCatalog` + `ProcessMapService` +
   `LakeUiStandaloneRunner`** once (4) lands and `LakeIngestProperties.uiPort` defaults to 0.
   Serving already does everything else better (data-derived process map, Jackson, React UI).

## Reference systems — what to borrow

- **ClickHouse `IAggregateFunction`**: one interface owns create/add/merge/serialize for each
  aggregate; the *state format* is the contract between the writer and every later merge. The
  lake's analog is: partials columns are the state format, so the SQL that merges/finalizes them
  must be *generated from the same declaration that wrote them* — one `PartialsSql`, consumed by
  both compactor-style merges and serving. Hand-writing that SQL in serving is the equivalent of
  a CH client re-implementing `AggregateFunctionQuantile::merge` by hand.
- **ClickHouse storage/interpreter split**: MergeTree knows parts, merges, and primary keys;
  query interpretation lives elsewhere; both hang off one `StorageInMemoryMetadata`. Analog:
  `LakeTableDef` as the single metadata object; ingest, compaction, and serving all derive from
  it (the compactor sort-clause drift is exactly what this prevents).
- **ClickHouse background merges vs inserts**: both go through the same part-writer machinery
  under the same commit discipline. Analog: compactor and flush commits share `CommitStamps` /
  `DataFileRegistration` instead of private copies.
- **Flink/Iceberg sink precedent**: checkpoint id stamped into the snapshot summary, carried
  forward on every commit, read back for exactly-once resume — exactly the lake's
  offset/frontier/zbpos scheme. Flink keeps it in *one* committer class; do the same.
- **Kafka Streams**: topology (declaration) built separately from the runtime that executes it;
  state stores are narrow per-domain interfaces, not one god store. Analog: `LakeDeclarations` /
  `MetricSpec` vs riders; `TranslatorState` split.
- **DataFusion/Calcite lesson for serving**: you don't need a full SQL AST — a ~5-class typed
  expression/fragment layer is the 80/20 that removes concatenation duplication while keeping
  DuckDB SQL visible and debuggable.

## Migration order (each step independently shippable, tests stay green)

1. **`commit/` unification** (highest risk-reduction per line): extract `CommitStamps`,
   `DataFileRegistration`, `ReplayGuard`; route compactor + gauge through them; retire
   `DirectCommitSink` in favor of one-table coordinator batches; unify the two replay behaviors.
2. **`schema/` single source of truth**: introduce `LakeTableDef` + projections; regenerate
   `RawTableSchemas`/Iceberg schemas/ordinals from it; fix the compactor sort drift as the
   proving change; delete the legacy buffered write path (move test seeding to a fixture over
   the sink pipeline).
3. **Translator decomposition**: extract the dedup gate and the seven capture folds behind
   `RecordFold`; introduce `FactEmitter`/`DictionaryEmitter` and typed row writers; builder
   instead of 7 constructors; split `TranslatorState`.
4. **Metrics unification**: one `MetricRider` over `FactSource`; split `CompiledEntityMetrics`
   into compiler / `PartialsSchemas` / `PartialsSql`; typed dim/counter bindings replacing the
   cross-file int constants (bind by name at wiring, fail at startup).
5. **Shared contract module** (`analytics-lake-schema`) + serving cutover: spec-published
   registry replaces suffix sniffing; `PartialsSql` replaces hand-written algebra; expression
   layer + `ToolResponse` + controller advice.
6. **Delete `LakeUiServer`** and friends after state-dir views land.

Steps 1–2 change no behavior except the intended drift fix and are the prerequisite for
everything else being safe. Step 3 is the biggest diff but is mechanical extraction along
existing banner-comment seams.

## Invariants a refactor must not lose

The full lists (with file:line anchors) live in the four area reviews this proposal was
synthesized from; the non-negotiables:

1. Dedup gate ordering: check before fold, watermark advance only on success, never on
   backpressure; the per-accumulator `lastPosition` XOR replay guard is *additional* to it.
2. Evict-after-emit everywhere; `emitInstance`'s internal ordering (lifecycle closings before
   any mutation; profiles/dict/relations after `endRow`).
3. Backpressure taxonomy: propagate for fact tables, absorb+unmark for dictionary tables —
   encode it, don't homogenize it.
4. Fixed-memory batching: ring is the whole budget; triggering segment held until
   `DescriptorSink.accept` returns; publish-before-seal with exact rollback.
5. Exactly-once stamping: files + offset + frontier + zbpos in one atomic commit; MIN-across-
   tables committed offset; every commit path (incl. rewrites) re-stamps all three prefixes;
   `validateFromSnapshot` on rewrites; `CommitStateUnknown` never enqueues deletes.
6. Field ids resolved from the live catalog by name, never hardcoded; `BatchRowView` maps by
   field id; name-mapping property for legacy files.
7. One family day per data file; day-scoped compaction; partition tuple derivation.
8. Fingerprint semantics (mergeability only; order-insensitive; scheme participates); frozen
   wire contracts (variant-k1, value layouts, column-family ids).
9. Freeze-before-seal alignment for poll-fed metrics; fresh-window-per-cut (no Java merge, no
   dedup guard needed on accumulators).
10. Allocation discipline: zero-alloc per record on the append path; per-completed-instance
    allocations only; rider-lifetime interners; pooled accumulators.
11. Read side: snapshot-consistent views from `planFiles()` (never directory globs), independent
    catalog handles, `duplicate()` per statement, row caps + truncation flags, escaping choke
    points, `decode(blob)` not `CAST`, bounded-response caps, missing-view → empty not 500.
