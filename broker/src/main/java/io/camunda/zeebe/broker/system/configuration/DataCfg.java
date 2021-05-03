/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.configuration;

import io.zeebe.broker.Loggers;
import java.io.File;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.util.unit.DataSize;

public final class DataCfg implements ConfigurationEntry {

  public static final String DEFAULT_DIRECTORY = "data";

  private static final Logger LOG = Loggers.SYSTEM_LOGGER;

  private static final DataSize DEFAULT_DATA_SIZE = DataSize.ofMegabytes(128);
  private static final boolean DEFAULT_DISK_USAGE_MONITORING_ENABLED = true;
  private static final double DEFAULT_DISK_USAGE_REPLICATION_WATERMARK = 0.99;
  private static final double DEFAULT_DISK_USAGE_COMMAND_WATERMARK = 0.97;
  private static final Duration DEFAULT_DISK_USAGE_MONITORING_DELAY = Duration.ofSeconds(1);
  private static final double DISABLED_DISK_USAGE_WATERMARK = 1.0;

  private String directory = DEFAULT_DIRECTORY;

  private DataSize logSegmentSize = DEFAULT_DATA_SIZE;

  private Duration snapshotPeriod = Duration.ofMinutes(5);

  private int logIndexDensity = 100;

  private boolean diskUsageMonitoringEnabled = DEFAULT_DISK_USAGE_MONITORING_ENABLED;
  private double diskUsageReplicationWatermark = DEFAULT_DISK_USAGE_REPLICATION_WATERMARK;
  private double diskUsageCommandWatermark = DEFAULT_DISK_USAGE_COMMAND_WATERMARK;
  private Duration diskUsageMonitoringInterval = DEFAULT_DISK_USAGE_MONITORING_DELAY;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    directory = ConfigurationUtil.toAbsolutePath(directory, brokerBase);

    if (!diskUsageMonitoringEnabled) {
      LOG.info(
          "Disk usage watermarks are disabled, setting all watermarks to {}",
          DISABLED_DISK_USAGE_WATERMARK);
      diskUsageReplicationWatermark = DISABLED_DISK_USAGE_WATERMARK;
      diskUsageCommandWatermark = DISABLED_DISK_USAGE_WATERMARK;
    }
  }

  public String getDirectory() {
    return directory;
  }

  public void setDirectory(final String directory) {
    this.directory = directory;
  }

  public long getLogSegmentSizeInBytes() {
    return Optional.ofNullable(logSegmentSize).orElse(DEFAULT_DATA_SIZE).toBytes();
  }

  public DataSize getLogSegmentSize() {
    return logSegmentSize;
  }

  public void setLogSegmentSize(final DataSize logSegmentSize) {
    this.logSegmentSize = logSegmentSize;
  }

  public Duration getSnapshotPeriod() {
    return snapshotPeriod;
  }

  public void setSnapshotPeriod(final Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }

  public int getLogIndexDensity() {
    return logIndexDensity;
  }

  public void setLogIndexDensity(final int logIndexDensity) {
    this.logIndexDensity = logIndexDensity;
  }

  public boolean isDiskUsageMonitoringEnabled() {
    return diskUsageMonitoringEnabled;
  }

  public void setDiskUsageMonitoringEnabled(final boolean diskUsageMonitoringEnabled) {
    this.diskUsageMonitoringEnabled = diskUsageMonitoringEnabled;
  }

  public double getDiskUsageCommandWatermark() {
    return diskUsageCommandWatermark;
  }

  public void setDiskUsageCommandWatermark(final double diskUsageCommandWatermark) {
    this.diskUsageCommandWatermark = diskUsageCommandWatermark;
  }

  public long getFreeDiskSpaceCommandWatermark() {
    final var directoryFile = new File(getDirectory());
    return Math.round(directoryFile.getTotalSpace() * (1 - diskUsageCommandWatermark));
  }

  public double getDiskUsageReplicationWatermark() {
    return diskUsageReplicationWatermark;
  }

  public void setDiskUsageReplicationWatermark(final double diskUsageReplicationWatermark) {
    this.diskUsageReplicationWatermark = diskUsageReplicationWatermark;
  }

  public long getFreeDiskSpaceReplicationWatermark() {
    final var directoryFile = new File(getDirectory());
    return Math.round(directoryFile.getTotalSpace() * (1 - diskUsageReplicationWatermark));
  }

  public Duration getDiskUsageMonitoringInterval() {
    return diskUsageMonitoringInterval;
  }

  public void setDiskUsageMonitoringInterval(final Duration diskUsageMonitoringInterval) {
    this.diskUsageMonitoringInterval = diskUsageMonitoringInterval;
  }

  @Override
  public String toString() {
    return "DataCfg{"
        + "directory="
        + directory
        + ", logSegmentSize="
        + logSegmentSize
        + ", snapshotPeriod="
        + snapshotPeriod
        + ", logIndexDensity="
        + logIndexDensity
        + ", diskUsageMonitoringEnabled="
        + diskUsageMonitoringEnabled
        + ", diskUsageReplicationWatermark="
        + diskUsageReplicationWatermark
        + ", diskUsageCommandWatermark="
        + diskUsageCommandWatermark
        + ", diskUsageMonitoringInterval="
        + diskUsageMonitoringInterval
        + '}';
  }
}
