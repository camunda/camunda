# Analytics pipeline — Apache Flink (DataStream API) reference

> **Status: REFERENCE ONLY.** Illustrative, not built, not wired into the reactor, not committed as
> a real module. Offline dependency resolution will not work here and that is expected — the value
> is showing a principal engineer, in idiomatic Flink, **what the framework owns vs. what we
> hand-write**. This is one of three comparison twins (Kafka Streams / Flink / Spark) implementing
> the *same* pipeline so they can be read side by side.

## The pipeline

Process-instance analytics, simplified:

1. **Stage A — base projection** (keyed by `instanceKey`): fold `ACTIVATED / VARIABLE / INCIDENT /
   COMPLETED / TERMINATED` into per-instance `InstanceState`. On a terminal event **derive** a
   `CompletionFact` and **evict** the instance. An **event-time SLA timer** at `start + 5 min`
   emits a breach fact and evicts if the instance is still open.
2. **Stage B — dataset subscription + local aggregate**: filter to completions, apply a
   **forward-only offset gate**, window into tumbling 1-minute event-time windows, and fold with a
   **meter** (`count` + `avg(durationMs)`).
3. **Shuffle**: `keyBy(processId)`.
4. **Stage C — global aggregate**: merge partial meters into serving `Cell`s.
5. **Sink**: stub (`print()`).

Files: [`pom.xml`](pom.xml), `SourceEvent`, `InstanceState`, `CompletionFact`, `Cell`,
[`BaseProjectionFunction`](src/main/java/io/camunda/analytics/examples/flink/BaseProjectionFunction.java),
[`MeterAggregate`](src/main/java/io/camunda/analytics/examples/flink/MeterAggregate.java),
[`AnalyticsJob`](src/main/java/io/camunda/analytics/examples/flink/AnalyticsJob.java) (the graph),
[`Main`](src/main/java/io/camunda/analytics/examples/flink/Main.java) (runtime config + source).

## Framework-ownership map

| Pipeline stage / concern | Flink primitive | Flink owns | You hand-write |
|---|---|---|---|
| **Stage A: base projection** | `KeyedProcessFunction<Long, SourceEvent, CompletionFact>` on `keyBy(instanceKey)` | Per-key routing, ordering, single-owner exclusivity, invoking your code per event | The state machine: how each `EventType` mutates `InstanceState` |
| **Base-projection store** | `ValueState<InstanceState>` on the RocksDB backend | The keyed store: out-of-core storage, per-key scoping, serialization, incremental snapshots, recovery | The shape of the projected value (`InstanceState`) |
| **Derive fact** | `out.collect(fact)` inside `processElement` | Delivering the emitted record downstream | The derivation logic (`deriveFact`: window floor, duration, identity) |
| **Timer / eviction** | `ctx.timerService().registerEventTimeTimer(...)` + `onTimer(...)`; `state.clear()`; (alt: `StateTtlConfig`) | Firing timers on watermark progress, persisting timers in checkpoints, removing keyed entries | The SLA policy (`start + 5 min`) and what a breach emits |
| **Event time / watermarks** | `WatermarkStrategy.forBoundedOutOfOrderness(...)` + timestamp assigner | Watermark generation/propagation, out-of-order handling, driving both windows and timers | Which field is event time (`timestampMs`) and the lateness bound |
| **Dataset subscription filter** | `.filter(!slaBreach)` | Operator scheduling | The subscription predicate (completions only) |
| **Forward-only gate** | `.filter(sourceOffset >= activationOffset)` | — | The gate predicate + the activation offset |
| **Shuffle** | `.keyBy(processId)` | The network repartition, hash partitioning, and re-routing to the key's owning subtask | Choosing the grouping key |
| **Windowing** | `.window(TumblingEventTimeWindows.of(Time.minutes(1)))` | Window assignment, per-window state, triggering/closing on watermark, allowed-lateness bookkeeping | Window size + type |
| **Local aggregate (Stage B)** | `AggregateFunction.add` via `.aggregate(meter, stamper)` | Running `add` incrementally on upstream subtasks *before* the shuffle | The fold arithmetic (`count++`, `sum += duration`) |
| **Global aggregate (Stage C)** | `AggregateFunction.merge` | Combining partial accumulators on the owning subtask after the shuffle | The combine arithmetic (`merge`) |
| **Result projection** | `AggregateFunction.getResult` + `ProcessWindowFunction` (`CellStamper`) | Calling `getResult` once per window; handing you window/key metadata | Projecting `Acc → Cell`; stamping `windowStart` + key |
| **Meter** | The whole `AggregateFunction` (`createAccumulator`/`add`/`merge`/`getResult`) | Where each of those runs (local vs. global) and calling them at the right time | The four methods themselves |
| **Checkpointing** | `env.enableCheckpointing(...)` + `EmbeddedRocksDBStateBackend` | Barrier injection, aligned distributed snapshot of *all* state + source offsets + timers, restore | Cadence, backend choice, retention knobs |
| **Exactly-once** | `CheckpointingMode.EXACTLY_ONCE` + offset-in-checkpoint source + 2PC sink | End-to-end EOS orchestration (see below) | Making the sink transactional (2PC committer); the source is committed inside the checkpoint |
| **State** | `ValueState` + window state + timer state, all keyed | Lifecycle, sharding by key, rescale redistribution (via stable `uid`s), fault-tolerant persistence | The value types you put in state |

## Our hand-rolled concepts → what Flink provides natively

| Our concept | Provided by Flink? | Mechanism |
|---|---|---|
| **Segment stride** (partition the source log into fixed strides for parallelism/recovery) | **Yes, as windows + key groups.** | Tumbling **event-time windows** are the time-stride; **key groups** (fixed max-parallelism buckets) are the parallelism-stride Flink uses to redistribute keyed state on rescale. We do not manage either. |
| **Segment-origin dedup** (dedup facts by their source coordinate so replays don't double-count) | **Mostly, at the framework layer, differently.** | We keep `sourcePartition`/`sourceOffset` on the fact and use a **forward-only offset gate** for the *dataset-activation* semantics. But the *replay* dedup we build by hand is largely unnecessary in Flink: on failure the job **rewinds source + state together to the last checkpoint**, so records between the last checkpoint and the crash are re-read but re-processed against the same rewound state — no external dedup needed for internal state. For the sink, exactly-once is the 2PC committer, not origin dedup. |
| **Produce-before-commit** (write facts to the downstream topic before committing source offsets, then reconcile) | **Yes, inverted into 2PC.** | Flink does not "produce then commit offsets and reconcile". Instead the **checkpoint barrier** aligns source-offset advance, state snapshot, and sink pre-commit into one atomic step; the sink's **two-phase commit** publishes only on `notifyCheckpointComplete`. The reconcile problem is designed away by the barrier protocol. |
| **Base-projection store** (sharded RocksDB + RDBMS projection we operate) | **Yes.** | Keyed `ValueState` on the `EmbeddedRocksDBStateBackend`: sharded by key group, incrementally checkpointed, recovered and rescaled by the runtime. We own only the projected value type. |
| **Meter** (add / merge / getResult contract) | **Yes, exactly.** | `AggregateFunction` **is** the meter contract. `.aggregate(...)` runs it as **local pre-aggregate → shuffle small accumulators → global merge**, i.e. the combine step is placed for us. We supply arithmetic; Flink supplies the topology and placement. |

## Flink's exactly-once mechanism (the 2–3 sentence version)

Flink injects numbered **checkpoint barriers** into the source streams; as a barrier flows through
the graph each operator snapshots its state, and on operators with multiple inputs the barrier is
**aligned** (inputs are held until the barrier arrives on all of them) so the snapshot is a
consistent global cut — the Chandy–Lamport distributed-snapshot algorithm. The snapshot captures
operator state, keyed state (including timers) **and** the source offsets together, so on failure
the whole job rewinds to one coherent point rather than re-reading with stale state. End-to-end
exactly-once at the sink is then a **two-phase commit**: the sink stages/pre-commits its writes as
part of the snapshot and only durably commits them when the checkpoint is confirmed complete, so an
output becomes visible if and only if the checkpoint that produced it succeeded.
