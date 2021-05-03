/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.broker.system.partitions.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.Test;

public class RandomDurationTest {

  @Test
  public void shouldGetRandomDuration() {
    // given
    final Duration minDuration = Duration.ofMinutes(1);
    final Duration maxDuration = Duration.ofMinutes(10);

    // when
    final var randomDuration =
        RandomDuration.getRandomDurationMinuteBased(minDuration, maxDuration);

    // then
    assertThat(randomDuration).isBetween(minDuration, maxDuration);
  }

  @Test
  public void shouldGetRandomDurationOnNegativeMin() {
    // given
    final Duration minDuration = Duration.ofMinutes(-1);
    final Duration maxDuration = Duration.ofMinutes(10);

    // when
    final var randomDuration =
        RandomDuration.getRandomDurationMinuteBased(minDuration, maxDuration);

    // then
    assertThat(randomDuration).isBetween(minDuration, maxDuration);
  }

  @Test
  public void shouldGetRandomDurationOnNegativeDurations() {
    // given
    final Duration minDuration = Duration.ofMinutes(-10);
    final Duration maxDuration = Duration.ofMinutes(-1);

    // when
    final var randomDuration =
        RandomDuration.getRandomDurationMinuteBased(minDuration, maxDuration);

    // then
    assertThat(randomDuration).isBetween(minDuration, maxDuration);
  }

  @Test
  public void shouldGetMinDurationWhenMaxDurationIsSmaller() {
    // given
    final Duration minDuration = Duration.ofMinutes(10);
    final Duration maxDuration = Duration.ofMinutes(1);

    // when
    final var randomDuration =
        RandomDuration.getRandomDurationMinuteBased(minDuration, maxDuration);

    // then
    assertThat(randomDuration).isEqualTo(minDuration);
  }

  @Test
  public void shouldGetMinDurationWhenDiffIsOnlySeconds() {
    // given
    final Duration minDuration = Duration.ofMinutes(1);
    final Duration maxDuration = Duration.ofSeconds(90);

    // when
    final var randomDuration =
        RandomDuration.getRandomDurationMinuteBased(minDuration, maxDuration);

    // then
    assertThat(randomDuration).isEqualTo(minDuration);
  }

  @Test
  public void shouldGetMinDurationWhenMaxDurationIsEquals() {
    // given
    final Duration minDuration = Duration.ofMinutes(1);

    // when
    final var randomDuration =
        RandomDuration.getRandomDurationMinuteBased(minDuration, minDuration);

    // then
    assertThat(randomDuration).isEqualTo(minDuration);
  }
}
