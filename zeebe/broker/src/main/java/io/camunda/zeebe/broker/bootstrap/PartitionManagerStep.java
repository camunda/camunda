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
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.partitioning.PartitionModeHandler;
import io.camunda.zeebe.broker.partitioning.topology.TopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import org.slf4j.Logger;

final class PartitionManagerStep extends AbstractBrokerStartupStep {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;

  private final String physicalTenantId;
  private TopologyManagerImpl topologyManager;
  private PartitionModeHandler modeHandler;

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

    // Register mode handler before starting the respective partition manager so that cluster
    // services already have the executor value set
    modeHandler = new PartitionModeHandler(brokerStartupContext, physicalTenantId, topologyManager);
    modeHandler.register();

    try {
      final var partitionStartupFuture =
          brokerStartupContext
              .getActorSchedulingService()
              .submitActor(topologyManager)
              .thenApply((ignore) -> buildPartitionManager(brokerStartupContext, topologyManager))
              .thenAccept(
                  (partitionManager) -> {
                    brokerStartupContext.addPartitionManager(physicalTenantId, partitionManager);

                    // We intentionally do not wait for start() to complete: broker startup only
                    // needs the partition manager registered. Individual partitions bootstrap
                    // asynchronously afterwards
                    partitionManager.start();
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

    concurrencyControl.runOnCompletion(
        stopModeChangeHandler()
            .andThen(ignore -> partitionManager.stop(), concurrencyControl)
            .andThen(ignore -> topologyManager.closeAsync(), concurrencyControl),
        (ok, error) -> {
          brokerShutdownContext.removePartitionManager(physicalTenantId);
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });
  }

  private PartitionManager buildPartitionManager(
      final BrokerStartupContext brokerStartupContext, final TopologyManagerImpl topologyManager) {

    final var clusterCfg = brokerStartupContext.getBrokerConfiguration().getCluster();
    final MemberId memberId = MemberId.from(clusterCfg.getZone(), clusterCfg.getNodeId());

    if (isRecovering(brokerStartupContext, memberId)) {
      LOGGER.info("Partition group in recovery, starting RecoveryPartitionManager");
      return recoveryPartitionManager(brokerStartupContext, topologyManager);
    } else {
      return partitionManager(brokerStartupContext, topologyManager);
    }
  }

  PartitionManager partitionManager(
      final BrokerStartupContext brokerStartupContext, final TopologyManagerImpl topologyManager) {
    return PartitionManager.createPartitionManager(
        brokerStartupContext, physicalTenantId, topologyManager);
  }

  PartitionManager recoveryPartitionManager(
      final BrokerStartupContext brokerStartupContext, final TopologyManagerImpl topologyManager) {
    return PartitionManager.createRecoveryPartitionManager(
        brokerStartupContext, physicalTenantId, topologyManager);
  }

  private boolean isRecovering(
      final BrokerStartupContext brokerStartupContext, final MemberId memberId) {

    final var clusterConfiguration =
        brokerStartupContext.getClusterConfigurationService().getInitialClusterConfiguration();

    final PartitionGroupConfiguration partitionGroupConfiguration =
        clusterConfiguration.partitionGroup(physicalTenantId);
    if (partitionGroupConfiguration == null) {
      return false;
    }
    final var memberState = partitionGroupConfiguration.members().get(memberId);
    return memberState != null && memberState.mode() == Mode.RECOVERING;
  }

  private ActorFuture<Void> stopModeChangeHandler() {
    return modeHandler != null ? modeHandler.closeAsync() : CompletableActorFuture.completed(null);
  }
}
