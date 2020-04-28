/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.zeebe.gateway.Gateway.Status;
import org.junit.Test;
import org.springframework.boot.actuate.health.Health;

public class GatewayStartedHealthIndicatorTest {

  @Test
  public void shouldRejectNullInConstructor() {
    assertThatThrownBy(() -> new GatewayStartedHealthIndicator(null))
        .isExactlyInstanceOf(NullPointerException.class);
  }

  @Test
  public void shouldReportUnknownWhenGatewayStateIsNull() {
    // given
    final GatewayStartedHealthIndicator sutHealthIndicator =
        new GatewayStartedHealthIndicator(() -> null);

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus()).isSameAs(org.springframework.boot.actuate.health.Status.UNKNOWN);
  }

  @Test
  public void shouldReportDownWhenGatewayStateIsInitial() {
    // given
    final GatewayStartedHealthIndicator sutHealthIndicator =
        new GatewayStartedHealthIndicator(() -> Status.INITIAL);

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus()).isSameAs(org.springframework.boot.actuate.health.Status.DOWN);
  }

  @Test
  public void shouldReportDownWhenGatewayStateIsStarting() {
    // given
    final GatewayStartedHealthIndicator sutHealthIndicator =
        new GatewayStartedHealthIndicator(() -> Status.STARTING);

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus()).isSameAs(org.springframework.boot.actuate.health.Status.DOWN);
  }

  @Test
  public void shouldReportUpWhenGatewayStateIsRunning() {
    // given
    final GatewayStartedHealthIndicator sutHealthIndicator =
        new GatewayStartedHealthIndicator(() -> Status.RUNNING);

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus()).isSameAs(org.springframework.boot.actuate.health.Status.UP);
  }

  @Test
  public void shouldReportOutOfServiceWhenGatewayStateIsShutdown() {
    // given
    final GatewayStartedHealthIndicator sutHealthIndicator =
        new GatewayStartedHealthIndicator(() -> Status.SHUTDOWN);

    // when
    final Health actual = sutHealthIndicator.health();

    // then
    assertThat(actual.getStatus())
        .isSameAs(org.springframework.boot.actuate.health.Status.OUT_OF_SERVICE);
  }
}
