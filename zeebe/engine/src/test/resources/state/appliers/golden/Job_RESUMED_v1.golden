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
import io.camunda.zeebe.engine.state.mutable.MutableSuspensionState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/**
 * Makes a parked job activatable again when its process instance is resumed, using the type, tenant
 * and priority from the stored job. Does nothing unless the job is in {@link State#SUSPENDED}, so a
 * job that a worker or a failure owns is never re-inserted into the index. A job that was suspended
 * over secret-waiting becomes {@link State#ACTIVATABLE}; if secrets are still required, the next
 * activation path re-parks it for resolution.
 *
 * <p>Also advances the process instance's resume cursor ({@code
 * SuspensionState#getLastResumedJobKey}) to this job's key, so a later {@code RESUME_JOBS} cycle
 * can seek past already-resumed jobs instead of re-scanning from the beginning. Resumed jobs are
 * applied one at a time and this always records the most recently applied one; using it as a
 * monotonic high-water mark is only correct because the resume walk finds and resumes the lowest
 * remaining {@code SUSPENDED} job key each cycle, seeking the {@code JOBS_BY_PROCESS_INSTANCE}
 * index from this very cursor.
 */
public final class JobResumedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableSuspensionState suspensionState;

  JobResumedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
    suspensionState = state.getSuspensionState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    if (!jobState.isInState(key, State.SUSPENDED)) {
      return;
    }
    final JobRecord storedJob = jobState.getJob(key);
    if (storedJob == null) {
      return;
    }
    jobState.updateJobState(key, State.ACTIVATABLE);
    jobState.makeJobActivatableByPriority(
        storedJob.getTypeBuffer(), key, storedJob.getTenantId(), storedJob.getPriority());
    suspensionState.setLastResumedJobKey(storedJob.getProcessInstanceKey(), key);
  }
}
