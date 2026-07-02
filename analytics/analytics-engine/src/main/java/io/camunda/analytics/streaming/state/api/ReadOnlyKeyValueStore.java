/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.state.api;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * The read view of a {@link KeyValueStore} — all the enrichment path needs. Keeping reads behind
 * this interface lets a caller (e.g. a stream-table join that enriches a fact from prior state)
 * depend only on reads, and lets the store be backed by something other than the local RocksDB
 * later without touching the read site.
 *
 * <p>Keys and values are ZeebeDb flyweights. As with ZeebeDb itself, a value returned by {@link
 * #get} (or visited during a scan) is only valid until the next store operation — copy out the
 * fields you keep (e.g. into an immutable record) before calling the store again.
 *
 * @param <K> the key type (a {@link DbKey} flyweight)
 * @param <V> the value type (a {@link DbValue} flyweight)
 */
public interface ReadOnlyKeyValueStore<K extends DbKey, V extends DbValue> {

  /**
   * The value stored under {@code key}, or empty if absent. Valid until the next store operation.
   */
  Optional<V> get(K key);

  /** Whether a value is stored under {@code key}. */
  boolean exists(K key);

  /**
   * Visits every entry whose key starts with {@code prefix}, in key order. The prefix is a partial
   * key — e.g. scan all {@code (instanceKey, variableName)} entries for one {@code instanceKey} by
   * passing just the {@code instanceKey} component.
   */
  void prefixScan(DbKey prefix, BiConsumer<K, V> visitor);

  /** Visits every entry in the store, in key order. */
  void forEach(BiConsumer<K, V> visitor);
}
