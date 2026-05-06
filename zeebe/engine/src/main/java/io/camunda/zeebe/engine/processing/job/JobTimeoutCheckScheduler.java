/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.scheduled.api.Outcome;
import io.camunda.zeebe.engine.processing.scheduled.api.ScheduledTask;
import io.camunda.zeebe.engine.processing.scheduled.api.TaskContext;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.DeadlineIndex;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.agrona.collections.MutableInteger;

/**
 * Times out activated jobs whose deadline has passed by writing a {@link JobIntent#TIME_OUT}
 * command for each.
 *
 * <p>Periodic with continuation: returns {@link Outcome.YieldNow} when the configured batch limit
 * is hit, retaining a cursor in {@link #startAtIndex} so the next run resumes where this one left
 * off. When the queue drains the cursor is cleared and the runtime falls back to its periodic
 * interval.
 */
public final class JobTimeoutCheckScheduler implements ScheduledTask {

  private final JobState state;
  private final int batchLimit;

  /** Stable timestamp across continuation iterations; cleared when the queue drains. */
  private long executionTimestamp = -1;

  /** Cursor for the next continuation; null between drains. */
  private DeadlineIndex startAtIndex = null;

  public JobTimeoutCheckScheduler(final JobState state, final int batchLimit) {
    this.state = state;
    this.batchLimit = batchLimit;
  }

  @Override
  public String name() {
    return "job-timeout-check";
  }

  @Override
  public Outcome run(final TaskContext ctx) {
    if (executionTimestamp == -1) {
      executionTimestamp = ctx.clock().millis();
    }

    final MutableInteger counter = new MutableInteger(0);

    final DeadlineIndex lastVisited =
        state.forEachTimedOutEntry(
            executionTimestamp,
            startAtIndex,
            (key, record) -> {
              if (counter.getAndIncrement() >= batchLimit || ctx.shouldYield()) {
                return false;
              }
              return ctx.sink().append(key, JobIntent.TIME_OUT, record);
            });

    if (lastVisited != null) {
      // Stopped early — keep the cursor and ask the runtime to reschedule immediately.
      startAtIndex = lastVisited;
      return Outcome.YIELD_NOW;
    }

    // Queue drained for this round.
    executionTimestamp = -1;
    startAtIndex = null;
    return Outcome.IDLE;
  }
}
