/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb.transaction;

import static io.zeebe.util.buffer.BufferUtil.startsWith;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.db.impl.rocksdb.Loggers;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.rocksdb.Checkpoint;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.OptimisticTransactionDB;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.RocksObject;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;
import org.slf4j.Logger;

public class ZeebeTransactionDb<ColumnFamilyNames extends Enum<ColumnFamilyNames>>
    implements ZeebeDb<ColumnFamilyNames> {

  private static final Logger LOG = Loggers.DB_LOGGER;
  private static final String ERROR_MESSAGE_CLOSE_RESOURCE =
      "Expected to close RocksDB resource successfully, but exception was thrown. Will continue to close remaining resources.";
  private final OptimisticTransactionDB optimisticTransactionDB;
  private final List<AutoCloseable> closables;
  private final EnumMap<ColumnFamilyNames, Long> columnFamilyMap;
  private final Long2ObjectHashMap<ColumnFamilyHandle> handelToEnumMap;
  private final ReadOptions prefixReadOptions;
  private final ReadOptions defaultReadOptions;
  private final WriteOptions defaultWriteOptions;
  private final ColumnFamilyHandle defaultHandle;

  protected ZeebeTransactionDb(
      final ColumnFamilyHandle defaultHandle,
      final OptimisticTransactionDB optimisticTransactionDB,
      final EnumMap<ColumnFamilyNames, Long> columnFamilyMap,
      final Long2ObjectHashMap<ColumnFamilyHandle> handelToEnumMap,
      final List<AutoCloseable> closables) {
    this.defaultHandle = defaultHandle;
    this.optimisticTransactionDB = optimisticTransactionDB;
    this.columnFamilyMap = columnFamilyMap;
    this.handelToEnumMap = handelToEnumMap;
    this.closables = closables;

    prefixReadOptions = new ReadOptions().setPrefixSameAsStart(true).setTotalOrderSeek(false);
    closables.add(prefixReadOptions);
    defaultReadOptions = new ReadOptions();
    closables.add(defaultReadOptions);
    defaultWriteOptions = new WriteOptions();
    closables.add(defaultWriteOptions);
  }

  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeTransactionDb<ColumnFamilyNames> openTransactionalDb(
          final DBOptions options,
          final String path,
          final List<ColumnFamilyDescriptor> columnFamilyDescriptors,
          final List<AutoCloseable> closables,
          final Class<ColumnFamilyNames> columnFamilyTypeClass)
          throws RocksDBException {
    final EnumMap<ColumnFamilyNames, Long> columnFamilyMap = new EnumMap<>(columnFamilyTypeClass);

    final List<ColumnFamilyHandle> handles = new ArrayList<>();
    final OptimisticTransactionDB optimisticTransactionDB =
        OptimisticTransactionDB.open(
            options, path, List.of(columnFamilyDescriptors.get(0)), handles);
    closables.add(optimisticTransactionDB);
    final var defaultHandle = handles.get(0);

    final ColumnFamilyNames[] enumConstants = columnFamilyTypeClass.getEnumConstants();
    final Long2ObjectHashMap<ColumnFamilyHandle> handleToEnumMap = new Long2ObjectHashMap<>();
    for (int i = 0; i < handles.size(); i++) {
      final ColumnFamilyHandle columnFamilyHandle = handles.get(i);
      closables.add(columnFamilyHandle);
      columnFamilyMap.put(enumConstants[i], getNativeHandle(columnFamilyHandle));
      handleToEnumMap.put(getNativeHandle(handles.get(i)), handles.get(i));
    }

    return new ZeebeTransactionDb<>(
        defaultHandle, optimisticTransactionDB, columnFamilyMap, handleToEnumMap, closables);
  }

  private static long getNativeHandle(final RocksObject object) {
    try {
      return RocksDbInternal.nativeHandle.getLong(object);
    } catch (final IllegalAccessException e) {
      throw new RuntimeException(
          "Unexpected error occurred trying to access private nativeHandle_ field", e);
    }
  }

  long getColumnFamilyHandle(final ColumnFamilyNames columnFamily) {
    return getNativeHandle(defaultHandle);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyNames columnFamily,
          final DbContext context,
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
  public DbContext createContext() {
    final Transaction transaction = optimisticTransactionDB.beginTransaction(defaultWriteOptions);
    final ZeebeTransaction zeebeTransaction = new ZeebeTransaction(transaction);
    closables.add(zeebeTransaction);
    return new DefaultDbContext(zeebeTransaction);
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// GET ///////////////////////////////////
  ////////////////////////////////////////////////////////////////////

  protected void put(
      final long columnFamilyHandle,
      final DbContext context,
      final DbKey key,
      final DbValue value) {
    ensureInOpenTransaction(
        context,
        transaction -> {
          context.writeKey(key);
          context.writeValue(value);

          transaction.put(
              columnFamilyHandle,
              context.getKeyBufferArray(),
              key.getLength(),
              context.getValueBufferArray(),
              value.getLength());
        });
  }

  private void ensureInOpenTransaction(
      final DbContext context, final TransactionConsumer operation) {
    context.runInTransaction(
        () -> operation.run((ZeebeTransaction) context.getCurrentTransaction()));
  }

  protected DirectBuffer get(
      final long columnFamilyHandle, final DbContext context, final DbKey key) {
    context.writeKey(key);
    final int keyLength = key.getLength();
    return getValue(columnFamilyHandle, context, keyLength);
  }

  private DirectBuffer getValue(
      final long columnFamilyHandle, final DbContext context, final int keyLength) {
    ensureInOpenTransaction(
        context,
        transaction -> {
          final byte[] value =
              transaction.get(
                  columnFamilyHandle,
                  getNativeHandle(defaultReadOptions),
                  context.getKeyBufferArray(),
                  keyLength);
          context.wrapValueView(value);
        });
    return context.getValueView();
  }

  @Override
  public Optional<String> getProperty(
      final ColumnFamilyNames columnFamilyName, final String propertyName) {

    final var handle = handelToEnumMap.get(columnFamilyMap.get(columnFamilyName));

    String propertyValue = null;
    try {
      propertyValue = optimisticTransactionDB.getProperty(handle, propertyName);
    } catch (final RocksDBException rde) {
      LOG.debug(rde.getMessage(), rde);
    }
    return Optional.ofNullable(propertyValue);
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// ITERATION /////////////////////////////
  ////////////////////////////////////////////////////////////////////

  protected boolean exists(
      final long columnFamilyHandle, final DbContext context, final DbKey key) {
    context.wrapValueView(new byte[0]);
    ensureInOpenTransaction(
        context,
        transaction -> {
          context.writeKey(key);
          getValue(columnFamilyHandle, context, key.getLength());
        });
    return !context.isValueViewEmpty();
  }

  protected void delete(final long columnFamilyHandle, final DbContext context, final DbKey key) {
    context.writeKey(key);

    ensureInOpenTransaction(
        context,
        transaction ->
            transaction.delete(columnFamilyHandle, context.getKeyBufferArray(), key.getLength()));
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// ITERATION /////////////////////////////
  ////////////////////////////////////////////////////////////////////

  RocksIterator newIterator(
      final long columnFamilyHandle, final DbContext context, final ReadOptions options) {
    final ColumnFamilyHandle handle = handelToEnumMap.get(columnFamilyHandle);
    return context.newIterator(options, handle);
  }

  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final DbLong columnFamilyKey,
      final long columnFamilyHandle,
      final DbContext context,
      final DbKey prefix,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        columnFamilyKey,
        columnFamilyHandle,
        context,
        prefix,
        keyInstance,
        valueInstance,
        (k, v) -> {
          visitor.accept(k, v);
          return true;
        });
  }

  // This method is used mainly from other iterator methods to iterate over column family entries,
  // which are prefixed with column family key.
  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final DbLong columnFamilyKey,
      final long columnFamilyHandle,
      final DbContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        columnFamilyKey,
        columnFamilyHandle,
        context,
        new DbNullKey(),
        keyInstance,
        valueInstance,
        (k, v) -> {
          visitor.accept(k, v);
          return true;
        });
  }

  // This method is used mainly from other iterator methods to iterate over column family entries,
  // which are prefixed with column family key.
  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final DbLong columnFamilyKey,
      final long columnFamilyHandle,
      final DbContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        columnFamilyKey,
        columnFamilyHandle,
        context,
        new DbNullKey(),
        keyInstance,
        valueInstance,
        visitor);
  }

  /**
   * NOTE: it doesn't seem possible in Java RocksDB to set a flexible prefix extractor on iterators
   * at the moment, so using prefixes seem to be mostly related to skipping files that do not
   * contain keys with the given prefix (which is useful anyway), but it will still iterate over all
   * keys contained in those files, so we still need to make sure the key actually matches the
   * prefix.
   *
   * <p>While iterating over subsequent keys we have to validate it.
   */
  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final DbLong columnFamilyKey,
      final long columnFamilyHandle,
      final DbContext context,
      final DbKey prefix,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.withPrefixKeyBuffer(
        prefixKeyBuffer ->
            ensureInOpenTransaction(
                context,
                transaction -> {
                  try (final RocksIterator iterator =
                      newIterator(columnFamilyHandle, context, prefixReadOptions)) {

                    columnFamilyKey.write(prefixKeyBuffer, 0);
                    prefix.write(prefixKeyBuffer, Long.BYTES);
                    final int prefixLength = Long.BYTES + prefix.getLength();

                    boolean shouldVisitNext = true;

                    for (RocksDbInternal.seek(
                            iterator,
                            getNativeHandle(iterator),
                            prefixKeyBuffer.byteArray(),
                            prefixLength);
                        iterator.isValid() && shouldVisitNext;
                        iterator.next()) {
                      final byte[] keyBytes = iterator.key();
                      if (!startsWith(
                          prefixKeyBuffer.byteArray(),
                          0,
                          prefixLength,
                          keyBytes,
                          0,
                          keyBytes.length)) {
                        break;
                      }

                      shouldVisitNext =
                          visit(context, keyInstance, valueInstance, visitor, iterator);
                    }
                  }
                }));
  }

  private <KeyType extends DbKey, ValueType extends DbValue> boolean visit(
      final DbContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> iteratorConsumer,
      final RocksIterator iterator) {
    final var keyBytes = iterator.key();

    context.wrapKeyView(keyBytes);
    context.wrapValueView(iterator.value());

    final DirectBuffer keyViewBuffer = context.getKeyView();
    keyInstance.wrap(keyViewBuffer, 0, keyViewBuffer.capacity());
    final DirectBuffer valueViewBuffer = context.getValueView();
    valueInstance.wrap(valueViewBuffer, 0, valueViewBuffer.capacity());

    return iteratorConsumer.visit(keyInstance, valueInstance);
  }

  public boolean isEmpty(
      final long columnFamilyKey, final long columnFamilyHandle, final DbContext context) {
    final var columnKey = new DbLong();
    columnKey.wrapLong(columnFamilyKey);
    final AtomicBoolean isEmpty = new AtomicBoolean(true);
    whileEqualPrefix(
        columnKey,
        columnFamilyHandle,
        context,
        DbNullKey.INSTANCE,
        DbNil.INSTANCE,
        (key, value) -> {
          isEmpty.set(false);
          return false;
        });
    return isEmpty.get();
  }

  @Override
  public boolean isEmpty(final ColumnFamilyNames columnFamilyName, final DbContext context) {
    return isEmpty(columnFamilyName.ordinal(), getNativeHandle(defaultHandle), context);
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

  @FunctionalInterface
  interface TransactionConsumer {

    void run(ZeebeTransaction transaction) throws Exception;
  }

  /**
   * This class is used only internally by #whileEqualPrefix to search for same column family
   * prefix.
   */
  private static final class DbNullKey implements DbKey {

    public static final DbNullKey INSTANCE = new DbNullKey();

    private DbNullKey() {}

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      // do nothing
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      // do nothing
    }

    @Override
    public int getLength() {
      return 0;
    }
  }
}
