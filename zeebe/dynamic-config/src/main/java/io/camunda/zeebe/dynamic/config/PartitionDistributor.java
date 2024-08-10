/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.List;
import java.util.Set;

/**
 * Maps a list of partitions to a set of members, based on the given replication factor.
 * Implementations of this class must guarantee that the distribution is complete, that is:
 *
 * <ul>
 *   <li>The number of members a partition belongs to is equal to the replication factor
 *   <li>All partitions are replicated
 * </ul>
 *
 * It's perfectly valid for an implementation to ignore some members, as long as the above
 * guarantees are met.
 */
public interface PartitionDistributor {

  /**
   * Provides the partition distribution based on the given list of partition IDs, cluster members,
   * and the replication factor. The set of partitions returned is guaranteed to be correctly
   * replicated.
   *
   * @param clusterMembers the set of members that can own partitions
   * @param sortedPartitionIds a sorted list of partition IDs
   * @param replicationFactor the replication factor for each partition
   * @return a set of distributed partitions, each specifying which members they belong to
   */
  Set<PartitionMetadata> distributePartitions(
      Set<MemberId> clusterMembers, List<PartitionId> sortedPartitionIds, int replicationFactor);
}
