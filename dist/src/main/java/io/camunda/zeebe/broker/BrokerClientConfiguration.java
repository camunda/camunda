/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.shared.BrokerConfiguration;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
final class BrokerClientConfiguration {
  private final BrokerCfg config;
  private final AtomixCluster cluster;
  private final ActorScheduler scheduler;
  private final BrokerTopologyManager topologyManager;
  private final BrokerClientRequestMetrics metrics;

  @Autowired
  BrokerClientConfiguration(
      final BrokerConfiguration config,
      final AtomixCluster cluster,
      final ActorScheduler scheduler,
      final BrokerTopologyManager topologyManager,
      final MeterRegistry registry) {
    this.config = config.config();
    this.cluster = cluster;
    this.scheduler = scheduler;
    this.topologyManager = topologyManager;
    metrics = BrokerClientRequestMetrics.of(registry);
  }

  @Bean(destroyMethod = "close")
  BrokerClient brokerClient() {
    final var brokerClient =
        new BrokerClientImpl(
            config.getGateway().getCluster().getRequestTimeout(),
            cluster.getMessagingService(),
            cluster.getEventService(),
            scheduler,
            topologyManager,
            metrics);
    brokerClient.start().forEach(ActorFuture::join);
    return brokerClient;
  }
}
