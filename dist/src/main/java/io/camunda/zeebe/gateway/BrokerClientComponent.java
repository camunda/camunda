/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.broker.cluster.BrokerTopologyManager;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.VisibleForTesting;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@VisibleForTesting
public final class BrokerClientComponent {
  final GatewayCfg config;
  final AtomixCluster atomixCluster;
  final ActorScheduler actorScheduler;
  private final BrokerTopologyManager topologyManager;

  @Autowired
  public BrokerClientComponent(
      final GatewayConfiguration config,
      final AtomixCluster atomixCluster,
      final ActorScheduler actorScheduler,
      final BrokerTopologyManager topologyManager) {
    this.config = config.config();
    this.atomixCluster = atomixCluster;
    this.actorScheduler = actorScheduler;
    this.topologyManager = topologyManager;
  }

  @Bean(destroyMethod = "close")
  public BrokerClient brokerClient() {
    final var brokerClient =
        new BrokerClientImpl(
            config.getCluster().getRequestTimeout(),
            atomixCluster.getMessagingService(),
            atomixCluster.getEventService(),
            actorScheduler,
            topologyManager);
    brokerClient.start().forEach(ActorFuture::join);
    return brokerClient;
  }
}
