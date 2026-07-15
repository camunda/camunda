# Optimize analytics pipeline (MVP) — handover

A handover for building a streaming **pre-aggregation pipeline** on top of the
Event Bridge. Source vision: `~/Downloads/Optimize _aufbohren_.pdf` (deck "Optimize aufbohren",
R. Smirnov). The deck argues Optimize's read-optimized, ad-hoc-query-over-unbounded-data design
hits a scaling ceiling, and proposes a **Kappa architecture**: treat the OC export as a continuous
stream and continuously pre-aggregate it into read-optimized datasets, so dashboard queries become
request/response lookups. It also calls out the current importer's operational pain: not HA, can't
scale horizontally, and shares the ES cluster with the OC.

First end-to-end increment: one fact — **process-instance execution time** — through the full
pipeline.

## FINAL DIRECTION (supersedes the in-broker design below)

After exploring the trade-offs, the architecture is **consumer-based, self-contained,
streaming-framework-style library** — *not* baked into the broker. Rationale: streaming-analytics systems
deliberately separate compute from the log (Flink, Spark and similar all consume the bus, none are
baked into it); in-broker also hit a hard publish-path problem (no programmatic cross-partition
producer in the broker). The already-built `StandaloneAnalyticsPipeline` IS this design.

**Current state — build on this, nothing to revert.** The in-broker approach was only ever design +
read-only spikes; **no in-broker code was written**. All six analytics commits (`d53faf5..a5ae32f`)
are consumer-based / framework-agnostic and are the foundation of this library: the timestamp fix,
the projector fold, the fact + codec, the aggregator + dedup, the RocksDB store, and the
`StandaloneAnalyticsPipeline`/`FactSink`/`EventBridgeFactPublisher` consumer runner. The analytics
module does not depend on `event-bridge-broker`. The `event-bridge-broker/partitioning/steps/*`
files are the bridge's own pre-existing data-partition machinery, unrelated to analytics. Start
from Phase 1 on top of the existing module.

**Correctness lives in the data model, not the transport** — nail these and the shuffle mechanism
becomes a swappable optimization:
1. deterministic, replayable derivation (a fact is a pure function of the source — no wall-clock /
randomness / nondeterministic lookups in the fold);
2. every fact carries its source coordinate `(sourcePartitionId, sourcePosition)` (done);
3. idempotent sink keyed by that coordinate (the per-source-partition watermark — done);
4. rewindable source + checkpointed offsets/state as the recovery backbone;
5. single-writer per key via co-partitioning;
6. an explicit guarantee: **effectively-once via idempotency**, NOT exactly-once-via-barriers.

**Shuffle / aggregation is a swappable strategy:**
- **Route A — DB-as-merge (default, what's built):** each instance pre-aggregates locally and upserts
partials into H2 (commutative count/sum/min/max), committing `{partial + watermark}` atomically
with its own source offset. No shuffle, no fact-topic required, exactly-once into H2. Correct for
all *commutative* rollups — keep it as the fast path.
- **Route B — in-engine shuffle (only when needed):** joins, windows, non-commutative metrics
(p95/distinct/sessions/top-N/CEP) need co-located keyed state, so they require a re-key shuffle
(one shared `defKey`-typed fact-topic, or direct transport) + keyed state + event-time timers +
watermarks. **DO NOT build this until the first operator that needs it.** When built, copy
**Spark's micro-batch checkpoint model** (per-batch `{offset range → run → state → sink →
commit}`, re-run uncommitted batch on crash) — NOT Flink's aligned barriers. The invariant:
**never commit a source offset past what is durably captured downstream** (aggregate watermark +
the base projection's open-instance state). Build the **micro-batch** model first and **benchmark
the real workload** before investing in Flink-style continuous + credit-based backpressure — the
durable bridge absorbs bursts as consumer lag (events wait in the topic, no memory blow-up), so a
backlog is non-catastrophic; go continuous only if micro-batch demonstrably can't stay balanced
under sustained rate (the deck's "PoC + benchmark" gate).
- **Coordination — prefer decentralized, NOT a global batch driver.** Our aggregations are
**per-key independent** (no metric needs a consistent cut across all `defKey`s at one instant),
so skip Spark's global synchronized batch + driver. Instead: each downstream operator
checkpoints `{its keyed state + per-source-partition watermark}` on its own schedule and reports
its durable watermark back; each source commits its offset only up to `min(downstream
watermarks)`; recovery = rewind to the watermark, replay, and the source-coordinate dedup drops
anything already applied. This works for non-commutative state too (replay is deterministic +
single-writer per key). Reuse the bridge's existing consumer-group/metadata coordinator for
**assignment only**; add just a small watermark-feedback path. Build a Spark-style global-batch
coordinator ONLY if a future metric genuinely needs cross-key consistency (rare here).

**Do NOT build:** in-broker partition wiring; a log of what the leader emitted (emitted-watermark
control-record) — both are artifacts of the rejected in-broker/follower-replay model.

**Operator API to design when generalizing:** keyed `process(record)` + `onTimer` + keyed-state
handles + watermark input (Flink `KeyedProcessFunction` / Spark `transformWithState` shape), with
DB-merge as one strategy underneath. One shared base projection, consumed once, with each fact a
fan-out branch (`ProcessInstanceProjector` → shared projection processor + `ExecutionTimeExtractor`).

### Build breakdown — phased (defer the shuffle as long as possible)

Status against the current module: `[done]` exists; `[extend]`/`[new]` to build.

- **Phase 1 — commutative windowed metrics** (counts/sums/min/max/avg; e.g. "last 1h = 100
  completed"). DB-merge with the **window as part of the key** `(dim, windowBucket)`; no shuffle, no
  coordinator. Tasks: multi-instance **consumer-group** membership for source assignment `[extend
  StandaloneAnalyticsPipeline]`; window-in-key + event-time watermark for "is this bucket final"
  `[new]`; per-instance commit of `{aggregate + position}` in one H2 txn `[done in spirit —
  ExecutionTimeAggregator]`. Most of the engine (`ProcessInstanceProjector`, `RocksDbBaseProjectionStore`,
  `ExecutionTimeAggregator`+dedup, codec, `FactSink`) is already built and carries over unchanged.
- **Phase 2 — mergeable-sketch metrics** (approx p95 via t-digest, approx distinct via HLL): add
  sketch state types + sketch-merge in the DB-merge path `[new]`. **Still no shuffle.**
- **Phase 3 — order-dependent / non-mergeable** (sequences/CEP, exact distinct/median, top-N,
  cross-partition last-value): now add the **shuffle** (assignment routing table + direct transport
  over the bridge's Netty + serde/batching/backpressure) `[new]`, per-operator checkpoint
  `{keyed state + commit-watermark}` → object store `[new]`, and the decentralized
  watermark-feedback recovery `[new]`.

**Operator/runtime API to introduce when generalizing (Phase 1+):** `process(key, rec, ctx)` +
`onTimer` + keyed-state handles (value/list/map/sketch) + event-time watermark input
(`transformWithState` shape), with DB-merge as one strategy underneath. Two distinct watermarks —
**event-time** (window finalization/lateness/eviction, from the preserved engine timestamps) and
**commit** (downstream→source, gates offset commit; Phase 3 only). State = ephemeral RocksDB cache;
durability remote (object-store checkpoints + RDBMS results + the rewindable bridge). Coordinator =
**assignment only** (reuse the bridge's consumer-group/metadata coordinator); a global-batch driver
is built ONLY for a cross-key consistent cut, which time-windowed counts/percentiles do not need.

Everything below documents the **superseded in-broker design** — kept for context only.

## (SUPERSEDED) Architecture: in-broker, co-located, per-partition stateful processor

The analytics processor runs **inside the Event Bridge broker**, co-located with the source topic
partition — not as an external consumer. This is the key decision; everything else follows.

```
zeebe-records-P ──▶ [in-broker Stage-1 projector on P's leader] ──▶ fact-topic ──▶ [in-broker Stage-3
  (existing topic)    base-projection state via SPI:                 (new topic,     aggregator on
                      RocksDB local cache (+ optional                 keyed by        Q's leader] ──▶ H2
                      external backing store)                         defKey)         aggregate state +
                                                                                      leader-only write
```

### Why in-broker (and why this dissolves the leader/follower problem)

- **Ownership = the source partition's Raft leadership.** The broker node that leads
  `zeebe-records`-P also runs the analytics fold for P. No separate consumer group, no separate
  election — the projector inherits the partition's leadership and failover.
- **Reuse-source works with HOT followers by running the fold on every replica.** A standalone
  consumer folding the source has no log for its own followers to replay. Co-located with the
  partition, every replica already receives the source log via Raft; running the *same deterministic
  fold on each replica* (not just the leader) keeps follower state warm — no changelog, no separate
  consumer group, and (importantly) *not* dependent on the partition's snapshot/InstallSnapshot,
  which data partitions don't have (see spike below). Warmth comes from folding on followers, not
  from shipping snapshots.
- **Shape = a stateful Zeebe exporter:** runs on the leader, reads the committed log, tracks a
  consumed position, resumes on failover — but keeps its own RocksDB state instead of pushing out.
- **Self-contained UX:** users enable analytics via config; nothing extra to deploy.
- **Does NOT recouple to the OC.** The deck's "importer impacts OC" pain is about sharing the *ES
  cluster* with OC. The bridge is already a separate substrate OC exports to; analytics in the
  bridge stays off the OC.

### Accepted trade-off

Analytics compute shares bridge nodes and **scales with bridge partitions, not independently**.
Mitigate with a dedicated actor/thread pool, failure isolation (an analytics bug must not crash the
log), and optionally running heavy work on followers. Accepted: the deck's priority is HA +
operational simplicity, which this delivers.

## Design decisions & rejected alternatives (do NOT relitigate)

- ✅ **In-broker co-located processor** (above).
- ❌ **Separate consumer group + checkpoint topic / shared storage** (Flink/KS style, external
  consumer): rejected — heavy for users to operate, and reintroduces the consumer-side
  leader/follower + checkpoint-location problem that co-location removes.
- ❌ **Own dedicated Raft log for analytics** (a second log): rejected — the source topic is already
  a replicated log; a second one is artificial duplication.
- ❌ **Changelog-topic per state store** (a common streaming-framework default): rejected — write amplification; the
  rewindable source + co-located snapshots cover recovery without it.
- **State backends** are an **SPI** with two operational modes (the SPI hides get/put + position;
  the replication model around it differs):
  - **Local mode** (RocksDB authoritative): *every replica folds* the source into its own RocksDB;
    warmth comes from follower-replay. Followers know the consumed position for free (they replayed
    it); they learn the *emitted* position either implicitly (don't need it — Stage 3 dedup handles
    re-emission) or via an emitted-watermark control-record on the log.
  - **Outsourced mode** (external store authoritative, RocksDB = read-through/write-back cache):
    *only the leader folds*; followers go passive. Warmth on failover comes from the shared store
    (new leader attaches, cold cache refills read-through) — no follower-replay needed. The
    consumed position lives in the store, written near-atomically with state. This is the path for
    long-running instances whose start entry may be evicted from the hot cache but still lives in
    the store. With write-back, advance the consumed-position checkpoint only once state is durable.
    Earlier "RocksDB *and* RDBMS both" is now **one SPI, two backings/modes**, not two pipelines.
- **Fact stream backend** is pluggable: Event-Bridge topic (default) or RDBMS (low workload).
- **Hot followers are inherent, not a later stage.** Every replica of the partition folds the
  committed log into its own co-located state, so followers are warm by construction (the bridge's
  Raft already ships the log to them). The ONLY role-gated behavior is **external side-effects**
  (publishing to the fact topic, writing H2) — done on the leader only. There is no separate
  "leader-only first, add followers later" split.

## Already DONE & committed

- `d53faf5dffd` `feat: carry the original event timestamp …`: `ZeebeRecordCodec` now carries the
  **engine event timestamp** in the payload frame (`timestamp(8) | metadataLength(4) | metadata |
  value`, LE) instead of defaulting to `-1`. Execution-time math needs true engine event time,
  independent of export/backlog lag. Relevant to any consumer of `zeebe-records` — internal
  (in-broker projector deserializes the same payload) or external. File:
  `event-bridge-zeebe-connector/.../ZeebeRecordCodec.java`.

## Stage mapping & data models

**Stage 1 — Base Projection** (in-broker, keyed RocksDB state)
- Read committed `zeebe-records`-P entries on the leader; deserialize via `ZeebeRecordCodec`.
- Act only on the **root process element**: `bpmnElementType == PROCESS`
(`processInstanceKey == elementInstanceKey`).
- `ProcessInstanceIntent.ELEMENT_ACTIVATED` → `startTime = record.getTimestamp()`.
- `ELEMENT_COMPLETED` / `ELEMENT_TERMINATED` → `endTime`, set outcome.
- State keyed by `processInstanceKey` → `{ bpmnProcessId, processDefinitionKey, version, tenantId,
startTime, endTime, terminated, factEmitted }`. Persist consumed source position in the same state.

**Stage 2 — Fact derivation → fact topic** (in-process produce)
- On first transition to complete (have both timestamps), emit immutable
`ProcessInstanceExecutionTimeFact { processInstanceKey, processDefinitionKey, bpmnProcessId,
version, tenantId, startTime, endTime, durationMs, completedNormally }`.
- `factEmitted` flag guards against re-emission on replay.
- Produce to **fact-topic** keyed by `processDefinitionKey`. MVP serialization: JSON.

**Stage 3 — Aggregated Dataset** (in-broker on fact-topic, → H2)
- Aggregate per `(processDefinitionKey, version, tenantId)`: `instance_count`, `total_duration_ms`,
`min/max_duration_ms` (avg derived at read).
- H2 tables: `proc_inst_exec_time_agg(process_definition_key, bpmn_process_id, version, tenant_id,
instance_count, total_duration_ms, min_duration_ms, max_duration_ms)`.

## Correctness

The log is read at-least-once across leader failover; the fold must be idempotent / replay-safe.

- **The analytics processor is both consumer AND producer.** Stage 1 consumes `zeebe-records` and,
  on the leader, *writes* facts to the `fact-topic` — that topic IS the inter-stage handover.
- **The fact topic is also a re-partition (shuffle).** Stage 1 state is keyed by
  `processInstanceKey`; Stage 3 aggregates by `processDefinitionKey`. Producing facts keyed by
  `processDefinitionKey` re-routes them to the fact-topic partition (often a different node) whose
  leader runs the matching aggregator — the standard "repartition topic between a per-key transform
  and a grouped aggregation." You can't aggregate per-definition inside a per-instance partition.
- **Consumed position is part of the state** and advances with it; both leader and followers fold,
  so a promoted follower already has warm state and resumes exactly where it left off.
- **Stage 2 fact production is at-least-once, NOT exactly-once.** True exactly-once would require a
  transaction spanning the publish to the `fact-topic` (a *different* Raft group) and the source
  consumed-position commit — the bridge has no cross-partition transaction, so it isn't pursued.
  The `factEmitted` flag (replicated, folded on all replicas) suppresses re-emission for the
  fully-folded prefix, but a promoted follower lags the old leader and will re-emit facts for the
  tail the old leader published beyond the followers' fold point → bounded duplicates at failover.
- **Dedup is done at Stage 3, keyed by source coordinates.** Each fact carries
  `(sourcePartitionId, sourcePosition)` of the completion record that derived it. The aggregator
  keeps a per-source-partition high-watermark in its own ZeebeDb and, in the *same* transaction as
  the aggregate update, drops any fact at/below the watermark → effectively-exactly-once
  aggregation despite at-least-once delivery. Bounded state (one watermark per source partition).
  Assumes per-(source→dest-partition) order is preserved; else use a bounded reorder window.
- Producing facts and writing H2 are **leader-only side-effects** (followers fold state, never
  publish/write).
- **No durable fact queue inside Stage 1.** Facts are a deterministic function of the committed
  source log, so they are recomputed by replay — not stored to avoid loss. The buffer between the
  fold and the fact-topic produce is an in-memory publish buffer (decouple fold from async network
  produce + batching). Correctness rule: advance the source consumed-position only past records
  whose facts the fact-topic has **acked**; if the topic is down, Stage 1 backpressures rather than
  losing facts. The durable inter-stage queue IS the fact-topic. v1: publish-then-advance (depth-1);
  async batched buffer with an in-flight low-watermark is a refinement.
- Ultimate cold-start fallback: replay `zeebe-records` from offset 0 — correct because the fold is
  deterministic, just slower.

## Extension point (decided by spike)

Data partitions are **leader-only**: `PartitionLifecycle` has only `leaderSteps`; non-LEADER roles
`stepDown()`. `LogStorageStep` requires the Raft appender, so followers set up no log reader. BUT
the Raft `CommitListener` (`RaftContext`) has no role check — it fires on all roles — and
`RaftPartitionLifecycle` already proves followers can read committed entries with a NOOP appender +
`server::openReader` + `addCommitListener`.

Chosen approach (spike's recommendation, NOT making data partitions full state machines — that's
~2000 LOC reimplementing the engine): a **co-located component** added to the partition step
lifecycle that owns its own `ZeebeDb`, reads the committed log via `EventStreamReader` +
`CommitListener`, and persists its own consumed position. To get warm followers, add a
**`followerSteps` path** (a read-only `FollowerLogStorageStep` with a NOOP appender + the analytics
step run on both roles) — ~150 LOC of lifecycle change over leader-only. Side-effects (fact produce,
H2 write) stay leader-only.

Concrete API map (2nd spike): `PartitionStartupStep` = `getName()` + default `prepare(ctx)` +
`activate(ctx)/deactivate(ctx)` returning `ActorFuture<Void>`. `PartitionContext` exposes
`getLogStorage/getRaftPartition/getActorScheduler/getMessagingService/getPartitionId/...` and
setters. Reader: `EventStreamReader(logStorage.newReader())`, seek/hasNext/next/position/timestamp/
copyTo + `BatchReader.read(bytes)` → entries `(position, key, value)`; the value is the
`ZeebeRecordCodec` payload. Insert `AnalyticsProcessorStep` into `PartitionLifecycle.leaderSteps`
after `PublishRequestHandlerStep`. Config via a new `AnalyticsProperties` on `EventBridgeProperties`
threaded through `PartitionBootstrapper.startDataPartition` → `PartitionLifecycle`.

**THE FORK — how to publish facts to the fact-topic from inside the broker.** The broker is NOT a
programmatic cross-partition producer: publishes only arrive via the gateway HTTP API → routed by
MessagingService → `PublishRequestHandler`. Two options:
- (loopback-messaging) replicate the gateway's publish path internally — build a `PublishRequest`,
look up the fact-topic partition leader from topology, send via `MessagingService`. No extra hop,
but non-trivial and the PublishRequest wire format + leader-lookup APIs need mapping.
- (client) use `EventBridgeClient` against the local gateway — fully-known API
(`publishToTopic`/`fetchFromTopic`), simplest to land, but an HTTP hop + client dep inside the
broker.
The cheap consumer-side equivalent fork: standalone `ZeebeRecordListener` consumer (easy, testable,
but consumer-group-based, not partition-leadership-based) vs the in-broker committed-log reader
(matches the chosen architecture, needs the shared-broker surgery above + a running cluster to
validate).

## Work breakdown (dependency order)

1. ✅ **Spike** — done (above).
2. ✅ **Module skeleton** — `event-bridge/event-bridge-analytics` created, registered, builds green;
   domain types `ProcessInstanceExecutionTimeFact`, `ProcessInstanceProjection` in place.
3. **Projection state SPI** — `BaseProjectionStore` (keyed get/put + consumed position, unit-of-work)
   with an in-memory impl (for fold tests) and a `ZeebeDb`/RocksDB impl (column families +
   `ProcessInstanceProjection` DbValue). External-backing impl deferred.
4. **Stage-1 projector fold** — `ProcessInstanceProjector`: ZeebeRecord → root-element filter
   (`bpmnElementType == PROCESS`) → upsert projection → derive fact on complete. **Unit-tested
   offline** (records in → fact out), no cluster.
5. **Fact codec + producer** — JSON codec for `ProcessInstanceExecutionTimeFact`; producer to
   `fact-topic` (`EventBridgeClient`), keyed by `processDefinitionKey`; `factEmitted` guard;
   publish-then-advance (depth-1) for v1.
6. **Stage-3 aggregator + H2** — `proc_inst_exec_time_agg` schema; fold facts → per-source-partition
   dedup watermark + aggregate upsert in one JDBC txn. **Unit-tested offline** (facts in → H2 row).
7. **Broker wiring** — `AnalyticsProcessorStep` + actor on the data-partition leader path (read real
   committed log, produce to real `fact-topic`); then the `followerSteps` path for warm followers.
8. **End-to-end smoke** — deploy/run a process against a Zeebe wired to the bridge exporter; assert a
   row in `proc_inst_exec_time_agg` with the expected count and a plausible duration. Mirror
   `event-bridge-examples`.

Staging for "make it work": items 3–6 are offline-testable engine logic (no cluster); item 7 is the
broker integration; warm-follower path and snapshots are the refinement after it works end-to-end.

### Status (implemented)

Items 2–6 plus a runnable pipeline are DONE and committed on `roman/optimize` (19 module tests
green):
- `d53faf5dffd` original event timestamp carried through the connector codec.
- `049a6f54d2a` Stage-1 fold (`ProcessInstanceProjector`, `BaseProjectionStore` SPI +
`InMemoryBaseProjectionStore`, fact/projection records).
- `8ef8b68530f` Stage-3 `ExecutionTimeAggregator` → H2 with per-source-partition exactly-once dedup.
- `fb922c26922` fact codec + offline end-to-end test (records → projection → fact stream → H2).
- `5c51b172def` `RocksDbBaseProjectionStore` (local cache; ZeebeDb; flush-on-close; reopen-tested).
- `a5ae32fcb09` `FactSink` seam + `EventBridgeFactPublisher` + `AnalyticsPipeline` +
`StandaloneAnalyticsPipeline` — the whole pipeline runs against a gateway as a standalone process.

**Remaining = item 7, the in-broker embedding** (so leadership/HA come from the source partition's
Raft rather than a consumer group): `AnalyticsProcessorStep` + actor reading the committed log via
`EventStreamReader`/`CommitListener`, the `followerSteps` path for warm followers, config +
bootstrap threading, and resolving THE FORK above (in-broker publish: messaging loopback vs client).
This needs a running cluster to develop against; the standalone runner is the validation path until
then.

## Deferred (post-MVP)

External state-backing SPI impl (RDBMS/object store, with RocksDB as cache). **Emitted-watermark
control-record**: the leader appends into the source log the highest source coordinate whose facts
are confirmed acked; followers fold it for free into a local `emittedThrough`, so on failover the
new leader re-emits only the bounded window `(emittedThrough, consumedThrough]` instead of relying
purely on dedup (dedup stays the backstop). Only advance the watermark once all facts ≤ X are acked
(under-claim safe, over-claim drops facts). In outsourced mode the watermark lives in the external
store instead. Later: RDBMS fact-stream backend; SQL-like dataset declaration parser ("think
backwards"); dimension backfill; retention/compaction of projection + facts; more facts/dimensions.

## First user-facing increment: UI to define a dataset + a report — DONE (v1)

Implemented as **`analytics/analytics-webapp`** (commit `bf3717ceec6`): a small Spring Boot app — a
declare/read REST API over the windowed H2 dataset + a static page. Loop verified (unit tests + boot
smoke): **declare dataset → build report → view** returns N-completed + avg-duration per process per
window, with an optional process filter; the page serves at `/`.

Run it:

```
mvn -q -pl analytics/analytics-webapp dependency:build-classpath -Dmdep.outputFile=/tmp/wcp.txt
java -cp "analytics/analytics-webapp/target/classes:$(cat /tmp/wcp.txt)" \
  -Danalytics.dataset.url='jdbc:h2:file:./data/analytics-dataset' -Danalytics.dataset.user=sa \
  io.camunda.analytics.webapp.AnalyticsWebappApplication      # open http://localhost:8090
```

**Live-data wiring — DONE (commit `4fd4ed37d39`).** The webapp hosts an H2 TCP server over its data
dir; the pipeline writes to the same DB over TCP, and the UI reflects it live (verified: external
writer over TCP → report endpoint updates with no restart). The report view also renders an SVG bar
chart of completed-per-process-per-window. Run both, sharing the DB:

```
# webapp: host the H2 server + serve the UI
java -cp "analytics/analytics-webapp/target/classes:$(cat /tmp/wcp.txt)" \
  -Danalytics.dataset.server.port=9092 -Danalytics.dataset.server.dir=./data \
  -Danalytics.dataset.url='jdbc:h2:tcp://localhost:9092/analytics-dataset' -Danalytics.dataset.user=sa \
  io.camunda.analytics.webapp.AnalyticsWebappApplication            # → http://localhost:8090
# pipeline: write to the same DB over TCP (plus the usual -Dgateway=… for the bridge)
java <jvm-flags> -cp "analytics/event-bridge-analytics/target/classes:$(cat /tmp/acp.txt)" \
  -DjdbcUrl='jdbc:h2:tcp://localhost:9092/analytics-dataset' -DjdbcUser=sa \
  io.camunda.eventbridge.analytics.StandaloneAnalyticsPipeline
```

Production integration into the OC webapp (React/TS) is the follow-up; this standalone app is the
demoable loop.

Original goal (2026-06-30): a very-first, not-perfect end-to-end UI where a user can (1) define a
dataset, (2) define a report on it, (3) view it. Correctness over polish.

### Dataset-driven pipeline — Step A DONE (commit `24ccbbb7e0b`)

Creating a dataset now *drives* the pipeline (was cosmetic). The windowed table is keyed by
`dataset_id`; the aggregator folds each fact into one cell **per declared dataset**, bucketing by
that dataset's **window** — so hourly vs daily datasets get independent rollups while sharing the
one fact stream. `DatasetRegistry` reads `analytics_dataset` from the shared H2 (webapp-written);
the pipeline refreshes every 5s, so a dataset declared in the UI is picked up live (forward-only).
Reports scope to their `dataset_id`. **Dimensions stay fixed (definition/version/tenant) — only the
window is dataset-driven in Step A.** Next: **Step B** pluggable facts (each fact an extractor on the
shared base projection), **Step C** dimension-driven grouping (drives projection-schema evolution;
forward-only vs backfill), **Step D** the SQL-like declaration parser.

This is a new **frontend + a thin read/declare API** track on top of the analytics serving store
(the queryable RDBMS dataset Phase 1 produces). Minimum viable shape:
- **Declare-dataset**: a small form/spec choosing the fact (execution time), the grouping dimensions
(definition, optionally version/tenant), and the window (e.g. hourly). For v1 this can map to the
existing `proc_inst_exec_time_window` table (or a named view over it) rather than a full SQL-like
parser — the parser ("think backwards" declaration) is the later, richer version.
- **Build-report**: pick a dataset + a visualization (a table or a simple bar/line of
`completed_count` / avg duration per definition per window) + basic filters (definition, time
range).
- **Serve**: a read API over the serving RDBMS (`SELECT … GROUP BY definition, window` — the
GROUP-BY-over-partials view discussed) feeding the report; near-real-time as the pipeline updates.
- **Where**: align with the repo's frontend conventions (`webapp/client` / Optimize frontend) and a
read controller in a `service`/REST layer; reuse the H2/RDBMS dataset as the query source.

Keep it minimal: one fact, one dataset shape, one or two chart types. The point is the full loop —
declare → pipeline fills the dataset → report renders — working end to end.

## Build / test (per AGENTS.md)

```bash
./mvnw license:format spotless:apply -T1C                       # mandatory before every commit
./mvnw install -pl event-bridge/event-bridge-analytics -am -Dquickly -T1C
./mvnw verify -pl event-bridge/event-bridge-analytics -Dtest=<Test> -DskipTests=false -DskipITs -Dquickly
./mvnw install -Dquickly -T1C                                    # full repo still compiles
```

Conventional Commits, no scope, ≤120-char header, explain *why*. Separate refactor from behavioral
commits. jspecify `@Nullable`/`@NullMarked` in a separate `refactor:` commit.

## Key file/class references (verified)

- **Stateful partition machinery to mirror** (`event-bridge-stream`):
  `ReplicatedStream.java` (extension points: `logName`, `recordValues`, `createRecordProcessor`,
  `onStarting`, `listener`), `RaftPartitionLifecycle.java` (role changes, `ensureStateRecovered`,
  `AsyncSnapshotDirector`, `DbPositionSupplier` → `LAST_PROCESSED` CF), `RecordProcessingEngine.java`
  (`process()` leader / `replay()` follower), `RecordProcessors.java`, `Writers.java`,
  `StateWriter.java` (`appendFollowUpEvent`), `EventAppliers.java` / `TypedEventApplier.java`
  (keyed by `intent.value()`).
- **Worked examples:** `event-bridge-cluster-metadata/.../stream/MetadataStream.java` +
  `MetadataPartition.java` + `state/MetadataColumnFamilies.java` + `record/MetadataRecordValues.java`
  (singleton stateful); `event-bridge-consumer-groups/.../stream/CoordinatorStream.java` +
  `CoordinatorPartition.java` (sharded stateful).
- **Data partition (raw log) + bootstrap:**
  `event-bridge-broker/.../partitioning/PartitionLifecycle.java` (data partition, no state machine),
  `PartitionFactory.java`, `bootstrap/PartitionBootstrapper.java` (hard-coded groups: coordinator,
  metadata, per-topic; role listener wired AFTER Raft bootstrap — see comment ~L312),
  `broker/.../steps/EventStreamStep.java` (opens a topic partition's log).
- **ZeebeDb state:** `zeebe/zb-db/.../ZeebeDb.java`, `ZeebeRocksDbFactory.java`, `ColumnFamily.java`,
  `TransactionContext.java`; reader pattern: own `zeebeDb.createContext()` per actor.
- **Codec / records:** `event-bridge-zeebe-connector/.../ZeebeRecordCodec.java` (frame above),
  `ZeebeRecord.java`.
- **Config:** `event-bridge-core/.../config/EventBridgeProperties.java` (prefix `event-bridge`).

