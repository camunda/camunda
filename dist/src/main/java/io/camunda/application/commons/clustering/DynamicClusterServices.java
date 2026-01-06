/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.clustering;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.GatewayClusterConfigurationService;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
public class DynamicClusterServices {

  private final ActorScheduler scheduler;
  private final ClusterMembershipService clusterMembershipService;
  private final ClusterCommunicationService clusterCommunicationService;
  private final MeterRegistry meterRegistry;
  private final BrokerClientTopologyMetrics brokerTopologyMetrics;

  @Autowired
  public DynamicClusterServices(
      final ActorScheduler scheduler,
      final AtomixCluster atomixCluster,
      final MeterRegistry meterRegistry) {
    this.scheduler = scheduler;
    clusterMembershipService = atomixCluster.getMembershipService();
    clusterCommunicationService = atomixCluster.getCommunicationService();
    this.meterRegistry = meterRegistry;
    brokerTopologyMetrics = new BrokerClientTopologyMetrics(meterRegistry);
  }

  @Bean
  public ClusterConfigurationGossiperConfig configManagerCfg(
      final UnifiedConfiguration unifiedConfiguration) {
    final var gossiperConfig = unifiedConfiguration.getCamunda().getCluster().getMetadata();
    return new ClusterConfigurationGossiperConfig(
        gossiperConfig.getSyncDelay(),
        gossiperConfig.getSyncRequestTimeout(),
        gossiperConfig.getGossipFanout());
  }

  @Bean
  @Profile("!broker")
  public GatewayClusterConfigurationService gatewayClusterTopologyService(
      final ClusterConfigurationGossiperConfig configManagerCfg) {
    final var service =
        new GatewayClusterConfigurationService(
            clusterCommunicationService, clusterMembershipService, configManagerCfg, meterRegistry);
    scheduler.submitActor(service).join();
    return service;
  }

  @Bean
  @Profile("broker")
  public BrokerTopologyManager brokerTopologyManagerForEmbeddedBrokerClient() {
    final var brokerTopologyManager =
        new BrokerTopologyManagerImpl(clusterMembershipService::getMembers, brokerTopologyMetrics);
    scheduler.submitActor(brokerTopologyManager).join();
    clusterMembershipService.addListener(brokerTopologyManager);
    return brokerTopologyManager;
  }

  @Bean
  @Profile("!broker")
  public BrokerTopologyManager brokerTopologyManager(
      final GatewayClusterConfigurationService gatewayClusterConfigurationService) {
    final var brokerTopologyManager =
        new BrokerTopologyManagerImpl(clusterMembershipService::getMembers, brokerTopologyMetrics);
    scheduler.submitActor(brokerTopologyManager).join();
    clusterMembershipService.addListener(brokerTopologyManager);
    gatewayClusterConfigurationService.addUpdateListener(brokerTopologyManager);
    return brokerTopologyManager;
  }

  @Bean
  public ClusterConfigurationManagementRequestSender clusterManagementRequestSender(
      final BrokerTopologyManager brokerTopologyManager) {
    return new ClusterConfigurationManagementRequestSender(
        clusterCommunicationService,
        ClusterConfigurationCoordinatorSupplier.of(brokerTopologyManager::getClusterConfiguration),
        new ProtoBufSerializer());
  }
}
