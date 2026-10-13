/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.application.ReadinessStateHealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness indicator that reports {@link ReadinessState#ACCEPTING_TRAFFIC} once the {@link
 * ConnectionMonitor} has retrieved the cluster topology.
 */
@Component
public class ConnectionReadyHealthIndicator extends ReadinessStateHealthIndicator {

  private final ConnectionMonitor connectionMonitor;

  public ConnectionReadyHealthIndicator(
      final ApplicationAvailability availability, final ConnectionMonitor connectionMonitor) {
    super(availability);
    this.connectionMonitor = connectionMonitor;
  }

  @Override
  protected AvailabilityState getState(final ApplicationAvailability applicationAvailability) {
    return connectionMonitor.isConnected()
        ? ReadinessState.ACCEPTING_TRAFFIC
        : ReadinessState.REFUSING_TRAFFIC;
  }
}
