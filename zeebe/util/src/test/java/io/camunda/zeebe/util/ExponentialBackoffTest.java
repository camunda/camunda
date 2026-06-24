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
}
