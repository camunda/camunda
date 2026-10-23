/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.ScheduledTaskTestHarness;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.stream.impl.ProcessingScheduleServiceImpl;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Runs the {@link JobBackoffCheckScheduler} on a real {@link ProcessingScheduleServiceImpl} with a
 * writer that fails on demand. This covers how the scheduler and the scheduling service work
 * together when the write of the recur commands is rejected.
 */
@ExtendWith(ProcessingStateExtension.class)
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class JobBackoffCheckSchedulerWriteFailureTest {

  private static final Duration RETRY_BACKOFF = Duration.ofMinutes(1);

  @RegisterExtension
  private final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  private MutableProcessingState processingState;
  private ScheduledTaskTestHarness harness;

  @BeforeEach
  void setUp() {
    final var scheduler =
        new JobBackoffCheckScheduler(actorScheduler.getClock(), processingState.getJobState());

    harness = new ScheduledTaskTestHarness(actorScheduler, scheduler);
  }

  @ParameterizedTest
  @EnumSource(
      value = WriteFailure.class,
      names = {"WRITE_LIMIT_EXHAUSTED", "REQUEST_LIMIT_EXHAUSTED"})
  void shouldRecurJobOnceRateLimitIsLifted(final WriteFailure rateLimited) {
    // given
    storeJobBackedOffFor(RETRY_BACKOFF);
    final var writer = harness.writer();
    writer.failWith(rateLimited);
    harness.recover();

    // when the backoff is over, but the limit stays exhausted
    harness.advanceTime(RETRY_BACKOFF);

    // then
    assertThat(writer.attempts())
        .describedAs("the result is abandoned after the first attempt")
        .isEqualTo(1);
    assertThat(writer.writtenIntents()).describedAs("nothing is written").isEmpty();

    // when the limit is lifted, the checker has rescheduled itself and runs again after its backoff
    writer.stopFailing();
    harness.advanceTime(Duration.ofSeconds(2));

    // then
    assertThat(writer.attempts())
        .describedAs(
            "1 rate limited attempt, then 1 write by the checker execution after its backoff")
        .isEqualTo(2);
    assertThat(writer.writtenIntents()).containsExactly(JobIntent.RECUR_AFTER_BACKOFF);
  }

  private void storeJobBackedOffFor(final Duration retryBackoff) {
    final long jobKey = 1L;
    final long recurringTime = actorScheduler.getClock().millis() + retryBackoff.toMillis();
    final var job =
        new JobRecord()
            .setType("test")
            .setRetries(1)
            .setRetryBackoff(retryBackoff.toMillis())
            .setRecurringTime(recurringTime);
    final var jobState = processingState.getJobState();
    jobState.create(jobKey, job);
    jobState.fail(jobKey, job);
  }
}
