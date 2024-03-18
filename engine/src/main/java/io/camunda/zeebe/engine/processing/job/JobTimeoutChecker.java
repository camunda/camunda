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
import io.camunda.zeebe.engine.api.Task;
import io.camunda.zeebe.engine.api.TaskResult;
import io.camunda.zeebe.engine.api.TaskResultBuilder;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import java.time.Duration;
import org.agrona.collections.MutableInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobTimeoutChecker implements StreamProcessorLifecycleAware {
  private static final Logger LOG = LoggerFactory.getLogger(JobTimeoutChecker.class);
  private final JobState state;
  private final Duration pollingInterval;
  private final int batchLimit;

  private boolean shouldReschedule = false;

  private ReadonlyStreamProcessorContext processingContext;
  private final Task deactivateTimedOutJobs;

  /** Keeps track of the timestamp to compare the message deadlines against. */
  private long executionTimestamp = -1;

  /** Keeps track of where to continue between iterations. */
  private DeadlineIndex startAtIndex = null;

  public JobTimeoutChecker(
      final JobState state, final Duration pollingInterval, final int batchLimit) {
    this.state = state;
    this.pollingInterval = pollingInterval;
    this.batchLimit = batchLimit;
    deactivateTimedOutJobs = new DeactivateTimeOutJobs();
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
    shouldReschedule = true;

    scheduleDeactivateTimedOutJobsTask(pollingInterval);
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
    shouldReschedule = true;
    scheduleDeactivateTimedOutJobsTask(pollingInterval);
  }

  private void scheduleDeactivateTimedOutJobsTask(final Duration idleInterval) {
    processingContext.getScheduleService().runDelayed(idleInterval, deactivateTimedOutJobs);
  }

  private void cancelTimer() {
    shouldReschedule = false;
    LOG.trace("Job timout checker canceled!");
  }

  private final class DeactivateTimeOutJobs implements Task {

    @Override
    public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
      LOG.trace("Job timout checker running...");
      if (executionTimestamp == -1) {
        executionTimestamp = currentTimeMillis();
      }

      final var counter = new MutableInteger(0);

      final DeadlineIndex nextIndex =
          state.forEachTimedOutEntry(
              executionTimestamp,
              startAtIndex,
              (key, record) -> {
                if (counter.getAndIncrement() >= batchLimit) {
                  return false;
                }

                return taskResultBuilder.appendCommandRecord(key, JobIntent.TIME_OUT, record);
              });

      if (shouldReschedule) {
        if (nextIndex != null) {
          LOG.trace(
              "Job timout checker yielded early. will reschedule immediately from where it left of.");
          startAtIndex = nextIndex;
          scheduleDeactivateTimedOutJobsTask(Duration.ZERO);
        } else {
          executionTimestamp = -1;
          startAtIndex = null;
          scheduleDeactivateTimedOutJobsTask(pollingInterval);
        }
      }

      LOG.trace("{} jobs has been marked to timeout", counter.get());

      return taskResultBuilder.build();
    }
  }
}
