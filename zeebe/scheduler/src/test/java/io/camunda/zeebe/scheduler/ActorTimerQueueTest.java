/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.scheduler.clock.ActorClock;
import io.camunda.zeebe.scheduler.clock.DefaultActorClock;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

final class ActorTimerQueueTest {

  private final ActorClock clock = mock(ActorClock.class);

  @Test
  void shouldExpireTimerInSinglePollWhenClockIsFarAhead() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock);
    final var timer = timerWithDeadline(1_010L);
    queue.schedule(timer, clock);

    // when - the clock jumped far ahead of the wheel, e.g. after a long park or a busy job
    when(clock.getTimeMillis()).thenReturn(1_500L);
    queue.processExpiredTimers(clock);

    // then
    verify(timer).onTimerExpired(TimeUnit.MILLISECONDS, 1_500L);
  }

  @Test
  void shouldExpireTimersOnDifferentSpokes() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock);
    final var timers = new TimerSubscription[20];
    for (int i = 0; i < timers.length; i++) {
      timers[i] = timerWithDeadline(1_003L + i * 7L);
      queue.schedule(timers[i], clock);
    }

    // when
    when(clock.getTimeMillis()).thenReturn(2_000L);
    queue.processExpiredTimers(clock);

    // then
    for (final var timer : timers) {
      verify(timer).onTimerExpired(any(), anyLong());
    }
  }

  @ParameterizedTest
  @ValueSource(ints = {2, 8, 32, 128, 1024})
  void shouldExpireAllDueTimersInSingleCallForAnyWheelSize(final int ticksPerWheel) {
    // given - timers spread over more or fewer spokes than the wheel has
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock, ticksPerWheel);
    final var timers = new TimerSubscription[20];
    for (int i = 0; i < timers.length; i++) {
      timers[i] = timerWithDeadline(1_003L + i * 7L);
      queue.schedule(timers[i], clock);
    }

    // when
    when(clock.getTimeMillis()).thenReturn(2_000L);
    queue.processExpiredTimers(clock);

    // then
    for (final var timer : timers) {
      verify(timer).onTimerExpired(any(), anyLong());
    }
  }

  @Test
  void shouldStartWheelAtCurrentTimeWhenCreatedWithDefaultClock() {
    // when
    final var queue = new ActorTimerQueue(new DefaultActorClock());

    // then - the wheel must not start at epoch 0, otherwise it is always far behind real time
    assertThat(queue.currentTickTime()).isCloseTo(System.currentTimeMillis(), within(1_000L));
  }

  @Test
  void shouldExpireTimerDueInTheTickTheClockIsIn() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock);
    final var timer = timerWithDeadline(1_010L);
    queue.schedule(timer, clock);

    // when - the wheel has to move into the tick of the deadline and scan it in the same call
    when(clock.getTimeMillis()).thenReturn(1_010L);
    queue.processExpiredTimers(clock);

    // then
    verify(timer).onTimerExpired(TimeUnit.MILLISECONDS, 1_010L);
  }

  @Test
  void shouldExpireTimerThatIsAlreadyDueWithoutTheClockChanging() {
    // given - the wheel was already polled at this millisecond
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock);
    queue.processExpiredTimers(clock);

    // when
    final var timer = timerWithDeadline(1_000L);
    queue.schedule(timer, clock);
    queue.processExpiredTimers(clock);

    // then
    verify(timer).onTimerExpired(TimeUnit.MILLISECONDS, 1_000L);
  }

  @Test
  void shouldNotPollWheelWhileTheClockDidNotChange() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = spy(new ActorTimerQueue(clock));
    queue.processExpiredTimers(clock);
    queue.schedule(timerWithDeadline(1_010L), clock);
    clearInvocations(queue);

    // when
    queue.processExpiredTimers(clock);
    queue.processExpiredTimers(clock);

    // then
    verify(queue, never()).poll(anyLong(), any(), anyInt());
  }

  @Test
  void shouldPollWheelWhenTheClockChanged() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = spy(new ActorTimerQueue(clock));
    queue.processExpiredTimers(clock);
    clearInvocations(queue);

    // when
    when(clock.getTimeMillis()).thenReturn(1_001L);
    queue.processExpiredTimers(clock);

    // then
    verify(queue, atLeastOnce()).poll(anyLong(), any(), anyInt());
  }

  @Test
  void shouldNotExpireTimerBeforeDeadline() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock);
    final var timer = timerWithDeadline(1_100L);
    queue.schedule(timer, clock);

    // when
    when(clock.getTimeMillis()).thenReturn(1_099L);
    queue.processExpiredTimers(clock);

    // then
    verify(timer, never()).onTimerExpired(any(), anyLong());
    assertThat(queue.timerCount()).isEqualTo(1);
  }

  @Test
  void shouldYieldAfterExpiringManyTimersAndContinueOnNextCall() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock);
    final var timers = new TimerSubscription[1_000];
    for (int i = 0; i < timers.length; i++) {
      timers[i] = timerWithDeadline(1_005L);
      queue.schedule(timers[i], clock);
    }

    // when - the clock does not change between the calls
    when(clock.getTimeMillis()).thenReturn(1_010L);
    queue.processExpiredTimers(clock);
    final long expiredAfterFirstCall = timers.length - queue.timerCount();
    for (int i = 0; i < 10 && queue.timerCount() > 0; i++) {
      queue.processExpiredTimers(clock);
    }

    // then
    assertThat(expiredAfterFirstCall).isPositive().isLessThan(timers.length);
    assertThat(queue.timerCount()).isZero();
  }

  @Test
  void shouldJumpToNowWhenCatchingUpOnALargeClockJump() {
    // given
    when(clock.getTimeMillis()).thenReturn(1_000L);
    final var queue = new ActorTimerQueue(clock);
    final var timer = timerWithDeadline(2_000L);
    queue.schedule(timer, clock);

    // when - far more ticks than a single call polls
    when(clock.getTimeMillis()).thenReturn(10_000L);
    queue.processExpiredTimers(clock);

    // then
    verify(timer).onTimerExpired(TimeUnit.MILLISECONDS, 10_000L);
    assertThat(queue.currentTickTime()).isGreaterThanOrEqualTo(10_000L);
  }

  private TimerSubscription timerWithDeadline(final long deadline) {
    final var timer = mock(TimerSubscription.class);
    when(timer.getDeadline(any())).thenReturn(deadline);
    return timer;
  }
}
