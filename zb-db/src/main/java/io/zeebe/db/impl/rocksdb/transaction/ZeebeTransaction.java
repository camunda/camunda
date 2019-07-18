/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb.transaction;

import static io.zeebe.db.impl.rocksdb.transaction.RocksDbInternal.isRocksDbExceptionRecoverable;

import io.zeebe.db.TransactionOperation;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.ZeebeDbTransaction;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.Transaction;

public class ZeebeTransaction implements ZeebeDbTransaction, AutoCloseable {

  private final Transaction transaction;
  private final long nativeHandle;
  private boolean inCurrentTransaction;

  public ZeebeTransaction(Transaction transaction) {
    this.transaction = transaction;
    try {
      nativeHandle = RocksDbInternal.nativeHandle.getLong(transaction);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void put(long columnFamilyHandle, byte[] key, int keyLength, byte[] value, int valueLength)
      throws Exception {
    RocksDbInternal.putWithHandle.invoke(
        transaction, nativeHandle, key, keyLength, value, valueLength, columnFamilyHandle, false);
  }

  public byte[] get(long columnFamilyHandle, long readOptionsHandle, byte[] key, int keyLength)
      throws Exception {
    return (byte[])
        RocksDbInternal.getWithHandle.invoke(
            transaction, nativeHandle, readOptionsHandle, key, keyLength, columnFamilyHandle);
  }

  public void delete(long columnFamilyHandle, byte[] key, int keyLength) throws Exception {
    RocksDbInternal.removeWithHandle.invoke(
        transaction, nativeHandle, key, keyLength, columnFamilyHandle, false);
  }

  public RocksIterator newIterator(ReadOptions options, ColumnFamilyHandle handle) {
    return transaction.getIterator(options, handle);
  }

  void resetTransaction() {
    inCurrentTransaction = true;
  }

  boolean isInCurrentTransaction() {
    return inCurrentTransaction;
  }

  @Override
  public void run(TransactionOperation operations) throws Exception {
    try {
      operations.run();
    } catch (RocksDBException rdbex) {
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
    } catch (RocksDBException rdbex) {
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
    } catch (RocksDBException rdbex) {
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

  public void close() {
    transaction.close();
  }
}
