/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.broker.client;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class BrokerClientConfiguration {

  private final BrokerClientTimeoutConfiguration config;
  private final AtomixCluster cluster;
  private final ActorScheduler scheduler;
  private final BrokerTopologyManager topologyManager;

  @Autowired
  public BrokerClientConfiguration(
      final BrokerClientTimeoutConfiguration config,
      final AtomixCluster cluster,
      final ActorScheduler scheduler,
      final BrokerTopologyManager topologyManager) {
    this.config = config;
    this.cluster = cluster;
    this.scheduler = scheduler;
    this.topologyManager = topologyManager;
  }

  @Bean(destroyMethod = "close")
  public BrokerClient brokerClient() {
    final var brokerClient =
        new BrokerClientImpl(
            config.requestTimeout(),
            cluster.getMessagingService(),
            cluster.getEventService(),
            scheduler,
            topologyManager);
    brokerClient.start().forEach(ActorFuture::join);
    return brokerClient;
  }

  public static record BrokerClientTimeoutConfiguration(Duration requestTimeout) {}
}
