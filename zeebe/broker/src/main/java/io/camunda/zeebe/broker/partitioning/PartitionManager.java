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
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.broker.system.partitions.ZeebePartition;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Objects;
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
    final var physicalTenantContext =
        brokerStartupContext.getPhysicalTenantContext(physicalTenantId);

    final var jobStreamService =
        Objects.requireNonNull(
            brokerStartupContext.getJobStreamService(physicalTenantId),
            "JobStreamService not initialized for tenant " + physicalTenantId);

    // Combine global listeners with this tenant's error handler so each handler only ever
    // sees its own group's partitions, resolving the bare-int partition-id aliasing bug.
    final var partitionListeners = new ArrayList<>(brokerStartupContext.getPartitionListeners());
    partitionListeners.add(jobStreamService.errorHandlerService());

    return new PartitionManagerImpl(
        physicalTenantId,
        brokerStartupContext.getConcurrencyControl(),
        brokerStartupContext.getActorSchedulingService(),
        // Use the physicalTenantConfig which contains both the shared broker-wide properties and
        // the properties overridden for the physical tenant.
        physicalTenantContext.config(),
        brokerStartupContext.getBrokerInfo(),
        brokerStartupContext.getClusterServices(),
        brokerStartupContext.getHealthCheckService(),
        brokerStartupContext.getDiskSpaceUsageMonitor(),
        partitionListeners,
        brokerStartupContext.getPartitionRaftListeners(),
        brokerStartupContext.getSnapshotApiRequestHandler(),
        physicalTenantContext.exporterRepository(),
        brokerStartupContext.getGatewayBrokerTransport(),
        jobStreamService.jobStreamer(),
        brokerStartupContext.getClusterConfigurationService(),
        brokerStartupContext.getMeterRegistry(),
        brokerStartupContext.getBrokerClient(),
        brokerStartupContext.getRocksDbResources(),
        physicalTenantContext.securityConfig(),
        brokerStartupContext.getSearchClientsProxy(),
        physicalTenantContext.authorizationConverter(),
        physicalTenantContext.featureFlags(),
        topologyManager);
  }

  static RecoveryPartitionManager createRecoveryPartitionManager(
      final BrokerStartupContext brokerStartupContext,
      final String physicalTenantId,
      final TopologyManagerImpl topologyManager) {

    return new RecoveryPartitionManager(
        physicalTenantId,
        brokerStartupContext.getPhysicalTenantContext(physicalTenantId).config(),
        brokerStartupContext.getBrokerInfo(),
        brokerStartupContext.getConcurrencyControl(),
        brokerStartupContext.getClusterConfigurationService(),
        brokerStartupContext.getClusterServices().getMembershipService(),
        brokerStartupContext.getActorSchedulingService(),
        brokerStartupContext.getMeterRegistry(),
        brokerStartupContext.getGatewayBrokerTransport(),
        brokerStartupContext.getExportedPositionSupplier(),
        topologyManager);
  }
}
