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
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.Collection;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public interface PartitionManager {
  String DEFAULT_GROUP_NAME = Protocol.DEFAULT_PARTITION_GROUP_NAME;

  /**
   * @return the partition with the given id or null if partition does not exist
   */
  @Nullable RaftPartition getRaftPartition(int partitionId);

  /**
   * @return the partition with the given id or null if partition does not exist
   */
  default @Nullable RaftPartition getRaftPartition(final PartitionId partitionId) {
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

  void start();

  /** Stops partitions managed by this partition manager. */
  ActorFuture<Void> stop();

  static boolean isDefaultPhysicalTenant(final String physicalTenantId) {
    return PartitionManagerImpl.DEFAULT_GROUP_NAME.equals(physicalTenantId);
  }
}
