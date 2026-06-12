/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.broker.partitioning.RecoveryPartitionManager;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.MemberState.State;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;

final class PartitionManagerStep extends AbstractBrokerStartupStep {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;
  private static final int ERROR_CODE_ON_INCONSISTENT_TOPOLOGY = 3;

  private final String physicalTenantId;
  private TopologyManagerImpl topologyManager;

  PartitionManagerStep(final String physicalTenantId) {
    this.physicalTenantId = physicalTenantId;
  }

  @Override
  public String getName() {
    return "Partition Manager [" + physicalTenantId + "]";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var brokerInfo = brokerStartupContext.getBrokerInfo();

    topologyManager =
        new TopologyManagerImpl(
            brokerStartupContext.getClusterServices().getMembershipService(),
            brokerInfo.withPartitionGroup(physicalTenantId));
    try {
      final var partitionStartupFuture =
          brokerStartupContext
              .getActorSchedulingService()
              .submitActor(topologyManager)
              .thenApply((ignore) -> buildPartitionManager(brokerStartupContext, topologyManager))
              .thenAccept(
                  (partitionManager) -> {
                    partitionManager.start();
                    brokerStartupContext.addPartitionManager(physicalTenantId, partitionManager);
                  });

      concurrencyControl.runOnCompletion(
          partitionStartupFuture,
          (ignore, error) -> {
            if (error == null) {
              startupFuture.complete(brokerStartupContext);
            } else {
              startupFuture.completeExceptionally(error);
            }
          });
    } catch (final Exception e) {
      startupFuture.completeExceptionally(e);
    }
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var partitionManager = brokerShutdownContext.getPartitionManagers().get(physicalTenantId);
    if (partitionManager == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    if (PartitionManager.isDefaultPhysicalTenant(physicalTenantId)) {
      brokerShutdownContext.getClusterConfigurationService().removePartitionChangeExecutor();
    }

    concurrencyControl.runOnCompletion(
        partitionManager.stop().andThen(ignore -> topologyManager.closeAsync(), concurrencyControl),
        (ok, error) -> {
          brokerShutdownContext.removePartitionManager(physicalTenantId);
          if (PartitionManager.isDefaultPhysicalTenant(physicalTenantId)) {
            brokerShutdownContext
                .getClusterConfigurationService()
                .removeInconsistentConfigurationListener();
          }
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }

  private PartitionManager buildPartitionManager(
      final BrokerStartupContext brokerStartupContext, final TopologyManagerImpl topologyManager) {
    final var clusterConfiguration =
        brokerStartupContext.getClusterConfigurationService().getInitialClusterConfiguration();

    final var clusterCfg = brokerStartupContext.getBrokerConfiguration().getCluster();
    final MemberId memberId = MemberId.from(clusterCfg.getZone(), clusterCfg.getNodeId());

    if (PartitionManager.isDefaultPhysicalTenant(physicalTenantId)) {
      brokerStartupContext
          .getClusterConfigurationService()
          .registerInconsistentConfigurationListener(
              (newTopology, oldTopology) -> {
                shutdownOnInconsistentTopology(
                    memberId,
                    brokerStartupContext.getSpringBrokerBridge(),
                    newTopology,
                    oldTopology);
              });
    }

    if (State.RECOVERING == memberState.state()) {
      LOGGER.info("Partition group in recovery, starting RecoveryPartitionManager");
      return recoveryPartitionManager(brokerStartupContext, topologyManager);
    } else {
      return partitionManager(brokerStartupContext, topologyManager);
    }
  }

  PartitionManager partitionManager(
      final BrokerStartupContext brokerStartupContext, final TopologyManagerImpl topologyManager) {

    return new PartitionManagerImpl(
        physicalTenantId,
        brokerStartupContext.getConcurrencyControl(),
        brokerStartupContext.getActorSchedulingService(),
        brokerStartupContext.getBrokerConfiguration(),
        brokerStartupContext.getBrokerInfo(),
        brokerStartupContext.getClusterServices(),
        brokerStartupContext.getHealthCheckService(),
        brokerStartupContext.getDiskSpaceUsageMonitor(),
        brokerStartupContext.getPartitionListeners(),
        brokerStartupContext.getPartitionRaftListeners(),
        brokerStartupContext.getSnapshotApiRequestHandler(),
        brokerStartupContext.getExporterRepository(),
        brokerStartupContext.getGatewayBrokerTransport(),
        brokerStartupContext.getJobStreamService().jobStreamer(),
        brokerStartupContext.getClusterConfigurationService(),
        brokerStartupContext.getMeterRegistry(),
        brokerStartupContext.getBrokerClient(),
        brokerStartupContext.getRocksDbResources(),
        brokerStartupContext.getSecurityConfiguration(),
        brokerStartupContext.getSearchClientsProxy(),
        brokerStartupContext.getBrokerRequestAuthorizationConverter(),
        topologyManager);
  }

  PartitionManager recoveryPartitionManager(
      final BrokerStartupContext brokerStartupContext, final TopologyManagerImpl topologyManager) {

    return new RecoveryPartitionManager(
        physicalTenantId,
        brokerStartupContext.getBrokerConfiguration().getData().getDirectory(),
        brokerStartupContext.getConcurrencyControl(),
        brokerStartupContext.getClusterConfigurationService(),
        brokerStartupContext.getClusterServices(),
        brokerStartupContext.getActorSchedulingService(),
        brokerStartupContext.getMeterRegistry(),
        topologyManager);
  }

  private void shutdownOnInconsistentTopology(
      final MemberId memberId,
      final SpringBrokerBridge springBrokerBridge,
      final ClusterConfiguration newTopology,
      final ClusterConfiguration oldTopology) {
    LOGGER.warn(
        """
          Received a newer topology which has a different state for this broker.
          State of this broker in new topology :'{}'
          State of this broker in old topology: '{}'
          This usually happens when the topology was changed forcefully when this broker was unreachable or this broker encountered a data loss. Shutting down the broker. Please restart the broker to use the new topology.
        """,
        newTopology.getMember(memberId),
        oldTopology.getMember(memberId));
    springBrokerBridge.initiateShutdown(
        ERROR_CODE_ON_INCONSISTENT_TOPOLOGY,
        "Inconsistent cluster topology detected - topology was changed while broker was"
            + " unreachable or broker encountered data loss");
  }
}
