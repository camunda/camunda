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

public class TimeOutProcessor implements CommandProcessor<JobRecord> {
  public static final String NOT_ACTIVATED_JOB_MESSAGE =
      "Expected to time out activated job with key '%d', but %s";
  private final JobState state;

  public TimeOutProcessor(JobState state) {
    this.state = state;
  }

  @Override
  public void onCommand(TypedRecord<JobRecord> command, CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobState.State jobState = state.getState(jobKey);

    if (jobState == State.ACTIVATED) {
      state.timeout(command.getKey(), command.getValue());
      commandControl.accept(JobIntent.TIMED_OUT, command.getValue());
    } else {
      final String textState;

      switch (jobState) {
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
  }
}
