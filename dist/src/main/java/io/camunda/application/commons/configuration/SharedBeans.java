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
import io.camunda.application.commons.job.JobHandlerConfiguration.ActivateJobHandlerConfiguration;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.broker.clustering.ClusterConfigFactory;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.LongPollingCfg;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

@Configuration
public class SharedBeans {

  @Value("${zeebe.broker.gateway.enable}")
  private boolean embeddedGateway = true;

  private GatewayBasedProperties gatewayProperties;
  private BrokerCfg brokerProperties;

  @Autowired(required = false)
  public SharedBeans(
      final GatewayBasedProperties gatewayProperties,
      final BrokerCfg brokerProperties) {
    this.gatewayProperties = gatewayProperties;
    this.brokerProperties = brokerProperties;
  }

  @Bean
  public ActivateJobHandlerConfiguration activateJobHandlerConfiguration() {
    return new ActivateJobHandlerConfiguration(
        "ActivateJobsHandlerRest",
        gateway().getLongPolling(),
        gateway().getNetwork().getMaxMessageSize());
  }

  @Bean
  public BrokerClientTimeoutConfiguration brokerClientConfig() {
    return new BrokerClientTimeoutConfiguration(gateway().getCluster().getRequestTimeout());
  }

  @Bean
  public SchedulerConfiguration schedulerConfiguration() {
    if (embeddedGateway) {
      final var threadCfg = brokerProperties.getThreads();
      final var cpuThreads = threadCfg.getCpuThreadCount();
      final var ioThreads = threadCfg.getIoThreadCount();
      final var metricsEnabled = brokerProperties.getExperimental().getFeatures()
          .isEnableActorMetrics();
      final var nodeId = String.valueOf(brokerProperties.getCluster().getNodeId());
      return new SchedulerConfiguration(cpuThreads, ioThreads, metricsEnabled, "Broker", nodeId);
    } else {
      final var cpuThreads = gatewayProperties.getThreads().getManagementThreads();
      // We set ioThreads to zero as the Gateway isn't using any IO threads.
      final var ioThreads = 0;
      final var metricsEnabled = false;
      final var nodeId = gatewayProperties.getCluster().getMemberId();
      return new SchedulerConfiguration(cpuThreads, ioThreads, metricsEnabled, "Gateway", nodeId);
    }
  }

  @Bean
  public ClusterConfig clusterConfig() {
    if (embeddedGateway) {
      final var configFactory = new ClusterConfigFactory();
      return configFactory.mapConfiguration(brokerProperties);
    } else {
      final var cluster = gatewayProperties.getCluster();
      final var name = cluster.getClusterName();
      final var messaging = GatewayBasedConfiguration.messagingConfig(gatewayProperties);
      final var discovery = GatewayBasedConfiguration.discoveryConfig(cluster.getInitialContactPoints());
      final var membership = GatewayBasedConfiguration.membershipConfig(cluster.getMembership());
      final var member = GatewayBasedConfiguration.memberConfig(cluster);

      return new ClusterConfig()
          .setClusterId(name)
          .setNodeConfig(member)
          .setDiscoveryConfig(discovery)
          .setMessagingConfig(messaging)
          .setProtocolConfig(membership);
    }
  }

  private GatewayCfg gateway() {
    return embeddedGateway ? brokerProperties.getGateway() : gatewayProperties;
  }
}
