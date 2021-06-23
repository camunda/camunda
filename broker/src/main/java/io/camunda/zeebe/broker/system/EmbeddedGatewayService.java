/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system;

import io.atomix.core.Atomix;
import io.camunda.zeebe.broker.system.configuration.BrokerCfg;
import io.camunda.zeebe.gateway.Gateway;
import io.camunda.zeebe.gateway.impl.broker.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.camunda.zeebe.gateway.impl.configuration.GatewayCfg;
import io.camunda.zeebe.util.sched.ActorScheduler;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

public final class EmbeddedGatewayService implements AutoCloseable {
  private final Gateway gateway;

  public EmbeddedGatewayService(
      final BrokerCfg configuration, final ActorScheduler actorScheduler, final Atomix atomix) {
    final Function<GatewayCfg, BrokerClient> brokerClientFactory =
        cfg ->
            new BrokerClientImpl(
                cfg,
                atomix.getMessagingService(),
                atomix.getMembershipService(),
                atomix.getEventService(),
                actorScheduler,
                false);
    gateway = new Gateway(configuration.getGateway(), brokerClientFactory, actorScheduler);
    startGateway();
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

  private void startGateway() {
    try {
      gateway.start();
    } catch (final IOException e) {
      throw new UncheckedIOException("Gateway was not able to start", e);
    }
  }
}
