/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.unifiedconfig;

import java.time.Duration;
import org.springframework.util.unit.DataSize;

public class Cluster {
  private Integer nodeId;
  private String runtimeDirectory;
  private Duration snapshotPeriod;
  private Boolean diskMonitoringEnabled = true;
  private Duration diskMonitoringInterval = Duration.ofSeconds(1);
  private DataSize diskFreeSpaceProcessing = DataSize.ofGigabytes(2);
  private DataSize diskFreeSpaceReplication = DataSize.ofGigabytes(1);

  public int getNodeId() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.nodeId");
    if (deprecated != null && nodeId == null) {
      return Integer.parseInt(deprecated);
    }

    return nodeId;
  }

  public String getRuntimeDirectory() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.runtimeDirectory");
    if (deprecated != null && runtimeDirectory == null) {
      return deprecated;
    }

    return runtimeDirectory;
  }

  public Duration getSnapshotPeriod() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.snapshotPeriod");
    if (deprecated != null && snapshotPeriod == null) {
      return Duration.parse(snapshotPeriod.toString());
    }

    return snapshotPeriod;
  }

  public boolean getDiskMonitoringEnabled() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.enableMonitoring");
    if (deprecated != null && diskMonitoringEnabled == null) {
      return Boolean.parseBoolean(deprecated);
    }

    return diskMonitoringEnabled;
  }

  public DataSize getDiskFreeSpaceProcessing() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.disk.freeSpace.processing");
    if (deprecated != null && diskFreeSpaceProcessing == null) {
      return DataSize.parse(deprecated);
    }

    return diskFreeSpaceProcessing;
  }

  public DataSize getDiskFreeSpaceReplication() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.disk.freeSpace.replication");
    if (deprecated != null && diskFreeSpaceReplication == null) {
      return DataSize.parse(deprecated);
    }

    return diskFreeSpaceReplication;
  }

  public Duration getDiskMonitoringInterval() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.disk.monitoringInterval");
    if (deprecated != null && diskMonitoringInterval == null) {
      return Duration.parse(deprecated);
    }

    return diskMonitoringInterval;
  }

  public void setDiskMonitoringInterval(Duration diskMonitoringInterval) {
    this.diskMonitoringInterval = diskMonitoringInterval;
  }

  public void setDiskMonitoringEnabled(boolean diskMonitoringEnabled) {
    this.diskMonitoringEnabled = diskMonitoringEnabled;
  }

  public void setNodeId(int nodeId) {
    this.nodeId = nodeId;
  }

  public void setRuntimeDirectory(String runtimeDirectory) {
    this.runtimeDirectory = runtimeDirectory;
  }

  public void setSnapshotPeriod(Duration snapshotPeriod) {
    this.snapshotPeriod = snapshotPeriod;
  }
}
