/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

final class PartitionManagerStep extends AbstractBrokerStartupStep {
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
            brokerStartupContext.getClusterTopology().getPartitionDistribution());
    concurrencyControl.run(
        () -> {
          try {
            partitionManager.start();
            brokerStartupContext.setPartitionManager(partitionManager);
            brokerStartupContext
                .getClusterTopology()
                .registerPartitionChangeExecutor(partitionManager);
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

    brokerShutdownContext.getClusterTopology().removePartitionChangeExecutor();

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
  }
}
