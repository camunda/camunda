/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.scheduled.api.Result;
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
 * <p>Periodic with continuation: returns {@link Result.Builder#yieldNow(Object) yieldNow(cursor)}
 * when the configured batch limit is hit; the runtime stores the {@link JobTimeoutCursor} and hands
 * it back via {@link TaskContext#resumeCursor()} on the next run, so iteration resumes where this
 * run left off and is evaluated against the same execution timestamp. When the queue drains, the
 * run terminates with {@link Result.Builder#idle()} and the runtime clears the cursor.
 */
public final class JobTimeoutCheckScheduler implements ScheduledTask<JobTimeoutCursor> {

  private final JobState state;
  private final int batchLimit;

  public JobTimeoutCheckScheduler(final JobState state, final int batchLimit) {
    this.state = state;
    this.batchLimit = batchLimit;
  }

  @Override
  public String name() {
    return "job-timeout-check";
  }

  @Override
  public Result run(final TaskContext<JobTimeoutCursor> ctx) {
    final Result.Builder<JobTimeoutCursor> result = ctx.result();
    final JobTimeoutCursor saved = ctx.resumeCursor();
    final long executionTimestamp =
        saved != null ? saved.executionTimestamp() : ctx.clock().millis();
    final DeadlineIndex startAt = saved != null ? saved.resumeFrom() : null;

    final MutableInteger counter = new MutableInteger(0);

    final DeadlineIndex lastVisited =
        state.forEachTimedOutEntry(
            executionTimestamp,
            startAt,
            (key, record) -> {
              if (counter.getAndIncrement() >= batchLimit || ctx.shouldYield()) {
                return false;
              }
              return result.append(key, JobIntent.TIME_OUT, record);
            });

    if (lastVisited != null) {
      // Stopped early — keep the cursor so the runtime can resume on the next run.
      return result.yieldNow(new JobTimeoutCursor(executionTimestamp, lastVisited));
    }

    // Queue drained for this round.
    return result.idle();
  }
}
