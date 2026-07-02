/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.dsl;

import io.camunda.analytics.streaming.aggregate.AggregateFunction;
import io.camunda.analytics.streaming.aggregate.KeySelector;
import io.camunda.analytics.streaming.aggregate.PreAggregatingRollup;
import io.camunda.analytics.streaming.aggregate.Rollup;
import io.camunda.analytics.streaming.aggregate.RollupStore;

/**
 * A grouped, aggregated stream materialized into a {@link RollupStore}. {@link #into} builds the
 * {@link Rollup} (a pre-aggregating combiner) ready to register with a projector.
 *
 * @param <F> the fact type
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class Aggregated<F, K, ACC> {

  /** Default combiner size before an automatic flush. */
  public static final int DEFAULT_MAX_BUFFERED_KEYS = 10_000;

  private final KeySelector<F, K> keySelector;
  private final AggregateFunction<F, ACC, ?> metric;

  Aggregated(final KeySelector<F, K> keySelector, final AggregateFunction<F, ACC, ?> metric) {
    this.keySelector = keySelector;
    this.metric = metric;
  }

  /** Materializes into {@code store}, flushing the combiner after {@code maxBufferedKeys} keys. */
  public Rollup<F> into(final RollupStore<K, ACC> store, final int maxBufferedKeys) {
    return new PreAggregatingRollup<>(metric, keySelector, store, maxBufferedKeys);
  }

  /** Materializes into {@code store} with the default combiner size. */
  public Rollup<F> into(final RollupStore<K, ACC> store) {
    return into(store, DEFAULT_MAX_BUFFERED_KEYS);
  }
}
