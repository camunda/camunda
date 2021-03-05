/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.zeebe.gateway.Gateway.Status;
import io.zeebe.gateway.Loggers;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Health indicator that signals whether the gateway is starting {@code DOWN }, running {@code UP}
 * or shut down {@code OUT_OF_SERVICE}
 */
public class StartedHealthIndicator implements HealthIndicator {

  public static final Logger LOG = Loggers.GATEWAY_LOGGER;

  private final Supplier<Optional<Status>> gatewayStatusSupplier;

  public StartedHealthIndicator(final Supplier<Optional<Status>> gatewayStatusSupplier) {
    this.gatewayStatusSupplier = requireNonNull(gatewayStatusSupplier);
  }

  @Override
  public Health health() {
    final Optional<Status> optStatus = gatewayStatusSupplier.get();

    if (optStatus.isEmpty()) {
      return Health.unknown().build();
    } else {
      final var status = optStatus.get();
      switch (status) {
        case INITIAL:
        case STARTING:
          return Health.down().build();
        case RUNNING:
          return Health.up().build();
        case SHUTDOWN:
          return Health.outOfService().build();
        default:
          LOG.warn("Encountered unexpected status " + status);
          return Health.unknown().build();
      }
    }
  }
}
