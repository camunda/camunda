/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableSecretReferenceState;
import io.camunda.zeebe.protocol.impl.record.value.secretreference.SecretReferenceRecord;
import io.camunda.zeebe.protocol.record.intent.SecretReferenceIntent;

public final class SecretReferenceResolutionRequestedApplier
    implements TypedEventApplier<SecretReferenceIntent, SecretReferenceRecord> {

  private final MutableSecretReferenceState secretReferenceState;

  public SecretReferenceResolutionRequestedApplier(
      final MutableSecretReferenceState secretReferenceState) {
    this.secretReferenceState = secretReferenceState;
  }

  @Override
  public void applyState(final long key, final SecretReferenceRecord value) {
    final String storeId = value.getStoreId();
    final String secretReference = value.getSecretReference();

    secretReferenceState.addPendingSecretReference(storeId, secretReference);

    for (final long jobKey : value.getJobKeys()) {
      secretReferenceState.addWaitingJob(storeId, secretReference, jobKey);
    }
  }
}
