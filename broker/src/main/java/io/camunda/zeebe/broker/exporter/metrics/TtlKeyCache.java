/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.exporter.metrics;

import java.util.NavigableMap;
import java.util.TreeMap;
import org.agrona.collections.Long2LongHashMap;
import org.agrona.collections.LongArrayList;

final class TtlKeyCache {
  private static final int DEFAULT_MAX_CAPACITY = 10_000;

  private final Long2LongHashMap keyToTimestamp = new Long2LongHashMap(-1);

  // only used to keep track of how long the entries are existing and to clean up the
  // corresponding
  // maps
  private final NavigableMap<Long, LongArrayList> timestampToKeys = new TreeMap<>();

  private final int maxCapacity;

  TtlKeyCache() {
    this(DEFAULT_MAX_CAPACITY);
  }

  /**
   * @param maxCapacity the maximum number of entries that can be cached, regardless of TTL
   */
  TtlKeyCache(final int maxCapacity) {
    this.maxCapacity = maxCapacity;
  }

  void store(final long key, final long timestamp) {
    if (keyToTimestamp.size() >= maxCapacity) {
      evictOldestKey();
    }

    keyToTimestamp.put(key, timestamp);
    timestampToKeys.computeIfAbsent(timestamp, ignored -> new LongArrayList()).add(key);
  }

  long remove(final long key) {
    final var timestamp = keyToTimestamp.remove(key);
    final var keys = timestampToKeys.get(timestamp);

    if (keys != null) {
      keys.remove(key);
      if (keys.isEmpty()) {
        timestampToKeys.remove(timestamp);
      }
    }

    return timestamp;
  }

  void cleanup(final long timestamp) {
    final var outOfScopeInstances = timestampToKeys.headMap(timestamp);
    for (final LongArrayList keys : outOfScopeInstances.values()) {
      keys.forEach(keyToTimestamp::remove);
    }

    outOfScopeInstances.clear();
  }

  boolean isEmpty() {
    return keyToTimestamp.isEmpty() && timestampToKeys.isEmpty();
  }

  void clear() {
    keyToTimestamp.clear();
    timestampToKeys.clear();
  }

  int size() {
    return keyToTimestamp.size();
  }

  private void evictOldestKey() {
    final var oldestEntries = timestampToKeys.firstEntry();
    if (oldestEntries != null) {
      final var evictedKey = oldestEntries.getValue().remove(0);
      if (evictedKey != null) {
        remove(evictedKey);
      }
    }
  }
}
