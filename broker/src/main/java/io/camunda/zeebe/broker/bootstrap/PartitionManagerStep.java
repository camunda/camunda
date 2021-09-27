/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.partitioning.PartitionManagerImpl;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.CompletableFuture;

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
            brokerStartupContext.getActorSchedulingService(),
            brokerStartupContext.getBrokerConfiguration(),
            brokerStartupContext.getBrokerInfo(),
            brokerStartupContext.getClusterServices(),
            brokerStartupContext.getHealthCheckService(),
            brokerStartupContext
                .getLeaderManagementRequestHandler()
                .getPushDeploymentRequestHandler(),
            brokerStartupContext::addDiskSpaceUsageListener,
            brokerStartupContext.getPartitionListeners(),
            brokerStartupContext.getCommandApiService(),
            brokerStartupContext.getExporterRepository());

    CompletableFuture.runAsync(
        () ->
            partitionManager
                .start()
                .whenComplete(
                    (ok, error) -> {
                      if (error != null) {
                        startupFuture.completeExceptionally(error);
                        return;
                      }

                      forwardExceptions(
                          () ->
                              concurrencyControl.run(
                                  () ->
                                      forwardExceptions(
                                          () -> {
                                            final var adminService =
                                                brokerStartupContext.getBrokerAdminService();
                                            adminService.injectAdminAccess(
                                                partitionManager.createAdminAccess(adminService));
                                            adminService.injectPartitionInfoSource(
                                                partitionManager.getPartitions());

                                            brokerStartupContext.setPartitionManager(
                                                partitionManager);

                                            startupFuture.complete(brokerStartupContext);
                                          },
                                          startupFuture)),
                          startupFuture);
                    }));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var partitionManger = brokerShutdownContext.getPartitionManager();
    if (partitionManger == null) {
      shutdownFuture.complete(null);
      return;
    }

    CompletableFuture.runAsync(
        () ->
            partitionManger
                .stop()
                .whenComplete(
                    (ok, error) -> {
                      if (error != null) {
                        shutdownFuture.completeExceptionally(error);
                        return;
                      }
                      forwardExceptions(
                          () ->
                              concurrencyControl.run(
                                  () ->
                                      forwardExceptions(
                                          () -> {
                                            brokerShutdownContext.setPartitionManager(null);
                                            shutdownFuture.complete(brokerShutdownContext);
                                          },
                                          shutdownFuture)),
                          shutdownFuture);
                    }));
  }
}
