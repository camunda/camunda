/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl.rocksdb;

import java.util.Properties;

public final class RocksDbConfiguration {

  public static final long DEFAULT_MEMORY_LIMIT = 512 * 1024 * 1024L;
  public static final int DEFAULT_UNLIMITED_MAX_OPEN_FILES = -1;

  private final Properties columnFamilyOptions;
  private final boolean statisticsEnabled;
  private final long memoryLimit;

  /**
   * Defines how many files are kept open by RocksDB, per default it is unlimited (-1). This is done
   * for performance reasons, if we set a value higher then zero it needs to keep track of open
   * files in the TableCache and look up on accessing them.
   *
   * <p>https://github.com/facebook/rocksdb/wiki/RocksDB-Tuning-Guide#general-options
   */
  private final int maxOpenFiles;

  private RocksDbConfiguration(
      final Properties columnFamilyOptions,
      final boolean statisticsEnabled,
      final long memoryLimit,
      final int maxOpenFiles) {
    this.columnFamilyOptions = columnFamilyOptions;
    this.statisticsEnabled = statisticsEnabled;
    this.memoryLimit = memoryLimit;
    this.maxOpenFiles = maxOpenFiles;
  }

  public static RocksDbConfiguration empty() {
    return of(new Properties());
  }

  public static RocksDbConfiguration of(final Properties properties) {
    return of(properties, false);
  }

  public static RocksDbConfiguration of(
      final Properties properties, final boolean statisticsEnabled) {
    return of(properties, statisticsEnabled, DEFAULT_MEMORY_LIMIT);
  }

  public static RocksDbConfiguration of(
      final Properties properties, final boolean statisticsEnabled, final long memoryLimit) {
    return of(properties, statisticsEnabled, memoryLimit, DEFAULT_UNLIMITED_MAX_OPEN_FILES);
  }

  public static RocksDbConfiguration of(
      final Properties properties,
      final boolean statisticsEnabled,
      final long memoryLimit,
      final int maxOpenFiles) {
    return new RocksDbConfiguration(properties, statisticsEnabled, memoryLimit, maxOpenFiles);
  }

  public Properties getColumnFamilyOptions() {
    return columnFamilyOptions;
  }

  public boolean isStatisticsEnabled() {
    return statisticsEnabled;
  }

  public long getMemoryLimit() {
    return memoryLimit;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }
}
