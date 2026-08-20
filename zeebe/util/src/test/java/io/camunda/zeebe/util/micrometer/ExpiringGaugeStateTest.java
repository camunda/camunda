/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.micrometer;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpiringGaugeStateTest {
  @Mock private Clock clock;
  private ExpiringGaugeState state;

  @BeforeEach
  public void setup() {
    state = new ExpiringGaugeState(clock, Duration.ofSeconds(60), 0L);
  }

  @Test
  void getReturnsZeroByDefault() {
    assertThat(state.get()).isEqualTo(0L);
  }

  @Test
  void getReturnsPreviouslySetValueIfNotExpired() {
    state.set(99L);
    assertThat(state.get()).isEqualTo(99L);
  }

  @Test
  void getReturnsZeroAfterValueExpires() {
    when(clock.millis()).thenReturn(0L, 30_000L, 60_000L, 61_000L);

    state.set(101L);

    // not expired yet
    assertThat(state.get()).isEqualTo(101L);
    assertThat(state.get()).isEqualTo(101L);

    // should have expired now
    assertThat(state.get()).isEqualTo(0L);
  }
}
