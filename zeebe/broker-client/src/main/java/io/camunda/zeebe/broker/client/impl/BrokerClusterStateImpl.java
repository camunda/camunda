/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.client.impl;

import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

public final class BrokerClusterStateImpl implements BrokerClusterState {

  public static final int UNINITIALIZED_CLUSTER_SIZE = -1;
  public static final long NO_COMPLETED_LAST_CHANGE_ID = -1;
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
  private final IntArrayList partitions;
  private final Random randomBroker;
  private int clusterSize = UNINITIALIZED_CLUSTER_SIZE;
  private int partitionsCount;
  private int replicationFactor;
  private long lastCompletedChangeId = NO_COMPLETED_LAST_CHANGE_ID;

  public BrokerClusterStateImpl(final BrokerClusterStateImpl topology) {
    this();
    if (topology != null) {
      partitionLeaders.putAll(topology.partitionLeaders);
      partitionLeaderTerms.putAll(topology.partitionLeaderTerms);
      partitionFollowers.putAll(topology.partitionFollowers);
      partitionsHealthPerBroker.putAll(topology.partitionsHealthPerBroker);
      brokerAddresses.putAll(topology.brokerAddresses);
      brokerVersions.putAll(topology.brokerVersions);
      partitionInactiveNodes.putAll(topology.partitionInactiveNodes);

      brokers.addAll(topology.brokers);
      partitions.addAll(topology.partitions);

      clusterSize = topology.clusterSize;
      partitionsCount = topology.partitionsCount;
      replicationFactor = topology.replicationFactor;
      lastCompletedChangeId = topology.lastCompletedChangeId;
    }
  }

  public BrokerClusterStateImpl() {
    partitionLeaders = new Int2IntHashMap(NODE_ID_NULL);
    partitionLeaderTerms = new Int2ObjectHashMap<>();
    partitionFollowers = new Int2ObjectHashMap<>();
    partitionInactiveNodes = new Int2ObjectHashMap<>();
    partitionsHealthPerBroker = new Int2ObjectHashMap<>();
    brokerAddresses = new Int2ObjectHashMap<>();
    brokerVersions = new Int2ObjectHashMap<>();
    brokers = new IntArrayList(5, NODE_ID_NULL);
    partitions = new IntArrayList(32, PARTITION_ID_NULL);
    randomBroker = new Random();
  }

  public void setPartitionLeader(final int partitionId, final int leaderId, final long term) {
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

  /**
   * Syncs the partitions of the given node with the local state. Removes partitions which are not
   * present on the node anymore.
   */
  public void syncPartitions(final int nodeId, final Set<Integer> partitions) {
    partitionsHealthPerBroker
        .getOrDefault(nodeId, new Int2ObjectHashMap<>())
        .keySet()
        .removeIf(Predicate.not(partitions::contains));
    partitionLeaders
        .entrySet()
        .removeIf(entry -> entry.getValue() == nodeId && !partitions.contains(entry.getKey()));
    partitionFollowers.forEach(
        (partitionId, followers) -> {
          if (!partitions.contains(partitionId)) {
            followers.removeIf(follower -> follower == nodeId);
          }
        });
    partitionInactiveNodes.forEach(
        (partitionId, inactives) -> {
          if (!partitions.contains(partitionId)) {
            inactives.removeIf(inactive -> inactive == nodeId);
          }
        });
  }

  public void setPartitionHealthStatus(
      final int brokerId, final int partitionId, final PartitionHealthStatus status) {
    final var partitionsHealth =
        partitionsHealthPerBroker.computeIfAbsent(brokerId, integer -> new Int2ObjectHashMap<>());
    partitionsHealth.put(partitionId, status);
  }

  public void addPartitionFollower(final int partitionId, final int followerId) {
    partitionFollowers.computeIfAbsent(partitionId, HashSet::new).add(followerId);
    partitionLeaders.remove(partitionId, followerId);
    final Set<Integer> inactives = partitionInactiveNodes.get(partitionId);
    if (inactives != null) {
      inactives.remove(followerId);
    }
  }

  public void addPartitionInactive(final int partitionId, final int brokerId) {
    partitionInactiveNodes.computeIfAbsent(partitionId, HashSet::new).add(brokerId);
    partitionLeaders.remove(partitionId, brokerId);
    final Set<Integer> followers = partitionFollowers.get(partitionId);
    if (followers != null) {
      followers.remove(brokerId);
    }
  }

  public void addPartitionIfAbsent(final int partitionId) {
    if (!partitions.contains(partitionId)) {
      partitions.addInt(partitionId);
    }
  }

  public boolean addBrokerIfAbsent(final int nodeId) {
    if (brokerAddresses.get(nodeId) != null) {
      return false;
    }

    brokerAddresses.put(nodeId, "");
    brokerVersions.put(nodeId, "");
    brokers.addInt(nodeId);
    return true;
  }

  public void setBrokerAddressIfPresent(final int brokerId, final String address) {
    brokerAddresses.computeIfPresent(brokerId, (k, v) -> address);
  }

  public void setBrokerVersionIfPresent(final int brokerId, final String version) {
    brokerVersions.computeIfPresent(brokerId, (k, v) -> version);
  }

  public void removeBroker(final int brokerId) {
    brokerAddresses.remove(brokerId);
    brokerVersions.remove(brokerId);
    brokers.removeInt(brokerId);
    partitions.forEachOrderedInt(
        partitionId -> {
          if (partitionLeaders.get(partitionId) == brokerId) {
            partitionLeaders.remove(partitionId);
          }
          final Set<Integer> followers = partitionFollowers.get(partitionId);
          if (followers != null) {
            followers.remove(brokerId);
          }
          final Set<Integer> inactive = partitionInactiveNodes.get(partitionId);
          if (inactive != null) {
            inactive.remove(brokerId);
          }
        });
  }

  @Override
  public boolean isInitialized() {
    return clusterSize != UNINITIALIZED_CLUSTER_SIZE;
  }

  @Override
  public int getClusterSize() {
    return clusterSize;
  }

  public void setClusterSize(final int clusterSize) {
    this.clusterSize = clusterSize;
  }

  @Override
  public int getPartitionsCount() {
    return partitionsCount;
  }

  public void setPartitionsCount(final int partitionsCount) {
    this.partitionsCount = partitionsCount;
  }

  @Override
  public int getReplicationFactor() {
    return replicationFactor;
  }

  public void setReplicationFactor(final int replicationFactor) {
    this.replicationFactor = replicationFactor;
  }

  @Override
  public int getLeaderForPartition(final int partition) {
    return partitionLeaders.get(partition);
  }

  @Override
  public Set<Integer> getFollowersForPartition(final int partition) {
    return partitionFollowers.get(partition);
  }

  @Override
  public Set<Integer> getInactiveNodesForPartition(final int partition) {
    return partitionInactiveNodes.get(partition);
  }

  @Override
  public int getRandomBroker() {
    if (brokers.isEmpty()) {
      return UNKNOWN_NODE_ID;
    } else {
      return brokers.get(randomBroker.nextInt(brokers.size()));
    }
  }

  @Override
  public List<Integer> getPartitions() {
    return partitions;
  }

  @Override
  public List<Integer> getBrokers() {
    return brokers;
  }

  @Override
  public String getBrokerAddress(final int brokerId) {
    return brokerAddresses.get(brokerId);
  }

  @Override
  public int getPartition(final int index) {
    if (!partitions.isEmpty()) {
      return partitions.getInt(index % partitions.size());
    } else {
      return PARTITION_ID_NULL;
    }
  }

  @Override
  public String getBrokerVersion(final int brokerId) {
    return brokerVersions.get(brokerId);
  }

  @Override
  public PartitionHealthStatus getPartitionHealth(final int brokerId, final int partitionId) {
    final var brokerHealthyPartitions = partitionsHealthPerBroker.get(brokerId);

    if (brokerHealthyPartitions == null) {
      return PartitionHealthStatus.UNHEALTHY;
    } else {
      return brokerHealthyPartitions.getOrDefault(partitionId, PartitionHealthStatus.UNHEALTHY);
    }
  }

  @Override
  public long getLastCompletedChangeId() {
    return lastCompletedChangeId;
  }

  public void setLastCompletedChangeId(final long lastCompletedChangeId) {
    this.lastCompletedChangeId = lastCompletedChangeId;
  }

  @Override
  public String toString() {
    return "BrokerClusterStateImpl{"
        + "partitionLeaders="
        + partitionLeaders
        + ", brokers="
        + brokers
        + ", partitions="
        + partitions
        + ", clusterSize="
        + clusterSize
        + ", partitionsCount="
        + partitionsCount
        + ", replicationFactor="
        + replicationFactor
        + '}';
  }
}
