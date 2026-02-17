/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import io.camunda.configuration.UnifiedConfigurationHelper.BackwardsCompatibilityMode;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import java.util.Properties;
import java.util.Set;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

public class RocksDb {

  private static final String PREFIX = "camunda.data.primary-storage.rocks-db";

  private static final Set<String> LEGACY_ENABLE_STATISTICS_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.enableStatistics");
  private static final Set<String> LEGACY_ACCESS_METRICS_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.accessMetrics");
  private static final Set<String> LEGACY_MEMORY_LIMIT_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.memoryLimit");
  private static final Set<String> LEGACY_MAX_OPEN_FILES_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.maxOpenFiles");
  private static final Set<String> LEGACY_MAX_WRITE_BUFFER_NUMBER_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.maxWriteBufferNumber");
  private static final Set<String> LEGACY_MIN_WRITE_BUFFER_NUMBER_TO_MERGE_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.minWriteBufferNumberToMerge");
  private static final Set<String> LEGACY_IO_RATE_BYTES_PER_SECOND_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.ioRateBytesPerSecond");
  private static final Set<String> LEGACY_DISABLE_WAL_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.disableWal");
  private static final Set<String> LEGACY_ENABLE_SST_PARTITIONING_PROPERTIES =
      Set.of("zeebe.broker.experimental.rocksdb.enableSstPartitioning");

  /**
   * Specify custom column family options overwriting Zeebe's own defaults. WARNING: This setting
   * requires in-depth knowledge of Zeebe's embedded database: RocksDB. The expected property key
   * names and values are derived from RocksDB's C implementation, and are not limited to the
   * provided examples below. Please look in RocksDB's SCM repo for the files: `cf_options.h` and
   * `options_helper.cc`.
   */
  private Properties columnFamilyOptions = new Properties();

  /** Enables RocksDB statistics, which will be written to the RocksDB log file. */
  private boolean statisticsEnabled = false;

  /**
   * Configures which, if any, RocksDB column family access metrics are exposed. Valid values are
   * none (the default), and fine which exposes many metrics covering the read, write, delete and
   * iteration latency per partition and column family.
   */
  @NestedConfigurationProperty private AccessMetricsKind accessMetrics = AccessMetricsKind.NONE;

  /**
   * Configures the memory limit, which can be used by RocksDB. Be aware that this setting only
   * applies to RocksDB, which is used by the Zeebe's state management with the memory limit being
   * shared across all partitions in a broker. The memory allocation strategy depends on the
   * memoryAllocationStrategy setting.
   */
  private DataSize memoryLimit = DataSize.ofBytes(512 * 1024 * 1024L);

  /**
   * Configures the memory allocation strategy by RocksDB. If set to 'PARTITION', the total memory
   * allocated to RocksDB will be the number of partitions times the configured memory limit. If the
   * value is set to 'BROKER', the total memory allocated to RocksDB will be equal to the configured
   * memory limit. If set to 'FRACTION', Zeebe will allocate a configurable percentage of total RAM
   * to RocksDB that can be configured via the memoryFraction configuration [0,1].
   */
  private MemoryAllocationStrategy memoryAllocationStrategy = MemoryAllocationStrategy.PARTITION;

  /**
   * Configures the fraction of total system memory to allocate to RocksDB when using the 'FRACTION'
   * memory allocation strategy. The value must be between 0 and 1 (exclusive). For example, a value
   * of 0.1 means 10% of total system memory will be allocated to RocksDB.
   */
  private double memoryFraction = 0.1;

  /**
   * Configures how many files are kept open by RocksDB, per default it is unlimited (-1). This is a
   * performance optimization: if you set a value greater than zero, it will keep track and cap the
   * number of open files in the TableCache. On accessing the files it needs to look them up in the
   * cache. You should configure this property if the maximum open files are limited on your system,
   * or if you have thousands of files in your RocksDB state as there is a memory overhead to
   * keeping all of them open, and setting maxOpenFiles will bound that.
   */
  private int maxOpenFiles = -1;

  /**
   * Configures the maximum number of simultaneous write buffers/memtables RocksDB will have in
   * memory. Normally about 2/3s of the memoryLimit is used by the write buffers, and this is shared
   * equally by each write buffers. This means the higher maxWriteBufferNumber is, the less memory
   * is available for each. This means you will flush less data at once, but may flush more often.
   */
  private int maxWriteBufferNumber = 6;

  /**
   * Configures how many write buffers should be full before they are merged and flushed to disk.
   * Having a higher number here means you may flush less often, but will flush more data at once.
   * Have a lower one means flushing more often, but flushing less data at once.
   */
  private int minWriteBufferNumberToMerge = 3;

  /**
   * Configures a rate limit for write I/O of RocksDB. Setting any value less than or equal to 0
   * will disable this, which is the default setting. Setting a rate limit on the write I/O can help
   * achieve more stable performance by avoiding write spikes consuming all available IOPS, leading
   * to more predictable read rates.
   */
  private int ioRateBytesPerSecond = 0;

  /**
   * Configures if the RocksDB write-ahead-log is used or not. By default, every write in RocksDB
   * goes to the active write buffer and the WAL; this helps recover a RocksDB instance should it
   * crash before the write buffer is flushed. Zeebe however only recovers from specific
   * point-in-time snapshot, and never from a previously active RocksDB instance, which makes it a
   * good candidate to disable the WAL. WAL is disabled by default as it can improve performance of
   * Zeebe.
   */
  private boolean walDisabled = true;

  /**
   * Configures if the RocksDB SST files should be partitioned based on some virtual column
   * families. By default, RocksDB will not partition the SST files, which might have influence on
   * the compacting of certain key ranges. Enabling this option gives RocksDB some good hints how to
   * improve compaction and reduce the write amplification. Benchmarks have show impressive results
   * allowing to sustain performance on larger states. This setting will increase the general file
   * count of runtime and snapshots.
   */
  private boolean sstPartitioningEnabled = true;

  public Properties getColumnFamilyOptions() {
    return columnFamilyOptions;
  }

  public void setColumnFamilyOptions(final Properties columnFamilyOptions) {
    this.columnFamilyOptions = columnFamilyOptions;
  }

  public boolean isStatisticsEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".statistics-enabled",
        statisticsEnabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLE_STATISTICS_PROPERTIES);
  }

  public void setStatisticsEnabled(final boolean statisticsEnabled) {
    this.statisticsEnabled = statisticsEnabled;
  }

  public AccessMetricsKind getAccessMetrics() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".access-metrics",
        accessMetrics,
        AccessMetricsKind.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ACCESS_METRICS_PROPERTIES);
  }

  public void setAccessMetrics(final AccessMetricsKind accessMetrics) {
    this.accessMetrics = accessMetrics;
  }

  public DataSize getMemoryLimit() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".memory-limit",
        memoryLimit,
        DataSize.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MEMORY_LIMIT_PROPERTIES);
  }

  public void setMemoryLimit(final DataSize memoryLimit) {
    this.memoryLimit = memoryLimit;
  }

  public MemoryAllocationStrategy getMemoryAllocationStrategy() {
    return memoryAllocationStrategy;
  }

  public void setMemoryAllocationStrategy(final MemoryAllocationStrategy memoryAllocationStrategy) {
    this.memoryAllocationStrategy = memoryAllocationStrategy;
  }

  public double getMemoryFraction() {
    return memoryFraction;
  }

  public void setMemoryFraction(final double memoryFraction) {
    this.memoryFraction = memoryFraction;
  }

  public int getMaxOpenFiles() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-open-files",
        maxOpenFiles,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_OPEN_FILES_PROPERTIES);
  }

  public void setMaxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
  }

  public int getMaxWriteBufferNumber() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".max-write-buffer-number",
        maxWriteBufferNumber,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MAX_WRITE_BUFFER_NUMBER_PROPERTIES);
  }

  public void setMaxWriteBufferNumber(final int maxWriteBufferNumber) {
    this.maxWriteBufferNumber = maxWriteBufferNumber;
  }

  public int getMinWriteBufferNumberToMerge() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".min-write-buffer-number-to-merge",
        minWriteBufferNumberToMerge,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_MIN_WRITE_BUFFER_NUMBER_TO_MERGE_PROPERTIES);
  }

  public void setMinWriteBufferNumberToMerge(final int minWriteBufferNumberToMerge) {
    this.minWriteBufferNumberToMerge = minWriteBufferNumberToMerge;
  }

  public int getIoRateBytesPerSecond() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".io-rate-bytes-per-second",
        ioRateBytesPerSecond,
        Integer.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_IO_RATE_BYTES_PER_SECOND_PROPERTIES);
  }

  public void setIoRateBytesPerSecond(final int ioRateBytesPerSecond) {
    this.ioRateBytesPerSecond = ioRateBytesPerSecond;
  }

  public boolean isWalDisabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".wal-disabled",
        walDisabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_DISABLE_WAL_PROPERTIES);
  }

  public void setWalDisabled(final boolean walDisabled) {
    this.walDisabled = walDisabled;
  }

  public boolean isSstPartitioningEnabled() {
    return UnifiedConfigurationHelper.validateLegacyConfiguration(
        PREFIX + ".sst-partitioning-enabled",
        sstPartitioningEnabled,
        Boolean.class,
        BackwardsCompatibilityMode.SUPPORTED,
        LEGACY_ENABLE_SST_PARTITIONING_PROPERTIES);
  }

  public void setSstPartitioningEnabled(final boolean sstPartitioningEnabled) {
    this.sstPartitioningEnabled = sstPartitioningEnabled;
  }

  @Override
  public String toString() {
    return "RocksDb{"
        + "columnFamilyOptions="
        + columnFamilyOptions
        + ", statisticsEnabled="
        + statisticsEnabled
        + ", accessMetrics="
        + accessMetrics
        + ", memoryLimit="
        + memoryLimit
        + ", maxOpenFiles="
        + maxOpenFiles
        + ", maxWriteBufferNumber="
        + maxWriteBufferNumber
        + ", minWriteBufferNumberToMerge="
        + minWriteBufferNumberToMerge
        + ", ioRateBytesPerSecond="
        + ioRateBytesPerSecond
        + ", walDisabled="
        + walDisabled
        + ", sstPartitioningEnabled="
        + sstPartitioningEnabled
        + '}';
  }

  public enum AccessMetricsKind {
    NONE,
    FINE
  }
}
