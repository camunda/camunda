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
package io.zeebe.db.impl.rocksdb;

import static io.zeebe.util.buffer.BufferUtil.startsWith;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import io.zeebe.db.ZeebeDb;
import java.io.File;
import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.Checkpoint;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.DBOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;
import org.rocksdb.WriteOptions;

class ZeebeRocksDb<ColumnFamilyNames extends Enum<ColumnFamilyNames>> extends RocksDB
    implements ZeebeDb<ColumnFamilyNames> {

  private static final Field NATIVE_HANDLE_FIELD;

  static {
    RocksDB.loadLibrary();

    try {
      NATIVE_HANDLE_FIELD = RocksObject.class.getDeclaredField("nativeHandle_");
      NATIVE_HANDLE_FIELD.setAccessible(true);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean activePrefixIteration;

  public static <ColumnFamilyNames extends Enum<ColumnFamilyNames>>
      ZeebeRocksDb<ColumnFamilyNames> openZbDb(
          final DBOptions options,
          final String path,
          final List<ColumnFamilyDescriptor> columnFamilyDescriptors,
          final List<AutoCloseable> closables,
          Class<ColumnFamilyNames> columnFamilyTypeClass)
          throws RocksDBException {
    final EnumMap<ColumnFamilyNames, Long> columnFamilyMap = new EnumMap<>(columnFamilyTypeClass);

    final byte[][] cfNames = new byte[columnFamilyDescriptors.size()][];
    final long[] cfOptionHandles = new long[columnFamilyDescriptors.size()];
    for (int i = 0; i < columnFamilyDescriptors.size(); i++) {
      final ColumnFamilyDescriptor cfDescriptor = columnFamilyDescriptors.get(i);
      cfNames[i] = cfDescriptor.getName();
      cfOptionHandles[i] = getNativeHandle(cfDescriptor.getOptions());
    }

    final long[] handles = open(getNativeHandle(options), path, cfNames, cfOptionHandles);

    final ColumnFamilyNames[] enumConstants = columnFamilyTypeClass.getEnumConstants();
    for (int i = 1; i < handles.length; i++) {
      columnFamilyMap.put(enumConstants[i - 1], handles[i]);
    }

    final ZeebeRocksDb<ColumnFamilyNames> db =
        new ZeebeRocksDb<ColumnFamilyNames>(
            handles[0], columnFamilyMap, closables, columnFamilyTypeClass);
    db.storeOptionsInstance(options);

    return db;
  }

  private static long getNativeHandle(final RocksObject object) {
    try {
      return (long) NATIVE_HANDLE_FIELD.get(object);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(
          "Unexpected error occurred trying to access private nativeHandle_ field", e);
    }
  }

  private final List<AutoCloseable> closables;
  private final Class<ColumnFamilyNames> columnFamilyNamesClass;
  private RocksDbBatch batch;

  // we can also simply use one buffer
  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final DirectBuffer keyViewBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer valueViewBuffer = new UnsafeBuffer(0, 0);

  private final ExpandableArrayBuffer prefixKeyBuffer = new ExpandableArrayBuffer();

  // buffers used inside the batch
  private final ExpandableArrayBuffer keyBatchBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBatchBuffer = new ExpandableArrayBuffer();

  private final EnumMap<ColumnFamilyNames, Long> columnFamilyMap;

  /**
   * The Rocks DB {@link #keyMayExist(byte[], StringBuilder)} and others need a string build for
   * what ever reason.
   */
  private final StringBuilder keyMayExistStringBuilder = new StringBuilder();

  protected ZeebeRocksDb(
      long l,
      EnumMap<ColumnFamilyNames, Long> columnFamilyMap,
      List<AutoCloseable> closables,
      Class<ColumnFamilyNames> columnFamilyNamesClass) {
    super(l);
    this.columnFamilyMap = columnFamilyMap;
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
    return new RocksDbColumnFamily<>(this, columnFamily, keyInstance, valueInstance);
  }

  protected void put(long columnFamilyHandle, DbKey key, DbValue value) {
    key.write(keyBuffer, 0);
    value.write(valueBuffer, 0);

    try {
      if (isInBatch()) {
        batch.put(columnFamilyHandle, key, value);
      } else {
        put(
            nativeHandle_,
            keyBuffer.byteArray(),
            0,
            key.getLength(),
            valueBuffer.byteArray(),
            0,
            value.getLength(),
            columnFamilyHandle);
      }
    } catch (Exception e) {
      throw new RuntimeException("Unexpected error occurred trying to write RocksDB entry", e);
    }
  }

  private boolean isInBatch() {
    return batch != null;
  }

  @Override
  public void batch(Runnable operations) {
    try (WriteOptions options = new WriteOptions()) {
      batch = new RocksDbBatch(keyBatchBuffer, valueBatchBuffer);

      operations.run();
      write(options, batch);
    } catch (RocksDBException e) {
      throw new RuntimeException("Unexpected error occurred during RocksDB batch operation", e);
    } finally {
      if (batch != null) {
        batch.close();
        batch = null;
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
    final int valueLength = valueBuffer.capacity();
    try {
      final int readBytes =
          get(
              nativeHandle_,
              keyBuffer.byteArray(),
              0,
              keyLength,
              valueBuffer.byteArray(),
              0,
              valueLength,
              columnFamilyHandle);

      if (readBytes >= valueLength) {
        valueBuffer.checkLimit(readBytes);
        return getValue(columnFamilyHandle, keyLength);
      } else if (readBytes <= RocksDB.NOT_FOUND) {
        return null;
      } else {
        valueViewBuffer.wrap(valueBuffer, 0, readBytes);
        return valueViewBuffer;
      }
    } catch (RocksDBException e) {
      throw new RuntimeException("Unexpected error trying to read RocksDB entry", e);
    }
  }

  protected boolean exists(long columnFamilyHandle, DbKey key) {
    key.write(keyBuffer, 0);

    if (!keyMayExist(
        nativeHandle_,
        keyBuffer.byteArray(),
        0,
        key.getLength(),
        columnFamilyHandle,
        keyMayExistStringBuilder)) {
      return false;
    }

    // to read not the value
    final int zeroLength = 0;
    try {
      final int readBytes =
          get(
              nativeHandle_,
              keyBuffer.byteArray(),
              0,
              key.getLength(),
              valueBuffer.byteArray(),
              0,
              zeroLength,
              columnFamilyHandle);

      return readBytes != NOT_FOUND;
    } catch (RocksDBException e) {
      throw new RuntimeException("Unexpected error occurred trying to read RocksDB entry", e);
    }
  }

  protected void delete(long columnFamilyHandle, DbKey key) {
    key.write(keyBuffer, 0);

    try {
      if (isInBatch()) {
        batch.delete(columnFamilyHandle, key);
      } else {
        delete(nativeHandle_, keyBuffer.byteArray(), 0, key.getLength(), columnFamilyHandle);
      }
    } catch (RocksDBException rdbE) {
      throw new RuntimeException(rdbE);
    }
  }

  ////////////////////////////////////////////////////////////////////
  //////////////////////////// ITERATION /////////////////////////////
  ////////////////////////////////////////////////////////////////////

  public RocksDbIterator newIterator(long columnFamilyHandle) {
    return new RocksDbIterator(this, iteratorCF(nativeHandle_, columnFamilyHandle));
  }

  public RocksDbIterator newIterator(long columnFamilyHandle, RocksDbReadOptions options) {
    return new RocksDbIterator(
        this, iteratorCF(nativeHandle_, columnFamilyHandle, options.getNativeHandle()));
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
    try (RocksDbIterator iterator = newIterator(columnFamilyHandle)) {
      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        keyViewBuffer.wrap(iterator.key());
        valueViewBuffer.wrap(iterator.value());
        keyValuePairConsumer.accept(keyViewBuffer, valueViewBuffer);
      }
    }
  }

  public <KeyType extends DbKey, ValueType extends DbValue> void whileTrue(
      long columnFamilyHandle,
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> visitor) {

    try (RocksDbIterator iterator = newIterator(columnFamilyHandle)) {
      boolean shouldVisitNext = true;
      for (iterator.seekToFirst(); iterator.isValid() && shouldVisitNext; iterator.next()) {
        shouldVisitNext = visit(keyInstance, valueInstance, visitor, iterator);
      }
    }
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
    if (activePrefixIteration) {
      throw new IllegalStateException(
          "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
    }

    activePrefixIteration = true;
    try (RocksDbReadOptions options =
            new RocksDbReadOptions().setPrefixSameAsStart(true).setTotalOrderSeek(false);
        RocksDbIterator iterator = newIterator(columnFamilyHandle, options)) {
      prefix.write(prefixKeyBuffer, 0);
      final int prefixLength = prefix.getLength();

      boolean shouldVisitNext = true;
      for (iterator.seek(prefixKeyBuffer.byteArray(), prefixLength);
          iterator.isValid() && shouldVisitNext;
          iterator.next()) {
        final byte[] keyBytes = iterator.key();
        if (startsWith(
            prefixKeyBuffer.byteArray(), 0, prefix.getLength(), keyBytes, 0, keyBytes.length)) {
          shouldVisitNext = visit(keyInstance, valueInstance, visitor, iterator);
        }
      }
    } finally {
      activePrefixIteration = false;
    }
  }

  private <KeyType extends DbKey, ValueType extends DbValue> boolean visit(
      KeyType keyInstance,
      ValueType valueInstance,
      KeyValuePairVisitor<KeyType, ValueType> iteratorConsumer,
      RocksDbIterator iterator) {
    keyViewBuffer.wrap(iterator.key());
    valueViewBuffer.wrap(iterator.value());

    keyInstance.wrap(keyViewBuffer, 0, keyViewBuffer.capacity());
    valueInstance.wrap(valueViewBuffer, 0, valueViewBuffer.capacity());

    return iteratorConsumer.visit(keyInstance, valueInstance);
  }

  public boolean isEmpty(long columnFamilyHandle) {
    try (RocksDbIterator iterator = newIterator(columnFamilyHandle)) {
      iterator.seekToFirst();
      final boolean hasEntry = iterator.isValid();

      return !hasEntry;
    }
  }

  @Override
  public void createSnapshot(File snapshotDir) {
    try (Checkpoint checkpoint = Checkpoint.create(this)) {
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

    super.close();
  }
}
