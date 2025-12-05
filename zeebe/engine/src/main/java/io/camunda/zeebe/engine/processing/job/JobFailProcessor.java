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
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.common.ElementTreePathBuilder;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
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
import org.agrona.DirectBuffer;

public final class JobFailProcessor implements TypedRecordProcessor<JobRecord> {

  private static final DirectBuffer DEFAULT_ERROR_MESSAGE = wrapString("No more retries left.");
  private final IncidentRecord incidentEvent = new IncidentRecord();

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final KeyGenerator keyGenerator;
  private final JobProcessingMetrics jobMetrics;
  private final JobBackoffChecker jobBackoffChecker;
  private final VariableBehavior variableBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final AuthorizationCheckBehavior authCheckBehavior;
  private final SideEffectWriter sideEffectWriter;
  private final JobCommandPreconditionChecker preconditionChecker;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public JobFailProcessor(
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final JobProcessingMetrics jobMetrics,
      final JobBackoffChecker jobBackoffChecker,
      final BpmnBehaviors bpmnBehaviors,
      final AuthorizationCheckBehavior authCheckBehavior) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    variableBehavior = bpmnBehaviors.variableBehavior();
    jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    this.authCheckBehavior = authCheckBehavior;
    preconditionChecker =
        new JobCommandPreconditionChecker(
            jobState, "fail", List.of(State.ACTIVATABLE, State.ACTIVATED), authCheckBehavior);
    this.keyGenerator = keyGenerator;
    this.jobBackoffChecker = jobBackoffChecker;
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
            failedJob -> failJob(record, failedJob),
            rejection -> {
              rejectionWriter.appendRejection(record, rejection.type(), rejection.reason());
              responseWriter.writeRejectionOnCommand(record, rejection.type(), rejection.reason());
            });
  }

  private void failJob(final TypedRecord<JobRecord> record, final JobRecord failedJob) {
    final long jobKey = record.getKey();
    final JobRecord failJobCommandRecord = record.getValue();
    final var retries = failJobCommandRecord.getRetries();
    final var retryBackOff = failJobCommandRecord.getRetryBackoff();

    failedJob.setRetries(retries);
    failedJob.setErrorMessage(
        limitString(failJobCommandRecord.getErrorMessage(), DEFAULT_MAX_ERROR_MESSAGE_SIZE));
    failedJob.setRetryBackoff(retryBackOff);
    failedJob.setVariables(failJobCommandRecord.getVariablesBuffer());

    if (retries > 0 && retryBackOff > 0) {
      final long receivedTime = record.getTimestamp();
      failedJob.setRecurringTime(receivedTime + retryBackOff);
      sideEffectWriter.appendSideEffect(
          () -> {
            jobBackoffChecker.scheduleBackOff(retryBackOff + receivedTime);
            return true;
          });
    }
    stateWriter.appendFollowUpEvent(jobKey, JobIntent.FAILED, failedJob);
    responseWriter.writeEventOnCommand(jobKey, JobIntent.FAILED, failedJob, record);
    jobMetrics.countJobEvent(JobAction.FAILED, failedJob.getJobKind(), failedJob.getType());

    setFailedVariables(failedJob);

    final boolean retryImmediately = retries > 0 && retryBackOff <= 0;
    if (retryImmediately) {
      jobActivationBehavior.publishWork(jobKey, failedJob);
    }

    if (retries <= 0) {
      raiseIncident(jobKey, failedJob);
    }
  }

  private void setFailedVariables(final JobRecord value) {
    // set fail job variables locally
    final DirectBuffer variables = value.getVariablesBuffer();
    if (variables.capacity() > 0) {
      variableBehavior.mergeLocalDocument(
          value.getElementInstanceKey(),
          value.getProcessDefinitionKey(),
          value.getProcessInstanceKey(),
          value.getBpmnProcessIdBuffer(),
          value.getTenantId(),
          variables);
    }
  }

  private void raiseIncident(final long key, final JobRecord value) {
    final DirectBuffer jobErrorMessage = value.getErrorMessageBuffer();
    DirectBuffer incidentErrorMessage = DEFAULT_ERROR_MESSAGE;
    if (jobErrorMessage.capacity() > 0) {
      incidentErrorMessage = jobErrorMessage;
    }

    final var treePathProperties =
        new ElementTreePathBuilder()
            .withElementInstanceProvider(elementInstanceState::getInstance)
            .withCallActivityIndexProvider(processState::getFlowElement)
            .withElementInstanceKey(value.getElementInstanceKey())
            .build();

    incidentEvent.reset();
    incidentEvent
        .setErrorType(determineErrorType(value))
        .setErrorMessage(incidentErrorMessage)
        .setBpmnProcessId(value.getBpmnProcessIdBuffer())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setElementId(value.getElementIdBuffer())
        .setElementInstanceKey(value.getElementInstanceKey())
        .setJobKey(key)
        .setVariableScopeKey(value.getElementInstanceKey())
        .setTenantId(value.getTenantId())
        .setElementInstancePath(treePathProperties.elementInstancePath())
        .setProcessDefinitionPath(treePathProperties.processDefinitionPath())
        .setCallingElementPath(treePathProperties.callingElementPath());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
  }

  private ErrorType determineErrorType(final JobRecord jobRecord) {
    return switch (jobRecord.getJobKind()) {
      case JobKind.BPMN_ELEMENT -> ErrorType.JOB_NO_RETRIES;
      case JobKind.EXECUTION_LISTENER -> ErrorType.EXECUTION_LISTENER_NO_RETRIES;
      case JobKind.TASK_LISTENER -> ErrorType.TASK_LISTENER_NO_RETRIES;
      case JobKind.AD_HOC_SUB_PROCESS -> ErrorType.AD_HOC_SUB_PROCESS_NO_RETRIES;
    };
  }

  private Either<Rejection, JobRecord> checkAuthorization(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.UPDATE_PROCESS_INSTANCE)
            .tenantId(job.getTenantId())
            .addResourceId(job.getBpmnProcessId())
            .build();
    return authCheckBehavior.isAuthorizedOrInternalCommand(request).map(unused -> job);
  }
}
