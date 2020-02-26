/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.job;

import io.zeebe.engine.processor.CommandProcessor;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.instance.JobState;
import io.zeebe.engine.state.instance.JobState.State;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.JobIntent;

public class JobThrowErrorProcessor implements CommandProcessor<JobRecord> {

  private static final String NO_JOB_FOUND_MESSAGE =
      "Expected to throw an error for job with key '%d', but no such job was found";
  private static final String INVALID_JOB_STATE_MESSAGE =
      "Expected to throw an error for job with key '%d', but it is in state '%s'";

  private final JobState state;

  public JobThrowErrorProcessor(final JobState state) {
    this.state = state;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final State jobState = state.getState(jobKey);

    if (jobState == State.ACTIVATABLE || jobState == State.ACTIVATED) {
      acceptCommand(jobKey, command, commandControl);

    } else if (jobState == State.NOT_FOUND) {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, jobKey));

    } else {
      commandControl.reject(
          RejectionType.INVALID_STATE, String.format(INVALID_JOB_STATE_MESSAGE, jobKey, jobState));
    }

    return true;
  }

  private void acceptCommand(
      final long jobKey,
      final TypedRecord<JobRecord> command,
      final CommandControl<JobRecord> commandControl) {

    final JobRecord job = state.getJob(jobKey);
    job.setErrorCode(command.getValue().getErrorCodeBuffer());
    job.setErrorMessage(command.getValue().getErrorMessageBuffer());

    state.throwError(jobKey, job);

    commandControl.accept(JobIntent.ERROR_THROWN, job);
  }
}
