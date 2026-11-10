/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.StampedLock;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import org.ehcache.spi.loaderwriter.CacheLoaderWriter;

/**
 * Loads cache misses from the backend and writes the cache's batches back to it.
 *
 * <p>A load checks the DirtyIndex before the backend, since EhCache can drop a pending op from the
 * cache before its version reaches the backend. EhCache runs the loader on the thread that reads
 * the cache, so the reader passes its own pass-through column families through {@link
 * #readThrough}.
 *
 * <p>A batch is written as one backend transaction, on a backend context of the writer thread's
 * own. Its versions then leave the DirtyIndex, unless a newer version of the key was published in
 * the meantime. Deletes arrive as tombstone puts, since the cache never removes entries.
 */
final class BackendLoaderWriter<ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue>
    implements CacheLoaderWriter<CacheKey, CachedValue> {

  private final DirtyIndex dirtyIndex;
  private final StampedLock publishLock;
  private final LongSupplier lastCommittedSeq;
  private final Runnable onFlushed;
  private final ThreadLocal<PassThroughColumnFamilies<ColumnFamilyNames>> writers;
  private final ThreadLocal<PassThroughColumnFamilies<ColumnFamilyNames>> readers =
      new ThreadLocal<>();

  BackendLoaderWriter(
      final ZeebeDb<ColumnFamilyNames> backend,
      final DirtyIndex dirtyIndex,
      final StampedLock publishLock,
      final LongSupplier lastCommittedSeq,
      final Runnable onFlushed) {
    this.dirtyIndex = dirtyIndex;
    this.publishLock = publishLock;
    this.lastCommittedSeq = lastCommittedSeq;
    this.onFlushed = onFlushed;
    writers =
        ThreadLocal.withInitial(
            () -> new PassThroughColumnFamilies<>(backend, backend.createContext()));
  }

  /**
   * Runs {@code read} with {@code columnFamilies} as the source of any cache load it triggers on
   * this thread.
   */
  <T> T readThrough(
      final PassThroughColumnFamilies<ColumnFamilyNames> columnFamilies, final Supplier<T> read) {
    readers.set(columnFamilies);
    try {
      return read.get();
    } finally {
      readers.remove();
    }
  }

  @Override
  public CachedValue load(final CacheKey key) {
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

    // read before the backend, so a commit landing in between makes the value look older, not newer
    final long seq = lastCommittedSeq.getAsLong();
    return new CachedValue(reader.get(key), seq);
  }

  @Override
  public void write(final CacheKey key, final CachedValue value) {
    writeAll(List.of(Map.entry(key, value)));
  }

  @Override
  public void writeAll(
      final Iterable<? extends Map.Entry<? extends CacheKey, ? extends CachedValue>> entries) {
    final List<Map.Entry<? extends CacheKey, ? extends CachedValue>> batch = new ArrayList<>();
    entries.forEach(batch::add);

    final var writer = writers.get();
    writer
        .context()
        .runInTransaction(
            () -> {
              for (final var entry : batch) {
                final var bytes = entry.getValue().bytes();
                if (bytes == null) {
                  writer.deleteIfExists(entry.getKey());
                } else {
                  writer.upsert(entry.getKey(), bytes);
                }
              }
            });

    final long stamp = publishLock.writeLock();
    try {
      for (final var entry : batch) {
        dirtyIndex.removeFlushed(entry.getKey(), entry.getValue().seq());
      }
    } finally {
      publishLock.unlockWrite(stamp);
    }
    onFlushed.run();
  }

  @Override
  public void delete(final CacheKey key) {
    throw new UnsupportedOperationException(
        "Expected deletes to arrive as tombstone puts, but the cache removed " + key);
  }
}
