/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.dynamic.config.GatewayClusterConfigurationService;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationCoordinatorSupplier.ClusterClusterConfigurationAwareCoordinatorSupplier;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.gossip.ClusterConfigurationGossiperConfig;
import io.camunda.zeebe.dynamic.config.serializer.ProtoBufSerializer;
import io.camunda.zeebe.scheduler.ActorScheduler;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class DynamicClusterServices {
  private final ActorScheduler scheduler;
  private final ClusterMembershipService clusterMembershipService;
  private final ClusterCommunicationService clusterCommunicationService;

  @Autowired
  public DynamicClusterServices(final ActorScheduler scheduler, final AtomixCluster atomixCluster) {
    this.scheduler = scheduler;
    clusterMembershipService = atomixCluster.getMembershipService();
    clusterCommunicationService = atomixCluster.getCommunicationService();
  }

  @Bean
  GatewayClusterConfigurationService gatewayClusterTopologyService() {
    final var service =
        new GatewayClusterConfigurationService(
            clusterCommunicationService,
            clusterMembershipService,
            new ClusterConfigurationGossiperConfig(
                false, Duration.ofSeconds(10), Duration.ofSeconds(1), 2));
    scheduler.submitActor(service).join();
    return service;
  }

  @Bean
  BrokerTopologyManager brokerTopologyManager(
      @Autowired final GatewayClusterConfigurationService gatewayClusterTopologyService) {
    final var brokerTopologyManager =
        new BrokerTopologyManagerImpl(clusterMembershipService::getMembers);
    scheduler.submitActor(brokerTopologyManager).join();
    clusterMembershipService.addListener(brokerTopologyManager);
    gatewayClusterTopologyService.addUpdateListener(brokerTopologyManager);

    return brokerTopologyManager;
  }

  @Bean
  ClusterConfigurationManagementRequestSender clusterManagementRequestSender(
      final BrokerTopologyManager brokerTopologyManager) {
    return new ClusterConfigurationManagementRequestSender(
        clusterCommunicationService,
        new ClusterClusterConfigurationAwareCoordinatorSupplier(
            brokerTopologyManager::getClusterConfiguration),
        new ProtoBufSerializer());
  }
}
