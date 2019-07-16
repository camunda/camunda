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

public class CompleteProcessor implements CommandProcessor<JobRecord> {
  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to complete job with key '%d', but no such job was found";
  public static final String FAILED_JOB_MESSAGE =
      "Expected to complete job with key '%d', but the job is marked as failed";

  private final JobState state;

  public CompleteProcessor(JobState state) {
    this.state = state;
  }

  @Override
  public void onCommand(TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobState.State jobState = state.getState(jobKey);

    if (jobState == State.NOT_FOUND) {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, jobKey));
    } else if (jobState == State.FAILED) {
      commandControl.reject(RejectionType.INVALID_STATE, String.format(FAILED_JOB_MESSAGE, jobKey));
    } else {
      final JobRecord job = state.getJob(jobKey);
      job.setVariables(command.getValue().getVariablesBuffer());

      state.complete(jobKey, job);
      commandControl.accept(JobIntent.COMPLETED, job);
    }
  }
}
