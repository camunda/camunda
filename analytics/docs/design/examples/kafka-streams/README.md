# Analytics pipeline — Kafka Streams reference example

> **Reference only.** This module is illustrative. It is **not** wired into any parent `pom.xml`, is
> **not** built in CI, and is **not** expected to compile offline. Its job is to show, in idiomatic
> Kafka Streams, exactly what the framework owns versus what you hand-write — so it can be compared
> line-for-line against the Flink and Spark examples of the *same* pipeline.

## The pipeline

```
process-events topic (keyed by instanceKey)
     │
[Stage A] BaseProjectionProcessor + KeyValueStore<Long,InstanceState>   ← hand-written fold
     │        · ACTIVATED→start · VARIABLE→put · INCIDENT→flag · COMPLETED/TERMINATED→derive+evict
     │        · STREAM_TIME punctuator: SLA (5 min) breach → derive+evict
     ▼  CompletionFact
[Stage B] filter: forward-only gate (sourceOffset >= activationOffset)  ← the "dataset subscription"
     │
selectKey(processId)                                                    ← declares the shuffle
     ▼
[Stage C] groupByKey → windowedBy(1 min, no grace) → aggregate(count+sum)
     │        · repartition topic created here = the physical shuffle
     ▼  Cell{processId, windowStartMs, count, avgDurationMs}
foreach(ServingSink)                                                    ← sink stub (logs)
```

Files: `SourceEvent`, `InstanceState`, `CompletionFact`, `DurationAggregate`, `Cell`, `JsonSerde`,
`BaseProjectionProcessor` (Stage A), `AnalyticsTopology` (Stages B/C + wiring), `ServingSink`,
`Main` (config + guarantees).

## Framework ownership map

| Pipeline stage | Kafka Streams primitive that provides it | Owned by KS / hand-written |
|---|---|---|
| **Base projection** (per-instance fold) | `KStream.process(ProcessorSupplier, storeName)` running `BaseProjectionProcessor`, keyed by `instanceKey` | **Fold logic hand-written**; keying, task placement, invocation owned by KS |
| **Projection state** (`InstanceState`) | `Stores.persistentKeyValueStore` + `addStateStore`; local RocksDB + **compacted changelog topic** | **KS owns** durability, restore, rebalance migration; you declare the store + serde |
| **Derive** (`CompletionFact` on completion) | `context.forward(new Record<>(...))` with preserved event-time timestamp | **Hand-written** derivation; forwarding + timestamp propagation owned by KS |
| **Timer / eviction** (SLA breach) | `context.schedule(interval, PunctuationType.STREAM_TIME, punctuator)` + `store.delete` | **Hand-written** scan + evict; the scheduled callback + stream-time clock owned by KS. **No per-key event-time timer exists** — you scan the store on an interval |
| **Dataset subscription / gate** (forward-only) | `KStream.filter(sourceOffset >= activationOffset)` | **Hand-written** predicate; carrying the source coordinate is on you |
| **Local aggregate** | *(collapses into the single windowed aggregate — KS DSL has no separate combine step)* | See global aggregate |
| **Shuffle** (repartition by processId) | `selectKey(processId)` → KS auto-inserts a **repartition topic** before `groupByKey` | **KS owns** entirely; you only asked for a new key |
| **Global aggregate** (count + avg, windowed) | `groupByKey().windowedBy(TimeWindows.ofSizeWithNoGrace(1 min)).aggregate(init, adder, Materialized)` | **KS owns** windowing, ordering, store; you supply an **associative** accumulator (count+sum, avg deferred) |
| **Windowing / event time** | `TimeWindows` + record timestamps preserved through `forward` | **KS owns**; you preserve the event-time timestamp |
| **Checkpoint** | `COMMIT_INTERVAL_MS` (commit = checkpoint); RocksDB offset checkpoints in `STATE_DIR`; changelog is source of truth | **KS owns** entirely |
| **Exactly-once** | `PROCESSING_GUARANTEE_CONFIG = exactly_once_v2` (one config line) | **KS owns** entirely — Kafka transactions |
| **State recovery / failover** | changelog replay + `NUM_STANDBY_REPLICAS` hot standbys | **KS owns** entirely |
| **Sink** | `KStream.foreach(ForeachAction)` → `ServingSink` | **Hand-written**; external-store exactly-once is **your** problem (see below) |

### What Kafka Streams owns for you

- **All state durability and recovery.** Every `store.put` is mirrored to a compacted changelog
  topic; after a crash or rebalance the store is restored automatically. You never write a WAL.
- **The shuffle.** `selectKey` + `groupByKey` transparently creates and manages a repartition topic.
- **Windowing and window state.** Tumbling event-time windows, their store, retention, and late-record
  handling (grace) are library features.
- **Checkpointing and exactly-once.** One config line each; the transactional commit ties source
  offsets + state + output atomically.
- **Scheduling of the punctuator** and the stream-time clock that makes it deterministic.

### What you hand-write

- **The fold** in `BaseProjectionProcessor`: the per-event-type state transitions.
- **The derivation** of `CompletionFact` and the **eviction** (`store.delete`).
- **The SLA breach logic** — because KS has no per-key event-time timer, you scan the store in a
  STREAM_TIME punctuator and evict overdue instances yourself.
- **The forward-only gate** predicate and the discipline of carrying source coordinates through.
- **The associative accumulator** (count + sum, averaging deferred to the end) so the aggregate is
  correct across the shuffle regardless of arrival order.
- **The serdes** for every domain type (KS never infers them).
- **External-store exactly-once at the sink.** The Kafka transaction does not span your database, so
  the upsert must be idempotent — keyed by `(processId, windowStartMs)`, which `Cell` is.

## Exactly-once mechanism (and how it differs from a hand-rolled runtime)

Kafka Streams `exactly_once_v2` wraps each commit interval in a single Kafka transaction: the
consumed source offsets, all writes to changelog / repartition / output topics, and the state-store
updates are committed atomically, and any failure aborts and replays the whole transaction so no
partial effect is ever observed. It relies entirely on the Kafka transaction coordinator and
idempotent producers, and the "v2" variant uses one producer per instance with consumer-group-aware
transactions so it scales to many partitions cheaply. This differs from a hand-rolled
segment-origin-dedup runtime, which claims no distributed transaction: it processes at-least-once and
makes the *effect* idempotent by tagging every derived record with its source `(partition, offset)`
origin and dropping any replay at or below the last durably-served coordinate — pushing the
correctness boundary into the sink/dataset (a forward-only gate, exactly the `activationOffset` filter
in Stage B) instead of into a transactional commit protocol.
