/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.transport.commandapi.GetApiServiceImpl;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.transport.ServerTransport;

final class GetApiServiceStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Get API";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () ->
            startGetApiService(
                brokerStartupContext,
                brokerStartupContext.getGatewayBrokerTransport(),
                startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var getApiServiceActor = brokerShutdownContext.getGetApiService();

    brokerShutdownContext.removePartitionListener(getApiServiceActor);
    brokerShutdownContext.getDiskSpaceUsageMonitor().removeDiskUsageListener(getApiServiceActor);

    concurrencyControl.runOnCompletion(
        getApiServiceActor.closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.setGetApiService(null);
              shutdownFuture.complete(brokerShutdownContext);
            },
            shutdownFuture));
  }

  private void startGetApiService(
      final BrokerStartupContext brokerStartupContext,
      final ServerTransport serverTransport,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var concurrencyControl = brokerStartupContext.getConcurrencyControl();
    final var schedulingService = brokerStartupContext.getActorSchedulingService();

    final var getApiService = new GetApiServiceImpl(serverTransport, schedulingService);

    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(getApiService),
        proceed(
            () -> {
              brokerStartupContext.setGetApiService(getApiService);
              brokerStartupContext.addPartitionListener(getApiService);
              brokerStartupContext.getDiskSpaceUsageMonitor().addDiskUsageListener(getApiService);
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }
}
