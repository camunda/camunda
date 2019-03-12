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
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import io.zeebe.db.TransactionOperation;
import io.zeebe.db.ZeebeDb;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.Checkpoint;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.RocksObject;
import org.rocksdb.WriteOptions;

public class ZeebeTransactionDb<ColumnFamilyNames extends Enum<ColumnFamilyNames>>
    implements ZeebeDb<ColumnFamilyNames> {

  public static final byte[] ZERO_SIZE_ARRAY = new byte[0];

  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeTransactionDb<ColumnFamilyNames> openTransactionalDb(
          final DBOptions options,
          final String path,
          final List<ColumnFamilyDescriptor> columnFamilyDescriptors,
          final List<AutoCloseable> closables,
          Class<ColumnFamilyNames> columnFamilyTypeClass) {
    final EnumMap<ColumnFamilyNames, Long> columnFamilyMap = new EnumMap<>(columnFamilyTypeClass);

    final List<ColumnFamilyHandle> handles = new ArrayList<>();
    final DbReference optimisticTransactionDB =
        RocksDbRegistry.open(options, path, columnFamilyDescriptors, handles);

    final ColumnFamilyNames[] enumConstants = columnFamilyTypeClass.getEnumConstants();
    final Long2ObjectHashMap<ColumnFamilyHandle> handleToEnumMap = new Long2ObjectHashMap();
    for (int i = 0; i < handles.size(); i++) {
      columnFamilyMap.put(enumConstants[i], getNativeHandle(handles.get(i)));
      handleToEnumMap.put(getNativeHandle(handles.get(i)), handles.get(i));
    }

    final ZeebeTransactionDb<ColumnFamilyNames> db =
        new ZeebeTransactionDb<>(
            optimisticTransactionDB,
            columnFamilyMap,
            handleToEnumMap,
            closables,
            columnFamilyTypeClass);

    return db;
  }

  private static long getNativeHandle(final RocksObject object) {
    try {
      return RocksDbInternal.nativeHandle.getLong(object);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Unexpected error occurred trying to access private nativeHandle_ field", e);
    }
  }

  private final DbReference reference;
  private ZeebeTransaction currentTransaction;
  private final List<AutoCloseable> closables;
  private final Class<ColumnFamilyNames> columnFamilyNamesClass;

  // we can also simply use one buffer
  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final DirectBuffer keyViewBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer valueViewBuffer = new UnsafeBuffer(0, 0);

  private int activePrefixIterations = 0;
  private final ExpandableArrayBuffer[] prefixKeyBuffers =
      new ExpandableArrayBuffer[] {new ExpandableArrayBuffer(), new ExpandableArrayBuffer()};

  private final EnumMap<ColumnFamilyNames, Long> columnFamilyMap;
  private final Long2ObjectHashMap<ColumnFamilyHandle> handelToEnumMap;

  protected ZeebeTransactionDb(
      DbReference reference,
      EnumMap<ColumnFamilyNames, Long> columnFamilyMap,
      Long2ObjectHashMap<ColumnFamilyHandle> handelToEnumMap,
      List<AutoCloseable> closables,
      Class<ColumnFamilyNames> columnFamilyNamesClass) {
    this.reference = reference;
    this.columnFamilyMap = columnFamilyMap;
    this.handelToEnumMap = handelToEnumMap;
    this.closables = closables;
    this.columnFamilyNamesClass = columnFamilyNamesClass;
  }

  protected long getColumnFamilyHandle(ColumnFamilyNames columnFamily) {
    return columnFamilyMap.get(columnFamily);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          ColumnFamilyNames columnFamily, KeyType keyInstance, ValueType valueInstance) {
    return new TransactionalColumnFamily<>(this, columnFamily, keyInstance, valueInstance);
  }

  protected void put(long columnFamilyHandle, DbKey key, DbValue value) {
    ensureInOpenTransaction(
        () -> {
          key.write(keyBuffer, 0);
          value.write(valueBuffer, 0);

          currentTransaction.put(
              columnFamilyHandle,
              keyBuffer.byteArray(),
              key.getLength(),
              valueBuffer.byteArray(),
              value.getLength());
        });
  }

  private void ensureInOpenTransaction(TransactionOperation runnable) {
    transaction(runnable);
  }

  private boolean isInCurrentTransaction() {
    return currentTransaction != null;
  }

  @Override
  public void transaction(TransactionOperation operations) {
    try {
      if (isInCurrentTransaction()) {
        operations.run();
      } else {
        runInNewTransaction(operations);
      }
    } catch (Exception ex) {
      throw new RuntimeException("Unexpected error occurred during RocksDB transaction.", ex);
    }
  }

  private void runInNewTransaction(TransactionOperation operations) throws Exception {
    try (WriteOptions options = new WriteOptions()) {
      currentTransaction = new ZeebeTransaction(reference.beginTransaction(options));

      operations.run();

      currentTransaction.commit();
    } finally {
      if (currentTransaction != null) {
        currentTransaction.close();
        currentTransaction = null;
      }
    }
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// GET ///////////////////////////////////
  ////////////////////////////////////////////////////////////////////

  protected DirectBuffer get(long columnFamilyHandle, DbKey key) {
    key.write(keyBuffer, 0);
    final int keyLength = key.getLength();
    return getValue(columnFamilyHandle, keyLength);
  }

  private DirectBuffer getValue(long columnFamilyHandle, int keyLength) {
    ensureInOpenTransaction(
        () -> {
          try (ReadOptions readOptions = new ReadOptions()) {
            final byte[] value =
                currentTransaction.get(
                    columnFamilyHandle,
                    getNativeHandle(readOptions),
                    keyBuffer.byteArray(),
                    keyLength);
            if (value != null) {
              valueViewBuffer.wrap(value);
            } else {
              valueViewBuffer.wrap(ZERO_SIZE_ARRAY);
            }
          }
        });
    return valueViewBuffer.capacity() == ZERO_SIZE_ARRAY.length ? null : valueViewBuffer;
  }

  protected boolean exists(long columnFamilyHandle, DbKey key) {
    valueViewBuffer.wrap(new byte[0]);
    ensureInOpenTransaction(
        () -> {
          key.write(keyBuffer, 0);
          getValue(columnFamilyHandle, key.getLength());
        });
    return valueViewBuffer.capacity() > ZERO_SIZE_ARRAY.length;
  }

  protected void delete(long columnFamilyHandle, DbKey key) {
    key.write(keyBuffer, 0);

    ensureInOpenTransaction(
        () ->
            currentTransaction.delete(columnFamilyHandle, keyBuffer.byteArray(), key.getLength()));
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// ITERATION /////////////////////////////
  ////////////////////////////////////////////////////////////////////

  RocksIterator newIterator(long columnFamilyHandle, ReadOptions options) {
    final ColumnFamilyHandle handle = handelToEnumMap.get(columnFamilyHandle);
    return currentTransaction.newIterator(options, handle);
  }

  public <ValueType extends DbValue> void foreach(
      long columnFamilyHandle, ValueType iteratorValue, Consumer<ValueType> consumer) {
    foreach(
        columnFamilyHandle,
        (keyBuffer, valueBuffer) -> {
          iteratorValue.wrap(valueBuffer, 0, valueBuffer.capacity());
          consumer.accept(iteratorValue);
        });
  }

  public <KeyType extends DbKey, ValueType extends DbValue> void foreach(
      long columnFamilyHandle,
      KeyType iteratorKey,
      ValueType iteratorValue,
      BiConsumer<KeyType, ValueType> consumer) {
    foreach(
        columnFamilyHandle,
        (keyBuffer, valueBuffer) -> {
          iteratorKey.wrap(keyBuffer, 0, keyBuffer.capacity());
          iteratorValue.wrap(valueBuffer, 0, valueBuffer.capacity());
          consumer.accept(iteratorKey, iteratorValue);
        });
  }

  private void foreach(
      long columnFamilyHandle, BiConsumer<DirectBuffer, DirectBuffer> keyValuePairConsumer) {
    ensureInOpenTransaction(
        () -> {
          try (ReadOptions readOptions = new ReadOptions();
              RocksIterator iterator = newIterator(columnFamilyHandle, readOptions)) {
            for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
              keyViewBuffer.wrap(iterator.key());
              valueViewBuffer.wrap(iterator.value());
              keyValuePairConsumer.accept(keyViewBuffer, valueViewBuffer);
            }
          }
        });
  }

  public <KeyType extends DbKey, ValueType extends DbValue> void whileTrue(
      long columnFamilyHandle,
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> visitor) {
    ensureInOpenTransaction(
        () -> {
          try (ReadOptions readOptions = new ReadOptions();
              RocksIterator iterator = newIterator(columnFamilyHandle, readOptions)) {
            boolean shouldVisitNext = true;
            for (iterator.seekToFirst(); iterator.isValid() && shouldVisitNext; iterator.next()) {
              shouldVisitNext = visit(keyInstance, valueInstance, visitor, iterator);
            }
          }
        });
  }

  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      long columnFamilyHandle,
      DbKey prefix,
      KeyType keyInstance,
      ValueType valueInstance,
      BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        columnFamilyHandle,
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
      DbKey prefix,
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> visitor) {
    if (activePrefixIterations + 1 > prefixKeyBuffers.length) {
      throw new IllegalStateException(
          "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
    }

    ensureInOpenTransaction(
        () -> {
          activePrefixIterations++;
          final ExpandableArrayBuffer prefixKeyBuffer =
              prefixKeyBuffers[activePrefixIterations - 1];
          try (ReadOptions options =
                  new ReadOptions().setPrefixSameAsStart(true).setTotalOrderSeek(false);
              RocksIterator iterator = newIterator(columnFamilyHandle, options)) {
            prefix.write(prefixKeyBuffer, 0);
            final int prefixLength = prefix.getLength();

            boolean shouldVisitNext = true;

            for (RocksDbInternal.seek(
                    iterator, getNativeHandle(iterator), prefixKeyBuffer.byteArray(), prefixLength);
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

              shouldVisitNext = visit(keyInstance, valueInstance, visitor, iterator);
            }
          } finally {
            activePrefixIterations--;
          }
        });
  }

  private <KeyType extends DbKey, ValueType extends DbValue> boolean visit(
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> iteratorConsumer,
      RocksIterator iterator) {
    keyViewBuffer.wrap(iterator.key());
    valueViewBuffer.wrap(iterator.value());

    keyInstance.wrap(keyViewBuffer, 0, keyViewBuffer.capacity());
    valueInstance.wrap(valueViewBuffer, 0, valueViewBuffer.capacity());

    return iteratorConsumer.visit(keyInstance, valueInstance);
  }

  public boolean isEmpty(long columnFamilyHandle) {
    final AtomicBoolean isEmpty = new AtomicBoolean(false);
    ensureInOpenTransaction(
        () -> {
          try (ReadOptions options = new ReadOptions();
              RocksIterator iterator = newIterator(columnFamilyHandle, options)) {
            iterator.seekToFirst();
            final boolean hasEntry = iterator.isValid();
            isEmpty.set(!hasEntry);
            return;
          }
        });
    return isEmpty.get();
  }

  @Override
  public void createSnapshot(File snapshotDir) {
    try (Checkpoint checkpoint = Checkpoint.create(reference.getTransactionDB())) {
      try {
        checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
      } catch (RocksDBException rocksException) {
        throw new RuntimeException(rocksException);
      }
    }
  }

  @Override
  public void close() {
    closables.forEach(
        closable -> {
          try {
            closable.close();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });

    reference.close();
  }
}
