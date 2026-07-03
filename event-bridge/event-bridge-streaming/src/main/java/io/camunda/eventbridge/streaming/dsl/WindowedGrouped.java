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
import io.camunda.eventbridge.streaming.window.Windows;
import java.util.function.ToLongFunction;

/**
 * A group scoped into event-time windows, awaiting an aggregate. Carries the window strategy and
 * the event-time extractor through to {@link #aggregate}, which picks the metric.
 *
 * @param <F> the value type
 * @param <K> the grouping key type
 */
public final class WindowedGrouped<F, K> {

  private final KeySelector<F, K> keySelector;
  private final Windows windows;
  private final ToLongFunction<F> eventTime;

  WindowedGrouped(
      final KeySelector<F, K> keySelector,
      final Windows windows,
      final ToLongFunction<F> eventTime) {
    this.keySelector = keySelector;
    this.windows = windows;
    this.eventTime = eventTime;
  }

  /** Folds each windowed group with the given metric. */
  public <ACC> WindowedAggregated<F, K, ACC> aggregate(final AggregateFunction<F, ACC, ?> metric) {
    return new WindowedAggregated<>(keySelector, windows, eventTime, metric);
  }
}
