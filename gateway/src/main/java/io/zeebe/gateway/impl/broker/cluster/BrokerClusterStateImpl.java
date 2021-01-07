/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.broker.cluster;

import static org.agrona.collections.IntArrayList.DEFAULT_NULL_VALUE;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.agrona.collections.Int2IntHashMap;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.collections.IntArrayList;

public final class BrokerClusterStateImpl implements BrokerClusterState {

  private final Int2IntHashMap partitionLeaders;
  private final Int2ObjectHashMap<Long> partitionLeaderTerms;
  private final Int2ObjectHashMap<List<Integer>> partitionFollowers;
  private final Int2ObjectHashMap<IntArrayList> healthyPartitionsPerBroker;
  private final Int2ObjectHashMap<String> brokerAddresses;
  private final Int2ObjectHashMap<String> brokerVersions;
  private final IntArrayList brokers;
  private final IntArrayList partitions;
  private final Random randomBroker;
  private int clusterSize;
  private int partitionsCount;
  private int replicationFactor;

  public BrokerClusterStateImpl(final BrokerClusterStateImpl topology) {
    this();
    if (topology != null) {
      partitionLeaders.putAll(topology.partitionLeaders);
      partitionLeaderTerms.putAll(topology.partitionLeaderTerms);
      partitionFollowers.putAll(topology.partitionFollowers);
      healthyPartitionsPerBroker.putAll(topology.healthyPartitionsPerBroker);
      brokerAddresses.putAll(topology.brokerAddresses);
      brokerVersions.putAll(topology.brokerVersions);

      brokers.addAll(topology.brokers);
      partitions.addAll(topology.partitions);

      clusterSize = topology.clusterSize;
      partitionsCount = topology.partitionsCount;
      replicationFactor = topology.replicationFactor;
    }
  }

  public BrokerClusterStateImpl() {
    partitionLeaders = new Int2IntHashMap(NODE_ID_NULL);
    partitionLeaderTerms = new Int2ObjectHashMap<>();
    partitionFollowers = new Int2ObjectHashMap<>();
    healthyPartitionsPerBroker = new Int2ObjectHashMap<>();
    brokerAddresses = new Int2ObjectHashMap<>();
    brokerVersions = new Int2ObjectHashMap<>();
    brokers = new IntArrayList(5, NODE_ID_NULL);
    partitions = new IntArrayList(32, PARTITION_ID_NULL);
    randomBroker = new Random();
  }

  public void setPartitionLeader(final int partitionId, final int leaderId, final long term) {
    if (partitionLeaderTerms.getOrDefault(partitionId, -1L) <= term) {
      partitionLeaders.put(partitionId, leaderId);
      partitionLeaderTerms.put(partitionId, Long.valueOf(term));
      final List<Integer> followers = partitionFollowers.get(partitionId);
      if (followers != null) {
        followers.removeIf(follower -> follower == leaderId);
      }
    }
  }

  public void setPartitionHealthy(final int brokerId, final int partitionId) {
    final IntArrayList brokerHealthyPartitions = healthyPartitionsPerBroker.get(brokerId);
    if (brokerHealthyPartitions != null) {
      if (!brokerHealthyPartitions.containsInt(partitionId)) {
        brokerHealthyPartitions.add(partitionId);
      }
    } else {
      healthyPartitionsPerBroker.put(
          brokerId, new IntArrayList(new int[] {partitionId}, 1, DEFAULT_NULL_VALUE));
    }
  }

  public void setPartitionUnhealthy(final int brokerId, final int partitionId) {
    final IntArrayList brokerHealthyPartitions = healthyPartitionsPerBroker.get(brokerId);
    if (brokerHealthyPartitions != null && brokerHealthyPartitions.containsInt(partitionId)) {
      brokerHealthyPartitions.removeInt(partitionId);
    }
  }

  public void addPartitionFollower(final int partitionId, final int followerId) {
    partitionFollowers.computeIfAbsent(partitionId, ArrayList::new).add(followerId);
    partitionLeaders.remove(partitionId, followerId);
  }

  public void addPartitionIfAbsent(final int partitionId) {
    if (partitions.indexOf(partitionId) == -1) {
      partitions.addInt(partitionId);
    }
  }

  public void addBrokerIfAbsent(final int nodeId) {
    if (brokerAddresses.get(nodeId) == null) {
      brokerAddresses.put(nodeId, "");
      brokerVersions.put(nodeId, "");
      brokers.addInt(nodeId);
    }
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
          final List<Integer> followers = partitionFollowers.get(partitionId);
          if (followers != null) {
            followers.remove(Integer.valueOf(brokerId));
          }
        });
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
  public List<Integer> getFollowersForPartition(final int partition) {
    return partitionFollowers.get(partition);
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
  public boolean isPartitionHealthy(final int brokerId, final int partition) {
    final IntArrayList brokerHealthyPartitions = healthyPartitionsPerBroker.get(brokerId);
    if (brokerHealthyPartitions == null) {
      return false;
    } else {
      return brokerHealthyPartitions.containsInt(partition);
    }
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
