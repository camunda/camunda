/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.NodeConfig;
import io.atomix.cluster.discovery.BootstrapDiscoveryConfig;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.protocol.SwimMembershipProtocolConfig;
import io.atomix.utils.net.Address;
import io.camunda.application.commons.actor.ActorSchedulerConfiguration.SchedulerConfiguration;
import io.camunda.application.commons.broker.client.BrokerClientConfiguration.BrokerClientTimeoutConfiguration;
import io.camunda.application.commons.job.JobHandlerConfiguration.ActivateJobHandlerConfiguration;
import io.camunda.configuration.beans.GatewayBasedProperties;
import io.camunda.zeebe.gateway.RestApiCompositeFilter;
import io.camunda.zeebe.gateway.impl.configuration.ClusterCfg;
import io.camunda.zeebe.gateway.impl.configuration.FilterCfg;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.gateway.impl.configuration.MembershipCfg;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.impl.filters.FilterRepository;
import jakarta.servlet.Filter;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.context.LifecycleProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.filter.CompositeFilter;

@Configuration(proxyBeanMethods = false)
@Profile("!broker")
public final class GatewayBasedConfiguration {

  private final GatewayBasedProperties properties;
  private final LifecycleProperties lifecycleProperties;

  @Autowired
  public GatewayBasedConfiguration(
      final GatewayBasedProperties properties, final LifecycleProperties lifecycleProperties) {
    this.properties = properties;
    this.lifecycleProperties = lifecycleProperties;
    properties.init();
  }

  public GatewayBasedProperties config() {
    return properties;
  }

  public Duration shutdownTimeout() {
    return lifecycleProperties.getTimeoutPerShutdownPhase();
  }

  @ConditionalOnRestGatewayEnabled
  @Bean
  public CompositeFilter restApiCompositeFilter() {
    final List<FilterCfg> filterCfgs = properties.getFilters();
    final List<Filter> filters = new FilterRepository().load(filterCfgs).instantiate().toList();

    return new RestApiCompositeFilter(filters);
  }

  @Bean
  public ActivateJobHandlerConfiguration activateJobHandlerConfiguration() {
    return new ActivateJobHandlerConfiguration(
        "ActivateJobsHandlerRest-Gateway",
        properties.getLongPolling(),
        properties.getNetwork().getMaxMessageSize());
  }

  @Bean
  public BrokerClientTimeoutConfiguration brokerClientConfig() {
    return new BrokerClientTimeoutConfiguration(properties.getCluster().getRequestTimeout());
  }

  @Bean
  public SchedulerConfiguration schedulerConfiguration() {
    final var cpuThreads = properties.getThreads().getManagementThreads();
    // We set ioThreads to zero as the Gateway isn't using any IO threads.
    final var ioThreads = 0;
    final var metricsEnabled = false;
    final var nodeId = properties.getCluster().getMemberId();
    return new SchedulerConfiguration(cpuThreads, ioThreads, metricsEnabled, "Gateway", nodeId);
  }

  @Bean
  public ClusterConfig clusterConfig() {
    final var cluster = properties.getCluster();
    final var name = cluster.getClusterName();
    final var messaging = messagingConfig(properties);
    final var discovery = discoveryConfig(cluster.getInitialContactPoints());
    final var membership = membershipConfig(cluster.getMembership());
    final var member = memberConfig(cluster);

    return new ClusterConfig()
        .setClusterId(name)
        .setNodeConfig(member)
        .setDiscoveryConfig(discovery)
        .setMessagingConfig(messaging)
        .setProtocolConfig(membership);
  }

  private MemberConfig memberConfig(final ClusterCfg cluster) {
    final var advertisedAddress =
        Address.from(cluster.getAdvertisedHost(), cluster.getAdvertisedPort());

    return new MemberConfig().setId(cluster.getMemberId()).setAddress(advertisedAddress);
  }

  private SwimMembershipProtocolConfig membershipConfig(final MembershipCfg config) {
    return new SwimMembershipProtocolConfig()
        .setBroadcastDisputes(config.isBroadcastDisputes())
        .setBroadcastUpdates(config.isBroadcastUpdates())
        .setFailureTimeout(config.getFailureTimeout())
        .setGossipFanout(config.getGossipFanout())
        .setGossipInterval(config.getGossipInterval())
        .setNotifySuspect(config.isNotifySuspect())
        .setProbeInterval(config.getProbeInterval())
        .setProbeTimeout(config.getProbeTimeout())
        .setSuspectProbes(config.getSuspectProbes())
        .setSyncInterval(config.getSyncInterval());
  }

  private BootstrapDiscoveryConfig discoveryConfig(final Collection<String> contactPoints) {
    final var nodes =
        contactPoints.stream()
            .map(Address::from)
            .map(address -> new NodeConfig().setAddress(address))
            .collect(Collectors.toSet());
    return new BootstrapDiscoveryConfig().setNodes(nodes);
  }

  private MessagingConfig messagingConfig(final GatewayCfg config) {
    final var cluster = config.getCluster();

    final var messaging =
        new MessagingConfig()
            .setCompressionAlgorithm(cluster.getMessageCompression())
            .setInterfaces(Collections.singletonList(cluster.getHost()))
            .setPort(cluster.getPort());
    if (cluster.getSocketSendBuffer() != null) {
      messaging.setSocketSendBuffer((int) cluster.getSocketSendBuffer().toBytes());
    }
    if (cluster.getSocketReceiveBuffer() != null) {
      messaging.setSocketReceiveBuffer((int) cluster.getSocketReceiveBuffer().toBytes());
    }

    final var security = cluster.getSecurity();
    if (security.isEnabled()) {
      messaging
          .setTlsEnabled(true)
          .configureTls(
              security.getKeyStore().getFilePath(),
              security.getKeyStore().getPassword(),
              security.getPrivateKeyPath(),
              security.getCertificateChainPath());
    }
    return messaging;
  }
}
