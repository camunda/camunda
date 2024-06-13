/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.camunda.commons.actor.ActorSchedulerConfiguration.SchedulerConfiguration;
import io.camunda.commons.broker.client.BrokerClientConfiguration.BrokerClientTimeoutConfiguration;
import io.camunda.zeebe.gateway.GatewayConfiguration.GatewayProperties;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public final class GatewayConfiguration {

  private final GatewayProperties config;
  private final LifecycleProperties lifecycleProperties;

  @Autowired
  public GatewayConfiguration(
      final GatewayProperties config, final LifecycleProperties lifecycleProperties) {
    this.config = config;
    this.lifecycleProperties = lifecycleProperties;
    config.init();
  }

  public GatewayProperties config() {
    return config;
  }

  public Duration shutdownTimeout() {
    return lifecycleProperties.getTimeoutPerShutdownPhase();
  }

  @Bean
  public BrokerClientTimeoutConfiguration brokerClientConfig() {
    return new BrokerClientTimeoutConfiguration(config.getCluster().getRequestTimeout());
  }

  @Bean
  public MultiTenancyCfg multiTenancyCfg() {
    return config.getMultiTenancy();
  }

  @Bean
  public SchedulerConfiguration schedulerConfiguration() {
    final var cpuThreads = config.getThreads().getManagementThreads();
    // We set ioThreads to zero as the Gateway isn't using any IO threads.
    final var ioThreads = 0;
    final var metricsEnabled = false;
    final var nodeId = config.getCluster().getMemberId();
    return new SchedulerConfiguration(cpuThreads, ioThreads, metricsEnabled, "Gateway", nodeId);
  }

  @ConfigurationProperties("zeebe.gateway")
  public static final class GatewayProperties extends GatewayCfg {}
}
