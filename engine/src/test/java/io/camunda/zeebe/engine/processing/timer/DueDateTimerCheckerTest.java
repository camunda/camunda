/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.timer.DueDateTimerChecker.YieldingDecorator;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState.TimerVisitor;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.util.sched.clock.ActorClock;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DueDateTimerCheckerTest {

  @Nested
  final class YieldingDecoratorTest {

    public static final int TIME_TO_YIELD = 100;

    @Test
    void shouldForwardCallToDelegateWhenTimeToYieldIsNotYetReached() {
      // given
      final var mockTimer = mock(TimerInstance.class);

      final var mockDelegate = mock(TimerVisitor.class);
      when(mockDelegate.visit(Mockito.any())).thenReturn(true);

      final var testActorClock = new TestActorClock();
      testActorClock.setTime(TIME_TO_YIELD - 50);

      final var sut = new YieldingDecorator(testActorClock, TIME_TO_YIELD, mockDelegate);

      // when
      final var actual = sut.visit(mockTimer);

      // then
      assertThat(actual).isTrue();
      verify(mockDelegate).visit(mockTimer);
    }

    @Test
    void shouldNotForwardCallToDelegateWhenTimeToYieldIsReached() {
      // given
      final var mockTimer = mock(TimerInstance.class);

      final var mockDelegate = mock(TimerVisitor.class);
      when(mockDelegate.visit(Mockito.any())).thenReturn(true);

      final var testActorClock = new TestActorClock();
      testActorClock.setTime(TIME_TO_YIELD);

      final var sut = new YieldingDecorator(testActorClock, TIME_TO_YIELD, mockDelegate);

      // when
      final var actual = sut.visit(mockTimer);

      // then
      assertThat(actual).isFalse();
      verifyNoInteractions(mockDelegate);
    }

    @Test
    void shouldNotForwardCallToDelegateWhenTimeToYieldHasPassed() {
      // given
      final var mockTimer = mock(TimerInstance.class);

      final var mockDelegate = mock(TimerVisitor.class);
      when(mockDelegate.visit(Mockito.any())).thenReturn(true);

      final var testActorClock = new TestActorClock();
      testActorClock.setTime(TIME_TO_YIELD + 50);

      final var sut = new YieldingDecorator(testActorClock, TIME_TO_YIELD, mockDelegate);

      // when
      final var actual = sut.visit(mockTimer);

      // then
      assertThat(actual).isFalse();
      verifyNoInteractions(mockDelegate);
    }
  }

  private final class TestActorClock implements ActorClock {

    private long time = 0;

    public void setTime(final long time) {
      this.time = time;
    }

    @Override
    public boolean update() {
      time = time + 10;
      return true;
    }

    @Override
    public long getTimeMillis() {
      time = time + 10;
      return time;
    }

    @Override
    public long getNanosSinceLastMillisecond() {
      return 0;
    }

    @Override
    public long getNanoTime() {
      return Duration.ofMillis(getTimeMillis()).toNanos();
    }
  }
}
