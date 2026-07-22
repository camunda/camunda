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
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;

public final class SecretReferenceResolutionRequestedApplier
    implements TypedEventApplier<SecretReferenceIntent, SecretReferenceRecord> {

  private final MutableSecretReferenceState secretReferenceState;
  private final MutableJobState jobState;

  public SecretReferenceResolutionRequestedApplier(final MutableProcessingState processingState) {
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
  }

  @Override
  public void applyState(final long key, final SecretReferenceRecord value) {
    final String storeId = value.getStoreId();
    final String secretReference = value.getSecretReference();

    secretReferenceState.addPendingSecretReference(storeId, secretReference);

    for (final long jobKey : value.getJobKeys()) {
      secretReferenceState.addWaitingJob(storeId, secretReference, jobKey);
      // stop a waiting job from being collected by a long poll again until it is reactivated once
      // its secrets resolve. The job stays in state ACTIVATABLE and is only removed from the
      // activatable index. A concurrent priority update re-inserts it there (see
      // DbJobState#updateJobPriority). If the secret is still uncached at the next activation,
      // the job is parked again and the parking self-heals. If the secret got cached by then, the
      // job activates normally while its waiting entries remain in the SecretReferenceState, so
      // the reactivation flow (see #57852) must only make a job activatable again if it still
      // exists and is in state ACTIVATABLE, and must remove the waiting entries of every job
      // regardless.
      final JobRecord job = jobState.getJob(jobKey);
      if (job != null) {
        jobState.makeJobNotActivatable(jobKey, job);
      }
    }
  }
}
