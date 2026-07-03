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
import io.camunda.eventbridge.streaming.aggregate.PreAggregatingRollup;
import io.camunda.eventbridge.streaming.aggregate.Rollup;
import io.camunda.eventbridge.streaming.aggregate.RollupStore;

/**
 * A grouped, aggregated stream materialized into a {@link RollupStore}. {@link #into} builds the
 * {@link Rollup} (a pre-aggregating combiner) ready to register with a projector.
 *
 * @param <F> the fact type
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class Aggregated<F, K, ACC> {

  private final KeySelector<F, K> keySelector;
  private final AggregateFunction<F, ACC, ?> metric;

  Aggregated(final KeySelector<F, K> keySelector, final AggregateFunction<F, ACC, ?> metric) {
    this.keySelector = keySelector;
    this.metric = metric;
  }

  /**
   * Materializes into {@code store}. The combiner drains on the runtime's commit tick (and on
   * close) — there is no per-key-count flush.
   */
  public Rollup<F> into(final RollupStore<K, ACC> store) {
    return new PreAggregatingRollup<>(metric, keySelector, store);
  }
}
