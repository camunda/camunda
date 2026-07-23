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
      secretReferenceState.addWaitingJob(storeId, secretReference, jobKey);
      parkJob(jobKey);
    }
  }

  /**
   * Removes the job from the activatable index so a long poll does not collect it again while it
   * waits for secret resolution. The job keeps its {@code ACTIVATABLE} state and is reactivated
   * once the secret is resolved. A missing job record is skipped as a defensive guard.
   */
  private void parkJob(final long jobKey) {
    final JobRecord job = jobState.getJob(jobKey);
    if (job != null) {
      jobState.makeJobNotActivatable(jobKey, job);
    }
  }
}
