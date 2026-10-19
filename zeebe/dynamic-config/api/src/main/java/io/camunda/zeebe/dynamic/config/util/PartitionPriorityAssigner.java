/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.util;

import io.atomix.cluster.MemberId;
import io.camunda.cluster.PartitionId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared Raft election priority assignment used by {@link RoundRobinPartitionDistributor} and
 * {@link AdditivePartitionReassigner} (and, in the future, a general-case reassigner supporting
 * broker/replication-factor changes), so they all produce the same failover behavior: the primary
 * gets the highest priority, and which follower gets the second-highest priority alternates across
 * partitions so that leadership loss is spread evenly across followers.
 */
final class PartitionPriorityAssigner {

  private PartitionPriorityAssigner() {}

  static Map<MemberId, Integer> assignPriorities(
      final PartitionId partitionId,
      final List<MemberId> membersForPartition,
      final MemberId primary,
      final int clusterSize,
      final int replicationFactor) {
    final Map<MemberId, Integer> priority = new HashMap<>();
    final int lowestPriority = 1;

    priority.put(primary, replicationFactor);
    // To ensure that secondary priorities are distributed evenly, we alternate the nodes for which
    // second priority is assigned. Example, clusterSize = 3 partitionCount = 12. Node 0 has highest
    // priority (=3) for partition 1,4,7 and 10. For partition 1 and 7, node 1 gets priority 2. For
    // partition 4 and 10, node 2 gets priority 2. This is done so that if node 0 dies, the
    // leadership is evenly distributed on the rest of the followers.
    if ((partitionId.number() - 1) / clusterSize % 2 == 0) {
      int nextPriority = replicationFactor - 1;
      for (final MemberId member : membersForPartition) {
        if (!member.equals(primary)) {
          priority.put(member, nextPriority);
          nextPriority--;
        }
      }
    } else {
      int nextPriority = lowestPriority;
      for (final MemberId member : membersForPartition) {
        if (!member.equals(primary)) {
          priority.put(member, nextPriority);
          nextPriority++;
        }
      }
    }
    return priority;
  }
}
