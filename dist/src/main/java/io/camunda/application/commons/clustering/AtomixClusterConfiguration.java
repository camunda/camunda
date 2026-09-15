/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.clustering;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.utils.Version;
import io.camunda.application.commons.actor.ActorSchedulerConfiguration.SchedulerConfiguration;
import io.camunda.application.commons.configuration.BrokerMeterRegistry;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class AtomixClusterConfiguration {

  private final ClusterConfig config;
  private final String actorSchedulerName;
  private final MeterRegistry meterRegistry;

  @Autowired
  public AtomixClusterConfiguration(
      final ClusterConfig config,
      final SchedulerConfiguration schedulerConfiguration,
      final MeterRegistry meterRegistry,
      final Optional<BrokerMeterRegistry> brokerMeterRegistry) {

    this.config = config;
    actorSchedulerName =
        schedulerConfiguration != null
                && schedulerConfiguration.schedulerPrefix() != null
                && schedulerConfiguration.nodeId() != null
            ? String.format(
                "%s-%s", schedulerConfiguration.schedulerPrefix(), schedulerConfiguration.nodeId())
            : "";
    // Broker and gateway can run in the same JVM. Keep gateway-owned metrics on the root registry,
    // but use the broker-scoped registry for the shared Atomix cluster because Atomix is
    // broker-owned
    // and its metrics must carry the broker's nodeId tag.
    this.meterRegistry =
        brokerMeterRegistry.map(BrokerMeterRegistry::registry).orElse(meterRegistry);
  }

  public AtomixClusterConfiguration(
      final ClusterConfig config,
      final SchedulerConfiguration schedulerConfiguration,
      final MeterRegistry meterRegistry) {
    this(config, schedulerConfiguration, meterRegistry, Optional.empty());
  }

  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster() {
    final var atomixCluster =
        new AtomixCluster(
            config, Version.from(VersionUtil.getVersion()), actorSchedulerName, meterRegistry);
    atomixCluster.start();
    return atomixCluster;
  }
}
