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
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public final class BrokerStartupHealthIndicator implements HealthIndicator {
  private static final Health STARTED = Health.up().build();
  private static final Health NOT_STARTED = Health.down().build();

  private final SpringBrokerBridge brokerBridge;

  // cache the result since once started, the broker remains started
  private boolean isStarted;

  @Autowired
  public BrokerStartupHealthIndicator(final SpringBrokerBridge brokerBridge) {
    this.brokerBridge = brokerBridge;
  }

  @Override
  public Health health() {
    if (isStarted) {
      return STARTED;
    }

    isStarted =
        brokerBridge
            .getBrokerHealthCheckService()
            .map(BrokerHealthCheckService::isBrokerStarted)
            .orElse(false);

    return isStarted ? STARTED : NOT_STARTED;
  }
}
