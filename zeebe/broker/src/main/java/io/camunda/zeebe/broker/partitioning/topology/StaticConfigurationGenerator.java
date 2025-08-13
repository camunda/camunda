/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static io.camunda.zeebe.broker.system.configuration.partitioning.Scheme.FIXED;

import io.atomix.cluster.MemberId;
import io.atomix.primitive.partition.PartitionId;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributor;
import io.camunda.zeebe.broker.partitioning.distribution.FixedPartitionDistributorBuilder;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.broker.system.configuration.ClusterCfg;
import io.camunda.zeebe.broker.system.configuration.PartitioningCfg;
import io.camunda.zeebe.dynamic.config.PartitionDistributor;
import io.camunda.zeebe.dynamic.config.StaticConfiguration;
import io.camunda.zeebe.dynamic.config.state.DynamicPartitionConfig;
import io.camunda.zeebe.dynamic.config.state.ExporterState;
import io.camunda.zeebe.dynamic.config.state.ExporterState.State;
import io.camunda.zeebe.dynamic.config.state.ExportersConfig;
import io.camunda.zeebe.dynamic.config.util.RoundRobinPartitionDistributor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Utility class to determine partition distribution from the given cluster configuration. */
public final class StaticConfigurationGenerator {

  private StaticConfigurationGenerator() {}

  public static StaticConfiguration getStaticConfiguration(
      final BrokerCfg brokerCfg, final MemberId localMemberId) {
    final var clusterCfg = brokerCfg.getCluster();
    final var enablePartitionScaling =
        brokerCfg.getExperimental().getFeatures().isEnablePartitionScaling();
    final var partitioningCfg = brokerCfg.getExperimental().getPartitioning();
    final var partitionCount = clusterCfg.getPartitionsCount();
    final var replicationFactor = clusterCfg.getReplicationFactor();

    final var partitionDistributor = getPartitionDistributor(partitioningCfg);
    final var clusterMembers = getRaftGroupMembers(clusterCfg);
    final var partitionIds = getSortedPartitionIds(partitionCount);
    final var partitionConfig = generatePartitionConfig(brokerCfg);
    final var clusterId = clusterCfg.getClusterId();

    return new StaticConfiguration(
        enablePartitionScaling,
        partitionDistributor,
        clusterMembers,
        localMemberId,
        partitionIds,
        replicationFactor,
        partitionConfig,
        clusterId);
  }

  private static PartitionDistributor getPartitionDistributor(final PartitioningCfg partitionCfg) {
    return buildPartitionDistributor(partitionCfg);
  }

  private static PartitionDistributor buildPartitionDistributor(final PartitioningCfg config) {
    return config.getScheme() == FIXED
        ? buildFixedPartitionDistributor(config)
        : new RoundRobinPartitionDistributor();
  }

  private static FixedPartitionDistributor buildFixedPartitionDistributor(
      final PartitioningCfg config) {
    final var distributionBuilder =
        new FixedPartitionDistributorBuilder(PartitionManagerImpl.GROUP_NAME);

    for (final var partition : config.getFixed()) {
      for (final var node : partition.getNodes()) {
        distributionBuilder.assignMember(
            partition.getPartitionId(), node.getNodeId(), node.getPriority());
      }
    }

    return distributionBuilder.build();
  }

  private static Set<MemberId> getRaftGroupMembers(final ClusterCfg clusterCfg) {
    final int clusterSize = clusterCfg.getClusterSize();
    // node ids are always 0 to clusterSize - 1
    return IntStream.range(0, clusterSize)
        .mapToObj(nodeId -> MemberId.from(Integer.toString(nodeId)))
        .collect(Collectors.toSet());
  }

  private static List<PartitionId> getSortedPartitionIds(final int partitionCount) {
    // partition ids start from 1
    return IntStream.rangeClosed(1, partitionCount)
        .mapToObj(p -> PartitionId.from(PartitionManagerImpl.GROUP_NAME, p))
        .sorted()
        .toList();
  }

  private static DynamicPartitionConfig generatePartitionConfig(final BrokerCfg brokerCfg) {
    final Map<String, ExporterState> exporters = new HashMap<>();
    brokerCfg
        .getExporters()
        .forEach(
            (exporterId, ignore) ->
                exporters.put(exporterId, new ExporterState(0, State.ENABLED, Optional.empty())));
    return new DynamicPartitionConfig(new ExportersConfig(Map.copyOf(exporters)));
  }
}
