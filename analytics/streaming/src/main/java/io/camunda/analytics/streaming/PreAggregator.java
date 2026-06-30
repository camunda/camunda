/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming;

import java.util.HashMap;
import java.util.Map;

/**
 * An in-memory pre-aggregation buffer (a combiner): folds facts into a per-key accumulator so the
 * serving store is touched once per key at flush rather than once per fact. If many facts share a
 * key between flushes, they collapse into a single partial.
 *
 * <p>The buffer is <strong>ephemeral and rebuildable</strong>: the runtime advances the source
 * offset only when it {@link #drain() drains} and flushes. A crash loses the buffer but not the
 * offset, so the records replay and the partials are rebuilt — no double counting, because
 * un-flushed facts never advanced the offset.
 *
 * <p>Not thread-safe — one combiner per single-writer fold.
 *
 * @param <IN> the fact type
 * @param <K> the grouping key type (value-equal)
 * @param <ACC> the accumulator type
 */
public final class PreAggregator<IN, K, ACC> {

  private final AggregateFunction<IN, ACC, ?> aggregate;
  private final Map<K, ACC> buffer = new HashMap<>();

  public PreAggregator(final AggregateFunction<IN, ACC, ?> aggregate) {
    this.aggregate = aggregate;
  }

  /** Folds {@code value} into the accumulator for {@code key}. */
  public void add(final K key, final IN value) {
    final ACC accumulator = buffer.computeIfAbsent(key, k -> aggregate.createAccumulator());
    buffer.put(key, aggregate.add(value, accumulator));
  }

  /** Number of distinct keys currently buffered (a flush-size signal). */
  public int size() {
    return buffer.size();
  }

  public boolean isEmpty() {
    return buffer.isEmpty();
  }

  /**
   * Returns the buffered partials and clears the buffer, so the caller can merge each partial into
   * the serving store. The returned map is a snapshot the caller owns.
   */
  public Map<K, ACC> drain() {
    if (buffer.isEmpty()) {
      return Map.of();
    }
    final Map<K, ACC> drained = new HashMap<>(buffer);
    buffer.clear();
    return drained;
  }
}
