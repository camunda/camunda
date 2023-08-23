/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.partitioning.topology.ClusterTopologyService;
import io.camunda.zeebe.broker.partitioning.topology.StaticClusterTopologyService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public class ClusterTopologyManagerStep
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

    final ClusterTopologyService clusterTopologyService = new StaticClusterTopologyService();
    clusterTopologyService
        .start(brokerStartupContext)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                brokerStartupContext.setClusterTopology(clusterTopologyService);
              } else {
                started.completeExceptionally(error);
              }
            });

    return started;
  }

  @Override
  public ActorFuture<BrokerStartupContext> shutdown(
      final BrokerStartupContext brokerStartupContext) {
    brokerStartupContext.setClusterTopology(null);
    return CompletableActorFuture.completed(brokerStartupContext);
  }
}
