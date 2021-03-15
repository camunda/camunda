/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl.rocksdb.transaction;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.rocksdb.Loggers;
import io.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.rocksdb.Checkpoint;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;

public class ZeebeTransactionDb<ColumnFamilyNames extends Enum<ColumnFamilyNames>>
    implements ZeebeDb<ColumnFamilyNames>, TransactionRenovator {

  private static final Logger LOG = Loggers.DB_LOGGER;
  private static final String ERROR_MESSAGE_CLOSE_RESOURCE =
      "Expected to close RocksDB resource successfully, but exception was thrown. Will continue to close remaining resources.";
  private final OptimisticTransactionDB optimisticTransactionDB;
  private final List<AutoCloseable> closables;
  private final ReadOptions prefixReadOptions;
  private final ReadOptions defaultReadOptions;
  private final WriteOptions defaultWriteOptions;
  private final ColumnFamilyHandle defaultHandle;
  private final long defaultNativeHandle;

  protected ZeebeTransactionDb(
      final ColumnFamilyHandle defaultHandle,
      final OptimisticTransactionDB optimisticTransactionDB,
      final List<AutoCloseable> closables,
      final RocksDbConfiguration rocksDbConfiguration) {
    this.defaultHandle = defaultHandle;
    defaultNativeHandle = getNativeHandle(defaultHandle);
    this.optimisticTransactionDB = optimisticTransactionDB;
    this.closables = closables;

    prefixReadOptions =
        new ReadOptions()
            .setPrefixSameAsStart(true)
            .setTotalOrderSeek(false)
            // setting a positive value to readahead is only useful when using network storage with
            // high latency, at the cost of making iterators expensiver (memory and computation
            // wise)
            .setReadaheadSize(0);
    closables.add(prefixReadOptions);
    defaultReadOptions = new ReadOptions();
    closables.add(defaultReadOptions);
    defaultWriteOptions = new WriteOptions().setDisableWAL(rocksDbConfiguration.isWalDisabled());
    closables.add(defaultWriteOptions);
  }

  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeTransactionDb<ColumnFamilyNames> openTransactionalDb(
          final Options options,
          final String path,
          final List<AutoCloseable> closables,
          final RocksDbConfiguration rocksDbConfiguration)
          throws RocksDBException {
    final OptimisticTransactionDB optimisticTransactionDB =
        OptimisticTransactionDB.open(options, path);
    closables.add(optimisticTransactionDB);
    final var defaultColumnFamilyHandle = optimisticTransactionDB.getDefaultColumnFamily();

    return new ZeebeTransactionDb<>(
        defaultColumnFamilyHandle, optimisticTransactionDB, closables, rocksDbConfiguration);
  }

  static long getNativeHandle(final RocksObject object) {
    try {
      return RocksDbInternal.nativeHandle.getLong(object);
    } catch (final IllegalAccessException e) {
      throw new RuntimeException(
          "Unexpected error occurred trying to access private nativeHandle_ field", e);
    }
  }

  protected ReadOptions getPrefixReadOptions() {
    return prefixReadOptions;
  }

  protected ColumnFamilyHandle getDefaultHandle() {
    return defaultHandle;
  }

  protected long getReadOptionsNativeHandle() {
    return getNativeHandle(defaultReadOptions);
  }

  protected long getDefaultNativeHandle() {
    return defaultNativeHandle;
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyNames columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    return new TransactionalColumnFamily<>(this, columnFamily, context, keyInstance, valueInstance);
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    try (final Checkpoint checkpoint = Checkpoint.create(optimisticTransactionDB)) {
      try {
        checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
      } catch (final RocksDBException rocksException) {
        throw new ZeebeDbException(
            String.format("Failed to take snapshot in path %s.", snapshotDir), rocksException);
      }
    }
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    String propertyValue = null;
    try {
      propertyValue = optimisticTransactionDB.getProperty(defaultHandle, propertyName);
    } catch (final RocksDBException rde) {
      LOG.debug(rde.getMessage(), rde);
    }
    return Optional.ofNullable(propertyValue);
  }

  @Override
  public TransactionContext createContext() {
    final Transaction transaction = optimisticTransactionDB.beginTransaction(defaultWriteOptions);
    final ZeebeTransaction zeebeTransaction = new ZeebeTransaction(transaction, this);
    closables.add(zeebeTransaction);
    return new DefaultTransactionContext(zeebeTransaction);
  }

  @Override
  public boolean isEmpty(
      final ColumnFamilyNames columnFamilyName, final TransactionContext context) {
    return createColumnFamily(columnFamilyName, context, DbNullKey.INSTANCE, DbNil.INSTANCE)
        .isEmpty();
  }

  @Override
  public Transaction renewTransaction(final Transaction oldTransaction) {
    return optimisticTransactionDB.beginTransaction(defaultWriteOptions, oldTransaction);
  }

  @Override
  public void close() {
    // Correct order of closing
    // 1. transaction
    // 2. options
    // 3. column family handles
    // 4. database
    // 5. db options
    // 6. column family options
    // https://github.com/facebook/rocksdb/wiki/RocksJava-Basics#opening-a-database-with-column-families
    Collections.reverse(closables);
    closables.forEach(
        closable -> {
          try {
            closable.close();
          } catch (final Exception e) {
            LOG.error(ERROR_MESSAGE_CLOSE_RESOURCE, e);
          }
        });
  }
}
