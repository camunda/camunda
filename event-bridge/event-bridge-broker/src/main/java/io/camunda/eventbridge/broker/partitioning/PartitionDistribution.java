/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.partitioning;

import io.atomix.cluster.MemberId;
import java.util.Map;
import java.util.Set;

/**
 * Describes which partitions a specific broker owns and which members participate in each.
 *
 * @param members partition ID → member set, ordered by priority (first = preferred leader)
 */
public record PartitionDistribution(Map<Integer, Set<MemberId>> members) {

  public Set<Integer> getPartitionIds() {
    return members.keySet();
  }

  public Set<MemberId> getMembersForPartition(final int partitionId) {
    return members.get(partitionId);
  }
}
