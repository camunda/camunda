/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.gateway.Loggers;
import io.camunda.zeebe.gateway.health.Status;
import io.micronaut.health.HealthStatus;
import io.micronaut.management.health.indicator.HealthIndicator;
import io.micronaut.management.health.indicator.HealthResult;
import java.util.Optional;
import java.util.function.Supplier;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import reactor.core.publisher.Mono;

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
  public Publisher<HealthResult> getResult() {
    final Optional<Status> optStatus = gatewayStatusSupplier.get();

    final HealthStatus healthStatus;
    if (optStatus.isEmpty()) {
      healthStatus = HealthStatus.UNKNOWN;
    } else {
      final var status = optStatus.get();
      switch (status) {
        case INITIAL:
        case STARTING:
          healthStatus = HealthStatus.DOWN;
          break;
        case RUNNING:
          healthStatus = HealthStatus.UP;
          break;
        case SHUTDOWN:
          healthStatus = new HealthStatus("OUT_OF_SERVICE");
          break;
        default:
          LOG.warn("Encountered unexpected status " + status);
          healthStatus = HealthStatus.UNKNOWN;
      }
    }
    return Mono.just(HealthResult.builder(getClass().getSimpleName(), healthStatus).build());
  }
}
