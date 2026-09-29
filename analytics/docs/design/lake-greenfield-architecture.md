# Analytics Lake — Greenfield Architecture

Status: proposal (2026-07-24). Companion to `lake-restructuring-proposal.md` (which reviews the
code that exists). This document ignores the existing code and asks: **if we built the analytics
lake today, from nothing, on top of the event-bridge libraries as they now are — what would we
build?** The existing PoC's *lessons* (invariants, gotchas, validated numbers) are inputs; its
*shapes* are not.

---

## 1. What the system is for

- **Process, decision, and object-centric analytics** over Zeebe execution data: finished-row
  facts (instances, activities), derived dictionaries (variants, definitions, objects, links,
  relations), pre-aggregated metrics (counts, scalar stats, histograms/percentiles, cohorts),
  and an explain plane that turns those into typed, reproducible findings.
- **Live and historical** in one store: minutes-fresh data and years of history behind the same
  query surface; open state ("what is running now") queryable next to closed facts.
- **Trustworthy numbers**: exactly-once effect on every stored row and every partial, auditable
  by SQL recomputation from raw facts.
- **Replayable**: every derived artifact is a deterministic fold of the log; a fresh node with
  empty disks can rebuild everything.
- **Cheap to run small, able to grow**: one JVM for dev/demo/single-tenant; scale-out by
  partition for ingest and by replica for reads; no mandatory external services beyond the
  event bridge and a filesystem/object store.
- **A platform seam, not just an app** (settled target architecture, 2026-07-23): the derivation
  results must eventually be consumable by more than one product surface (an Operate-like
  operational tracer and this Optimize-like analytics stack) without either re-deriving.

Non-goals for v1: cross-partition object shuffling (partition-local object assumption stands),
OCEL export (latent capability only), multi-warehouse federation, non-Zeebe sources.

## 2. Prime contracts (the load-bearing decisions)

Everything below follows from five contracts. Each is stated once here; the rest of the document
is their consequences.

**C1 — The log is the source of truth.** The event bridge `zeebe-records` topic (typed
SBE+MsgPack via `event-bridge-zeebe-connector`, no JSON) is the system's input and its recovery
story. Derived state is a cache of a fold over the log; nothing derived is ever authoritative.

**C2 — The table is the API.** Downstream consumers (serving, explain, external BI, future
products) read open-format tables (Iceberg + Parquet) through the catalog's current snapshot —
never internal state, never private RPC. Anything a reader needs to interpret a table (its kind,
its metric spec, its algebra schemes) is *published in the catalog alongside the table*, so no
reader ever reverse-engineers column-name conventions.

**C3 — Declarations over code.** Tables, metrics, object types, and closing rules are data
(specs), validated and compiled at startup into plans. Adding an entity, a metric, or an object
type means writing a declaration, not touching the engine. Specs carry fingerprints; stored data
is guarded by them.

**C4 — Single-writer shards with atomic cuts.** Each source partition is an independent shard:
one task, one state slice, one output stream, committed as atomic cuts stamped with origin
coordinates. This is `event-bridge-streaming`'s native execution model (`StreamRuntime`, `Task`,
`CommitCut`) — the lake **adopts it instead of owning a poll loop, rebalance handling, retry
policy, or commit scheduling**. Exactly-once is the composition of idempotent publish (replay
skip by stamped coordinates) with state-and-offset persisted in the same cut.

**C5 — Aggregation state must be engine-portable.** Metric partials are relational rows whose
merge operator is ANSI `GROUP BY` SQL generated from the spec — never opaque blobs, never Java
merge. This is the one deliberate divergence from the analytics-engine's DataSketches lineage,
and it is what makes the lake readable by DuckDB today and anything else tomorrow.

## 3. The shape: four planes and a contract module

```
                    ┌───────────────────────────────────────────────────────┐
 Zeebe exporter ──► │ EVENT BRIDGE  zeebe-records topic (+ derived-fact and │
                    │ changelog topics later — same broker, same client)    │
                    └────────────────────────┬──────────────────────────────┘
                                             │ StreamRuntime (consumer group, per-partition Tasks)
                    ┌────────────────────────▼──────────────────────────────┐
                    │ DERIVATION PLANE   lake-derivation                    │
                    │  RecordFolds over shard state → row emissions +       │
                    │  metric folds; freezeCut → LakeCut                    │
                    └────────────────────────┬──────────────────────────────┘
                                             │ LakeCut: sealed segments + partials + state delta
                    ┌────────────────────────▼──────────────────────────────┐
                    │ STORAGE PLANE      lake-storage / lake-columnar       │
                    │  encode Parquet → one atomic multi-table catalog      │
                    │  commit (stamped) ; maintenance (kind-aware           │
                    │  compaction, expiry, orphan sweep)                    │
                    └────────────────────────┬──────────────────────────────┘
                                             │ Iceberg catalog + files (the API, C2)
                    ┌────────────────────────▼──────────────────────────────┐
                    │ SERVING PLANE      lake-serving (+ web client)        │
                    │  snapshot-consistent views, spec-driven registry,     │
                    │  query/tool/explain services, REST                    │
                    └───────────────────────────────────────────────────────┘

        lake-contract (specs, vocabulary, PartialsSql, stamps) is depended on by ALL planes.
```

### Module layout

| Module | Contents | Depends on |
|---|---|---|
| `lake-contract` | `TableSpec`, `MetricSpec`, `ObjectTypeSpec`, vocabulary enums, table kinds, partials naming, `PartialsSql` generators, stamp/coordinate types, spec fingerprints + JSON codec | nothing lake-internal |
| `lake-columnar` | segment ring, columnar vectors, sorter, interner, day router, Parquet encoder (field ids, blooms, zstd), algebra SPI + accumulators | `lake-contract` |
| `lake-storage` | `LakeCatalog` (Iceberg JdbcCatalog authority, table create + spec publication), `CutCommitter` (the one commit path), `Maintenance` (compaction/expiry/sweep), FileIO impls | `lake-contract`, iceberg-core |
| `lake-derivation` | `RecordFold` implementations, shard state slices, metric rider, object fabric, the `LakeTask` assembly + `StreamRuntime` wiring | `lake-contract`, `lake-columnar`, `lake-storage`, `event-bridge-streaming` |
| `lake-serving` | view registry, query service, tools/explain services, REST, spec-fed registries | `lake-contract`, duckdb, iceberg-core (read-only) |
| `lake-app` | composition roots: `LakeNode` (derivation), `ServingNode`, `UnifiedNode` (both in one Spring Boot app) | all above |

Rules: `lake-serving` never depends on `lake-derivation` (C2 — it reads tables, not code);
`lake-columnar` and `lake-contract` have no Spring, no event-bridge, no DuckDB; only
`lake-derivation` knows the event bridge exists; only `lake-storage` writes to the catalog.

## 4. The data model: table kinds as first-class semantics

Every table is declared as a `TableSpec` in `lake-contract` — name, columns (typed, with
nullability, dictionary-encoding hint, sort position, family-day role), partition rule, and
**kind**. The kind is not documentation; it selects the merge discipline, the compaction
strategy, and the read semantics:

| Kind | Merge discipline | Compaction | Examples |
|---|---|---|---|
| `FACT` | append-only, immutable rows | day-scoped sort+rewrite | instances, activities, object_lifecycle, state_transitions |
| `DICTIONARY` | keep-any-per-key (rows byte-identical per key by construction) | day-scoped rewrite + key-dedup | variants, objects, instance_links, object_relations, process_definitions |
| `PARTIAL` | `GROUP BY` with spec-generated merge projections (C5) | generated-merge collapse into coarser windows (1m→1h→1d) | *_metrics, *_hist, cohorts |
| `SNAPSHOT` | last-wins per key at a stamped position | replace | open-state exports |
| `GAUGE` | append-only samples, time-partitioned, no offset stamping | day-scoped rewrite + retention | open_instances_gauge |

Everything is *derived* from the spec: the Iceberg schema (field ids still assigned by the
catalog and read back — the catalog stays the id authority), the columnar segment layout, typed
column handles for writers, the compactor's ORDER BY, and the serving registry entry. A column
is spelled once, ever.

Specs are **published**: `LakeCatalog` writes each table's spec JSON (+ fingerprint) into the
table's Iceberg properties at creation, and refuses to open a table whose stored fingerprint
disagrees (schema evolution = explicit spec migration, not drift). Serving reads specs from
table properties — its registry has zero heuristics.

Vocabulary (instance states, sighting qualifiers, link types, birth qualifiers) lives as enums
in `lake-contract` with stable wire names. No string is minted at an emission site.

## 5. Derivation plane: folds on the StreamRuntime

### 5.1 Execution substrate — adopt, don't rebuild

The lake's ingest process is a `StreamRuntimeGroup` running one `StreamRuntime` over the
`zeebe-records` topic. The library already provides, and the lake therefore does **not** own:

- the poll/decode/route source loop and its backpressure (bounded per-partition queues),
- consumer-group membership, rebalance delta handling, seek/retry/error backoff,
- per-partition single-writer execution on actors (partition count ≠ thread count),
- the commit barrier (`Task.freezeCut` → `CommitCut.publish/persist/complete` on an IO pool),
- wall-clock + event-time punctuation, `needsCheckpoint` pressure-driven early cuts,
- state-store SPI (`KeyValueStore` on RocksDB with tuning + caching + in-memory for tests),
- Micrometer metrics for flow/cut/store, and — when HA is wanted — the changelog/standby
  machinery (ADR 0009) for warm failover of shard state instead of full replay.

This deletes, by decision rather than refactoring: the hand-rolled poll loop, the per-table
flush threads, the bespoke RocksDB wiring, and the ad-hoc elapsed-time multiplexing of
housekeeping duties onto the poll thread.

### 5.2 The LakeTask: composition of folds

One `LakeTask` per source partition, assembled from small units with one shared contract:

```
LakeTask (Task<ZeebeRecord>)
├── OriginGate                 per-Zeebe-partition position watermark; duplicate drop;
│                              advance-only-on-success (frozen into every cut)
├── RecordFold (interface)     boolean onRecord(record) — one backpressure contract
│   ├── InstanceLifecycleFold  open/close instances & elements → instances/activities rows
│   ├── VariantFold            flow/element mix (variant-k1 scheme) → variant_hash + dictionary
│   ├── VariableProfileFold    typed profiling → profile metric folds
│   ├── ObjectFabricFold       sightings/links/relations capture
│   ├── ObjectLifecycleFold    birth/close/tombstone protocol → lifecycle facts
│   └── DefinitionFold         deployment records → BPMN model index + process_definitions
├── ShardState                 per-domain KeyValueStore slices (open entities, variants,
│                              objects, definitions) via the streaming state SPI
├── TableWriters               typed, spec-bound row writers over the columnar segment ring
│                              (FactWriter = propagate backpressure; DictionaryWriter =
│                              seen-cache + absorb — the policy is the type)
└── MetricRider                one engine over FactSource (row-fed from sealed segments,
                               record-fed from folds), spec-compiled plans, algebra accumulators
```

The BPMN model index deserves its own note: definitions are facts on the log (deployment
records), so the model graph (flow endpoints, scope trees, MI bits, gateway conditions) is
folded into shard state like everything else — there is no filesystem scanning and no
"model catalog service"; serving reads models from the `process_definitions` table.

### 5.3 The cut is the unit of everything (kills the crash-window class)

`freezeCut(offset)` produces a `LakeCut` capturing, at one barrier:

1. **sealed segments** for every table touched this window (columnar, sorted, day-routed),
2. **frozen metric partials** (accumulators swapped, fresh window armed),
3. **the shard state delta** (buffered puts/evictions since the last cut),
4. **origin coordinates**: source offset, event-time frontier, per-Zeebe-partition watermarks.

Lifecycle, mapped onto `CommitCut`:

- `publish()` (IO thread): encode segments to Parquet; register all files of all tables of this
  shard in **one atomic catalog commit** via the commit coordinator, stamped with the cut's
  coordinates. Idempotent by construction: a replayed cut is detected by its stamped offset and
  skipped, with its files enqueued for orphan sweep.
- `persist()` (same IO thread, after publish): the state delta + offset in one RocksDB
  transaction.
- `complete(success)` (processing thread): retire the frozen data, or merge it back under live
  state for retry.

Because **evictions ride the state delta, not the fold**, the PoC's evict-after-emit crash
window cannot exist here: state mutations become durable in the same cut as the rows they
justify, and a crash replays from the persisted offset into idempotent publishes. `restore()`
returns the state's offset; the lake's stamped offsets remain a cross-check and the
rebuild-from-scratch authority (empty disk ⇒ `NO_OFFSET` ⇒ replay from the log, C1).

Cut cadence: the runtime's commit interval, tightened by `needsCheckpoint()` when the segment
ring or state-delta buffer fills — memory stays fixed (ring geometry is the budget) without a
dedicated flush thread per table.

### 5.4 Metric plane inside the task

- `MetricSpec` (in `lake-contract`): entity, dims, window grid + source column, measures with
  algebra schemes, count/named counters, null policy. Compiled once at startup against table
  specs; dim/counter handles are **bound by name** and validated then — no positional int
  contracts across files.
- One `MetricRider`, two `FactSource`s (row-fed from sealed segments at freeze; record-fed via a
  typed staging API during folding). Same accumulators, same drain, same partials writer.
- Algebra SPI (in `lake-columnar`): typed `LongAccumulator`/`DoubleAccumulator`, scheme-versioned
  (fingerprint participation), garbage-free, `drain`-only-when-non-empty. Count, scalar stats
  (long + double + non-finite counting), signed/unsigned exponential histograms, later top-K —
  the SQL each algebra needs for merge/finalize is generated **only** in `lake-contract`'s
  `PartialsSql` (C5), which is consumed by three parties: serving reads, PARTIAL compaction
  collapse, and the accuracy auditor. Merge SQL is ANSI-locked by a CI test that runs the merge
  closure on a second engine (H2); finalize macros may be dialect-scoped and labeled.

### 5.5 Derived facts as a seam, not a phase-2 rewrite

The target product architecture (one derivation, N consumers) is honored structurally from day
one without building the second consumer: each fold's emissions go through the `TableWriters`
seam, and the cut's `publish()` is already produce-before-commit. When the time comes, a
`DerivedFactPublisher` is added to `publish()` — appending the same emissions to event-bridge
topics (via `FrozenOutbox`), stamped with the same origin coordinates — and downstream products
consume the log instead of the tables. Nothing in the derivation plane changes shape; v1 simply
doesn't wire that publisher.

Similarly, HA is a configuration, not an architecture change: shard state can adopt the
streaming changelog (ADR 0009) for warm standby failover; until then, a moved partition rebuilds
from the log (C1) — correct, just slower.

## 6. Storage plane

- **`LakeCatalog`**: the only component that creates or opens tables. JdbcCatalog (H2 file for
  embedded; Postgres for multi-node) as both Iceberg pointer store and coordinator substrate.
  Owns: namespace bootstrap, `tableFor(TableSpec)` (create + publish spec + set retention
  properties), fingerprint guards, and the typed handle registry.
- **`CutCommitter`**: the one commit path — multi-table atomic swap (write-ahead metadata
  staging, CAS on `metadata_location`, deterministic table ordering, whole-batch re-stage on
  conflict, `CommitStateUnknown` never enqueues deletes, journaled delete intents). Stamping
  (offset / frontier / zbpos carry-forward) lives here and **only** here; every committer —
  cuts, compaction, snapshot exports — goes through it. `committedCoordinates()` (MIN across
  tables) is its read API.
- **`Maintenance`**: its own scheduler (never the derivation threads), coordinated with cuts
  through the same CAS discipline. Kind-aware passes: FACT/DICTIONARY day-scoped rewrite with
  the spec's sort; PARTIAL collapse via `PartialsSql` merge into coarser grids; manifest
  consolidation; snapshot expiry; orphan-file sweep (the journaled crash litter). Row-count
  verification before any swap; `validateFromSnapshot` always.
- **Rewrite engine**: v1 keeps an embedded DuckDB *inside `lake-storage` only* as the rewrite
  executor (read-sort-write, merge-collapse). It is an implementation detail behind a
  `RewriteEngine` interface — candidates to replace it later are the columnar encoder itself
  (for pure re-sorts) or an external job. No other plane may touch this connection.
- **FileIO**: local filesystem for embedded; S3/object-store FileIO is a config swap (Iceberg
  already abstracts it) — this, plus Postgres catalog, is the whole multi-node storage story.

## 7. Serving plane

- **Stateless reads** over the catalog: `ViewRegistry` discovers tables from the catalog, builds
  snapshot-consistent DuckDB views from `planFiles()` (never directory globs), refreshes on a
  schedule and on demand; open-state SNAPSHOT tables appear the same way as facts.
- **Spec-fed registries**: entities, dims, measures, windows, and algebra schemes come from the
  published specs (C2/C4) — `DESCRIBE`-sniffing does not exist. Percentiles, weighted averages,
  and count expressions come from `PartialsSql`.
- **A small typed SQL fragment layer** (not an AST): `TimeWindow`, `TimeBucket`, `Select`
  composer, filter/predicate builders with column whitelists, one `RowMapper`. All literal
  escaping through one choke point. Services are thin plan-builders; algorithms
  (changepoint, screening, cohort lift) are pure classes fed by query results.
- **Explain plane** as designed (rung ladder, typed findings with kind/claim/support/sql,
  cost gates on raw scans) — unchanged, but its tools consume the spec registry and fragment
  layer instead of hand-rolled SQL.
- **API discipline**: typed request/response records with one `ToolResponse<T>` envelope
  (body + sql + params), one `@RestControllerAdvice`, 400 for validation vs 500 for execution;
  row caps + truncation flags + statement timeouts everywhere; missing table ⇒ empty result.
- **One web client** (the React/design-system app). There is no embedded second UI; an ops
  status endpoint replaces it.

## 8. Exactly-once, end to end (the whole story in one place)

1. Every Zeebe record carries origin coordinates (Zeebe partition + position) via the connector.
2. The `OriginGate` drops duplicates before folding; its watermarks freeze into every cut.
3. A cut's publish registers files + offset + frontier + zbpos in one atomic catalog commit;
   replayed cuts are skipped by stamp comparison; skipped files are swept.
4. The same cut's persist writes state delta + offset transactionally; state can never be ahead
   of what its offset claims (the delta *is* the claim).
5. Crash anywhere ⇒ replay from persisted offset ⇒ re-publish is idempotent (3), re-fold is
   gated (2), non-idempotent folds (variant XOR) carry their own per-accumulator position guard.
6. Compaction re-stamps all coordinate families on every rewrite (carry-forward in
   `CutCommitter`, one copy).
7. The auditor (accuracy gate) recomputes partials from facts via `PartialsSql` and recomputes
   variant hashes via the scheme's SQL rendering — trust is verified, not assumed.

## 9. Deployment topologies

| Topology | What runs | When |
|---|---|---|
| **Unified node** | one Spring Boot app: `StreamRuntimeGroup` (derivation) + maintenance + serving; H2 catalog, local FS | dev, demo, small installs — the v1 default |
| **Split** | N derivation nodes (consumer group shares partitions; `StreamRuntimeGroup` per node) + M serving replicas; Postgres catalog, object store | growth path; no code change, composition only |
| **Facts-on-log** | derivation publishes derived-fact topics; other products consume the log; serving unchanged | when the second consumer exists |

## 10. What this design deliberately does differently from the PoC

| PoC | Greenfield | Why |
|---|---|---|
| Hand-rolled poll loop, rebalance, retry, per-table flush threads | `StreamRuntime` tasks + cuts | C4; deletes the largest bespoke-infrastructure surface |
| Evict-at-emit + commit later (crash window) | evictions ride the cut's state delta | exactly-once by construction, not by scheduled follow-up |
| Offset authority = Iceberg stamps only; RocksDB can run ahead | state+offset persisted per cut; stamps as cross-check + rebuild authority | removes the "state durably ahead of the lake" ambiguity |
| Schemas spelled 5–10×; specs implicit | `TableSpec`/`MetricSpec` in `lake-contract`, published in the catalog | C2/C3; readers get specs, not conventions |
| Two riders, dead generated SQL, serving re-implements algebra | one rider over `FactSource`; `PartialsSql` as the single interpreter | C5 |
| Two UIs, embedded HTML-in-Java | one client, one read service | — |
| Housekeeping multiplexed on the poll thread | maintenance owns a scheduler; punctuation for time-driven fold work | isolation of concerns and of failure |
| Backpressure policy in comments | `FactWriter` vs `DictionaryWriter` types | policy as type |
| ms/µs by caller discipline | timestamp writes typed at the writer boundary | units as type |

And what it deliberately keeps: Iceberg + Parquet + DuckDB as the storage/query trio; the L0
columnar sink's fixed-memory design (ring, field-id encoding, blooms, day routing); the
multi-table CAS commit coordinator; relational partials with SQL-as-merge; declaration-style
object types and closing rules; snapshot-consistent serving reads; caps/timeouts/escaping
discipline; the explain-plane design.

## 11. Build order (greenfield sequencing, each stage demoable)

1. **Contracts + storage core**: `lake-contract` specs/vocabulary/stamps, `LakeCatalog`,
   `CutCommitter`, spec publication; `lake-columnar` ring/encoder. Demo: seed rows through a
   test cut, query with DuckDB.
2. **Minimal derivation**: `LakeTask` on `StreamRuntime` with `OriginGate` +
   `InstanceLifecycleFold` only → instances/activities land exactly-once from a live stack;
   kill-and-replay test proves the cut story.
3. **Metric plane**: spec compiler, one rider, scalar+count algebras, PARTIAL tables +
   `PartialsSql`; the accuracy auditor lands *with* it, not after.
4. **Serving**: view registry + spec registry + query/tools + client; explain rungs 0–2.
5. **Full derivation**: variants, profiles, object fabric + lifecycle, definitions fold;
   histograms; cohorts; explain rung 3+.
6. **Maintenance**: kind-aware compaction incl. PARTIAL collapse, expiry, orphan sweep, gauge
   retention.
7. **Options as demanded**: changelog/standby HA, derived-fact topics, object-store + Postgres
   catalog, second-engine serving.

## 12. Open questions (decide during build, flagged now)

- **Cut cadence vs file size**: one cut per commit interval per shard produces more, smaller
  files than the PoC's per-table flush threads; PARTIAL/DICTIONARY tables may want in-cut
  batching thresholds and the compactor absorbs the rest. Needs a load-run to tune.
- **DuckDB-as-rewrite-engine longevity**: acceptable v1; the `RewriteEngine` seam exists so a
  pure-Java re-sort (encoder reuse) can replace it if the dependency footprint hurts.
- **Catalog under concurrency**: JdbcCatalog CAS on Postgres is proven ground (Iceberg
  community), but the coordinator's journal tables should be load-tested at split topology
  before that path is advertised.
- **Partials ANSI guarantee**: the H2 merge-closure CI gate is the enforcement mechanism;
  whether finalize macros stay DuckDB-scoped or grow a renderer decides itself when a second
  serving engine actually arrives.
- **Object partition-locality**: v1 assumes it (documented failure mode: double-counted
  births). The shuffle/`aggregate` machinery in `event-bridge-streaming` is the escape hatch if
  a real workload breaks the assumption — that is the future tracer tier, not this system.
