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
package io.zeebe.logstreams.rocksdb;

import static io.zeebe.logstreams.rocksdb.ZeebeStateConstants.STATE_BYTE_ORDER;
import static io.zeebe.util.buffer.BufferUtil.startsWith;

import io.zeebe.util.EnsureUtil;
import io.zeebe.util.buffer.BufferUtil;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.DBOptions;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksObject;

/**
 * Extends {@link RocksDB} base class to expose a few protected methods, cuts down on the need for
 * reflection since all we need is the initial pointer to the wrapped database.
 *
 * <p>Add methods as you require them.
 */
public class ZbRocksDb extends RocksDB {
  private static final Field NATIVE_HANDLE_FIELD;
  private static final Constructor<ColumnFamilyHandle> COLUMN_FAMILY_HANDLE_CONSTRUCTOR;

  private final MutableDirectBuffer existsBuffer = new UnsafeBuffer(new byte[1]);
  private final MutableDirectBuffer longKeyBuffer = new UnsafeBuffer(new byte[Long.BYTES]);

  static {
    RocksDB.loadLibrary();

    try {
      NATIVE_HANDLE_FIELD = RocksObject.class.getDeclaredField("nativeHandle_");
      NATIVE_HANDLE_FIELD.setAccessible(true);
      COLUMN_FAMILY_HANDLE_CONSTRUCTOR =
          ColumnFamilyHandle.class.getDeclaredConstructor(RocksDB.class, long.class);
      COLUMN_FAMILY_HANDLE_CONSTRUCTOR.setAccessible(true);
    } catch (NoSuchFieldException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  protected ZbRocksDb(long nativeHandle) {
    super(nativeHandle);
  }

  public static ZbRocksDb open(final Options options, final String path) throws RocksDBException {
    final long nativeHandle = RocksDB.open(getNativeHandle(options), path);
    final ZbRocksDb db = new ZbRocksDb(nativeHandle);
    db.storeOptionsInstance(options);

    return db;
  }

  public static ZbRocksDb open(
      final DBOptions options,
      final String path,
      final List<ColumnFamilyDescriptor> columnFamilyDescriptors,
      final List<ColumnFamilyHandle> columnFamilyHandles)
      throws RocksDBException {

    final byte[][] cfNames = new byte[columnFamilyDescriptors.size()][];
    final long[] cfOptionHandles = new long[columnFamilyDescriptors.size()];
    for (int i = 0; i < columnFamilyDescriptors.size(); i++) {
      final ColumnFamilyDescriptor cfDescriptor = columnFamilyDescriptors.get(i);
      cfNames[i] = cfDescriptor.getName();
      cfOptionHandles[i] = getNativeHandle(cfDescriptor.getOptions());
    }

    final long[] handles = open(getNativeHandle(options), path, cfNames, cfOptionHandles);
    final ZbRocksDb db = new ZbRocksDb(handles[0]);
    db.storeOptionsInstance(options);

    for (int i = 1; i < handles.length; i++) {
      try {
        columnFamilyHandles.add(COLUMN_FAMILY_HANDLE_CONSTRUCTOR.newInstance(db, handles[i]));
      } catch (InstantiationException | InvocationTargetException | IllegalAccessException e) {
        throw new RuntimeException(e);
      }
    }

    return db;
  }

  public void put(
      ColumnFamilyHandle columnFamily,
      byte[] key,
      int keyOffset,
      int keyLength,
      byte[] value,
      int valueOffset,
      int valueLength) {
    try {
      super.put(
          nativeHandle_,
          key,
          keyOffset,
          keyLength,
          value,
          valueOffset,
          valueLength,
          getNativeHandle(columnFamily));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void put(ColumnFamilyHandle columnFamily, DirectBuffer key, DirectBuffer value) {
    EnsureUtil.ensureArrayBacked(key, value);
    put(
        columnFamily,
        key.byteArray(),
        key.wrapAdjustment(),
        key.capacity(),
        value.byteArray(),
        value.wrapAdjustment(),
        value.capacity());
  }

  public int get(
      ColumnFamilyHandle columnFamily,
      byte[] key,
      int keyOffset,
      int keyLength,
      byte[] value,
      int valueOffset,
      int valueLength) {
    try {
      return super.get(
          nativeHandle_,
          key,
          keyOffset,
          keyLength,
          value,
          valueOffset,
          valueLength,
          getNativeHandle(columnFamily));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public int get(ColumnFamilyHandle columnFamily, DirectBuffer key, MutableDirectBuffer value) {
    EnsureUtil.ensureArrayBacked(key, value);
    int bytesRead =
        get(
            columnFamily,
            key.byteArray(),
            key.wrapAdjustment(),
            key.capacity(),
            value.byteArray(),
            value.wrapAdjustment(),
            value.capacity());

    if (value.isExpandable() && bytesRead > value.capacity()) {
      value.checkLimit(bytesRead);
      bytesRead = get(columnFamily, key, value);
    }

    return bytesRead;
  }

  public int get(ColumnFamilyHandle columnFamily, long key, MutableDirectBuffer value) {
    setKey(key);
    return get(columnFamily, longKeyBuffer, value);
  }

  public void delete(ColumnFamilyHandle columnFamily, byte[] key, int keyOffset, int keyLength) {
    try {
      super.delete(nativeHandle_, key, keyOffset, keyLength, getNativeHandle(columnFamily));
    } catch (RocksDBException e) {
      throw new RuntimeException(e);
    }
  }

  public void delete(ColumnFamilyHandle columnFamily, DirectBuffer key) {
    EnsureUtil.ensureArrayBacked(key);
    delete(columnFamily, key.byteArray(), key.wrapAdjustment(), key.capacity());
  }

  public boolean exists(ColumnFamilyHandle columnFamily, byte[] key, int keyOffset, int keyLength) {
    return exists(columnFamily, new UnsafeBuffer(key, keyOffset, keyLength), existsBuffer);
  }

  public boolean exists(ColumnFamilyHandle columnFamily, DirectBuffer key) {
    EnsureUtil.ensureArrayBacked(key);
    return exists(columnFamily, key.byteArray(), key.wrapAdjustment(), key.capacity());
  }

  public boolean exists(ColumnFamilyHandle columnFamily, long key) {
    setKey(key);
    return exists(columnFamily, longKeyBuffer, existsBuffer);
  }

  /**
   * Allows doing an exist call and getting the value back (since it has to be read anyway) if it
   * does.
   *
   * <p>NOTE: creates garbage by allocating a new string builder, which will then allocate a new
   * string before filling the value buffer.
   *
   * @param columnFamily column family to look into
   * @param key the key to look for
   * @param value a buffer which will be filled with the value if it exists
   * @return true or false
   */
  public boolean exists(
      ColumnFamilyHandle columnFamily, DirectBuffer key, MutableDirectBuffer value) {
    EnsureUtil.ensureArrayBacked(key, value);

    // todo(npepinpe): afaik keyMayExist doesn't necessarily fill the StringBuilder completely, so
    // it doesn't seem particularly useful
    final StringBuilder builder = new StringBuilder();

    if (!keyMayExist(
        nativeHandle_,
        key.byteArray(),
        key.wrapAdjustment(),
        key.capacity(),
        getNativeHandle(columnFamily),
        builder)) {
      return false;
    }

    return get(columnFamily, key, value) != RocksDB.NOT_FOUND;
  }

  /**
   * NOTE: it doesn't seem possible in Java RocksDB to set a flexible prefix extractor on iterators
   * at the moment, so using prefixes seem to be mostly related to skipping files that do not
   * contain keys with the given prefix (which is useful anyway), but it will still iterate over all
   * keys contained in those files, so we still need to make sure the key actually matches the
   * prefix. Or I misunderstood how to do it, which is equally as likely.
   *
   * <p>My understanding is seek(prefix) simply positions the cursor to the first key (allowing to
   * skip previous keys), but while iterating over subsequent keys we have to validate it.
   */
  public void forEachPrefixed(
      ColumnFamilyHandle columnFamily, DirectBuffer prefix, IteratorCallback callback) {
    try (ReadOptions options =
            new ReadOptions().setPrefixSameAsStart(true).setTotalOrderSeek(false);
        ZbRocksIterator iterator = newIterator(columnFamily, options)) {
      final IteratorControl control = new IteratorControl();
      // clone buffer to not interfere when keyBuffer is reused inside callback
      prefix = BufferUtil.cloneBuffer(prefix);

      for (iterator.seek(prefix); iterator.isValid(); iterator.next()) {
        if (startsWith(iterator.keyBuffer(), prefix)) {
          final ZbRocksEntry entry = new ZbRocksEntry(iterator.keyBuffer(), iterator.valueBuffer());
          callback.accept(entry, control);

          if (control.shouldStop()) {
            break;
          }
        }
      }
    }
  }

  public void forEach(ColumnFamilyHandle columnFamily, IteratorCallback callback) {
    try (ReadOptions options = new ReadOptions().setTotalOrderSeek(true);
        ZbRocksIterator iterator = newIterator(columnFamily, options)) {
      final IteratorControl control = new IteratorControl();

      for (iterator.seekToFirst(); iterator.isValid(); iterator.next()) {
        final ZbRocksEntry entry = new ZbRocksEntry(iterator.keyBuffer(), iterator.valueBuffer());
        callback.accept(entry, control);

        if (control.shouldStop()) {
          break;
        }
      }
    }
  }

  /**
   * NOTE: not recommended for production use, since it loads all key/values into memory, but it's
   * very useful for debugging.
   *
   * @param columnFamily the column family to list all entries for
   * @return a list of all entries in the database
   */
  public List<ZbRocksEntry> list(ColumnFamilyHandle columnFamily) {
    final List<ZbRocksEntry> entries = new ArrayList<>();
    forEach(columnFamily, (e, c) -> entries.add(e));
    return entries;
  }

  @Override
  public ZbRocksIterator newIterator() {
    return new ZbRocksIterator(this, iterator(nativeHandle_));
  }

  @Override
  public ZbRocksIterator newIterator(ColumnFamilyHandle columnFamily) {
    return new ZbRocksIterator(this, iteratorCF(nativeHandle_, getNativeHandle(columnFamily)));
  }

  @Override
  public ZbRocksIterator newIterator(ColumnFamilyHandle columnFamily, ReadOptions readOptions) {
    return new ZbRocksIterator(
        this,
        iteratorCF(nativeHandle_, getNativeHandle(columnFamily), getNativeHandle(readOptions)));
  }

  private void setKey(final long key) {
    longKeyBuffer.putLong(0, key, STATE_BYTE_ORDER);
  }

  static long getNativeHandle(final RocksObject object) {
    try {
      return (long) NATIVE_HANDLE_FIELD.get(object);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  @FunctionalInterface
  public interface IteratorCallback extends BiConsumer<ZbRocksEntry, IteratorControl> {}

  public static class IteratorControl {
    boolean stop = false;

    public void stop() {
      stop = true;
    }

    public boolean shouldStop() {
      return stop;
    }
  }
}
