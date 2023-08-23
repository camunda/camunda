/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static io.camunda.zeebe.broker.system.configuration.partitioning.Scheme.FIXED;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.atomix.raft.partition.PartitionDistributor;
import io.atomix.raft.partition.RoundRobinPartitionDistributor;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributor;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributorBuilder;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.PartitioningCfg;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Manage how partitions are distributed among the brokers. */
public class PartitionDistributionResolver {

  public PartitionDistribution resolvePartitionDistribution(
      final PartitioningCfg partitionCfg, final ClusterCfg clusterCfg) {
    final var partitionDistributor = buildPartitionDistributor(partitionCfg);
    final var clusterMembers = getRaftGroupMembers(clusterCfg);
    final var partitionDistribution =
        partitionDistributor.distributePartitions(
            clusterMembers,
            getSortedPartitionIds(clusterCfg.getPartitionsCount()),
            clusterCfg.getReplicationFactor());
    return new PartitionDistribution(partitionDistribution);
  }

  private static PartitionDistributor buildPartitionDistributor(final PartitioningCfg config) {
    return config.getScheme() == FIXED
        ? buildFixedPartitionDistributor(config)
        : new RoundRobinPartitionDistributor();
  }

  private static FixedPartitionDistributor buildFixedPartitionDistributor(
      final PartitioningCfg config) {
    final var distributionBuilder =
        new FixedPartitionDistributorBuilder(PartitionManagerImpl.GROUP_NAME);

    for (final var partition : config.getFixed()) {
      for (final var node : partition.getNodes()) {
        distributionBuilder.assignMember(
            partition.getPartitionId(), node.getNodeId(), node.getPriority());
      }
    }

    return distributionBuilder.build();
  }

  public static Set<MemberId> getRaftGroupMembers(final ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    return IntStream.range(0, clusterSize)
        .mapToObj(nodeId -> MemberId.from(Integer.toString(nodeId)))
        .collect(Collectors.toSet());
  }

  private static List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    // partition ids start from 1
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(p -> PartitionId.from(PartitionManagerImpl.GROUP_NAME, p))
        .sorted()
        .toList();
  }

  public PartitionDistribution resolveParitionDistribution(final ClusterTopology clusterTopology) {
    // TODO: Add tests, error checks, edge cases etc.
    if (clusterTopology.isUninitialized()) {
      throw new IllegalStateException(
          "Cannot generated partition distribution from uninitialized topology");
    }
    final var tempStream =
        clusterTopology.members().entrySet().stream()
            .flatMap(
                memberEntry ->
                    memberEntry.getValue().partitions().entrySet().stream()
                        .map(
                            p ->
                                Map.entry(
                                    p.getKey(),
                                    Map.entry(memberEntry.getKey(), p.getValue().priority()))));
    final var partitionsToMembersMap =
        tempStream.collect(
            Collectors.groupingBy(
                Entry::getKey,
                Collectors.toMap(e -> e.getValue().getKey(), e -> e.getValue().getValue())));

    final var partitionDistribution =
        partitionsToMembersMap.entrySet().stream()
            .map(this::getPartitionMetadata)
            .collect(Collectors.toSet());

    return new PartitionDistribution(partitionDistribution);
  }

  private PartitionMetadata getPartitionMetadata(final Entry<Integer, Map<MemberId, Integer>> e) {
    final Map<MemberId, Integer> memberPriorities = e.getValue();
    final var optionalPrimary = memberPriorities.entrySet().stream().max(Entry.comparingByValue());
    if (optionalPrimary.isEmpty()) {
      throw new IllegalStateException("Found partition with no members");
    }
    return new PartitionMetadata(
        partitionId(e.getKey()),
        memberPriorities.keySet(),
        memberPriorities,
        optionalPrimary.get().getValue(),
        optionalPrimary.get().getKey());
  }

  private PartitionId partitionId(final Integer key) {
    return PartitionId.from(PartitionManagerImpl.GROUP_NAME, key);
  }
}
