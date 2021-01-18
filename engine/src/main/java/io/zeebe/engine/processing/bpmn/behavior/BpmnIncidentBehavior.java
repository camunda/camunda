/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.IncidentState;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.value.ErrorType;

public final class BpmnIncidentBehavior {

  private final IncidentRecord incidentRecord = new IncidentRecord();

  private final IncidentState incidentState;
  private final ElementInstanceState elementInstanceState;
  private final TypedStreamWriter streamWriter;
  private final KeyGenerator keyGenerator;

  public BpmnIncidentBehavior(final ZeebeState zeebeState, final TypedStreamWriter streamWriter) {
    incidentState = zeebeState.getIncidentState();
    elementInstanceState = zeebeState.getWorkflowState().getElementInstanceState();
    this.streamWriter = streamWriter;
    keyGenerator = zeebeState.getKeyGenerator();
  }

  public void resolveJobIncident(final long jobKey) {
    final long incidentKey = incidentState.getJobIncidentKey(jobKey);
    final boolean hasIncident = incidentKey != IncidentState.MISSING_INCIDENT;

    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
      streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
    }
  }

  public void createIncident(final Failure failure, final BpmnElementContext context) {
    final var variableScopeKey =
        failure.getVariableScopeKey() > 0
            ? failure.getVariableScopeKey()
            : context.getElementInstanceKey();

    createIncident(failure.getErrorType(), failure.getMessage(), context, variableScopeKey);
  }

  private void createIncident(
      final ErrorType errorType,
      final String errorMessage,
      final BpmnElementContext context,
      final long variableScopeKey) {

    // todo: check whether incident can be created like in CreateIncidentProcessor#L44

    incidentRecord.reset();
    incidentRecord
        .setWorkflowInstanceKey(context.getWorkflowInstanceKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setWorkflowKey(context.getWorkflowKey())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setElementId(context.getElementId())
        .setVariableScopeKey(variableScopeKey)
        .setErrorType(errorType)
        .setErrorMessage(errorMessage);

    // todo: move state behaviour to eventapplier
    elementInstanceState.storeRecord(
        context.getElementInstanceKey(),
        context.getFlowScopeKey(),
        context.getRecordValue(),
        context.getIntent(),
        Purpose.FAILED);

    final var incidentKey = keyGenerator.nextKey();
    streamWriter.appendFollowUpEvent(incidentKey, IncidentIntent.CREATED, incidentRecord);

    //    ServiceTask:Activate_Element -> WorkflowInstance:Element_Activating .... Incident:Created
    // {elementInstanceKey}
    //    Incident:Resolve -> WorkflowInstance:Element_Activated
  }

  public void resolveIncidents(final BpmnElementContext context) {
    incidentState.forExistingWorkflowIncident(
        context.getElementInstanceKey(),
        (record, key) -> streamWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, record));
  }
}
