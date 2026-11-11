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
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.mailbox.GatewayMailbox;
import io.camunda.zeebe.broker.client.impl.mailbox.GatewayMailboxSetups;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class BrokerClientConfiguration {

  private final BrokerClientCfg config;
  private final AtomixCluster cluster;
  private final ActorScheduler scheduler;
  private final BrokerTopologyManager topologyManager;
  private final BrokerClientRequestMetrics metrics;
  private final MeterRegistry meterRegistry;

  @Autowired
  public BrokerClientConfiguration(
      final BrokerClientCfg config,
      final AtomixCluster cluster,
      final ActorScheduler scheduler,
      final BrokerTopologyManager topologyManager,
      final MeterRegistry meterRegistry) {
    this.config = config;
    this.cluster = cluster;
    this.scheduler = scheduler;
    this.topologyManager = topologyManager;
    metrics = new BrokerClientRequestMetrics(meterRegistry);
    this.meterRegistry = meterRegistry;
  }

  @Bean(destroyMethod = "close")
  public BrokerClient brokerClient() {
    final var brokerClient =
        new BrokerClientImpl(
            config.requestTimeout(),
            cluster.getMessagingService(),
            cluster.getEventService(),
            scheduler,
            topologyManager,
            metrics,
            mailbox());
    brokerClient.start().forEach(ActorFuture::join);
    return brokerClient;
  }

  /**
   * The mailbox for requests to leaders that the gateway cannot see, if a provider for it is on the
   * class path and turns it on.
   */
  private @Nullable GatewayMailbox mailbox() {
    final var memberId = cluster.getMembershipService().getLocalMember().id().id();
    return GatewayMailboxSetups.load(memberId)
        .map(setup -> new GatewayMailbox(setup, meterRegistry))
        .orElse(null);
  }

  public record BrokerClientCfg(Duration requestTimeout) {}
}
