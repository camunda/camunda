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

public class FailProcessor implements CommandProcessor<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to fail activated job with key '%d', but it %s";
  private final JobState state;

  public FailProcessor(JobState state) {
    this.state = state;
  }

  @Override
  public void onCommand(TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
    final long key = command.getKey();
    final JobState.State jobState = state.getState(key);

    if (jobState == State.ACTIVATED) {
      final JobRecord failedJob = state.getJob(key);
      failedJob.setRetries(command.getValue().getRetries());
      failedJob.setErrorMessage(command.getValue().getErrorMessageBuffer());
      state.fail(key, failedJob);

      commandControl.accept(JobIntent.FAILED, failedJob);
    } else if (jobState == State.ACTIVATABLE) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          String.format(NOT_ACTIVATED_JOB_MESSAGE, key, "must be activated first"));
    } else if (jobState == State.FAILED) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          String.format(NOT_ACTIVATED_JOB_MESSAGE, key, "is marked as failed"));
    } else {
      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NOT_ACTIVATED_JOB_MESSAGE, key, "does not exist"));
    }
  }
}
