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
import static org.rocksdb.Status.Code.Aborted;
import static org.rocksdb.Status.Code.Busy;
import static org.rocksdb.Status.Code.Expired;
import static org.rocksdb.Status.Code.IOError;
import static org.rocksdb.Status.Code.MergeInProgress;
import static org.rocksdb.Status.Code.Ok;
import static org.rocksdb.Status.Code.TimedOut;
import static org.rocksdb.Status.Code.TryAgain;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.ZeebeDbException;
import io.zeebe.db.impl.rocksdb.DbContext;
import io.zeebe.db.impl.rocksdb.DbContext.BufferSupplier;
import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.Long2ObjectHashMap;
import org.rocksdb.Checkpoint;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.RocksObject;
import org.rocksdb.Status.Code;
import org.rocksdb.Transaction;
import org.rocksdb.WriteOptions;

public class ZeebeTransactionDb<ColumnFamilyNames extends Enum<ColumnFamilyNames>>
    implements ZeebeDb<ColumnFamilyNames> {

  public static final EnumSet<Code> RECOVERABLE_ERROR_CODES =
      EnumSet.of(Ok, Aborted, Expired, IOError, Busy, TimedOut, TryAgain, MergeInProgress);

  public static final byte[] ZERO_SIZE_ARRAY = new byte[0];
  public static final String NESTED_PREFIX_ITERATION_ERROR =
      "Currently nested prefix iterations are not supported! This will cause unexpected behavior.";

  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeTransactionDb<ColumnFamilyNames> openTransactionalDb(
          final DBOptions options,
          final String path,
          final List<ColumnFamilyDescriptor> columnFamilyDescriptors,
          final List<AutoCloseable> closeables,
          Class<ColumnFamilyNames> columnFamilyTypeClass) {
    final EnumMap<ColumnFamilyNames, Long> columnFamilyMap = new EnumMap<>(columnFamilyTypeClass);

    final List<ColumnFamilyHandle> handles = new ArrayList<>();
    final DbReference dbReference =
        RocksDbRegistry.open(options, path, columnFamilyDescriptors, handles);

    final ColumnFamilyNames[] enumConstants = columnFamilyTypeClass.getEnumConstants();
    final Long2ObjectHashMap<ColumnFamilyHandle> handleToEnumMap = new Long2ObjectHashMap();
    for (int i = 0; i < handles.size(); i++) {
      columnFamilyMap.put(enumConstants[i], getNativeHandle(handles.get(i)));
      handleToEnumMap.put(getNativeHandle(handles.get(i)), handles.get(i));
    }
    final ZeebeTransactionDb<ColumnFamilyNames> db =
        new ZeebeTransactionDb<>(dbReference, columnFamilyMap, handleToEnumMap, closeables);
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

  private static final String ERROR_MESSAGE =
      "Unexpected error occurred during RocksDb operation '%s'";

  private final DbReference reference;
  private final List<AutoCloseable> closeables;
  private final EnumMap<ColumnFamilyNames, Long> columnFamilyMap;
  private final Long2ObjectHashMap<ColumnFamilyHandle> handleToEnumMap;

  protected ZeebeTransactionDb(
      DbReference reference,
      EnumMap<ColumnFamilyNames, Long> columnFamilyMap,
      Long2ObjectHashMap<ColumnFamilyHandle> handleToEnumMap,
      List<AutoCloseable> closeables) {
    this.reference = reference;
    this.columnFamilyMap = columnFamilyMap;
    this.handleToEnumMap = handleToEnumMap;
    this.closeables = closeables;
  }

  public Transaction getTransaction(WriteOptions options) {
    return reference.beginTransaction(options);
  }

  protected long getColumnFamilyHandle(ColumnFamilyNames columnFamily) {
    return columnFamilyMap.get(columnFamily);
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          DbContext dbContext,
          ColumnFamilyNames columnFamily,
          KeyType keyInstance,
          ValueType valueInstance) {
    return new TransactionalColumnFamily<>(
        dbContext, this, columnFamily, keyInstance, valueInstance);
  }

  protected void put(final DbContext dbContext, long columnFamilyHandle, DbKey key, DbValue value)
      throws Exception {
    final ExpandableArrayBuffer keyBuffer = dbContext.getKeyBuffer();
    final ExpandableArrayBuffer valueBuffer = dbContext.getValueBuffer();

    key.write(keyBuffer, 0);
    value.write(valueBuffer, 0);

    dbContext
        .getCurrentTransaction()
        .put(
            columnFamilyHandle,
            keyBuffer.byteArray(),
            key.getLength(),
            valueBuffer.byteArray(),
            value.getLength());
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// GET ///////////////////////////////////
  ////////////////////////////////////////////////////////////////////

  protected DirectBuffer get(
      final DbContext dbContext, final long columnFamilyHandle, final DbKey key) throws Exception {
    try (WriteOptions options = new WriteOptions()) {
      final ExpandableArrayBuffer keyBuffer = dbContext.getKeyBuffer();
      final DirectBuffer valueViewBuffer = dbContext.getValueViewBuffer();
      final ZeebeTransaction transaction = dbContext.getCurrentTransaction();

      key.write(keyBuffer, 0);
      final int keyLength = key.getLength();
      return getValue(columnFamilyHandle, transaction, keyBuffer, valueViewBuffer, keyLength);
    }
  }

  private DirectBuffer getValue(
      final long columnFamilyHandle,
      final ZeebeTransaction transaction,
      final ExpandableArrayBuffer keyBuffer,
      final DirectBuffer valueViewBuffer,
      final int keyLength)
      throws Exception {
    try (final ReadOptions readOptions = new ReadOptions()) {
      final byte[] value =
          transaction.get(
              columnFamilyHandle, getNativeHandle(readOptions), keyBuffer.byteArray(), keyLength);
      if (value != null) {
        valueViewBuffer.wrap(value);
      } else {
        valueViewBuffer.wrap(ZERO_SIZE_ARRAY);
      }
    }

    return valueViewBuffer.capacity() == ZERO_SIZE_ARRAY.length ? null : valueViewBuffer;
  }

  protected boolean exists(
      final DbContext dbContext, final long columnFamilyHandle, final DbKey key) throws Exception {
    final ZeebeTransaction transaction = dbContext.getCurrentTransaction();
    final ExpandableArrayBuffer keyBuffer = dbContext.getKeyBuffer();
    final DirectBuffer valueViewBuffer = dbContext.getValueViewBuffer();

    valueViewBuffer.wrap(new byte[0]);
    key.write(keyBuffer, 0);

    getValue(columnFamilyHandle, transaction, keyBuffer, valueViewBuffer, key.getLength());
    return valueViewBuffer.capacity() > ZERO_SIZE_ARRAY.length;
  }

  protected void delete(final DbContext dbContext, final long columnFamilyHandle, final DbKey key)
      throws Exception {
    final ZeebeTransaction transaction = dbContext.getCurrentTransaction();
    final ExpandableArrayBuffer keyBuffer = dbContext.getKeyBuffer();

    key.write(keyBuffer, 0);
    transaction.delete(columnFamilyHandle, keyBuffer.byteArray(), key.getLength());
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// ITERATION /////////////////////////////
  ////////////////////////////////////////////////////////////////////

  RocksIterator newIterator(
      final ZeebeTransaction transaction,
      final long columnFamilyHandle,
      final ReadOptions options) {
    final ColumnFamilyHandle handle = handleToEnumMap.get(columnFamilyHandle);
    return transaction.newIterator(options, handle);
  }

  public <ValueType extends DbValue> void foreach(
      DbContext dbContext,
      long columnFamilyHandle,
      ValueType iteratorValue,
      Consumer<ValueType> consumer) {
    foreach(
        dbContext,
        columnFamilyHandle,
        (keyBuffer, valueBuffer) -> {
          iteratorValue.wrap(valueBuffer, 0, valueBuffer.capacity());
          consumer.accept(iteratorValue);
        });
  }

  public <KeyType extends DbKey, ValueType extends DbValue> void foreach(
      DbContext dbContext,
      long columnFamilyHandle,
      KeyType iteratorKey,
      ValueType iteratorValue,
      BiConsumer<KeyType, ValueType> consumer) {
    foreach(
        dbContext,
        columnFamilyHandle,
        (keyBuffer, valueBuffer) -> {
          iteratorKey.wrap(keyBuffer, 0, keyBuffer.capacity());
          iteratorValue.wrap(valueBuffer, 0, valueBuffer.capacity());
          consumer.accept(iteratorKey, iteratorValue);
        });
  }

  private void foreach(
      final DbContext dbContext,
      final long columnFamilyHandle,
      final BiConsumer<DirectBuffer, DirectBuffer> keyValuePairConsumer) {
    final ZeebeTransaction transaction = dbContext.getCurrentTransaction();
    final DirectBuffer keyViewBuffer = dbContext.getKeyViewBuffer();
    final DirectBuffer valueViewBuffer = dbContext.getValueViewBuffer();

    try (ReadOptions readOptions = new ReadOptions();
        RocksIterator iterator = newIterator(transaction, columnFamilyHandle, readOptions)) {
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        keyViewBuffer.wrap(iterator.key());
        valueViewBuffer.wrap(iterator.value());
        keyValuePairConsumer.accept(keyViewBuffer, valueViewBuffer);
      }
    }
  }

  public <KeyType extends DbKey, ValueType extends DbValue> void whileTrue(
      final DbContext dbContext,
      final long columnFamilyHandle,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final ZeebeTransaction transaction = dbContext.getCurrentTransaction();
    final DirectBuffer keyViewBuffer = dbContext.getKeyViewBuffer();
    final DirectBuffer valueViewBuffer = dbContext.getValueViewBuffer();

    try (ReadOptions readOptions = new ReadOptions();
        RocksIterator iterator = newIterator(transaction, columnFamilyHandle, readOptions)) {
      boolean shouldVisitNext = true;
      for (iterator.seekToFirst(); iterator.isValid() && shouldVisitNext; iterator.next()) {
        shouldVisitNext =
            visit(keyViewBuffer, valueViewBuffer, keyInstance, valueInstance, visitor, iterator);
      }
    }
  }

  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final DbContext dbContext,
      long columnFamilyHandle,
      DbKey prefix,
      KeyType keyInstance,
      ValueType valueInstance,
      BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        dbContext,
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
      DbContext dbContext,
      long columnFamilyHandle,
      DbKey prefix,
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final DirectBuffer keyViewBuffer = dbContext.getKeyViewBuffer();
    final DirectBuffer valueViewBuffer = dbContext.getValueViewBuffer();
    final ZeebeTransaction transaction = dbContext.getCurrentTransaction();

    try (ReadOptions options =
            new ReadOptions().setPrefixSameAsStart(true).setTotalOrderSeek(false);
        RocksIterator iterator = newIterator(transaction, columnFamilyHandle, options);
        BufferSupplier bufferSupplier = dbContext.getPrefixBufferSupplier()) {
      final ExpandableArrayBuffer prefixKeyBuffer = bufferSupplier.getAvailablePrefixBuffer();

      if (prefixKeyBuffer == null) {
        throw new IllegalStateException(NESTED_PREFIX_ITERATION_ERROR);
      }

      prefix.write(prefixKeyBuffer, 0);
      final int prefixLength = prefix.getLength();

      boolean shouldVisitNext = true;

      for (RocksDbInternal.seek(
              iterator, getNativeHandle(iterator), prefixKeyBuffer.byteArray(), prefixLength);
          iterator.isValid() && shouldVisitNext;
          iterator.next()) {
        final byte[] keyBytes = iterator.key();
        if (!startsWith(
            prefixKeyBuffer.byteArray(), 0, prefix.getLength(), keyBytes, 0, keyBytes.length)) {
          break;
        }

        shouldVisitNext =
            visit(keyViewBuffer, valueViewBuffer, keyInstance, valueInstance, visitor, iterator);
      }
    }
  }

  private <KeyType extends DbKey, ValueType extends DbValue> boolean visit(
      DirectBuffer keyViewBuffer,
      DirectBuffer valueViewBuffer,
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

  public boolean isEmpty(final DbContext dbContext, final long columnFamilyHandle) {
    final ZeebeTransaction transaction = dbContext.getCurrentTransaction();
    try (ReadOptions options = new ReadOptions();
        RocksIterator iterator = newIterator(transaction, columnFamilyHandle, options)) {
      iterator.seekToFirst();
      return !iterator.isValid();
    }
  }

  @Override
  public void createSnapshot(File snapshotDir) {
    try (Checkpoint checkpoint = Checkpoint.create(reference.getTransactionDB())) {
      try {
        checkpoint.createCheckpoint(snapshotDir.getAbsolutePath());
      } catch (RocksDBException rocksException) {
        throw new ZeebeDbException(rocksException);
      }
    }
  }

  @Override
  public void close() {
    closeables.forEach(
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
