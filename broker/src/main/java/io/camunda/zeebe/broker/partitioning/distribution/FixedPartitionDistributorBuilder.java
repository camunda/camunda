/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.partitioning.distribution;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Simplified builder interface to construct a {@link FixedPartitionDistributor} to reduce the risk
 * of misconfiguration when building a fixed partition distribution.
 */
public final class FixedPartitionDistributorBuilder {
  private final Map<PartitionId, Set<FixedDistributionMember>> partitions = new HashMap<>();

  private final String partitionGroupName;

  /**
   * Creates a new builder with the specific partition group name. The group name will be used to
   * generated {@link PartitionId} from raw integers. This effectively scopes the distributor to a
   * given partition group - you should not use the distributor with a different partition group.
   *
   * @param partitionGroupName the name of the partition group for which the distributor is built
   */
  public FixedPartitionDistributorBuilder(final String partitionGroupName) {
    this.partitionGroupName = partitionGroupName;
  }

  /**
   * Assigns a member, with a given priority, to the given partition. Members assigned to a
   * partition will take part in the Raft for that partition.
   *
   * <p>NOTE: this method is a convenience method which will convert the raw IDs into strongly typed
   * identifiers. See {@link #assignMember(PartitionId, MemberId, int)} for more.
   *
   * @param partitionId the ID of the partition to which the member should be assigned
   * @param nodeId the ID of the member to assign, e.g. 0, 1, 2
   * @param priority the priority of the member
   * @return this builder for chaining
   */
  public FixedPartitionDistributorBuilder assignMember(
      final int partitionId, final int nodeId, final int priority) {
    return assignMember(
        PartitionId.from(partitionGroupName, partitionId),
        MemberId.from(String.valueOf(nodeId)),
        priority);
  }

  /**
   * Assigns a member, with a given priority, to the given partition. Members assigned to a
   * partition will take part in the Raft for that partition.
   *
   * <p>There is no validation applied to the IDs, as there is no intrinsically "wrong" ID. However
   * passing the wrong ID may cause failures later on when attempting to use the distributor.
   *
   * @param partitionId the ID of the partition to which the member should be assigned
   * @param memberId the ID of the member to assign
   * @param priority the priority of the member
   * @return this builder for chaining
   */
  public FixedPartitionDistributorBuilder assignMember(
      final PartitionId partitionId, final MemberId memberId, final int priority) {
    final var members = partitions.computeIfAbsent(partitionId, ignored -> new HashSet<>());
    members.add(new FixedDistributionMember(memberId, priority));

    return this;
  }

  /** @return a distributor configured for a map of partitions to members */
  public FixedPartitionDistributor build() {
    return new FixedPartitionDistributor(partitions);
  }
}
