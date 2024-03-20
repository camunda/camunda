/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobTimeoutChecker implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(JobTimeoutChecker.class);
  private final Duration pollingInterval;
  private final DeactivateTimeOutJobs deactivateTimedOutJobs;

  public JobTimeoutChecker(
      final JobState state, final Duration pollingInterval, final int batchLimit) {
    this.pollingInterval = pollingInterval;
    deactivateTimedOutJobs = new DeactivateTimeOutJobs(state, pollingInterval, batchLimit);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    deactivateTimedOutJobs.setProcessingContext(processingContext);
    deactivateTimedOutJobs.setShouldReschedule(true);
    deactivateTimedOutJobs.schedule(pollingInterval);
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
    deactivateTimedOutJobs.setShouldReschedule(true);
    deactivateTimedOutJobs.schedule(pollingInterval);
  }

  private void cancelTimer() {
    deactivateTimedOutJobs.setShouldReschedule(false);
    LOG.trace("Job timeout checker canceled!");
  }
}
