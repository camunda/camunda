/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

final class ThresholdConditionTest {

  private static final Instant T0 = Instant.parse("2026-11-27T10:00:00Z");

  private final ThresholdCondition condition =
      new ThresholdCondition(
          new ConditionThresholds(100, 50, Duration.ofSeconds(20), Duration.ofSeconds(30)));

  @Test
  void shouldStartOnlyAfterValueStaysAboveThresholdForMinDuration() {
    // given
    condition.update(150, T0);
    condition.update(150, T0.plusSeconds(10));

    // when
    final var started = condition.update(150, T0.plusSeconds(20));

    // then
    assertThat(started).isTrue();
    assertThat(condition.isActive()).isTrue();
    assertThat(condition.since()).isEqualTo(T0);
  }

  @Test
  void shouldNotStartWhenValueDropsBelowThresholdBeforeMinDuration() {
    // given
    condition.update(150, T0);
    condition.update(90, T0.plusSeconds(10));

    // when
    final var started = condition.update(150, T0.plusSeconds(20));

    // then
    assertThat(started).isFalse();
    assertThat(condition.isActive()).isFalse();
  }

  @Test
  void shouldKeepConditionWhileValueStaysAboveClearThreshold() {
    // given
    condition.update(150, T0);
    condition.update(150, T0.plusSeconds(20));

    // when - below the threshold, but above the clear threshold
    final var changed = condition.update(60, T0.plusSeconds(120));

    // then
    assertThat(changed).isFalse();
    assertThat(condition.isActive()).isTrue();
  }

  @Test
  void shouldEndOnlyAfterValueStaysBelowClearThresholdForClearDuration() {
    // given
    condition.update(150, T0);
    condition.update(150, T0.plusSeconds(20));
    condition.update(10, T0.plusSeconds(30));
    condition.update(10, T0.plusSeconds(50));

    // when
    final var ended = condition.update(10, T0.plusSeconds(60));

    // then
    assertThat(ended).isTrue();
    assertThat(condition.isActive()).isFalse();
  }

  @Test
  void shouldRestartClearDurationWhenValueRisesAgain() {
    // given
    condition.update(150, T0);
    condition.update(150, T0.plusSeconds(20));
    condition.update(10, T0.plusSeconds(30));
    condition.update(70, T0.plusSeconds(50));

    // when
    final var ended = condition.update(10, T0.plusSeconds(70));

    // then
    assertThat(ended).isFalse();
    assertThat(condition.isActive()).isTrue();
  }
}
