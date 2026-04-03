/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.archiver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PIDControllerTest {

  private PIDController pid;

  @BeforeEach
  public void setup() {
    final Clock clock = mock(Clock.class);
    final var seconds = new AtomicInteger();
    when(clock.millis()).then(inv -> seconds.getAndIncrement() * 1000L);
    pid = new PIDController(clock, 0.5, 0.01, 0.1);
  }

  @Test
  void shouldOnlyUseProportionalTermOnFirstCall() {
    pid.setTarget(1.0);
    assertThat(pid.update(0.0)).isEqualTo(0.5); // only proportional term (error = 1.0)
  }

  @Test
  void shouldOtherTermsOnSecondCall() {
    pid.setTarget(1.0);
    pid.update(0.0);
    // proportional only would be 0.25, so verify the other parts have an effect
    assertThat(pid.update(0.5)).isCloseTo(0.205, within(0.001));
  }

  @Test
  void test() {
    pid.setTarget(1.0);

    var d = 0.0;
    for (int i = 0; i < 100; i++) {
      final var prev = d;
      d += pid.update(d);
      System.out.println(i + " d: " + d + (prev < d ? " (increasing)" : " (decreasing)"));
    }
  }
}
