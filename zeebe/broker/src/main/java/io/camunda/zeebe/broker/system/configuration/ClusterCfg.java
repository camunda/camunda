/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static io.camunda.zeebe.broker.Broker.LOG;
import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.camunda.zeebe.util.StringUtil.LIST_SANITIZER;

import io.atomix.cluster.messaging.MessagingConfig.CompressionAlgorithm;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class ClusterCfg implements ConfigurationEntry {

  public static final List<String> DEFAULT_CONTACT_POINTS = Collections.emptyList();
  public static final int DEFAULT_NODE_ID = 0;
  public static final int DEFAULT_PARTITIONS_COUNT = 1;
  public static final int DEFAULT_REPLICATION_FACTOR = 1;
  public static final int DEFAULT_CLUSTER_SIZE = 1;
  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";
  public static final Duration DEFAULT_ELECTION_TIMEOUT = Duration.ofMillis(2500);

  private static final String NODE_ID_ERROR_MSG =
      "Node id %s needs to be non negative and smaller then cluster size %s.";
  private static final String REPLICATION_FACTOR_ERROR_MSG =
      "Replication factor %s needs to be larger then zero and not larger then cluster size %s.";

  private static final String REPLICATION_FACTOR_WARN_MSG =
      "Expected to have odd replication factor, but was even ({}). Even replication factor has no benefit over "
          + "the previous odd value and is weaker than next odd. Quorum is calculated as:"
          + " quorum = floor(replication factor / 2) + 1. In this current case the quorum will be"
          + " quorum = {}. If you want to ensure high fault-tolerance and availability,"
          + " make sure to use an odd replication factor.";
  private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofMillis(250);

  private List<String> initialContactPoints = DEFAULT_CONTACT_POINTS;

  private List<Integer> partitionIds;
  private int nodeId = DEFAULT_NODE_ID;
  private int partitionsCount = DEFAULT_PARTITIONS_COUNT;
  private int replicationFactor = DEFAULT_REPLICATION_FACTOR;
  private int clusterSize = DEFAULT_CLUSTER_SIZE;
  private String clusterName = DEFAULT_CLUSTER_NAME;
  private String clusterId;
  private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
  private Duration electionTimeout = DEFAULT_ELECTION_TIMEOUT;
  private MembershipCfg membership = new MembershipCfg();
  private RaftCfg raft = new RaftCfg();
  private CompressionAlgorithm messageCompression = CompressionAlgorithm.NONE;
  private ConfigManagerCfg configManager = ConfigManagerCfg.defaultConfig();

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    initPartitionIds();

    if (partitionsCount < 1) {
      throw new IllegalArgumentException("Partition count must not be smaller than 1.");
    }

    if (nodeId < 0) {
      throw new IllegalArgumentException("Node id must be positive");
    }

    if (replicationFactor < 1 || replicationFactor > clusterSize) {
      throw new IllegalArgumentException(
          String.format(REPLICATION_FACTOR_ERROR_MSG, replicationFactor, clusterSize));
    }

    if (replicationFactor % 2 == 0) {
      LOG.warn(REPLICATION_FACTOR_WARN_MSG, replicationFactor, (replicationFactor / 2) + 1);
    }

    if (heartbeatInterval.toMillis() < 1) {
      throw new IllegalArgumentException(
          String.format("heartbeatInterval %s must be at least 1ms", heartbeatInterval));
    }
    if (electionTimeout.toMillis() < 1) {
      throw new IllegalArgumentException(
          String.format("electionTimeout %s must be at least 1ms", electionTimeout));
    }
    if (electionTimeout.compareTo(heartbeatInterval) < 1) {
      throw new IllegalArgumentException(
          String.format(
              "electionTimeout %s must be greater than heartbeatInterval %s",
              electionTimeout, heartbeatInterval));
    }
  }

  private void initPartitionIds() {
    partitionIds =
        IntStream.range(START_PARTITION_ID, START_PARTITION_ID + partitionsCount)
            .boxed()
            .collect(Collectors.toList());
  }

  public List<String> getInitialContactPoints() {
    return initialContactPoints;
  }

  public void setInitialContactPoints(final List<String> initialContactPoints) {
    this.initialContactPoints = LIST_SANITIZER.apply(initialContactPoints);
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

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public MembershipCfg getMembership() {
    return membership;
  }

  public void setMembership(final MembershipCfg membership) {
    this.membership = membership;
  }

  public Duration getHeartbeatInterval() {
    return heartbeatInterval;
  }

  public void setHeartbeatInterval(final Duration heartbeatInterval) {
    this.heartbeatInterval = heartbeatInterval;
  }

  public RaftCfg getRaft() {
    return raft;
  }

  public void setRaft(final RaftCfg raft) {
    this.raft = raft;
  }

  public Duration getElectionTimeout() {
    return electionTimeout;
  }

  public void setElectionTimeout(final Duration electionTimeout) {
    this.electionTimeout = electionTimeout;
  }

  public CompressionAlgorithm getMessageCompression() {
    return messageCompression;
  }

  public void setMessageCompression(final CompressionAlgorithm messageCompression) {
    this.messageCompression = messageCompression;
  }

  public ConfigManagerCfg getConfigManager() {
    return configManager;
  }

  public void setConfigManager(final ConfigManagerCfg configManagerCfg) {
    configManager = configManagerCfg;
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        initialContactPoints,
        partitionIds,
        nodeId,
        partitionsCount,
        replicationFactor,
        clusterSize,
        clusterName,
        heartbeatInterval,
        electionTimeout,
        membership,
        raft,
        messageCompression,
        configManager);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterCfg that = (ClusterCfg) o;
    return nodeId == that.nodeId
        && partitionsCount == that.partitionsCount
        && replicationFactor == that.replicationFactor
        && clusterSize == that.clusterSize
        && Objects.equals(initialContactPoints, that.initialContactPoints)
        && Objects.equals(partitionIds, that.partitionIds)
        && Objects.equals(clusterName, that.clusterName)
        && Objects.equals(heartbeatInterval, that.heartbeatInterval)
        && Objects.equals(electionTimeout, that.electionTimeout)
        && Objects.equals(membership, that.membership)
        && Objects.equals(raft, that.raft)
        && messageCompression == that.messageCompression
        && Objects.equals(configManager, that.configManager);
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
        + ", heartbeatInterval="
        + heartbeatInterval
        + ", electionTimeout="
        + electionTimeout
        + ", membership="
        + membership
        + ", raft="
        + raft
        + ", messageCompression="
        + messageCompression
        + ", configManagerCfg="
        + configManager
        + '}';
  }
}
