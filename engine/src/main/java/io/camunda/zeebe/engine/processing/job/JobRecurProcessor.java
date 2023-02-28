/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

public class JobRecurProcessor implements CommandProcessor<JobRecord> {

  private static final String NOT_FAILED_JOB_MESSAGE =
      "Expected to back off failed job with key '%d', but %s";
  private final JobState jobState;

  public JobRecurProcessor(final ProcessingState processingState) {
    jobState = processingState.getJobState();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobState.State state = jobState.getState(jobKey);

    if (state == State.FAILED) {
      commandControl.accept(JobIntent.RECURRED_AFTER_BACKOFF, command.getValue());
    } else {
      final String textState;

      switch (state) {
        case ACTIVATABLE:
          textState = "it is already activable";
          break;
        case ACTIVATED:
          textState = "it is already activated";
          break;
        case ERROR_THROWN:
          textState = "it is in error state";
          break;
        default:
          textState = "no such job was found";
          break;
      }

      commandControl.reject(
          RejectionType.NOT_FOUND, String.format(NOT_FAILED_JOB_MESSAGE, jobKey, textState));
    }
    return true;
  }
}
