/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.atomix.cluster.ClusterConfig;
import io.camunda.application.commons.broker.client.BrokerClientConfiguration.BrokerClientTimeoutConfiguration;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.application.commons.job.JobHandlerConfiguration.ActivateJobHandlerConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
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

  @ConditionalOnRestGatewayEnabled
  @Bean
  public CompositeFilter restApiCompositeFilter() {
    final List<FilterCfg> filterCfgs = properties.getGateway().getFilters();
    final List<Filter> filters = new FilterRepository().load(filterCfgs).instantiate().toList();

    return new RestApiCompositeFilter(filters);
  }

  public Duration shutdownTimeout() {
    return lifecycle.getTimeoutPerShutdownPhase();
  }

  @Bean
  public String clusterId() {
    return properties.getCluster().getClusterId();
  }
}
