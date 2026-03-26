/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.List;
import java.util.Set;

/**
 * Distributes partitions across cluster members. Implementations determine which members
 * participate in which partitions and who the preferred leader is.
 */
public interface PartitionDistributor {

  /**
   * Computes the full partition distribution for the cluster.
   *
   * @param clusterMembers all members in the cluster (sorted for determinism)
   * @param partitionCount total number of partitions (1-based: 1..N)
   * @param replicationFactor number of replicas per partition
   * @return set of partition metadata, one per partition
   */
  Set<PartitionMetadata> distributePartitions(
      List<MemberId> clusterMembers, int partitionCount, int replicationFactor);

  /**
   * Computes the partition distribution for a specific member. Returns only partitions that the
   * given member participates in.
   *
   * @param localMemberId the member to compute the distribution for
   * @param clusterMembers all members in the cluster (sorted for determinism)
   * @param partitionCount total number of partitions
   * @param replicationFactor number of replicas per partition
   * @return partition distribution containing only this member's partitions
   */
  PartitionDistribution distributeForMember(
      MemberId localMemberId,
      List<MemberId> clusterMembers,
      int partitionCount,
      int replicationFactor);
}
