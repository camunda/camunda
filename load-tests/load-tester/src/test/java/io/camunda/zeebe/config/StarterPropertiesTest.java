/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class StarterPropertiesTest {

  @Test
  void shouldDeriveMaxInFlightFromRateAndMultiplier() {
    // given
    final var props = new StarterProperties();
    props.setRate(150);
    props.setRateDuration(Duration.ofSeconds(1));
    props.setMaxInFlightRateMultiplier(10);

    // when / then
    assertThat(props.getMaxInFlightRequests()).isEqualTo(1500);
  }

  @Test
  void shouldRoundMaxInFlightUp() {
    // given - non-integer rate per second
    final var props = new StarterProperties();
    props.setRate(1);
    props.setRateDuration(Duration.ofSeconds(3));
    props.setMaxInFlightRateMultiplier(2);

    // when / then - ceil(0.333.. * 2) = ceil(0.666..) = 1
    assertThat(props.getMaxInFlightRequests()).isEqualTo(1);
  }

  @Test
  void shouldDisableCapWhenMultiplierIsZero() {
    // given
    final var props = new StarterProperties();
    props.setRate(150);
    props.setMaxInFlightRateMultiplier(0);

    // when / then
    assertThat(props.getMaxInFlightRequests()).isZero();
  }

  @Test
  void shouldDisableCapWhenMultiplierIsNegative() {
    // given
    final var props = new StarterProperties();
    props.setRate(150);
    props.setMaxInFlightRateMultiplier(-1);

    // when / then
    assertThat(props.getMaxInFlightRequests()).isZero();
  }

  @Test
  void shouldDefaultMultiplierToTen() {
    // given
    final var props = new StarterProperties();

    // when / then
    assertThat(props.getMaxInFlightRateMultiplier()).isEqualTo(10);
  }
}
