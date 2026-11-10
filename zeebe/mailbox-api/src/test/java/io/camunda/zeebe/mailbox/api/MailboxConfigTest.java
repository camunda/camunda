/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class MailboxConfigTest {

  private static final Duration ONE_SECOND = Duration.ofSeconds(1);

  @Test
  void shouldProvideValidDefaults() {
    // when
    final var defaults = MailboxConfig.defaults();

    // then
    assertThat(defaults.minPollInterval()).isLessThanOrEqualTo(defaults.maxPollInterval());
    assertThat(defaults.batchSize()).isPositive();
  }

  @Test
  void shouldAcceptEqualPollIntervals() {
    // when / then
    assertThatCode(() -> config(ONE_SECOND, ONE_SECOND, ONE_SECOND, 1)).doesNotThrowAnyException();
  }

  @Test
  void shouldRejectMaxPollIntervalBelowMin() {
    // when / then
    assertThatThrownBy(() -> config(Duration.ofSeconds(2), ONE_SECOND, ONE_SECOND, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("maxPollInterval");
  }

  @Test
  void shouldRejectNonPositiveDurations() {
    // when / then
    assertThatThrownBy(() -> config(Duration.ZERO, ONE_SECOND, ONE_SECOND, 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("minPollInterval");
    assertThatThrownBy(() -> config(ONE_SECOND, ONE_SECOND, Duration.ofSeconds(-1), 1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("claimTimeout");
  }

  @Test
  void shouldRejectNonPositiveBatchSize() {
    // when / then
    assertThatThrownBy(() -> config(ONE_SECOND, ONE_SECOND, ONE_SECOND, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("batchSize");
  }

  private static MailboxConfig config(
      final Duration minPoll,
      final Duration maxPoll,
      final Duration claimTimeout,
      final int batchSize) {
    return new MailboxConfig(minPoll, maxPoll, claimTimeout, batchSize, ONE_SECOND, ONE_SECOND);
  }
}
