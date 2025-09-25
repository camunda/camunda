/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.common.processing.timer.DueDateTimerChecker.TriggerTimersSideEffect;
import io.camunda.zeebe.engine.common.processing.timer.DueDateTimerChecker.YieldingDecorator;
import io.camunda.zeebe.engine.common.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.common.state.immutable.TimerInstanceState.TimerVisitor;
import io.camunda.zeebe.engine.common.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DueDateTimerCheckerTest {

  @Nested
  final class TriggerTimersSideEffectTest {

    @Test
    void shouldAbortIterationAndGiveYieldAfterSomeTimeHasPassed() {
      /* This test verifies that the class will yield control at some point. This is related to
       * https://github.com/camunda/camunda/issues/8991 where one issue was that the list of due timers
       * grew substantially to millions of entries. The algorithm iterated over each entry and blocked
       * the execution of any other work on that thread during that time.
       */

      // given
      final var mockTaskResultBuilder = mock(TaskResultBuilder.class);
      when(mockTaskResultBuilder.appendCommandRecord(anyLong(), any(), any())).thenReturn(true);

      final var mockTimer = mock(TimerInstance.class, Mockito.RETURNS_DEEP_STUBS);
      final var timerKey = 42L;
      when(mockTimer.getKey()).thenReturn(timerKey);
      when(mockTimer.getTenantId()).thenReturn(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      final var testActorClock = new TestActorClock();

      final var testTimerInstanceState =
          new TestTimerInstanceStateThatSimulatesAnEndlessListOfDueTimers(
              mockTimer, testActorClock);

      final var sut = new TriggerTimersSideEffect(testTimerInstanceState, testActorClock, true);

      // when
      sut.apply(mockTaskResultBuilder);

      // then
      verify(mockTaskResultBuilder, times(4))
          .appendCommandRecord(eq(timerKey), eq(TimerIntent.TRIGGER), any());
      /*
       * Why 4 times? The actor clock is advanced by 10 units before the timer visitor is called, and
       * thus before a trigger event command is written.
       *
       * Internally, the threshold to give yield is calculated by
       * final var yieldAfter = now + Math.round(TIMER_RESOLUTION * GIVE_YIELD_FACTOR) == 50
       *
       * So in the fifth iteration, the mechanism will yield
       */
    }

    @Test
    void shouldAbortIterationWhenRecordBatchReturnsFalseOnAppend() {
      /* This test verifies that the class will yield at some point, and will not add endless records
       * to a batch.
       */

      // given
      final var mockTaskResultBuilder = mock(TaskResultBuilder.class);
      when(mockTaskResultBuilder.appendCommandRecord(anyLong(), any(), any()))
          .thenReturn(true)
          .thenReturn(false);

      final var mockTimer = mock(TimerInstance.class, Mockito.RETURNS_DEEP_STUBS);
      final var timerKey = 42L;
      when(mockTimer.getKey()).thenReturn(timerKey);
      when(mockTimer.getTenantId()).thenReturn(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

      final var testActorClock = new TestActorClock();

      final var testTimerInstanceState =
          new TestTimerInstanceStateThatSimulatesAnEndlessListOfDueTimers(
              mockTimer, testActorClock);

      final var sut = new TriggerTimersSideEffect(testTimerInstanceState, testActorClock, true);

      // when
      sut.apply(mockTaskResultBuilder);

      // then
      verify(mockTaskResultBuilder, times(2))
          .appendCommandRecord(eq(timerKey), eq(TimerIntent.TRIGGER), any());
    }
  }

  @Nested
  final class YieldingDecoratorTest {

    private static final int TIME_TO_YIELD = 100;

    private TimerInstance mockTimer;
    private TimerVisitor mockDelegate;
    private final TestActorClock testActorClock = new TestActorClock();

    @BeforeEach
    void setUpMocks() {
      // given
      mockTimer = mock(TimerInstance.class);

      mockDelegate = mock(TimerVisitor.class);
      when(mockDelegate.visit(any())).thenReturn(true);
    }

    @Test
    void shouldForwardCallToDelegateWhenTimeToYieldIsNotYetReached() {
      // given
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
      testActorClock.setTime(TIME_TO_YIELD + 50);

      final var sut = new YieldingDecorator(testActorClock, TIME_TO_YIELD, mockDelegate);

      // when
      final var actual = sut.visit(mockTimer);

      // then
      assertThat(actual).isFalse();
      verifyNoInteractions(mockDelegate);
    }
  }

  private final class TestActorClock implements InstantSource {

    private long time = 0;

    public void setTime(final long time) {
      this.time = time;
    }

    public boolean update() {
      time = time + 10;
      return true;
    }

    @Override
    public Instant instant() {
      return Instant.ofEpochMilli(time);
    }

    @Override
    public long millis() {
      return time;
    }
  }

  private final class TestTimerInstanceStateThatSimulatesAnEndlessListOfDueTimers
      implements TimerInstanceState {

    private final TimerInstance timer;
    private final TestActorClock testActorClock;

    private TestTimerInstanceStateThatSimulatesAnEndlessListOfDueTimers(
        final TimerInstance timer, final TestActorClock testActorClock) {
      this.timer = timer;
      this.testActorClock = testActorClock;
    }

    @Override
    public long processTimersWithDueDateBefore(final long timestamp, final TimerVisitor consumer) {
      var yield = false;

      while (!yield) {
        testActorClock.update();
        yield = !consumer.visit(timer);
      }
      return 0;
    }

    @Override
    public void forEachTimerForElementInstance(
        final long elementInstanceKey, final Consumer<TimerInstance> action) {}

    @Override
    public TimerInstance get(final long elementInstanceKey, final long timerKey) {
      return null;
    }
  }
}
