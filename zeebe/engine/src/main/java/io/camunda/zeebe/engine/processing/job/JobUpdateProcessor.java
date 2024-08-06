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
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.stream.api.records.TypedRecord;

public class JobUpdateProcessor implements TypedRecordProcessor<JobRecord> {

  private final JobUpdateBehaviour jobUpdateBehaviour;

  public JobUpdateProcessor(final JobUpdateBehaviour jobUpdateBehaviour) {
    this.jobUpdateBehaviour = jobUpdateBehaviour;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> command) {
    final long jobKey = command.getKey();

    final var jobRecord = jobUpdateBehaviour.getJobOrAppendRejection(jobKey, command);
    if (jobRecord == null) {
      return;
    }
    final var updatedRetries = jobUpdateBehaviour.updateJobRetries(jobKey, jobRecord, command);
    final var updatedTimeout = jobUpdateBehaviour.updateJobTimeout(jobKey, jobRecord, command);
    // if retries or timeout are updated the command is rejected
    if (updatedRetries || updatedTimeout) {
      jobUpdateBehaviour.completeJobUpdate(jobKey, jobRecord, command);
    } else {
      jobUpdateBehaviour.rejectJobUpdate(command);
    }
  }
}
