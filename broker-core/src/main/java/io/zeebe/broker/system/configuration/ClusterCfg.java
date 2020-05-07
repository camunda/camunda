/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.zeebe.util.Environment;
import java.util.Collections;
import java.util.List;
import org.agrona.collections.IntArrayList;

public class ClusterCfg implements ConfigurationEntry {
  public static final List<String> DEFAULT_CONTACT_POINTS = Collections.emptyList();
  public static final int DEFAULT_NODE_ID = 0;
  public static final int DEFAULT_PARTITIONS_COUNT = 1;
  public static final int DEFAULT_REPLICATION_FACTOR = 1;
  public static final int DEFAULT_CLUSTER_SIZE = 1;
  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";

  // the following values are from atomix per default
  private static final boolean DEFAULT_GOSSIP_BROADCAST_UPDATES = false;
  private static final boolean DEFAULT_GOSSIP_BROADCAST_DISPUTES = true;
  private static final boolean DEFAULT_GOSSIP_NOTIFY_SUSPECT = false;
  private static final int DEFAULT_GOSSIP_INTERVAL = 250;
  private static final int DEFAULT_GOSSIP_FANOUT = 2;
  private static final int DEFAULT_GOSSIP_PROBE_INTERVAL = 1_000;
  private static final int DEFAULT_GOSSIP_SUSPECT_PROBES = 3;
  private static final int DEFAULT_GOSSIP_FAILURE_TIMEOUT = 10_000;

  private List<String> initialContactPoints = DEFAULT_CONTACT_POINTS;

  private List<Integer> partitionIds;
  private int nodeId = DEFAULT_NODE_ID;
  private int partitionsCount = DEFAULT_PARTITIONS_COUNT;
  private int replicationFactor = DEFAULT_REPLICATION_FACTOR;
  private int clusterSize = DEFAULT_CLUSTER_SIZE;
  private String clusterName = DEFAULT_CLUSTER_NAME;

  private long gossipFailureTimeout = DEFAULT_GOSSIP_FAILURE_TIMEOUT;
  private long gossipInterval = DEFAULT_GOSSIP_INTERVAL;
  private long gossipProbeInterval = DEFAULT_GOSSIP_PROBE_INTERVAL;
  private boolean gossipBroadcastDisputes = DEFAULT_GOSSIP_BROADCAST_DISPUTES;
  private int gossipFanout = DEFAULT_GOSSIP_FANOUT;
  private boolean notifySuspect = DEFAULT_GOSSIP_NOTIFY_SUSPECT;
  private int suspectedProbes = DEFAULT_GOSSIP_SUSPECT_PROBES;
  private boolean gossipBroadcastUpdates = DEFAULT_GOSSIP_BROADCAST_UPDATES;

  @Override
  public void init(
      final BrokerCfg globalConfig, final String brokerBase, final Environment environment) {
    applyEnvironment(environment);

    initPartitionIds();
  }

  private void initPartitionIds() {
    final IntArrayList list = new IntArrayList();
    for (int i = START_PARTITION_ID; i < START_PARTITION_ID + partitionsCount; i++) {
      final int partitionId = i;
      list.add(partitionId);
    }

    partitionIds = Collections.unmodifiableList(list);
  }

  private void applyEnvironment(final Environment environment) {
    environment.getInt(EnvironmentConstants.ENV_NODE_ID).ifPresent(v -> nodeId = v);
    environment.getInt(EnvironmentConstants.ENV_CLUSTER_SIZE).ifPresent(v -> clusterSize = v);
    environment.get(EnvironmentConstants.ENV_CLUSTER_NAME).ifPresent(v -> clusterName = v);
    environment
        .getInt(EnvironmentConstants.ENV_PARTITIONS_COUNT)
        .ifPresent(v -> partitionsCount = v);
    environment
        .getInt(EnvironmentConstants.ENV_REPLICATION_FACTOR)
        .ifPresent(v -> replicationFactor = v);
    environment
        .getList(EnvironmentConstants.ENV_INITIAL_CONTACT_POINTS)
        .ifPresent(v -> initialContactPoints = v);

    environment
        .getLong(EnvironmentConstants.ENV_GOSSIP_FAILURE_TIMEOUT)
        .ifPresent(v -> gossipFailureTimeout = v);
    environment
        .getLong(EnvironmentConstants.ENV_GOSSIP_INTERVAL)
        .ifPresent(v -> gossipInterval = v);
    environment
        .getLong(EnvironmentConstants.ENV_GOSSIP_PROBE_INTERVAL)
        .ifPresent(v -> gossipProbeInterval = v);
    environment
        .getBool(EnvironmentConstants.ENV_GOSSIP_BROADCAST_DISPUTES)
        .ifPresent(v -> gossipBroadcastDisputes = v);
    environment.getInt(EnvironmentConstants.ENV_GOSSIP_FANOUT).ifPresent(v -> gossipFanout = v);
    environment
        .getBool(EnvironmentConstants.ENV_GOSSIP_NOTIFY_SUSPECT)
        .ifPresent(v -> notifySuspect = v);
    environment
        .getInt(EnvironmentConstants.ENV_GOSSIP_SUSPECT_PROBES)
        .ifPresent(v -> suspectedProbes = v);
    environment
        .getBool(EnvironmentConstants.ENV_GOSSIP_BROADCAST_UPDATES)
        .ifPresent(v -> gossipBroadcastUpdates = v);
  }

  public List<String> getInitialContactPoints() {
    return initialContactPoints;
  }

  public void setInitialContactPoints(final List<String> initialContactPoints) {
    this.initialContactPoints = initialContactPoints;
  }

  public int getNodeId() {
    return nodeId;
  }

  public void setNodeId(final int nodeId) {
    this.nodeId = nodeId;
  }

  public int getPartitionsCount() {
    return partitionsCount;
  }

  public void setPartitionsCount(final int partitionsCount) {
    this.partitionsCount = partitionsCount;
  }

  public List<Integer> getPartitionIds() {
    return partitionIds;
  }

  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  public int getClusterSize() {
    return clusterSize;
  }

  public void setClusterSize(final int clusterSize) {
    this.clusterSize = clusterSize;
  }

  public String getClusterName() {
    return clusterName;
  }

  public void setClusterName(String clusterName) {
    this.clusterName = clusterName;
  }

  public long getGossipFailureTimeout() {
    return gossipFailureTimeout;
  }

  public void setGossipFailureTimeout(final long gossipFailureTimeout) {
    this.gossipFailureTimeout = gossipFailureTimeout;
  }

  public long getGossipInterval() {
    return gossipInterval;
  }

  public void setGossipInterval(final long gossipInterval) {
    this.gossipInterval = gossipInterval;
  }

  public long getGossipProbeInterval() {
    return gossipProbeInterval;
  }

  public void setGossipProbeInterval(final long gossipProbeInterval) {
    this.gossipProbeInterval = gossipProbeInterval;
  }

  public boolean isGossipBroadcastDisputes() {
    return gossipBroadcastDisputes;
  }

  public void setGossipBroadcastDisputes(final boolean gossipBroadcastDisputes) {
    this.gossipBroadcastDisputes = gossipBroadcastDisputes;
  }

  public int getGossipFanout() {
    return gossipFanout;
  }

  public void setGossipFanout(final int gossipFanout) {
    this.gossipFanout = gossipFanout;
  }

  public boolean isNotifySuspect() {
    return notifySuspect;
  }

  public void setNotifySuspect(final boolean notifySuspect) {
    this.notifySuspect = notifySuspect;
  }

  public int getSuspectedProbes() {
    return suspectedProbes;
  }

  public void setSuspectedProbes(final int suspectedProbes) {
    this.suspectedProbes = suspectedProbes;
  }

  public boolean isGossipBroadcastUpdates() {
    return gossipBroadcastUpdates;
  }

  public void setGossipBroadcastUpdates(final boolean gossipBroadcastUpdates) {
    this.gossipBroadcastUpdates = gossipBroadcastUpdates;
  }

  @Override
  public String toString() {
    return "ClusterCfg{"
        + "initialContactPoints="
        + initialContactPoints
        + ", partitionIds="
        + partitionIds
        + ", nodeId="
        + nodeId
        + ", partitionsCount="
        + partitionsCount
        + ", replicationFactor="
        + replicationFactor
        + ", clusterSize="
        + clusterSize
        + ", clusterName='"
        + clusterName
        + '\''
        + ", gossipFailureTimeout="
        + gossipFailureTimeout
        + ", gossipInterval="
        + gossipInterval
        + ", gossipProbeInterval="
        + gossipProbeInterval
        + ", gossipBroadcastDisputes="
        + gossipBroadcastDisputes
        + ", gossipFanout="
        + gossipFanout
        + ", notifySuspect="
        + notifySuspect
        + ", suspectedProbes="
        + suspectedProbes
        + ", gossipBroadcastUpdates="
        + gossipBroadcastUpdates
        + '}';
  }
}
