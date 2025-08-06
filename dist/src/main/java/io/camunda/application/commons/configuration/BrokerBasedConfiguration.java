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
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.application.commons.job.JobHandlerConfiguration.ActivateJobHandlerConfiguration;
import io.camunda.configuration.beans.LegacyBrokerBasedProperties;
import io.camunda.zeebe.broker.clustering.ClusterConfigFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.RestApiCompositeFilter;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.impl.filters.FilterRepository;
import jakarta.servlet.Filter;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.CompositeFilter;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"broker", "restore"})
public class BrokerBasedConfiguration {

  private final WorkingDirectory workingDirectory;
  private final BrokerCfg properties;
  private final LifecycleProperties lifecycle;

  @Autowired
  public BrokerBasedConfiguration(
      final WorkingDirectory workingDirectory,
      final LegacyBrokerBasedProperties properties,
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

  @ConditionalOnRestGatewayEnabled
  @Bean
  public CompositeFilter restApiCompositeFilter() {
    final List<FilterCfg> filterCfgs = properties.getGateway().getFilters();
    final List<Filter> filters = new FilterRepository().load(filterCfgs).instantiate().toList();

    return new RestApiCompositeFilter(filters);
  }

  @Bean
  public ActivateJobHandlerConfiguration activateJobHandlerConfiguration() {
    return new ActivateJobHandlerConfiguration(
        "ActivateJobsHandlerRest-Broker",
        properties.getGateway().getLongPolling(),
        properties.getGateway().getNetwork().getMaxMessageSize());
  }

  public Duration shutdownTimeout() {
    return lifecycle.getTimeoutPerShutdownPhase();
  }

  @Bean
  public ClusterConfig clusterConfig() {
    final var configFactory = new ClusterConfigFactory();
    return configFactory.mapConfiguration(properties);
  }
}
