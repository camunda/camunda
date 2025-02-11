/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.util.VisibleForTesting;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
@VisibleForTesting
public final class BrokerClientComponent {
  private final GatewayCfg config;
  private final AtomixCluster atomixCluster;
  private final ActorScheduler actorScheduler;
  private final BrokerTopologyManager topologyManager;
  private final BrokerClientRequestMetrics metrics;

  @Autowired
  public BrokerClientComponent(
      final GatewayConfiguration config,
      final AtomixCluster atomixCluster,
      final ActorScheduler actorScheduler,
      final BrokerTopologyManager topologyManager,
      final MeterRegistry registry) {
    this.config = config.config();
    this.atomixCluster = atomixCluster;
    this.actorScheduler = actorScheduler;
    this.topologyManager = topologyManager;
    metrics = BrokerClientRequestMetrics.of(registry);
  }

  @Bean(destroyMethod = "close")
  public BrokerClient brokerClient() {
    final var brokerClient =
        new BrokerClientImpl(
            config.getCluster().getRequestTimeout(),
            atomixCluster.getMessagingService(),
            atomixCluster.getEventService(),
            actorScheduler,
            topologyManager,
            metrics);
    brokerClient.start().forEach(ActorFuture::join);
    return brokerClient;
  }
}
