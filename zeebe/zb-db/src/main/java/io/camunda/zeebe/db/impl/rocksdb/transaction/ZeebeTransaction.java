/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbInternal.isRocksDbExceptionRecoverable;

import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.util.VisibleForTesting;
import org.agrona.LangUtil;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;

public class ZeebeTransaction implements ZeebeDbTransaction, AutoCloseable {

  private final long nativeHandle;
  private final long dbNativeHandle;
  private final WriteBatchWithIndex writeBatch;
  private final RocksDB db;
  private final WriteOptions writeOptions;

  private boolean inCurrentTransaction;

  public ZeebeTransaction(final RocksDB db, final WriteOptions writeOptions) {
    this(new WriteBatchWithIndex(), db, writeOptions);
  }

  @VisibleForTesting
  ZeebeTransaction(
      final WriteBatchWithIndex writeBatch, final RocksDB db, final WriteOptions writeOptions) {
    this.writeBatch = writeBatch;
    this.db = db;
    this.writeOptions = writeOptions;
    try {
      // clear() uses placement new (Rep::Clear in write_batch_with_index.cc) — the native pointer
      // is stable across resets, so caching the handle here is safe for the object's lifetime.
      // If a future RocksDB upgrade changes clear() semantics, re-read the handle on each reset.
      nativeHandle = RocksDbInternal.nativeHandle.getLong(writeBatch);
      dbNativeHandle = RocksDbInternal.nativeHandle.getLong(db);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void put(
      final long columnFamilyHandle,
      final byte[] key,
      final int keyLength,
      final byte[] value,
      final int valueLength)
      throws Exception {
    // WriteBatchWithIndex.put is an instance method; the receiver must be the first arg.
    try {
      RocksDbInternal.putWithHandle.invokeExact(
          writeBatch, nativeHandle, key, keyLength, value, valueLength, columnFamilyHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public byte[] get(
      final long columnFamilyHandle,
      final long readOptionsHandle,
      final byte[] key,
      final int keyLength)
      throws Exception {
    try {
      return (byte[])
          RocksDbInternal.getWithHandle.invokeExact(
              nativeHandle, dbNativeHandle, readOptionsHandle, key, keyLength, columnFamilyHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }
  }

  public void delete(final long columnFamilyHandle, final byte[] key, final int keyLength)
      throws Exception {
    // WriteBatchWithIndex.delete is an instance method; the receiver must be the first arg.
    try {
      RocksDbInternal.removeWithHandle.invokeExact(
          writeBatch, nativeHandle, key, keyLength, columnFamilyHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public RocksIterator newIterator(final ReadOptions options, final ColumnFamilyHandle handle) {
    return writeBatch.newIteratorWithBase(handle, db.newIterator(handle, options));
  }

  void resetTransaction() {
    writeBatch.clear();
    inCurrentTransaction = true;
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }

  @Override
  public void run(final TransactionOperation operations) throws Exception {
    try {
      operations.run();
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction commit.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      }
      throw rdbex;
    }
  }

  @Override
  public void commit() throws RocksDBException {
    try {
      commitInternal();
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction commit.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      }
      throw rdbex;
    }
  }

  @Override
  public void rollback() throws RocksDBException {
    try {
      rollbackInternal();
    } catch (final RocksDBException rdbex) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction rollback.";
      if (isRocksDbExceptionRecoverable(rdbex)) {
        throw new ZeebeDbException(errorMessage, rdbex);
      }
      throw rdbex;
    }
  }

  void commitInternal() throws RocksDBException {
    inCurrentTransaction = false;
    db.write(writeOptions, writeBatch);
    writeBatch.clear();
  }

  void rollbackInternal() throws RocksDBException {
    inCurrentTransaction = false;
    writeBatch.clear();
  }

  @Override
  public void close() {
    writeBatch.close();
  }
}
