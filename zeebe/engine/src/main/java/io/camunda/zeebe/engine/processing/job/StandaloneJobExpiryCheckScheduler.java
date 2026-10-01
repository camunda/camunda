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

/**
 * Expires the standalone jobs that no worker answered in time, by writing an {@link
 * JobIntent#EXPIRE} command for each of them once its expiry has passed.
 */
public final class StandaloneJobExpiryCheckScheduler implements StreamProcessorLifecycleAware {

  static final long EXPIRY_RESOLUTION = Duration.ofMillis(100).toMillis();

  private final DueDateCheckScheduler expiryDueDateChecker;

  public StandaloneJobExpiryCheckScheduler(final InstantSource clock, final JobState jobState) {
    expiryDueDateChecker =
        new DueDateCheckScheduler(
            EXPIRY_RESOLUTION,
            false,
            taskResultBuilder ->
                jobState.findExpiredStandaloneJobs(
                    clock.millis(),
                    (key, record) ->
                        taskResultBuilder.appendCommandRecord(key, JobIntent.EXPIRE, record)),
            clock);
  }

  public void scheduleExpiry(final long expiresAt) {
    expiryDueDateChecker.schedule(expiresAt);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    expiryDueDateChecker.onRecovered(context);
  }

  @Override
  public void onClose() {
    expiryDueDateChecker.onClose();
  }

  @Override
  public void onFailed() {
    expiryDueDateChecker.onFailed();
  }

  @Override
  public void onPaused() {
    expiryDueDateChecker.onPaused();
  }

  @Override
  public void onResumed() {
    expiryDueDateChecker.onResumed();
  }
}
