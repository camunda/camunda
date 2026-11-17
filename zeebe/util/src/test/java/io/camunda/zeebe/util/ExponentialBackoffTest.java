/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExponentialBackoffTest {

  @Test
  void shouldThrowExceptionIfMinDelayIsGreaterThanMaxDelay() {
    assertThatThrownBy(() -> new ExponentialBackoff(100L, 200L, 1.2, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldBackoffUntilMaxDelay() {
    final var backoff = new ExponentialBackoff(200L, 100L, 1.4, 0);
    assertThat(backoff.applyAsLong(0L)).isEqualTo(100L);
    assertThat(backoff.applyAsLong(100L)).isEqualTo(140L);
    assertThat(backoff.applyAsLong(140L)).isEqualTo(196L);
    assertThat(backoff.applyAsLong(196L)).isEqualTo(200L);
  }

  @ParameterizedTest
  @ValueSource(doubles = {1.0, 0.5, 0.0, -1.0})
  void shouldThrowExceptionIfBackoffFactorIsNotGreaterThanOne(final double backoffFactor) {
    // given - a backoffFactor of 1.0 or less, which would produce a non-increasing delay

    // when - then
    assertThatThrownBy(() -> new ExponentialBackoff(200L, 100L, backoffFactor, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest
  @ValueSource(doubles = {1.2, 1.6})
  void shouldConstructAndBackoffWithValidBackoffFactor(final double backoffFactor) {
    // given
    final var backoff = new ExponentialBackoff(200L, 10L, backoffFactor, 0);

    // when
    final var firstDelay = backoff.applyAsLong(10L);
    final var secondDelay = backoff.applyAsLong(firstDelay);

    // then
    assertThat(firstDelay).isGreaterThan(10L);
    assertThat(secondDelay).isGreaterThan(firstDelay);
  }
}
