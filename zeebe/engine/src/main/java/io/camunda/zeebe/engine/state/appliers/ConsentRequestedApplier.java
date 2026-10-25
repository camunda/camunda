/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableConsentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.consent.ConsentRecord;
import io.camunda.zeebe.protocol.record.intent.ConsentIntent;

/** Parks the job of a consent request and indexes it by what it waits on. */
final class ConsentRequestedApplier implements TypedEventApplier<ConsentIntent, ConsentRecord> {

  private final MutableConsentState consentState;
  private final MutableJobState jobState;

  ConsentRequestedApplier(final MutableProcessingState state) {
    consentState = state.getConsentState();
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final ConsentRecord value) {
    final var job = jobState.getJob(value.getJobKey());
    if (job == null) {
      return;
    }
    jobState.parkForConsent(value.getJobKey(), job);
    consentState.addParkedJob(
        value.getSubject(), value.getConnection(), value.getOperation(), value.getJobKey());
  }
}
