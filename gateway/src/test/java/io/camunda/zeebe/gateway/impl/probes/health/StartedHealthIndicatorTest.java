/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.gateway.impl.probes.health;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.health.Status;
import io.micronaut.health.HealthStatus;
import java.time.Duration;
import java.util.Optional;
import org.junit.Test;
import reactor.core.publisher.Mono;

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
    final var actualHealth = sutHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    assertThat(healthResult).isNotNull();
    assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.UNKNOWN);
  }

  @Test
  public void shouldReportDownWhenGatewayStateIsInitial() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.INITIAL));

    // when
    final var actualHealth = sutHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    assertThat(healthResult).isNotNull();
    assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  public void shouldReportDownWhenGatewayStateIsStarting() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.STARTING));

    // when
    final var actualHealth = sutHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    assertThat(healthResult).isNotNull();
    assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.DOWN);
  }

  @Test
  public void shouldReportUpWhenGatewayStateIsRunning() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.RUNNING));

    // when
    final var actualHealth = sutHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    assertThat(healthResult).isNotNull();
    assertThat(healthResult.getStatus()).isEqualTo(HealthStatus.UP);
  }

  @Test
  public void shouldReportOutOfServiceWhenGatewayStateIsShutdown() {
    // given
    final StartedHealthIndicator sutHealthIndicator =
        new StartedHealthIndicator(() -> Optional.of(Status.SHUTDOWN));

    // when
    final var actualHealth = sutHealthIndicator.getResult();
    final var healthResult = Mono.from(actualHealth).block(Duration.ofMillis(5000));

    // then
    assertThat(healthResult).isNotNull();
    assertThat(healthResult.getStatus()).isEqualTo(new HealthStatus("OUT_OF_SERVICE"));
  }
}
