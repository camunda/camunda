/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbOptions;
import io.camunda.zeebe.db.impl.rocksdb.transaction.ZeebeTransactionDb;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Supplier;
import org.agrona.CloseHelper;
import org.rocksdb.BlockBasedTableConfig;
import org.rocksdb.BloomFilter;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.DBOptions;
import org.rocksdb.DataBlockIndexType;
import org.rocksdb.IndexType;
import org.rocksdb.LRUCache;
import org.rocksdb.Options;
import org.rocksdb.RateLimiter;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.SstPartitionerFixedPrefixFactory;
import org.rocksdb.Statistics;
import org.rocksdb.StatsLevel;
import org.rocksdb.TableFormatConfig;

public final class ZeebeRocksDbFactory<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements ZeebeDbFactory<ColumnFamilyType> {

  static {
    RocksDB.loadLibrary();
  }

  private final RocksDbConfiguration rocksDbConfiguration;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final AccessMetricsConfiguration metrics;
  private final Supplier<MeterRegistry> meterRegistryFactory;

  public ZeebeRocksDbFactory(
      final RocksDbConfiguration rocksDbConfiguration,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final AccessMetricsConfiguration metricsConfiguration,
      final Supplier<MeterRegistry> meterRegistryFactory) {
    this.rocksDbConfiguration = Objects.requireNonNull(rocksDbConfiguration);
    this.consistencyChecksSettings = Objects.requireNonNull(consistencyChecksSettings);
    metrics = metricsConfiguration;
    this.meterRegistryFactory = Objects.requireNonNull(meterRegistryFactory);
  }

  @Override
  public ZeebeTransactionDb<ColumnFamilyType> createDb(final File pathName) {
    final List<AutoCloseable> closeables = Collections.synchronizedList(new ArrayList<>());
    try {
      return ZeebeTransactionDb.openTransactionalDb(
          prepareOptions(closeables),
          pathName.getAbsolutePath(),
          closeables,
          rocksDbConfiguration,
          consistencyChecksSettings,
          metrics,
          meterRegistryFactory);
    } catch (final RocksDBException e) {
      CloseHelper.quietCloseAll(closeables);
      throw new IllegalStateException("Unexpected error occurred trying to open the database", e);
    }
  }

  @Override
  public ZeebeDb<ColumnFamilyType> openSnapshotOnlyDb(final File pathName) {
    final List<AutoCloseable> managedResources = Collections.synchronizedList(new ArrayList<>());
    final var options = prepareOptions(managedResources);
    final var snapshotOnlyOptions =
        new Options(options.dbOptions(), options.cfOptions())
            // only open existing databases
            .setCreateIfMissing(false)
            // this can slow down open significantly if there are many SST files
            .setSkipCheckingSstFileSizesOnDbOpen(true);
    managedResources.add(snapshotOnlyOptions);

    try {
      return SnapshotOnlyDb.openDb(
          snapshotOnlyOptions, pathName.getAbsolutePath(), managedResources);
    } catch (final RocksDBException e) {
      CloseHelper.quietCloseAll(managedResources);
      throw new IllegalStateException(
          "Unexpected error occurred trying to open a snapshot-only database", e);
    }
  }

  private RocksDbOptions prepareOptions(final List<AutoCloseable> managedResources) {
    // column family options have to be closed as last
    final var columnFamilyOptions = createColumnFamilyOptions(managedResources);
    managedResources.add(columnFamilyOptions);
    final var dbOptions = createDefaultDbOptions(managedResources);
    managedResources.add(dbOptions);
    return new RocksDbOptions(dbOptions, columnFamilyOptions);
  }

  private DBOptions createDefaultDbOptions(final List<AutoCloseable> closeables) {
    final var props = new Properties();
    props.put("file_checksum_gen_factory", "FileChecksumGenCrc32cFactory");
    //    Enables full file checksum

    final var dbOptions =
        DBOptions.getDBOptionsFromProps(props)
            .setErrorIfExists(false)
            .setCreateIfMissing(true)
            .setParanoidChecks(true)
            .setMaxOpenFiles(rocksDbConfiguration.getMaxOpenFiles())
            // 1 flush, 1 compaction
            .setMaxBackgroundJobs(2)
            // we only use the default CF
            .setCreateMissingColumnFamilies(false)
            // may not be necessary when WAL is disabled, but nevertheless recommended to avoid
            // many small SST files
            .setAvoidFlushDuringRecovery(true)
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

  /**
   * Creates column family options by merging user-provided options with our optimized defaults.
   * User-provided options take precedence over defaults, ensuring users can customize behavior
   * while still benefiting from our performance optimizations for unspecified settings.
   *
   * @param closeables list to track resources that need to be closed
   * @return configured ColumnFamilyOptions with merged user and default settings
   */
  public ColumnFamilyOptions createColumnFamilyOptions(final List<AutoCloseable> closeables) {

    final var memoryConfig = calculateMemoryConfiguration();
    final var options = createDefaultColumnFamilyOptionsAsProperties(memoryConfig);
    // Overwrite with user-provided options
    options.putAll(rocksDbConfiguration.getColumnFamilyOptions());

    final var columnFamilyOptions = ColumnFamilyOptions.getColumnFamilyOptionsFromProps(options);
    if (columnFamilyOptions == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to create column family options for RocksDB, "
                  + "but one or many values are undefined in the context of RocksDB "
                  + "[User-provided ColumnFamilyOptions: %s]. "
                  + "See RocksDB's cf_options.h and options_helper.cc for available keys and values.",
              rocksDbConfiguration.getColumnFamilyOptions()));
    }

    // Apply configuration that cannot be set via Properties
    final var tableConfig = createTableFormatConfig(closeables, memoryConfig.blockCacheMemory);
    columnFamilyOptions.setTableFormatConfig(tableConfig);

    // Apply SST partitioner factory if enabled (also cannot be set via properties)
    if (rocksDbConfiguration.isSstPartitioningEnabled()) {
      columnFamilyOptions.setSstPartitionerFactory(
          new SstPartitionerFixedPrefixFactory(Long.BYTES));
    }
    return columnFamilyOptions;
  }

  /**
   * Calculates memory configuration values based on the RocksDB configuration. This method
   * centralizes memory calculations to avoid duplication.
   */
  MemoryConfiguration calculateMemoryConfiguration() {
    final var totalMemoryBudget = rocksDbConfiguration.getMemoryLimit();
    // recommended by RocksDB, but we could tweak it; keep in mind we're also caching the indexes
    // and filters into the block cache, so we don't need to account for more memory there
    final var blockCacheMemory = totalMemoryBudget / 3;
    // flushing the memtables is done asynchronously, so there may be multiple memtables in memory,
    // although only a single one is writable. once we have too many memtables, writes will stop.
    // since prefix iteration is our bread n butter, we will build an additional filter for each
    // memtable which takes a bit of memory which must be accounted for from the memtable's memory
    final var maxConcurrentMemtableCount = rocksDbConfiguration.getMaxWriteBufferNumber();
    // this is a current guess and candidate for further tuning
    // values can be between 0 and 0.25 (anything higher gets clamped to 0.25), we randomly picked
    // 0.15
    // prefix seek must be fast, so we allocate some extra memory of a single memtable budget to
    // create
    // a filter for each memtable, allowing us to skip the prefixes if possible
    final var memtablePrefixFilterMemory = 0.15;
    final var memtableMemory =
        Math.round(
            ((totalMemoryBudget - blockCacheMemory) / (double) maxConcurrentMemtableCount)
                * (1 - memtablePrefixFilterMemory));

    return new MemoryConfiguration(
        totalMemoryBudget,
        blockCacheMemory,
        memtableMemory,
        memtablePrefixFilterMemory,
        maxConcurrentMemtableCount);
  }

  /**
   * Creates default column family options as Properties. This method translates all the method
   * calls from createDefaultColumnFamilyOptions to their corresponding Properties keys based on
   * RocksDB's options format.
   */
  Properties createDefaultColumnFamilyOptionsAsProperties(final MemoryConfiguration memoryConfig) {
    final var props = new Properties();

    if (rocksDbConfiguration.isSstPartitioningEnabled()) {
      props.setProperty(
          "sst_partitioner_factory",
          "{id=SstPartitionerFixedPrefixFactory;length=" + Long.BYTES + ";}");
    }

    // to extract our column family type (used as prefix) and seek faster
    props.setProperty("prefix_extractor", "rocksdb.FixedPrefix." + Long.BYTES);
    props.setProperty(
        "memtable_prefix_bloom_size_ratio",
        RocksDbOptionsFormatter.format(memoryConfig.memtablePrefixFilterMemory()));

    // memtables
    // merge at least 3 memtables per L0 file, otherwise all memtables are flushed as individual
    // files
    // this is also a candidate for tuning, it was a rough guess
    props.setProperty(
        "min_write_buffer_number_to_merge",
        RocksDbOptionsFormatter.format(rocksDbConfiguration.getMinWriteBufferNumberToMerge()));
    props.setProperty(
        "max_write_buffer_number",
        RocksDbOptionsFormatter.format(memoryConfig.maxConcurrentMemtableCount));
    props.setProperty(
        "write_buffer_size", RocksDbOptionsFormatter.format(memoryConfig.memtableMemory));

    // compaction
    props.setProperty("level_compaction_dynamic_level_bytes", RocksDbOptionsFormatter.format(true));
    props.setProperty("compaction_pri", "kOldestSmallestSeqFirst");
    props.setProperty("compaction_style", "kCompactionStyleLevel");

    // L-0 means immediately flushed memtables
    props.setProperty(
        "level0_file_num_compaction_trigger",
        RocksDbOptionsFormatter.format(memoryConfig.maxConcurrentMemtableCount));
    props.setProperty(
        "level0_slowdown_writes_trigger",
        RocksDbOptionsFormatter.format(
            memoryConfig.maxConcurrentMemtableCount
                + (memoryConfig.maxConcurrentMemtableCount / 2)));
    props.setProperty(
        "level0_stop_writes_trigger", String.valueOf(memoryConfig.maxConcurrentMemtableCount * 2));

    // configure 4 levels: L1 = 32mb, L2 = 320mb, L3 = 3.2Gb, L4 >= 3.2Gb
    // level 1 and 2 are uncompressed, level 3 and above are compressed using a CPU-cheap
    // compression algo. compressed blocks are stored in the OS page cache, and uncompressed in
    // the LRUCache created above. note L0 is always uncompressed
    props.setProperty("num_levels", RocksDbOptionsFormatter.format(4));
    props.setProperty(
        "max_bytes_for_level_base", RocksDbOptionsFormatter.format(32 * 1024 * 1024L));
    props.setProperty("max_bytes_for_level_multiplier", RocksDbOptionsFormatter.format(10.0));
    props.setProperty(
        "compression_per_level", "kNoCompression:kNoCompression:kLZ4Compression:kLZ4Compression");

    // Target file size for compaction.
    // Defines the desired SST file size for different levels (but not guaranteed, it is usually
    // lower)
    // L0 is what gets merged and flushed, e.g. 3 memtables to X, and target file size and
    // multiplier is for L1 and other levels.
    // L1 => 8Mb, L2 => 16Mb, L3 => 32Mb
    // As levels get bigger, we want to have a good balance between the number of files and the
    // individual file sizes
    // https://github.com/facebook/rocksdb/blob/fd0d35d390e212b617e90d7567102d3e5fd1c706/include/rocksdb/advanced_options.h#L417-L429
    props.setProperty("target_file_size_base", RocksDbOptionsFormatter.format(8 * 1024 * 1024L));
    props.setProperty("target_file_size_multiplier", RocksDbOptionsFormatter.format(2));

    return props;
  }

  private TableFormatConfig createTableFormatConfig(
      final List<AutoCloseable> closeables, final long blockCacheMemory) {
    // you can use the perf context to check if we're often blocked on the block cache mutex, in
    // which case we want to increase the number of shards (shard count == 2^shardBits)
    final var cache = new LRUCache(blockCacheMemory, 8, false, 0.15);
    closeables.add(cache);

    final var filter = new BloomFilter(10, false);
    closeables.add(filter);

    return new BlockBasedTableConfig()
        .setBlockCache(cache)
        // increasing block size means reducing memory usage, but increasing read iops
        .setBlockSize(32 * 1024L)
        // full and partitioned filters use a more efficient bloom filter implementation when
        // using format 5
        .setFormatVersion(5)
        .setFilterPolicy(filter)
        // caching and pinning indexes and filters is important to keep reads/seeks fast when we
        // have many memtables, and pinning them ensures they are never evicted from the block
        // cache
        .setCacheIndexAndFilterBlocks(true)
        .setPinL0FilterAndIndexBlocksInCache(true)
        .setCacheIndexAndFilterBlocksWithHighPriority(true)
        // default is binary search, but all of our scans are prefix based which is a good use
        // case for efficient hashing
        .setIndexType(IndexType.kHashSearch)
        .setDataBlockIndexType(DataBlockIndexType.kDataBlockBinaryAndHash)
        // RocksDB dev benchmarks show improvements when this is between 0.5 and 1, so let's
        // start with the middle and optimize later from there
        .setDataBlockHashTableUtilRatio(0.75)
        // while we mostly care about the prefixes, these are covered below by the
        // setMemtablePrefixBloomSizeRatio which will create a separate index for prefixes, so
        // keeping the whole keys in the prefixes is still useful for efficient gets. think of
        // it as a two-tiered index
        .setWholeKeyFiltering(true);
  }

  /** Holds calculated memory configuration values to avoid duplication. */
  private record MemoryConfiguration(
      long totalMemoryBudget,
      long blockCacheMemory,
      long memtableMemory,
      double memtablePrefixFilterMemory,
      int maxConcurrentMemtableCount) {}
}
