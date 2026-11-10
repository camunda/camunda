/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.protocol.EnumValue;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;

/**
 * The versions that are committed but not yet written to the backend, with one sorted map per
 * column family so that a scan copies from a single map.
 *
 * <p>Only publication and the cleanup after a flush change it, and both hold the store's publish
 * lock, so the changes need no locking of their own. Reads can run at any time.
 */
final class DirtyIndex {

  private final Map<EnumValue, ConcurrentNavigableMap<CacheKey, CachedValue>> columnFamilies =
      new ConcurrentHashMap<>();
  private final AtomicLong bytes = new AtomicLong();

  @Nullable CachedValue get(final CacheKey key) {
    final var versions = columnFamilies.get(key.columnFamily());
    return versions == null ? null : versions.get(key);
  }

  /** Publishes {@code value}, replacing any older unflushed version of the key. */
  void put(final CacheKey key, final CachedValue value) {
    final var previous =
        columnFamilies
            .computeIfAbsent(key.columnFamily(), cf -> new ConcurrentSkipListMap<>())
            .put(key, value);
    bytes.addAndGet(size(key, value) - (previous == null ? 0 : size(key, previous)));
  }

  /**
   * Removes the version of {@code key} that was flushed with {@code seq}. A newer version stays,
   * since it still has to be flushed.
   */
  void removeFlushed(final CacheKey key, final long seq) {
    final var versions = columnFamilies.get(key.columnFamily());
    if (versions == null) {
      return;
    }

    final var current = versions.get(key);
    if (current != null && current.seq() == seq) {
      versions.remove(key);
      bytes.addAndGet(-size(key, current));
    }
  }

  /**
   * Copies the unflushed versions of {@code columnFamily} between {@code from} and {@code to}. A
   * {@code null} bound leaves that side open.
   */
  NavigableMap<CacheKey, CachedValue> copy(
      final EnumValue columnFamily,
      final @Nullable CacheKey from,
      final boolean fromInclusive,
      final @Nullable CacheKey to,
      final boolean toInclusive) {
    NavigableMap<CacheKey, CachedValue> range = columnFamilies.get(columnFamily);
    if (range == null) {
      return new TreeMap<>();
    }
    if (from != null) {
      range = range.tailMap(from, fromInclusive);
    }
    if (to != null) {
      range = range.headMap(to, toInclusive);
    }
    return new TreeMap<>(range);
  }

  boolean isEmpty() {
    return columnFamilies.values().stream().allMatch(Map::isEmpty);
  }

  /** Returns the key and value bytes of all unflushed versions. */
  long bytes() {
    return bytes.get();
  }

  private static long size(final CacheKey key, final CachedValue value) {
    final var valueBytes = value.bytes();
    return key.key().length + (valueBytes == null ? 0 : valueBytes.length);
  }
}
