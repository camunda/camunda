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
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import java.util.function.ToLongFunction;

/**
 * Facts grouped by a key, awaiting an aggregate. Optionally scope the group into event-time windows
 * with {@link #windowedBy} (which lifts the key to {@link Windowed}), then pick the metric with
 * {@link #aggregate}.
 *
 * @param <F> the fact type
 * @param <K> the grouping key type
 */
public final class Grouped<F, K> {

  private final KeySelector<F, K> keySelector;

  Grouped(final KeySelector<F, K> keySelector) {
    this.keySelector = keySelector;
  }

  /** Scopes the group into tumbling windows by the fact's event time; the key becomes windowed. */
  public Grouped<F, Windowed<K>> windowedBy(
      final TumblingWindows windows, final ToLongFunction<F> eventTime) {
    return new Grouped<>(
        fact ->
            new Windowed<>(
                keySelector.getKey(fact), windows.windowStart(eventTime.applyAsLong(fact))));
  }

  /** Folds each group with the given metric. */
  public <ACC> Aggregated<F, K, ACC> aggregate(final AggregateFunction<F, ACC, ?> metric) {
    return new Aggregated<>(keySelector, metric);
  }
}
