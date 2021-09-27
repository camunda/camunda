/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.management.LeaderManagementRequestHandler;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

final class LeaderManagementRequestHandlerStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Leader Management Request Handler";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var brokerInfo = brokerStartupContext.getBrokerInfo();
    final var clusterServices = brokerStartupContext.getClusterServices();

    final var managementRequestHandler =
        new LeaderManagementRequestHandler(
            brokerInfo,
            clusterServices.getCommunicationService(),
            clusterServices.getEventService());

    final var actorStartFuture =
        brokerStartupContext.getActorSchedulingService().submitActor(managementRequestHandler);

    concurrencyControl.runOnCompletion(
        actorStartFuture,
        (ok, error) -> {
          if (error != null) {
            startupFuture.completeExceptionally(error);
            return;
          }

          forwardExceptions(
              () -> {
                brokerStartupContext.addPartitionListener(managementRequestHandler);
                brokerStartupContext.addDiskSpaceUsageListener(managementRequestHandler);

                brokerStartupContext.setLeaderManagementRequestHandler(managementRequestHandler);

                startupFuture.complete(brokerStartupContext);
              },
              startupFuture);
        });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var handler = brokerShutdownContext.getLeaderManagementRequestHandler();

    if (handler == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    final var closeFuture = handler.closeAsync();

    concurrencyControl.runOnCompletion(
        closeFuture,
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
            return;
          }

          forwardExceptions(
              () -> {
                brokerShutdownContext.removeDiskSpaceUsageListener(handler);
                brokerShutdownContext.removePartitionListener(handler);
                brokerShutdownContext.setLeaderManagementRequestHandler(null);

                shutdownFuture.complete(brokerShutdownContext);
              },
              shutdownFuture);
        });
  }
}
