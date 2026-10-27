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
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;

public final class SecretReferenceResolutionRequestedApplier
    implements TypedEventApplier<SecretReferenceIntent, SecretReferenceRecord> {

  private final MutableSecretReferenceState secretReferenceState;
  private final MutableJobState jobState;

  public SecretReferenceResolutionRequestedApplier(
      final MutableSecretReferenceState secretReferenceState, final MutableJobState jobState) {
    this.secretReferenceState = secretReferenceState;
    this.jobState = jobState;
  }

  @Override
  public void applyState(final long key, final SecretReferenceRecord value) {
    final String storeId = value.getStoreId();
    final String secretReference = value.getSecretReference();

    secretReferenceState.addPendingSecretReference(storeId, secretReference);

    for (final long jobKey : value.getJobKeys()) {
      parkWaitingJob(storeId, secretReference, jobKey);
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
