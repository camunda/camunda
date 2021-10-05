/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

final class MonitoringServerStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "monitoring services";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var healthCheckService = brokerStartupContext.getHealthCheckService();
    concurrencyControl.runOnCompletion(
        brokerStartupContext.scheduleActor(healthCheckService),
        (ok, error) ->
            forwardExceptions(
                () ->
                    completeStartup(brokerStartupContext, startupFuture, healthCheckService, error),
                startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var healthCheckService = brokerShutdownContext.getHealthCheckService();

    final var springBrokerBridge = brokerShutdownContext.getSpringBrokerBridge();
    springBrokerBridge.registerBrokerHealthCheckServiceSupplier(() -> null);
    brokerShutdownContext.removePartitionListener(healthCheckService);
    concurrencyControl.runOnCompletion(
        healthCheckService.closeAsync(),
        (ok, error) ->
            forwardExceptions(
                () -> {
                  if (error != null) {
                    shutdownFuture.completeExceptionally(error);
                  } else {
                    shutdownFuture.complete(brokerShutdownContext);
                  }
                },
                shutdownFuture));
  }

  private void completeStartup(
      final BrokerStartupContext brokerStartupContext,
      final ActorFuture<BrokerStartupContext> startupFuture,
      final BrokerHealthCheckService healthCheckService,
      final Throwable error) {
    if (error != null) {
      startupFuture.completeExceptionally(error);
    } else {
      final var springBrokerBridge = brokerStartupContext.getSpringBrokerBridge();
      springBrokerBridge.registerBrokerHealthCheckServiceSupplier(() -> healthCheckService);
      brokerStartupContext.addPartitionListener(healthCheckService);
      startupFuture.complete(brokerStartupContext);
    }
  }
}
