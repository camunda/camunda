/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.metrics.IncidentMetrics;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

/**
 * Raises an incident on the element instance of a job that cannot be handed to a worker, so the
 * reason is visible instead of the job being silently stuck. Shared by the two activation paths,
 * which both need the incident but have no element context to build it from, only the job.
 *
 * <p>Only the job's identity is copied into the incident; the message is the caller's, so a caller
 * whose failure details could carry secret data must log those details instead of passing them in.
 */
public final class JobIncidentBehavior {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final IncidentMetrics incidentMetrics;

  public JobIncidentBehavior(
      final ProcessingState state,
      final KeyGenerator keyGenerator,
      final StateWriter stateWriter,
      final IncidentMetrics incidentMetrics) {
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
    this.incidentMetrics = incidentMetrics;
  }

  /** Appends an incident for the job, on the element instance the job belongs to. */
  public void createIncident(
      final long jobKey, final JobRecord job, final ErrorType errorType, final String message) {
    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();

    final var incidentEvent =
        new IncidentRecord()
            .setErrorType(errorType)
            .setErrorMessage(wrapString(message))
            .setBpmnProcessId(job.getBpmnProcessIdBuffer())
            .setProcessDefinitionKey(job.getProcessDefinitionKey())
            .setProcessInstanceKey(job.getProcessInstanceKey())
            .setElementId(job.getElementIdBuffer())
            .setElementInstanceKey(job.getElementInstanceKey())
            .setJobKey(jobKey)
            .setTenantId(job.getTenantId())
            .setVariableScopeKey(job.getElementInstanceKey())
            .setElementInstancePath(treePathProperties.elementInstancePath())
            .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
            .setCallingElementPath(treePathProperties.callingElementPath());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
    incidentMetrics.incidentCreated();
  }
}
