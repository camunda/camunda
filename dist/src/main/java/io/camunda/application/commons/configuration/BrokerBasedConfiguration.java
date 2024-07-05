/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.atomix.cluster.ClusterConfig;
import io.camunda.application.commons.actor.ActorSchedulerConfiguration.SchedulerConfiguration;
import io.camunda.application.commons.broker.client.BrokerClientConfiguration.BrokerClientTimeoutConfiguration;
import io.camunda.application.commons.configuration.BrokerBasedConfiguration.BrokerBasedProperties;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.application.commons.job.JobHandlerConfiguration.ActivateJobHandlerConfiguration;
import io.camunda.zeebe.broker.clustering.ClusterConfigFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.impl.configuration.MultiTenancyCfg;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled.RestGatewayDisabled;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BrokerBasedProperties.class)
@Profile(value = {"broker", "restore"})
public final class BrokerBasedConfiguration {

  private final WorkingDirectory workingDirectory;
  private final BrokerCfg properties;
  private final LifecycleProperties lifecycle;

  @Autowired
  public BrokerBasedConfiguration(
      final WorkingDirectory workingDirectory,
      final BrokerBasedProperties properties,
      final LifecycleProperties lifecycle) {
    this.workingDirectory = workingDirectory;
    this.properties = properties;
    this.lifecycle = lifecycle;

    properties.init(workingDirectory.path().toAbsolutePath().toString());
  }

  public BrokerCfg config() {
    return properties;
  }

  public WorkingDirectory workingDirectory() {
    return workingDirectory;
  }

  @Bean
  public BrokerClientTimeoutConfiguration brokerClientConfig() {
    return new BrokerClientTimeoutConfiguration(
        properties.getGateway().getCluster().getRequestTimeout());
  }

  @Bean
  public SchedulerConfiguration schedulerConfiguration() {
    final var threadCfg = properties.getThreads();
    final var cpuThreads = threadCfg.getCpuThreadCount();
    final var ioThreads = threadCfg.getIoThreadCount();
    final var metricsEnabled = properties.getExperimental().getFeatures().isEnableActorMetrics();
    final var nodeId = String.valueOf(properties.getCluster().getNodeId());
    return new SchedulerConfiguration(cpuThreads, ioThreads, metricsEnabled, "Broker", nodeId);
  }

  @ConditionalOnProperty(prefix = "zeebe.broker.gateway", name = "enable", havingValue = "false")
  @Bean
  public RestGatewayDisabled disableRestGateway() {
    return new RestGatewayDisabled();
  }

  @Bean
  public ActivateJobHandlerConfiguration activateJobHandlerConfiguration() {
    return new ActivateJobHandlerConfiguration(
        "ActivateJobsHandlerRest-Broker",
        properties.getGateway().getLongPolling(),
        properties.getGateway().getNetwork().getMaxMessageSize());
  }

  @Bean
  public MultiTenancyCfg multiTenancyCfg() {
    return properties.getGateway().getMultiTenancy();
  }

  public Duration shutdownTimeout() {
    return lifecycle.getTimeoutPerShutdownPhase();
  }

  @Bean
  public ClusterConfig clusterConfig() {
    final var configFactory = new ClusterConfigFactory();
    return configFactory.mapConfiguration(properties);
  }

  @ConfigurationProperties("zeebe.broker")
  public static final class BrokerBasedProperties extends BrokerCfg {}
}
