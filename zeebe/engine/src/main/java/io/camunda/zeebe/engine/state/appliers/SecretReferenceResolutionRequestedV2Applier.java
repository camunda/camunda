/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProbeState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;

/** Parks the waiting probes as well as the waiting jobs, which version 1 knows nothing about. */
public final class SecretReferenceResolutionRequestedV2Applier
    implements TypedEventApplier<SecretReferenceIntent, SecretReferenceRecord> {

  private final MutableSecretReferenceState secretReferenceState;
  private final MutableJobState jobState;
  private final MutableProbeState probeState;

  public SecretReferenceResolutionRequestedV2Applier(
      final MutableSecretReferenceState secretReferenceState,
      final MutableJobState jobState,
      final MutableProbeState probeState) {
    this.secretReferenceState = secretReferenceState;
    this.jobState = jobState;
    this.probeState = probeState;
  }

  @Override
  public void applyState(final long key, final SecretReferenceRecord value) {
    final String storeId = value.getStoreId();
    final String secretReference = value.getSecretReference();

    secretReferenceState.addPendingSecretReference(storeId, secretReference);

    for (final long jobKey : value.getJobKeys()) {
      parkWaitingJob(storeId, secretReference, jobKey);
    }
    for (final long probeKey : value.getProbeKeys()) {
      if (probeState.getState(probeKey) != ProbeState.State.NOT_FOUND) {
        probeState.park(probeKey, storeId, secretReference);
      }
    }
  }

  /**
   * Records the job as waiting for the secret reference and parks it, so a long poll does not
   * collect it again while it waits for the resolution. A job that no longer exists is skipped
   * entirely, leaving no waiting entry behind for it.
   */
  private void parkWaitingJob(
      final String storeId, final String secretReference, final long jobKey) {
    final JobRecord job = jobState.getJob(jobKey);
    if (job == null) {
      return;
    }
    secretReferenceState.addWaitingJob(storeId, secretReference, jobKey);
    jobState.parkForSecretResolution(jobKey, job);
  }
}
