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
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class SecretReferenceBatchJobsReactivatedApplier
    implements TypedEventApplier<SecretReferenceIntent, SecretReferenceRecord> {

  private final MutableSecretReferenceState secretReferenceState;
  private final MutableJobState jobState;

  SecretReferenceBatchJobsReactivatedApplier(final MutableProcessingState processingState) {
    secretReferenceState = processingState.getSecretReferenceState();
    jobState = processingState.getJobState();
  }

  @Override
  public void applyState(final long key, final SecretReferenceRecord value) {
    final var storeId = value.getStoreId();
    final var secretReference = value.getSecretReference();
    for (final long jobKey : value.getJobKeys()) {
      secretReferenceState.removeWaitingJob(storeId, secretReference, jobKey);
      if (isEligible(jobKey)) {
        jobState.makeActivatable(jobKey);
      }
    }
  }

  private boolean isEligible(final long jobKey) {
    final List<Map.Entry<String, String>> refs = new ArrayList<>();
    secretReferenceState.visitSecretReferencesByJob(
        jobKey,
        (secretId, secretRef) -> {
          refs.add(Map.entry(secretId, secretRef));
          return true;
        });
    for (final Map.Entry<String, String> ref : refs) {
      if (secretReferenceState.isPending(ref.getKey(), ref.getValue())) {
        return false;
      }
    }
    return true;
  }
}
