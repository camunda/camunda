/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.health.Status;
import io.camunda.zeebe.gateway.impl.MicronautGatewayBridge;
import io.micronaut.health.HealthStatus;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import reactor.core.publisher.Mono;

public class StartedHealthIndicatorAutoConfigurationTest {

  private MicronautGatewayBridge helperGatewayBridge;

  private StartedHealthIndicatorAutoConfiguration sutAutoConfig;

  @Before
  public void setUp() {
    helperGatewayBridge = new MicronautGatewayBridge();
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
    final var actualHealth = healthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    assertThat(healthResult).isNotNull();
    assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.UP);
  }
}
