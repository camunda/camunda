/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import java.util.Properties;
import org.jspecify.annotations.Nullable;

public final class RocksDbConfiguration {

  public static final long DEFAULT_MEMORY_LIMIT = 512 * 1024 * 1024L;
  public static final double DEFAULT_MEMORY_FRACTION = 0.1;
  public static final int DEFAULT_UNLIMITED_MAX_OPEN_FILES = -1;
  public static final int DEFAULT_MAX_WRITE_BUFFER_NUMBER = 6;

  /**
   * Flush every write buffer on its own: merging several before flushing keeps deleted entries
   * (tombstones) in memory for longer, where every iteration over their key range has to step over
   * them one by one.
   */
  public static final int DEFAULT_MIN_WRITE_BUFFER_NUMBER_TO_MERGE = 1;

  /**
   * Upper bound for the size of a single write buffer, regardless of the memory budget. Small write
   * buffers are flushed sooner, which moves tombstones out of memory into SST files where they can
   * be compacted away. Can be overridden with the {@code write_buffer_size} column family option.
   */
  public static final long DEFAULT_MAX_WRITE_BUFFER_SIZE = 16 * 1024 * 1024L;

  public static final boolean DEFAULT_STATISTICS_ENABLED = false;

  /**
   * Marks a file for compaction if any 1000 consecutive entries contain at least 500 deletions,
   * which is the shape queue-like column families (activatable jobs, deadlines, timers) produce.
   */
  public static final CompactOnDeletion DEFAULT_COMPACT_ON_DELETION =
      new CompactOnDeletion(1000, 500, 0);

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
      MemoryAllocationStrategy.FRACTION;
  private Properties columnFamilyOptions = new Properties();
  private boolean statisticsEnabled = DEFAULT_STATISTICS_ENABLED;
  private long memoryLimit = DEFAULT_MEMORY_LIMIT;
  private double memoryFraction = DEFAULT_MEMORY_FRACTION;
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

  /**
   * When set, SST files whose entries contain a high density of deletions are marked for compaction
   * as soon as they are written. Set to {@code null} to disable.
   *
   * <p>https://github.com/facebook/rocksdb/wiki/Implement-Queue-Service-Using-RocksDB
   */
  private @Nullable CompactOnDeletion compactOnDeletion = DEFAULT_COMPACT_ON_DELETION;

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

  public double getMemoryFraction() {
    return memoryFraction;
  }

  public RocksDbConfiguration setMemoryFraction(final double memoryFraction) {
    this.memoryFraction = memoryFraction;
    return this;
  }

  public @Nullable CompactOnDeletion getCompactOnDeletion() {
    return compactOnDeletion;
  }

  public RocksDbConfiguration setCompactOnDeletion(
      final @Nullable CompactOnDeletion compactOnDeletion) {
    this.compactOnDeletion = compactOnDeletion;
    return this;
  }

  /**
   * Settings of RocksDB's {@code CompactOnDeletionCollector}: a file is marked for compaction if
   * any sliding window of {@code windowSize} consecutive entries contains at least {@code
   * deletionTrigger} deletions, or if the share of deletions in the whole file is at least {@code
   * deletionRatio} (a ratio of 0 disables the latter).
   */
  public record CompactOnDeletion(long windowSize, long deletionTrigger, double deletionRatio) {
    public CompactOnDeletion {
      if (windowSize <= 0 || deletionTrigger <= 0 || deletionTrigger > windowSize) {
        throw new IllegalArgumentException(
            "Expected 0 < deletionTrigger <= windowSize, but got deletionTrigger=%d, windowSize=%d"
                .formatted(deletionTrigger, windowSize));
      }
      if (deletionRatio < 0 || deletionRatio > 1) {
        throw new IllegalArgumentException(
            "Expected deletionRatio in [0, 1], but got " + deletionRatio);
      }
    }
  }

  public enum MemoryAllocationStrategy {
    PARTITION,
    BROKER,
    FRACTION
  }
}
