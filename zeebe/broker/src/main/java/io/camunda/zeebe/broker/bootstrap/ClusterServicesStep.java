/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.future.ActorFuture;

class ClusterServicesStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Cluster Services";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    brokerStartupContext
        .getClusterServices()
        .start()
        .whenComplete(
            (ok, error) -> {
              if (error != null) {
                startupFuture.completeExceptionally(error);
              } else {
                startupFuture.complete(brokerStartupContext);
              }
            });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var clusterServices = brokerShutdownContext.getClusterServices();

    if (clusterServices == null) {
      shutdownFuture.complete(brokerShutdownContext);
      return;
    }

    clusterServices
        .stop()
        .whenComplete(
            (ok, error) -> {
              if (error != null) {
                shutdownFuture.completeExceptionally(error);
              } else {
                shutdownFuture.complete(brokerShutdownContext);
              }
            });
  }
}
