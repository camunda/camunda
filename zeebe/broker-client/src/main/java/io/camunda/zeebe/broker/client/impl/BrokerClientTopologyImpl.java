/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
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
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

/**
 * Represents the current state of the broker cluster topology. It includes both the live state of
 * the brokers such as the partition roles and health, as well as the configured state of the
 * cluster such as the configured clusterSize, number of partitions and replication factor. The live
 * state is aggregated from the member properties received via membership gossip from each broker.
 * The configured cluster state is updated from the global {@link
 * io.camunda.zeebe.dynamic.config.state.ClusterConfiguration}.
 */
public record BrokerClientTopologyImpl(
    Map<String, LiveClusterState> liveClusterState, ConfiguredClusterState configuredClusterState)
    implements BrokerClusterState {

  public static final int UNINITIALIZED_CLUSTER_SIZE = -1;
  public static final long NO_COMPLETED_LAST_CHANGE_ID = -1;
  public static final String NO_CLUSTER_ID = "";
  private static final long UNINITIALIZED_INCARNATION_NUMBER =
      ClusterConfiguration.INITIAL_INCARNATION_NUMBER;

  public static BrokerClientTopologyImpl uninitialized() {
    return new BrokerClientTopologyImpl(
        Map.of(),
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
  public int getLeaderForPartition(final PartitionId partition) {
    if (partition == null) {
      return UNKNOWN_NODE_ID;
    }
    final var group = liveClusterState.get(partition.group());
    if (group == null) {
      return UNKNOWN_NODE_ID;
    }

    return group.partitionLeaders.get((int) partition.id());
  }

  @Override
  public Set<Integer> getFollowersForPartition(final PartitionId partition) {
    if (partition == null) {
      return Set.of();
    }
    final var group = liveClusterState.get(partition.group());
    if (group == null) {
      return Set.of();
    }

    return group.partitionFollowers.getOrDefault(partition.id(), Set.of());
  }

  @Override
  public Set<Integer> getInactiveNodesForPartition(final PartitionId partition) {
    if (partition == null) {
      return Set.of();
    }
    final var group = liveClusterState.get(partition.group());
    if (group == null) {
      return Set.of();
    }

    return group.partitionInactiveNodes.getOrDefault(partition.id(), Set.of());
  }

  @Override
  public int getRandomBroker(final String partitionGroup) {
    final var group = liveClusterState.get(partitionGroup);
    if (group == null) {
      return UNKNOWN_NODE_ID;
    }
    if (group.brokers.isEmpty()) {
      return UNKNOWN_NODE_ID;
    } else {
      return group.brokers.get(group.randomBroker.nextInt(group.brokers.size()));
    }
  }

  @Override
  public List<Integer> getPartitions() {
    return configuredClusterState.partitionIds;
  }

  @Override
  public List<Integer> getBrokers() {
    return liveClusterState.values().stream().flatMap(group -> group.brokers.stream()).toList();
  }

  @Override
  public String getBrokerAddress(final int brokerId) {
    return liveClusterState.values().stream()
        .filter(group -> group.brokerAddresses.containsKey(brokerId))
        .map(group -> group.brokerAddresses.get(brokerId))
        .findAny()
        .orElse(null);
  }

  @Override
  public String getBrokerVersion(final int brokerId) {
    return liveClusterState.values().stream()
        .filter(group -> group.brokerAddresses.containsKey(brokerId))
        .map(group -> group.brokerAddresses.get(brokerId))
        .findAny()
        .orElse(null);
  }

  @Override
  public PartitionHealthStatus getPartitionHealth(
      final int brokerId, final PartitionId partitionId) {
    final var group = liveClusterState.get(partitionId.group());
    if (group == null) {
      return PartitionHealthStatus.UNHEALTHY;
    }

    final var brokerHealthyPartitions = group.partitionsHealthPerBroker.get(brokerId);

    if (brokerHealthyPartitions == null) {
      return PartitionHealthStatus.UNHEALTHY;
    } else {
      return brokerHealthyPartitions.getOrDefault(
          partitionId.id(), PartitionHealthStatus.UNHEALTHY);
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
      final Map<MemberId, Map<String, BrokerInfo>> values,
      final ConfiguredClusterState clusterConfiguration) {
    final var allGroups = new HashMap<String, Collection<BrokerInfo>>();

    for (final var member : values.entrySet()) {
      final var groupInMember = member.getValue();
      for (final var groupEntry : groupInMember.entrySet()) {
        final var groupName = groupEntry.getKey();
        final var group = groupEntry.getValue();
        allGroups.compute(
            groupName,
            (k, v) -> {
              if (v == null) {
                v = new ArrayList<>();
              }
              v.add(group);
              return v;
            });
      }
    }

    final Map<String, LiveClusterState> result =
        allGroups.entrySet().stream()
            .collect(
                Collectors.toMap(Entry::getKey, value -> new LiveClusterState(value.getValue())));
    return new BrokerClientTopologyImpl(result, clusterConfiguration);
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
