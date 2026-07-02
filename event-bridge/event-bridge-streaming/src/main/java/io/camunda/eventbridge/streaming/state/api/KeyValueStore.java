/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.api;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;

/**
 * A named, typed key-value store backed by a single column family. Writes are upserts; reads (and
 * scans) come from {@link ReadOnlyKeyValueStore}.
 *
 * <p>Keys and values are ZeebeDb flyweights passed by the caller; the store reads them eagerly on
 * each call, so a single flyweight instance may be reused across calls.
 *
 * @param <K> the key type (a {@link DbKey} flyweight)
 * @param <V> the value type (a {@link DbValue} flyweight)
 */
public interface KeyValueStore<K extends DbKey, V extends DbValue>
    extends ReadOnlyKeyValueStore<K, V> {

  /** Stores {@code value} under {@code key}, replacing any existing value. */
  void put(K key, V value);

  /** Removes the value under {@code key} if present; a no-op otherwise. */
  void delete(K key);
}
