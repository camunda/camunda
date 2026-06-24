/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class TimerSubscriptionTest {

  @Test
  void shouldRecordDelayedTimerExpirationWithMonotonicClock() {
    // given
    final var timer = newDelayedTimer();
    final var before = System.nanoTime();

    // when
    timer.onTimerExpired(TimeUnit.MILLISECONDS, Long.MAX_VALUE);
    final var after = System.nanoTime();

    // then
    assertThat(timer.getTimerExpiredAt()).isBetween(before, after);
  }

  @Test
  void shouldRecordStampedTimerExpirationWithMonotonicClock() {
    // given
    final var timer = newStampedTimer();
    final var before = System.nanoTime();

    // when
    timer.onTimerExpired(TimeUnit.MILLISECONDS, Long.MAX_VALUE);
    final var after = System.nanoTime();

    // then
    assertThat(timer.getTimerExpiredAt()).isBetween(before, after);
  }

  @Test
  void shouldWakeTaskWhenDelayedTimerExpires() {
    // given
    final var task = mock(ActorTask.class);
    final var timer = new DelayedTimerSubscription(newJob(task), 1, TimeUnit.MILLISECONDS, false);

    // when
    timer.onTimerExpired(TimeUnit.MILLISECONDS, 1);

    // then
    verify(task).tryWakeup();
  }

  @Test
  void shouldWakeTaskWhenStampedTimerExpires() {
    // given
    final var task = mock(ActorTask.class);
    final var timer = new StampedTimerSubscription(newJob(task), 1);

    // when
    timer.onTimerExpired(TimeUnit.MILLISECONDS, 1);

    // then
    verify(task).tryWakeup();
  }

  private static DelayedTimerSubscription newDelayedTimer() {
    return new DelayedTimerSubscription(
        newJob(mock(ActorTask.class)), 1, TimeUnit.MILLISECONDS, false);
  }

  private static StampedTimerSubscription newStampedTimer() {
    return new StampedTimerSubscription(newJob(mock(ActorTask.class)), 1);
  }

  private static ActorJob newJob(final ActorTask task) {
    final var job = mock(ActorJob.class);
    when(job.getTask()).thenReturn(task);
    return job;
  }
}
