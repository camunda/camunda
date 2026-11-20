/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class FailureEscalationTest {

  private static final Instant START = Instant.parse("2026-10-16T08:00:00Z");

  private final FailureEscalation failures =
      new FailureEscalation(Duration.ofMinutes(1), Duration.ofHours(1));

  @Test
  void failuresThatPassAreNotWorthAWarning() {
    // when
    final var first = failures.failed(START);
    final var later = failures.failed(START.plusSeconds(59));

    // then
    assertThat(first).isFalse();
    assertThat(later).isFalse();
    assertThat(failures.succeeded()).isFalse();
  }

  @Test
  void failuresThatPersistAreWorthAWarningAndThenOnlyNowAndThen() {
    // given
    failures.failed(START);

    // when
    final var afterAMinute = failures.failed(START.plusSeconds(60));
    final var soonAfter = failures.failed(START.plusSeconds(120));
    final var anHourLater = failures.failed(START.plusSeconds(60 + 3600));

    // then
    assertThat(afterAMinute).isTrue();
    assertThat(soonAfter).isFalse();
    assertThat(anHourLater).isTrue();
  }

  @Test
  void aSuccessAfterAWarningIsWorthWord() {
    // given
    failures.failed(START);
    failures.failed(START.plusSeconds(90));

    // when
    final var failedFor = failures.failingFor(START.plusSeconds(100));
    final var warned = failures.succeeded();

    // then
    assertThat(failedFor).isEqualTo(Duration.ofSeconds(100));
    assertThat(warned).isTrue();
    assertThat(failures.failingFor(START.plusSeconds(200))).isZero();
  }

  @Test
  void aSuccessStartsTheCountAgain() {
    // given
    failures.failed(START);
    failures.succeeded();

    // when
    final var failedAgain = failures.failed(START.plusSeconds(90));

    // then
    assertThat(failedAgain).isFalse();
  }
}
