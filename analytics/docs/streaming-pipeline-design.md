# Streaming analytics pipeline — framework design

How the consumer-based, self-contained pipeline is structured: the domain-independent seams, the
runtime that drives them, and the pre-aggregation that keeps the DB off the per-record path. This is
the design we build *before* wiring the concrete process-instance-execution-time use case onto it.

Scope here is **write + read of state, derive facts, pre-aggregate, sink to the serving DB**.
Checkpoints, retention/delete, and pushing facts over a fact-topic (Route B) are deliberately out.

## Stages

```
source ──▶ fold (Stage 1) ──▶ collect facts ──▶ pre-aggregate (combiner) ──▶ flush ──▶ serving DB
           reads/writes                          in-memory Map<GroupKey,Acc>
           keyed state                            keyed by group key
           (state-store lib)
```

- **Stage 1 — fold:** per record, read the projection for its key, update it, and emit 0..n facts.
  Deterministic function of the in-order, per-partition source stream. Owns *state*, not offsets.
- **Collect:** the fold writes facts into a runtime-owned `Collector`. State stays in RocksDB; only
  facts flow onward.
- **Pre-aggregate (combiner):** each fact is folded into an in-memory `Map<GroupKey, Accumulator>`
  via the metric's `add`/`merge` — no DB touch per fact.
- **Flush:** on a size/time boundary, merge each buffered partial into its serving-DB row (one merge
  per group key), then advance the source offset — atomically.

## Seams (the domain-independent interfaces)

```java
// COLLECT — emit channel the runtime owns and drains.
interface Collector<F> { void collect(F fact); }

// FOLD — Stage 1. Domain owns keyed state + logic; runtime owns offsets + routing.
interface Projector<R, F> { void apply(R record, Collector<F> out); }

// AGGREGATE — the mergeable metric (Flink AggregateFunction shape).
interface AggregateFunction<IN, ACC, OUT> {
  ACC createAccumulator();
  ACC add(IN value, ACC acc);   // fold one fact
  ACC merge(ACC a, ACC b);      // combine partials — the pre-aggregation enabler
  OUT getResult(ACC acc);       // ACC (count,total,min,max) -> OUT (count,avg,min,max)
}

// GROUP KEY — what the combiner buffers by; must be value-equal (a record).
record GroupKey(long datasetId, String region, long processDefinitionKey,
                int version, String tenantId, long windowStart) {}
interface KeySelector<IN, KEY> { KEY getKey(IN value); }   // deterministic

// SINK — buffers via the combiner, flushes merged partials to the serving DB.
interface FactSink<F> {
  void apply(F fact);   // fold into the in-memory buffer
  void flush();         // drain buffer -> DB merges + advance offset, atomically
}
```

## Runtime loop (with pre-aggregation)

```
buffer: Map<GroupKey, Acc>                         // in memory, ephemeral, rebuildable

for each record from the source (in order, per partition):
    collector.clear()
    projector.apply(record, collector)             // fold: update state, maybe collect facts
    for fact in collector:
        for dataset in datasets:
            k = keySelector.getKey(fact, dataset)
            buffer[k] = merge(buffer[k] ?? createAccumulator(), add(fact, createAccumulator()))
    if flushDue (N facts or T ms):
        flush()

flush():
    begin tx
      for (k, partial) in buffer:
          DB: row[k] = merge(row[k], partial)       // ONE merge per key, not per fact
      advance fact_watermark + offset to batch's max source position
    commit
    buffer.clear()
```

## Pre-aggregation (combiner) — why and how

If 500 EU completions land in one window between flushes, that is **1 DB merge, not 500**. The fold
still runs per record (to update per-instance state and produce the fact); what we drop is the
per-fact DB round-trip.

**Correctness rests on three things:**

1. **Mergeability.** `merge` must be commutative + associative, so "fold 500 locally then merge one
   partial into the DB" equals "merge 500 partials one at a time". `merge`'s *existence* is also the
   test for whether a metric is DB-as-mergeable at all — distinct-count / percentiles have no exact
   `merge` and would need the shuffle path (Route B) or a sketch.
2. **Ephemeral, rebuildable buffer.** The offset advances *only at flush*, atomically with the
   merged partials. Crash mid-batch → the in-memory buffer is lost but the offset never moved → the
   records replay from the source → the fold reproduces the same facts → they re-accumulate and
   flush. No double counting, because un-flushed facts never advanced the offset.
3. **Source-coordinate dedup** (`fact_consumer_watermark`, per source partition) backstops the
   cross-store case (flush to the serving DB committed but the offset, in a different store, did
   not): replayed facts at/below the watermark are dropped.

**Flush triggers:** time (every T ms) or size (buffer exceeds N keys / M facts), whichever first.
Bigger buffer / longer interval → fewer DB writes, but more memory and more latency (a fact is not
visible in the DB until the next flush, bounded by T).

**Consequence for the merge SQL:** the DB increment is a *buffered partial*, not a single fact —
`UPDATE … count = count + ?, total = total + ?, min = LEAST(min, ?), max = GREATEST(max, ?)` with the
partial's `(count, total, min, max)`, rather than `+1, +dur, dur, dur`.

## Correctness invariants

| Invariant | Mechanism |
|---|---|
| Deterministic fold | pure function of the in-order per-partition stream |
| Single-writer-per-key | one consumer owns a source partition's keys |
| Mergeable aggregate | `AggregateFunction.merge`, commutative + associative |
| Effectively-once | offset advances only at flush + source-coordinate dedup in the sink |
| Finalization | event-time watermark (max seen) − allowed lateness ≥ window end (lazy, read-time) |

## Design lineage (borrowed concepts)

| Concern | Streaming framework | Flink | Ours |
|---|---|---|---|
| Fold step | `Processor.process` + `ProcessorContext` | `KeyedProcessFunction.processElement` | `Projector.apply(record, Collector)` |
| Collect | `ProcessorContext.forward` | `Collector.collect` | `Collector<F>` |
| Aggregate | `Initializer`+`Aggregator`(+`Merger`) | `AggregateFunction<IN,ACC,OUT>` | same shape |
| Group key | implicit `groupBy` key | `KeySelector<IN,KEY>` | `GroupKey` record + `KeySelector` |
| Pre-aggregate | record cache + `commit.interval.ms` | mini-batch / local-global two-phase agg | in-memory combiner buffer + flush |
| State store | changelog-backed store | keyed state backend | `analytics-state-store` |
| Finalize / retention | `Suppressed` / `Punctuator` | event-time timers + `onTimer` | lazy `finalized` (now); timers later |

## What must be added now (detected from the Flink API)

Pre-aggregation forces a couple of pieces that lazy per-record writing let us skip:

1. **`GroupKey` value type** — the combiner buffers in a `Map`, so the group key must be a
   first-class, value-equal object (a Java `record` gives `equals`/`hashCode`). Today the aggregator
   builds the key implicitly as SQL parameters; buffering makes it explicit.
2. **`KeySelector<Fact, GroupKey>`** — deterministic extraction of the group key from a fact (per
   dataset). Flink's contract: "same object → same key" — ours is a pure function of fact fields.
3. **`AggregateFunction<Fact, Acc, Result>`** — the metric, with `merge`. Flink confirms `merge`
   "supports pre-aggregation optimizations" — exactly the combiner. The execution-time metric *is*
   one (`Acc = {count,total,min,max}`); the SQL becomes a thin driver of `add`/`merge`.
4. **`Collector<F>`** — the fold's emit channel; the runtime owns collection + offset co-commit.
5. **The combiner buffer + flush boundary** in the runtime/sink.

### Explicitly NOT needed now (Flink has it; we don't yet)

- **Serializable / `TypeSerializer` accumulator.** Flink requires `AggregateFunction` accumulators be
  `Serializable` because they are shipped between processes and checkpointed. Our buffer is in-memory
  and ephemeral (rebuilt by replay), and the *durable* aggregate is the DB row — so the accumulator
  needs no serialization. Add only if we later checkpoint the buffer or ship partials over the
  event-bridge for Route B.
- **Timers / `WatermarkStrategy` / `TimestampAssigner`.** Finalization is lazy (read-time); retention
  (delete-on-completion) is deferred. These are the same mechanism (an event-time timer at
  `windowEnd + lateness` that fires to finalize *and* prune) and are best taken together, later.
- **`Merger` for session windows.** We use fixed/tumbling windows; no session merge.
- **Topology DSL** (`filter`/`mapValues`/`groupBy` combinators). One fact type, one hand fold —
  premature.

## Open items (deferred, tracked)

- Retention / delete-on-completion via event-time timers (with `WatermarkStrategy`).
- Batch transaction boundary tuning (per-record vs per-batch flush) — knob, not a redesign.
- Route B (facts over a fact-topic) for non-mergeable metrics — needs fact codec + serializable acc.
