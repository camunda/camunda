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
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.Cache;
import org.rocksdb.ChecksumType;
import org.rocksdb.ClockCache;
import org.rocksdb.Env;
import org.rocksdb.Filter;
import org.rocksdb.MemTableConfig;
import org.rocksdb.Options;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
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

  private boolean isOpened = false;
  private RocksDB db;
  protected File dbDirectory;
  protected List<AutoCloseable> closeables = new ArrayList<>();

  static {
    RocksDB.loadLibrary();
  }

  public void delete() throws Exception {
    delete(dbDirectory);
  }

  public void delete(final File dbDirectory) throws Exception {
    if (isOpened && this.dbDirectory == dbDirectory) {
      close();
    }

    try (Options options = createOptions()) {
      RocksDB.destroyDB(dbDirectory.toString(), options);
    } finally {
      closeables.forEach(CloseHelper::quietClose);
    }
  }

  public RocksDB open(final File dbDirectory, boolean reopen) throws Exception {
    if (!isOpened) {
      try {
        this.dbDirectory = dbDirectory;
        final Options options =
            createOptions().setErrorIfExists(!reopen).setCreateIfMissing(!reopen);
        closeables.add(options);

        db = openDB(options);
        isOpened = true;
      } catch (final RocksDBException ex) {
        close();
        throw ex;
      }

      LOG.trace("Opened RocksDB {}", this.dbDirectory);
    }

    return db;
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

  public boolean isOpened() {
    return isOpened;
  }

  public RocksDB getDb() {
    return db;
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

  protected void ensureIsOpened(String operation) {
    if (!isOpened()) {
      final String message =
          String.format("%s cannot be executed unless database is opened", operation);
      throw new IllegalStateException(message);
    }
  }
}
