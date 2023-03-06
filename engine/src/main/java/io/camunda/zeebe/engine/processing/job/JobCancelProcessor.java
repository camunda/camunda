/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

public final class JobCancelProcessor implements CommandProcessor<JobRecord> {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to cancel job with key '%d', but no such job was found";
  private final JobState jobState;
  private final JobMetrics jobMetrics;

  public JobCancelProcessor(final ProcessingState state, final JobMetrics jobMetrics) {
    jobState = state.getJobState();
    this.jobMetrics = jobMetrics;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    final long jobKey = command.getKey();
    final JobRecord job = jobState.getJob(jobKey);
    if (job != null) {
      // Note that this logic is duplicated in BpmnJobBehavior, if you change this please change
      // it there as well.
      commandControl.accept(JobIntent.CANCELED, job);
      jobMetrics.jobCanceled(job.getType());
    } else {
      commandControl.reject(RejectionType.NOT_FOUND, String.format(NO_JOB_FOUND_MESSAGE, jobKey));
    }

    return true;
  }
}
