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
 * Parks an activatable job of a suspended process instance: sets {@link State#SUSPENDED} and
 * removes it from the activatable index. Does nothing unless the job is {@link State#ACTIVATABLE},
 * so a job a worker or a failure already owns is left alone.
 */
public final class JobSuspendedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  JobSuspendedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    if (!jobState.isInState(key, State.ACTIVATABLE)) {
      return;
    }
    jobState.updateJobState(key, State.SUSPENDED);
    jobState.makeJobNotActivatable(key, value);
  }
}
