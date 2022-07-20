/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.scheduler.clock.ActorClock.currentTimeMillis;

import io.camunda.zeebe.engine.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.engine.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.LegacyTypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.time.Duration;

public final class JobTimeoutTrigger implements StreamProcessorLifecycleAware {
  public static final Duration TIME_OUT_POLLING_INTERVAL = Duration.ofSeconds(30);
  private final JobState state;

  private boolean shouldReschedule = false;

  private LegacyTypedCommandWriter writer;
  private ReadonlyStreamProcessorContext processingContext;

  public JobTimeoutTrigger(final JobState state) {
    this.state = state;
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
    shouldReschedule = true;
    scheduleDeactivateTimedOutJobsTask();
    writer = processingContext.getLogStreamWriter();
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
        .runDelayed(TIME_OUT_POLLING_INTERVAL, this::deactivateTimedOutJobs);
  }

  private void cancelTimer() {
    shouldReschedule = false;
  }

  void deactivateTimedOutJobs() {
    final long now = currentTimeMillis();
    state.forEachTimedOutEntry(
        now,
        (key, record) -> {
          writer.reset();
          writer.appendFollowUpCommand(key, JobIntent.TIME_OUT, record);

          return writer.flush() >= 0;
        });
    if (shouldReschedule) {
      scheduleDeactivateTimedOutJobsTask();
    }
  }
}
