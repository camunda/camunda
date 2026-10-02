/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.config.StarterProperties.WorkloadModel;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class StarterPropertiesTest {

  @Test
  void shouldDeriveMaxInFlightRequestsFromRatePerSecond() {
    // given
    final var properties = new StarterProperties();
    properties.setRate(500);
    properties.setRateDuration(Duration.ofSeconds(1));

    // when
    final var maxInFlight = properties.getMaxInFlightRequests();

    // then
    assertThat(maxInFlight).isEqualTo(5000);
  }

  @Test
  void shouldDeriveMaxInFlightRequestsForNonSecondRateDuration() {
    // given
    final var properties = new StarterProperties();
    properties.setRate(60);
    properties.setRateDuration(Duration.ofMinutes(1));

    // when
    final var maxInFlight = properties.getMaxInFlightRequests();

    // then
    assertThat(maxInFlight).isEqualTo(10);
  }

  @Test
  void shouldAllowAtLeastOneRequestInFlightForVeryLowRates() {
    // given
    final var properties = new StarterProperties();
    properties.setRate(0.001);

    // when
    final var maxInFlight = properties.getMaxInFlightRequests();

    // then
    assertThat(maxInFlight).isEqualTo(1);
  }

  @Test
  void shouldLimitMaxInFlightRequestsToRatePerSecondInClosedWorkloadModel() {
    // given
    final var properties = new StarterProperties();
    properties.setRate(500);
    properties.setWorkloadModel(WorkloadModel.CLOSED);

    // when
    final var maxInFlight = properties.getMaxInFlightRequests();

    // then
    assertThat(maxInFlight).isEqualTo(500);
  }
}
