/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public final class JobUpdateRetriesProcessor implements TypedRecordProcessor<JobRecord> {

  private static final String NO_JOB_FOUND_MESSAGE =
      "Expected to update retries for job with key '%d', but no such job was found";
  private static final String NEGATIVE_RETRIES_MESSAGE =
      "Expected to update retries for job with key '%d' with a positive amount of retries, "
          + "but the amount given was '%d'";

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;

  public JobUpdateRetriesProcessor(final ProcessingState state, final Writers writers) {
    jobState = state.getJobState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long key = command.getKey();
    final int retries = command.getValue().getRetries();

    if (retries > 0) {
      final JobRecord job = jobState.getJob(key, command.getAuthorizations());

      if (job != null) {
        // update retries for response sent to client
        job.setRetries(retries);

        stateWriter.appendFollowUpEvent(key, JobIntent.RETRIES_UPDATED, job);
        responseWriter.writeEventOnCommand(key, JobIntent.RETRIES_UPDATED, job, command);
      } else {
        rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, key));
        responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, key));
      }
    } else {
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, key));
      responseWriter.writeRejectionOnCommand(
          command, RejectionType.INVALID_ARGUMENT, String.format(NEGATIVE_RETRIES_MESSAGE, key, retries));
    }
  }
}
