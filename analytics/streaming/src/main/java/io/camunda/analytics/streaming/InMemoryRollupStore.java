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
import java.util.Optional;

/**
 * A heap-backed {@link RollupStore} for tests and demos: merges partials into stored rows using the
 * metric's {@link AggregateFunction#merge}. The durable RDBMS implementation expresses the same
 * merge as an upsert.
 *
 * @param <K> the grouping key type
 * @param <ACC> the accumulator type
 */
public final class InMemoryRollupStore<K, ACC> implements RollupStore<K, ACC> {

  private final AggregateFunction<?, ACC, ?> aggregate;
  private final Map<K, ACC> rows = new HashMap<>();

  public InMemoryRollupStore(final AggregateFunction<?, ACC, ?> aggregate) {
    this.aggregate = aggregate;
  }

  @Override
  public void merge(final Map<K, ACC> partials) {
    partials.forEach((key, partial) -> rows.merge(key, partial, aggregate::merge));
  }

  /** The stored accumulator for {@code key}, if any. */
  public Optional<ACC> get(final K key) {
    return Optional.ofNullable(rows.get(key));
  }

  /** An immutable snapshot of all stored rows. */
  public Map<K, ACC> snapshot() {
    return Map.copyOf(rows);
  }
}
