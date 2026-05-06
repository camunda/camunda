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
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

final class JobBackoffCheckSchedulerTest {

  @Test
  void shouldReturnIdleWhenNoBackedOffJobsFound() {
    // given
    final JobState state = mock(JobState.class);
    when(state.findBackedOffJobs(anyLong(), any())).thenReturn(0L);
    final var scheduler = new JobBackoffCheckScheduler(state);

    // when
    final Outcome outcome = scheduler.run(FakeTaskContext.create().withClockMillis(123L));

    // then
    assertThat(outcome).isEqualTo(Outcome.IDLE);
  }

  @Test
  void shouldReturnAwaitDueAtWhenNextBackoffKnown() {
    // given
    final JobState state = mock(JobState.class);
    when(state.findBackedOffJobs(anyLong(), any())).thenReturn(5_000L);
    final var scheduler = new JobBackoffCheckScheduler(state);

    // when
    final Outcome outcome = scheduler.run(FakeTaskContext.create().withClockMillis(1_000L));

    // then
    assertThat(outcome).isInstanceOf(Outcome.AwaitDueAt.class);
    assertThat(((Outcome.AwaitDueAt) outcome).timestampMs()).isEqualTo(5_000L);
  }

  @Test
  void shouldAppendRecurAfterBackoffCommandPerJob() {
    // given
    final JobState state = mock(JobState.class);
    final ArgumentCaptor<BiPredicate<Long, JobRecord>> visitor = visitorCaptor();
    when(state.findBackedOffJobs(anyLong(), visitor.capture())).thenReturn(0L);
    final var scheduler = new JobBackoffCheckScheduler(state);
    final var ctx = FakeTaskContext.create().withClockMillis(1_000L);

    // when
    scheduler.run(ctx);
    final var record = new JobRecord();
    visitor.getValue().test(42L, record);
    visitor.getValue().test(43L, record);

    // then
    assertThat(ctx.appended()).hasSize(2);
    assertThat(ctx.appended().get(0).key()).isEqualTo(42L);
    assertThat(ctx.appended().get(0).intent()).isEqualTo(JobIntent.RECUR_AFTER_BACKOFF);
    assertThat(ctx.appended().get(1).key()).isEqualTo(43L);
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<BiPredicate<Long, JobRecord>> visitorCaptor() {
    return ArgumentCaptor.forClass(BiPredicate.class);
  }
}
