/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import static io.camunda.zeebe.util.sched.future.CompletableActorFuture.completedExceptionally;

import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.startup.StartupStep;
import java.util.function.BiConsumer;

final class MonitoringServerStep implements StartupStep<BrokerStartupContext> {

  @Override
  public String getName() {
    return "monitoring services";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    return createFutureAndRun(
        brokerStartupContext,
        (concurrencyControl, future) ->
            startupInternal(brokerStartupContext, concurrencyControl, future));
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerShutdownContext) {
    return createFutureAndRun(
        brokerShutdownContext,
        (concurrencyControl, future) ->
            shutdownInternal(brokerShutdownContext, concurrencyControl, future));
  }

  private void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var brokerInfo = brokerStartupContext.getBrokerInfo();

    final var healthCheckService = brokerStartupContext.getHealthCheckService();
    concurrencyControl.runOnCompletion(
        brokerStartupContext.scheduleActor(healthCheckService),
        (nil, error) ->
            forwardExceptions(
                () ->
                    completeStartup(brokerStartupContext, startupFuture, healthCheckService, error),
                startupFuture));
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

  private void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    final var healthCheckService = brokerShutdownContext.getHealthCheckService();

    final var springBrokerBridge = brokerShutdownContext.getSpringBrokerBridge();
    springBrokerBridge.registerBrokerHealthCheckServiceSupplier(() -> null);
    brokerShutdownContext.removePartitionListener(healthCheckService);
    concurrencyControl.runOnCompletion(
        healthCheckService.closeAsync(),
        (nil, error) ->
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

  /**
   * Helper function that tries to create a future and call a runnable. If an exception is thrown
   * while creating the future, then this exception is forwarded to a dummy future
   */
  private ActorFuture<BrokerStartupContext> createFutureAndRun(
      final BrokerStartupContext brokerStartupContext,
      final BiConsumer<ConcurrencyControl, ActorFuture<BrokerStartupContext>> runnable) {
    try {
      final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
      final ActorFuture<BrokerStartupContext> future = concurrencyControl.createFuture();

      forwardExceptions(() -> runnable.accept(concurrencyControl, future), future);
      return future;
    } catch (final Throwable t) {
      return completedExceptionally(t);
    }
  }
  /**
   * helper function that forwards exceptions thrown by a synchronous block of code to a future
   * object
   */
  private <V> void forwardExceptions(final Runnable r, final ActorFuture<V> future) {
    try {
      r.run();
    } catch (final Throwable t) {
      future.completeExceptionally(t);
    }
  }
}
