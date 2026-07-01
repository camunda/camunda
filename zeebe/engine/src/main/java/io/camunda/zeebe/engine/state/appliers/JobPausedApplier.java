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
 * Applies {@link JobIntent#PAUSED}: transitions a job from {@code State.ACTIVATED} to {@code
 * State.PAUSED} and removes its activation deadline. Paired with {@code JobPauseProcessor} on the
 * command side; both are gated under {@link Capability#JOB_PAUSE_RESUME} (ordinal 19).
 *
 * <p>Below the gate the engine emits neither the {@code PAUSE} command (admission rejects) nor the
 * {@code PAUSED} event, so this applier is never invoked on a pre-feature replica. The record
 * stream remains byte-identical to a pre-feature broker, which is the property that makes a
 * mid-rolling-upgrade leader handover safe.
 */
public final class JobPausedApplier implements TypedEventApplier<JobIntent, JobRecord> {

  private final MutableJobState jobState;

  public JobPausedApplier(final MutableProcessingState state) {
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final JobRecord value) {
    jobState.pause(key, value);
  }

  @Override
  public Capability gatedBy() {
    return Capability.JOB_PAUSE_RESUME;
  }
}
