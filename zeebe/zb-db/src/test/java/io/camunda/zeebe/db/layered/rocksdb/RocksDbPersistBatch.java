/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.rocksdb;

import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.layered.PersistBatch;
import java.util.Map;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

/**
 * A {@link PersistBatch} over a RocksDB {@code WriteBatch}. {@link #commit()} is a single {@code
 * db.write} call, which RocksDB applies atomically across all column families — the whole batch
 * lands in one write-ahead-log entry, so state entries and the anchor move as one cut.
 */
final class RocksDbPersistBatch implements PersistBatch {

  private final RocksDB db;
  private final WriteOptions writeOptions;
  private final Map<String, ColumnFamilyHandle> storeHandles;
  private final ColumnFamilyHandle anchorHandle;
  private final WriteBatch batch = new WriteBatch();
  private boolean anchorStaged;

  RocksDbPersistBatch(
      final RocksDB db,
      final WriteOptions writeOptions,
      final Map<String, ColumnFamilyHandle> storeHandles,
      final ColumnFamilyHandle anchorHandle) {
    this.db = db;
    this.writeOptions = writeOptions;
    this.storeHandles = storeHandles;
    this.anchorHandle = anchorHandle;
  }

  @Override
  public void put(final String storeName, final byte[] key, final byte[] value) {
    try {
      batch.put(handleFor(storeName), key, value);
    } catch (final RocksDBException e) {
      throw new ZeebeDbException("Failed to stage put for store '%s'".formatted(storeName), e);
    }
  }

  @Override
  public void delete(final String storeName, final byte[] key) {
    try {
      batch.delete(handleFor(storeName), key);
    } catch (final RocksDBException e) {
      throw new ZeebeDbException("Failed to stage delete for store '%s'".formatted(storeName), e);
    }
  }

  @Override
  public void putAnchor(final long position) {
    if (anchorStaged) {
      throw new IllegalStateException(
          "The recovery anchor was already staged in this batch; putAnchor must be called at most"
              + " once per batch");
    }
    try {
      batch.put(
          anchorHandle, RocksDbPersistSink.ANCHOR_KEY, RocksDbPersistSink.encodePosition(position));
    } catch (final RocksDBException e) {
      throw new ZeebeDbException("Failed to stage the recovery anchor", e);
    }
    anchorStaged = true;
  }

  @Override
  public void commit() throws Exception {
    db.write(writeOptions, batch);
  }

  @Override
  public void close() {
    // releases the native WriteBatch; anything staged but not committed is discarded
    batch.close();
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
