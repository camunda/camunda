/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionMetadata;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.util.ConfigurationUtil;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public record StaticConfiguration(
    PartitionDistributor partitionDistributor,
    Set<MemberId> clusterMembers,
    MemberId localMemberId,
    List<PartitionId> partitionIds,
    int replicationFactor,
    Map<String, DynamicPartitionConfig> partitionConfigPerPhysicalTenant,
    @Nullable String clusterId) {

  public int partitionCount() {
    return partitionIds.size();
  }

  public ClusterConfiguration generateTopology() {
    final Set<PartitionMetadata> partitionDistribution = generatePartitionDistribution();
    // Legacy model can only work with default partition group.
    final var defaultPartitions =
        partitionDistribution.stream()
            .filter(p -> p.id().group().equals(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID))
            .collect(Collectors.toSet());
    return ConfigurationUtil.getClusterConfigFrom(
        defaultPartitions,
        partitionConfigPerPhysicalTenant.get(PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID),
        clusterId);
  }

  /**
   * Generates the multi-partition-group counterpart of {@link #generateTopology()}, used by the new
   * configuration model. Partitions are split into groups by their {@link
   * io.camunda.cluster.PartitionId#group()}; every configured cluster member is visible in the
   * result regardless of partition assignment. See {@link
   * ConfigurationUtil#getCurrentClusterConfigurationFrom} for details.
   */
  public CurrentClusterConfiguration generateCurrentClusterConfiguration() {
    final Set<PartitionMetadata> partitionDistribution = generatePartitionDistribution();
    return ConfigurationUtil.getCurrentClusterConfigurationFrom(
        clusterMembers, partitionDistribution, partitionConfigPerPhysicalTenant, clusterId);
  }

  public Set<PartitionMetadata> generatePartitionDistribution() {
    final var sortedPartitionIds = partitionIds.stream().sorted().toList();
    return partitionDistributor.distributePartitions(
        clusterMembers, sortedPartitionIds, replicationFactor);
  }
}
