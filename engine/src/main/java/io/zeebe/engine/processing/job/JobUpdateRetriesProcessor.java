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

public final class JobUpdateRetriesProcessor implements CommandProcessor<JobRecord> {

  private static final String NO_JOB_FOUND_MESSAGE =
      "Expected to update retries for job with key '%d', but no such job was found";
  private static final String NEGATIVE_RETRIES_MESSAGE =
      "Expected to update retries for job with key '%d' with a positive amount of retries, "
          + "but the amount given was '%d'";

  private final JobState jobState;

  public JobUpdateRetriesProcessor(final ZeebeState state) {
    jobState = state.getJobState();
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long key = command.getKey();
    final int retries = command.getValue().getRetries();

    if (retries > 0) {
      final JobRecord job = jobState.getJob(key);

      if (job != null) {
        // update retries for response sent to client
        job.setRetries(retries);

        commandControl.accept(JobIntent.RETRIES_UPDATED, job);
      } else {
        commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, key));
      }
    } else {
      commandControl.reject(
          RejectionType.INVALID_ARGUMENT, String.format(NEGATIVE_RETRIES_MESSAGE, key, retries));
    }
    return true;
  }
}
