/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.broker.client;

import io.atomix.cluster.AtomixCluster;
import io.camunda.application.commons.actor.GatewayActorScheduler;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.BrokerClientRequestMetrics;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerClientImpl;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.GatewayClusterConfigurationService;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
public final class BrokerClientConfiguration {

  private final BrokerClientCfg config;
  private final AtomixCluster cluster;
  private final ActorScheduler scheduler;
  private final BrokerClientRequestMetrics metrics;
  private final BrokerClientTopologyMetrics topologyMetrics;
  private final OpenTelemetry openTelemetry;

  public BrokerClientConfiguration(
      final BrokerClientCfg config,
      final AtomixCluster cluster,
      final GatewayActorScheduler scheduler,
      final MeterRegistry meterRegistry) {
    this(config, cluster, scheduler, meterRegistry, null);
  }

  @Autowired
  public BrokerClientConfiguration(
      final BrokerClientCfg config,
      final AtomixCluster cluster,
      final GatewayActorScheduler scheduler,
      final MeterRegistry meterRegistry,
      // Spring Boot provides a bean also when the SDK is disabled; none exists only without its
      // autoconfiguration
      @Autowired(required = false) final @Nullable OpenTelemetry openTelemetry) {
    this.config = config;
    this.cluster = cluster;
    this.scheduler = scheduler.scheduler();
    metrics = new BrokerClientRequestMetrics(meterRegistry);
    topologyMetrics = new BrokerClientTopologyMetrics(meterRegistry);
    this.openTelemetry = openTelemetry != null ? openTelemetry : OpenTelemetry.noop();
  }

  @Bean
  @Profile("broker")
  public BrokerTopologyManager brokerTopologyManagerForEmbeddedBrokerClient() {
    return startTopologyManager();
  }

  @Bean
  @Profile("!broker")
  public BrokerTopologyManager brokerTopologyManager(
      final GatewayClusterConfigurationService gatewayClusterConfigurationService) {
    final var brokerTopologyManager = startTopologyManager();
    gatewayClusterConfigurationService.addUpdateListener(brokerTopologyManager);
    return brokerTopologyManager;
  }

  @Bean(destroyMethod = "close")
  public BrokerClient brokerClient(final BrokerTopologyManager topologyManager) {
    final var brokerClient =
        new BrokerClientImpl(
            config.requestTimeout(),
            cluster.getMessagingService(),
            cluster.getEventService(),
            scheduler,
            topologyManager,
            metrics,
            openTelemetry);
    brokerClient.start().forEach(ActorFuture::join);
    return brokerClient;
  }

  private BrokerTopologyManagerImpl startTopologyManager() {
    final var membershipService = cluster.getMembershipService();
    final var brokerTopologyManager =
        new BrokerTopologyManagerImpl(membershipService::getMembers, topologyMetrics);
    scheduler.submitActor(brokerTopologyManager).join();
    membershipService.addListener(brokerTopologyManager);
    // Re-seed after the listener is attached: closes the race where a MEMBER_ADDED
    // event fires between the actor's initial snapshot and listener registration.
    brokerTopologyManager.initializeTopologyFromMembership();
    return brokerTopologyManager;
  }

  public record BrokerClientCfg(Duration requestTimeout) {}
}
