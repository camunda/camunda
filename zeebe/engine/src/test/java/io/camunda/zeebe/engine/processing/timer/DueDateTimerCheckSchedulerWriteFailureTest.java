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
  @EnumSource(WriteFailure.class)
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

  private void storeTimerDueIn(@SuppressWarnings("SameParameterValue") final Duration delay) {
    final long elementInstanceKey = 1L;
    processingState
        .getElementInstanceState()
        .createInstance(
            new ElementInstance(
                elementInstanceKey,
                ProcessInstanceIntent.ELEMENT_ACTIVATED,
                new ProcessInstanceRecord()));
    final var timer = new TimerInstance();
    timer.setElementInstanceKey(elementInstanceKey);
    timer.setKey(2L);
    timer.setDueDate(actorScheduler.getClock().millis() + delay.toMillis());
    processingState.getTimerState().store(timer);
  }
}
