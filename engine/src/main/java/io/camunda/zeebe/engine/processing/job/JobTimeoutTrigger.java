/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;

public final class JobTimeoutTrigger implements StreamProcessorLifecycleAware {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobState state;

  private boolean shouldReschedule = false;

  private ReadonlyStreamProcessorContext processingContext;
  private final Task deactivateTimedOutJobs;
  private final InstantSource clock;

  public JobTimeoutTrigger(final JobState state, final InstantSource clock) {
    this.state = state;
    this.clock = clock;
    deactivateTimedOutJobs = new DeactivateTimeOutJobs();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
    shouldReschedule = true;

    scheduleDeactivateTimedOutJobsTask();
  }

  @Override
  public void onClose() {
    cancelTimer();
  }

  @Override
  public void onFailed() {
    cancelTimer();
  }

  @Override
  public void onPaused() {
    cancelTimer();
  }

  @Override
  public void onResumed() {
    if (shouldReschedule) {
      scheduleDeactivateTimedOutJobsTask();
    }
  }

  private void scheduleDeactivateTimedOutJobsTask() {
    processingContext
        .getScheduleService()
        .runDelayed(TIME_OUT_POLLING_INTERVAL, deactivateTimedOutJobs);
  }

  private void cancelTimer() {
    shouldReschedule = false;
  }

  private final class DeactivateTimeOutJobs implements Task {

    @Override
    public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
      final long now = clock.millis();
      state.forEachTimedOutEntry(
          now,
          (key, record) -> taskResultBuilder.appendCommandRecord(key, JobIntent.TIME_OUT, record));
      if (shouldReschedule) {
        scheduleDeactivateTimedOutJobsTask();
      }
      return taskResultBuilder.build();
    }
  }
}
