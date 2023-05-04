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
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
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
  private final Map<Integer, DirectBuffer> keyValueLRUCache;

  public ZeebeTransaction(
      final Transaction transaction, final TransactionRenovator transactionRenovator) {
    this.transactionRenovator = transactionRenovator;
    this.transaction = transaction;
    try {
      nativeHandle = RocksDbInternal.nativeHandle.getLong(transaction);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
    keyValueLRUCache = new HashMap<>();
  }

  public void put(
      final long columnFamilyHandle,
      final byte[] key,
      final int keyLength,
      final byte[] value,
      final int valueLength)
      throws Exception {

    RocksDbInternal.putWithHandle.invoke(
        transaction, nativeHandle, key, keyLength, value, valueLength, columnFamilyHandle, false);

    final var valueClone =
        BufferUtil.cloneBuffer(new UnsafeBuffer(value, 0, valueLength), 0, valueLength);

    final var offset = 0; // CF byte
    final int reducedLength = keyLength - (offset);
    keyValueLRUCache.put(hashCode(key, offset, reducedLength), valueClone);
  }

  public static int hashCode(final byte[] a, final int offset, final int length) {
    if (a == null) {
      return 0;
    } else {
      int result = 1;

      for (int arrOffset = offset; arrOffset < length; ++arrOffset) {
        final byte element = a[arrOffset];
        result = 31 * result + element;
      }

      return result;
    }
  }

  public byte[] get(
      final long columnFamilyHandle,
      final long readOptionsHandle,
      final byte[] key,
      final int keyLength)
      throws Exception {

    final var offset = 0; // CF byte
    final int reducedLength = keyLength - (offset);
    final DirectBuffer valueBuffer =
        keyValueLRUCache.computeIfAbsent(
            hashCode(key, offset, reducedLength),
            (h) -> {
              try {
                final var bytes =
                    (byte[])
                        RocksDbInternal.getWithHandle.invoke(
                            transaction,
                            nativeHandle,
                            readOptionsHandle,
                            key,
                            keyLength,
                            columnFamilyHandle);
                if (bytes != null) {
                  return new UnsafeBuffer(bytes);
                } else {
                  return new UnsafeBuffer(0, 0);
                }

              } catch (final IllegalAccessException e) {
                throw new RuntimeException(e);
              } catch (final InvocationTargetException e) {
                throw new RuntimeException(e);
              }
            });

    if (valueBuffer.capacity() == 0) {
      return null;
    } else {
      return valueBuffer.byteArray();
    }
  }

  public void delete(final long columnFamilyHandle, final byte[] key, final int keyLength)
      throws Exception {
    RocksDbInternal.removeWithHandle.invoke(
        transaction, nativeHandle, key, keyLength, columnFamilyHandle, false);

    final var offset = 0; // CF byte
    final int reducedLength = keyLength - (offset);
    keyValueLRUCache.remove(hashCode(key, offset, reducedLength));
  }

  public RocksIterator newIterator(final ReadOptions options, final ColumnFamilyHandle handle) {
    return transaction.getIterator(options, handle);
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
    keyValueLRUCache.clear();
  }

  void rollbackInternal() throws RocksDBException {
    inCurrentTransaction = false;
    transaction.rollback();
    keyValueLRUCache.clear();
  }

  @Override
  public void close() {
    transaction.close();
    keyValueLRUCache.clear();
  }
}
