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
import java.nio.ByteBuffer;
import org.agrona.LangUtil;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;

public class ZeebeTransaction implements ZeebeDbTransaction, AutoCloseable {

  private final long nativeHandle;
  private final RocksDB rocksDB;
  private final TransactionRenovator transactionRenovator;

  private boolean inCurrentTransaction;
  private Transaction transaction;

  public ZeebeTransaction(
      final RocksDB rocksDB,
      final Transaction transaction,
      final TransactionRenovator transactionRenovator) {
    this.rocksDB = rocksDB;
    this.transactionRenovator = transactionRenovator;
    this.transaction = transaction;
    try {
      nativeHandle = RocksDbInternal.nativeHandle.getLong(transaction);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void put(
      final byte[] key,
      final int keyOffset,
      final int keyLength,
      final byte[] value,
      final int valueOffset,
      final int valueLength)
      throws Exception {
    try {
      final var keyBB = ByteBuffer.wrap(key, keyOffset, keyLength);
      final var valueBB = ByteBuffer.wrap(value, valueOffset, valueLength);
      transaction.put(keyBB, valueBB);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void put(final byte[] key, final int keyLength, final byte[] value, final int valueLength)
      throws Exception {
    put(key, 0, keyLength, value, 0, valueLength);
  }

  public byte[] get(
      final long columnFamilyHandle,
      final long readOptionsHandle,
      final byte[] key,
      final int keyLength)
      throws Exception {
    return get(columnFamilyHandle, readOptionsHandle, key, 0, keyLength);
  }

  public byte[] get(
      final long columnFamilyHandle,
      final long readOptionsHandle,
      final byte[] key,
      final int keyOffset,
      final int keyLength)
      throws Exception {
    try {
      return (byte[])
          RocksDbInternal.getWithHandle.invokeExact(
              nativeHandle, readOptionsHandle, key, keyOffset, keyLength, columnFamilyHandle);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
      return null; // unreachable
    }
  }

  public void delete(final long columnFamilyHandle, final byte[] key, final int keyLength)
      throws Exception {
    try {
      RocksDbInternal.removeWithHandle.invokeExact(
          nativeHandle, key, keyLength, columnFamilyHandle, false);
    } catch (final Throwable e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void deleteRange(final byte[] beginKey, final byte[] endKey) throws Exception {
    rocksDB.deleteRange(beginKey, endKey);
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
  }

  void rollbackInternal() throws RocksDBException {
    inCurrentTransaction = false;
    transaction.rollback();
  }

  @Override
  public void close() {
    transaction.close();
  }
}
