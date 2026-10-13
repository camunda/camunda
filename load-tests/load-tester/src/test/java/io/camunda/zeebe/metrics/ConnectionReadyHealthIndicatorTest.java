/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.health.contributor.Status;

class ConnectionReadyHealthIndicatorTest {

  @Test
  void shouldRefuseTrafficUntilConnected() {
    // given
    final var monitor = mock(ConnectionMonitor.class);
    when(monitor.isConnected()).thenReturn(false);
    final var indicator =
        new ConnectionReadyHealthIndicator(mock(ApplicationAvailability.class), monitor);

    // when
    final var health = indicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
  }

  @Test
  void shouldAcceptTrafficOnceConnected() {
    // given
    final var monitor = mock(ConnectionMonitor.class);
    when(monitor.isConnected()).thenReturn(true);
    final var indicator =
        new ConnectionReadyHealthIndicator(mock(ApplicationAvailability.class), monitor);

    // when
    final var health = indicator.health();

    // then
    assertThat(health.getStatus()).isEqualTo(Status.UP);
  }
}
