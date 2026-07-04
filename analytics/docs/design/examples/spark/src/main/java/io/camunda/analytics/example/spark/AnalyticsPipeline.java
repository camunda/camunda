/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.example.spark;

import static org.apache.spark.sql.functions.avg;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.not;
import static org.apache.spark.sql.functions.window;

import java.util.concurrent.TimeoutException;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.streaming.GroupStateTimeout;
import org.apache.spark.sql.streaming.OutputMode;
import org.apache.spark.sql.streaming.StreamingQuery;
import org.apache.spark.sql.streaming.StreamingQueryException;
import org.apache.spark.sql.streaming.Trigger;

/**
 * Wires the shared process-instance analytics pipeline as a single Spark Structured Streaming query.
 *
 * <p>REFERENCE EXAMPLE — not built, not wired into anything.
 *
 * <p>Read the pipeline top-to-bottom as three stages plus a shuffle:
 *
 * <pre>
 *   Dataset&lt;SourceEvent&gt;
 *     └─ Stage A  groupByKey(instanceKey) + flatMapGroupsWithState   → Dataset&lt;CompletionFact&gt;
 *                 (base projection, derive, SLA timer, eviction — all hand-written)
 *     └─ Stage B  filter(forward-only gate) + filter(completed only)
 *                 groupBy(window(1 min), processId)                   ← the groupBy is the SHUFFLE
 *     └─ Stage C  agg(count, avg(durationMs))                         → serving cells
 *     └─ Sink     writeStream().format("console")  (+ checkpointLocation)
 * </pre>
 *
 * <p><strong>Micro-batch execution.</strong> Unlike Kafka Streams / Flink, which advance
 * per-record, Structured Streaming runs as a sequence of micro-batches. Each trigger: Spark reads a
 * bounded slice of new input (recorded in the offset log), runs the whole DAG above as an
 * incremental batch job — including the shuffle for the {@code groupBy} — updates the state stores,
 * writes the sink, then records completion in the commit log. Latency is therefore per-batch, not
 * per-record. (Continuous Processing mode exists but does not support arbitrary stateful ops, so it
 * is not an option for this pipeline.)
 */
public final class AnalyticsPipeline {

  private AnalyticsPipeline() {}

  /**
   * Builds and starts the streaming query.
   *
   * @param events the source stream, already decoded into typed {@link SourceEvent}s with {@code
   *     eventTime} populated
   * @param activationOffset the forward-only gate: only facts whose terminal event sits at or beyond
   *     this source offset are aggregated (Stage B replay boundary)
   * @param checkpointLocation durable path for the offset log + commit log + state store (the basis
   *     of Spark's fault tolerance and exactly-once guarantee — see the README)
   */
  public static StreamingQuery start(
      final Dataset<SourceEvent> events,
      final long activationOffset,
      final String checkpointLocation)
      throws TimeoutException {

    // ============================================================================================
    // Stage A — base projection (Model A), keyed by instanceKey.
    // ============================================================================================
    // The watermark on the event-time column is a precondition for EventTimeTimeout: the SLA timer
    // in BaseProjectionState fires when THIS watermark crosses the registered timeout timestamp.
    // 2 minutes of allowed lateness is illustrative.
    final Dataset<CompletionFact> facts =
        events
            .withWatermark("eventTime", "2 minutes")
            .groupByKey(
                (MapFunction<SourceEvent, Long>) SourceEvent::getInstanceKey, Encoders.LONG())
            .flatMapGroupsWithState(
                new BaseProjectionState(),
                OutputMode.Append(), // facts are emitted, never retracted
                Encoders.bean(InstanceState.class), // state store row encoder
                Encoders.bean(CompletionFact.class), // output row encoder
                GroupStateTimeout.EventTimeTimeout());

    // ============================================================================================
    // Stage B — dataset subscription + local aggregate.
    // ============================================================================================
    // The "dataset" here == completed instances grouped by processId over tumbling 1-min event-time
    // windows, metric count + avg(durationMs). Two filters express the subscription:
    //   1. forward-only gate: only facts at/after the configured activation offset
    //   2. completions only: drop SLA-breach facts (they are not "completed instances")
    // The groupBy(window, processId) is the SHUFFLE — Spark inserts an Exchange here and performs a
    // partial aggregate on each input partition, shuffles by the grouping key, then a final
    // aggregate (Stage C). count + avg are declarative aggregate functions, so Spark owns the whole
    // partial→shuffle→final machinery. A sketch metric (percentile/distinct/top-k) would NOT fit a
    // declarative agg: it would need its own accumulator, i.e. a second flatMapGroupsWithState (or a
    // custom Aggregator) holding the sketch as state — that is the hand-written escape hatch.
    final Dataset<Row> servingCells =
        facts
            .filter(col("sourceOffset").geq(lit(activationOffset)))
            .filter(not(col("slaBreach")))
            .withWatermark("startWindow", "2 minutes") // chained stateful op needs its own watermark
            .groupBy(window(col("startWindow"), "1 minute"), col("processId"))
            .agg(
                count(lit(1)).as("count"),
                avg(col("durationMs")).as("avgDurationMs"))
            // ==== Stage C — global aggregate → serving Cell shape ====
            // Project the (window struct, processId, metrics) row into the flat Cell schema.
            .select(
                col("processId"),
                expr("unix_timestamp(window.start) * 1000").as("windowStartMs"),
                col("count"),
                col("avgDurationMs"));

    // ============================================================================================
    // Sink — stub console sink.
    // ============================================================================================
    // Update output mode emits each window's running count/avg as it changes within the micro-batch
    // model; Append would emit a window only once its watermark closes it. checkpointLocation is
    // where exactly-once lives: offset log (what was read) + commit log (what was finished) + state
    // store snapshots/deltas. A real sink would be an idempotent/transactional writer keyed by
    // (window, processId) so replay after failure overwrites rather than double-counts.
    return servingCells
        .writeStream()
        .queryName("process-instance-analytics")
        .format("console")
        .outputMode(OutputMode.Update())
        .option("truncate", false)
        .option("checkpointLocation", checkpointLocation)
        // Fixed-interval micro-batch trigger; the default (Trigger.ProcessingTime(0)) fires batches
        // as fast as possible. Trigger.AvailableNow() would drain the source once and stop.
        .trigger(Trigger.ProcessingTime("10 seconds"))
        .start();
  }

  /**
   * Convenience for callers that want to block until the query terminates.
   *
   * <p>{@link StreamingQuery#awaitTermination()} throws {@link StreamingQueryException} on a query
   * failure; surfaced here so {@link Main} stays terse.
   */
  public static void awaitTermination(final StreamingQuery query)
      throws StreamingQueryException {
    query.awaitTermination();
  }
}
