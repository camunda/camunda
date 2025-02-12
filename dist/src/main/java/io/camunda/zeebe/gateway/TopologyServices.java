/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.topology.GatewayClusterTopologyService;
import io.camunda.zeebe.topology.api.TopologyCoordinatorSupplier.ClusterTopologyAwareCoordinatorSupplier;
import io.camunda.zeebe.topology.api.TopologyManagementRequestSender;
import io.camunda.zeebe.topology.gossip.ClusterTopologyGossiperConfig;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class TopologyServices {
  private final ActorScheduler scheduler;
  private final ClusterMembershipService clusterMembershipService;
  private final ClusterCommunicationService clusterCommunicationService;
  private final BrokerClientTopologyMetrics brokerClientTopologyMetrics;
  private final MeterRegistry meterRegistry;

  @Autowired
  public TopologyServices(
      final ActorScheduler scheduler,
      final AtomixCluster atomixCluster,
      final MeterRegistry registry) {
    this.scheduler = scheduler;
    clusterMembershipService = atomixCluster.getMembershipService();
    clusterCommunicationService = atomixCluster.getCommunicationService();
    brokerClientTopologyMetrics = BrokerClientTopologyMetrics.of(registry);
    meterRegistry = registry;
  }

  @Bean
  GatewayClusterTopologyService gatewayClusterTopologyService() {
    final var service =
        new GatewayClusterTopologyService(
            clusterCommunicationService,
            clusterMembershipService,
            new ClusterTopologyGossiperConfig(
                false, Duration.ofSeconds(10), Duration.ofSeconds(1), 2),
            meterRegistry);
    scheduler.submitActor(service).join();
    return service;
  }

  @Bean
  BrokerTopologyManager brokerTopologyManager(
      @Autowired final GatewayClusterTopologyService gatewayClusterTopologyService) {
    final var brokerTopologyManager =
        new BrokerTopologyManagerImpl(
            clusterMembershipService::getMembers, brokerClientTopologyMetrics);
    scheduler.submitActor(brokerTopologyManager).join();
    clusterMembershipService.addListener(brokerTopologyManager);
    gatewayClusterTopologyService.addUpdateListener(brokerTopologyManager);

    return brokerTopologyManager;
  }

  @Bean
  TopologyManagementRequestSender topologyManagementRequestSender(
      final BrokerTopologyManager brokerTopologyManager) {
    return new TopologyManagementRequestSender(
        clusterCommunicationService,
        new ClusterTopologyAwareCoordinatorSupplier(brokerTopologyManager::getClusterTopology),
        new ProtoBufSerializer());
  }
}
