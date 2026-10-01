/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/** Removes a standalone job that expired before a worker answered it. */
final class JobExpiredApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;
  private final MutableSecretReferenceState secretReferenceState;

  JobExpiredApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
    secretReferenceState = state.getSecretReferenceState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.removeStandaloneJob(key, value);
    jobState.delete(key, value);
    secretReferenceState.removeAllSecretReferencesByJobKey(key);
  }
}
