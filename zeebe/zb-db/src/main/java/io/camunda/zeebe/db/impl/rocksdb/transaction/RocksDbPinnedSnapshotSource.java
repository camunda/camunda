/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.db.layered.ReadSnapshot;
import io.camunda.zeebe.db.layered.SnapshotSource;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Snapshot;

/**
 * A pinning {@link SnapshotSource} over the same RocksDB instance a {@link ZeebeTransactionDb}
 * writes: every {@link ReadSnapshot} is backed by one {@code db.getSnapshot()}, so reads see
 * exactly the committed state at pin time no matter what commits concurrently — the property the
 * layered store's read views need once asynchronous readers consume them (see {@code
 * io.camunda.zeebe.db.layered.ReadOnlyView}).
 *
 * <p><b>Key routing:</b> the transactional database multiplexes all logical column families into
 * the single default RocksDB column family, prefixing every key with the 8-byte big-endian value of
 * its column family enum (see {@link ColumnFamilyContext}). This source replicates that routing:
 * store names are the enum constant names (matching the store names a {@code LayeredZeebeDb}
 * derives from the same constants), reads prepend the corresponding prefix, and scans strip it
 * before handing keys to the visitor.
 *
 * <p><b>Threading and lifecycle:</b> {@link #takeSnapshot()} is owner-thread only (the layered
 * coordinator's rotation); the returned snapshots are safe to read from any number of concurrent
 * threads — every scan opens its own iterator, and the shared read options are never mutated after
 * construction. Each snapshot owns its native resources and releases them on close; the source
 * itself holds none, but must not be used after the database closed.
 */
public final class RocksDbPinnedSnapshotSource implements SnapshotSource {

  private final RocksDB db;
  private final ColumnFamilyHandle defaultHandle;
  private final Map<String, byte[]> prefixByStore;

  private RocksDbPinnedSnapshotSource(
      final RocksDB db,
      final ColumnFamilyHandle defaultHandle,
      final Map<String, byte[]> prefixByStore) {
    this.db = db;
    this.defaultHandle = defaultHandle;
    this.prefixByStore = prefixByStore;
  }

  /**
   * Creates a source routing the given enum's constants: each constant's {@code name()} becomes a
   * store name mapped to its {@code getValue()} key prefix.
   */
  static <ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
      RocksDbPinnedSnapshotSource of(
          final RocksDB db,
          final ColumnFamilyHandle defaultHandle,
          final Class<ColumnFamilyType> columnFamilyType) {
    Objects.requireNonNull(db, "db");
    Objects.requireNonNull(defaultHandle, "defaultHandle");
    Objects.requireNonNull(columnFamilyType, "columnFamilyType");
    final Map<String, byte[]> prefixByStore = new HashMap<>();
    for (final ColumnFamilyType columnFamily : columnFamilyType.getEnumConstants()) {
      final byte[] prefix = new byte[Long.BYTES];
      new UnsafeBuffer(prefix)
          .putLong(0, columnFamily.getValue(), ZeebeDbConstants.ZB_DB_BYTE_ORDER);
      prefixByStore.put(columnFamily.name(), prefix);
    }
    return new RocksDbPinnedSnapshotSource(db, defaultHandle, Map.copyOf(prefixByStore));
  }

  @Override
  public ReadSnapshot takeSnapshot() {
    return new PinnedSnapshot();
  }

  private byte[] prefixOf(final String storeName) {
    final byte[] prefix = prefixByStore.get(storeName);
    if (prefix == null) {
      throw new IllegalArgumentException(
          "Unknown store '%s'; known stores: %s".formatted(storeName, prefixByStore.keySet()));
    }
    return prefix;
  }

  private static byte[] concat(final byte[] prefix, final byte[] suffix) {
    final byte[] combined = Arrays.copyOf(prefix, prefix.length + suffix.length);
    System.arraycopy(suffix, 0, combined, prefix.length, suffix.length);
    return combined;
  }

  private final class PinnedSnapshot implements ReadSnapshot {

    private final Snapshot snapshot;
    private final ReadOptions pointReadOptions;
    private final ReadOptions scanReadOptions;
    private final AtomicBoolean closed = new AtomicBoolean();

    private PinnedSnapshot() {
      snapshot = db.getSnapshot();
      pointReadOptions = new ReadOptions().setSnapshot(snapshot);
      // scans mirror the transactional database's prefix iteration settings: every scanned range
      // stays within one column family's 8-byte prefix, matching the configured prefix extractor
      scanReadOptions =
          new ReadOptions()
              .setSnapshot(snapshot)
              .setPrefixSameAsStart(true)
              .setTotalOrderSeek(false);
    }

    @Override
    public byte[] get(final String storeName, final byte[] key) {
      try {
        return db.get(defaultHandle, pointReadOptions, concat(prefixOf(storeName), key));
      } catch (final RocksDBException e) {
        throw new ZeebeDbException(
            "Failed to read from pinned snapshot of store '%s'".formatted(storeName), e);
      }
    }

    @Override
    public void prefixScan(
        final String storeName, final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
      final byte[] columnFamilyPrefix = prefixOf(storeName);
      final byte[] prefixedPrefix = concat(columnFamilyPrefix, prefix);
      try (final RocksIterator iterator = db.newIterator(defaultHandle, scanReadOptions)) {
        for (iterator.seek(prefixedPrefix); iterator.isValid(); iterator.next()) {
          final byte[] prefixedKey = iterator.key();
          if (!startsWith(prefixedKey, prefixedPrefix)) {
            return;
          }
          visitor.accept(
              Arrays.copyOfRange(prefixedKey, columnFamilyPrefix.length, prefixedKey.length),
              iterator.value());
        }
      }
    }

    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        // the read options reference the snapshot, so they must be closed before it is released
        pointReadOptions.close();
        scanReadOptions.close();
        db.releaseSnapshot(snapshot);
      }
    }

    private boolean startsWith(final byte[] key, final byte[] prefix) {
      return key.length >= prefix.length
          && Arrays.equals(key, 0, prefix.length, prefix, 0, prefix.length);
    }
  }
}
