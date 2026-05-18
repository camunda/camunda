/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.agrona.collections.Int2ObjectHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Represents the current state of the broker cluster topology. It includes both the live state of
 * the brokers such as the partition roles and health, as well as the configured state of the
 * cluster such as the configured clusterSize, number of partitions and replication factor. The live
 * state is aggregated from the member properties received via membership gossip from each broker.
 * The configured cluster state is updated from the global {@link
 * io.camunda.zeebe.dynamic.config.state.ClusterConfiguration}.
 */
@NullMarked
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
  public @Nullable BrokerMemberId getLeaderForPartition(final int partition) {
    return liveClusterState.partitionLeaders.get(partition);
  }

  @Override
  public Set<BrokerMemberId> getFollowersForPartition(final int partition) {
    return liveClusterState.partitionFollowers.getOrDefault(partition, Set.of());
  }

  @Override
  public Set<BrokerMemberId> getInactiveNodesForPartition(final int partition) {
    return liveClusterState.partitionInactiveNodes.getOrDefault(partition, Set.of());
  }

  @Override
  public @Nullable BrokerMemberId getRandomBroker() {
    if (liveClusterState.brokers.isEmpty()) {
      return null;
    } else {
      return liveClusterState.brokers.get(
          liveClusterState.randomBroker.nextInt(liveClusterState.brokers.size()));
    }
  }

  @Override
  public List<Integer> getPartitions() {
    return configuredClusterState.partitionIds;
  }

  @Override
  public List<BrokerMemberId> getBrokers() {
    return liveClusterState.brokers;
  }

  @Override
  public @Nullable String getBrokerAddress(final BrokerMemberId brokerId) {
    return liveClusterState.brokerAddresses.get(brokerId);
  }

  @Override
  public @Nullable String getBrokerVersion(final BrokerMemberId brokerId) {
    return liveClusterState.brokerVersions.get(brokerId);
  }

  @Override
  public PartitionHealthStatus getPartitionHealth(
      final BrokerMemberId brokerId, final int partitionId) {
    final var brokerHealthyPartitions = liveClusterState.partitionsHealthPerBroker.get(brokerId);

    if (brokerHealthyPartitions == null) {
      return PartitionHealthStatus.UNHEALTHY;
    } else {
      return brokerHealthyPartitions.getOrDefault(partitionId, PartitionHealthStatus.UNHEALTHY);
    }
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
    private final Int2ObjectHashMap<BrokerMemberId> partitionLeaders;
    private final Int2ObjectHashMap<Long> partitionLeaderTerms;
    private final Int2ObjectHashMap<Set<BrokerMemberId>> partitionFollowers;
    private final Int2ObjectHashMap<Set<BrokerMemberId>> partitionInactiveNodes;
    private final HashMap<BrokerMemberId, Int2ObjectHashMap<PartitionHealthStatus>>
        partitionsHealthPerBroker;
    private final HashMap<BrokerMemberId, String> brokerAddresses;
    private final HashMap<BrokerMemberId, String> brokerVersions;
    private final List<BrokerMemberId> brokers;
    private final Random randomBroker;

    public LiveClusterState(final Collection<BrokerInfo> distributedBrokerInfos) {
      partitionLeaders = new Int2ObjectHashMap<>();
      partitionLeaderTerms = new Int2ObjectHashMap<>();
      partitionFollowers = new Int2ObjectHashMap<>();
      partitionInactiveNodes = new Int2ObjectHashMap<>();
      partitionsHealthPerBroker = new HashMap<>();
      brokerAddresses = new HashMap<>();
      brokerVersions = new HashMap<>();
      brokers = new ArrayList<>(5);
      randomBroker = new Random();

      distributedBrokerInfos.forEach(
          brokerInfo -> {
            final var memberId = BrokerMemberId.from(brokerInfo.getZone(), brokerInfo.getNodeId());
            brokers.add(memberId);
            final String clientAddress = brokerInfo.getCommandApiAddress();
            if (clientAddress != null) {
              brokerAddresses.put(memberId, brokerInfo.getCommandApiAddress());
            }
            brokerVersions.put(memberId, brokerInfo.getVersion());
            brokerInfo.consumePartitions(
                pId -> {}, // nothing to consume
                (leaderPartitionId, term) -> setPartitionLeader(leaderPartitionId, memberId, term),
                followerPartitionId -> addPartitionFollower(followerPartitionId, memberId),
                inactivePartitionId -> addPartitionInactive(inactivePartitionId, memberId));
            brokerInfo.consumePartitionsHealth(
                (partition, health) -> setPartitionHealthStatus(memberId, partition, health));
          });
    }

    void setPartitionLeader(final int partitionId, final BrokerMemberId leaderId, final long term) {
      if (partitionLeaderTerms.getOrDefault(partitionId, TERM_NONE) <= term) {
        partitionLeaders.put(partitionId, leaderId);
        partitionLeaderTerms.put(partitionId, Long.valueOf(term));
        final var followers = partitionFollowers.get(partitionId);
        if (followers != null) {
          followers.removeIf(follower -> follower.equals(leaderId));
        }
        final var inactives = partitionInactiveNodes.get(partitionId);
        if (inactives != null) {
          inactives.removeIf(inactive -> inactive.equals(leaderId));
        }
      }
    }

    void setPartitionHealthStatus(
        final BrokerMemberId brokerId, final int partitionId, final PartitionHealthStatus status) {
      final var partitionsHealth =
          partitionsHealthPerBroker.computeIfAbsent(brokerId, integer -> new Int2ObjectHashMap<>());
      partitionsHealth.put(partitionId, status);
    }

    void addPartitionFollower(final int partitionId, final BrokerMemberId followerId) {
      partitionFollowers.computeIfAbsent(partitionId, HashSet::new).add(followerId);
      partitionLeaders.remove(partitionId, followerId);
      final var inactives = partitionInactiveNodes.get(partitionId);
      if (inactives != null) {
        inactives.remove(followerId);
      }
    }

    void addPartitionInactive(final int partitionId, final BrokerMemberId brokerId) {
      partitionInactiveNodes.computeIfAbsent(partitionId, HashSet::new).add(brokerId);
      partitionLeaders.remove(partitionId, brokerId);
      final var followers = partitionFollowers.get(partitionId);
      if (followers != null) {
        followers.remove(brokerId);
      }
    }
  }
}
