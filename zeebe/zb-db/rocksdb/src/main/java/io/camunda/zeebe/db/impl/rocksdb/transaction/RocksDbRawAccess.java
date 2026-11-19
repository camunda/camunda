/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbInternal.isRocksDbExceptionRecoverable;

import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.RawDbAccess;
import io.camunda.zeebe.db.ZeebeDbException;
import java.io.File;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatch;
import org.rocksdb.WriteOptions;

/** {@link RawDbAccess} over the default column family of a RocksDB instance. */
final class RocksDbRawAccess implements RawDbAccess {

  private final RocksDB db;
  private final ColumnFamilyHandle defaultHandle;
  private final ReadOptions defaultReadOptions;
  private final ReadOptions prefixReadOptions;
  private final WriteOptions writeOptions;
  private final Consumer<File> checkpointer;

  RocksDbRawAccess(
      final RocksDB db,
      final ColumnFamilyHandle defaultHandle,
      final ReadOptions defaultReadOptions,
      final ReadOptions prefixReadOptions,
      final WriteOptions writeOptions,
      final Consumer<File> checkpointer) {
    this.db = db;
    this.defaultHandle = defaultHandle;
    this.defaultReadOptions = defaultReadOptions;
    this.prefixReadOptions = prefixReadOptions;
    this.writeOptions = writeOptions;
    this.checkpointer = checkpointer;
  }

  @Override
  public byte @Nullable [] get(final byte[] key, final int keyLength) {
    try {
      return db.get(defaultHandle, defaultReadOptions, key, 0, keyLength);
    } catch (final RocksDBException e) {
      throw translate("read a key", e);
    }
  }

  @Override
  public boolean keyMayExist(final byte[] key, final int keyLength) {
    // no value holder: the value is not needed, and RocksDB skips copying it
    return db.keyMayExist(defaultHandle, defaultReadOptions, key, 0, keyLength, null);
  }

  @Override
  public DbIterator newIterator() {
    return new RocksDbIterator(db.newIterator(defaultHandle, prefixReadOptions));
  }

  @Override
  public RawBatch newBatch() {
    return new RocksDbRawBatch();
  }

  @Override
  public void createCheckpoint(final File directory) {
    checkpointer.accept(directory);
  }

  private static RuntimeException translate(final String action, final RocksDBException e) {
    final var message = "Failed to %s in RocksDB".formatted(action);
    return isRocksDbExceptionRecoverable(e)
        ? new ZeebeDbException(message, e)
        : new RuntimeException(message, e);
  }

  private final class RocksDbRawBatch implements RawBatch {

    private final WriteBatch batch = new WriteBatch();

    @Override
    public void put(final byte[] key, final byte[] value) {
      try {
        batch.put(defaultHandle, key, value);
      } catch (final RocksDBException e) {
        throw translate("add a put to a batch", e);
      }
    }

    @Override
    public void delete(final byte[] key) {
      try {
        batch.delete(defaultHandle, key);
      } catch (final RocksDBException e) {
        throw translate("add a delete to a batch", e);
      }
    }

    @Override
    public void write() {
      try {
        db.write(writeOptions, batch);
      } catch (final RocksDBException e) {
        throw translate("write a batch", e);
      }
    }

    @Override
    public void close() {
      batch.close();
    }
  }
}
