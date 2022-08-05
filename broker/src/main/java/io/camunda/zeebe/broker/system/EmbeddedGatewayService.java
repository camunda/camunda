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
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import java.util.function.Function;

public final class EmbeddedGatewayService implements AutoCloseable {
  private final Gateway gateway;

  public EmbeddedGatewayService(
      final BrokerCfg configuration,
      final ActorScheduler actorScheduler,
      final MessagingService messagingService,
      final ClusterMembershipService membershipService,
      final ClusterEventService eventService) {
    final Function<GatewayCfg, BrokerClient> brokerClientFactory =
        cfg ->
            new BrokerClientImpl(
                cfg, messagingService, membershipService, eventService, actorScheduler);
    gateway = new Gateway(configuration.getGateway(), brokerClientFactory, actorScheduler);
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
    return gateway.start();
  }
}
