/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.window;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

final class TumblingWindowsTest {

  private static final long HOUR = 3_600_000L;

  @Test
  void shouldAssignTimestampToWindowStart() {
    // given
    final TumblingWindows hourly = TumblingWindows.of(HOUR);

    // then — floored to the window start
    assertThat(hourly.windowStart(0L)).isZero();
    assertThat(hourly.windowStart(HOUR - 1)).isZero();
    assertThat(hourly.windowStart(HOUR)).isEqualTo(HOUR);
    assertThat(hourly.windowStart(HOUR + 1)).isEqualTo(HOUR);
    assertThat(hourly.windowStart(2 * HOUR + 500)).isEqualTo(2 * HOUR);
  }

  @Test
  void shouldExposeSize() {
    assertThat(TumblingWindows.of(HOUR).sizeMs()).isEqualTo(HOUR);
  }

  @Test
  void shouldRejectNonPositiveSize() {
    assertThatThrownBy(() -> TumblingWindows.of(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
