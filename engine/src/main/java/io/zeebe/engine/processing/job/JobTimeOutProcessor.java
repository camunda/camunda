/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.job;

import io.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.immutable.JobState;
import io.zeebe.engine.state.immutable.JobState.State;
import io.zeebe.engine.state.immutable.ZeebeState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.JobIntent;

public final class JobTimeOutProcessor implements CommandProcessor<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to time out activated job with key '%d', but %s";
  private final JobState jobState;

  public JobTimeOutProcessor(final ZeebeState state) {
    jobState = state.getJobState();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobState.State state = jobState.getState(jobKey);

    if (state == State.ACTIVATED) {
      commandControl.accept(JobIntent.TIMED_OUT, command.getValue());
    } else {
      final String textState;

      switch (state) {
        case ACTIVATABLE:
          textState = "it must be activated first";
          break;
        case FAILED:
          textState = "it is marked as failed";
          break;
        default:
          textState = "no such job was found";
          break;
      }

      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NOT_ACTIVATED_JOB_MESSAGE, jobKey, textState));
    }
    return true;
  }
}
