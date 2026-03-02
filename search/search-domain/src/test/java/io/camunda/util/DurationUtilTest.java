/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

public class DurationUtilTest {

  @Test
  void minReturnsFirstWhenSmaller() {
    assertThat(DurationUtil.min(Duration.ofSeconds(30), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void minReturnsSecondWhenSmaller() {
    assertThat(DurationUtil.min(Duration.ofMinutes(2), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void minReturnsEitherWhenEqual() {
    final var duration = Duration.ofMinutes(1);
    assertThat(DurationUtil.min(duration, duration)).isEqualTo(duration);
  }

  @Test
  void maxReturnsFirstWhenLarger() {
    assertThat(DurationUtil.max(Duration.ofMinutes(2), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofMinutes(2));
  }

  @Test
  void maxReturnsSecondWhenLarger() {
    assertThat(DurationUtil.max(Duration.ofSeconds(30), Duration.ofMinutes(1)))
        .isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  void maxReturnsEitherWhenEqual() {
    final var duration = Duration.ofMinutes(1);
    assertThat(DurationUtil.max(duration, duration)).isEqualTo(duration);
  }
}
