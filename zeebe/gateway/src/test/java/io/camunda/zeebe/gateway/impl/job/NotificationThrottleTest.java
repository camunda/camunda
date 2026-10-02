/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.impl.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class NotificationThrottleTest {

  private static final long WINDOW = 100;

  private final NotificationThrottle throttle = new NotificationThrottle();
  private final List<Long> handledAt = new ArrayList<>();
  private final List<Long> scheduledDelays = new ArrayList<>();

  private void notifyAt(final long now, final long window) {
    throttle.onNotification(now, window, () -> handledAt.add(now), scheduledDelays::add);
  }

  @Test
  void shouldHandleFirstNotificationImmediately() {
    // when
    notifyAt(0, WINDOW);

    // then
    assertThat(handledAt).containsExactly(0L);
    assertThat(scheduledDelays).isEmpty();
  }

  @Test
  void shouldCollapseNotificationsWithinWindowIntoOneTrailingFlush() {
    // given
    notifyAt(1_000, WINDOW);

    // when
    notifyAt(1_030, WINDOW);
    notifyAt(1_060, WINDOW);

    // then
    assertThat(handledAt).containsExactly(1_000L);
    assertThat(scheduledDelays).containsExactly(70L);
    assertThat(throttle.shouldFlush(1_100)).isTrue();
    assertThat(throttle.shouldFlush(1_100)).isFalse();
  }

  @Test
  void shouldHandleNotificationOnceWindowElapsed() {
    // given
    notifyAt(1_000, WINDOW);

    // when
    notifyAt(1_100, WINDOW);

    // then
    assertThat(handledAt).containsExactly(1_000L, 1_100L);
    assertThat(scheduledDelays).isEmpty();
  }

  @Test
  void shouldNotFlushWhenNothingIsPending() {
    // given
    notifyAt(1_000, WINDOW);

    // then
    assertThat(throttle.shouldFlush(1_100)).isFalse();
  }

  @Test
  void shouldDropPendingFlushWhenNotificationHandledInBetween() {
    // given
    notifyAt(1_000, WINDOW);
    notifyAt(1_050, WINDOW);

    // when
    notifyAt(1_120, WINDOW);

    // then
    assertThat(handledAt).containsExactly(1_000L, 1_120L);
    assertThat(throttle.shouldFlush(1_150)).isFalse();
  }

  @Test
  void shouldHandleNotificationAfterClockMovedBackwards() {
    // given
    notifyAt(10_000, WINDOW);

    // when
    notifyAt(5_000, WINDOW);

    // then
    assertThat(handledAt).containsExactly(10_000L, 5_000L);
  }

  @Test
  void shouldNotThrottleWhenWindowIsZero() {
    // when
    notifyAt(1_000, 0);
    notifyAt(1_000, 0);

    // then
    assertThat(handledAt).containsExactly(1_000L, 1_000L);
    assertThat(scheduledDelays).isEmpty();
  }
}
