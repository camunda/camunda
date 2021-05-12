/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import java.util.Properties;

public final class RocksdbCfg implements ConfigurationEntry {

  private boolean enableStatistics = RocksDbConfiguration.DEFAULT_STATISTICS_ENABLED;
  private int maxOpenFiles = RocksDbConfiguration.DEFAULT_MAX_OPEN_FILES;
  private int ioRateBytesPerSecond = RocksDbConfiguration.DEFAULT_IO_RATE_BYTES_PER_SECOND;
  private boolean disableWal = RocksDbConfiguration.DEFAULT_WAL_DISABLED;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {}

  public boolean isEnableStatistics() {
    return enableStatistics;
  }

  public void setEnableStatistics(final boolean enableStatistics) {
    this.enableStatistics = enableStatistics;
  }

  public int getMaxOpenFiles() {
    return maxOpenFiles;
  }

  public void setMaxOpenFiles(final int maxOpenFiles) {
    this.maxOpenFiles = maxOpenFiles;
  }

  public int getIoRateBytesPerSecond() {
    return ioRateBytesPerSecond;
  }

  public void setIoRateBytesPerSecond(final int ioRateBytesPerSecond) {
    this.ioRateBytesPerSecond = ioRateBytesPerSecond;
  }

  public boolean isDisableWal() {
    return disableWal;
  }

  public void setDisableWal(final boolean disableWal) {
    this.disableWal = disableWal;
  }

  public RocksDbConfiguration createRocksDbConfiguration(
      final Properties rocksdbColumnFamilyOptions) {
    return new RocksDbConfiguration()
        .setMaxOpenFiles(maxOpenFiles)
        .setStatisticsEnabled(enableStatistics)
        .setIoRateBytesPerSecond(ioRateBytesPerSecond)
        .setWalDisabled(disableWal)
        .setColumnFamilyOptions(rocksdbColumnFamilyOptions);
  }

  @Override
  public String toString() {
    return "RocksdbCfg{"
        + "enableStatistics="
        + enableStatistics
        + ", maxOpenFiles="
        + maxOpenFiles
        + ", ioRateBytesPerSecond="
        + ioRateBytesPerSecond
        + ", disableWal="
        + disableWal
        + '}';
  }
}
