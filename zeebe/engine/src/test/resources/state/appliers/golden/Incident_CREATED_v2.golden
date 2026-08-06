/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableIncidentState;
import io.camunda.zeebe.engine.state.mutable.MutableJobState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;

/**
 * Adds {@code SECRET_RESOLUTION_ERROR} handling on top of {@link IncidentCreatedApplier} (v1). A
 * job whose secret injection fails during batch activation (see {@code
 * JobBatchActivateProcessor#raiseIncidentJobSecretInjectionFailed}) was never parked for background
 * resolution (contrast {@code SecretReferenceResolutionRequestedApplier#parkWaitingJob}) - it is
 * still sitting in the activatable index. Removing it here, while keeping its ACTIVATABLE state,
 * stops every subsequent poll from re-attempting the same failing injection and raising another
 * incident. {@link IncidentResolvedV4Applier} already re-inserts ACTIVATABLE jobs into the index
 * via {@code makeActivatableAfterSecretResolution} when this incident resolves, so this reuses that
 * existing recovery path. For a job that was already parked by the other producer of this same
 * error type ({@code SecretReferenceBatchCreateIncidentsProcessor}), this call is a harmless no-op:
 * it is already out of the index.
 */
final class IncidentCreatedV2Applier implements TypedEventApplier<IncidentIntent, IncidentRecord> {

  private final MutableIncidentState incidentState;
  private final MutableJobState jobState;

  IncidentCreatedV2Applier(
      final MutableIncidentState incidentState, final MutableJobState jobState) {
    this.incidentState = incidentState;
    this.jobState = jobState;
  }

  @Override
  public void applyState(final long incidentKey, final IncidentRecord value) {
    incidentState.createIncident(incidentKey, value);

    final long jobKey = value.getJobKey();
    if (jobKey == -1) {
      return;
    }

    if (ErrorType.MESSAGE_SIZE_EXCEEDED == value.getErrorType()) {
      final var jobRecord = jobState.getJob(jobKey);
      jobState.disable(jobKey, jobRecord);
    } else if (ErrorType.SECRET_RESOLUTION_ERROR == value.getErrorType()) {
      final var jobRecord = jobState.getJob(jobKey);
      jobState.makeJobNotActivatable(jobKey, jobRecord);
    }
  }
}
