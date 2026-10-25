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

/** Makes the jobs a grant released activatable again. */
final class ConsentReleasedApplier implements TypedEventApplier<ConsentIntent, ConsentRecord> {

  private final MutableConsentState consentState;
  private final MutableJobState jobState;

  ConsentReleasedApplier(final MutableProcessingState state) {
    consentState = state.getConsentState();
    jobState = state.getJobState();
  }

  @Override
  public void applyState(final long key, final ConsentRecord value) {
    for (final long jobKey : value.getJobKeys()) {
      consentState.removeParkedJob(jobKey);
      jobState.makeActivatableAfterConsent(jobKey);
    }
  }
}
