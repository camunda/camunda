/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.analytics.examples.flink;

import java.time.Duration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Wires the DataStream graph: source → Stage A → Stage B (filter + gate + window + local aggregate)
 * → shuffle → Stage C (global merge) → sink.
 *
 * <p>Kept separate from {@link Main} so the topology is testable in isolation (you can feed it a
 * bounded source in a test and assert on a collecting sink) and so the graph reads top to bottom
 * without the runtime/checkpoint boilerplate in the way.
 */
public final class AnalyticsJob {

  private AnalyticsJob() {}

  /**
   * Build the analytics graph on top of an already-timestamped source stream and attach the stub
   * sink.
   *
   * @param sourceEvents the input stream; the caller is responsible for attaching a {@code
   *     WatermarkStrategy} so event-time and the SLA timers work (see {@link Main})
   * @param activationOffset forward-only gate — facts whose source offset is below this are dropped
   *     (they predate this dataset's activation and must not be counted)
   */
  public static void build(final DataStream<SourceEvent> sourceEvents, final long activationOffset) {

    // --- Stage A: base projection, keyed by instanceKey ---------------------------------------
    // keyBy(instanceKey) partitions the stream so each instance is owned by exactly one subtask;
    // the KeyedProcessFunction then owns that instance's state + SLA timer. Output is the derived
    // fact stream.
    final DataStream<CompletionFact> facts =
        sourceEvents
            .keyBy(SourceEvent::instanceKey)
            .process(new BaseProjectionFunction())
            .name("stage-a-base-projection")
            .uid("stage-a-base-projection"); // stable uid => state maps back on rescale/restore

    // --- Stage B + C: dataset subscription, gate, window, aggregate ---------------------------
    // Dataset definition: "completed instances grouped by processId, tumbling 1-minute event-time
    // windows, metric = count + avg(durationMs)".
    final DataStream<Cell> cells =
        facts
            // Subscription filter: this dataset is about *completions*, so drop SLA-breach facts.
            .filter(fact -> !fact.slaBreach())
            .name("stage-b-completions-only")
            // Forward-only gate: ignore anything at or below the activation offset. Idempotent and
            // monotonic — replays before the activation point can never inflate the counts.
            .filter(fact -> fact.sourceOffset() >= activationOffset)
            .name("stage-b-forward-only-gate")
            // Shuffle: repartition by the grouping key. This is the Stage-A → Stage-B/C shuffle.
            .keyBy(CompletionFact::processId)
            // Tumbling 1-minute event-time windows — Flink assigns each fact to its window from the
            // record's event-time timestamp and closes the window on watermark progress.
            .window(TumblingEventTimeWindows.of(Time.minutes(1)))
            // The meter. The two-arg aggregate() overload runs MeterAggregate incrementally (local
            // pre-aggregate + shuffle + global merge) and then hands the single per-window result
            // to CellStamper, which stamps the window start + key onto the Cell.
            .aggregate(new MeterAggregate(), new CellStamper())
            .name("stage-c-global-aggregate")
            .uid("stage-c-global-aggregate");

    // --- Sink: stub -----------------------------------------------------------------------------
    // print() is a stand-in. For end-to-end exactly-once the sink must participate in the
    // checkpoint's two-phase commit (see README): a TwoPhaseCommitSinkFunction / the unified Sink
    // API's committer, which stages writes on snapshot and commits them on checkpoint-complete.
    cells.print().name("serving-sink-stub");
  }

  /**
   * Stamps the window coordinate (and key) onto the {@link Cell} produced by {@link MeterAggregate}.
   *
   * <p>Why this exists: {@link org.apache.flink.api.common.functions.AggregateFunction} is
   * intentionally context-free — {@code getResult} sees only the accumulator, never the window or
   * key. Pairing it with a {@link ProcessWindowFunction} in the two-arg {@code aggregate(...)}
   * overload keeps the incremental (memory-cheap) aggregation while still giving the final emit
   * access to window metadata. This is the standard Flink idiom for "incremental aggregate that
   * also needs the window bounds".
   */
  static final class CellStamper extends ProcessWindowFunction<Cell, Cell, String, TimeWindow> {

    @Override
    public void process(
        final String processId,
        final ProcessWindowFunction<Cell, Cell, String, TimeWindow>.Context context,
        final Iterable<Cell> aggregated, // exactly one element: the merged per-window result
        final Collector<Cell> out) {

      final Cell partial = aggregated.iterator().next();
      out.collect(
          new Cell(
              processId,
              context.window().getStart(),
              partial.count(),
              partial.avgDurationMs()));
    }
  }

  /** Convenience constant so Main and tests agree on the SLA used by Stage A. */
  static Duration sla() {
    return BaseProjectionFunction.SLA;
  }
}
