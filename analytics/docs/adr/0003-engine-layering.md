# ADR 0003 — Analytics engine layering and the operator model

- Status: Accepted
- Date: 2026-07-04
- Scope: `event-bridge/event-bridge-streaming`, `analytics/analytics-engine`,
  `analytics/analytics-model`, `analytics/event-bridge-analytics`
- Supersedes (in part): ADR 0002 — its "no applier seam" and "engine-internals-only" scope
  decisions are revised here.

## Context

We implemented the same base-projection → derive → windowed-aggregate → shuffle → merge pipeline
in Kafka Streams, Flink, and Spark (`analytics/docs/design/examples/`). Every mature engine splits
into three tiers, and you only author the middle one:

- **Substrate** (framework): ingest+offsets, keyed state + backends, the operator SPI, windowing,
  the shuffle/repartition, timers/punctuation, checkpoint + exactly-once, rescale.
- **Operators** (you write): a stateful keyed process function (Flink `KeyedProcessFunction`, KS
  `Processor`+store, Spark `flatMapGroupsWithState`) and an `AggregateFunction`
  (createAccumulator/add/merge/getResult).
- **Topology** (you declare): the DAG of operators (Flink `DataStream` graph, KS `Topology`, Spark
  query plan).

`event-bridge-streaming` is **already the substrate**: it has `processor/{Processor,
ProcessorContext, ProcessorTopology, Punctuator, PunctuationType}`, `window/`, `aggregate/`
(`AggregateFunction`, `SegmentSealingAggregation`, `SegmentDedup`, `SegmentPosition`,
`SourceCoordinate`, `MergingAggregation`, `KeySelector`), `state/` (KeyValueStore + RocksDB/caching),
`internals/{CommitBarrier, PunctuationDriver, RebalanceCoordinator}`, and `StreamRuntime`. The
problem is that the analytics code **bypasses it**:

1. There are **two operator generations**. The domain uses the older `fold/Projector` +
   `aggregate/Aggregation` seams via `ProjectionStage`; the newer `Processor` + `ProcessorTopology`
   DAG sits **unused**.
2. The app **hand-wires per-partition shards** (`CubeProjectionShard`, `CubeAggregationShard`)
   instead of declaring a topology, because the shard does work the DAG can't yet: it reads
   `safeOffset()` off the sealing aggregations and drives produce-before-commit, and it fans the
   commit barrier to the projector + aggregations.
3. The **DAG is incomplete**: the `Processor` SPI has only `init/process/close` — no
   `checkpoint()/needsCheckpoint()/flush()` and no `safeOffset()` — and `ProcessorTopology` does not
   propagate the commit barrier to its nodes. So a stateful node cannot participate in checkpointing
   or produce-before-commit through the DAG.
4. The **shuffle wire format** (`shuffle/ShuffleEnvelope`, `CellDelta`, `ShuffleEnvelopeCodec`) lives
   in `analytics-engine`, overlapping the library's segment/dedup machinery — two shuffle
   mechanisms.

## Decision

### 1. One operator model: `Processor` + `ProcessorTopology`

Consolidate on a single operator abstraction. The `Processor` gains the runtime lifecycle so a node
can be stateful and commit-aware:

```
interface Processor<In, Out> {
  default void init(ProcessorContext<Out> ctx) {}
  void process(In record);
  default void flush() {}                              // wall-clock freshness (publish sealed / converge sinks)
  default void checkpoint() {}                         // make ALL state durable, inside the commit transaction
  default boolean needsCheckpoint() { return false; }  // bounded cache full → commit early
  default void close() {}
}
```

`ProcessorContext` keeps `forward`, `schedule` (punctuation), `getStateStore`. `ProcessorTopology`
(the single `Stage`) drives the DAG: `process` at the source, punctuation fanned to `Punctuator`s,
`checkpoint()/flush()` fanned to every node, `needsCheckpoint()` = OR over nodes.

Note there is **no `safeOffset`** on the operator — see the durability decision below. Retire
`fold/Projector`, `fold/Collector`, `aggregate/Aggregation`, and `ProjectionStage` once the domain
is expressed as processor nodes. Keep `AggregateFunction` (the meter contract — Flink's
`AggregateFunction` proves it) and the segment/dedup machinery (now consumed by an aggregate
processor node, not the retired `Aggregation` seam).

### 1b. Durability: consistent-cut checkpoint (Model F, the Flink/KS way) — not `safeOffset` replay

The current shard commits `min(safeOffset)` over its meters while checkpointing the base projection
**fold-ahead** of that offset (its own `TODO(e2e)`): an element activated before `safeOffset` and
completed after it re-folds wrong on restart, because the destructive read of its start was already
checkpointed. That "replay the open segment from a safe offset" scheme (**Model R**) is not what
Flink or Kafka Streams do, and it is the source of the bug.

Adopt **Model F**: the runtime checkpoints **all** operator state — including each windowed
aggregate's **open segment** — together with the **full processed offset**, in one per-partition
atomic transaction. The committed offset therefore always *equals* the checkpointed state position
(one consistent cut), so replay-from-committed after a crash lands on matching state — there is **no
reconcile**. Sealed partial-aggregate deltas are **published before** the offset commits
(produce-before-commit); the only crash window is publish→commit, and the existing downstream
**origin-dedup** makes a re-published segment idempotent. Consequences:

- `Task.ownsDurability`, `Task.commit(offset)`/`restore()`/`safeOffset`, and the hand-rolled shards
  are **deleted**. The runtime owns the per-partition state backend (with the consumed offset stored
  in it), drives `checkpoint()` + the offset in one transaction, and publishes sealed deltas via the
  sink node's `flush()`/pre-commit before advancing the offset.
- The windowed segment-sealing aggregate node **must checkpoint its open segment** (today
  `SegmentSealingAggregation.checkpoint()` is a no-op — a Model-R assumption); otherwise committing
  the full offset would lose the open partial. This is the one substantive change the model
  requires.
- On crash the runtime replays from the last committed offset onto the restored (matching) state —
  standard at-least-once source replay with exactly-once *effect*, exactly as Flink (barrier
  snapshots) and KS (EOS transactions) do.

### 2. Standard node kit in the substrate

Provide the reusable node types so the domain declares a topology instead of hand-wiring:
- **source** — feeds the deserialized source record in;
- **stateful process node** — a `Processor` with attached `KeyValueStore`s (the base-projection
  home);
- **windowed segment-sealing aggregate node** — wraps `AggregateFunction` + the segment/seal/dedup
  machinery, **checkpoints its open segment** (Model F), and forwards sealed partial-aggregate
  deltas;
- **sink node** — `Processor<In, Void>` that publishes (to the shuffle topic, or a serving store),
  flushing its output before the offset commits (produce-before-commit).

### 3. Three layers with hard boundaries

- **L1 `event-bridge-streaming` — substrate (domain-agnostic).** Owns and guarantees everything in
  the table above; exposes the operator SPI, the node kit, the `ProcessorTopology` builder, and
  `StreamRuntime`. Behaviour contract: exactly-once *effect* (deterministic segmentation +
  origin-dedup + atomic commit + produce-before-commit), event-time punctuation, backpressure via
  poll-gating, rescale.
- **L2 `analytics-engine` — domain operators + vocabulary, authored only against L1.** Internal
  structure is the **workflow-engine shape**: `record/` (SourceRecord + canonical Fact),
  `state/{immutable,mutable}/` (`ProjectionState`/`MutableProjectionState` + impl + column-family
  enum + a zeebe-style variable store), `projection/` (the base-projection `Processor` = Model A
  fold + derive + eviction, with `dispatch/` `(ValueType,Intent)` registry, `applier/` sole
  mutators, `derive/` facts read from the projection, `behavior/` pure helpers), the meters
  (`AggregateFunction` impls), and the dataset compiler + forward-only gate.
- **L3 `event-bridge-analytics` — topology declaration + IO + config (thin).** Builds the analytics
  `ProcessorTopology` from the compiled datasets and runs `StreamRuntime`. No hand-rolled shards.

### 4. Move the shuffle wire format into L1

`ShuffleEnvelope`/`CellDelta`/`ShuffleEnvelopeCodec` fold into L1's segment machinery as the generic
partial-aggregate shuffle record; the engine supplies only the accumulator serde. One shuffle
mechanism.

### 5. Model A base projection (revises ADR 0002)

The base projection is a **materialized entity** (Model A), not derivation scratch: appliers fold
each event into the entity row (the read/write `ProjectionState` split makes appliers the sole
mutators); derivers emit facts **read from the updated row**; terminal rows are **evicted after
emit**; an **event-time** SLA timer handles cohort timeout / straggler eviction (never wall-clock —
it must be replay-deterministic). Variables are stored per `(instanceKey, name)` like the workflow
engine, not as a map blob. We adopt the applier seam (its decide/mutate separation is the value; the
replay-determinism motive is N/A because recovery is offset-refold).

## Consequences

- One operator abstraction; the DAG is the topology; the shard's bespoke orchestration
  (safeOffset/produce-before-commit/commit fan-out) becomes a substrate responsibility of
  `ProcessorTopology` + the runtime.
- "Who mutates the read-model" is compiler-enforced; facts are a pure function of the base
  projection; a new dataset is a forward-only subscription that never touches derive.
- Executed as refactor-only phases, green + one commit each (see below).

## Migration phases

1. **L1 operator model + Model-F durability** — extend `Processor` with the lifecycle
   (`flush`/`checkpoint`/`needsCheckpoint`); make `ProcessorTopology` drive them. Make the runtime
   commit a **consistent cut** (all state + full offset in one per-partition transaction, sealed
   deltas published before commit) and **delete** `Task.ownsDurability`/`commit`/`restore` + the
   `safeOffset` scheme. Add the standard node kit (source / stateful process / windowed segment-seal
   aggregate that **checkpoints its open segment** / sink).
2. **Shuffle → L1** — move the envelope/codec into the segment machinery; one mechanism.
3. **L2 base projection** — Model A entity state (record/state{immutable,mutable}), zeebe-style
   variable store, `(ValueType,Intent)` dispatch, appliers, derive-from-projection, event-time SLA
   timer — as an L1 `Processor`.
4. **L2 aggregation** — express cube meters as windowed segment-seal aggregate nodes.
5. **L3 topology** — declare the analytics `ProcessorTopology`; delete the shards; retire
   `Projector`/`Aggregation`/`ProjectionStage`.
