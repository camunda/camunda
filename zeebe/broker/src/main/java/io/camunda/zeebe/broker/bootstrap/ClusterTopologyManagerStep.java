/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.partitioning.topology.ClusterTopologyService;
import io.camunda.zeebe.broker.partitioning.topology.DynamicClusterTopologyService;
import io.camunda.zeebe.broker.partitioning.topology.StaticClusterTopologyService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.topology.state.ClusterTopology;
import java.time.Duration;

public class ClusterTopologyManagerStep
    implements io.camunda.zeebe.scheduler.startup.StartupStep<BrokerStartupContext> {

  private static final int MAX_RETRIES = 10;

  @Override
  public String getName() {
    return "Cluster Topology Manager";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    final var started =
        brokerStartupContext.getConcurrencyControl().<BrokerStartupContext>createFuture();
    final ClusterTopologyService clusterTopologyService =
        getClusterTopologyService(brokerStartupContext.getBrokerConfiguration());
    clusterTopologyService
        .start(brokerStartupContext)
        .andThen(
            (ignore) -> getClusterConfiguration(brokerStartupContext, MAX_RETRIES),
            brokerStartupContext.getConcurrencyControl())
        .onComplete(
            (clusterConfiguration, error) -> {
              if (error == null) {
                brokerStartupContext.setClusterTopology(clusterTopologyService);
                final var brokerInfo = brokerStartupContext.getBrokerInfo();
                brokerInfo
                    .setClusterSize(clusterConfiguration.clusterSize())
                    .setPartitionsCount(clusterConfiguration.partitionCount())
                    .setReplicationFactor(clusterConfiguration.minReplicationFactor());
                started.complete(brokerStartupContext);
              } else {
                started.completeExceptionally(error);
              }
            });
    return started;
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> stopFuture =
        brokerStartupContext.getConcurrencyControl().createFuture();
    final var clusterTopologyService = brokerStartupContext.getClusterTopology();
    if (clusterTopologyService != null) {
      clusterTopologyService
          .closeAsync()
          .onComplete(
              (ignore, error) -> {
                if (error == null) {
                  brokerStartupContext.setClusterTopology(null);
                  stopFuture.complete(brokerStartupContext);
                } else {
                  stopFuture.completeExceptionally(error);
                }
              });
    } else {
      stopFuture.complete(brokerStartupContext);
    }
    return stopFuture;
  }

  /**
   * Retrieves the cluster configuration from the broker client, retrying if it is still
   * uninitialized. This method will retry up to {@link #MAX_RETRIES} times, waiting 200
   * milliseconds between each retry. If the configuration is still uninitialized after the maximum
   * number of retries, it will complete exceptionally with an {@link IllegalStateException}.
   *
   * @param brokerStartupContext the context containing the broker client
   * @param retriesLeft the number of retries left to attempt
   * @return an ActorFuture that completes with the ClusterConfiguration
   */
  private ActorFuture<ClusterTopology> getClusterConfiguration(
      final BrokerStartupContext brokerStartupContext, final int retriesLeft) {
    if (retriesLeft <= 0) {
      return CompletableActorFuture.completedExceptionally(
          new IllegalStateException(
              "Cluster configuration is still uninitialized after maximum retries of "
                  + MAX_RETRIES));
    }
    final var configuration =
        brokerStartupContext.getBrokerClient().getTopologyManager().getClusterTopology();
    if (configuration.isUninitialized()) {
      final ActorFuture<ClusterTopology> future =
          brokerStartupContext.getConcurrencyControl().createFuture();
      brokerStartupContext
          .getConcurrencyControl()
          .schedule(
              Duration.ofMillis(200),
              () ->
                  getClusterConfiguration(brokerStartupContext, retriesLeft - 1)
                      .onComplete(future));
      return future;
    } else {
      return CompletableActorFuture.completed(configuration);
    }
  }

  private static ClusterTopologyService getClusterTopologyService(
      final BrokerCfg brokerConfiguration) {
    return brokerConfiguration.getExperimental().getFeatures().isEnableDynamicClusterTopology()
        ? new DynamicClusterTopologyService()
        : new StaticClusterTopologyService();
  }
}
