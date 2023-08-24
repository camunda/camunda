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

  ClusterTopology generateTopology() {
    final var sortedPartitionIds = partitionIds.stream().sorted().toList();
    final Set<PartitionMetadata> partitionDistribution =
        partitionDistributor.distributePartitions(
            clusterMembers, sortedPartitionIds, replicationFactor);
    return TopologyUtil.getClusterTopologyFrom(partitionDistribution);
  }
}
