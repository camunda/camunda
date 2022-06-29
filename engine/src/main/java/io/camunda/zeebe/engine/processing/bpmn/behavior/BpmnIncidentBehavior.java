/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateBuilder;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.util.collection.Tuple;

public final class BpmnIncidentBehavior {

  private final IncidentRecord incidentRecord = new IncidentRecord();

  private final IncidentState incidentState;
  private final StateBuilder stateBuilder;
  private final KeyGenerator keyGenerator;

  public BpmnIncidentBehavior(
      final ZeebeState zeebeState,
      final KeyGenerator keyGenerator,
      final StateBuilder stateBuilder) {
    incidentState = zeebeState.getIncidentState();
    this.keyGenerator = keyGenerator;
    this.stateBuilder = stateBuilder;
  }

  public void resolveJobIncident(final long jobKey) {
    final long incidentKey = incidentState.getJobIncidentKey(jobKey);
    final boolean hasIncident = incidentKey != IncidentState.MISSING_INCIDENT;

    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
      stateBuilder.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
    }
  }

  public void createIncident(final Tuple<Failure, BpmnElementContext> failureAndContext) {
    createIncident(failureAndContext.getLeft(), failureAndContext.getRight());
  }

  public void createIncident(final Failure failure, final BpmnElementContext context) {
    final var variableScopeKey =
        failure.getVariableScopeKey() > 0
            ? failure.getVariableScopeKey()
            : context.getElementInstanceKey();

    incidentRecord.reset();
    incidentRecord
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setElementId(context.getElementId())
        .setVariableScopeKey(variableScopeKey)
        .setErrorType(failure.getErrorType())
        .setErrorMessage(failure.getMessage());

    final var key = keyGenerator.nextKey();
    stateBuilder.appendFollowUpEvent(key, IncidentIntent.CREATED, incidentRecord);
  }

  public void resolveIncidents(final BpmnElementContext context) {
    incidentState.forExistingProcessIncident(
        context.getElementInstanceKey(),
        (record, key) -> stateBuilder.appendFollowUpEvent(key, IncidentIntent.RESOLVED, record));
  }
}
