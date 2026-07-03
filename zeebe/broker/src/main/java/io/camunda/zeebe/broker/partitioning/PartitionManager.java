/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning;

import io.atomix.cluster.MemberId;
import io.atomix.raft.partition.RaftPartition;
import io.camunda.cluster.PartitionId;
import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.zeebe.broker.bootstrap.BrokerStartupContext;
import io.camunda.zeebe.broker.exporter.repo.ExporterLoadException;
import io.camunda.zeebe.broker.exporter.repo.ExporterRepository;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.jar.ExternalJarLoadException;
import java.util.Collection;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public interface PartitionManager {
  String DEFAULT_GROUP_NAME = PhysicalTenantIds.DEFAULT_PHYSICAL_TENANT_ID;

  /**
   * @return the partition with the given id or null if partition does not exist
   */
  @Nullable RaftPartition getRaftPartition(int partitionId);

  /**
   * @return the partition with the given id or null if partition does not exist
   */
  default @Nullable RaftPartition getRaftPartition(final PartitionId partitionId) {
    return getRaftPartition(partitionId.number());
  }

  /**
   * @return all known partitions
   */
  Collection<RaftPartition> getRaftPartitions();

  /**
   * @return all partitions with the given member
   */
  default Collection<RaftPartition> getRaftPartitionsWithMember(final MemberId memberId) {
    return getRaftPartitions().stream()
        .filter(p -> p.members().contains(memberId))
        .collect(Collectors.toList());
  }

  /**
   * @return all Zeebe partitions that are managed by this broker.
   */
  Collection<ZeebePartition> getZeebePartitions();

  /** Starts the partitions managed by this partition manager in its mode. */
  ActorFuture<Void> start();

  /** Stops partitions managed by this partition manager. */
  ActorFuture<Void> stop();

  static boolean isDefaultPhysicalTenant(final String physicalTenantId) {
    return PartitionManagerImpl.DEFAULT_GROUP_NAME.equals(physicalTenantId);
  }

  static PartitionManagerImpl createPartitionManager(
      final BrokerStartupContext brokerStartupContext,
      final String physicalTenantId,
      final TopologyManagerImpl topologyManager) {
    final var engineContext = brokerStartupContext.getPhysicalTenantEngineContext(physicalTenantId);
    final var perTenantBrokerConfig = engineContext.brokerConfig();
    // The default tenant's engine context carries the root BrokerCfg itself; reuse the shared
    // repository already loaded by the Broker (it may also contain predefined descriptors
    // injected by embedding applications and tests).
    final var perTenantExporterRepository =
        perTenantBrokerConfig == brokerStartupContext.getBrokerConfiguration()
            ? brokerStartupContext.getExporterRepository()
            : buildExporterRepository(perTenantBrokerConfig);
    return new PartitionManagerImpl(
        physicalTenantId,
        brokerStartupContext.getConcurrencyControl(),
        brokerStartupContext.getActorSchedulingService(),
        perTenantBrokerConfig,
        brokerStartupContext.getBrokerInfo(),
        brokerStartupContext.getClusterServices(),
        brokerStartupContext.getHealthCheckService(),
        brokerStartupContext.getDiskSpaceUsageMonitor(),
        brokerStartupContext.getPartitionListeners(),
        brokerStartupContext.getPartitionRaftListeners(),
        brokerStartupContext.getSnapshotApiRequestHandler(),
        perTenantExporterRepository,
        brokerStartupContext.getGatewayBrokerTransport(),
        brokerStartupContext.getJobStreamService().jobStreamer(),
        brokerStartupContext.getClusterConfigurationService(),
        brokerStartupContext.getMeterRegistry(),
        brokerStartupContext.getBrokerClient(),
        brokerStartupContext.getRocksDbResources(),
        engineContext.securityConfig(),
        brokerStartupContext.getSearchClientsProxy(),
        engineContext.authorizationConverter(),
        engineContext.featureFlags(),
        topologyManager);
  }

  private static ExporterRepository buildExporterRepository(
      final io.camunda.zeebe.broker.system.configuration.BrokerCfg brokerCfg) {
    final var repository = new ExporterRepository();
    repository.setLicenseKey(brokerCfg.getLicenseKey());
    for (final var entry : brokerCfg.getExporters().entrySet()) {
      try {
        repository.load(entry.getKey(), entry.getValue());
      } catch (final ExporterLoadException | ExternalJarLoadException e) {
        throw new IllegalStateException(
            "Failed to load per-tenant exporter with configuration: " + entry.getValue(), e);
      }
    }
    return repository;
  }

  static RecoveryPartitionManager createRecoveryPartitionManager(
      final BrokerStartupContext brokerStartupContext,
      final String physicalTenantId,
      final TopologyManagerImpl topologyManager) {

    return new RecoveryPartitionManager(
        physicalTenantId,
        brokerStartupContext.getBrokerConfiguration().getData().getDirectory(),
        brokerStartupContext.getConcurrencyControl(),
        brokerStartupContext.getClusterConfigurationService(),
        brokerStartupContext.getClusterServices().getMembershipService(),
        brokerStartupContext.getActorSchedulingService(),
        brokerStartupContext.getMeterRegistry(),
        topologyManager);
  }
}
