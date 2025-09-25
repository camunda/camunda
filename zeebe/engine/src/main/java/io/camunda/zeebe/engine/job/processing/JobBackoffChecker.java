/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.job.processing;

import io.camunda.zeebe.engine.common.processing.scheduled.DueDateChecker;
import io.camunda.zeebe.engine.job.state.immutable.JobState;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.time.InstantSource;

public final class JobBackoffChecker implements StreamProcessorLifecycleAware {

  public static final long BACKOFF_RESOLUTION = Duration.ofMillis(100).toMillis();

  private final DueDateChecker backOffDueDateChecker;

  public JobBackoffChecker(final InstantSource clock, final JobState jobState) {
    backOffDueDateChecker =
        new DueDateChecker(
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
    backOffDueDateChecker.schedule(dueDate);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    backOffDueDateChecker.onRecovered(context);
  }

  @Override
  public void onClose() {
    backOffDueDateChecker.onClose();
  }

  @Override
  public void onFailed() {
    backOffDueDateChecker.onFailed();
  }

  @Override
  public void onPaused() {
    backOffDueDateChecker.onPaused();
  }

  @Override
  public void onResumed() {
    backOffDueDateChecker.onResumed();
  }
}
