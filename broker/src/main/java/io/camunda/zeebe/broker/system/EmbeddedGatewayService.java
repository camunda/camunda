/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system;

import io.atomix.cluster.ClusterMembershipService;
import io.atomix.cluster.messaging.ClusterEventService;
import io.atomix.cluster.messaging.MessagingService;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.scheduler.ActorSchedulingService;
import io.camunda.zeebe.scheduler.future.ActorFuture;

public final class EmbeddedGatewayService implements AutoCloseable {
  private final Gateway gateway;
  private final BrokerClientImpl brokerClient;

  public EmbeddedGatewayService(
      final BrokerCfg configuration,
      final ActorSchedulingService actorScheduler,
      final MessagingService messagingService,
      final ClusterMembershipService membershipService,
      final ClusterEventService eventService) {
    brokerClient =
        new BrokerClientImpl(
            configuration.getGateway(),
            messagingService,
            membershipService,
            eventService,
            actorScheduler);
    gateway = new Gateway(configuration.getGateway(), brokerClient, actorScheduler);
  }

  @Override
  public void close() {
    if (gateway != null) {
      gateway.stop();
    }
  }

  public Gateway get() {
    return gateway;
  }

  public ActorFuture<Gateway> start() {
    brokerClient.start();
    return gateway.start();
  }
}
