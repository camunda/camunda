/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.transport.adminapi.AdminApiRequestHandler;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

public class AdminApiServiceStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Admin API";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var schedulingService = brokerStartupContext.getActorSchedulingService();
    final var transport = brokerStartupContext.getCommandApiServerTransport();
    final var handler = new AdminApiRequestHandler(transport);

    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(handler),
        proceed(
            () -> {
              if (brokerStartupContext.getAdminApiService() == null) {
                brokerStartupContext.setAdminApiService(handler);
              }
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var service = brokerShutdownContext.getAdminApiService();
    if (service == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }
    concurrencyControl.runOnCompletion(
        service.closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.setAdminApiService(null);
              shutdownFuture.complete(brokerShutdownContext);
            },
            shutdownFuture));
  }
}
