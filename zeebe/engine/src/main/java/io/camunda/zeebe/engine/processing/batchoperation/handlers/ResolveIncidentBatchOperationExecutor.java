/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.batchoperation.PersistedBatchOperation;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.stream.api.FollowUpCommandMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResolveIncidentBatchOperationExecutor implements BatchOperationExecutor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ResolveIncidentBatchOperationExecutor.class);

  final TypedCommandWriter commandWriter;
  final IncidentState incidentState;
  private final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  public ResolveIncidentBatchOperationExecutor(
      final TypedCommandWriter commandWriter,
      final IncidentState incidentState,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this.commandWriter = commandWriter;
    this.incidentState = incidentState;
    this.brokerRequestAuthorizationConverter = brokerRequestAuthorizationConverter;
  }

  @Override
  public void execute(final long incidentKey, final PersistedBatchOperation batchOperation) {
    final var incident = incidentState.getIncidentRecord(incidentKey);
    if (incident == null) {
      // ok I admit it, this is a bit of a hack, but we need to provoke an IncidentIntent.REVOLVE
      // rejection here, so we still append the resolve incident command
      resolveIncident(incidentKey, batchOperation);
      return;
    }

    if (incidentState.isJobIncident(incident)) {
      LOGGER.trace("Increasing retries for job with key '{}'", incident.getJobKey());
      final var jobRecord = new JobRecord().setRetries(1);
      commandWriter.appendFollowUpCommand(
          incident.getJobKey(), JobIntent.UPDATE_RETRIES, jobRecord);
    }

    LOGGER.trace("Resolving incident with key '{}'", incidentKey);
    resolveIncident(incidentKey, batchOperation);
  }

  private void resolveIncident(
      final long incidentKey, final PersistedBatchOperation batchOperation) {
    final var command = new IncidentRecord();
    final var authentication = batchOperation.getAuthentication();
    final var claims = brokerRequestAuthorizationConverter.convert(authentication);
    commandWriter.appendFollowUpCommand(
        incidentKey,
        IncidentIntent.RESOLVE,
        command,
        FollowUpCommandMetadata.of(
            b -> b.batchOperationReference(batchOperation.getKey()).claims(claims)));
  }
}
