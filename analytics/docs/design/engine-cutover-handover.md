# Handover — analytics engine → DAG cutover (tasks #38–41)

> Start a fresh session with this. Goal: make the analytics pipeline a **declared
> `ProcessorTopology` (DAG)** on the `event-bridge-streaming` substrate, per ADR 0003 — as **one
> coordinated cutover, green at the end, no hacks / shims / parallel cruft**.

## 0. Branch & working conventions

- Branch `roman/optimize`. Commit per coherent green step. Commit trailer:
  `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`.
- Before every commit touching Java/pom/md: `./mvnw license:format spotless:apply -T1C`, then the
  module `verify`.
- No inline FQNs (always import); license header on every new `.java`; no Kafka/KIP mentions in new
  comments/javadoc.
- After any class move/rename: `./mvnw clean -pl <module>` before trusting `-Dquickly` test runs
  (stale `target/` classes give phantom `NoClassDefFoundError`s — hit twice already).

## 1. Read first (context is already written down)

- **ADR 0003** `analytics/docs/adr/0003-engine-layering.md` — layering + operator model + Model‑F
  durability (authoritative). ADR 0002 = engine internal structure.
- **Memory** `analytics-engine-structure-plan.md` — the full cutover plan + critical findings.
  `analytics-module-restructure.md` — the module layout already in place.
- **Reference examples** `analytics/docs/design/examples/{kafka-streams,flink,spark}/` — same
  pipeline in each engine + an "ownership map" README. Flink is the closest analogue.

## 2. Design (decided — do not re‑litigate)

- **Three layers.** L1 `event-bridge-streaming` = substrate (operator SPI, DAG, windows,
  AggregateFunction, segment/dedup, state, CommitBarrier, StreamRuntime — already Flink/KS‑shaped).
  L2 `analytics-engine` = domain operators + vocabulary, workflow‑engine internal structure. L3
  `event-bridge-analytics` = declares the topology; thin.
- **One operator model.** `Processor` (has `init/process/flush/checkpoint/needsCheckpoint/close` —
  lifecycle already added, committed `9fd3197d4ce`) + `ProcessorTopology` = the single `Stage`.
  Retire `fold/Projector`, `fold/Collector`, `aggregate/Aggregation`, `ProjectionStage`.
- **Durability = Model F (Flink/KS), not Model R.** The runtime checkpoints ALL operator state
  (incl. a windowed aggregate's OPEN segment) + the FULL processed offset in one per‑partition
  transaction → committed offset == checkpointed state (one consistent cut, **no reconcile**).
  Sealed deltas are published BEFORE commit; downstream origin‑dedup covers only the publish→commit
  gap. **The runtime's generic path already IS Model F**: `StreamProcessor` (ownsDurability=false)
  → `transaction { offsets.store(processed); checkpoint() }`, and `OffsetStore` is state‑backend‑
  backed (co‑committed). So there is **no new commit machinery** and **no `safeOffset`** — delete
  `Task.ownsDurability`/`commit`/`restore` + the shards.
- **Base projection = Model A** (materialized element entity, apply‑then‑derive, evict‑after‑emit)
  for **legibility only** — Model F already gives correctness (the old shard fold‑ahead bug was a
  Model‑R/`safeOffset` artifact). **Do not over‑engineer base‑projection durability**; it
  checkpoints its stores plainly.

## 3. Ordered plan (each step green + committed)

**Step 1 — #38: generalize + move the shuffle format to L1.**
The envelope is already structurally generic: `CellDelta(int aggId, long windowStart, byte[] key,
byte[] payload)` + `ShuffleEnvelope(producedAt, schemaVersion, producerPartition, segment, chunk,
moreChunks, payloadKind, operation, cells)`. Only naming/packaging is analytics‑coupled. Rename to a
domain‑neutral segment‑delta record + segment‑shuffle codec in `event-bridge-streaming` (`aggId` =
generic stream id; key/payload opaque). **Relocate SBE codegen**: it's the real‑logic `sbe-tool`
`exec` plugin in `analytics/analytics-engine/pom.xml` (~lines 122–132) generating from
`analytics/analytics-engine/src/main/resources/sbe/analytics-shuffle.xml`; `event-bridge-streaming`
has none — add the plugin + move/rename the XML (schema id can stay). Repoint the app
(`EnvelopePublisher`, `CubeShuffleSink`, `EnvelopeTransport`, Stage‑2 decode). Delete
`analytics-engine/.../shuffle/`.

**Step 2 — #40 prerequisite (the one substantive Model‑F change): checkpoint the open segment.**
`event-bridge-streaming/.../aggregate/SegmentSealingAggregation.java` — `checkpoint()` is a no‑op
today (~line 116, a Model‑R assumption). Make it durably persist the in‑memory open segment (the
`open` map of `Windowed<K> → ACC`) into a state store, so committing the full offset never loses the
open partial. Then wrap `(gate + AggregateFunction + seal)` as a windowed‑aggregate `Processor`
node.

**Step 3 — #39: base projection as a `Processor<SourceRecord, Fact>`** with workflow‑engine
internals (all in `analytics-engine`):
- `record/` — `SourceRecord` + the canonical `Fact` (Fact stays in `analytics-model`).
- `state/immutable/ProjectionState` (read) + `state/mutable/MutableProjectionState` (write) +
`StateBackedProjectionState` (refactor of `StateBackedProjectionStore`) + move
`AnalyticsColumnFamilies` here + **zeebe‑style variables per `(instanceKey, name)`** (reshape
`PersistedVariable(s)`, today a per‑instance map blob).
- `projection/` — `AnalyticsBaseProjection implements Processor` + `dispatch/` (a `(ValueType,
Intent)` registry) + `applier/` (sole mutators; upsert the element entity row) + `derive/` (read
the updated row → `context.forward(fact)`) + `behavior/VariableEnricher`.
- Model A: materialized element entity `{start, end, status, isProcess}`; apply‑then‑derive;
evict‑after‑emit; **event‑time SLA sweep** via a `STREAM_TIME` punctuator over a **deadline
secondary‑index CF** (KS has no per‑key timer → scan; the index avoids full scans, per the
event‑bridge control‑plane engines).
- Fact API: `Fact.builder(FactType).eventTime(ts).source(part,pos).transition(Transition).field(name,val).build()`;
`FactType {PROCESS_INSTANCE, ELEMENT, INCIDENT, PROCESS_DEFINITION}`;
`Transition {ACTIVATED, COMPLETED, TERMINATED, CREATED, RESOLVED, DEPLOYED}`.

**Step 4 — #41: cutover.**
`event-bridge-analytics` builds the analytics `ProcessorTopology` (Stage 1: source → base‑projection
processor → per‑cube windowed‑aggregate node → shuffle sink; Stage 2: shuffle source → merge →
serving sink) and runs it via the **generic** runtime path (`StreamProcessor` + `Stage`,
ownsDurability=false). **Delete**: `ProjectionStageTask`, `AggregationStageTask`,
`Task.ownsDurability/commit/restore`, all `safeOffset`, `fold/Projector`, `fold/Collector`,
`aggregate/Aggregation`, `ProjectionStage`, the old `AnalyticsFactProjector` + derivers. Full‑repo
green.

## 4. Verify

- Module: `./mvnw verify -pl <module> -DskipTests=false -DskipITs -Dquickly` (clean first after
  moves).
- Full repo: `./mvnw install -Dquickly -T1C`.
- Standard datasets are seeded by `StandardDatasets` (analytics-serving); the webapp reads via the
  executor. ES/OS end‑to‑end parity remains the deferred joint e2e — do not attempt it here.

## 5. Already done this arc (committed green, don't redo)

ADRs 0002/0003; KS/Flink/Spark examples; `Processor` operator lifecycle; Model‑F durability decision
(fold‑ahead bug retired). The module restructure (model/serving/engine/stores/app), engine
legibility (`FactDeriver` registry), backend selection, and the webapp read rewire were all landed
earlier and are green.
