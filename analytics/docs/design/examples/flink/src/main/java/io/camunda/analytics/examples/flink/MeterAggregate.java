/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.analytics.examples.flink;

import io.camunda.analytics.examples.flink.MeterAggregate.Acc;
import java.io.Serializable;
import org.apache.flink.api.common.functions.AggregateFunction;

/**
 * Stages B + C — the aggregate itself, expressed as a Flink {@link AggregateFunction}.
 *
 * <p><b>This class is the single most important comparison point in the whole example.</b> Flink's
 * {@link AggregateFunction} contract — {@code createAccumulator} / {@code add} / {@code merge} /
 * {@code getResult} — is <em>exactly</em> the shape of our "meter":
 *
 * <ul>
 *   <li>{@code createAccumulator()} → the meter's zero/identity value.
 *   <li>{@code add(acc, fact)} → fold one fact into the running aggregate (Stage B, local).
 *   <li>{@code merge(a, b)} → combine two partial aggregates (Stage C, global).
 *   <li>{@code getResult(acc)} → project the accumulator to the emitted {@link Cell}.
 * </ul>
 *
 * <p>Because we hand Flink a type with a {@code merge}, Flink runs the classic two-phase aggregate
 * for us: it <b>pre-aggregates locally</b> on each upstream subtask, ships only the small partial
 * accumulators across the {@code keyBy} shuffle, and <b>merges</b> them on the key's owning subtask
 * to produce one result per window. We write the fold arithmetic once; Flink decides where each
 * step runs. In our hand-rolled pipeline this local-combine / shuffle / global-merge split is
 * something we build and operate ourselves.
 *
 * <p>Note the accumulator carries a COUNT and a SUM, not an average. Average is not associative, so
 * you never merge averages — you merge the components and divide only in {@code getResult}. This is
 * the same reason our additive metrics store SUM/COUNT and compute derived ratios at read time.
 */
public final class MeterAggregate implements AggregateFunction<CompletionFact, Acc, Cell> {

  /**
   * The running aggregate for one window+key. Mergeable and serializable: Flink checkpoints it and
   * ships it across the shuffle.
   *
   * <p>{@code processId} is captured from the facts so {@code getResult} can stamp identity onto
   * the {@link Cell}. The window start is <em>not</em> known here — {@link AggregateFunction} is
   * context-free by design — so it is filled in by the paired {@code ProcessWindowFunction} (see
   * {@code AnalyticsJob.CellStamper}). This split is deliberate and idiomatic Flink.
   */
  public static final class Acc implements Serializable {
    String processId = "";
    long count;
    long sumDurationMs;
  }

  @Override
  public Acc createAccumulator() {
    return new Acc();
  }

  @Override
  public Acc add(final CompletionFact fact, final Acc acc) {
    acc.processId = fact.processId();
    acc.count += 1;
    acc.sumDurationMs += fact.durationMs();
    return acc;
  }

  @Override
  public Acc merge(final Acc a, final Acc b) {
    final Acc merged = new Acc();
    merged.processId = !a.processId.isEmpty() ? a.processId : b.processId;
    merged.count = a.count + b.count;
    merged.sumDurationMs = a.sumDurationMs + b.sumDurationMs;
    return merged;
  }

  @Override
  public Cell getResult(final Acc acc) {
    final double avg = acc.count == 0 ? 0.0 : (double) acc.sumDurationMs / acc.count;
    // windowStartMs left as 0 here; the ProcessWindowFunction stamps the real window start.
    return new Cell(acc.processId, 0L, acc.count, avg);
  }
}
