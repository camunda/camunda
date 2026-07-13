/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.rocksdb;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.layered.ReadSnapshot;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Snapshot;

/**
 * A {@link ReadSnapshot} pinned to a RocksDB snapshot: every read goes through {@code ReadOptions}
 * carrying the pinned sequence number, so writes committed after construction are invisible.
 */
final class RocksDbReadSnapshot implements ReadSnapshot {

  private final RocksDB db;
  private final Map<String, ColumnFamilyHandle> storeHandles;
  private final Snapshot snapshot;
  private final ReadOptions readOptions;
  private final AtomicBoolean closed = new AtomicBoolean();

  RocksDbReadSnapshot(final RocksDB db, final Map<String, ColumnFamilyHandle> storeHandles) {
    this.db = db;
    this.storeHandles = storeHandles;
    snapshot = db.getSnapshot();
    readOptions = new ReadOptions().setSnapshot(snapshot);
  }

  @Override
  public byte[] get(final String storeName, final byte[] key) {
    try {
      return db.get(handleFor(storeName), readOptions, key);
    } catch (final RocksDBException e) {
      throw new ZeebeDbException(
          "Failed to read from snapshot of store '%s'".formatted(storeName), e);
    }
  }

  @Override
  public void prefixScan(
      final String storeName, final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    RocksDbBytesStore.prefixScan(
        db.newIterator(handleFor(storeName), readOptions), prefix, visitor);
  }

  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      // the read options reference the snapshot, so they must be closed before it is released
      readOptions.close();
      db.releaseSnapshot(snapshot);
    }
  }

  private ColumnFamilyHandle handleFor(final String storeName) {
    final ColumnFamilyHandle handle = storeHandles.get(storeName);
    if (handle == null) {
      throw new IllegalArgumentException(
          "Unknown store '%s'; known stores: %s".formatted(storeName, storeHandles.keySet()));
    }
    return handle;
  }
}
