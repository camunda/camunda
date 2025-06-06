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
  private int nodeId = 0;
  private String runtimeDirectory = null;
  private Duration snapshotPeriod = Duration.ofMinutes(5);
  private boolean diskMonitoringEnabled = true;
  private Duration diskMonitoringInterval = Duration.ofSeconds(1);
  private DataSize diskFreeSpaceProcessing = DataSize.ofGigabytes(2);
  private DataSize diskFreeSpaceReplication = DataSize.ofGigabytes(1);
  private boolean gossipSyncEnabled = true; // double check that this is correct
  private Duration gossipSyncDelay = Duration.ofSeconds(10);
  private Duration gossipSyncRequestTimeout = Duration.ofSeconds(2);
  private int gossipFanout = 2;

  public int getGossipFanout() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.gossipFanout");
    if (deprecated != null) {
      return Integer.parseInt(deprecated);
    }

    return gossipFanout;
  }

  public void setGossipFanout(int gossipFanout) {
    this.gossipFanout = gossipFanout;
  }

  public Duration getGossipSyncRequestTimeout() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.syncRequestTimeout");
    if (deprecated != null) {
      return Duration.parse(deprecated);
    }

    return gossipSyncRequestTimeout;
  }

  public Duration getGossipSyncDelay() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.syncDelay");
    if (deprecated != null) {
      return Duration.parse(deprecated);
    }

    return gossipSyncDelay;
  }

  public void setGossipSyncDelay(Duration gossipSyncDelay) {
    this.gossipSyncDelay = gossipSyncDelay;
  }

  public boolean getGossipSyncEnabled() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.configManager.gossip.enableSync");
    if (deprecated != null) {
      return Boolean.parseBoolean(deprecated);
    }

    return gossipSyncEnabled;
  }

  public void setGossipSyncEnabled(boolean gossipSyncEnabled) {
    this.gossipSyncEnabled = gossipSyncEnabled;
  }

  public int getNodeId() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.cluster.nodeId");
    if (deprecated != null) {
      return Integer.parseInt(deprecated);
    }

    return nodeId;
  }

  public String getRuntimeDirectory() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.runtimeDirectory");
    if (deprecated != null) {
      return deprecated;
    }

    return runtimeDirectory;
  }

  public Duration getSnapshotPeriod() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.snapshotPeriod");
    if (deprecated != null) {
      return Duration.parse(snapshotPeriod.toString());
    }

    return snapshotPeriod;
  }

  public boolean getDiskMonitoringEnabled() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.enableMonitoring");
    if (deprecated != null) {
      return Boolean.parseBoolean(deprecated);
    }

    return diskMonitoringEnabled;
  }

  public DataSize getDiskFreeSpaceProcessing() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.disk.freeSpace.processing");
    if (deprecated != null) {
      return DataSize.parse(deprecated);
    }

    return diskFreeSpaceProcessing;
  }

  public DataSize getDiskFreeSpaceReplication() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.disk.freeSpace.replication");
    if (deprecated != null) {
      return DataSize.parse(deprecated);
    }

    return diskFreeSpaceReplication;
  }

  public Duration getDiskMonitoringInterval() {
    String deprecated = UnifiedConfigurationRegistry.getDeprecatedValue("zeebe.broker.data.disk.monitoringInterval");
    if (deprecated != null) {
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
