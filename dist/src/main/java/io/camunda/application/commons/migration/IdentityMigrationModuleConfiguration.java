/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.migration;

import io.atomix.cluster.ClusterConfig;
import io.atomix.cluster.MemberConfig;
import io.atomix.cluster.NodeConfig;
import io.atomix.cluster.discovery.BootstrapDiscoveryConfig;
import io.atomix.cluster.messaging.MessagingConfig;
import io.atomix.cluster.protocol.SwimMembershipProtocolConfig;
import io.atomix.utils.net.Address;
import io.camunda.application.commons.actor.ActorSchedulerConfiguration.SchedulerConfiguration;
import io.camunda.application.commons.broker.client.BrokerClientConfiguration.BrokerClientTimeoutConfiguration;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.cluster.ClusterProperties;
import io.camunda.migration.identity.config.cluster.MembershipConfig;
import io.camunda.search.clients.AuthorizationSearchClient;
import io.camunda.search.clients.GroupSearchClient;
import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.clients.RoleSearchClient;
import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.clients.TenantSearchClient;
import io.camunda.service.ApiServicesExecutorProvider;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = {
      "io.camunda.migration.identity",
      // broker client setup requires actor and clustering setup as well
      "io.camunda.application.commons.actor",
      "io.camunda.application.commons.broker.client",
      "io.camunda.application.commons.clustering",
      // security setup is needed for service layer
      "io.camunda.application.commons.security"
    })
@Profile("identity-migration")
@EnableConfigurationProperties(IdentityMigrationProperties.class)
@EnableAutoConfiguration
public class IdentityMigrationModuleConfiguration {

  private final IdentityMigrationProperties properties;

  @Autowired
  public IdentityMigrationModuleConfiguration(final IdentityMigrationProperties properties) {
    this.properties = properties;
  }

  @Bean
  public AuthorizationServices authorizationServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final AuthorizationSearchClient authorizationSearchClient,
      final ApiServicesExecutorProvider executorProvider) {
    return new AuthorizationServices(
        brokerClient, securityContextProvider, authorizationSearchClient, null, executorProvider);
  }

  @Bean
  public GroupServices groupServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final GroupSearchClient groupSearchClient,
      final ApiServicesExecutorProvider executorProvider) {
    return new GroupServices(
        brokerClient, securityContextProvider, groupSearchClient, null, executorProvider);
  }

  @Bean
  public MappingRuleServices mappingRuleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final MappingRuleSearchClient mappingRuleSearchClient,
      final ApiServicesExecutorProvider executorProvider) {
    return new MappingRuleServices(
        brokerClient, securityContextProvider, mappingRuleSearchClient, null, executorProvider);
  }

  @Bean
  public RoleServices roleServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final RoleSearchClient roleSearchClient,
      final ApiServicesExecutorProvider executorProvider) {
    return new RoleServices(
        brokerClient, securityContextProvider, roleSearchClient, null, executorProvider);
  }

  @Bean
  public TenantServices tenantServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final TenantSearchClient tenantSearchClient,
      final ApiServicesExecutorProvider executorProvider) {
    return new TenantServices(
        brokerClient, securityContextProvider, tenantSearchClient, null, executorProvider);
  }

  @Bean
  public SearchClientsProxy noopSearchClientsProxy() {
    return SearchClientsProxy.noop();
  }

  @Bean
  public SecurityContextProvider securityContextProvider() {
    return new SecurityContextProvider();
  }

  @Bean
  public BrokerClientTimeoutConfiguration brokerClientConfig() {
    return new BrokerClientTimeoutConfiguration(properties.getCluster().getRequestTimeout());
  }

  @Bean
  public ClusterConfig clusterConfig() {
    final var cluster = properties.getCluster();
    final var name = cluster.getClusterName();
    final var messaging = messagingConfig(cluster);
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

  private MemberConfig memberConfig(final ClusterProperties cluster) {
    final var advertisedAddress =
        Address.from(cluster.getAdvertisedHost(), cluster.getAdvertisedPort());

    return new MemberConfig().setId(cluster.getMemberId()).setAddress(advertisedAddress);
  }

  private SwimMembershipProtocolConfig membershipConfig(final MembershipConfig config) {
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

  private MessagingConfig messagingConfig(final ClusterProperties cluster) {
    final var messaging =
        new MessagingConfig()
            .setCompressionAlgorithm(cluster.getMessageCompression())
            .setInterfaces(Collections.singletonList(cluster.getHost()))
            .setPort(cluster.getPort());

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

  @Bean
  public SchedulerConfiguration schedulerConfiguration() {
    final var cpuThreads = 1;
    // We set ioThreads to zero as the migration app isn't using any io threads
    final var ioThreads = 0;
    final var metricsEnabled = false;
    final var nodeId = properties.getCluster().getMemberId();
    return new SchedulerConfiguration(cpuThreads, ioThreads, metricsEnabled, "Migration", nodeId);
  }
}
