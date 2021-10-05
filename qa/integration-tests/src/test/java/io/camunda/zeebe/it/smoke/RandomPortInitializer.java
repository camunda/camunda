/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.smoke;

import io.camunda.zeebe.test.util.socket.SocketUtil;
import java.util.Map;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * An {@link ApplicationContextInitializer} which sets all known ports to random ports. This allows
 * us to run multiple tests in parallel without running into port collisions. To simplify things,
 * and because we can not parameterize initializers, we have a single initializer which sets all
 * ports for both the broker and the gateway. This is safe as both applications isolated
 * configuration prefixes.
 */
public final class RandomPortInitializer
    implements ApplicationContextInitializer<ConfigurableApplicationContext> {
  private final int gatewayPort = SocketUtil.getNextAddress().getPort();
  private final int commandPort = SocketUtil.getNextAddress().getPort();
  private final int internalPort = SocketUtil.getNextAddress().getPort();
  private final int monitoringPort = SocketUtil.getNextAddress().getPort();

  @Override
  public void initialize(final ConfigurableApplicationContext applicationContext) {
    final var environment = applicationContext.getEnvironment();
    final var sources = environment.getPropertySources();

    sources.addFirst(
        new MapPropertySource(
            "random ports",
            Map.of(
                "zeebe.gateway.network.port", gatewayPort,
                "zeebe.gateway.monitoring.port", monitoringPort,
                "zeebe.broker.gateway.network.port", gatewayPort,
                "zeebe.broker.network.commandApi.port", commandPort,
                "zeebe.broker.network.internalApi.port", internalPort,
                "zeebe.broker.network.monitoringApi.port", monitoringPort)));
  }
}
