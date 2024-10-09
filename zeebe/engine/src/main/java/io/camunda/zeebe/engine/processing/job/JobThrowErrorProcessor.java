/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.engine.EngineConfiguration.DEFAULT_MAX_ERROR_MESSAGE_SIZE;
import static io.camunda.zeebe.util.StringUtil.limitString;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.analyzers.CatchEventAnalyzer;
import io.camunda.zeebe.engine.state.analyzers.CatchEventAnalyzer.CatchEventTuple;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.Optional;

public class JobThrowErrorProcessor implements CommandProcessor<JobRecord> {

  /**
   * Marker element ID. This ID is used to indicate that a given catch event could not be found. The
   * marker ID is used to prevent repeated catch event lookups, which is an expensive operation
   * (particularly when no catch event can be found)
   */
  public static final String NO_CATCH_EVENT_FOUND = "NO_CATCH_EVENT_FOUND";

  public static final String ERROR_REJECTION_MESSAGE =
      "Cannot throw BPMN error from %s job with key '%d', type '%s' and processInstanceKey '%d'";

  private final IncidentRecord incidentEvent = new IncidentRecord();
  private Either<Failure, CatchEventTuple> foundCatchEvent;

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard defaultProcessor;
  private final CatchEventAnalyzer stateAnalyzer;
  private final KeyGenerator keyGenerator;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final JobMetrics jobMetrics;
  private final ProcessState processState;

  public JobThrowErrorProcessor(
      final ProcessingState state,
      final BpmnEventPublicationBehavior eventPublicationBehavior,
      final KeyGenerator keyGenerator,
      final JobMetrics jobMetrics,
      final AuthorizationCheckBehavior authCheckBehavior) {
    this.keyGenerator = keyGenerator;
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
    eventScopeInstanceState = state.getEventScopeInstanceState();

    defaultProcessor =
        new DefaultJobCommandPreconditionGuard(
            "throw an error for", jobState, this::acceptCommand, authCheckBehavior);

    stateAnalyzer = new CatchEventAnalyzer(state.getProcessState(), elementInstanceState);
    this.eventPublicationBehavior = eventPublicationBehavior;
    this.jobMetrics = jobMetrics;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> commandControl) {
    return defaultProcessor.onCommand(command, commandControl);
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long jobKey,
      final Intent intent,
      final JobRecord job) {
    jobMetrics.jobErrorThrown(job.getType(), job.getJobKind());

    final var serviceTaskInstanceKey = job.getElementId();

    if (NO_CATCH_EVENT_FOUND.equals(serviceTaskInstanceKey)) {
      raiseIncident(jobKey, job, stateWriter, foundCatchEvent.getLeft());
      return;
    }

    eventPublicationBehavior.throwErrorEvent(foundCatchEvent.get(), job.getVariablesBuffer());
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command,
      final CommandControl<JobRecord> commandControl,
      final JobRecord job) {
    final long jobKey = command.getKey();

    // Check if the job is of kind EXECUTION_LISTENER. Execution Listener jobs should not throw
    // BPMN errors because the element is not in an ACTIVATED state.
    final var jobKind = job.getJobKind();
    if (jobKind == JobKind.EXECUTION_LISTENER) {
      final long processInstanceKey = job.getProcessInstanceKey();
      commandControl.reject(
          RejectionType.INVALID_STATE,
          String.format(
              ERROR_REJECTION_MESSAGE, jobKind, jobKey, job.getType(), processInstanceKey));
      return;
    }

    job.setErrorCode(command.getValue().getErrorCodeBuffer());
    job.setErrorMessage(
        limitString(command.getValue().getErrorMessage(), DEFAULT_MAX_ERROR_MESSAGE_SIZE));
    job.setVariables(command.getValue().getVariablesBuffer());

    final var serviceTaskInstanceKey = job.getElementInstanceKey();
    final var serviceTaskInstance = elementInstanceState.getInstance(serviceTaskInstanceKey);

    final var errorCode = job.getErrorCodeBuffer();
    final var foundCatchEvent =
        stateAnalyzer.findErrorCatchEvent(
            errorCode, serviceTaskInstance, Optional.of(job.getErrorMessageBuffer()));
    this.foundCatchEvent = foundCatchEvent;

    if (foundCatchEvent.isLeft()) {
      job.setElementId(NO_CATCH_EVENT_FOUND);

      commandControl.accept(JobIntent.ERROR_THROWN, job);
    } else if (!serviceTaskInstanceIsActive(serviceTaskInstance)) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          "Expected to find active service task, but was " + serviceTaskInstance);
    } else if (!eventScopeInstanceState.canTriggerEvent(
        foundCatchEvent.get().getElementInstance().getKey(),
        foundCatchEvent.get().getCatchEvent().getId())) {
      commandControl.reject(
          RejectionType.INVALID_STATE,
          "Expected to find event scope that is accepting events, but was "
              + foundCatchEvent.get().getElementInstance());
    } else {
      commandControl.accept(JobIntent.ERROR_THROWN, job);
    }
  }

  private boolean serviceTaskInstanceIsActive(final ElementInstance serviceTaskInstance) {
    return serviceTaskInstance != null && serviceTaskInstance.isActive();
  }

  private void raiseIncident(
      final long key, final JobRecord job, final StateWriter stateWriter, final Failure failure) {

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceState(elementInstanceState)
            .withProcessState(processState)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();

    incidentEvent.reset();
    incidentEvent
        .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .setErrorMessage(failure.getMessage())
        .setBpmnProcessId(job.getBpmnProcessIdBuffer())
        .setProcessDefinitionKey(job.getProcessDefinitionKey())
        .setProcessInstanceKey(job.getProcessInstanceKey())
        .setElementId(job.getElementIdBuffer())
        .setElementInstanceKey(job.getElementInstanceKey())
        .setTenantId(job.getTenantId())
        .setJobKey(key)
        .setVariableScopeKey(job.getElementInstanceKey())
        .setElementInstancePath(treePathProperties.elementInstancePath())
        .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
        .setCallingElementPath(treePathProperties.callingElementPath());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
  }
}
