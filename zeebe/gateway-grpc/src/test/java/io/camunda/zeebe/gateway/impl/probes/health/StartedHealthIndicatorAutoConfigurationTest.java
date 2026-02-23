/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.health.Status;
import io.camunda.zeebe.gateway.impl.SpringGatewayBridge;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.health.contributor.Health;

public class StartedHealthIndicatorAutoConfigurationTest {

  private SpringGatewayBridge helperGatewayBridge;

  private StartedHealthIndicatorAutoConfiguration sutAutoConfig;

  @Before
  public void setUp() {
    helperGatewayBridge = new SpringGatewayBridge();
    sutAutoConfig = new StartedHealthIndicatorAutoConfiguration();
  }

  @Test
  public void shouldCreateHealthIndicatorEvenBeforeGatewayStatusSupplierIsRegistered() {
    // when
    final StartedHealthIndicator actual =
        sutAutoConfig.gatewayStartedHealthIndicator(helperGatewayBridge);

    // then
    assertThat(actual).isNotNull();
  }

  @Test
  public void
      shouldCreateHealthIndicatorThatReportsHealthBasedOnResultOfRegisteredGatewayStatusSupplier() {
    // given
    final Supplier<Status> statusSupplier = () -> Status.RUNNING;
    final StartedHealthIndicator healthIndicator =
        sutAutoConfig.gatewayStartedHealthIndicator(helperGatewayBridge);

    // when
    helperGatewayBridge.registerGatewayStatusSupplier(statusSupplier);
    final Health actualHealth = healthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus())
        .isSameAs(org.springframework.boot.health.contributor.Status.UP);
  }
}
