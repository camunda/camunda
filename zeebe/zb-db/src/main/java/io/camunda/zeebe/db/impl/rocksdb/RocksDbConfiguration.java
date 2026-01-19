/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import java.util.Properties;

public final class RocksDbConfiguration {

  public static final long DEFAULT_MEMORY_LIMIT = 512 * 1024 * 1024L;
  public static final int DEFAULT_UNLIMITED_MAX_OPEN_FILES = -1;
  public static final int DEFAULT_MAX_WRITE_BUFFER_NUMBER = 6;
  public static final int DEFAULT_MIN_WRITE_BUFFER_NUMBER_TO_MERGE = 3;
  public static final boolean DEFAULT_STATISTICS_ENABLED = false;

  /**
   * WARN: It is safe to disable wal as long as there is only one column family. With more than one
   * column family, consistency across multiple column family is ensured by WAL while taking a
   * checkpoint.
   *
   * <p>http://rocksdb.org/blog/2015/11/10/use-checkpoints-for-efficient-snapshots.html >>> The
   * Checkpoint feature enables RocksDB to create a consistent snapshot of a given RocksDB database
   * in the specified directory. If the snapshot is on the same filesystem as the original database,
   * the SST files will be hard-linked, otherwise SST files will be copied. The manifest and CURRENT
   * files will be copied. In addition, if there are multiple column families, log files will be
   * copied for the period covering the start and end of the checkpoint, in order to provide a
   * consistent snapshot across column families. <<<
   */
  public static final boolean DEFAULT_WAL_DISABLED = true;

  /**
   * Enabling this feature gives a hint to the RocksDB compaction to compact based on virtual column
   * family prefixes. In consequence this means we will have more SST files, but split up into
   * related data sets.
   *
   * <p>Benchmarks have shown that this allows better performance even on large RocksDB state.
   */
  public static final boolean DEFAULT_SST_PARTITIONING_ENABLED = true;

  public static final int DEFAULT_IO_RATE_BYTES_PER_SECOND = 0;
  public static final MemoryAllocationStrategy DEFAULT_ROCKSDB_MEMORY_ALLOCATION_STRATEGY =
      MemoryAllocationStrategy.PARTITION;
  private Properties columnFamilyOptions = new Properties();
  private boolean statisticsEnabled = DEFAULT_STATISTICS_ENABLED;
  private long memoryLimit = DEFAULT_MEMORY_LIMIT;
  private int maxWriteBufferNumber = DEFAULT_MAX_WRITE_BUFFER_NUMBER;
  private int minWriteBufferNumberToMerge = DEFAULT_MIN_WRITE_BUFFER_NUMBER_TO_MERGE;
  private boolean walDisabled = DEFAULT_WAL_DISABLED;
  private boolean sstPartitioningEnabled = DEFAULT_SST_PARTITIONING_ENABLED;

  /**
   * Defines how many files are kept open by RocksDB, per default it is unlimited (-1). This is done
   * for performance reasons, if we set a value higher than zero it needs to keep track of open
   * files in the TableCache and look up on accessing them.
   *
   * <p>https://github.com/facebook/rocksdb/wiki/RocksDB-Tuning-Guide#general-options
   */
  private int maxOpenFiles = DEFAULT_UNLIMITED_MAX_OPEN_FILES;

  /**
   * Allows limiting the rate of I/O writes by RocksDB. This affects all writes performed by
   * RocksDB, including flushing, compaction, WAL, etc. It can be useful to configure to prevent
   * write spikes from affecting reads, thereby achieving a more predictable performance.
   *
   * <p>Setting to 0 (the default) or less will disable any rate limiting.
   *
   * <p>https://github.com/facebook/rocksdb/wiki/Rate-Limiter
   */
  private int ioRateBytesPerSecond = DEFAULT_IO_RATE_BYTES_PER_SECOND;

  private MemoryAllocationStrategy memoryAllocationStrategy =
      DEFAULT_ROCKSDB_MEMORY_ALLOCATION_STRATEGY;

  public RocksDbConfiguration() {}

  public Properties getColumnFamilyOptions() {
    return columnFamilyOptions;
  }

  public RocksDbConfiguration setColumnFamilyOptions(final Properties columnFamilyOptions) {
    this.columnFamilyOptions = columnFamilyOptions;
    return this;
  }

  public boolean isStatisticsEnabled() {
    return statisticsEnabled;
  }

  public RocksDbConfiguration setStatisticsEnabled(final boolean statisticsEnabled) {
    this.statisticsEnabled = statisticsEnabled;
    return this;
  }

  public long getMemoryLimit() {
    return memoryLimit;
  }

  public RocksDbConfiguration setMemoryLimit(final long memoryLimit) {
    this.memoryLimit = memoryLimit;
    return this;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public RocksDbConfiguration setMaxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
    return this;
  }

  public int getMaxWriteBufferNumber() {
    return maxWriteBufferNumber;
  }

  public RocksDbConfiguration setMaxWriteBufferNumber(final int maxWriteBufferNumber) {
    this.maxWriteBufferNumber = maxWriteBufferNumber;
    return this;
  }

  public int getMinWriteBufferNumberToMerge() {
    return minWriteBufferNumberToMerge;
  }

  public RocksDbConfiguration setMinWriteBufferNumberToMerge(
      final int minWriteBufferNumberToMerge) {
    this.minWriteBufferNumberToMerge = minWriteBufferNumberToMerge;
    return this;
  }

  public int getIoRateBytesPerSecond() {
    return ioRateBytesPerSecond;
  }

  public RocksDbConfiguration setIoRateBytesPerSecond(final int ioRateBytesPerSecond) {
    this.ioRateBytesPerSecond = ioRateBytesPerSecond;
    return this;
  }

  public boolean isWalDisabled() {
    return walDisabled;
  }

  public RocksDbConfiguration setWalDisabled(final boolean walDisabled) {
    this.walDisabled = walDisabled;
    return this;
  }

  public boolean isSstPartitioningEnabled() {
    return sstPartitioningEnabled;
  }

  public RocksDbConfiguration setSstPartitioningEnabled(final boolean sstPartitioningEnabled) {
    this.sstPartitioningEnabled = sstPartitioningEnabled;
    return this;
  }

  public MemoryAllocationStrategy getMemoryAllocationStrategy() {
    return memoryAllocationStrategy;
  }

  public RocksDbConfiguration setMemoryAllocationStrategy(
      final MemoryAllocationStrategy memoryAllocationStrategy) {
    this.memoryAllocationStrategy = memoryAllocationStrategy;
    return this;
  }

  public enum MemoryAllocationStrategy {
    PARTITION,
    BROKER,
    FRACTION
  }
}
