/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.asserts;

import io.camunda.client.api.response.BrokerInfo;
import io.camunda.client.api.response.PartitionBrokerHealth;
import io.camunda.client.api.response.PartitionInfo;
import io.camunda.client.api.response.Topology;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractObjectAssert;
import org.assertj.core.condition.VerboseCondition;

/** Convenience class to assert certain properties of a Zeebe cluster {@link Topology}. */
@SuppressWarnings("UnusedReturnValue")
public final class TopologyAssert extends AbstractObjectAssert<TopologyAssert, Topology> {

  /**
   * @param topology the actual topology to assert against
   */
  public TopologyAssert(final Topology topology) {
    super(topology, TopologyAssert.class);
  }

  /**
   * A convenience factory method that's consistent with AssertJ conventions.
   *
   * @param actual the actual topology to assert against
   * @return an instance of {@link TopologyAssert} to assert properties of the given topology
   */
  public static TopologyAssert assertThat(final Topology actual) {
    return new TopologyAssert(actual);
  }

  /**
   * Asserts that the actual topology is complete. A complete topology is one which has the expected
   * number of brokers, the expected number of partitions, the expected number of replicas per
   * partition, one leader per partition, and all partitions are healthy.
   *
   * <p>This is a convenience method that combines all other assertions from this class.
   *
   * @param clusterSize the expected number of brokers in the cluster
   * @param partitionCount the expected number of partitions in the cluster
   * @param replicationFactor the expected number of replicas per partition
   * @return itself for chaining
   */
  public TopologyAssert isComplete(
      final int clusterSize, final int partitionCount, final int replicationFactor) {
    isNotNull()
        .hasClusterSize(clusterSize)
        .hasPartitionsCount(partitionCount)
        .hasReplicationFactor(replicationFactor)
        .hasBrokersCount(clusterSize)
        .hasExpectedReplicasCount(partitionCount, replicationFactor)
        .hasLeaderForEachPartition(partitionCount)
        .isHealthy();

    return myself;
  }

  /**
   * Verifies that all partitions are healthy for all brokers.
   *
   * @return a new map assert for all partitions
   */
  public TopologyAssert isHealthy() {
    isNotNull();

    boolean hasHealthyPartition = false;
    for (final BrokerInfo broker : actual.getBrokers()) {
      for (final PartitionInfo partition : broker.getPartitions()) {
        if (partition.getHealth() != PartitionBrokerHealth.HEALTHY) {
          throw failure(
              "Expected all partitions to be healthy, but partition <%d> of broker <%d> is <%s>",
              partition.getPartitionId(), broker.getNodeId(), partition.getHealth());
        }
        hasHealthyPartition = true;
      }
    }

    if (!hasHealthyPartition) {
      throw failure(
          "Expected at least one partition to be healthy, but no healthy partitions were found in the topology.");
    }

    return this;
  }

  /**
   * Asserts that the topology reports the correct cluster size. Does not check whether the number
   * of brokers is as expected, only the topology metadata.
   *
   * @param clusterSize the expected cluster size
   * @return itself for chaining
   */
  public TopologyAssert hasClusterSize(final int clusterSize) {
    isNotNull();

    if (actual.getClusterSize() != clusterSize) {
      throw failure(
          "Expected cluster size to be <%d> but was <%d>", clusterSize, actual.getClusterSize());
    }

    return myself;
  }

  /**
   * Asserts that the topology reports the right number of partitions. Does not verify that each
   * partition is present via the brokers list, only check the topology metadata.
   *
   * @param partitionCount the expected partitions count
   * @return itself for chaining
   */
  public TopologyAssert hasPartitionsCount(final int partitionCount) {
    isNotNull();

    if (actual.getPartitionsCount() != partitionCount) {
      throw failure(
          "Expected partitions count to be <%d> but was <%d>",
          partitionCount, actual.getPartitionsCount());
    }

    return myself;
  }

  /**
   * Asserts that the topology reports the expected replication factor. Does not actually check that
   * each reported partition contains the expected number of replicas, but simply the topology's
   * metadata.
   *
   * @param replicationFactor the expected replication factor
   * @return itself for chaining
   */
  public TopologyAssert hasReplicationFactor(final int replicationFactor) {
    isNotNull();

    if (actual.getReplicationFactor() != replicationFactor) {
      throw failure(
          "Expected replication factor to be <%d> but was <%d>",
          replicationFactor, actual.getReplicationFactor());
    }

    return myself;
  }

  /**
   * Asserts that the brokers list contains the expected number of brokers.
   *
   * @param count the expected brokers count
   * @return itself for chaining
   */
  public TopologyAssert hasBrokersCount(final int count) {
    isNotNull();

    if (actual.getBrokers().size() != count) {
      throw failure(
          "Expected topology to contain <%d> brokers, but it contains <%s>",
          count, actual.getBrokers());
    }

    return myself;
  }

  /**
   * Asserts that each partition has the expected number of replicas.
   *
   * <p>NOTE: this will not work with the fixed partitioning scheme.
   *
   * @param partitionCount the partition count in the cluster
   * @param replicationFactor the replication factor
   * @return itself for chaining
   */
  public TopologyAssert hasExpectedReplicasCount(
      final int partitionCount, final int replicationFactor) {
    isNotNull();

    final Map<Integer, List<PartitionBroker>> partitionMap = buildPartitionsMap();

    if (partitionMap.size() != partitionCount) {
      throw failure(
          "Expected <%d> partitions to have <%d> replicas, but there are <%d> partitions in the topology: partitions <%s>",
          partitionCount, replicationFactor, partitionMap.size(), partitionMap.keySet());
    }

    for (final Entry<Integer, List<PartitionBroker>> partitionBrokers : partitionMap.entrySet()) {
      final int partitionId = partitionBrokers.getKey();
      final List<PartitionBroker> brokers = partitionBrokers.getValue();

      if (brokers.size() != replicationFactor) {
        throw failure(
            "Expected partition <%d> to have <%d> replicas, but it has <%d>: brokers <%s>",
            partitionId,
            replicationFactor,
            brokers.size(),
            brokers.stream().map(PartitionBroker::brokerInfo).toList());
      }
    }

    return myself;
  }

  /**
   * Asserts that each partition has exactly one leader.
   *
   * @param partitionCount the expected number of partitions
   * @return itself for chaining
   */
  public TopologyAssert hasLeaderForEachPartition(final int partitionCount) {
    isNotNull();

    final Map<Integer, List<PartitionBroker>> partitionMap = buildPartitionsMap();

    if (partitionMap.size() != partitionCount) {
      throw failure(
          "Expected <%d> partitions to have one leader, but there are <%d> partitions in the topology: partitions <%s>",
          partitionCount, partitionMap.size(), partitionMap.keySet());
    }

    for (final Entry<Integer, List<PartitionBroker>> partitionBrokers : partitionMap.entrySet()) {
      final int partitionId = partitionBrokers.getKey();
      final List<PartitionBroker> brokers = partitionBrokers.getValue();
      final List<PartitionBroker> leaders =
          partitionBrokers.getValue().stream().filter(p -> p.partitionInfo.isLeader()).toList();

      if (leaders.isEmpty()) {
        throw failure(
            "Expected partition <%d> to have a healthy leader, but it only has the following brokers: <%s>",
            partitionId, brokers.stream().map(PartitionBroker::brokerInfo).toList());
      }

      if (leaders.size() > 1) {
        throw failure(
            "Expected partition <%d> to have a healthy leader, but it has the following leaders: <%s>",
            partitionId, leaders.stream().map(PartitionBroker::brokerInfo).toList());
      }
    }

    return myself;
  }

  public TopologyAssert hasLeaderForPartition(final int partitionId, final int expectedLeaderId) {
    isNotNull();

    final Map<Integer, List<PartitionBroker>> partitionMap = buildPartitionsMap();

    has(hasPartitionId(partitionId, partitionMap));

    final var partitionBrokers = partitionMap.get(partitionId);
    final var leader =
        partitionBrokers.stream()
            .filter(p -> p.partitionInfo.isLeader())
            .map(p -> p.brokerInfo.getNodeId())
            .findFirst();
    return has(hasLeaderForPartition(partitionId, expectedLeaderId, leader));
  }

  /**
   * Fails if the topology contains one or more brokers with the given node ID. For more general
   * broker assertions, use {@link #hasBrokerSatisfying(Consumer)}.
   *
   * @param nodeId the node ID none of the brokers should have
   * @return itself for chaining
   */
  public TopologyAssert doesNotContainBroker(final int nodeId) {
    isNotNull();

    final Set<Integer> brokerIds =
        actual.getBrokers().stream().map(BrokerInfo::getNodeId).collect(Collectors.toSet());
    if (brokerIds.contains(nodeId)) {
      throw failure(
          "Expected topology not to contain broker with ID <%d>, but found the following: <%s>",
          nodeId, brokerIds);
    }

    return myself;
  }

  /**
   * Fails if the topology contains a broker that is leader for the given partition.
   *
   * @param partitionId id of the partition
   * @return itself for chaining
   */
  public TopologyAssert doesNotContainLeaderForPartition(final int partitionId) {
    isNotNull();

    final var leaderForPartition =
        actual.getBrokers().stream()
            .filter(
                b ->
                    b.getPartitions().stream()
                        .filter(PartitionInfo::isLeader)
                        .anyMatch(p -> p.getPartitionId() == partitionId))
            .map(BrokerInfo::getNodeId)
            .findAny();

    if (leaderForPartition.isPresent()) {
      {
        throw failure(
            "Expected topology not to contain leader for partition <%d>, but found broker <%d>",
            partitionId, leaderForPartition.get());
      }
    }

    return myself;
  }

  /**
   * Fails if the topology does NOT contain exactly one broker with the given node ID. For more
   * general broker assertions, use {@link #hasBrokerSatisfying(Consumer)}.
   *
   * @param nodeId the node ID of exactly one broker in the topology
   * @return itself for chaining
   */
  public TopologyAssert containsBroker(final int nodeId) {
    isNotNull();

    final Set<Integer> brokers =
        actual.getBrokers().stream().map(BrokerInfo::getNodeId).collect(Collectors.toSet());
    if (!brokers.contains(nodeId)) {
      throw failure(
          "Expected topology to contain broker with ID <%d>, but found only the following: <%s>",
          nodeId, brokers);
    }

    return myself;
  }

  /**
   * Verifies that at least one element satisfies the given requirements expressed as a {@link
   * Consumer}. This is useful to check that a group of assertions is verified by (at least) one
   * element. If the group of elements to assert is empty, the assertion will fail.
   *
   * @param condition should throw an exception on failure
   * @return itself for chaining
   */
  public TopologyAssert hasBrokerSatisfying(final Consumer<BrokerInfo> condition) {
    isNotNull();

    final List<BrokerInfo> brokers = actual.getBrokers();
    if (brokers.isEmpty()) {
      throw failure(
          "Expected topology to contain broker satisfying a condition, but there are "
              + "no brokers in the topology");
    }

    newListAssertInstance(brokers).as(info.description()).anySatisfy(condition);
    return myself;
  }

  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  private VerboseCondition<Topology> hasLeaderForPartition(
      final int partitionId, final int expectedLeaderId, final Optional<Integer> leader) {
    return VerboseCondition.verboseCondition(
        topology -> leader.isPresent() && leader.get() == expectedLeaderId,
        "a topology where the leader of partition '%d' is '%d'"
            .formatted(partitionId, expectedLeaderId),
        topology ->
            " but the actual leader is '%s'".formatted(leader.map(String::valueOf).orElse("null")));
  }

  private VerboseCondition<Topology> hasPartitionId(
      final int partitionId, final Map<Integer, List<PartitionBroker>> partitionMap) {
    return VerboseCondition.verboseCondition(
        topology -> partitionMap.containsKey(partitionId),
        "a topology with a partition with ID '%d'".formatted(partitionId),
        topology -> " but there is no partition info for this ID");
  }

  private Map<Integer, List<PartitionBroker>> buildPartitionsMap() {
    final Map<Integer, List<PartitionBroker>> partitionMap = new HashMap<>();

    for (final BrokerInfo broker : actual.getBrokers()) {
      for (final PartitionInfo partition : broker.getPartitions()) {
        final List<PartitionBroker> partitionBrokers =
            partitionMap.computeIfAbsent(partition.getPartitionId(), ignored -> new ArrayList<>());
        partitionBrokers.add(new PartitionBroker(partition, broker));
      }
    }

    return partitionMap;
  }

  private record PartitionBroker(PartitionInfo partitionInfo, BrokerInfo brokerInfo) {}
}
