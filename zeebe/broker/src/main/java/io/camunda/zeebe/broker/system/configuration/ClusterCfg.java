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
import io.camunda.zeebe.broker.system.configuration.partitioning.Scheme;
import io.camunda.zeebe.broker.system.configuration.partitioning.ZoneAwareCfg;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class ClusterCfg implements ConfigurationEntry {

  public static final List<String> DEFAULT_CONTACT_POINTS = Collections.emptyList();
  public static final int DEFAULT_PARTITIONS_COUNT = 1;
  public static final int DEFAULT_REPLICATION_FACTOR = 1;
  public static final int DEFAULT_CLUSTER_SIZE = 1;
  public static final String DEFAULT_CLUSTER_NAME = "zeebe-cluster";
  public static final Duration DEFAULT_ELECTION_TIMEOUT = Duration.ofMillis(2500);

  private static final String ZONE_SCHEME_ERROR_MSG =
      "Broker has zone '%s' configured but partitioning scheme is %s; set scheme to REGION_AWARE.";
  private static final String ZONE_NOT_FOUND_ERROR_MSG =
      "Broker zone '%s' is not defined in the zoneAware partitioning configuration. "
          + "Configured zones: %s.";
  private static final String NODE_ID_ZONE_ERROR_MSG =
      "Node id %d is out of range for zone '%s' (numberOfBrokers=%d). "
          + "nodeId must be in [0, numberOfBrokers).";
  private static final String BROKER_SUM_ERROR_MSG =
      "Sum of numberOfBrokers across all zones (%d) must equal clusterSize (%d).";
  private static final String REPLICA_SUM_ERROR_MSG =
      "Sum of numberOfReplicas across all zones (%d) must equal replicationFactor (%d).";
  private static final String REPLICAS_EXCEED_BROKERS_ERROR_MSG =
      "Zone '%s' has numberOfReplicas (%d) > numberOfBrokers (%d). "
          + "Two replicas of the same partition cannot reside on one broker.";
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

  @SuppressWarnings("NullAway.Init")
  // initialized in the init() method with the correct partitionCount
  private List<Integer> partitionIds;

  private Integer nodeId = 0;

  // TODO add javaDoc
  private Long nodeVersion = 0L;

  private @Nullable String clusterId;

  private int partitionsCount = DEFAULT_PARTITIONS_COUNT;
  private int replicationFactor = DEFAULT_REPLICATION_FACTOR;
  private int clusterSize = DEFAULT_CLUSTER_SIZE;
  private String clusterName = DEFAULT_CLUSTER_NAME;
  private Duration heartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
  private Duration electionTimeout = DEFAULT_ELECTION_TIMEOUT;
  private MembershipCfg membership = new MembershipCfg();
  private RaftCfg raft = new RaftCfg();
  private CompressionAlgorithm messageCompression = CompressionAlgorithm.NONE;
  private ConfigManagerCfg configManager = ConfigManagerCfg.defaultConfig();
  private @Nullable String zone;

  @Override
  public void init(final BrokerCfg globalConfig, final String brokerBase) {
    initPartitionIds();

    if (partitionsCount < 1) {
      throw new IllegalArgumentException("Partition count must not be smaller than 1.");
    }

    if (zone != null && !zone.isBlank()) {
      validateZoneAwareConfig(globalConfig);
    } else {
      if (nodeId < 0) {
        throw new IllegalArgumentException("Node id must be positive");
      }
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

  private void validateZoneAwareConfig(final BrokerCfg globalConfig) {
    final var partitioningCfg = globalConfig.getExperimental().getPartitioning();

    if (partitioningCfg.getScheme() != Scheme.REGION_AWARE) {
      throw new IllegalArgumentException(
          String.format(ZONE_SCHEME_ERROR_MSG, zone, partitioningCfg.getScheme()));
    }

    final ZoneAwareCfg zoneAwareCfg = partitioningCfg.getZoneAware();
    final var zones = zoneAwareCfg.getRegions();

    if (!zones.containsKey(zone)) {
      throw new IllegalArgumentException(
          String.format(ZONE_NOT_FOUND_ERROR_MSG, zone, zones.keySet()));
    }

    final var zoneCfg = zones.get(zone);
    if (nodeId < 0 || nodeId >= zoneCfg.getNumberOfBrokers()) {
      throw new IllegalArgumentException(
          String.format(NODE_ID_ZONE_ERROR_MSG, nodeId, zone, zoneCfg.getNumberOfBrokers()));
    }

    final int totalBrokers = zones.values().stream().mapToInt(r -> r.getNumberOfBrokers()).sum();
    if (totalBrokers != clusterSize) {
      throw new IllegalArgumentException(
          String.format(BROKER_SUM_ERROR_MSG, totalBrokers, clusterSize));
    }

    final int totalReplicas = zones.values().stream().mapToInt(r -> r.getNumberOfReplicas()).sum();
    if (totalReplicas != replicationFactor) {
      throw new IllegalArgumentException(
          String.format(REPLICA_SUM_ERROR_MSG, totalReplicas, replicationFactor));
    }

    zones.forEach(
        (zoneName, cfg) -> {
          if (cfg.getNumberOfReplicas() > cfg.getNumberOfBrokers()) {
            throw new IllegalArgumentException(
                String.format(
                    REPLICAS_EXCEED_BROKERS_ERROR_MSG,
                    zoneName,
                    cfg.getNumberOfReplicas(),
                    cfg.getNumberOfBrokers()));
          }
          if (cfg.getNumberOfBrokers() < 1 || cfg.getNumberOfReplicas() < 1) {
            throw new IllegalArgumentException(
                String.format("Zone '%s' must have at least 1 broker and 1 replica.", zoneName));
          }
        });
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

  public Integer getNodeId() {
    return nodeId;
  }

  public void setNodeId(final Integer nodeId) {
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

  public @Nullable String getClusterId() {
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

  public @Nullable String getZone() {
    return zone;
  }

  public void setZone(final @Nullable String zone) {
    this.zone = zone;
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
        configManager,
        zone);
  }

  @Override
  public boolean equals(@Nullable final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final ClusterCfg that = (ClusterCfg) o;
    return Objects.equals(nodeId, that.nodeId)
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
        && Objects.equals(configManager, that.configManager)
        && Objects.equals(zone, that.zone);
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
        + ", zone='"
        + zone
        + '\''
        + '}';
  }

  public Long getNodeVersion() {
    return nodeVersion;
  }

  public void setNodeVersion(final Long nodeVersion) {
    this.nodeVersion = nodeVersion;
  }
}
