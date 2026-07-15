# Analytics pipeline — Apache Spark Structured Streaming (reference)

> **Illustrative only.** This module is **not built, not wired into the reactor, not committed as a
> dependency of anything.** Its dependencies will not resolve offline in this repo — that is
> expected. The point is to show, in idiomatic Spark, *what the framework owns vs. what you
> hand-write* for the shared process-instance analytics pipeline, so it can be compared side-by-side
> with the Kafka Streams and Flink references.

The pipeline is identical across all three examples:

- **Stage A — base projection (Model A)**, keyed by `instanceKey`: build `InstanceState`, derive a
  `CompletionFact` on COMPLETED/TERMINATED and evict, and emit an SLA-breach fact + evict for
  instances that never complete within 5 minutes.
- **Stage B — dataset subscription + local aggregate**: completed instances grouped by `processId`
  over tumbling 1-minute event-time windows, metric `count` + `avg(durationMs)`, behind a
  forward-only offset gate.
- **Shuffle**: the `groupBy` exchange.
- **Stage C — global aggregate**: serving `Cell{processId, windowStartMs, count, avgDurationMs}`.
- **Sink**: a console stub.

## Files

|            File            |                                            Role                                             |
|----------------------------|---------------------------------------------------------------------------------------------|
| `pom.xml`                  | Standalone POM (spark-sql + spark-core). Not in any reactor.                                |
| `SourceEvent.java`         | Input record (JavaBean, for `Encoders.bean`).                                               |
| `InstanceState.java`       | Per-instance projection held in `GroupState`.                                               |
| `CompletionFact.java`      | Fact derived by Stage A; input to the Stage B/C aggregation.                                |
| `Cell.java`                | Serving-side output shape.                                                                  |
| `BaseProjectionState.java` | The `FlatMapGroupsWithStateFunction` — Stage A logic (state + transitions + timer + evict). |
| `AnalyticsPipeline.java`   | Builds the whole streaming query (Stage A → shuffle → Stage B/C → sink).                    |
| `Main.java`                | `SparkSession` + a synthetic source + starts the query.                                     |

## Framework ownership map

|            Concern             |                                          Spark primitive                                          |                                            Spark owns                                            |                                                                       You hand-write                                                                        |
|--------------------------------|---------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Base projection (Stage A)**  | `KeyValueGroupedDataset.flatMapGroupsWithState(fn, Append, stateEnc, outEnc, EventTimeTimeout)`   | Keying, delivering events grouped by key per batch, invoking `fn`                                | The `fn` body: state shape + every transition rule                                                                                                          |
| **Keyed state**                | `GroupState<InstanceState>` backed by the **state store** (RocksDB or in-memory)                  | State persistence, per-batch snapshot/delta checkpoint, replay on restart                        | The `InstanceState` model; when to `update` / `get` / `remove`                                                                                              |
| **Derive**                     | Returning `Iterator<CompletionFact>` from `flatMapGroupsWithState`                                | Plumbing the emitted rows downstream                                                             | Building the `CompletionFact` (floor-to-minute, duration, incident flag)                                                                                    |
| **Timer / SLA**                | `state.setTimeoutTimestamp(...)` + `GroupStateTimeout.EventTimeTimeout()` + `state.hasTimedOut()` | Firing the callback once the **watermark** crosses the registered timestamp                      | Choosing the deadline (`start + 5 min`), building the breach fact                                                                                           |
| **Eviction**                   | `GroupState.remove()`                                                                             | Actually dropping the state store entry                                                          | Deciding *when* to evict (terminal reached, or timeout)                                                                                                     |
| **Local aggregate (Stage B)**  | `groupBy(...).agg(count, avg)` (declarative)                                                      | Partial aggregation on each input partition                                                      | The metric choice; for **sketches** (pctl/distinct/top-k) you'd swap to a second `flatMapGroupsWithState` / custom `Aggregator` holding the sketch as state |
| **Shuffle**                    | The `groupBy(window, processId)` **Exchange**                                                     | Hash-partitioning by grouping key, network transfer, spill                                       | Nothing — Spark inserts and sizes the exchange (`spark.sql.shuffle.partitions`)                                                                             |
| **Global aggregate (Stage C)** | Final `agg` after the exchange                                                                    | partial → shuffle → **final** merge                                                              | Projecting the result `Row` into the `Cell` schema                                                                                                          |
| **Windowing**                  | `window(col("startWindow"), "1 minute")` + `withWatermark(...)`                                   | Bucketing rows into tumbling windows, closing them by watermark, retaining/expiring window state | Window size, event-time column, allowed lateness                                                                                                            |
| **Checkpoint**                 | `.option("checkpointLocation", path)`                                                             | Offset log + commit log + state store checkpoint written per micro-batch                         | Choosing a durable, per-query path                                                                                                                          |
| **Exactly-once**               | checkpoint (replay) + idempotent/transactional sink                                               | Recording read/committed offsets, deterministic replay of a failed batch                         | Making the **sink** idempotent (key by window+processId) and the source **replayable**                                                                      |
| **State backend**              | `spark.sql.streaming.stateStore.providerClass` (RocksDB)                                          | Off-heap keyed state, compaction, checkpoint upload                                              | Just the config choice                                                                                                                                      |
| **Micro-batch vs. continuous** | Structured Streaming micro-batch engine (default)                                                 | Slicing input into batches, running the DAG incrementally, advancing offsets                     | Trigger interval (`Trigger.ProcessingTime` / `AvailableNow`); note continuous mode can't run arbitrary stateful ops, so it's not usable here                |

### The short version

- **Spark owns the runtime plumbing:** keying and grouping, the state store and its checkpointing,
  watermark tracking and timeout firing, the shuffle exchange, the partial/final aggregation split,
  window lifecycle, and offset/commit bookkeeping.
- **You hand-write the domain:** the state model and its transition rules, the derive step, the
  eviction and SLA-timer decisions, the forward-only gate, and — for any non-declarative metric
  (sketches) — a custom stateful accumulator, because `count`/`avg` are the only part that comes for
  free from `agg`.

## Exactly-once and the micro-batch model

Spark Structured Streaming gets **end-to-end exactly-once** from three things working together: a
**checkpoint** (the offset log records exactly which input each micro-batch consumed, the commit log
records which batches finished, and the state store is snapshotted/delta-checkpointed alongside
them), a **replayable source** (Kafka offsets, file listings — so a failed batch re-reads the exact
same input), and an **idempotent or transactional sink** (so replaying a batch overwrites rather
than double-writes, typically by keying writes on `(window, processId)`). On restart Spark reads the
last committed batch from the logs, restores state, and deterministically re-runs any batch that was
read-but-not-committed.

The **micro-batch** execution model is the key contrast with Kafka Streams and Flink for our use
case. KS and Flink advance **per record**: a record flows through the topology, timers fire on a
continuous event-time clock, and latency is roughly per-record. Spark instead advances **per
batch** — every trigger it reads a bounded slice, runs the *entire* DAG (including the `groupBy`
shuffle) as one incremental job, updates state, writes the sink, and only then commits offsets. That
buys simple, coarse-grained fault tolerance and a single consistent watermark advance per batch, at
the cost of per-batch (not per-record) latency and a hard shuffle boundary between Stage A and Stage
B/C — whereas KS re-keys through an intermediate topic and Flink does a streaming `keyBy`
network exchange, both without batch boundaries.
