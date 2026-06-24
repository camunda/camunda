/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.api.util;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;

import io.atomix.cluster.BrokerMemberId;
import io.camunda.zeebe.broker.client.api.BrokerClusterState;
import io.camunda.zeebe.protocol.record.PartitionHealthStatus;
import io.camunda.zeebe.util.collection.Tuple;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class TestBrokerClusterState implements BrokerClusterState {

  private final Map<BrokerMemberId, String> brokerAddresses = new HashMap<>();
  private final Map<Integer, Tuple<@Nullable BrokerMemberId, Long>> partitionLeaders =
      new HashMap<>();
  private final Set<Integer> partitions = new HashSet<>();
  private final Map<Tuple<BrokerMemberId, Integer>, PartitionHealthStatus>
      brokerPartitionHealthStatus = new HashMap<>();
  private final Map<Integer, Set<BrokerMemberId>> inactivePartitionsToNodeIds = new HashMap<>();
  private final Map<Integer, Set<BrokerMemberId>> followerPartitionToNodeIds = new HashMap<>();
  private String clusterId = "";

  public TestBrokerClusterState() {
    this(0);
  }

  public TestBrokerClusterState(final int partitionCount) {
    IntStream.range(START_PARTITION_ID, START_PARTITION_ID + partitionCount)
        .forEach(
            pId -> {
              partitions.add(pId);
              partitionLeaders.put(pId, Tuple.of(null, 0L));
              inactivePartitionsToNodeIds.put(pId, new HashSet<>());
              followerPartitionToNodeIds.put(pId, new HashSet<>());
            });
  }

  @Override
  public boolean isInitialized() {
    return true;
  }

  @Override
  public int getClusterSize() {
    return brokerAddresses.size();
  }

  @Override
  public int getPartitionsCount() {
    return partitions.size();
  }

  @Override
  public int getReplicationFactor() {
    return 1;
  }

  @Override
  public @Nullable BrokerMemberId getLeaderForPartition(final int partition) {
    if (!partitionLeaders.containsKey(partition)) {
      return null;
    }
    return partitionLeaders.get(partition).getLeft();
  }

  @Override
  public Set<BrokerMemberId> getFollowersForPartition(final int partition) {
    return new HashSet<>(followerPartitionToNodeIds.getOrDefault(partition, Set.of()));
  }

  @Override
  public Set<BrokerMemberId> getInactiveNodesForPartition(final int partition) {
    return new HashSet<>(inactivePartitionsToNodeIds.getOrDefault(partition, Set.of()));
  }

  @Override
  public @Nullable BrokerMemberId getRandomBroker() {
    throw new UnsupportedOperationException();
  }

  @Override
  public List<Integer> getPartitions() {
    return partitions.stream().toList();
  }

  @Override
  public List<BrokerMemberId> getBrokers() {
    return brokerAddresses.keySet().stream().toList();
  }

  @Override
  public String getBrokerAddress(final BrokerMemberId brokerId) {
    return brokerAddresses.get(brokerId);
  }

  @Override
  public String getBrokerVersion(final BrokerMemberId brokerId) {
    return "1.0.0"; // Default version for testing purposes;
  }

  @Override
  public PartitionHealthStatus getPartitionHealth(
      final BrokerMemberId brokerId, final int partition) {
    return brokerPartitionHealthStatus.getOrDefault(
        Tuple.of(brokerId, partition), PartitionHealthStatus.UNHEALTHY);
  }

  @Override
  public long getLastCompletedChangeId() {
    throw new UnsupportedOperationException();
  }

  @Override
  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public void addBroker(final BrokerMemberId nodeId, final String address) {
    brokerAddresses.put(nodeId, address);
  }

  public void setPartitionLeader(
      final int partitionId, final BrokerMemberId leaderId, final long term) {
    partitionLeaders.put(partitionId, Tuple.of(leaderId, term));
  }

  public void addPartition(final int partitionId) {
    partitions.add(partitionId);
  }

  public void setPartitionHealthStatus(
      final BrokerMemberId nodeId,
      final int partitionId,
      final PartitionHealthStatus partitionHealthStatus) {
    brokerPartitionHealthStatus.put(Tuple.of(nodeId, partitionId), partitionHealthStatus);
  }

  public void addPartitionInactive(final int partitionId, final BrokerMemberId nodeId) {
    addPartition(partitionId);
    inactivePartitionsToNodeIds.compute(
        partitionId,
        (key, value) -> {
          if (value == null) {
            value = new HashSet<>();
          }
          value.add(nodeId);
          return value;
        });
    followerPartitionToNodeIds.compute(
        partitionId,
        (key, value) -> {
          if (value == null) {
            return null;
          }
          value.remove(nodeId); // Ensure the node is not counted as a follower
          return value;
        });

    if (partitionLeaders.containsKey(partitionId)) {
      partitionLeaders.remove(partitionId); // Remove leader if it was the inactive node
    }
  }

  public void addFollowerPartition(final int partitionId, final BrokerMemberId nodeId) {
    followerPartitionToNodeIds.compute(
        partitionId,
        (key, value) -> {
          if (value == null) {
            value = new HashSet<>();
          }
          value.add(nodeId);
          return value;
        });
  }
}
