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
package io.zeebe.logstreams.state;

import io.zeebe.logstreams.impl.Loggers;
import io.zeebe.util.ByteValue;
import io.zeebe.util.LangUtil;
import io.zeebe.util.buffer.BufferWriter;
import java.io.File;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.agrona.CloseHelper;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Cache;
import org.rocksdb.ChecksumType;
import org.rocksdb.ClockCache;
import org.rocksdb.ColumnFamilyHandle;
import org.rocksdb.Env;
import org.rocksdb.Filter;
import org.rocksdb.MemTableConfig;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.SkipListMemTableConfig;
import org.rocksdb.TableFormatConfig;
import org.slf4j.Logger;

/**
 * Controls opening, closing, and managing of RocksDB associated resources. Could be argued that db
 * reference should not be made transparent, as it could be closed on its own elsewhere, but for now
 * it's easier.
 *
 * <p>Current suggested method of customizing RocksDB instance per stream processor is to subclass
 * this class and to override the protected methods to your liking.
 *
 * <p>Another option would be to use a Builder class and make StateController entirely controlled
 * through its properties.
 */
public class StateController implements AutoCloseable {
  private static final Logger LOG = Loggers.ROCKSDB_LOGGER;

  private final MutableDirectBuffer dbLongBuffer = new UnsafeBuffer(new byte[Long.BYTES]);
  private boolean isOpened = false;
  private RocksDB db;
  protected File dbDirectory;
  protected List<AutoCloseable> closeables = new ArrayList<>();

  private long nativeHandle_;

  static {
    RocksDB.loadLibrary();
  }

  public RocksDB open(final File dbDirectory, final boolean reopen) throws Exception {
    if (!isOpened) {
      try {
        this.dbDirectory = dbDirectory;
        final Options options =
            createOptions().setErrorIfExists(!reopen).setCreateIfMissing(!reopen);
        closeables.add(options);
        db = openDB(options);
        isOpened = true;

        collectInternalMethods();
      } catch (final RocksDBException ex) {
        close();
        throw ex;
      }

      LOG.trace("Opened RocksDB {}", this.dbDirectory);
    }

    return db;
  }

  private void collectInternalMethods() throws Exception {
    this.nativeHandle_ = (long) RocksDbInternal.rocksDbNativeHandle.get(db);
  }

  protected RocksDB openDB(final Options options) throws RocksDBException {
    return RocksDB.open(options, dbDirectory.getAbsolutePath());
  }

  protected Options createOptions() {
    final Filter filter = new BloomFilter();
    closeables.add(filter);

    final Cache cache = new ClockCache(ByteValue.ofMegabytes(16).toBytes(), 10);
    closeables.add(cache);

    final TableFormatConfig sstTableConfig =
        new BlockBasedTableConfig()
            .setBlockCache(cache)
            .setBlockSize(ByteValue.ofKilobytes(16).toBytes())
            .setChecksumType(ChecksumType.kCRC32c)
            .setFilter(filter);
    final MemTableConfig memTableConfig = new SkipListMemTableConfig();

    return new Options()
        .setEnv(getDbEnv())
        .setWriteBufferSize(ByteValue.ofMegabytes(64).toBytes())
        .setMemTableConfig(memTableConfig)
        .setTableFormatConfig(sstTableConfig);
  }

  protected Env getDbEnv() {
    return Env.getDefault();
  }

  protected void ensureIsOpened(final String operation) {
    if (!isOpened()) {
      final String message =
          String.format("%s cannot be executed unless database is opened", operation);
      throw new IllegalStateException(message);
    }
  }

  public boolean isOpened() {
    return isOpened;
  }

  public void delete() throws Exception {
    delete(dbDirectory);
  }

  public void delete(final File dbDirectory) throws Exception {
    if (isOpened && this.dbDirectory == dbDirectory) {
      close();
    }

    try (final Options options = createOptions()) {
      RocksDB.destroyDB(dbDirectory.toString(), options);
    } finally {
      closeables.forEach(CloseHelper::quietClose);
    }
  }

  @Override
  public void close() {
    if (db != null) {
      db.close();
      db = null;
    }

    closeables.forEach(CloseHelper::quietClose);
    closeables.clear();

    LOG.trace("Closed RocksDB {}", dbDirectory);
    dbDirectory = null;
    isOpened = false;
  }

  public RocksDB getDb() {
    return db;
  }

  private void setKey(final long key) {
    dbLongBuffer.putLong(0, key, ByteOrder.LITTLE_ENDIAN);
  }

  public void put(final long key, final byte[] valueBuffer) {
    setKey(key);
    try {
      db.put(dbLongBuffer.byteArray(), valueBuffer);
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void put(
      final ColumnFamilyHandle handle,
      final long key,
      final byte[] value,
      final int valueOffset,
      final int valueLength) {
    setKey(key);
    put(
        handle,
        dbLongBuffer.byteArray(),
        0,
        dbLongBuffer.capacity(),
        value,
        valueOffset,
        valueLength);
  }

  public void put(
      final ColumnFamilyHandle handle,
      final byte[] key,
      final int keyOffset,
      final int keyLength,
      final byte[] value,
      final int valueOffset,
      final int valueLength) {
    try {
      final long columnFamilyHandle = (long) RocksDbInternal.columnFamilyHandle.get(handle);
      RocksDbInternal.putWithHandle.invoke(
          db,
          nativeHandle_,
          key,
          keyOffset,
          keyLength,
          value,
          valueOffset,
          valueLength,
          columnFamilyHandle);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void put(
      final long key, final byte[] value, final int valueOffset, final int valueLength) {
    setKey(key);
    put(dbLongBuffer.byteArray(), 0, dbLongBuffer.capacity(), value, valueOffset, valueLength);
  }

  public void put(
      final byte[] key,
      final int keyOffset,
      final int keyLength,
      final byte[] value,
      final int valueOffset,
      final int valueLength) {
    try {
      RocksDbInternal.putMethod.invoke(
          db, nativeHandle_, key, keyOffset, keyLength, value, valueOffset, valueLength);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * *creates garbage*
   *
   * @param key
   * @param valueWriter
   */
  public void put(final long key, final BufferWriter valueWriter) {
    setKey(key);
    final int length = valueWriter.getLength();
    final byte[] bytes = new byte[length];
    final UnsafeBuffer buffer = new UnsafeBuffer(bytes);

    valueWriter.write(buffer, 0);

    try {
      db.put(dbLongBuffer.byteArray(), bytes);
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void put(final byte[] key, final byte[] valueBuffer) {
    try {
      db.put(key, valueBuffer);
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void delete(final long key) {
    setKey(key);
    try {
      db.delete(dbLongBuffer.byteArray());
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public void delete(final byte[] key) {
    try {
      db.delete(key);
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }
  }

  public int get(
      final byte[] key,
      final int keyOffset,
      final int keyLength,
      final byte[] value,
      final int valueOffset,
      final int valueLength) {
    try {
      return (int)
          RocksDbInternal.getMethod.invoke(
              db, nativeHandle_, key, keyOffset, keyLength, value, valueOffset, valueLength);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public int get(
      final ColumnFamilyHandle columnFamilyHandle,
      final byte[] key,
      final int keyOffset,
      final int keyLength,
      final byte[] value,
      final int valueOffset,
      final int valueLength) {
    try {
      final long nativeHandle = (long) RocksDbInternal.columnFamilyHandle.get(columnFamilyHandle);
      return (int)
          RocksDbInternal.getWithHandle.invoke(
              db,
              nativeHandle_,
              key,
              keyOffset,
              keyLength,
              value,
              valueOffset,
              valueLength,
              nativeHandle);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  /**
   * !creates garbage!
   *
   * @param key
   * @return
   */
  public byte[] get(final long key) {
    setKey(key);

    byte[] bytes = null;
    try {
      bytes = getDb().get(dbLongBuffer.byteArray());
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }

    return bytes;
  }

  public boolean tryGet(final long key, final byte[] valueBuffer) {
    setKey(key);

    return tryGet(dbLongBuffer.byteArray(), valueBuffer);
  }

  public boolean tryGet(final byte[] keyBuffer, final byte[] valueBuffer) {
    boolean found = false;

    try {
      final int bytesRead = getDb().get(keyBuffer, valueBuffer);
      found = bytesRead == valueBuffer.length;
    } catch (final RocksDBException e) {
      LangUtil.rethrowUnchecked(e);
    }

    return found;
  }

  public boolean exist(
      final ColumnFamilyHandle handle, final byte[] key, final int offset, final int length) {
    try {
      final long nativeHandle = (long) RocksDbInternal.columnFamilyHandle.get(handle);
      final int readBytes =
          (int)
              RocksDbInternal.getWithHandle.invoke(
                  db,
                  nativeHandle_,
                  key,
                  offset,
                  length,
                  dbLongBuffer.byteArray(),
                  0,
                  dbLongBuffer.capacity(),
                  nativeHandle);
      return readBytes > 0;
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void remove(final byte[] key, final int offset, final int length) {
    try {
      RocksDbInternal.removeMethod.invoke(db, nativeHandle_, key, offset, length);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void remove(
      final ColumnFamilyHandle handle, final byte[] key, final int offset, final int length) {
    try {
      final long nativeHandle = (long) RocksDbInternal.columnFamilyHandle.get(handle);
      RocksDbInternal.removeWithHandle.invoke(db, nativeHandle_, key, offset, length, nativeHandle);
    } catch (final Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  public void foreach(
      final ColumnFamilyHandle handle, final BiConsumer<byte[], byte[]> keyValueConsumer) {
    try (final RocksIterator rocksIterator = getDb().newIterator(handle)) {
      rocksIterator.seekToFirst();
      while (rocksIterator.isValid()) {
        keyValueConsumer.accept(rocksIterator.key(), rocksIterator.value());
        rocksIterator.next();
      }
    }
  }

  public void foreach(final byte[] startAt, final BiConsumer<byte[], byte[]> keyValueConsumer) {
    try (final RocksIterator rocksIterator = getDb().newIterator()) {
      rocksIterator.seek(startAt);
      while (rocksIterator.isValid()) {
        keyValueConsumer.accept(rocksIterator.key(), rocksIterator.value());
        rocksIterator.next();
      }
    }
  }

  public void foreach(final BiConsumer<byte[], byte[]> keyValueConsumer) {
    try (final RocksIterator rocksIterator = getDb().newIterator()) {
      rocksIterator.seekToFirst();
      while (rocksIterator.isValid()) {
        keyValueConsumer.accept(rocksIterator.key(), rocksIterator.value());
        rocksIterator.next();
      }
    }
  }
}
