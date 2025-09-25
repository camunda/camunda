/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.job.processing;

import io.camunda.zeebe.engine.job.state.immutable.JobState;
import io.camunda.zeebe.engine.job.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.scheduling.Task;
import io.camunda.zeebe.stream.api.scheduling.TaskResult;
import io.camunda.zeebe.stream.api.scheduling.TaskResultBuilder;
import java.time.Duration;
import java.time.InstantSource;
import org.agrona.collections.MutableInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class JobTimeoutChecker implements Task {
  private static final Logger LOG = LoggerFactory.getLogger(JobTimeoutChecker.class);

  private boolean shouldReschedule = false;

  /** Keeps track of the timestamp to compare the message deadlines against. */
  private long executionTimestamp = -1;

  /** Keeps track of where to continue between iterations. */
  private DeadlineIndex startAtIndex = null;

  private final JobState state;
  private ReadonlyStreamProcessorContext processingContext;
  private final Duration pollingInterval;
  private final int batchLimit;
  private final InstantSource clock;

  public JobTimeoutChecker(
      final JobState state,
      final Duration pollingInterval,
      final int batchLimit,
      final InstantSource clock) {
    this.state = state;
    this.pollingInterval = pollingInterval;
    this.batchLimit = batchLimit;
    this.clock = clock;
  }

  public void schedule(final Duration idleInterval) {
    if (shouldReschedule) {
      processingContext.getScheduleService().runAt(clock.millis() + idleInterval.toMillis(), this);
    }
  }

  @Override
  public TaskResult execute(final TaskResultBuilder taskResultBuilder) {
    LOG.trace("Job timeout checker running...");
    if (executionTimestamp == -1) {
      executionTimestamp = clock.millis();
    }

    final var counter = new MutableInteger(0);

    final DeadlineIndex lastVisitedIndex =
        state.forEachTimedOutEntry(
            executionTimestamp,
            startAtIndex,
            (key, record) -> {
              if (counter.getAndIncrement() >= batchLimit) {
                return false;
              }

              return taskResultBuilder.appendCommandRecord(key, JobIntent.TIME_OUT, record);
            });

    if (lastVisitedIndex != null) {
      LOG.trace(
          "Job timeout checker yielded early. Will reschedule immediately from {}",
          lastVisitedIndex);
      startAtIndex = lastVisitedIndex;
      schedule(Duration.ZERO);
    } else {
      executionTimestamp = -1;
      startAtIndex = null;
      schedule(pollingInterval);
    }

    LOG.trace("{} timeout job commands appended to task result builder", counter.get());

    return taskResultBuilder.build();
  }

  public void setProcessingContext(final ReadonlyStreamProcessorContext processingContext) {
    this.processingContext = processingContext;
  }

  public void setShouldReschedule(final boolean shouldReschedule) {
    this.shouldReschedule = shouldReschedule;
  }
}
