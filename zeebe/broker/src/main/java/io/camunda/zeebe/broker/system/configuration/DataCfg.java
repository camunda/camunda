/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.configuration.backup.BackupCfg;
import java.io.File;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.springframework.util.unit.DataSize;

public final class DataCfg implements ConfigurationEntry {

  public static final String DEFAULT_DIRECTORY = "data";
  private static final Logger LOG = Loggers.SYSTEM_LOGGER;
  private static final DataSize DEFAULT_DATA_SIZE = DataSize.ofMegabytes(128);

  /**
   * Root (shared) data directory.
   *
   * <p>This is the base directory configured by users. The broker's effective {@link #directory}
   * may be derived from this root (e.g. by appending node ID/version subdirectories).
   */
  private String rootDirectory = DEFAULT_DIRECTORY;

  /**
   * Effective broker data directory.
   *
   * <p>By default this is unset, and {@link #getDirectory()} falls back to {@link #rootDirectory}
   * when using {@link DataDirectoryInitializationMode#USE_PRECONFIGURED_DIRECTORY}.
   */
  private String directory;

  private String runtimeDirectory = null;

  private DataDirectoryInitializationMode initializationMode =
      DataDirectoryInitializationMode.USE_PRECONFIGURED_DIRECTORY;

  private DataSize logSegmentSize = DEFAULT_DATA_SIZE;

  private Duration snapshotPeriod = Duration.ofMinutes(5);

  private int logIndexDensity = 100;

  // diskUsageMonitoring and watermark configs are deprecated and replaced by DiskCfg
  private Boolean diskUsageMonitoringEnabled;
  private Double diskUsageReplicationWatermark;
  private Double diskUsageCommandWatermark;
  private Duration diskUsageMonitoringInterval;
  private DiskCfg disk = new DiskCfg();
  private BackupCfg backup = new BackupCfg();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    rootDirectory = ConfigurationUtil.toAbsolutePath(rootDirectory, brokerBase);
    if (directory != null && !directory.isBlank()) {
      directory = ConfigurationUtil.toAbsolutePath(directory, brokerBase);
    }
    if (runtimeDirectory != null) {
      runtimeDirectory = ConfigurationUtil.toAbsolutePath(runtimeDirectory, brokerBase);
    }

    backup.init(globalConfig, brokerBase);

    overrideDiskConfig();
    disk.init(globalConfig, brokerBase);
  }

  private void overrideDiskConfig() {
    // For backward compatibility, if the old disk watermarks are configured use those values
    // instead of the new ones
    if (diskUsageMonitoringEnabled != null) {
      LOG.warn(
          "Configuration parameter data.diskUsageMonitoringEnabled is deprecated. Use data.disk.enableMonitoring instead.");
      disk.setEnableMonitoring(diskUsageMonitoringEnabled);
    }
    if (diskUsageMonitoringInterval != null) {
      LOG.warn(
          "Configuration parameter data.diskUsageMonitoringInterval is deprecated. Use data.disk.monitoringInterval instead.");
      disk.setMonitoringInterval(diskUsageMonitoringInterval);
    }
    if (diskUsageCommandWatermark != null) {
      LOG.warn(
          "Configuration parameter data.diskUsageCommandWatermark is deprecated. Use data.disk.freeSpace.processing instead.");
      final DataSize requiredFreeSpace = convertWatermarkToFreeSpace(diskUsageCommandWatermark);
      disk.getFreeSpace().setProcessing(requiredFreeSpace);
    }
    if (diskUsageReplicationWatermark != null) {
      LOG.warn(
          "Configuration parameter data.diskUsageReplicationWatermark is deprecated. Use data.disk.freeSpace.replication instead.");
      final DataSize requiredFreeSpace = convertWatermarkToFreeSpace(diskUsageReplicationWatermark);
      disk.getFreeSpace().setReplication(requiredFreeSpace);
    }
  }

  private DataSize convertWatermarkToFreeSpace(final Double watermark) {
    // TODO check what to do here.
    final String directoryForDiskCalculation =
        directory == null || directory.isBlank() ? rootDirectory : directory;
    final var directoryFile = new File(directoryForDiskCalculation);
    return DataSize.ofBytes(Math.round(directoryFile.getTotalSpace() * (1 - watermark)));
  }

  public String getRootDirectory() {
    return rootDirectory;
  }

  public void setRootDirectory(final String rootDirectory) {
    this.rootDirectory = rootDirectory;
  }

  public String getDirectory() {
    if (directory == null || directory.isBlank()) {
      if (initializationMode == DataDirectoryInitializationMode.SHARED_ROOT_VERSIONED_NODE) {
        throw new IllegalStateException(
            "Data directory is not initialized; expected a versioned directory to be derived from rootDirectory");
      }
      return rootDirectory;
    }
    return directory;
  }

  public void setDirectory(final String directory) {
    this.directory = directory;
  }

  public String getRuntimeDirectory() {
    return runtimeDirectory;
  }

  public void setRuntimeDirectory(final String runtimeDirectory) {
    this.runtimeDirectory = runtimeDirectory;
  }

  public boolean useSeparateRuntimeDirectory() {
    return runtimeDirectory != null && !runtimeDirectory.isEmpty();
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

  public void setDiskUsageMonitoringEnabled(final boolean diskUsageMonitoringEnabled) {
    this.diskUsageMonitoringEnabled = diskUsageMonitoringEnabled;
  }

  public void setDiskUsageCommandWatermark(final double diskUsageCommandWatermark) {
    this.diskUsageCommandWatermark = diskUsageCommandWatermark;
  }

  public void setDiskUsageReplicationWatermark(final double diskUsageReplicationWatermark) {
    this.diskUsageReplicationWatermark = diskUsageReplicationWatermark;
  }

  public void setDiskUsageMonitoringInterval(final Duration diskUsageMonitoringInterval) {
    this.diskUsageMonitoringInterval = diskUsageMonitoringInterval;
  }

  public DiskCfg getDisk() {
    return disk;
  }

  public void setDisk(final DiskCfg disk) {
    this.disk = disk;
  }

  public BackupCfg getBackup() {
    return backup;
  }

  public void setBackup(final BackupCfg backup) {
    this.backup = backup;
  }

  public DataDirectoryInitializationMode getInitializationMode() {
    return initializationMode;
  }

  public void setInitializationMode(final DataDirectoryInitializationMode initializationMode) {
    this.initializationMode = initializationMode;
  }

  @Override
  public String toString() {
    return "DataCfg{"
        + "rootDirectory='"
        + rootDirectory
        + '\''
        + ", directory='"
        + directory
        + '\''
        + ", stateDirectory='"
        + runtimeDirectory
        + '\''
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
        + ", disk="
        + disk
        + ", backup="
        + backup
        + '}';
  }
}
