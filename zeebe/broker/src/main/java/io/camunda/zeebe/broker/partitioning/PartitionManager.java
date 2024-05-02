/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import java.util.Collection;
import java.util.stream.Collectors;

public interface PartitionManager {
  /**
   * @return the partition with the given id or null if partition does not exist
   */
  RaftPartition getRaftPartition(int partitionId);

  /**
   * @return the partition with the given id or null if partition does not exist
   */
  default RaftPartition getRaftPartition(final PartitionId partitionId) {
    return getRaftPartition(partitionId.id());
  }

  /**
   * @return all known partitions
   */
  Collection<RaftPartition> getRaftPartitions();

  /**
   * @return all partitions with the given member
   */
  default Collection<RaftPartition> getRaftPartitionsWithMember(final MemberId memberId) {
    return getRaftPartitions().stream()
        .filter(p -> p.members().contains(memberId))
        .collect(Collectors.toList());
  }

  /**
   * @return all Zeebe partitions that are managed by this broker.
   */
  Collection<ZeebePartition> getZeebePartitions();
}
