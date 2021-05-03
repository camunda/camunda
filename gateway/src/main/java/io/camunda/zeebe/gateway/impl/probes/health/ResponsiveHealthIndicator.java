/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.zeebe.client.ZeebeClient;
import io.zeebe.client.ZeebeClientBuilder;
import io.zeebe.gateway.impl.configuration.GatewayCfg;
import java.time.Duration;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator that sends a request to the gateway to check its responsiveness. The request
 * must yield a response within a given timeout for this health indicator to report {@code
 * Status.UP}
 */
public class ResponsiveHealthIndicator implements HealthIndicator {
  private final GatewayCfg gatewayCfg;
  private final Duration defaultTimeout;

  private ZeebeClient zeebeClient;

  public ResponsiveHealthIndicator(final GatewayCfg gatewayCfg, final Duration defaultTimeout) {
    this.gatewayCfg = requireNonNull(gatewayCfg);

    requireNonNull(defaultTimeout);

    if (defaultTimeout.toMillis() <= 0) {
      throw new IllegalArgumentException();
    }

    this.defaultTimeout = defaultTimeout;
  }

  GatewayCfg getGatewayCfg() {
    return gatewayCfg;
  }

  Duration getDefaultTimeout() {
    return defaultTimeout;
  }

  @Override
  public Health health() {
    final var zeebeClient = supplyZeebeClient();
    Builder resultBuilder;

    if (zeebeClient == null) {
      resultBuilder = Health.unknown();
    } else {
      try {
        zeebeClient.newTopologyRequest().send().get();
        resultBuilder = Health.up();
      } catch (Throwable t) {
        resultBuilder = Health.down().withException(t);
      }
    }

    return resultBuilder.withDetail("timeOut", defaultTimeout).build();
  }

  ZeebeClient supplyZeebeClient() {
    if (zeebeClient == null && gatewayCfg.isInitialized()) {
      zeebeClient = createZeebeClient(gatewayCfg, defaultTimeout);
    }

    return zeebeClient;
  }

  static ZeebeClient createZeebeClient(final GatewayCfg gatewayCfg, final Duration defaultTimeout) {
    final String gatewayAddress = getContactPoint(gatewayCfg);

    ZeebeClientBuilder clientBuilder =
        ZeebeClient.newClientBuilder()
            .gatewayAddress(gatewayAddress)
            .defaultRequestTimeout(defaultTimeout);

    if (gatewayCfg.getSecurity().isEnabled()) {
      clientBuilder =
          clientBuilder.caCertificatePath(gatewayCfg.getSecurity().getCertificateChainPath());
    } else {
      clientBuilder = clientBuilder.usePlaintext();
    }

    return clientBuilder.build();
  }

  static String getContactPoint(final GatewayCfg gatewayCfg) {
    final String host = gatewayCfg.getNetwork().getHost();
    final int port = gatewayCfg.getNetwork().getPort();

    return host + ":" + port;
  }
}
