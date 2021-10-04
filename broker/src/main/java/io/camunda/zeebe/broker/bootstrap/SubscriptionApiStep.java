/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.engine.impl.SubscriptionApiCommandMessageHandlerService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

class SubscriptionApiStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Subscription API";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var brokerInfo = brokerStartupContext.getBrokerInfo();
    final var actorSchedulingService = brokerStartupContext.getActorSchedulingService();
    final var clusterServices = brokerStartupContext.getClusterServices();

    final SubscriptionApiCommandMessageHandlerService subscriptionApiService =
        new SubscriptionApiCommandMessageHandlerService(
            brokerInfo, clusterServices.getCommunicationService());

    concurrencyControl.runOnCompletion(
        actorSchedulingService.submitActor(subscriptionApiService),
        proceed(
            () -> {
              brokerStartupContext.addPartitionListener(subscriptionApiService);
              brokerStartupContext.addDiskSpaceUsageListener(subscriptionApiService);

              brokerStartupContext.setSubscriptionApiService(subscriptionApiService);
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var subscriptionApiService = brokerShutdownContext.getSubscriptionApiService();

    if (subscriptionApiService == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    concurrencyControl.runOnCompletion(
        subscriptionApiService.closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.removePartitionListener(subscriptionApiService);
              brokerShutdownContext.removeDiskSpaceUsageListener(subscriptionApiService);

              brokerShutdownContext.setSubscriptionApiService(null);
              shutdownFuture.complete(brokerShutdownContext);
            },
            shutdownFuture));
  }
}
