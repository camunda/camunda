/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import java.util.Map;

public final class SecretReferenceBatchJobsReactivatedApplier
    implements TypedEventApplier<SecretReferenceIntent, SecretReferenceRecord> {

  private final MutableSecretReferenceState secretReferenceState;
  private final MutableJobState jobState;
  private final IncidentState incidentState;

  SecretReferenceBatchJobsReactivatedApplier(final MutableProcessingState processingState) {
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
    incidentState = processingState.getIncidentState();
  }

  @Override
  public void applyState(final long key, final SecretReferenceRecord value) {
    final var storeId = value.getStoreId();
    final var secretReference = value.getSecretReference();
    for (final long jobKey : value.getJobKeys()) {
      secretReferenceState.removeWaitingJob(storeId, secretReference, jobKey);
      if (isEligible(jobKey)) {
        jobState.makeActivatableAfterSecretResolution(jobKey);
      }
    }
  }

  private boolean isEligible(final long jobKey) {
    if (incidentState.getJobIncidentKey(jobKey) != IncidentState.MISSING_INCIDENT) {
      // an incident was already raised for the job (e.g. another of its secret references failed
      // permanently); the job must stay parked until the incident is resolved, which re-inserts it
      // into the activatable index
      return false;
    }
    for (final Map.Entry<String, String> ref :
        secretReferenceState.collectSecretReferencesByJob(jobKey)) {
      if (secretReferenceState.isPending(ref.getKey(), ref.getValue())) {
        return false;
      }
    }
    return true;
  }
}
