/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterConfig;
import io.atomix.utils.Version;
import io.camunda.zeebe.broker.clustering.ClusterConfigFactory;
import io.camunda.zeebe.broker.shared.BrokerConfiguration;
import io.camunda.zeebe.util.VersionUtil;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public final class BrokerClusterConfiguration {

  private MeterRegistry meterRegistry;

  @Bean
  public ClusterConfig clusterConfig(
      final BrokerConfiguration config, final MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry;
    final var configFactory = new ClusterConfigFactory();
    return configFactory.mapConfiguration(config.config());
  }

  @Bean(destroyMethod = "stop")
  public AtomixCluster atomixCluster(final ClusterConfig config) {
    return new AtomixCluster(config, Version.from(VersionUtil.getVersion()), meterRegistry);
  }
}
