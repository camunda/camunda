/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.engine.processing.job.JobCommandPreconditionChecker.NO_JOB_FOUND_MESSAGE;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.streamprocessor.AuthorizableCommandProcessor.Authorizable;
import io.camunda.zeebe.engine.processing.streamprocessor.AuthorizableCommandProcessor.Rejection;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor.CommandControl;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.JobState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

public final class JobCompleteProcessor implements Authorizable<JobRecord, JobRecord> {

  private final JobState jobState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard defaultProcessor;
  private final JobMetrics jobMetrics;
  private final EventHandle eventHandle;

  public JobCompleteProcessor(
      final ProcessingState state, final JobMetrics jobMetrics, final EventHandle eventHandle) {
    jobState = state.getJobState();
    elementInstanceState = state.getElementInstanceState();
    defaultProcessor =
        new DefaultJobCommandPreconditionGuard("complete", jobState, this::acceptCommand);
    this.jobMetrics = jobMetrics;
    this.eventHandle = eventHandle;
  }

  @Override
  public Either<Rejection, AuthorizationRequest<JobRecord>> getAuthorizationRequest(
      final TypedRecord<JobRecord> command, final CommandControl<JobRecord> controller) {
    final var job = jobState.getJob(command.getKey(), command.getAuthorizations());

    if (job == null) {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(NO_JOB_FOUND_MESSAGE, "complete", command.getKey())));
    }

    final var authorizationRequest =
        new AuthorizationRequest<JobRecord>(
                AuthorizationResourceType.PROCESS_DEFINITION, PermissionType.UPDATE)
            .setResource(job)
            .addResourceId(job.getBpmnProcessId());

    return Either.right(authorizationRequest);
  }

  @Override
  public boolean onCommand(
      final TypedRecord<JobRecord> command,
      final CommandControl<JobRecord> commandControl,
      final JobRecord jobFromState) {
    return defaultProcessor.onCommand(command, commandControl, jobFromState);
  }

  @Override
  public void afterAccept(
      final TypedCommandWriter commandWriter,
      final StateWriter stateWriter,
      final long key,
      final Intent intent,
      final JobRecord value) {

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

  private void acceptCommand(
      final TypedRecord<JobRecord> command,
      final CommandControl<JobRecord> commandControl,
      final JobRecord job) {
    job.setVariables(command.getValue().getVariablesBuffer());
    commandControl.accept(JobIntent.COMPLETED, job);
    jobMetrics.jobCompleted(job.getType(), job.getJobKind());
  }
}
