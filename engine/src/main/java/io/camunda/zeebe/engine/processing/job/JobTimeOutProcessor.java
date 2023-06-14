/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.util.sched.clock.ActorClock;

public final class JobTimeOutProcessor implements CommandProcessor<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to time out activated job with key '%d', but %s";
  private final JobState jobState;
  private final JobMetrics jobMetrics;

  public JobTimeOutProcessor(final ZeebeState state, final JobMetrics jobMetrics) {
    jobState = state.getJobState();
    this.jobMetrics = jobMetrics;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final var jobKey = command.getKey();
    final var job = jobState.getJob(jobKey);
    final var state = jobState.getState(jobKey);

    if (state == State.ACTIVATED && hasTimedOut(job)) {
      commandControl.accept(JobIntent.TIMED_OUT, job);
      jobMetrics.jobTimedOut(job.getType());
    } else {
      final var reason =
          switch (state) {
            case ACTIVATED -> "it has not timed out";
            case ACTIVATABLE -> "it must be activated first";
            case FAILED -> "it is marked as failed and is not activated";
            case ERROR_THROWN -> "it has thrown an error and is not activated";
            case NOT_FOUND -> "no such job was found";
          };

      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NOT_ACTIVATED_JOB_MESSAGE, jobKey, reason));
    }
    return true;
  }

  private boolean hasTimedOut(final JobRecord job) {
    return job.getDeadline() < ActorClock.currentTimeMillis();
  }
}
