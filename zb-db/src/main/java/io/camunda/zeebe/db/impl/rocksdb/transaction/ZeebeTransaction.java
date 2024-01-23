/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbInternal.isRocksDbExceptionRecoverable;

import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDbException;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.util.CloseableSilently;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;
import org.agrona.LangUtil;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;

public class ZeebeTransaction implements ZeebeDbTransaction, AutoCloseable {

  private final long nativeHandle;
  private final TransactionRenovator transactionRenovator;

  private boolean inCurrentTransaction;
  private Transaction transaction;

  public ZeebeTransaction(
      final Transaction transaction, final TransactionRenovator transactionRenovator) {
    this.transactionRenovator = transactionRenovator;
    this.transaction = transaction;
    try {
      nativeHandle = RocksDbInternal.nativeHandle.getLong(transaction);
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
    try {
      RocksDbInternal.putWithHandle.invokeExact(
          transaction, nativeHandle, key, keyLength, value, valueLength, columnFamilyHandle, false);
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
              transaction, nativeHandle, readOptionsHandle, key, keyLength, columnFamilyHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }
  }

  public void delete(final long columnFamilyHandle, final byte[] key, final int keyLength)
      throws Exception {
    try {
      RocksDbInternal.removeWithHandle.invokeExact(
          transaction, nativeHandle, key, keyLength, columnFamilyHandle, false);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public XIterator newIterator(final ReadOptions options, final ColumnFamilyHandle handle) {
    return new XIterator(transaction.getIterator(options, handle));
  }

  void resetTransaction() {
    transaction = transactionRenovator.renewTransaction(transaction);
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
    transaction.commit();
  }

  void rollbackInternal() throws RocksDBException {
    inCurrentTransaction = false;
    transaction.rollback();
  }

  @Override
  public void close() {
    transaction.close();
  }

  public static class XIterator implements CloseableSilently {
    public static final AtomicInteger counter = new AtomicInteger(0);
    private final RocksIterator iterator;

    public XIterator(final RocksIterator iterator) {
      counter.incrementAndGet();
      this.iterator = iterator;
    }

    public byte[] key() {
      return iterator.key();
    }

    public int key(final ByteBuffer key) {
      return iterator.key(key);
    }

    public byte[] value() {
      return iterator.value();
    }

    public int value(final ByteBuffer value) {
      return iterator.value(value);
    }

    public boolean isValid() {
      return iterator.isValid();
    }

    public void seekToFirst() {
      iterator.seekToFirst();
    }

    public void seekToLast() {
      iterator.seekToLast();
    }

    public void seek(final byte[] target) {
      iterator.seek(target);
    }

    public void seekForPrev(final byte[] target) {
      iterator.seekForPrev(target);
    }

    public void seek(final ByteBuffer target) {
      iterator.seek(target);
    }

    public void seekForPrev(final ByteBuffer target) {
      iterator.seekForPrev(target);
    }

    public void next() {
      iterator.next();
    }

    public void prev() {
      iterator.prev();
    }

    public void refresh() throws RocksDBException {
      iterator.refresh();
    }

    public void status() throws RocksDBException {
      iterator.status();
    }

    public long getNativeHandle() {
      return iterator.getNativeHandle();
    }

    public boolean isOwningHandle() {
      return iterator.isOwningHandle();
    }

    @Override
    public void close() {
      iterator.close();
      counter.decrementAndGet();
    }
  }
}
