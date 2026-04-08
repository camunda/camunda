/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.EngineMetricsDoc.JobAction;
import io.camunda.zeebe.engine.metrics.JobProcessingMetrics;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedRejectionWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.JobState.State;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

public final class JobCompleteProcessor implements TypedRecordProcessor<JobRecord> {

  public static final String NO_JOB_FOUND_MESSAGE =
      "Expected to complete job with key '%d', but no such job was found";

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final JobCommandPreconditionChecker preconditionChecker;
  private final JobProcessingMetrics jobMetrics;
  private final EventHandle eventHandle;

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedRejectionWriter rejectionWriter;

  public JobCompleteProcessor(
      final ProcessingState state,
      final Writers writers,
      final JobProcessingMetrics jobMetrics,
      final EventHandle eventHandle) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    commandWriter = writers.command();
    stateWriter = writers.state();
    responseWriter = writers.response();
    rejectionWriter = writers.rejection();
    preconditionChecker =
        new JobCommandPreconditionChecker("complete", List.of(State.ACTIVATABLE, State.ACTIVATED));
    this.jobMetrics = jobMetrics;
    this.eventHandle = eventHandle;
  }

  @Override
  public void processRecord(final TypedRecord<JobRecord> record) {
    final long jobKey = record.getKey();
    final JobState.State state = jobState.getState(jobKey);

    preconditionChecker
        .check(state, jobKey)
        .ifRightOrLeft(
            ignored -> completeJob(record),
            rejection -> {
              rejectionWriter.appendRejection(record, rejection.getLeft(), rejection.getRight());
              responseWriter.writeRejectionOnCommand(
                  record, rejection.getLeft(), rejection.getRight());
            });
  }

  private void completeJob(final TypedRecord<JobRecord> command) {
    final var jobKey = command.getKey();
    final var job = jobState.getJob(jobKey, command.getAuthorizations());
    if (job == null) {
      final String errorMessage = String.format(NO_JOB_FOUND_MESSAGE, jobKey);
      rejectionWriter.appendRejection(command, RejectionType.NOT_FOUND, errorMessage);
      responseWriter.writeRejectionOnCommand(command, RejectionType.NOT_FOUND, errorMessage);
      return;
    }

    job.setVariables(command.getValue().getVariablesBuffer());

    stateWriter.appendFollowUpEvent(command.getKey(), JobIntent.COMPLETED, job);
    responseWriter.writeEventOnCommand(command.getKey(), JobIntent.COMPLETED, job, command);

    jobMetrics.countJobEvent(JobAction.COMPLETED, job.getJobKind(), job.getType());

    postCompleteActions(job);
  }

  private void postCompleteActions(final JobRecord value) {

    final var serviceTaskKey = value.getElementInstanceKey();

    final ElementInstance serviceTask = elementInstanceState.getInstance(serviceTaskKey);

    if (serviceTask != null) {
      if (value.getJobKind() == JobKind.EXECUTION_LISTENER) {
        // to store the variable for merge, to handle concurrent commands
        eventHandle.triggeringProcessEvent(value);

        commandWriter.appendFollowUpCommand(
            serviceTaskKey,
            ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER,
            serviceTask.getValue());
        return;
      }

      final long scopeKey = serviceTask.getValue().getFlowScopeKey();
      final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

      if (scopeInstance != null && scopeInstance.isActive()) {
        eventHandle.triggeringProcessEvent(value);
        commandWriter.appendFollowUpCommand(
            serviceTaskKey, ProcessInstanceIntent.COMPLETE_ELEMENT, serviceTask.getValue());
      }
    }
  }
}
