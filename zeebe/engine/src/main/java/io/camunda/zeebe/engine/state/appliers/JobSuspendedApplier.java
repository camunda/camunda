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
import java.util.EnumSet;
import java.util.Set;

/**
 * Parks a job of a suspended process instance: sets {@link State#SUSPENDED} and removes it from the
 * activatable index. Uses the stored job record for the index key so a mismatched event value
 * cannot leave a ghost entry.
 *
 * <p>Acts on {@link State#ACTIVATABLE} and {@link State#WAITING_FOR_SECRET_RESOLUTION}. Suspension
 * overrides secret-waiting so a later secret reactivation cannot put the job back into the index
 * while the process instance is still suspended. A job a worker or a failure already owns is left
 * alone.
 *
 * <p>The {@code JOBS_BY_PROCESS_INSTANCE} index entry is backfilled inside {@link
 * MutableJobState#updateJobState} on the transition to {@link State#SUSPENDED} — {@code
 * Job.SUSPENDED} is the only event that writes that state, so every suspended job is indexed there,
 * including a pre-8.10 job that has no entry from creation.
 */
public final class JobSuspendedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private static final Set<State> SUSPENDABLE_STATES =
      EnumSet.of(State.ACTIVATABLE, State.WAITING_FOR_SECRET_RESOLUTION);

  private final MutableJobState jobState;

  JobSuspendedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    if (!SUSPENDABLE_STATES.contains(jobState.getState(key))) {
      return;
    }
    final JobRecord storedJob = jobState.getJob(key);
    if (storedJob == null) {
      return;
    }
    jobState.updateJobState(key, State.SUSPENDED);
    jobState.makeJobNotActivatable(key, storedJob);
  }
}
