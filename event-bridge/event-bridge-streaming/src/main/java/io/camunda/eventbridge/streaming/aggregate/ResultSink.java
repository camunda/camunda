/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

/**
 * An idempotent serving sink: writes the <em>full current value</em> for a key, overwriting any
 * previous value. Because the write is keyed by a deterministic key and carries the whole value
 * (not a delta), re-applying it is a no-op — so reprocessing after a failure converges rather than
 * double-counts. This is what makes the pipeline correct against a non-transactional, eventually
 * consistent sink (e.g. Elasticsearch, keyed by a deterministic document id) as well as an RDBMS
 * upsert.
 *
 * @param <K> the deterministic key type
 * @param <V> the value type (the aggregate's read-facing result or accumulator)
 */
public interface ResultSink<K, V> {

  /** Idempotently sets the value for {@code key} (insert or overwrite). */
  void upsert(K key, V value);

  /**
   * Like {@link #upsert(Object, Object)}, but the caller also hands over {@code value}'s serialized
   * form (produced by the aggregation's own codec) so a sink that persists bytes does not serialize
   * the same value a second time. The default ignores the bytes and delegates to the two-argument
   * overload; sinks that store the serialized form override it.
   */
  default void upsert(final K key, final V value, final byte[] serialized) {
    upsert(key, value);
  }
}
