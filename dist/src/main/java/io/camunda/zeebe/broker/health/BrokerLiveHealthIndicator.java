/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.availability.LivenessStateHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.LivenessState;
import org.springframework.stereotype.Component;

@Component
public final class BrokerLiveHealthIndicator extends LivenessStateHealthIndicator {

  private final SpringBrokerBridge brokerBridge;

  @Autowired
  public BrokerLiveHealthIndicator(
      final ApplicationAvailability availability, final SpringBrokerBridge brokerBridge) {
    super(availability);
    this.brokerBridge = brokerBridge;
  }

  @Override
  public Health getHealth(final boolean includeDetails) {
    return super.getHealth(includeDetails);
  }

  @Override
  protected AvailabilityState getState(final ApplicationAvailability applicationAvailability) {
    final var isBrokerHealthy =
        brokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerHealthy)
            .orElse(false);
    return isBrokerHealthy ? LivenessState.CORRECT : LivenessState.BROKEN;
  }
}
