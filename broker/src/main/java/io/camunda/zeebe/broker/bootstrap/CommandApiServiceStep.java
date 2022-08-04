/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.transport.backpressure.PartitionAwareRequestLimiter;
import io.camunda.zeebe.broker.transport.commandapi.CommandApiServiceImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.ServerTransport;

final class CommandApiServiceStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Command API";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () ->
            startCommandApiService(
                brokerStartupContext,
                brokerStartupContext.getGatewayBrokerTransport(),
                startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var commandApiServiceActor = brokerShutdownContext.getCommandApiService();

    brokerShutdownContext.removePartitionListener(commandApiServiceActor);
    brokerShutdownContext
        .getDiskSpaceUsageMonitor()
        .removeDiskUsageListener(commandApiServiceActor);

    concurrencyControl.runOnCompletion(
        commandApiServiceActor.closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.setCommandApiService(null);
              shutdownFuture.complete(brokerShutdownContext);
            },
            shutdownFuture));
  }

  private void startCommandApiService(
      final BrokerStartupContext brokerStartupContext,
      final ServerTransport serverTransport,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
    final var brokerInfo = brokerStartupContext.getBrokerInfo();
    final var brokerCfg = brokerStartupContext.getBrokerConfiguration();
    final var schedulingService = brokerStartupContext.getActorSchedulingService();

    final var backpressureCfg = brokerCfg.getBackpressure();
    var limiter = PartitionAwareRequestLimiter.newNoopLimiter();
    if (backpressureCfg.isEnabled()) {
      limiter = PartitionAwareRequestLimiter.newLimiter(backpressureCfg);
    }

    final var commandApiService =
        new CommandApiServiceImpl(
            serverTransport,
            brokerInfo,
            limiter,
            schedulingService,
            brokerCfg.getExperimental().getQueryApi());

    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(commandApiService),
        proceed(
            () -> {
              brokerStartupContext.setCommandApiService(commandApiService);
              brokerStartupContext.addPartitionListener(commandApiService);
              brokerStartupContext
                  .getDiskSpaceUsageMonitor()
                  .addDiskUsageListener(commandApiService);
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }
}
