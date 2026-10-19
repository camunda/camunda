/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.timer;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.instance.TimerInstance;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.engine.util.ScheduledTaskTestHarness;
import io.camunda.zeebe.logstreams.log.LogStreamWriter.WriteFailure;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.scheduler.testing.ControlledActorSchedulerExtension;
import io.camunda.zeebe.stream.impl.ProcessingScheduleServiceImpl;
import io.camunda.zeebe.util.FeatureFlags;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Runs the {@link DueDateTimerCheckScheduler} on a real {@link ProcessingScheduleServiceImpl} with
 * a writer that fails on demand. This covers how the scheduler and the scheduling service work
 * together when the write of the trigger commands is rejected, and how the scheduler's lifecycle
 * callbacks interact with that.
 */
@ExtendWith(ProcessingStateExtension.class)
@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
final class DueDateTimerCheckSchedulerWriteFailureTest {

  private static final Duration TIMER_DELAY = Duration.ofMinutes(1);

  @RegisterExtension
  private final ControlledActorSchedulerExtension actorScheduler =
      new ControlledActorSchedulerExtension();

  private MutableProcessingState processingState;
  private ScheduledTaskTestHarness harness;

  @BeforeEach
  void setUp() {
    final var featureFlags = FeatureFlags.createDefaultForTests();
    featureFlags.setEnableTimerDueDateCheckerAsync(false);
    final var scheduler =
        new DueDateTimerCheckScheduler(
            processingState.getTimerState(), featureFlags, actorScheduler.getClock());

    harness = new ScheduledTaskTestHarness(actorScheduler, scheduler);
  }

  @ParameterizedTest
  @EnumSource(
      value = WriteFailure.class,
      names = {"WRITE_LIMIT_EXHAUSTED", "REQUEST_LIMIT_EXHAUSTED"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldRetryTheSameWriteUntilItIsAcceptedForAnyFailure(final WriteFailure failure) {
    // given a failure that goes away again after many rejected attempts
    storeTimerDueIn(TIMER_DELAY);
    final var writer = harness.writer();
    writer.failWhile(attempt -> attempt <= 10, failure);
    harness.recover();

    // when
    harness.advanceTime(TIMER_DELAY);

    // then
    assertThat(writer.attempts())
        .describedAs("same write is retried inside the actor, whatever the failure was")
        .isEqualTo(11);
    assertThat(writer.writtenIntents()).containsExactly(TimerIntent.TRIGGER);
  }

  @ParameterizedTest
  @EnumSource(
      value = WriteFailure.class,
      names = {"WRITE_LIMIT_EXHAUSTED", "REQUEST_LIMIT_EXHAUSTED"})
  void shouldTriggerTimerOnceRateLimitIsLifted(final WriteFailure rateLimited) {
    // given
    storeTimerDueIn(TIMER_DELAY);
    final var writer = harness.writer();
    writer.failWith(rateLimited);
    harness.recover();

    // when the timer is due, but the limit stays exhausted
    harness.advanceTime(TIMER_DELAY);

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
    assertThat(writer.writtenIntents()).containsExactly(TimerIntent.TRIGGER);
  }

  @Test
  void shouldRunCheckerOnceAtNextDueDateAfterLongRateLimit() {
    // given timer A that is due soon and timer B that is due much later
    storeTimer(1L, 2L, Duration.ofMinutes(1));
    storeTimer(3L, 4L, Duration.ofHours(1));
    final var writer = harness.writer();
    writer.failWith(WriteFailure.WRITE_LIMIT_EXHAUSTED);
    harness.recover();

    // when the rate limit holds for 10 seconds after A is due
    harness.advanceTime(Duration.ofMinutes(1));
    for (int second = 1; second < 10; second++) {
      harness.advanceTime(Duration.ofSeconds(1));
    }
    writer.stopFailing();
    harness.advanceTime(Duration.ofSeconds(2));

    // then only the checker execution that backed off wrote the trigger of A
    assertThat(writer.writtenIntents()).containsExactly(TimerIntent.TRIGGER);

    // when B is due. The harness does not process commands, so A is still in the state.
    final var attemptsBeforeBIsDue = writer.attempts();
    harness.advanceTime(Duration.ofHours(1));

    // then
    assertThat(writer.attempts() - attemptsBeforeBIsDue)
        .describedAs("the checker runs once for B, not once per abandoned result")
        .isEqualTo(1);
    assertThat(writer.writtenIntents())
        .containsExactly(TimerIntent.TRIGGER, TimerIntent.TRIGGER, TimerIntent.TRIGGER);
  }

  @Test
  void shouldRunOncePerBackoffWhenMoreTimersAreDueThanFitInBatch() {
    // given more due timers than fit in one batch, so the checker schedules its next execution soon
    storeTimer(1L, 2L, TIMER_DELAY);
    storeTimer(3L, 4L, TIMER_DELAY);
    final var writer = harness.writer();
    writer.limitBatchTo(1);
    writer.failWith(WriteFailure.WRITE_LIMIT_EXHAUSTED);
    harness.recover();
    harness.advanceTime(TIMER_DELAY);
    assertThat(writer.attempts()).describedAs("the first result is abandoned").isEqualTo(1);

    // when the rate limit holds for another second
    for (int step = 0; step < 10; step++) {
      harness.advanceTime(Duration.ofMillis(100));
    }

    // then
    assertThat(writer.attempts())
        .describedAs("the checker runs once per backoff of 100 ms, with 1 attempt each time")
        .isEqualTo(11);
  }

  private void storeTimerDueIn(@SuppressWarnings("SameParameterValue") final Duration delay) {
    storeTimer(1L, 2L, delay);
  }

  private void storeTimer(
      final long elementInstanceKey, final long timerKey, final Duration delay) {
    processingState
        .getElementInstanceState()
        .createInstance(
            new ElementInstance(
                elementInstanceKey,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                new ProcessInstanceRecord()));
    final var timer = new TimerInstance();
    timer.setElementInstanceKey(elementInstanceKey);
    timer.setKey(timerKey);
    timer.setDueDate(actorScheduler.getClock().millis() + delay.toMillis());
    processingState.getTimerState().store(timer);
  }
}
