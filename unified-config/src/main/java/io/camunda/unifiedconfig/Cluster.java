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
    return FallbackConfig.getInt(
        "zeebe.broker.cluster.configManager.gossip.gossipFanout", gossipFanout);
  }

  public void setGossipFanout(int gossipFanout) {
    this.gossipFanout = gossipFanout;
  }

  public Duration getGossipSyncRequestTimeout() {
    return FallbackConfig.getDuration(
        "zeebe.broker.cluster.configManager.gossip.syncRequestTimeout", gossipSyncRequestTimeout);
  }

  public Duration getGossipSyncDelay() {
    return FallbackConfig.getDuration(
        "zeebe.broker.cluster.configManager.gossip.syncDelay", gossipSyncDelay);
  }

  public void setGossipSyncDelay(Duration gossipSyncDelay) {
    this.gossipSyncDelay = gossipSyncDelay;
  }

  public boolean getGossipSyncEnabled() {
    return FallbackConfig.getBoolean(
        "zeebe.broker.cluster.configManager.gossip.enableSync", gossipSyncEnabled);
  }

  public void setGossipSyncEnabled(boolean gossipSyncEnabled) {
    this.gossipSyncEnabled = gossipSyncEnabled;
  }

  public int getNodeId() {
    return FallbackConfig.getInt("zeebe.broker.cluster.nodeId", nodeId);
  }

  public String getRuntimeDirectory() {
    return FallbackConfig.getString("zeebe.broker.data.runtimeDirectory", runtimeDirectory);
  }

  public Duration getSnapshotPeriod() {
    return FallbackConfig.getDuration("zeebe.broker.data.snapshotPeriod", snapshotPeriod);
  }

  public boolean getDiskMonitoringEnabled() {
    return FallbackConfig.getBoolean("zeebe.broker.data.enableMonitoring", diskMonitoringEnabled);
  }

  public DataSize getDiskFreeSpaceProcessing() {
    return FallbackConfig.getDataSize(
        "zeebe.broker.data.disk.freeSpace.processing", diskFreeSpaceProcessing);
  }

  public DataSize getDiskFreeSpaceReplication() {
    return FallbackConfig.getDataSize(
        "zeebe.broker.data.disk.freeSpace.replication", diskFreeSpaceReplication);
  }

  public Duration getDiskMonitoringInterval() {
    return FallbackConfig.getDuration(
        "zeebe.broker.data.disk.monitoringInterval", diskMonitoringInterval);
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
