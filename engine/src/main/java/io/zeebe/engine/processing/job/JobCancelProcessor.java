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
import io.zeebe.engine.state.immutable.ZeebeState;
import io.zeebe.protocol.impl.record.value.job.JobRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.JobIntent;

public final class JobCancelProcessor implements CommandProcessor<JobRecord> {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to cancel job with key '%d', but no such job was found";
  private final JobState jobState;

  public JobCancelProcessor(final ZeebeState state) {
    jobState = state.getJobState();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobRecord job = jobState.getJob(jobKey);
    if (job != null) {
      commandControl.accept(JobIntent.CANCELED, job);
    } else {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, jobKey));
    }

    return true;
  }
}
