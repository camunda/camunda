/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.topology;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.zeebe.topology.state.ClusterTopology;
import io.camunda.zeebe.topology.util.TopologyUtil;
import java.util.List;
import java.util.Set;

public record StaticConfiguration(
    PartitionDistributor partitionDistributor,
    Set<MemberId> clusterMembers,
    MemberId localMemberId,
    List<PartitionId> partitionIds,
    int replicationFactor) {

  public ClusterTopology generateTopology() {
    final Set<PartitionMetadata> partitionDistribution = generatePartitionDistribution();
    return TopologyUtil.getClusterTopologyFrom(partitionDistribution);
  }

  public Set<PartitionMetadata> generatePartitionDistribution() {
    final var sortedPartitionIds = partitionIds.stream().sorted().toList();
    return partitionDistributor.distributePartitions(
        clusterMembers, sortedPartitionIds, replicationFactor);
  }
}
