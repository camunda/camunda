/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.dsl;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.KeySelector;
import io.camunda.eventbridge.streaming.aggregate.MaterializedRollup;
import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import io.camunda.eventbridge.streaming.aggregate.SourceCoordinate;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.window.Windows;
import java.util.function.ToLongFunction;

/**
 * A windowed, grouped, aggregated stream. {@link #into} materializes it as a {@link
 * MaterializedRollup} into a {@link ResultSink} — the single materialization model: local
 * authoritative windowed state, source-coordinate deduplication of replays, idempotent full-value
 * upserts, and event-time finalization/eviction once a window closes past its grace.
 *
 * @param <F> the fact type
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class WindowedAggregated<F, K, ACC> {

  private final KeySelector<F, K> keySelector;
  private final Windows windows;
  private final ToLongFunction<F> eventTime;
  private final AggregateFunction<F, ACC, ?> metric;

  WindowedAggregated(
      final KeySelector<F, K> keySelector,
      final Windows windows,
      final ToLongFunction<F> eventTime,
      final AggregateFunction<F, ACC, ?> metric) {
    this.keySelector = keySelector;
    this.windows = windows;
    this.eventTime = eventTime;
    this.metric = metric;
  }

  /**
   * Materializes into {@code sink}, deduplicating replays by {@code coordinate} (the fact's source
   * partition and position).
   */
  public Rollup<F> into(
      final ResultSink<Windowed<K>, ACC> sink, final SourceCoordinate<F> coordinate) {
    return new MaterializedRollup<>(metric, keySelector, eventTime, coordinate, windows, sink);
  }
}
