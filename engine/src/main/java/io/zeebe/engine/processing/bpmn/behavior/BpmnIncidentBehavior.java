/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.behavior;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.state.immutable.IncidentState;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableZeebeState;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.ErrorType;

public final class BpmnIncidentBehavior {

  private final IncidentRecord incidentCommand = new IncidentRecord();

  private final IncidentState incidentState;
  private final MutableElementInstanceState elementInstanceState;
  private final TypedStreamWriter streamWriter;

  public BpmnIncidentBehavior(
      final MutableZeebeState zeebeState, final TypedStreamWriter streamWriter) {
    incidentState = zeebeState.getIncidentState();
    elementInstanceState = zeebeState.getElementInstanceState();
    this.streamWriter = streamWriter;
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

    incidentCommand.reset();
    incidentCommand
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setElementId(context.getElementId())
        .setVariableScopeKey(variableScopeKey)
        .setErrorType(errorType)
        .setErrorMessage(errorMessage);

    // todo: remove storing record after incident is migrated
    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      final var intent = determineCommandIntent(context);
      elementInstanceState.storeRecord(
          context.getElementInstanceKey(),
          context.getFlowScopeKey(),
          context.getRecordValue(),
          intent,
          Purpose.FAILED);
    }

    streamWriter.appendNewCommand(IncidentIntent.CREATE, incidentCommand);
  }

  private ProcessInstanceIntent determineCommandIntent(final BpmnElementContext context) {
    if (!MigratedStreamProcessors.isMigrated(context.getBpmnElementType())) {
      return context.getIntent();
    } else {
      if (context.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING) {
        return ProcessInstanceIntent.ACTIVATE_ELEMENT;
      } else if (context.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETING) {
        return ProcessInstanceIntent.COMPLETE_ELEMENT;
      }
    }
    throw new UnsupportedOperationException(
        String.format(
            "Expected to raise incident, but element state %s not one of incident supporting states %s %s",
            context.getIntent(),
            ProcessInstanceIntent.ELEMENT_ACTIVATING,
            ProcessInstanceIntent.ELEMENT_COMPLETING));
  }

  public void resolveIncidents(final BpmnElementContext context) {
    incidentState.forExistingProcessIncident(
        context.getElementInstanceKey(),
        (record, key) -> streamWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, record));
  }
}
