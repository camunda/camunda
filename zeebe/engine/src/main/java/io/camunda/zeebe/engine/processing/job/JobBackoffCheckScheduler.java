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
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/**
 * Re-activates jobs whose backoff period has elapsed by writing a {@link
 * JobIntent#RECUR_AFTER_BACKOFF} command for each.
 *
 * <p>On-demand: returns {@link Outcome.AwaitDueAt} for the next due-date so the runtime sleeps
 * until then. External callers (e.g. {@code JobFailProcessor}) call {@code
 * managed.requestRun(dueDate)} when a new job enters backoff to wake the task earlier if needed.
 */
public final class JobBackoffCheckScheduler implements ScheduledTask {

  /**
   * Minimum resolution in millis between consecutive runs of this scheduler. Used by the runtime's
   * {@code minResolution} and exposed for tests that need to time-travel past it.
   */
  public static final long BACKOFF_RESOLUTION = 100L;

  private final JobState jobState;

  public JobBackoffCheckScheduler(final JobState jobState) {
    this.jobState = jobState;
  }

  @Override
  public String name() {
    return "job-backoff-check";
  }

  @Override
  public Outcome run(final TaskContext ctx) {
    final long nextDueDate =
        jobState.findBackedOffJobs(
            ctx.clock().millis(),
            (key, record) -> ctx.sink().append(key, JobIntent.RECUR_AFTER_BACKOFF, record));

    return nextDueDate > 0 ? new Outcome.AwaitDueAt(nextDueDate) : Outcome.IDLE;
  }
}
