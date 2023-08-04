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
import io.atomix.raft.partition.PartitionDistributor;
import io.atomix.raft.partition.RoundRobinPartitionDistributor;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributor;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributorBuilder;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.PartitioningCfg;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Manage how partitions are distributed among the brokers.
 *
 * <p>Note: Do not confuse it with the {@link TopologyManager}. {@link ClusterTopologyManager}
 * doesn't keep track of the current leader and followers.
 */
public class ClusterTopologyManager {

  public PartitionDistribution resolveTopology(
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

  private static Set<MemberId> getRaftGroupMembers(final ClusterCfg clusterCfg) {
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
}
