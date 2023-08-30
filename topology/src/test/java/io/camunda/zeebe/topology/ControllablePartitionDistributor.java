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
import java.util.List;
import java.util.Set;

public class ControllablePartitionDistributor implements PartitionDistributor {

  private Set<PartitionMetadata> partitions;

  public ControllablePartitionDistributor withPartitions(final Set<PartitionMetadata> partitions) {
    this.partitions = partitions;
    return this;
  }

  @Override
  public Set<PartitionMetadata> distributePartitions(
      final Set<MemberId> clusterMembers,
      final List<PartitionId> sortedPartitionIds,
      final int replicationFactor) {
    return partitions;
  }
}
