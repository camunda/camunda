/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

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
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Self-contained Spring configuration for the standalone Event Bridge. Provides the two
 * cross-cutting infrastructure beans ({@link ActorScheduler} and {@link AtomixCluster}) that every
 * other Event Bridge Spring module depends on, and enables Spring Boot auto-configuration (web
 * server, Jackson, actuator endpoints, etc.).
 *
 * <p>This class intentionally does <strong>not</strong> extend or import {@code
 * CommonsModuleConfiguration}: that class transitively imports {@code
 * ActorClockControlledPropertiesOverride}, which {@code @DependsOn("unifiedConfigurationHelper")}
 * — a bean present only in the full Camunda unified-config context, not in the lightweight Event
 * Bridge deployment.
 *
 * <p>Cluster networking is controlled via {@code event-bridge.cluster.*} properties; see {@link
 * EventBridgeProperties.ClusterProperties} for defaults.
 */
@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EnableConfigurationProperties(EventBridgeProperties.class)
public class EventBridgeModuleConfiguration {

  /**
   * Creates and starts the {@link ActorScheduler} used by all broker actors.
   *
   * <p>Thread counts:
   *
   * <ul>
   *   <li>CPU threads: {@code max(1, availableProcessors - 1)} — leaves one core for the OS and
   *       the HTTP server.
   *   <li>I/O threads: 2 — for snapshot disk writes and other blocking I/O.
   * </ul>
   */
  @Bean(destroyMethod = "close")
  public ActorScheduler actorScheduler(
      final EventBridgeProperties properties,
      @Autowired(required = false) final MeterRegistry meterRegistry) {
    final var cpuThreads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
    final var nodeId = properties.cluster().nodeId();

    final var scheduler =
        ActorScheduler.newActorScheduler()
            .setSchedulerName("EventBridge-" + nodeId)
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

  /**
   * Creates and starts the {@link AtomixCluster} used for RAFT consensus and SWIM gossip.
   *
   * <p>Networking is configured from {@link EventBridgeProperties.ClusterProperties}. For
   * single-node standalone mode the defaults (bind all interfaces on port 26502, no initial contact
   * points) are sufficient — RAFT will self-elect the single node as leader immediately.
   *
   * <p>For multi-node deployments set:
   *
   * <pre>
   *   event-bridge.cluster.node-id=broker-1
   *   event-bridge.cluster.bind-port=26502
   *   event-bridge.cluster.advertised-host=my-host
   *   event-bridge.cluster.initial-contact-points=broker-0-host:26502
   * </pre>
   */
  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster(
      final EventBridgeProperties properties,
      @Autowired(required = false) final MeterRegistry meterRegistry) {
    final var clusterCfg = properties.cluster();
    final var atomixCluster =
        new AtomixCluster(
            buildClusterConfig(clusterCfg),
            Version.from(VersionUtil.getVersion()),
            "EventBridge-" + clusterCfg.nodeId(),
            meterRegistry);
    atomixCluster.start().join();
    return atomixCluster;
  }

  // -------------------------------------------------------------------------

  private static ClusterConfig buildClusterConfig(
      final EventBridgeProperties.ClusterProperties cfg) {
    final var memberConfig =
        new MemberConfig()
            .setId(cfg.nodeId())
            .setAddress(Address.from(cfg.effectiveAdvertisedHost(), cfg.effectiveAdvertisedPort()));

    final var messagingConfig =
        new MessagingConfig()
            .setInterfaces(List.of(cfg.bindHost()))
            .setPort(cfg.bindPort());

    final var discoveryConfig =
        new DynamicDiscoveryConfig().setAddresses(cfg.initialContactPoints());

    return new ClusterConfig()
        .setClusterId(cfg.name())
        .setNodeConfig(memberConfig)
        .setMessagingConfig(messagingConfig)
        .setDiscoveryConfig(discoveryConfig)
        .setProtocolConfig(new SwimMembershipProtocolConfig());
  }
}
