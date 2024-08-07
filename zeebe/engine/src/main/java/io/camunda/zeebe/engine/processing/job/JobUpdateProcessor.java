/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.processing.job.behaviour.JobUpdateBehaviour;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.ArrayList;
import java.util.List;

public class JobUpdateProcessor implements TypedRecordProcessor<JobRecord> {

  private final JobUpdateBehaviour jobUpdateBehaviour;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final StateWriter stateWriter;

  public JobUpdateProcessor(final JobUpdateBehaviour jobUpdateBehaviour, final Writers writers) {
    this.jobUpdateBehaviour = jobUpdateBehaviour;
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    stateWriter = writers.state();
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();
    jobUpdateBehaviour
        .getJob(jobKey, command)
        .ifRightOrLeft(
            job -> {
              final List<String> errors = new ArrayList<>();
              jobUpdateBehaviour.updateJobRetries(jobKey, job, command).ifPresent(errors::add);
              final long timeout = command.getValue().getTimeout();
              // if no timeout is provided (the default value is -1L), no update
              if (timeout > -1L) {
                jobUpdateBehaviour.updateJobTimeout(jobKey, job, command).ifPresent(errors::add);
              }
              if (errors.isEmpty()) {
                stateWriter.appendFollowUpEvent(jobKey, JobIntent.UPDATED, job);
                responseWriter.writeEventOnCommand(jobKey, JobIntent.UPDATED, job, command);
              } else {
                final String errorMessage = String.join(", ", errors);
                rejectionWriter.appendRejection(
                    command, RejectionType.INVALID_ARGUMENT, errorMessage);
                responseWriter.writeRejectionOnCommand(
                    command, RejectionType.INVALID_ARGUMENT, errorMessage);
              }
            },
            errorMessage -> {
              responseWriter.writeRejectionOnCommand(
                  command, RejectionType.NOT_FOUND, errorMessage);
            });
  }
}
