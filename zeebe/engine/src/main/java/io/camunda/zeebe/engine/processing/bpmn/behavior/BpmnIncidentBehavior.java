/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.collection.Tuple;

public final class BpmnIncidentBehavior {

  private final IncidentRecord incidentRecord = new IncidentRecord();

  private final IncidentState incidentState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public BpmnIncidentBehavior(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter) {
    incidentState = processingState.getIncidentState();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
  }

  public void resolveJobIncident(final long jobKey) {
    final long incidentKey = incidentState.getJobIncidentKey(jobKey);
    final boolean hasIncident = incidentKey != IncidentState.MISSING_INCIDENT;

    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
      stateWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
    }
  }

  public void createIncident(final Tuple<Failure, BpmnElementContext> failureAndContext) {
    createIncident(failureAndContext.getLeft(), failureAndContext.getRight());
  }

  public void createIncident(final Failure failure, final BpmnElementContext context) {
    final var incidentContext = failure.getContext() != null ? failure.getContext() : context;

    final var variableScopeKey =
        failure.getVariableScopeKey() > 0
            ? failure.getVariableScopeKey()
            : incidentContext.getElementInstanceKey();

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(incidentContext.getElementInstanceKey())
            .build();

    incidentRecord.reset();
    incidentRecord
        .setProcessInstanceKey(incidentContext.getProcessInstanceKey())
        .setBpmnProcessId(incidentContext.getBpmnProcessId())
        .setProcessDefinitionKey(incidentContext.getProcessDefinitionKey())
        .setElementInstanceKey(incidentContext.getElementInstanceKey())
        .setElementId(incidentContext.getElementId())
        .setVariableScopeKey(variableScopeKey)
        .setErrorType(failure.getErrorType())
        .setErrorMessage(failure.getMessage())
        .setTenantId(incidentContext.getTenantId())
        .setElementInstancePath(treePathProperties.elementInstancePath())
        .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
        .setCallingElementPath(treePathProperties.callingElementPath());

    final var key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, IncidentIntent.CREATED, incidentRecord);
  }

  public void resolveIncidents(final BpmnElementContext context) {
    resolveIncidents(context.getElementInstanceKey());
  }

  public void resolveIncidents(final long elementInstanceKey) {
    incidentState.forExistingProcessIncident(
        elementInstanceKey,
        (record, key) -> stateWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, record));
  }
}
