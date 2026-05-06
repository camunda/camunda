/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Outcome;
import io.camunda.zeebe.engine.processing.scheduled.runtime.FakeTaskContext;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class JobTimeoutCheckSchedulerTest {

  @Test
  void shouldReturnIdleWhenQueueDrains() {
    // given
    final JobState state = mock(JobState.class);
    when(state.forEachTimedOutEntry(anyLong(), any(), any())).thenReturn(null);
    final var scheduler = new JobTimeoutCheckScheduler(state, 100);

    // when
    final Outcome outcome = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(outcome).isEqualTo(Outcome.IDLE);
  }

  @Test
  void shouldYieldAndKeepCursorWhenStoppedEarly() {
    // given
    final JobState state = mock(JobState.class);
    final DeadlineIndex cursor = new DeadlineIndex(2_000L, 99L);
    when(state.forEachTimedOutEntry(anyLong(), any(), any())).thenReturn(cursor);
    final var scheduler = new JobTimeoutCheckScheduler(state, 100);

    // when
    final Outcome firstOutcome = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then — first run yielded, runtime should reschedule immediately
    assertThat(firstOutcome).isEqualTo(Outcome.YIELD_NOW);

    // and a follow-up run picks up where it left off
    final ArgumentCaptor<DeadlineIndex> startAt = ArgumentCaptor.forClass(DeadlineIndex.class);
    when(state.forEachTimedOutEntry(anyLong(), startAt.capture(), any())).thenReturn(null);

    scheduler.run(FakeTaskContext.create().withClockMillis(5_000L));
    assertThat(startAt.getValue()).isEqualTo(cursor);
  }

  @Test
  void shouldEmitTimeOutCommandsUpToBatchLimit() {
    // given
    final JobState state = mock(JobState.class);
    final ArgumentCaptor<BiPredicate<Long, JobRecord>> visitor = visitorCaptor();
    when(state.forEachTimedOutEntry(anyLong(), any(), visitor.capture())).thenReturn(null);
    final var scheduler = new JobTimeoutCheckScheduler(state, 2);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    scheduler.run(ctx);
    final var record = new JobRecord();
    final boolean firstAccepted = visitor.getValue().test(1L, record);
    final boolean secondAccepted = visitor.getValue().test(2L, record);
    final boolean thirdAccepted = visitor.getValue().test(3L, record);

    // then — first two commands appended, third rejected because batch limit was reached
    assertThat(firstAccepted).isTrue();
    assertThat(secondAccepted).isTrue();
    assertThat(thirdAccepted).isFalse();
    assertThat(ctx.appended()).hasSize(2);
    assertThat(ctx.appended()).allMatch(c -> c.intent() == JobIntent.TIME_OUT);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<BiPredicate<Long, JobRecord>> visitorCaptor() {
    return ArgumentCaptor.forClass(BiPredicate.class);
  }
}
