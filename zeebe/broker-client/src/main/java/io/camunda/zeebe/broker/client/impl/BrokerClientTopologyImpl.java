/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Represents the current state of the broker cluster topology. It includes both the live state of
 * the brokers such as the partition roles and health, as well as the configured state of the
 * cluster such as the configured clusterSize, number of partitions and replication factor. The live
 * state is aggregated from the member properties received via membership gossip from each broker.
 * The configured cluster state is updated from the global {@link
 * io.camunda.zeebe.dynamic.config.state.ClusterConfiguration}.
 */
public record BrokerClientTopologyImpl(
    LiveClusterState liveClusterState, ConfiguredClusterState configuredClusterState)
    implements BrokerClusterState {

  public static final int UNINITIALIZED_CLUSTER_SIZE = -1;
  public static final long NO_COMPLETED_LAST_CHANGE_ID = -1;
  public static final String NO_CLUSTER_ID = "";
  private static final long UNINITIALIZED_INCARNATION_NUMBER =
      ClusterConfiguration.INITIAL_INCARNATION_NUMBER;

  public static BrokerClientTopologyImpl uninitialized() {
    return new BrokerClientTopologyImpl(
        new LiveClusterState(Set.of()),
        new ConfiguredClusterState(
            UNINITIALIZED_CLUSTER_SIZE,
            0,
            0,
            List.of(),
            NO_COMPLETED_LAST_CHANGE_ID,
            NO_CLUSTER_ID,
            UNINITIALIZED_INCARNATION_NUMBER));
  }

  @Override
  public boolean isInitialized() {
    return configuredClusterState.clusterSize() != UNINITIALIZED_CLUSTER_SIZE;
  }

  @Override
  public int getClusterSize() {
    return configuredClusterState.clusterSize();
  }

  @Override
  public int getPartitionsCount() {
    return configuredClusterState.partitionCount();
  }

  @Override
  public int getReplicationFactor() {
    return configuredClusterState.replicationFactor();
  }

  @Override
  public String getLeaderForPartition(final int partition) {
    return liveClusterState.partitionLeaders.get(partition);
  }

  @Override
  public Set<String> getFollowersForPartition(final int partition) {
    return liveClusterState.partitionFollowers.getOrDefault(partition, Set.of());
  }

  @Override
  public Set<String> getInactiveNodesForPartition(final int partition) {
    return liveClusterState.partitionInactiveNodes.getOrDefault(partition, Set.of());
  }

  @Override
  public String getRandomBroker() {
    if (liveClusterState.brokers.isEmpty()) {
      return null;
    }
    return liveClusterState.brokers.get(
        liveClusterState.randomBroker.nextInt(liveClusterState.brokers.size()));
  }

  @Override
  public List<Integer> getPartitions() {
    return configuredClusterState.partitionIds;
  }

  @Override
  public List<String> getBrokers() {
    return liveClusterState.brokers;
  }

  @Override
  public String getBrokerAddress(final String memberId) {
    return liveClusterState.brokerAddresses.get(memberId);
  }

  @Override
  public String getBrokerVersion(final String memberId) {
    return liveClusterState.brokerVersions.get(memberId);
  }

  @Override
  public String getBrokerRegion(final String memberId) {
    return liveClusterState.brokerRegions.get(memberId);
  }

  @Override
  public PartitionHealthStatus getPartitionHealth(final String memberId, final int partitionId) {
    final var brokerHealthyPartitions = liveClusterState.partitionsHealthPerBroker.get(memberId);

    if (brokerHealthyPartitions == null) {
      return PartitionHealthStatus.UNHEALTHY;
    }
    return brokerHealthyPartitions.getOrDefault(partitionId, PartitionHealthStatus.UNHEALTHY);
  }

  @Override
  public long getLastCompletedChangeId() {
    return configuredClusterState.lastChangeId;
  }

  @Override
  public String getClusterId() {
    return configuredClusterState.clusterId;
  }

  public static BrokerClientTopologyImpl fromMemberProperties(
      final Collection<BrokerInfo> values, final ConfiguredClusterState clusterConfiguration) {
    return new BrokerClientTopologyImpl(new LiveClusterState(values), clusterConfiguration);
  }

  record ConfiguredClusterState(
      int clusterSize,
      int partitionCount,
      int replicationFactor,
      List<Integer> partitionIds,
      long lastChangeId,
      String clusterId,
      long incarnationNumber) {}

  static class LiveClusterState {
    private static final Long TERM_NONE = -1L;

    // partitionId -> memberId of current leader
    private final Map<Integer, String> partitionLeaders;
    private final Map<Integer, Long> partitionLeaderTerms;
    private final Map<Integer, Set<String>> partitionFollowers;
    private final Map<Integer, Set<String>> partitionInactiveNodes;
    // memberId -> (partitionId -> health)
    private final Map<String, Map<Integer, PartitionHealthStatus>> partitionsHealthPerBroker;
    private final Map<String, String> brokerAddresses;
    private final Map<String, String> brokerVersions;
    private final Map<String, String> brokerRegions;
    private final List<String> brokers;
    private final Random randomBroker;

    public LiveClusterState(final Collection<BrokerInfo> distributedBrokerInfos) {
      partitionLeaders = new HashMap<>();
      partitionLeaderTerms = new HashMap<>();
      partitionFollowers = new HashMap<>();
      partitionInactiveNodes = new HashMap<>();
      partitionsHealthPerBroker = new HashMap<>();
      brokerAddresses = new HashMap<>();
      brokerVersions = new HashMap<>();
      brokerRegions = new HashMap<>();
      brokers = new ArrayList<>(distributedBrokerInfos.size());
      randomBroker = new Random();

      for (final BrokerInfo brokerInfo : distributedBrokerInfos) {
        final String memberId = brokerInfo.getNodeId();
        brokers.add(memberId);
        final String clientAddress = brokerInfo.getCommandApiAddress();
        if (clientAddress != null) {
          brokerAddresses.put(memberId, clientAddress);
        }
        brokerVersions.put(memberId, brokerInfo.getVersion());
        final String region = brokerInfo.getRegion();
        if (region != null && !region.isEmpty()) {
          brokerRegions.put(memberId, region);
        }
        brokerInfo.consumePartitions(
            pId -> {}, // nothing to consume
            (leaderPartitionId, term) -> setPartitionLeader(leaderPartitionId, memberId, term),
            followerPartitionId -> addPartitionFollower(followerPartitionId, memberId),
            inactivePartitionId -> addPartitionInactive(inactivePartitionId, memberId));
        brokerInfo.consumePartitionsHealth(
            (partition, health) -> setPartitionHealthStatus(memberId, partition, health));
      }
    }

    void setPartitionLeader(final int partitionId, final String leaderId, final long term) {
      if (partitionLeaderTerms.getOrDefault(partitionId, TERM_NONE) <= term) {
        partitionLeaders.put(partitionId, leaderId);
        partitionLeaderTerms.put(partitionId, term);
        final Set<String> followers = partitionFollowers.get(partitionId);
        if (followers != null) {
          followers.remove(leaderId);
        }
        final Set<String> inactives = partitionInactiveNodes.get(partitionId);
        if (inactives != null) {
          inactives.remove(leaderId);
        }
      }
    }

    void setPartitionHealthStatus(
        final String memberId, final int partitionId, final PartitionHealthStatus status) {
      final var partitionsHealth =
          partitionsHealthPerBroker.computeIfAbsent(memberId, id -> new HashMap<>());
      partitionsHealth.put(partitionId, status);
    }

    void addPartitionFollower(final int partitionId, final String followerId) {
      partitionFollowers.computeIfAbsent(partitionId, id -> new HashSet<>()).add(followerId);
      partitionLeaders.remove(partitionId, followerId);
      final Set<String> inactives = partitionInactiveNodes.get(partitionId);
      if (inactives != null) {
        inactives.remove(followerId);
      }
    }

    void addPartitionInactive(final int partitionId, final String memberId) {
      partitionInactiveNodes.computeIfAbsent(partitionId, id -> new HashSet<>()).add(memberId);
      partitionLeaders.remove(partitionId, memberId);
      final Set<String> followers = partitionFollowers.get(partitionId);
      if (followers != null) {
        followers.remove(memberId);
      }
    }
  }
}
