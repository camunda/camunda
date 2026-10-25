/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/** Version 4 plus: a job parked for consent leaves the parked index when it is cancelled. */
final class JobCanceledV5Applier implements TypedEventApplier<JobIntent, JobRecord> {

  private final JobCanceledV4Applier v4;
  private final MutableProcessingState state;

  JobCanceledV5Applier(final MutableProcessingState state) {
    v4 = new JobCanceledV4Applier(state);
    this.state = state;
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    v4.applyState(key, value);
    state.getConsentState().removeParkedJob(key);
  }
}
