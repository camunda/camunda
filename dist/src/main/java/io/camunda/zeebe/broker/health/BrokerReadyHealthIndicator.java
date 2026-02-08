/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.health;

import io.camunda.zeebe.broker.SpringBrokerBridge;
import io.camunda.zeebe.broker.system.monitoring.BrokerHealthCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.health.application.ReadinessStateHealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class BrokerReadyHealthIndicator extends ReadinessStateHealthIndicator {

  private final SpringBrokerBridge brokerBridge;

  @Autowired
  public BrokerReadyHealthIndicator(
      final ApplicationAvailability availability, final SpringBrokerBridge brokerBridge) {
    super(availability);
    this.brokerBridge = brokerBridge;
  }

  @Override
  protected AvailabilityState getState(final ApplicationAvailability applicationAvailability) {
    final var isBrokerReady =
        brokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerReady)
            .orElse(false);
    return isBrokerReady ? ReadinessState.ACCEPTING_TRAFFIC : ReadinessState.REFUSING_TRAFFIC;
  }
}
