/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import java.util.Properties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.unit.DataSize;

public class RocksDb {

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
   * applies to RocksDB, which is used by the Zeebe's state management and that an RocksDB instance
   * is used per partition.
   */
  private DataSize memoryLimit = DataSize.ofBytes(512 * 1024 * 1024L);

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
   * families. By default RocksDB will not partition the SST files, which might have influence on
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
    return statisticsEnabled;
  }

  public void setStatisticsEnabled(final boolean statisticsEnabled) {
    this.statisticsEnabled = statisticsEnabled;
  }

  public AccessMetricsKind getAccessMetrics() {
    return accessMetrics;
  }

  public void setAccessMetrics(final AccessMetricsKind accessMetrics) {
    this.accessMetrics = accessMetrics;
  }

  public DataSize getMemoryLimit() {
    return memoryLimit;
  }

  public void setMemoryLimit(final DataSize memoryLimit) {
    this.memoryLimit = memoryLimit;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public void setMaxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
  }

  public int getMaxWriteBufferNumber() {
    return maxWriteBufferNumber;
  }

  public void setMaxWriteBufferNumber(final int maxWriteBufferNumber) {
    this.maxWriteBufferNumber = maxWriteBufferNumber;
  }

  public int getMinWriteBufferNumberToMerge() {
    return minWriteBufferNumberToMerge;
  }

  public void setMinWriteBufferNumberToMerge(final int minWriteBufferNumberToMerge) {
    this.minWriteBufferNumberToMerge = minWriteBufferNumberToMerge;
  }

  public int getIoRateBytesPerSecond() {
    return ioRateBytesPerSecond;
  }

  public void setIoRateBytesPerSecond(final int ioRateBytesPerSecond) {
    this.ioRateBytesPerSecond = ioRateBytesPerSecond;
  }

  public boolean isWalDisabled() {
    return walDisabled;
  }

  public void setWalDisabled(final boolean walDisabled) {
    this.walDisabled = walDisabled;
  }

  public boolean isSstPartitioningEnabled() {
    return sstPartitioningEnabled;
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
