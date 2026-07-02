/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A heap-backed {@link ResultSink} for tests and demos: an idempotent map upsert (overwrite by
 * key). A durable implementation is an RDBMS upsert or an Elasticsearch index-by-deterministic-id.
 *
 * @param <K> the key type
 * @param <V> the value type
 */
public final class InMemoryResultSink<K, V> implements ResultSink<K, V> {

  private final Map<K, V> values = new HashMap<>();

  @Override
  public void upsert(final K key, final V value) {
    values.put(key, value);
  }

  public Optional<V> get(final K key) {
    return Optional.ofNullable(values.get(key));
  }

  public Map<K, V> snapshot() {
    return Map.copyOf(values);
  }
}
