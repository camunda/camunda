/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.concurrent.CompletableFuture;

class EmbeddedGatewayServiceStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Embedded Gateway";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var clusterServices = brokerStartupContext.getClusterServices();

    final var embeddedGatewayService =
        new EmbeddedGatewayService(
            brokerStartupContext.getBrokerConfiguration(),
            brokerStartupContext.getActorScheduler(),
            clusterServices.getMessagingService(),
            clusterServices.getMembershipService(),
            clusterServices.getEventService());

    brokerStartupContext.setEmbeddedGatewayService(embeddedGatewayService);

    startupFuture.complete(brokerStartupContext);
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var embeddedGatewayService = brokerShutdownContext.getEmbeddedGatewayService();

    if (embeddedGatewayService == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    CompletableFuture.runAsync(embeddedGatewayService::close)
        .whenComplete(
            (ok, error) -> {
              if (error != null) {
                shutdownFuture.completeExceptionally(error);
              } else {
                concurrencyControl.run(
                    () ->
                        forwardExceptions(
                            () -> {
                              brokerShutdownContext.setEmbeddedGatewayService(null);
                              shutdownFuture.complete(brokerShutdownContext);
                            },
                            shutdownFuture));
              }
            });
  }
}
