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
import io.camunda.zeebe.db.TransactionListener;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeTransaction;
import java.util.ArrayList;
import java.util.List;
import org.agrona.LangUtil;
import org.jspecify.annotations.Nullable;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteBatchWithIndex;
import org.rocksdb.WriteOptions;

/** {@link ZeebeTransaction} backed by a RocksDB {@link WriteBatchWithIndex}. */
public class WriteBatchTransaction implements ZeebeTransaction, AutoCloseable {

  private final long nativeHandle;
  private final long dbNativeHandle;
  private final WriteBatchWithIndex writeBatch;
  private final RocksDB db;
  private final WriteOptions writeOptions;
  private final ColumnFamilyHandle defaultHandle;
  private final long defaultNativeHandle;
  private final long readOptionsNativeHandle;
  private final ReadOptions prefixReadOptions;

  private final List<TransactionListener> listeners = new ArrayList<>();

  private boolean inCurrentTransaction;

  /**
   * Whether the listeners still have to be told how the current transaction ended. Unlike {@link
   * #inCurrentTransaction}, it stays set when a commit fails, so the rollback that follows is
   * reported; and it is cleared by a commit, so the rollback that {@link DefaultTransactionContext}
   * runs after every commit is not.
   */
  private boolean outcomePending;

  public WriteBatchTransaction(
      final RocksDB db,
      final WriteOptions writeOptions,
      final ColumnFamilyHandle defaultHandle,
      final ReadOptions defaultReadOptions,
      final ReadOptions prefixReadOptions) {
    // overwriteKey=true collapses repeated writes for the same key into a single index entry.
    // This ensures the delta-only WriteBatchWithIndex#newIterator does not return duplicates per
    // key. While we don't use it, it's still good to be prepared for it, and there is no cost to
    // setting this flag.
    writeBatch = new WriteBatchWithIndex(true);
    this.db = db;
    this.writeOptions = writeOptions;
    this.defaultHandle = defaultHandle;
    this.prefixReadOptions = prefixReadOptions;
    defaultNativeHandle = ZeebeTransactionDb.getNativeHandle(defaultHandle);
    readOptionsNativeHandle = ZeebeTransactionDb.getNativeHandle(defaultReadOptions);
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

  @Override
  public void put(final byte[] key, final int keyLength, final byte[] value, final int valueLength)
      throws Exception {
    // WriteBatchWithIndex.put is an instance method; the receiver must be the first arg.
    try {
      RocksDbInternal.putWithHandle.invokeExact(
          writeBatch, nativeHandle, key, keyLength, value, valueLength, defaultNativeHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  @Override
  public byte @Nullable [] get(final byte[] key, final int keyLength) throws Exception {
    try {
      return (byte[])
          RocksDbInternal.getWithHandle.invokeExact(
              nativeHandle,
              dbNativeHandle,
              readOptionsNativeHandle,
              key,
              keyLength,
              defaultNativeHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }
  }

  @Override
  public void delete(final byte[] key, final int keyLength) throws Exception {
    // WriteBatchWithIndex.delete is an instance method; the receiver must be the first arg.
    try {
      RocksDbInternal.removeWithHandle.invokeExact(
          writeBatch, nativeHandle, key, keyLength, defaultNativeHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  @Override
  public DbIterator newIterator(final byte[] prefix, final int prefixLength) {
    // the prefix isn't needed: the write batch overlay is already ordered and merged by RocksDB
    return new RocksDbIterator(
        writeBatch.newIteratorWithBase(
            defaultHandle, db.newIterator(defaultHandle, prefixReadOptions)));
  }

  @Override
  public void resetTransaction() {
    writeBatch.clear();
    inCurrentTransaction = true;
    outcomePending = true;
  }

  @Override
  public boolean isInCurrentTransaction() {
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

  @Override
  public void commitInternal() throws RocksDBException {
    inCurrentTransaction = false;
    db.write(writeOptions, writeBatch);
    writeBatch.clear();
    if (outcomePending) {
      outcomePending = false;
      for (final var listener : listeners) {
        listener.onCommit();
      }
    }
  }

  @Override
  public void rollbackInternal() throws RocksDBException {
    inCurrentTransaction = false;
    writeBatch.clear();
    if (outcomePending) {
      outcomePending = false;
      for (final var listener : listeners) {
        listener.onRollback();
      }
    }
  }

  @Override
  public void addListener(final TransactionListener listener) {
    listeners.add(listener);
  }

  @Override
  public void close() {
    writeBatch.close();
  }
}
