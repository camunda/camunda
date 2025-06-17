/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.transport.snapshotapi.SnapshotApiRequestHandler;
import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public class SnapshotApiServiceStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Snapshot API";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    concurrencyControl.run(
        () ->
            startSnapshotApi(
                brokerStartupContext,
                brokerStartupContext.getGatewayBrokerTransport(),
                concurrencyControl,
                startupFuture));
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {
    concurrencyControl.runOnCompletion(
        brokerShutdownContext.getSnapshotApiRequestHandler().closeAsync(),
        proceed(
            () -> {
              brokerShutdownContext.setSnapshotApiRequestHandler(null);
              shutdownFuture.complete(brokerShutdownContext);
            },
            shutdownFuture));
  }

  private void startSnapshotApi(
      final BrokerStartupContext brokerStartupContext,
      final io.camunda.zeebe.transport.ServerTransport serverTransport,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var handler =
        new SnapshotApiRequestHandler(serverTransport, brokerStartupContext.getBrokerClient());
    final var schedulingService = brokerStartupContext.getActorSchedulingService();
    concurrencyControl.runOnCompletion(
        schedulingService.submitActor(handler),
        proceed(
            () -> {
              brokerStartupContext.setSnapshotApiRequestHandler(handler);
              startupFuture.complete(brokerStartupContext);
            },
            startupFuture));
  }
}
