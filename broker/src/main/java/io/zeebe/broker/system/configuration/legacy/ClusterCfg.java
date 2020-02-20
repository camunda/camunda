/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system.configuration.legacy;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.zeebe.util.Environment;
import java.util.Collections;
import java.util.List;
import org.agrona.collections.IntArrayList;

@Deprecated(since = "0.23.0-alpha1")
/* Kept in order to be able to offer a migration path for old configuration.
 * It is not yet clear whether we intent to offer a migration path for old configurations.
 * This class might be moved or removed on short notice.
 */
public final class ClusterCfg implements ConfigurationEntry {
  public static final List<String> DEFAULT_CONTACT_POINTS = Collections.emptyList();
  public static final int DEFAULT_NODE_ID = 0;
  public static final int DEFAULT_PARTITIONS_COUNT = 1;
  public static final int DEFAULT_REPLICATION_FACTOR = 1;
  public static final int DEFAULT_CLUSTER_SIZE = 1;
  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";

  // the following values are from atomix per default
  private static final long DEFAULT_GOSSIP_FAILURE_TIMEOUT = 10_000;
  private static final int DEFAULT_GOSSIP_INTERVAL = 250;
  private static final int DEFAULT_GOSSIP_PROBE_INTERVAL = 1000;

  private List<String> initialContactPoints = DEFAULT_CONTACT_POINTS;

  private List<Integer> partitionIds;
  private int nodeId = DEFAULT_NODE_ID;
  private int partitionsCount = DEFAULT_PARTITIONS_COUNT;
  private int replicationFactor = DEFAULT_REPLICATION_FACTOR;
  private int clusterSize = DEFAULT_CLUSTER_SIZE;
  private String clusterName = DEFAULT_CLUSTER_NAME;

  // We do not add this to the toString or env - to hide it from the config
  private long gossipFailureTimeout = DEFAULT_GOSSIP_FAILURE_TIMEOUT;
  private long gossipInterval = DEFAULT_GOSSIP_INTERVAL;
  private long gossipProbeInterval = DEFAULT_GOSSIP_PROBE_INTERVAL;

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

  public void setClusterName(final String clusterName) {
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

  @Override
  public String toString() {

    return "ClusterCfg{"
        + "nodeId="
        + nodeId
        + ", partitionsCount="
        + partitionsCount
        + ", replicationFactor="
        + replicationFactor
        + ", clusterSize="
        + clusterSize
        + ", initialContactPoints="
        + initialContactPoints
        + '}';
  }
}
