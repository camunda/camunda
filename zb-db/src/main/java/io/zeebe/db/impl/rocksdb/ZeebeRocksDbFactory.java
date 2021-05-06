/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb;

import io.zeebe.db.ZeebeDbFactory;
import io.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyDescriptor;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactionPriority;
import org.rocksdb.CompactionStyle;
import org.rocksdb.CompressionType;
import org.rocksdb.DBOptions;
import org.rocksdb.RateLimiter;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.Statistics;
import org.rocksdb.StatsLevel;
import org.rocksdb.TableFormatConfig;

public final class ZeebeRocksDbFactory<ColumnFamilyType extends Enum<ColumnFamilyType>>
    implements ZeebeDbFactory<ColumnFamilyType> {

  static {
    RocksDB.loadLibrary();
  }

  private final Class<ColumnFamilyType> columnFamilyTypeClass;
  private final RocksDbConfiguration rocksDbConfiguration;

  private ZeebeRocksDbFactory(
      final Class<ColumnFamilyType> columnFamilyTypeClass,
      final RocksDbConfiguration rocksDbConfiguration) {
    this.columnFamilyTypeClass = columnFamilyTypeClass;
    this.rocksDbConfiguration = Objects.requireNonNull(rocksDbConfiguration);
  }

  public static <ColumnFamilyType extends Enum<ColumnFamilyType>>
      ZeebeDbFactory<ColumnFamilyType> newFactory(
          final Class<ColumnFamilyType> columnFamilyTypeClass) {
    return new ZeebeRocksDbFactory<>(columnFamilyTypeClass, new RocksDbConfiguration());
  }

  public static <ColumnFamilyType extends Enum<ColumnFamilyType>>
      ZeebeDbFactory<ColumnFamilyType> newFactory(
          final Class<ColumnFamilyType> columnFamilyTypeClass,
          final RocksDbConfiguration rocksDbConfiguration) {
    return new ZeebeRocksDbFactory<>(columnFamilyTypeClass, rocksDbConfiguration);
  }

  @Override
  public ZeebeTransactionDb<ColumnFamilyType> createDb(final File pathName) {
    return open(
        pathName,
        Arrays.stream(columnFamilyTypeClass.getEnumConstants())
            .map(c -> c.name().toLowerCase().getBytes())
            .collect(Collectors.toList()));
  }

  private ZeebeTransactionDb<ColumnFamilyType> open(
      final File dbDirectory, final List<byte[]> columnFamilyNames) {

    final ZeebeTransactionDb<ColumnFamilyType> db;
    try {
      final List<AutoCloseable> closeables = new ArrayList<>();

      // column family options have to be closed as last
      final ColumnFamilyOptions columnFamilyOptions = createColumnFamilyOptions(closeables);
      closeables.add(columnFamilyOptions);

      final List<ColumnFamilyDescriptor> columnFamilyDescriptors =
          createFamilyDescriptors(columnFamilyNames, columnFamilyOptions);
      final DBOptions dbOptions = createDefaultDbOptions(closeables);
      closeables.add(dbOptions);

      db =
          ZeebeTransactionDb.openTransactionalDb(
              dbOptions,
              dbDirectory.getAbsolutePath(),
              columnFamilyDescriptors,
              closeables,
              columnFamilyTypeClass);

    } catch (final RocksDBException e) {
      throw new RuntimeException("Unexpected error occurred trying to open the database", e);
    }
    return db;
  }

  private List<ColumnFamilyDescriptor> createFamilyDescriptors(
      final List<byte[]> columnFamilyNames, final ColumnFamilyOptions columnFamilyOptions) {
    final List<ColumnFamilyDescriptor> columnFamilyDescriptors = new ArrayList<>();

    if (columnFamilyNames != null && !columnFamilyNames.isEmpty()) {
      for (final byte[] name : columnFamilyNames) {
        final ColumnFamilyDescriptor columnFamilyDescriptor =
            new ColumnFamilyDescriptor(name, columnFamilyOptions);
        columnFamilyDescriptors.add(columnFamilyDescriptor);
      }
    }
    return columnFamilyDescriptors;
  }

  /** @return Options which are used on all column families */
  public ColumnFamilyOptions createColumnFamilyOptions(final List<AutoCloseable> closeables) {
    final var userProvidedColumnFamilyOptions = rocksDbConfiguration.getColumnFamilyOptions();
    final var hasUserOptions = !userProvidedColumnFamilyOptions.isEmpty();

    if (hasUserOptions) {
      return createFromUserOptions(userProvidedColumnFamilyOptions);
    }

    return createDefaultColumnFamilyOptions(closeables);
  }

  private ColumnFamilyOptions createDefaultColumnFamilyOptions(
      final List<AutoCloseable> closeables) {
    final var columnFamilyOptions = new ColumnFamilyOptions();
    final var tableConfig = createTableFormatConfig(closeables);

    final int level0CompactionTrigger = 4;
    columnFamilyOptions
        // compaction
        .setLevelCompactionDynamicLevelBytes(true)
        .setCompactionPriority(CompactionPriority.OldestSmallestSeqFirst)
        .setCompactionStyle(CompactionStyle.LEVEL)
        .setLevel0FileNumCompactionTrigger(level0CompactionTrigger)
        .setLevel0SlowdownWritesTrigger(level0CompactionTrigger + (level0CompactionTrigger / 2))
        .setLevel0StopWritesTrigger(level0CompactionTrigger * 2)
        // configure 4 levels: L1 = 32mb, L2 = 320mb, L3 = 3.2Gb, L4 >= 3.2Gb
        // level 1 and 2 are uncompressed, level 3 and above are compressed using a CPU-cheap
        // compression algo. compressed blocks are stored in the OS page cache, and uncompressed in
        // the LRUCache created above. note L0 is always uncompressed
        .setNumLevels(4)
        .setMaxBytesForLevelBase(32 * 1024 * 1024L)
        .setMaxBytesForLevelMultiplier(10)
        .setCompressionPerLevel(
            List.of(
                CompressionType.NO_COMPRESSION,
                CompressionType.NO_COMPRESSION,
                CompressionType.LZ4_COMPRESSION,
                CompressionType.LZ4_COMPRESSION))
        // Target file size for compaction.
        // Defines the desired SST file size for different levels (but not guaranteed, it is usually
        // lower)
        // L0 is what gets merged and flushed, e.g. 3 memtables to X, and target file size and
        // multiplier is for L1 and other levels.
        // L1 => 8Mb, L2 => 16Mb, L3 => 32Mb
        // As levels get bigger, we want to have a good balance between the number of files and the
        // individual file sizes
        // https://github.com/facebook/rocksdb/blob/fd0d35d390e212b617e90d7567102d3e5fd1c706/include/rocksdb/advanced_options.h#L417-L429
        .setTargetFileSizeBase(8 * 1024 * 1024L)
        .setTargetFileSizeMultiplier(2)
        // misc
        .setTableFormatConfig(tableConfig);

    return columnFamilyOptions;
  }

  private ColumnFamilyOptions createFromUserOptions(
      final Properties userProvidedColumnFamilyOptions) {
    final var columnFamilyOptions =
        ColumnFamilyOptions.getColumnFamilyOptionsFromProps(userProvidedColumnFamilyOptions);
    if (columnFamilyOptions == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to create column family options for RocksDB, "
                  + "but one or many values are undefined in the context of RocksDB "
                  + "[User-provided ColumnFamilyOptions: %s]. "
                  + "See RocksDB's cf_options.h and options_helper.cc for available keys and values.",
              userProvidedColumnFamilyOptions));
    }
    return columnFamilyOptions;
  }

  private DBOptions createDefaultDbOptions(final List<AutoCloseable> closeables) {
    final var dbOptions =
        new DBOptions()
            .setCreateMissingColumnFamilies(true)
            .setErrorIfExists(false)
            .setCreateIfMissing(true)
            .setParanoidChecks(true)
            // disabling mmap helps improve performance/memory usage when a DB is highly
            // fragmented with many small files
            .setAllowMmapReads(false)
            .setAllowMmapWrites(false)
            .setMaxOpenFiles(rocksDbConfiguration.getMaxOpenFiles())
            // do not hog processing if the DB is highly fragmented by opening many threads to
            // open all files
            .setMaxFileOpeningThreads(1)
            // 1 flush, 1 compaction
            .setMaxBackgroundJobs(2)
            // may not be necessary when WAL is disabled, but nevertheless recommended to avoid
            // many small SST files
            .setAvoidFlushDuringRecovery(true)
            .setAvoidFlushDuringShutdown(true)
            // limit the size of the manifest (logs all operations), otherwise it will grow
            // unbounded
            .setMaxManifestFileSize(256 * 1024 * 1024L)
            // keep 1 hour of logs - completely arbitrary. we should keep what we think would be
            // a good balance between useful for performance and small for replication
            .setLogFileTimeToRoll(Duration.ofMinutes(30).toSeconds())
            .setKeepLogFileNum(2);

    // limit I/O writes
    if (rocksDbConfiguration.getIoRateBytesPerSecond() > 0) {
      final RateLimiter rateLimiter =
          new RateLimiter(rocksDbConfiguration.getIoRateBytesPerSecond());
      dbOptions.setRateLimiter(rateLimiter);
    }

    if (rocksDbConfiguration.isStatisticsEnabled()) {
      final var statistics = new Statistics();
      closeables.add(statistics);
      statistics.setStatsLevel(StatsLevel.ALL);
      dbOptions
          .setStatistics(statistics)
          // speeds up opening the DB
          .setSkipStatsUpdateOnDbOpen(true)
          // can be disabled when not profiling
          .setStatsDumpPeriodSec(20);
    }

    return dbOptions;
  }

  private TableFormatConfig createTableFormatConfig(final List<AutoCloseable> closeables) {
    final var filter = new BloomFilter(10, false);
    closeables.add(filter);
    return new BlockBasedTableConfig()
        // increasing block size means reducing memory usage, but increasing read iops
        .setBlockSize(32 * 1024L)
        // caching and pinning indexes and filters is important to keep reads/seeks fast when we
        // have many memtables, and pinning them ensures they are never evicted from the block
        // cache
        .setFormatVersion(5)
        .setFilterPolicy(filter)
        .setCacheIndexAndFilterBlocks(true)
        .setPinL0FilterAndIndexBlocksInCache(true)
        .setCacheIndexAndFilterBlocksWithHighPriority(true);
  }
}
