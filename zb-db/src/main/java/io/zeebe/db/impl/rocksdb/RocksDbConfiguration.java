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

  private final Properties columnFamilyOptions;
  private final boolean statisticsEnabled;

  private RocksDbConfiguration(
      final Properties columnFamilyOptions, final boolean statisticsEnabled) {
    this.columnFamilyOptions = columnFamilyOptions;
    this.statisticsEnabled = statisticsEnabled;
  }

  public static RocksDbConfiguration empty() {
    return new RocksDbConfiguration(new Properties(), false);
  }

  public static RocksDbConfiguration of(final Properties properties) {
    return new RocksDbConfiguration(properties, false);
  }

  public static RocksDbConfiguration of(
      final Properties properties, final boolean statisticsEnabled) {
    return new RocksDbConfiguration(properties, statisticsEnabled);
  }

  public Properties getColumnFamilyOptions() {
    return columnFamilyOptions;
  }

  public boolean isStatisticsEnabled() {
    return statisticsEnabled;
  }
}
