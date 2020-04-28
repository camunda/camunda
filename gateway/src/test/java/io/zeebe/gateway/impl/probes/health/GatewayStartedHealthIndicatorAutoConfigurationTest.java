/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.gateway.Gateway.Status;
import io.zeebe.gateway.impl.SpringGatewayBridge;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.actuate.health.Health;

public class GatewayStartedHealthIndicatorAutoConfigurationTest {

  private SpringGatewayBridge helperGatewayBridge;

  private GatewayStartedHealthIndicatorAutoConfiguration sutAutoConfig;

  @Before
  public void setUp() {
    helperGatewayBridge = new SpringGatewayBridge();
    sutAutoConfig = new GatewayStartedHealthIndicatorAutoConfiguration();
  }

  @Test
  public void shouldCreateHealthIndicatorEvenBeforeGatewayStatusSupplierIsRegistered() {
    // when
    final GatewayStartedHealthIndicator actual =
        sutAutoConfig.gatewayStartedHealthIndicator(helperGatewayBridge);

    // then
    assertThat(actual).isNotNull();
  }

  @Test
  public void
      shouldCreateHealthIndicatorThatReportsHealthBasedOnResultOfRegisteredGatewayStatusSupplier() {
    // given
    final Supplier<Status> statusSupplier = () -> Status.RUNNING;
    final GatewayStartedHealthIndicator healthIndicator =
        sutAutoConfig.gatewayStartedHealthIndicator(helperGatewayBridge);

    // when
    helperGatewayBridge.registerGatewayStatusSupplier(statusSupplier);
    final Health actualHealth = healthIndicator.health();

    // then
    assertThat(actualHealth).isNotNull();
    assertThat(actualHealth.getStatus())
        .isSameAs(org.springframework.boot.actuate.health.Status.UP);
  }
}
