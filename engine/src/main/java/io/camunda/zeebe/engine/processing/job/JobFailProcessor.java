/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.util.StringUtil.limitString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobActivationBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.SideEffectWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.List;
import org.agrona.DirectBuffer;

public final class JobFailProcessor implements TypedRecordProcessor<JobRecord> {

  private static final DirectBuffer DEFAULT_ERROR_MESSAGE = wrapString("No more retries left.");
  private static final int MAX_ERROR_MESSAGE_SIZE = 500;
  private final IncidentRecord incidentEvent = new IncidentRecord();

  private final JobState jobState;
  private final StateWriter stateWriter;
  private final TypedRejectionWriter rejectionWriter;
  private final TypedResponseWriter responseWriter;
  private final KeyGenerator keyGenerator;
  private final JobMetrics jobMetrics;
  private final JobBackoffChecker jobBackoffChecker;
  private final VariableBehavior variableBehavior;
  private final BpmnJobActivationBehavior jobActivationBehavior;
  private final SideEffectWriter sideEffectWriter;
  private final JobCommandPreconditionChecker preconditionChecker;

  public JobFailProcessor(
      final ProcessingState state,
      final Writers writers,
      final KeyGenerator keyGenerator,
      final JobMetrics jobMetrics,
      final JobBackoffChecker jobBackoffChecker,
      final BpmnBehaviors bpmnBehaviors) {
    jobState = state.getJobState();
    stateWriter = writers.state();
    rejectionWriter = writers.rejection();
    responseWriter = writers.response();
    sideEffectWriter = writers.sideEffect();
    variableBehavior = bpmnBehaviors.variableBehavior();
    jobActivationBehavior = bpmnBehaviors.jobActivationBehavior();
    preconditionChecker =
        new JobCommandPreconditionChecker("fail", List.of(State.ACTIVATABLE, State.ACTIVATED));
    this.keyGenerator = keyGenerator;
    this.jobBackoffChecker = jobBackoffChecker;
    this.jobMetrics = jobMetrics;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    preconditionChecker
        .check(state, jobKey)
        .ifRightOrLeft(
            ok -> failJob(record),
            violation -> {
              rejectionWriter.appendRejection(record, violation.getLeft(), violation.getRight());
              responseWriter.writeRejectionOnCommand(
                  record, violation.getLeft(), violation.getRight());
            });
  }

  private void failJob(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobRecord failJobCommandRecord = record.getValue();
    final var retries = failJobCommandRecord.getRetries();
    final var retryBackOff = failJobCommandRecord.getRetryBackoff();

    final JobRecord failedJob = jobState.getJob(jobKey);
    failedJob.setRetries(retries);
    failedJob.setErrorMessage(
        limitString(failJobCommandRecord.getErrorMessage(), MAX_ERROR_MESSAGE_SIZE));
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
    jobMetrics.jobFailed(failedJob.getType());

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
          variables);
    }
  }

  private void raiseIncident(final long key, final JobRecord value) {
    final DirectBuffer jobErrorMessage = value.getErrorMessageBuffer();
    DirectBuffer incidentErrorMessage = DEFAULT_ERROR_MESSAGE;
    if (jobErrorMessage.capacity() > 0) {
      incidentErrorMessage = jobErrorMessage;
    }

    incidentEvent.reset();
    incidentEvent
        .setErrorType(ErrorType.JOB_NO_RETRIES)
        .setErrorMessage(incidentErrorMessage)
        .setBpmnProcessId(value.getBpmnProcessIdBuffer())
        .setProcessDefinitionKey(value.getProcessDefinitionKey())
        .setProcessInstanceKey(value.getProcessInstanceKey())
        .setElementId(value.getElementIdBuffer())
        .setElementInstanceKey(value.getElementInstanceKey())
        .setJobKey(key)
        .setVariableScopeKey(value.getElementInstanceKey());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), IncidentIntent.CREATED, incidentEvent);
  }
}
