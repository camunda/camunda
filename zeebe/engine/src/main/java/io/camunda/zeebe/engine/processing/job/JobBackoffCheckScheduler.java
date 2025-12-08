/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.scheduled.DueDateCheckScheduler;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.time.InstantSource;

public final class JobBackoffCheckScheduler implements StreamProcessorLifecycleAware {

  static final long BACKOFF_RESOLUTION = Duration.ofMillis(100).toMillis();

  private final DueDateCheckScheduler backOffDueDateCheckScheduler;

  public JobBackoffCheckScheduler(final InstantSource clock, final JobState jobState) {
    backOffDueDateCheckScheduler =
        new DueDateCheckScheduler(
            BACKOFF_RESOLUTION,
            false,
            taskResultBuilder ->
                jobState.findBackedOffJobs(
                    clock.millis(),
                    (key, record) ->
                        taskResultBuilder.appendCommandRecord(
                            key, JobIntent.RECUR_AFTER_BACKOFF, record)),
            clock);
  }

  public void scheduleBackOff(final long dueDate) {
    backOffDueDateCheckScheduler.schedule(dueDate);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    backOffDueDateCheckScheduler.onRecovered(context);
  }

  @Override
  public void onClose() {
    backOffDueDateCheckScheduler.onClose();
  }

  @Override
  public void onFailed() {
    backOffDueDateCheckScheduler.onFailed();
  }

  @Override
  public void onPaused() {
    backOffDueDateCheckScheduler.onPaused();
  }

  @Override
  public void onResumed() {
    backOffDueDateCheckScheduler.onResumed();
  }
}
