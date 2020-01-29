/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.broker.system;

import io.atomix.core.Atomix;
import io.zeebe.broker.system.configuration.BrokerCfg;
import io.zeebe.gateway.Gateway;
import io.zeebe.gateway.impl.broker.BrokerClientFactory;
import io.zeebe.gateway.impl.broker.BrokerClientImpl;
import io.zeebe.util.sched.ActorScheduler;
import java.io.IOException;
import java.io.UncheckedIOException;

public final class EmbeddedGatewayService implements AutoCloseable {
  private final Gateway gateway;

  public EmbeddedGatewayService(
      final BrokerCfg configuration, final ActorScheduler actorScheduler, final Atomix atomix) {
    final BrokerClientFactory brokerClientFactory =
        (cfg, tracer) -> new BrokerClientImpl(cfg, atomix, tracer, actorScheduler, false);
    gateway = new Gateway(configuration.getGateway(), actorScheduler, brokerClientFactory);
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
