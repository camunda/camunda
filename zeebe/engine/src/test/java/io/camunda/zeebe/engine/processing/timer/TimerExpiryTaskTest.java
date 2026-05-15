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
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.scheduled.runtime.Context;
import io.camunda.zeebe.engine.scheduled.runtime.Hint;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState.TimerVisitor;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Instant;
import java.time.InstantSource;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TimerExpiryTaskTest {

  @Test
  void shouldYieldAndReturnMoreWorkPending() {
    // given
    final var resultBuilder = mock(TaskResultBuilder.class);
    when(resultBuilder.appendCommandRecord(anyLong(), any(), any())).thenReturn(true);

    final var timer = mock(TimerInstance.class, Mockito.RETURNS_DEEP_STUBS);
    when(timer.getKey()).thenReturn(42L);
    when(timer.getTenantId()).thenReturn(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final var clock = new TestClock();
    final var state = new EndlessTimerState(timer, clock);

    final var task = new TimerExpiryTask(state, clock, true);

    // when
    final var result = task.run(new TestContext(clock, resultBuilder));

    // then
    verify(resultBuilder, times(4)).appendCommandRecord(eq(42L), eq(TimerIntent.TRIGGER), any());
    assertThat(result.hint()).isInstanceOf(Hint.MoreWorkPending.class);
  }

  @Test
  void shouldReturnNextDueAtWhenStateReportsRemainingDueDate() {
    // given
    final var resultBuilder = mock(TaskResultBuilder.class);
    final var clock = new TestClock();
    clock.setTime(1000);

    final TimerInstanceState state =
        new TimerInstanceState() {
          @Override
          public long processTimersWithDueDateBefore(final long ts, final TimerVisitor visitor) {
            return 5_000L;
          }

          @Override
          public void forEachTimerForElementInstance(
              final long elementInstanceKey, final Consumer<TimerInstance> action) {}

          @Override
          public TimerInstance get(final long elementInstanceKey, final long timerKey) {
            return null;
          }
        };

    final var task = new TimerExpiryTask(state, clock, true);

    // when
    final var result = task.run(new TestContext(clock, resultBuilder));

    // then
    assertThat(result.hint()).isEqualTo(new Hint.NextDueAt(5_000L));
  }

  @Test
  void shouldReturnIdleWhenStateReportsNoDueDates() {
    // given
    final var resultBuilder = mock(TaskResultBuilder.class);
    final var clock = new TestClock();
    clock.setTime(1000);

    final TimerInstanceState state =
        new TimerInstanceState() {
          @Override
          public long processTimersWithDueDateBefore(final long ts, final TimerVisitor visitor) {
            return -1L;
          }

          @Override
          public void forEachTimerForElementInstance(
              final long elementInstanceKey, final Consumer<TimerInstance> action) {}

          @Override
          public TimerInstance get(final long elementInstanceKey, final long timerKey) {
            return null;
          }
        };

    final var task = new TimerExpiryTask(state, clock, true);

    // when
    final var result = task.run(new TestContext(clock, resultBuilder));

    // then
    assertThat(result.hint()).isInstanceOf(Hint.Idle.class);
  }

  private static final class TestContext implements Context {
    private final InstantSource clock;
    private final TaskResultBuilder builder;

    TestContext(final InstantSource clock, final TaskResultBuilder builder) {
      this.clock = clock;
      this.builder = builder;
    }

    @Override
    public InstantSource clock() {
      return clock;
    }

    @Override
    public TaskResultBuilder resultBuilder() {
      return builder;
    }
  }

  private static final class TestClock implements InstantSource {
    private long time;

    void setTime(final long t) {
      time = t;
    }

    boolean tick() {
      time += 10;
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

  private static final class EndlessTimerState implements TimerInstanceState {
    private final TimerInstance timer;
    private final TestClock clock;

    EndlessTimerState(final TimerInstance timer, final TestClock clock) {
      this.timer = timer;
      this.clock = clock;
    }

    @Override
    public long processTimersWithDueDateBefore(final long ts, final TimerVisitor visitor) {
      while (true) {
        clock.tick();
        if (!visitor.visit(timer)) {
          // visitor yielded. Return the timer's due date as the remaining one.
          return timer.getDueDate();
        }
      }
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
