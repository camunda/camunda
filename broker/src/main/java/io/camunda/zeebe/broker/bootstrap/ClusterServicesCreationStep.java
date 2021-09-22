/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.clustering.AtomixClusterFactory;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

final class ClusterServicesCreationStep extends AbstractBrokerStartupStep {

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {
    final var atomix =
        AtomixClusterFactory.fromConfiguration(brokerStartupContext.getBrokerConfiguration());

    final var clusterServices = new ClusterServicesImpl(atomix);

    brokerStartupContext.setClusterServices(clusterServices);
    startupFuture.complete(brokerStartupContext);
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

    final var stopFuture = clusterServices.stop();

    stopFuture.whenComplete(
        (ok, error) -> {
          if (error != null) {
            shutdownFuture.completeExceptionally(error);
          } else {
            concurrencyControl.run(
                () -> {
                  brokerShutdownContext.setClusterServices(null);
                  shutdownFuture.complete(brokerShutdownContext);
                });
          }
        });
  }

  @Override
  public String getName() {
    return "Cluster Services (Creation)";
  }
}
