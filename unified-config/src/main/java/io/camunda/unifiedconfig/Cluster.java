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
  private Boolean diskMonitoringEnabled;
  private Duration diskMonitoringInterval;
  private DataSize diskFreeSpaceProcessing;
  private DataSize diskFreeSpaceReplication;
  private Boolean gossipSyncEnabled;
  private Duration gossipSyncDelay;
  private Duration gossipSyncRequestTimeout;
  private Integer gossipFanout;

  public int getGossipFanout() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.gossipFanout");
    if (deprecated != null && gossipFanout == null) {
      return Integer.parseInt(deprecated);
    }

    return gossipFanout;
  }

  public void setGossipFanout(int gossipFanout) {
    this.gossipFanout = gossipFanout;
  }

  public Duration getGossipSyncRequestTimeout() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.syncRequestTimeout");
    if (deprecated != null && gossipSyncRequestTimeout == null) {
      return Duration.parse(deprecated);
    }

    return gossipSyncRequestTimeout;
  }

  public Duration getGossipSyncDelay() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.syncDelay");
    if (deprecated != null && gossipSyncDelay == null) {
      return Duration.parse(deprecated);
    }

    return gossipSyncDelay;
  }

  public void setGossipSyncDelay(Duration gossipSyncDelay) {
    this.gossipSyncDelay = gossipSyncDelay;
  }

  public boolean getGossipSyncEnabled() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.enableSync");
    if (deprecated != null && gossipSyncEnabled == null) {
      return Boolean.parseBoolean(deprecated);
    }

    return gossipSyncEnabled;
  }

  public void setGossipSyncEnabled(boolean gossipSyncEnabled) {
    this.gossipSyncEnabled = gossipSyncEnabled;
  }

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
