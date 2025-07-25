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

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.analyzers.CatchEventAnalyzer;
import io.camunda.zeebe.engine.state.analyzers.CatchEventAnalyzer.CatchEventTuple;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class JobThrowErrorProcessor implements TypedRecordProcessor<JobRecord> {

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
  private final JobCommandPreconditionChecker preconditionChecker;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final CatchEventAnalyzer stateAnalyzer;
  private final KeyGenerator keyGenerator;
  private final EventScopeInstanceState eventScopeInstanceState;
  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final JobProcessingMetrics jobMetrics;
  private final ProcessState processState;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final StateWriter stateWriter;

  public JobThrowErrorProcessor(
      final ProcessingState state,
      final BpmnEventPublicationBehavior eventPublicationBehavior,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final AuthorizationCheckBehavior authCheckBehavior,
      final Writers writers) {
    this.keyGenerator = keyGenerator;
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
    eventScopeInstanceState = state.getEventScopeInstanceState();
    this.authCheckBehavior = authCheckBehavior;

    preconditionChecker =
        new JobCommandPreconditionChecker(
            jobState,
            "throw an error for",
            List.of(State.ACTIVATABLE, State.ACTIVATED),
            authCheckBehavior);

    stateAnalyzer = new CatchEventAnalyzer(state.getProcessState(), elementInstanceState);
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();

    this.eventPublicationBehavior = eventPublicationBehavior;
    this.jobMetrics = jobMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    preconditionChecker
        .check(state, record)
        .flatMap(job -> checkAuthorization(record, job))
        .ifRightOrLeft(
            job -> throwError(record, job),
            rejection -> {
              rejectionWriter.appendRejection(record, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(record, rejection.type(), rejection.reason());
            });
  }

  private void throwError(final TypedRecord<JobRecord> command, final JobRecord job) {
    final long jobKey = command.getKey();

    // Check if the job is of kind EXECUTION_LISTENER. Execution Listener jobs should not throw
    // BPMN errors because the element is not in an ACTIVATED state.
    final var jobKind = job.getJobKind();
    if (jobKind == JobKind.EXECUTION_LISTENER) {
      final long processInstanceKey = job.getProcessInstanceKey();
      final var errorMessage =
          ERROR_REJECTION_MESSAGE.formatted(jobKind, jobKey, job.getType(), processInstanceKey);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, errorMessage);
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
      stateWriter.appendFollowUpEvent(jobKey, JobIntent.ERROR_THROWN, job);
      responseWriter.writeEventOnCommand(jobKey, JobIntent.ERROR_THROWN, job, command);
    } else if (!serviceTaskInstanceIsActive(serviceTaskInstance)) {
      final var errorMessage =
          "Expected to find active service task, but was %s".formatted(serviceTaskInstance);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, errorMessage);
    } else if (!eventScopeInstanceState.canTriggerEvent(
        foundCatchEvent.get().getElementInstance().getKey(),
        foundCatchEvent.get().getCatchEvent().getId())) {
      final var elementInstance = foundCatchEvent.get().getElementInstance();
      final var errorMessage =
          "Expected to find event scope that is accepting events, but was %s"
              .formatted(elementInstance);
      rejectionWriter.appendRejection(command, RejectionType.INVALID_STATE, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.INVALID_STATE, errorMessage);
    } else {
      stateWriter.appendFollowUpEvent(jobKey, JobIntent.ERROR_THROWN, job);
      responseWriter.writeEventOnCommand(jobKey, JobIntent.ERROR_THROWN, job, command);
    }

    raiseIncident(jobKey, job);
  }

  public void raiseIncident(final long jobKey, final JobRecord job) {
    jobMetrics.countJobEvent(JobAction.ERROR_THROWN, job.getJobKind(), job.getType());

    if (NO_CATCH_EVENT_FOUND.equals(job.getElementId())) {
      raiseIncident(jobKey, job, foundCatchEvent.getLeft());
      return;
    }

    eventPublicationBehavior.throwErrorEvent(foundCatchEvent.get(), job.getVariablesBuffer());
  }

  private boolean serviceTaskInstanceIsActive(final ElementInstance serviceTaskInstance) {
    return serviceTaskInstance != null && serviceTaskInstance.isActive();
  }

  private void raiseIncident(final long key, final JobRecord job, final Failure failure) {

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(job.getElementInstanceKey())
            .build();

    incidentEvent.reset();
    incidentEvent
        .setErrorType(ErrorType.UNHANDLED_ERROR_EVENT)
        .setErrorMessage(failure.getMessage())
        .setBpmnProcessId(job.getBpmnProcessIdBuffer())
        .setProcessDefinitionKey(job.getProcessDefinitionKey())
        .setProcessInstanceKey(job.getProcessInstanceKey())
        .setElementId(getElementId(job))
        .setElementInstanceKey(job.getElementInstanceKey())
        .setTenantId(job.getTenantId())
        .setJobKey(key)
        .setVariableScopeKey(job.getElementInstanceKey())
        .setElementInstancePath(treePathProperties.elementInstancePath())
        .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
        .setCallingElementPath(treePathProperties.callingElementPath());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
  }

  private DirectBuffer getElementId(final JobRecord job) {
    if (NO_CATCH_EVENT_FOUND.equals(job.getElementId())) {
      final var elementInstance = elementInstanceState.getInstance(job.getElementInstanceKey());
      if (elementInstance != null) {
        return elementInstance.getValue().getElementIdBuffer();
      }
    }
    return job.getElementIdBuffer();
  }

  private Either<Rejection, JobRecord> checkAuthorization(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    final var request =
        new AuthorizationRequest(
                command,
                AuthorizationResourceType.PROCESS_DEFINITION,
                PermissionType.UPDATE_PROCESS_INSTANCE,
                job.getTenantId())
            .addResourceId(job.getBpmnProcessId());
    return authCheckBehavior.isAuthorized(request).map(unused -> job);
  }
}
