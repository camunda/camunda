/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.partitioning.topology.ClusterChangeExecutorImpl;
import io.camunda.zeebe.broker.partitioning.topology.ClusterConfigurationService;
import io.camunda.zeebe.broker.partitioning.topology.DynamicClusterConfigurationService;
import io.camunda.zeebe.dynamic.config.changes.ClusterChangeExecutor;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public class ClusterConfigurationManagerStep
    implements io.camunda.zeebe.scheduler.startup.StartupStep<BrokerStartupContext> {

  @Override
  public String getName() {
    return "Cluster Topology Manager";
  }

  @Override
  public ActorFuture<BrokerStartupContext> startup(
      final BrokerStartupContext brokerStartupContext) {
    final ActorFuture<BrokerStartupContext> started =
        brokerStartupContext.getConcurrencyControl().createFuture();

    final ClusterChangeExecutor clusterChangeExecutor =
        new ClusterChangeExecutorImpl(
            brokerStartupContext.getConcurrencyControl(),
            brokerStartupContext.getExporterRepository(),
            brokerStartupContext.getMeterRegistry());
    final ClusterConfigurationService clusterConfigurationService =
        new DynamicClusterConfigurationService(clusterChangeExecutor);
    clusterConfigurationService
        .start(brokerStartupContext)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                brokerStartupContext.setClusterConfigurationService(clusterConfigurationService);
                final var brokerInfo = brokerStartupContext.getBrokerInfo();
                final var clusterConfiguration =
                    brokerStartupContext
                        .getBrokerClient()
                        .getTopologyManager()
                        .getClusterConfiguration();
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
    final var clusterTopologyService = brokerStartupContext.getClusterConfigurationService();
    if (clusterTopologyService != null) {
      clusterTopologyService
          .closeAsync()
          .onComplete(
              (ignore, error) -> {
                if (error == null) {
                  brokerStartupContext.setClusterConfigurationService(null);
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
}
