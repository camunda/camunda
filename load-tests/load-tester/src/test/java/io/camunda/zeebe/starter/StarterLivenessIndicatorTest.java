/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.starter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.config.StarterProperties;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

class StarterLivenessIndicatorTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
  private final InstantSource clock = now::get;

  private StarterLivenessIndicator indicator() {
    final var properties = new StarterProperties();
    properties.setLivenessMaxNoSuccessAge(Duration.ofSeconds(60));
    return new StarterLivenessIndicator(properties, clock);
  }

  private void advance(final long seconds) {
    now.set(now.get().plusSeconds(seconds));
  }

  @Test
  void shouldBeUpBeforeTheStarterIsConnected() {
    // given
    final var indicator = indicator();

    // when
    advance(3600);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeUpWhileRequestsSucceed() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    advance(50);
    indicator.recordResult(null);
    advance(50);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeDownWhenNothingSucceededWithinTheAgeAfterStart() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    advance(61);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldBeDownWhenOnlyFailuresArrive() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    for (int i = 0; i < 61; i++) {
      advance(1);
      indicator.recordResult(
          new CompletionException(new StatusRuntimeException(io.grpc.Status.UNAUTHENTICATED)));
    }

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.DOWN);
  }

  @Test
  void shouldTreatBackpressureAsAlive() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();

    // when
    for (int i = 0; i < 120; i++) {
      advance(1);
      indicator.recordResult(new StatusRuntimeException(io.grpc.Status.RESOURCE_EXHAUSTED));
    }

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void shouldBeUpAfterRunFinished() {
    // given
    final var indicator = indicator();
    indicator.recordStarted();
    indicator.recordFinished();

    // when
    advance(3600);

    // then
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }
}
