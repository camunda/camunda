/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.logstreams.impl.flowcontrol;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateMeasurementTest {
  @Test
  void shouldMeasureZeroWithoutObservations() {
    // given
    final var clock = new AtomicLong(0);
    final RateMeasurement rateMeasurement =
        new RateMeasurement(clock::get, Duration.ofMinutes(5), Duration.ofSeconds(10));

    // when
    final double rate = rateMeasurement.rate();

    // then
    assertThat(rate).isEqualTo(0);
  }

  @Test
  void shouldMeasureExpectedRate() {
    // given
    final var clock = new AtomicLong(0);
    final var observationWindow = Duration.ofMinutes(5);
    final RateMeasurement rateMeasurement =
        new RateMeasurement(clock::get, observationWindow, Duration.ofSeconds(10));

    // when
    rateMeasurement.observe(0);
    clock.set(observationWindow.toMillis());
    rateMeasurement.observe(12345);

    // then
    assertThat(rateMeasurement.rate()).isEqualTo(12345 / observationWindow.toSeconds());
  }

  @Test
  void shouldMeasureRate() {
    // given
    final var clock = new AtomicLong(0);
    final var observationWindow = Duration.ofMinutes(5);
    final RateMeasurement rateMeasurement =
        new RateMeasurement(clock::get, observationWindow, Duration.ofSeconds(1));

    // when
    for (var second = 0; second <= observationWindow.toSeconds(); second++) {
      clock.set(second * 1000L);
      rateMeasurement.observe(second * 10L);
    }

    // then
    assertThat(rateMeasurement.rate()).isEqualTo(10);
  }
}
