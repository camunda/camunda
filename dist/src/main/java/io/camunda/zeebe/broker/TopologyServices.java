/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.camunda.zeebe.broker.client.api.BrokerClientTopologyMetrics;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.broker.client.impl.BrokerTopologyManagerImpl;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.topology.api.TopologyCoordinatorSupplier.ClusterTopologyAwareCoordinatorSupplier;
import io.camunda.zeebe.topology.api.TopologyManagementRequestSender;
import io.camunda.zeebe.topology.serializer.ProtoBufSerializer;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class TopologyServices {
  private final ActorScheduler scheduler;

  private final ClusterMembershipService clusterMembershipService;
  private final ClusterCommunicationService clusterCommunicationService;
  private final BrokerClientTopologyMetrics metrics;

  @Autowired
  public TopologyServices(
      final ActorScheduler scheduler,
      final AtomixCluster atomixCluster,
      final MeterRegistry registry) {
    this.scheduler = scheduler;
    clusterMembershipService = atomixCluster.getMembershipService();
    clusterCommunicationService = atomixCluster.getCommunicationService();
    metrics = BrokerClientTopologyMetrics.of(registry);
  }

  @Bean
  BrokerTopologyManager brokerTopologyManager() {
    final var brokerTopologyManager =
        new BrokerTopologyManagerImpl(clusterMembershipService::getMembers, metrics);
    scheduler.submitActor(brokerTopologyManager).join();
    clusterMembershipService.addListener(brokerTopologyManager);
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
