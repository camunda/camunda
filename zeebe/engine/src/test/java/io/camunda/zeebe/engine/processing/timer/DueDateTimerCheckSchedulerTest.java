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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Outcome;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState;
import io.camunda.zeebe.engine.state.immutable.TimerInstanceState.TimerVisitor;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class DueDateTimerCheckSchedulerTest {

  @Test
  void shouldReturnAwaitDueAtForNextTimer() {
    // given
    final var state = mock(TimerInstanceState.class);
    when(state.processTimersWithDueDateBefore(anyLong(), any())).thenReturn(2_500L);
    final var scheduler = new DueDateTimerCheckScheduler(state);

    // when
    final Outcome outcome = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(outcome).isInstanceOf(Outcome.AwaitDueAt.class);
    assertThat(((Outcome.AwaitDueAt) outcome).timestampMs()).isEqualTo(2_500L);
  }

  @Test
  void shouldReturnIdleWhenNoTimersAndNoYield() {
    // given
    final var state = mock(TimerInstanceState.class);
    when(state.processTimersWithDueDateBefore(anyLong(), any())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);

    // when
    final Outcome outcome = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(outcome).isEqualTo(Outcome.IDLE);
  }

  @Test
  void shouldYieldWhenContextSignalsYieldAndNoNextDueDate() {
    // given
    final var state = mock(TimerInstanceState.class);
    when(state.processTimersWithDueDateBefore(anyLong(), any())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L).withShouldYield(true);

    // when
    final Outcome outcome = scheduler.run(ctx);

    // then
    assertThat(outcome).isEqualTo(Outcome.YIELD_NOW);
  }

  @Test
  void shouldEmitTimerTriggerCommandsForVisitedTimers() {
    // given
    final var state = mock(TimerInstanceState.class);
    final ArgumentCaptor<TimerVisitor> visitor = ArgumentCaptor.forClass(TimerVisitor.class);
    when(state.processTimersWithDueDateBefore(anyLong(), visitor.capture())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    scheduler.run(ctx);
    final TimerInstance timer = new TimerInstance();
    timer.setKey(7L);
    timer.setElementInstanceKey(11L);
    timer.setProcessInstanceKey(13L);
    timer.setDueDate(900L);
    timer.setRepetitions(1);
    timer.setProcessDefinitionKey(17L);
    final boolean accepted = visitor.getValue().visit(timer);

    // then
    assertThat(accepted).isTrue();
    assertThat(ctx.appended()).hasSize(1);
    assertThat(ctx.appended().get(0).key()).isEqualTo(7L);
    assertThat(ctx.appended().get(0).intent()).isEqualTo(TimerIntent.TRIGGER);
  }

  @Test
  void shouldStopVisitingWhenYieldRequested() {
    // given
    final var state = mock(TimerInstanceState.class);
    final ArgumentCaptor<TimerVisitor> visitor = ArgumentCaptor.forClass(TimerVisitor.class);
    when(state.processTimersWithDueDateBefore(anyLong(), visitor.capture())).thenReturn(-1L);
    final var scheduler = new DueDateTimerCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L).withShouldYield(true);

    // when
    scheduler.run(ctx);
    final boolean accepted = visitor.getValue().visit(new TimerInstance());

    // then
    assertThat(accepted).isFalse();
    assertThat(ctx.appended()).isEmpty();
  }
}
