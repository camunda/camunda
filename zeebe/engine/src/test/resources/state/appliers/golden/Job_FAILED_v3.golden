/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.mutable.MutableJobMetricsState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobMetricsExportState;

/**
 * Owns the FAILED orchestration directly and, on the activatable branch, inserts into {@code
 * JOB_ACTIVATABLE_BY_PRIORITY} via {@link MutableJobState#makeJobActivatableByPriority}, instead of
 * going through the deprecated {@link MutableJobState#fail}.
 */
final class JobFailedV3Applier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableJobMetricsState jobMetricsState;

  JobFailedV3Applier(final MutableProcessingState state) {
    jobState = state.getJobState();
    jobMetricsState = state.getJobMetricsState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    if (value.getRetries() > 0) {
      if (value.getRetryBackoff() > 0) {
        jobState.addJobBackoff(key, value.getRecurringTime());
        jobState.updateJobRecord(key, value);
        jobState.updateJobState(key, State.FAILED);
        jobState.removeJobDeadline(key, value.getDeadline());
        jobState.makeJobNotActivatable(key, value);
      } else {
        jobState.updateJobRecord(key, value);
        jobState.updateJobState(key, State.ACTIVATABLE);
        jobState.removeJobDeadline(key, value.getDeadline());
        jobState.makeJobActivatableByPriority(
            value.getTypeBuffer(), key, value.getTenantId(), value.getPriority());
      }
    } else {
      jobState.updateJobRecord(key, value);
      jobState.updateJobState(key, State.FAILED);
      jobState.removeJobDeadline(key, value.getDeadline());
      jobState.makeJobNotActivatable(key, value);
    }

    jobMetricsState.incrementMetric(value, JobMetricsExportState.FAILED);
  }
}
