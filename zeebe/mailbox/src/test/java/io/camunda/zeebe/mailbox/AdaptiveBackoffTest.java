/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.mailbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class AdaptiveBackoffTest {

  private final AdaptiveBackoff backoff =
      new AdaptiveBackoff(Duration.ofMillis(10), Duration.ofMillis(100));

  @Test
  void shouldDoubleTheWaitWhileIdleUpToTheMaximum() {
    // when
    final var waits =
        java.util.stream.Stream.generate(backoff::onIdle).limit(6).map(Duration::toMillis).toList();

    // then
    assertThat(waits).containsExactly(20L, 40L, 80L, 100L, 100L, 100L);
  }

  @Test
  void shouldReturnToTheMinimumAfterWork() {
    // given
    backoff.onIdle();
    backoff.onIdle();

    // when
    final var afterWork = backoff.onWork();
    final var nextIdle = backoff.onIdle();

    // then
    assertThat(afterWork).isEqualTo(Duration.ofMillis(10));
    assertThat(nextIdle).isEqualTo(Duration.ofMillis(20));
  }
}
