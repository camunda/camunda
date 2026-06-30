/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import java.util.Map;

/**
 * The default {@link Rollup}: selects each fact's group key, folds it into an in-memory {@link
 * PreAggregator} (the combiner), and flushes merged partials into a {@link RollupStore}. A fact
 * thus touches the durable store at most once per key per flush, not once per fact.
 *
 * <p>Flushes when the buffer reaches {@code maxBufferedKeys} (size), on {@link #flush()}
 * (wall-clock tick), and on {@link #close()} (final). The buffer is ephemeral and rebuildable:
 * since the runtime advances the source offset only after a flush, a lost buffer is reconstructed
 * by replay.
 *
 * @param <F> the fact type
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class PreAggregatingRollup<F, K, ACC> implements Rollup<F> {

  private final KeySelector<F, K> keySelector;
  private final PreAggregator<F, K, ACC> combiner;
  private final RollupStore<K, ACC> store;
  private final int maxBufferedKeys;

  public PreAggregatingRollup(
      final AggregateFunction<F, ACC, ?> aggregate,
      final KeySelector<F, K> keySelector,
      final RollupStore<K, ACC> store,
      final int maxBufferedKeys) {
    if (maxBufferedKeys <= 0) {
      throw new IllegalArgumentException(
          "maxBufferedKeys must be positive, was " + maxBufferedKeys);
    }
    this.keySelector = keySelector;
    this.combiner = new PreAggregator<>(aggregate);
    this.store = store;
    this.maxBufferedKeys = maxBufferedKeys;
  }

  @Override
  public void accept(final F fact) {
    combiner.add(keySelector.getKey(fact), fact);
    if (combiner.size() >= maxBufferedKeys) {
      flush();
    }
  }

  @Override
  public void flush() {
    final Map<K, ACC> partials = combiner.drain();
    if (!partials.isEmpty()) {
      store.merge(partials);
    }
  }

  @Override
  public void close() {
    flush();
  }
}
