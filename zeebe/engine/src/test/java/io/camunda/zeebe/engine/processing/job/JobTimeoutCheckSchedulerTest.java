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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
import io.camunda.zeebe.engine.processing.scheduled.api.Result.Decision;
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
    final Result result =
        scheduler.run(FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(1_000L));

    // then
    assertThat(result.decision()).isEqualTo(Decision.Idle.INSTANCE);
  }

  @Test
  void shouldYieldAndKeepCursorWhenStoppedEarly() {
    // given
    final JobState state = mock(JobState.class);
    final DeadlineIndex resumeFrom = new DeadlineIndex(2_000L, 99L);
    when(state.forEachTimedOutEntry(anyLong(), any(), any())).thenReturn(resumeFrom);
    final var scheduler = new JobTimeoutCheckScheduler(state, 100);

    // when — first run yields and produces a cursor
    final Result first =
        scheduler.run(FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(1_000L));

    // then
    assertThat(first.decision()).isInstanceOf(Decision.YieldNow.class);
    final JobTimeoutCursor cursor =
        (JobTimeoutCursor) ((Decision.YieldNow) first.decision()).cursor();
    assertThat(cursor.resumeFrom()).isEqualTo(resumeFrom);
    assertThat(cursor.executionTimestamp()).isEqualTo(1_000L);

    // and a follow-up run started with that cursor resumes at the saved index, preserving the
    // original executionTimestamp even when the wall-clock has advanced
    final ArgumentCaptor<Long> tsCaptor = ArgumentCaptor.forClass(Long.class);
    final ArgumentCaptor<DeadlineIndex> startAt = ArgumentCaptor.forClass(DeadlineIndex.class);
    when(state.forEachTimedOutEntry(tsCaptor.capture(), startAt.capture(), any())).thenReturn(null);

    scheduler.run(
        FakeTaskContext.createFor(JobTimeoutCursor.class)
            .withClockMillis(5_000L)
            .withResumeCursor(cursor));

    assertThat(startAt.getValue()).isEqualTo(resumeFrom);
    assertThat(tsCaptor.getValue()).isEqualTo(1_000L);
  }

  @Test
  void shouldEmitTimeOutCommandsUpToBatchLimit() {
    // given
    final JobState state = mock(JobState.class);
    final ArgumentCaptor<BiPredicate<Long, JobRecord>> visitor = visitorCaptor();
    when(state.forEachTimedOutEntry(anyLong(), any(), visitor.capture())).thenReturn(null);
    final var scheduler = new JobTimeoutCheckScheduler(state, 2);
    final var ctx = FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(1_000L);

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
    assertThat(ctx.lastResult().appendedCommands()).hasSize(2);
    assertThat(ctx.lastResult().appendedCommands()).allMatch(c -> c.intent() == JobIntent.TIME_OUT);
  }

  @Test
  void shouldUseClockNowAsExecutionTimestampOnFirstRun() {
    // given — no resume cursor on the context
    final JobState state = mock(JobState.class);
    final ArgumentCaptor<Long> tsCaptor = ArgumentCaptor.forClass(Long.class);
    when(state.forEachTimedOutEntry(tsCaptor.capture(), eq(null), any())).thenReturn(null);
    final var scheduler = new JobTimeoutCheckScheduler(state, 10);

    // when
    scheduler.run(FakeTaskContext.createFor(JobTimeoutCursor.class).withClockMillis(7_500L));

    // then
    assertThat(tsCaptor.getValue()).isEqualTo(7_500L);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<BiPredicate<Long, JobRecord>> visitorCaptor() {
    return ArgumentCaptor.forClass(BiPredicate.class);
  }
}
