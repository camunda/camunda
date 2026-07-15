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
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/**
 * Owns the RECURRED_AFTER_BACKOFF orchestration directly and inserts into {@code
 * JOB_ACTIVATABLE_BY_PRIORITY} via {@link MutableJobState#makeJobActivatableByPriority}, instead of
 * going through the deprecated {@link MutableJobState#recurAfterBackoff}.
 */
public class JobRecurredV2Applier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  JobRecurredV2Applier(final MutableProcessingState processingState) {
    jobState = processingState.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.updateJobRecord(key, value);
    jobState.updateJobState(key, State.ACTIVATABLE);
    jobState.removeJobDeadline(key, value.getDeadline());
    jobState.makeJobActivatableByPriority(
        value.getTypeBuffer(), key, value.getTenantId(), value.getPriority());
    jobState.removeJobBackoff(key, value.getRecurringTime());
  }
}
