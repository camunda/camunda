/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

final class LongPollingTest {

  private final LongPolling longPolling = new LongPolling();

  @Test
  void shouldAcceptZeroNotificationBatchWindow() {
    // when / then
    assertThatCode(() -> longPolling.setNotificationBatchWindow(Duration.ZERO))
        .doesNotThrowAnyException();
  }

  @Test
  void shouldRejectNegativeNotificationBatchWindow() {
    // when / then
    assertThatThrownBy(() -> longPolling.setNotificationBatchWindow(Duration.ofMillis(-1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("notificationBatchWindow");
  }

  @Test
  void shouldRejectSubMillisecondNotificationBatchWindow() {
    // when / then — it would silently truncate to 0 and disable throttling
    assertThatThrownBy(() -> longPolling.setNotificationBatchWindow(Duration.ofNanos(500_000)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("whole number of milliseconds");
  }

  @Test
  void shouldKeepDefaultNotificationBatchWindow() {
    // then
    assertThat(longPolling.getNotificationBatchWindow()).isEqualTo(Duration.ofMillis(100));
  }
}
