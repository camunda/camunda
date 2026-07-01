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
import io.camunda.zeebe.protocol.impl.clusterversion.ClusterVersionCatalog.Capability;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.JobIntent;

/**
 * Applies {@link JobIntent#RESUMED}: transitions a job from {@code State.PAUSED} back to {@code
 * State.ACTIVATED} and re-registers its activation deadline. Mirror of {@code JobPausedApplier};
 * both are gated under {@link Capability#JOB_PAUSE_RESUME}.
 *
 * <p>Below the gate this applier is never invoked because the {@code RESUME} command's admission
 * rejects and the {@code RESUMED} event never reaches the log.
 */
public final class JobResumedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  public JobResumedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.resume(key, value);
  }

  @Override
  public Capability gatedBy() {
    return Capability.JOB_PAUSE_RESUME;
  }
}
