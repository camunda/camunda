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
  private final Long2LongHashMap keyToTimestamp = new Long2LongHashMap(-1);

  // only used to keep track of how long the entries are existing and to clean up the
  // corresponding
  // maps
  private final NavigableMap<Long, LongArrayList> timestampToKeys = new TreeMap<>();

  void store(final long key, final long timestamp) {
    keyToTimestamp.put(key, timestamp);
    timestampToKeys.computeIfAbsent(timestamp, ignored -> new LongArrayList()).add(key);
  }

  long remove(final long key) {
    return keyToTimestamp.remove(key);
  }

  void cleanup(final long timestamp) {
    final var deadTime = timestamp - MetricsExporter.TIME_TO_LIVE.toMillis();
    final var outOfScopeInstances = timestampToKeys.headMap(deadTime);

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
}
