/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.atomix.primitive.partition.PartitionMetadata;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PartitionDistribution describes how partitions are distributed across broker. It doesn't keep
 * track of the current leader or followers.
 */
public record PartitionDistribution(Set<PartitionMetadata> partitions) {

  public static final PartitionDistribution NO_PARTITIONS = new PartitionDistribution(Set.of());

  /**
   * Returns a copy of this distribution where every partition's {@link PartitionId} is rewritten to
   * use {@code newGroupName}. Members, priorities, target priority, and primary are preserved
   * unchanged. Used by the physical-tenants (#50509) feature to derive a partition distribution for
   * a non-default physical tenant from the default tenant's distribution.
   */
  public PartitionDistribution withGroupName(final String newGroupName) {
    return new PartitionDistribution(
        partitions.stream()
            .map(pm -> clonePartitionMetadataWithGroup(pm, newGroupName))
            .collect(Collectors.toSet()));
  }

  private static PartitionMetadata clonePartitionMetadataWithGroup(
      final PartitionMetadata pm, final String newGroupName) {
    final Map<MemberId, Integer> priority =
        pm.members().stream().collect(Collectors.toMap(m -> m, pm::getPriority));
    return new PartitionMetadata(
        PartitionId.from(newGroupName, pm.id().id()),
        new HashSet<>(pm.members()),
        priority,
        pm.getTargetPriority(),
        pm.getPrimary().orElse(null));
  }
}
