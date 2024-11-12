/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import java.time.Duration;
import java.time.InstantSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobTimeoutCheckerScheduler implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(JobTimeoutCheckerScheduler.class);
  private final Duration pollingInterval;
  private final JobTimeoutChecker jobTimeoutChecker;

  public JobTimeoutCheckerScheduler(
      final JobState state,
      final Duration pollingInterval,
      final int batchLimit,
      final InstantSource clock) {
    this.pollingInterval = pollingInterval;
    jobTimeoutChecker = new JobTimeoutChecker(state, pollingInterval, batchLimit, clock);
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    jobTimeoutChecker.setProcessingContext(processingContext);
    jobTimeoutChecker.setShouldReschedule(true);
    jobTimeoutChecker.schedule(pollingInterval);
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
    jobTimeoutChecker.setShouldReschedule(true);
    jobTimeoutChecker.schedule(pollingInterval);
  }

  private void cancelTimer() {
    jobTimeoutChecker.setShouldReschedule(false);
    LOG.trace("Job timeout checker canceled!");
  }
}
