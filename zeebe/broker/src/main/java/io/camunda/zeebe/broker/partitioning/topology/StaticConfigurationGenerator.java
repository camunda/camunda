/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.topology;

import static io.camunda.zeebe.broker.system.configuration.partitioning.Scheme.FIXED;
import static io.camunda.zeebe.broker.system.configuration.partitioning.Scheme.REGION_AWARE;

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
import io.camunda.zeebe.dynamic.config.state.ExportingConfig;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.util.RegionAwarePartitionDistributor;
import io.camunda.zeebe.dynamic.config.util.RegionAwarePartitionDistributor.RegionSpec;
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
    final var partitioningCfg = brokerCfg.getExperimental().getPartitioning();
    final var partitionCount = clusterCfg.getPartitionsCount();
    final var replicationFactor = clusterCfg.getReplicationFactor();

    final var partitionDistributor = getPartitionDistributor(partitioningCfg);
    final var clusterMembers = getRaftGroupMembers(clusterCfg, partitioningCfg);
    final var partitionIds = getSortedPartitionIds(partitionCount);
    final var partitionConfig = generatePartitionConfig(brokerCfg);
    final var clusterId = clusterCfg.getClusterId();

    return new StaticConfiguration(
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
    return switch (config.getScheme()) {
      case FIXED -> buildFixedPartitionDistributor(config);
      case REGION_AWARE -> buildRegionAwarePartitionDistributor(config);
      default -> new RoundRobinPartitionDistributor();
    };
  }

  private static RegionAwarePartitionDistributor buildRegionAwarePartitionDistributor(
      final PartitioningCfg config) {
    final var regions = config.getRegionAware().getRegions();
    final List<RegionSpec> specs =
        regions.entrySet().stream()
            .map(
                e -> {
                  final String regionName = e.getKey();
                  final var regionCfg = e.getValue();
                  final List<MemberId> brokers =
                      IntStream.range(0, regionCfg.getNumberOfBrokers())
                          .mapToObj(localId -> MemberId.from(regionName + "-" + localId))
                          .toList();
                  return new RegionSpec(
                      regionName,
                      regionCfg.getNumberOfReplicas(),
                      regionCfg.getPriority(),
                      brokers);
                })
            .toList();
    return new RegionAwarePartitionDistributor(specs);
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

  private static Set<MemberId> getRaftGroupMembers(
      final ClusterCfg clusterCfg, final PartitioningCfg partitioningCfg) {
    if (partitioningCfg.getScheme() == REGION_AWARE) {
      return getRegionAwareRaftGroupMembers(partitioningCfg);
    }
    // Legacy path: node ids are always 0 to clusterSize - 1
    final int clusterSize = clusterCfg.getClusterSize();
    return IntStream.range(0, clusterSize)
        .mapToObj(nodeId -> MemberId.from(Integer.toString(nodeId)))
        .collect(Collectors.toSet());
  }

  /**
   * Generates composite {@link MemberId}s for all brokers in a region-aware cluster. The format is
   * {@code "region-localNodeId"} (e.g. {@code "us-east1-0"}). Local node IDs are 0-indexed within
   * each region, derived from the {@link
   * io.camunda.zeebe.broker.system.configuration.partitioning.RegionAwareCfg} configuration in the
   * same insertion order used by each broker to determine its own member ID.
   *
   * <p>Each broker's global node ID is mapped to a local ID by subtracting the cumulative broker
   * count of all preceding regions. {@link
   * io.camunda.zeebe.broker.clustering.ClusterConfigFactory} performs the same offset computation
   * to produce a consistent member ID.
   */
  private static Set<MemberId> getRegionAwareRaftGroupMembers(
      final PartitioningCfg partitioningCfg) {
    return partitioningCfg.getRegionAware().getRegions().entrySet().stream()
        .flatMap(
            e ->
                IntStream.range(0, e.getValue().getNumberOfBrokers())
                    .mapToObj(localId -> MemberId.from(e.getKey() + "-" + localId)))
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
    return new DynamicPartitionConfig(
        new ExportingConfig(ExportingState.EXPORTING, Map.copyOf(exporters)));
  }
}
