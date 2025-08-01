/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.protocol.impl.encoding.BrokerInfo;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

public record BrokerClientTopologyImpl(
    LiveClusterState liveClusterState, ConfiguredClusterState configuredClusterState)
    implements BrokerClusterState {

  public static final int UNINITIALIZED_CLUSTER_SIZE = -1;
  public static final long NO_COMPLETED_LAST_CHANGE_ID = -1;

  public static BrokerClientTopologyImpl uninitialized() {
    return new BrokerClientTopologyImpl(
        new LiveClusterState(Set.of()),
        new ConfiguredClusterState(
            UNINITIALIZED_CLUSTER_SIZE, 0, 0, List.of(), NO_COMPLETED_LAST_CHANGE_ID));
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
  public int getLeaderForPartition(final int partition) {
    return liveClusterState.partitionLeaders.get(partition);
  }

  @Override
  public Set<Integer> getFollowersForPartition(final int partition) {
    return liveClusterState.partitionFollowers.getOrDefault(partition, Set.of());
  }

  @Override
  public Set<Integer> getInactiveNodesForPartition(final int partition) {
    return liveClusterState.partitionInactiveNodes.getOrDefault(partition, Set.of());
  }

  @Override
  public int getRandomBroker() {
    if (liveClusterState.brokers.isEmpty()) {
      return UNKNOWN_NODE_ID;
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
  public List<Integer> getBrokers() {
    return liveClusterState.brokers;
  }

  @Override
  public String getBrokerAddress(final int brokerId) {
    return liveClusterState.brokerAddresses.get(brokerId);
  }

  @Override
  public int getPartition(final int index) {
    final List<Integer> partitions = getPartitions();
    if (!partitions.isEmpty()) {
      return partitions.get(index % partitions.size());
    } else {
      return PARTITION_ID_NULL;
    }
  }

  @Override
  public String getBrokerVersion(final int brokerId) {
    return liveClusterState.brokerVersions.get(brokerId);
  }

  @Override
  public PartitionHealthStatus getPartitionHealth(final int brokerId, final int partitionId) {
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

  public static BrokerClientTopologyImpl fromMemberProperties(
      final Collection<BrokerInfo> values, final ConfiguredClusterState clusterConfiguration) {
    return new BrokerClientTopologyImpl(new LiveClusterState(values), clusterConfiguration);
  }

  public record ConfiguredClusterState(
      int clusterSize,
      int partitionCount,
      int replicationFactor,
      List<Integer> partitionIds,
      long lastChangeId) {}

  static class LiveClusterState {
    private static final Long TERM_NONE = -1L;
    private final Int2IntHashMap partitionLeaders;
    private final Int2ObjectHashMap<Long> partitionLeaderTerms;
    private final Int2ObjectHashMap<Set<Integer>> partitionFollowers;
    private final Int2ObjectHashMap<Set<Integer>> partitionInactiveNodes;
    private final Int2ObjectHashMap<Int2ObjectHashMap<PartitionHealthStatus>>
        partitionsHealthPerBroker;
    private final Int2ObjectHashMap<String> brokerAddresses;
    private final Int2ObjectHashMap<String> brokerVersions;
    private final IntArrayList brokers;
    private final Random randomBroker;

    public LiveClusterState(final Collection<BrokerInfo> distributedBrokerInfos) {
      partitionLeaders = new Int2IntHashMap(NODE_ID_NULL);
      partitionLeaderTerms = new Int2ObjectHashMap<>();
      partitionFollowers = new Int2ObjectHashMap<>();
      partitionInactiveNodes = new Int2ObjectHashMap<>();
      partitionsHealthPerBroker = new Int2ObjectHashMap<>();
      brokerAddresses = new Int2ObjectHashMap<>();
      brokerVersions = new Int2ObjectHashMap<>();
      brokers = new IntArrayList(5, NODE_ID_NULL);
      randomBroker = new Random();

      distributedBrokerInfos.forEach(
          brokerInfo -> {
            final int nodeId = brokerInfo.getNodeId();
            brokers.add(brokerInfo.getNodeId());
            final String clientAddress = brokerInfo.getCommandApiAddress();
            if (clientAddress != null) {
              brokerAddresses.put(nodeId, brokerInfo.getCommandApiAddress());
            }
            brokerVersions.put(nodeId, brokerInfo.getVersion());
            brokerInfo.consumePartitions(
                pId -> {}, // nothing to consume
                (leaderPartitionId, term) -> setPartitionLeader(leaderPartitionId, nodeId, term),
                followerPartitionId -> addPartitionFollower(followerPartitionId, nodeId),
                inactivePartitionId -> addPartitionInactive(inactivePartitionId, nodeId));
            brokerInfo.consumePartitionsHealth(
                (partition, health) -> setPartitionHealthStatus(nodeId, partition, health));
          });
    }

    void setPartitionLeader(final int partitionId, final int leaderId, final long term) {
      if (partitionLeaderTerms.getOrDefault(partitionId, TERM_NONE) <= term) {
        partitionLeaders.put(partitionId, leaderId);
        partitionLeaderTerms.put(partitionId, Long.valueOf(term));
        final Set<Integer> followers = partitionFollowers.get(partitionId);
        if (followers != null) {
          followers.removeIf(follower -> follower == leaderId);
        }
        final Set<Integer> inactives = partitionInactiveNodes.get(partitionId);
        if (inactives != null) {
          inactives.removeIf(inactive -> inactive == leaderId);
        }
      }
    }

    void setPartitionHealthStatus(
        final int brokerId, final int partitionId, final PartitionHealthStatus status) {
      final var partitionsHealth =
          partitionsHealthPerBroker.computeIfAbsent(brokerId, integer -> new Int2ObjectHashMap<>());
      partitionsHealth.put(partitionId, status);
    }

    void addPartitionFollower(final int partitionId, final int followerId) {
      partitionFollowers.computeIfAbsent(partitionId, HashSet::new).add(followerId);
      partitionLeaders.remove(partitionId, followerId);
      final Set<Integer> inactives = partitionInactiveNodes.get(partitionId);
      if (inactives != null) {
        inactives.remove(followerId);
      }
    }

    void addPartitionInactive(final int partitionId, final int brokerId) {
      partitionInactiveNodes.computeIfAbsent(partitionId, HashSet::new).add(brokerId);
      partitionLeaders.remove(partitionId, brokerId);
      final Set<Integer> followers = partitionFollowers.get(partitionId);
      if (followers != null) {
        followers.remove(brokerId);
      }
    }
  }
}
