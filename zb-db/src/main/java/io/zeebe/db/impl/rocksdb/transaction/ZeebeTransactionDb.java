/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
import io.zeebe.db.impl.rocksdb.Loggers;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
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

  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeTransactionDb<ColumnFamilyNames> openTransactionalDb(
          final DBOptions options,
          final String path,
          final List<ColumnFamilyDescriptor> columnFamilyDescriptors,
          final List<AutoCloseable> closables,
          Class<ColumnFamilyNames> columnFamilyTypeClass)
          throws RocksDBException {
    final EnumMap<ColumnFamilyNames, Long> columnFamilyMap = new EnumMap<>(columnFamilyTypeClass);

    final List<ColumnFamilyHandle> handles = new ArrayList<>();
    final OptimisticTransactionDB optimisticTransactionDB =
        OptimisticTransactionDB.open(options, path, columnFamilyDescriptors, handles);
    closables.add(optimisticTransactionDB);

    final ColumnFamilyNames[] enumConstants = columnFamilyTypeClass.getEnumConstants();
    final Long2ObjectHashMap<ColumnFamilyHandle> handleToEnumMap = new Long2ObjectHashMap<>();
    for (int i = 0; i < handles.size(); i++) {
      final ColumnFamilyHandle columnFamilyHandle = handles.get(i);
      closables.add(columnFamilyHandle);
      columnFamilyMap.put(enumConstants[i], getNativeHandle(columnFamilyHandle));
      handleToEnumMap.put(getNativeHandle(handles.get(i)), handles.get(i));
    }

    return new ZeebeTransactionDb<>(
        optimisticTransactionDB, columnFamilyMap, handleToEnumMap, closables);
  }

  private static long getNativeHandle(final RocksObject object) {
    try {
      return RocksDbInternal.nativeHandle.getLong(object);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Unexpected error occurred trying to access private nativeHandle_ field", e);
    }
  }

  private final OptimisticTransactionDB optimisticTransactionDB;
  private final List<AutoCloseable> closables;

  private final EnumMap<ColumnFamilyNames, Long> columnFamilyMap;
  private final Long2ObjectHashMap<ColumnFamilyHandle> handelToEnumMap;

  private final ReadOptions prefixReadOptions;
  private final ReadOptions defaultReadOptions;
  private final WriteOptions defaultWriteOptions;

  protected ZeebeTransactionDb(
      OptimisticTransactionDB optimisticTransactionDB,
      EnumMap<ColumnFamilyNames, Long> columnFamilyMap,
      Long2ObjectHashMap<ColumnFamilyHandle> handelToEnumMap,
      List<AutoCloseable> closables) {
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

  long getColumnFamilyHandle(ColumnFamilyNames columnFamily) {
    return columnFamilyMap.get(columnFamily);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          ColumnFamilyNames columnFamily,
          DbContext context,
          KeyType keyInstance,
          ValueType valueInstance) {
    return new TransactionalColumnFamily<>(this, columnFamily, context, keyInstance, valueInstance);
  }

  protected void put(long columnFamilyHandle, DbContext context, DbKey key, DbValue value) {
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

  private void ensureInOpenTransaction(DbContext context, TransactionConsumer operation) {
    context.runInTransaction(
        () -> operation.run((ZeebeTransaction) context.getCurrentTransaction()));
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// GET ///////////////////////////////////
  ////////////////////////////////////////////////////////////////////

  protected DirectBuffer get(long columnFamilyHandle, DbContext context, DbKey key) {
    context.writeKey(key);
    final int keyLength = key.getLength();
    return getValue(columnFamilyHandle, context, keyLength);
  }

  private DirectBuffer getValue(long columnFamilyHandle, DbContext context, int keyLength) {
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

  protected boolean exists(long columnFamilyHandle, DbContext context, DbKey key) {
    context.wrapValueView(new byte[0]);
    ensureInOpenTransaction(
        context,
        transaction -> {
          context.writeKey(key);
          getValue(columnFamilyHandle, context, key.getLength());
        });
    return !context.isValueViewEmpty();
  }

  protected void delete(long columnFamilyHandle, DbContext context, DbKey key) {
    context.writeKey(key);

    ensureInOpenTransaction(
        context,
        transaction ->
            transaction.delete(columnFamilyHandle, context.getKeyBufferArray(), key.getLength()));
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// ITERATION /////////////////////////////
  ////////////////////////////////////////////////////////////////////

  RocksIterator newIterator(long columnFamilyHandle, DbContext context, ReadOptions options) {
    final ColumnFamilyHandle handle = handelToEnumMap.get(columnFamilyHandle);
    return context.newIterator(options, handle);
  }

  public <ValueType extends DbValue> void foreach(
      long columnFamilyHandle,
      DbContext context,
      ValueType iteratorValue,
      Consumer<ValueType> consumer) {
    foreach(
        columnFamilyHandle,
        context,
        (keyBuffer, valueBuffer) -> {
          iteratorValue.wrap(valueBuffer, 0, valueBuffer.capacity());
          consumer.accept(iteratorValue);
        });
  }

  public <KeyType extends DbKey, ValueType extends DbValue> void foreach(
      long columnFamilyHandle,
      DbContext context,
      KeyType iteratorKey,
      ValueType iteratorValue,
      BiConsumer<KeyType, ValueType> consumer) {
    foreach(
        columnFamilyHandle,
        context,
        (keyBuffer, valueBuffer) -> {
          iteratorKey.wrap(keyBuffer, 0, keyBuffer.capacity());
          iteratorValue.wrap(valueBuffer, 0, valueBuffer.capacity());
          consumer.accept(iteratorKey, iteratorValue);
        });
  }

  private void foreach(
      long columnFamilyHandle,
      DbContext context,
      BiConsumer<DirectBuffer, DirectBuffer> keyValuePairConsumer) {
    ensureInOpenTransaction(
        context,
        transaction -> {
          try (RocksIterator iterator =
              newIterator(columnFamilyHandle, context, defaultReadOptions)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
              context.wrapKeyView(iterator.key());
              context.wrapValueView(iterator.value());
              keyValuePairConsumer.accept(context.getKeyView(), context.getValueView());
            }
          }
        });
  }

  public <KeyType extends DbKey, ValueType extends DbValue> void whileTrue(
      long columnFamilyHandle,
      DbContext context,
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> visitor) {
    ensureInOpenTransaction(
        context,
        transaction -> {
          try (RocksIterator iterator =
              newIterator(columnFamilyHandle, context, defaultReadOptions)) {
            boolean shouldVisitNext = true;
            for (iterator.seekToFirst(); iterator.isValid() && shouldVisitNext; iterator.next()) {
              shouldVisitNext = visit(context, keyInstance, valueInstance, visitor, iterator);
            }
          }
        });
  }

  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      long columnFamilyHandle,
      DbContext context,
      DbKey prefix,
      KeyType keyInstance,
      ValueType valueInstance,
      BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
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
      long columnFamilyHandle,
      DbContext context,
      DbKey prefix,
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.withPrefixKeyBuffer(
        prefixKeyBuffer ->
            ensureInOpenTransaction(
                context,
                transaction -> {
                  try (RocksIterator iterator =
                      newIterator(columnFamilyHandle, context, prefixReadOptions)) {
                    prefix.write(prefixKeyBuffer, 0);
                    final int prefixLength = prefix.getLength();

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
                          prefix.getLength(),
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
      DbContext context,
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> iteratorConsumer,
      RocksIterator iterator) {
    context.wrapKeyView(iterator.key());
    context.wrapValueView(iterator.value());

    final DirectBuffer keyViewBuffer = context.getKeyView();
    keyInstance.wrap(keyViewBuffer, 0, keyViewBuffer.capacity());
    final DirectBuffer valueViewBuffer = context.getValueView();
    valueInstance.wrap(valueViewBuffer, 0, valueViewBuffer.capacity());

    return iteratorConsumer.visit(keyInstance, valueInstance);
  }

  public boolean isEmpty(long columnFamilyHandle, DbContext context) {
    final AtomicBoolean isEmpty = new AtomicBoolean(false);
    ensureInOpenTransaction(
        context,
        transaction -> {
          try (RocksIterator iterator =
              newIterator(columnFamilyHandle, context, defaultReadOptions)) {
            iterator.seekToFirst();
            final boolean hasEntry = iterator.isValid();
            isEmpty.set(!hasEntry);
          }
        });
    return isEmpty.get();
  }

  @Override
  public void createSnapshot(File snapshotDir) {
    try (Checkpoint checkpoint = Checkpoint.create(optimisticTransactionDB)) {
      try {
        checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
      } catch (RocksDBException rocksException) {
        throw new ZeebeDbException(rocksException);
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
          } catch (Exception e) {
            LOG.error(ERROR_MESSAGE_CLOSE_RESOURCE, e);
          }
        });
  }

  @FunctionalInterface
  interface TransactionConsumer {
    void run(ZeebeTransaction transaction) throws Exception;
  }
}
