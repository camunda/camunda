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
import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import org.slf4j.Logger;

final class PartitionManagerStep extends AbstractBrokerStartupStep {
  private static final Logger LOGGER = Loggers.SYSTEM_LOGGER;
  private static final int ERROR_CODE_ON_INCONSISTENT_TOPOLOGY = 3;

  @Override
  public String getName() {
    return "Partition Manager";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var partitionManager =
        new PartitionManagerImpl(
            brokerStartupContext.getConcurrencyControl(),
            brokerStartupContext.getActorSchedulingService(),
            brokerStartupContext.getBrokerConfiguration(),
            brokerStartupContext.getBrokerInfo(),
            brokerStartupContext.getClusterServices(),
            brokerStartupContext.getHealthCheckService(),
            brokerStartupContext.getDiskSpaceUsageMonitor(),
            brokerStartupContext.getPartitionListeners(),
            brokerStartupContext.getPartitionRaftListeners(),
            brokerStartupContext.getCommandApiService(),
            brokerStartupContext.getExporterRepository(),
            brokerStartupContext.getGatewayBrokerTransport(),
            brokerStartupContext.getJobStreamService().jobStreamer(),
            brokerStartupContext.getClusterConfigurationService(),
            brokerStartupContext.getMeterRegistry(),
            brokerStartupContext.getBrokerClient(),
            brokerStartupContext.getSecurityConfiguration(),
            brokerStartupContext.getSearchQueryServices());
    concurrencyControl.run(
        () -> {
          try {
            brokerStartupContext
                .getClusterConfigurationService()
                .registerInconsistentConfigurationListener(
                    (newTopology, oldTopology) ->
                        shutdownOnInconsistentTopology(
                            brokerStartupContext.getBrokerInfo().getNodeId(),
                            brokerStartupContext.getSpringBrokerBridge(),
                            newTopology,
                            oldTopology));
            partitionManager.start();
            brokerStartupContext.setPartitionManager(partitionManager);
            brokerStartupContext
                .getClusterConfigurationService()
                .registerPartitionChangeExecutors(partitionManager, partitionManager);
            startupFuture.complete(brokerStartupContext);
          } catch (final Exception e) {
            startupFuture.completeExceptionally(e);
          }
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var partitionManager = brokerShutdownContext.getPartitionManager();
    if (partitionManager == null) {
      shutdownFuture.complete(null);
      return;
    }

    brokerShutdownContext.getClusterConfigurationService().removePartitionChangeExecutor();

    concurrencyControl.runOnCompletion(
        partitionManager.stop(),
        (ok, error) -> {
          brokerShutdownContext.setPartitionManager(null);
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            shutdownFuture.complete(brokerShutdownContext);
          }
        });

    brokerShutdownContext
        .getClusterConfigurationService()
        .removeInconsistentConfigurationListener();
  }

  private void shutdownOnInconsistentTopology(
      final int localBrokerId,
      final SpringBrokerBridge springBrokerBridge,
      final ClusterConfiguration newTopology,
      final ClusterConfiguration oldTopology) {
    final MemberId localMemberId = MemberId.from(String.valueOf(localBrokerId));
    LOGGER.warn(
        """
          Received a newer topology which has a different state for this broker.
          State of this broker in new topology :'{}'
          State of this broker in old topology: '{}'
          This usually happens when the topology was changed forcefully when this broker was unreachable or this broker encountered a data loss. Shutting down the broker. Please restart the broker to use the new topology.
        """,
        newTopology.getMember(localMemberId),
        oldTopology.getMember(localMemberId));
    springBrokerBridge.initiateShutdown(ERROR_CODE_ON_INCONSISTENT_TOPOLOGY);
  }
}
