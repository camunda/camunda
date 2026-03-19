/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.discovery.DynamicDiscoveryConfig;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.protocol.SwimMembershipProtocolConfig;
import io.atomix.utils.Version;
import io.atomix.utils.net.Address;
import io.camunda.eventbridge.core.config.EventBridgeProperties;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Minimal Spring Boot configuration for {@link StandaloneEventBridgeIT}.
 *
 * <p>Provides the two cross-cutting infrastructure beans — {@link ActorScheduler} and {@link
 * AtomixCluster} — that the broker module requires, plus imports
 * {@link EventBridgeGatewayConfiguration} (which in turn imports the broker module).
 *
 * <p>The {@code event-bridge-gateway} module does not declare Spring Security or Camunda Identity
 * on its classpath, so no authentication filter chain is registered and all HTTP endpoints are
 * accessible without credentials in the test environment.
 */
@SpringBootConfiguration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableConfigurationProperties(EventBridgeProperties.class)
@Import(EventBridgeGatewayConfiguration.class)
public class EventBridgeTestApplication {

  @Bean
  public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }

  @Bean(destroyMethod = "close")
  public ActorScheduler actorScheduler(
      final EventBridgeProperties properties,
      @Autowired(required = false) final MeterRegistry meterRegistry) {
    final var cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    final var nodeId = properties.cluster().nodeId();
    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("EventBridge-IT-" + nodeId)
            .setCpuBoundActorThreadCount(cpuThreads)
            .setIoBoundActorThreadCount(2)
            .setMeterRegistry(meterRegistry)
            .setIdleStrategySupplier(
                () ->
                    new BackoffIdleStrategy(
                        ActorScheduler.ActorSchedulerBuilder.DEFAULT_MAX_SPINS,
                        ActorScheduler.ActorSchedulerBuilder.DEFAULT_MAX_YIELDS,
                        ActorScheduler.ActorSchedulerBuilder.DEFAULT_MIN_PARK_PERIOD_NS,
                        ActorScheduler.ActorSchedulerBuilder.DEFAULT_MAX_PARK_PERIOD_NS))
            .build();
    scheduler.start();
    return scheduler;
  }

  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster(
      final EventBridgeProperties properties, final MeterRegistry meterRegistry) {
    final var cfg = properties.cluster();
    final var memberConfig =
        new MemberConfig()
            .setId(cfg.nodeId())
            .setAddress(Address.from(cfg.effectiveAdvertisedHost(), cfg.effectiveAdvertisedPort()));
    final var messagingConfig =
        new MessagingConfig().setInterfaces(List.of(cfg.bindHost())).setPort(cfg.bindPort());
    final var clusterConfig =
        new ClusterConfig()
            .setClusterId(cfg.name())
            .setNodeConfig(memberConfig)
            .setMessagingConfig(messagingConfig)
            .setDiscoveryConfig(new DynamicDiscoveryConfig())
            .setProtocolConfig(new SwimMembershipProtocolConfig());
    final var cluster =
        new AtomixCluster(
            clusterConfig, Version.from("1.0.0"), "EventBridge-IT-" + cfg.nodeId(), meterRegistry);
    cluster.start().join();
    return cluster;
  }
}
