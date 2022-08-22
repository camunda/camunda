/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway;

import io.atomix.cluster.AtomixCluster;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
final class BrokerClientComponent {
  final GatewayCfg config;
  final AtomixCluster atomixCluster;
  final ActorScheduler actorScheduler;

  @Autowired
  BrokerClientComponent(
      final GatewayCfg config,
      final AtomixCluster atomixCluster,
      final ActorScheduler actorScheduler) {
    this.config = config;
    this.atomixCluster = atomixCluster;
    this.actorScheduler = actorScheduler;
  }

  @Bean("brokerClient")
  BrokerClient createBrokerClient() {
    return new BrokerClientImpl(
        config,
        atomixCluster.getMessagingService(),
        atomixCluster.getMembershipService(),
        atomixCluster.getEventService(),
        actorScheduler);
  }
}
