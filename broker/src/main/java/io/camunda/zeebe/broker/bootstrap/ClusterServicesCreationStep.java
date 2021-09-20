/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.bootstrap.ClusterServicesCreationStep.Input;
import io.camunda.zeebe.broker.clustering.AtomixClusterFactory;
import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.util.Objects;

final class ClusterServicesCreationStep
    extends AbstractTypedBrokerStartupStep<Input, ClusterServicesImpl> {

  ClusterServicesCreationStep() {
    super(
        Input::new,
        (context, clusterServices) -> {
          context.setClusterServices(clusterServices);
          return context;
        });
  }

  @Override
  void startupTyped(
      final Input input,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<ClusterServicesImpl> startupFuture) {

    final var atomix = AtomixClusterFactory.fromConfiguration(input.getBrokerConfiguration());

    final var clusterServices = new ClusterServicesImpl(atomix);

    startupFuture.complete(clusterServices);
  }

  @Override
  void shutdownTyped(
      final Input input,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<ClusterServicesImpl> shutdownFuture) {

    final var clusterServices = input.getClusterServices();

    if (clusterServices == null) {
      shutdownFuture.complete(null);
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
                  shutdownFuture.complete(null);
                });
          }
        });
  }

  @Override
  public String getName() {
    return "Cluster Services (Creation)";
  }

  protected static final class Input {

    private final BrokerCfg configuration;
    private final ClusterServicesImpl clusterServices;

    Input(final BrokerStartupContext context) {
      configuration = Objects.requireNonNull(context.getBrokerConfiguration());
      clusterServices = context.getClusterServices();
    }

    public BrokerCfg getBrokerConfiguration() {
      return configuration;
    }

    public ClusterServicesImpl getClusterServices() {
      return clusterServices;
    }
  }

  protected static final class Output {}
}
