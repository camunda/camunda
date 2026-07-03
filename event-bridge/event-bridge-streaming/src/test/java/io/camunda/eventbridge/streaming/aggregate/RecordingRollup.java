/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test-only {@link Rollup} double: folds facts by key into an in-memory map, buffering until {@link
 * #flush()} (or {@link #close()}), and exposes the merged value per key via {@link #get}. Used by
 * the processor/topology/transforming plumbing tests to assert that facts reach a rollup — the
 * production materialization model is {@link MaterializedRollup}/{@link ResultSink}, exercised by
 * the aggregation tests, not this helper.
 *
 * @param <F> the fact type
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class RecordingRollup<F, K, ACC> implements Rollup<F> {

  private final AggregateFunction<F, ACC, ?> aggregate;
  private final KeySelector<F, K> keySelector;
  private final Map<K, ACC> buffered = new HashMap<>();
  private final Map<K, ACC> committed = new HashMap<>();

  public RecordingRollup(
      final AggregateFunction<F, ACC, ?> aggregate, final KeySelector<F, K> keySelector) {
    this.aggregate = aggregate;
    this.keySelector = keySelector;
  }

  @Override
  public void accept(final F fact) {
    buffered.merge(
        keySelector.getKey(fact),
        aggregate.add(fact, aggregate.createAccumulator()),
        aggregate::merge);
  }

  @Override
  public void flush() {
    buffered.forEach((key, acc) -> committed.merge(key, acc, aggregate::merge));
    buffered.clear();
  }

  @Override
  public void close() {
    flush();
  }

  /** The merged value flushed for {@code key}, if any. */
  public Optional<ACC> get(final K key) {
    return Optional.ofNullable(committed.get(key));
  }
}
