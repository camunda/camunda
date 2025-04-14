/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.handlers;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResolveIncidentBatchOperationExecutor implements BatchOperationExecutor {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ResolveIncidentBatchOperationExecutor.class);

  final TypedCommandWriter commandWriter;
  final IncidentState incidentState;

  public ResolveIncidentBatchOperationExecutor(
      final TypedCommandWriter commandWriter, final IncidentState incidentState) {
    this.commandWriter = commandWriter;
    this.incidentState = incidentState;
  }

  @Override
  public void handle(final long incidentKey, final long batchOperationKey) {
    final var incident = incidentState.getIncidentRecord(incidentKey);
    if (incidentState.isJobIncident(incident)) {
      LOGGER.info("Increasing retries for job with key '{}'", incident.getJobKey());
      final var jobRecord = new JobRecord().setRetries(1);
      commandWriter.appendFollowUpCommand(
          incident.getJobKey(), JobIntent.UPDATE_RETRIES, jobRecord);
    }

    LOGGER.info("Resolving incident with key '{}'", incidentKey);
    final var command = new IncidentRecord();
    commandWriter.appendFollowUpCommand(
        incidentKey, IncidentIntent.RESOLVE, command, batchOperationKey);
  }
}
