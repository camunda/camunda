/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.IncidentState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.collection.Tuple;

public final class BpmnIncidentBehavior implements StreamProcessorLifecycleAware {

  private final IncidentRecord incidentRecord = new IncidentRecord();

  private final IncidentState incidentState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final IncidentMetrics incidentMetrics;

  public BpmnIncidentBehavior(
      final ProcessingState processingState,
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final IncidentMetrics incidentMetrics) {
    incidentState = processingState.getIncidentState();
    elementInstanceState = processingState.getElementInstanceState();
    processState = processingState.getProcessState();
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
    this.incidentMetrics = incidentMetrics;
  }

  public void resolveJobIncident(final long jobKey) {
    final long incidentKey = incidentState.getJobIncidentKey(jobKey);
    final boolean hasIncident = incidentKey != IncidentState.MISSING_INCIDENT;

    if (hasIncident) {
      final IncidentRecord incidentRecord = incidentState.getIncidentRecord(incidentKey);
      stateWriter.appendFollowUpEvent(incidentKey, IncidentIntent.RESOLVED, incidentRecord);
      incidentMetrics.incidentResolved();
    }
  }

  public void createIncident(final Tuple<Failure, BpmnElementContext> failureAndContext) {
    createIncident(failureAndContext.getLeft(), failureAndContext.getRight());
  }

  /**
   * Raises an incident on the element instance of a job that cannot be handed to a worker, so the
   * reason is visible instead of the job being silently stuck. Both activation paths need this and
   * have no element context to build an incident from, only the job.
   *
   * <p>Only the job's identity is copied into the incident; the message is the caller's, so a
   * caller whose failure details could carry secret data must log those details instead of passing
   * them in.
   *
   * <p>A job that already carries an unresolved incident gets no second one. The job to incident
   * index holds one entry per job and is written with an insert, so a second incident for the same
   * job fails the applier rather than replacing the first. The incident the job already has says it
   * needs attention, which is what a repeat would say too.
   */
  public void createJobIncident(
      final long jobKey, final JobRecord job, final ErrorType errorType, final String message) {
    if (incidentState.getJobIncidentKey(jobKey) != IncidentState.MISSING_INCIDENT) {
      return;
    }

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();

    // a local record, not the reused incidentRecord field: this runs while an activation is being
    // written, where a half-built shared record is easy to leak into the next incident
    final var jobIncidentRecord =
        new IncidentRecord()
            .setErrorType(errorType)
            .setErrorMessage(message)
            .setBpmnProcessId(job.getBpmnProcessIdBuffer())
            .setProcessDefinitionKey(job.getProcessDefinitionKey())
            .setProcessInstanceKey(job.getProcessInstanceKey())
            .setStorageOrdinalKey(job.getStorageOrdinalKey())
            .setElementId(job.getElementIdBuffer())
            .setElementInstanceKey(job.getElementInstanceKey())
            .setJobKey(jobKey)
            .setTenantId(job.getTenantId())
            .setVariableScopeKey(job.getElementInstanceKey())
            .setElementInstancePath(treePathProperties.elementInstancePath())
            .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
            .setCallingElementPath(treePathProperties.callingElementPath());

    stateWriter.appendFollowUpEvent(
        keyGenerator.nextKey(), IncidentIntent.CREATED, jobIncidentRecord);
    incidentMetrics.incidentCreated();
  }

  public void createIncident(final Failure failure, final BpmnElementContext context) {
    final var variableScopeKey =
        failure.getVariableScopeKey() > 0
            ? failure.getVariableScopeKey()
            : context.getElementInstanceKey();

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(context.getElementInstanceKey())
            .build();

    incidentRecord.reset();
    incidentRecord
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setStorageOrdinalKey(context.getStorageOrdinalKey())
        .setBpmnProcessId(context.getBpmnProcessId())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setElementInstanceKey(context.getElementInstanceKey())
        .setElementId(context.getElementId())
        .setVariableScopeKey(variableScopeKey)
        .setErrorType(failure.getErrorType())
        .setErrorMessage(failure.getMessage())
        .setTenantId(context.getTenantId())
        .setElementInstancePath(treePathProperties.elementInstancePath())
        .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
        .setCallingElementPath(treePathProperties.callingElementPath());

    final var key = keyGenerator.nextKey();
    stateWriter.appendFollowUpEvent(key, IncidentIntent.CREATED, incidentRecord);
    incidentMetrics.incidentCreated();
  }

  public void resolveIncidents(final BpmnElementContext context) {
    resolveIncidents(context.getElementInstanceKey());
  }

  public void resolveIncidents(final long elementInstanceKey) {
    incidentState.forExistingProcessIncident(
        elementInstanceKey,
        (record, key) -> {
          stateWriter.appendFollowUpEvent(key, IncidentIntent.RESOLVED, record);
          incidentMetrics.incidentResolved();
        });
  }

  @Override
  public void onRecovered(final ReadonlyStreamProcessorContext context) {
    incidentMetrics.setPendingIncidents(incidentState.getIncidentCount());
  }
}
