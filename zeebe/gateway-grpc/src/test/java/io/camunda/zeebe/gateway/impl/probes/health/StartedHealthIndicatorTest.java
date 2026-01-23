/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.health.Status;
import java.util.Optional;
import org.junit.Test;
import org.springframework.boot.health.contributor.Health;

public class StartedHealthIndicatorTest {

  @Test
  public void shouldRejectNullInConstructor() {
    assertThatThrownBy(() -> new StartedHealthIndicator(null))
        .isExactlyInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportUnknownWhenGatewayStateIsEmpty() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.empty());

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus())
        .isSameAs(org.springframework.boot.health.contributor.Status.UNKNOWN);
  }

  @Test
  public void shouldReportDownWhenGatewayStateIsInitial() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.INITIAL));

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus())
        .isSameAs(org.springframework.boot.health.contributor.Status.DOWN);
  }

  @Test
  public void shouldReportDownWhenGatewayStateIsStarting() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.STARTING));

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus())
        .isSameAs(org.springframework.boot.health.contributor.Status.DOWN);
  }

  @Test
  public void shouldReportUpWhenGatewayStateIsRunning() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.RUNNING));

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus()).isSameAs(org.springframework.boot.health.contributor.Status.UP);
  }

  @Test
  public void shouldReportOutOfServiceWhenGatewayStateIsShutdown() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.SHUTDOWN));

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus())
        .isSameAs(org.springframework.boot.health.contributor.Status.OUT_OF_SERVICE);
  }
}
