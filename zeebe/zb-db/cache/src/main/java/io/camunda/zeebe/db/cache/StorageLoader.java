/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.protocol.EnumValue;
import java.util.function.Supplier;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;

/**
 * Loads cache misses from the storage. It writes nothing: the store's writer persists every
 * version, and EhCache only holds them.
 *
 * <p>A load checks the DirtyIndex before the storage. It runs inside EhCache's per-key compute, so
 * it installs either the newest committed version or a value that the committing thread's {@code
 * cache.put} overwrites; never an old storage value after a newer version was put and evicted.
 * EhCache runs the loader on the thread that reads the cache, so the reader passes its own storage
 * column families through {@link #readThrough}.
 */
final class StorageLoader<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
    implements CacheLoaderWriter<CacheKey, CachedValue> {

  /** Only the DirtyIndex and the queue use a version's seq; a loaded value needs none. */
  private static final long LOADED = 0;

  private final DirtyIndex dirtyIndex;
  private final WriteBehindMetrics metrics;
  private final ThreadLocal<StorageColumnFamilies<ColumnFamilyNames>> readers = new ThreadLocal<>();

  StorageLoader(final DirtyIndex dirtyIndex, final WriteBehindMetrics metrics) {
    this.dirtyIndex = dirtyIndex;
    this.metrics = metrics;
  }

  /**
   * Runs {@code read} with {@code columnFamilies} as the source of any cache load it triggers on
   * this thread.
   */
  <T> T readThrough(
      final StorageColumnFamilies<ColumnFamilyNames> columnFamilies, final Supplier<T> read) {
    readers.set(columnFamilies);
    try {
      return read.get();
    } finally {
      readers.remove();
    }
  }

  @Override
  public CachedValue load(final CacheKey key) {
    metrics.observeCacheLoad();
    final var unflushed = dirtyIndex.get(key);
    if (unflushed != null) {
      return unflushed;
    }

    final var reader = readers.get();
    if (reader == null) {
      throw new IllegalStateException(
          "Expected to load %s inside readThrough, but no reader is set on this thread"
              .formatted(key));
    }
    return new CachedValue(reader.get(key), LOADED);
  }

  @Override
  public void write(final CacheKey key, final CachedValue value) {
    // the store's writer persists versions; the cache only holds them
  }

  @Override
  public void delete(final CacheKey key) {
    throw new UnsupportedOperationException(
        "Expected deletes to arrive as tombstone puts, but the cache removed " + key);
  }
}
