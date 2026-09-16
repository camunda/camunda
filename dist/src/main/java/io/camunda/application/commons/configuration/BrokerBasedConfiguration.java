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
import io.camunda.application.commons.broker.client.BrokerClientConfiguration.BrokerClientCfg;
import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.application.commons.configuration.WorkingDirectoryConfiguration.WorkingDirectory;
import io.camunda.application.commons.job.HttpJobHandlerConfiguration.ActivateJobHandlerConfiguration;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.zeebe.broker.clustering.ClusterConfigFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.dynamic.nodeid.NodeIdProvider;
import io.camunda.zeebe.gateway.RestApiCompositeFilter;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.rest.impl.filters.FilterRepository;
import io.camunda.zeebe.util.MemberIdUtil;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile(value = {"broker", "restore"})
public class BrokerBasedConfiguration {
  private static final Logger LOG = LoggerFactory.getLogger(BrokerBasedConfiguration.class);

  private final WorkingDirectory workingDirectory;
  private final BrokerCfg properties;
  private final LifecycleProperties lifecycle;

  @Autowired
  public BrokerBasedConfiguration(
      final WorkingDirectory workingDirectory,
      final NodeIdProvider nodeIdProvider,
      final BrokerBasedProperties properties,
      final LifecycleProperties lifecycle) {
    this.workingDirectory = workingDirectory;
    this.properties = properties;
    this.lifecycle = lifecycle;

    final var cluster = properties.getCluster();
    final var currentInstance = nodeIdProvider.currentNodeInstance();
    cluster.setNodeId(currentInstance.id());
    cluster.setNodeVersion(currentInstance.version().version());
    properties.init(workingDirectory.path().toAbsolutePath().toString());
  }

  public BrokerCfg config() {
    return properties;
  }

  /**
   * Adds a {@code broker-id} common tag to all broker metrics, set to the broker's member id
   * ({@code $zone_$nodeId}, or the bare {@code $nodeId} when no zone is configured).
   */
  @Bean
  public MeterRegistryCustomizer<MeterRegistry> brokerIdMeterRegistryCustomizer() {
    final var cluster = properties.getCluster();
    final var brokerId = MemberIdUtil.memberIdString(cluster.getZone(), cluster.getNodeId());
    return registry -> registry.config().commonTags("broker-id", brokerId);
  }

  public WorkingDirectory workingDirectory() {
    return workingDirectory;
  }

  @Bean
  public BrokerClientCfg brokerClientConfig() {
    return new BrokerClientCfg(properties.getGateway().getCluster().getRequestTimeout());
  }

  @Bean
  public SchedulerConfiguration schedulerConfiguration() {
    final var threadCfg = properties.getThreads();
    final var cpuThreads = threadCfg.getCpuThreadCount();
    final var ioThreads = threadCfg.getIoThreadCount();
    final var metricsEnabled = properties.getExperimental().getFeatures().isEnableActorMetrics();
    final var nodeId =
        MemberIdUtil.memberIdString(
            properties.getCluster().getZone(), properties.getCluster().getNodeId());
    return new SchedulerConfiguration(cpuThreads, ioThreads, metricsEnabled, "Broker", nodeId);
  }

  /**
   * Registers the user-defined REST API filters configured under {@code
   * zeebe.broker.gateway.filters}.
   *
   * <p>Returned as a {@link FilterRegistrationBean} rather than a bare {@link
   * org.springframework.web.filter.CompositeFilter} bean so the registration can be switched off:
   * Spring Boot maps every {@link Filter}-typed bean onto {@code /*}, which would run an empty
   * composite filter on every request. Configuring no filters is the common case, so the
   * registration is disabled unless at least one filter was loaded.
   */
  @ConditionalOnAnyHttpGatewayEnabled
  @Bean
  public FilterRegistrationBean<RestApiCompositeFilter> restApiCompositeFilter() {
    final List<FilterCfg> filterCfgs = properties.getGateway().getFilters();
    final List<Filter> filters = new FilterRepository().load(filterCfgs).instantiate().toList();

    final var registration = new FilterRegistrationBean<>(new RestApiCompositeFilter(filters));
    registration.setEnabled(!filters.isEmpty());
    return registration;
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

  @Bean
  public @Nullable String clusterId() {
    return properties.getCluster().getClusterId();
  }
}
