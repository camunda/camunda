/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import io.camunda.zeebe.engine.metrics.JobMetrics;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.EventHandle;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.JobKind;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class JobCompleteProcessor implements CommandProcessor<JobRecord> {

  public static final String TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE =
      "Task Listener job completion with variables payload provided is not yet supported (job key '%d', type '%s', processInstanceKey '%d'). "
          + "Support will be enabled with the resolution of issue #23702";
  private static final Set<String> CORRECTABLE_PROPERTIES =
      Set.of(
          UserTaskRecord.ASSIGNEE,
          UserTaskRecord.CANDIDATE_GROUPS,
          UserTaskRecord.CANDIDATE_USERS,
          UserTaskRecord.DUE_DATE,
          UserTaskRecord.FOLLOW_UP_DATE,
          UserTaskRecord.PRIORITY);

  private final UserTaskState userTaskState;
  private final ElementInstanceState elementInstanceState;
  private final DefaultJobCommandPreconditionGuard defaultProcessor;
  private final JobMetrics jobMetrics;
  private final EventHandle eventHandle;

  public JobCompleteProcessor(
      final ProcessingState state,
      final JobMetrics jobMetrics,
      final EventHandle eventHandle,
      final AuthorizationCheckBehavior authCheckBehavior) {
    userTaskState = state.getUserTaskState();
    elementInstanceState = state.getElementInstanceState();
    defaultProcessor =
        new DefaultJobCommandPreconditionGuard(
            "complete",
            state.getJobState(),
            this::acceptCommand,
            authCheckBehavior,
            List.of(
                this::checkTaskListenerJobForProvidingVariables,
                this::checkTaskListenerJobForDenyingWithCorrections,
                this::checkTaskListenerJobForUnknownPropertyCorrections));
    this.jobMetrics = jobMetrics;
    this.eventHandle = eventHandle;
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
      final long key,
      final Intent intent,
      final JobRecord value) {

    final var elementInstanceKey = value.getElementInstanceKey();

    final var elementInstance = elementInstanceState.getInstance(elementInstanceKey);

    if (elementInstance == null) {
      return;
    }

    switch (value.getJobKind()) {
      case EXECUTION_LISTENER -> {
        // to store the variable for merge, to handle concurrent commands
        eventHandle.triggeringProcessEvent(value);

        commandWriter.appendFollowUpCommand(
            elementInstanceKey,
            ProcessInstanceIntent.COMPLETE_EXECUTION_LISTENER,
            elementInstance.getValue());
      }
      case TASK_LISTENER -> {
        /*
         We retrieve the intermediate user task state rather than the regular user task record
         because the intermediate state captures the exact data provided during the original
         user task command (e.g., COMPLETE, ASSIGN). This data includes variables, actions,
         and other command-related details that may not yet be reflected in the persisted user
         task record, that can be accessed via `userTaskState.getUserTask`.

         When task listeners are involved, it's essential to preserve this original state
         until all task listeners have been executed. Retrieving the intermediate state here
         ensures that the finalization of the user task command uses the correct, unmodified
         data as originally intended by the user. Once all task listeners have been processed
         and the original user task command is finalized, the intermediate state is cleared.
        */
        final var userTask =
            userTaskState.getIntermediateState(elementInstance.getUserTaskKey()).getRecord();

        // passing denied reason for both DENY_TASK_LISTENER and COMPLETE_TASK_LISTENER cases as
        // we would want to keep track of the current denied reason and it should be empty in case
        // of successful completion of task listener
        userTask.setDeniedReason(value.getResult().getDeniedReason());

        if (value.getResult().isDenied()) {
          commandWriter.appendFollowUpCommand(
              userTask.getUserTaskKey(), UserTaskIntent.DENY_TASK_LISTENER, userTask);
        } else {
          userTask.correctAttributes(
              value.getResult().getCorrectedAttributes(), value.getResult().getCorrections());
          commandWriter.appendFollowUpCommand(
              userTask.getUserTaskKey(), UserTaskIntent.COMPLETE_TASK_LISTENER, userTask);
        }
      }
      default -> {
        final long scopeKey = elementInstance.getValue().getFlowScopeKey();
        final ElementInstance scopeInstance = elementInstanceState.getInstance(scopeKey);

        if (scopeInstance != null && scopeInstance.isActive()) {
          eventHandle.triggeringProcessEvent(value);
          commandWriter.appendFollowUpCommand(
              elementInstanceKey,
              ProcessInstanceIntent.COMPLETE_ELEMENT,
              elementInstance.getValue());
        }
      }
    }
  }

  /** We currently don't support completing task listener jobs with variables. */
  private Either<Rejection, JobRecord> checkTaskListenerJobForProvidingVariables(
      final TypedRecord<JobRecord> command, final JobRecord job) {

    if (job.getJobKind() == JobKind.TASK_LISTENER && hasVariables(command)) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              TL_JOB_COMPLETION_WITH_VARS_NOT_SUPPORTED_MESSAGE.formatted(
                  command.getKey(), job.getType(), job.getProcessInstanceKey())));
    }

    return Either.right(job);
  }

  private boolean hasVariables(final TypedRecord<JobRecord> command) {
    return !DocumentValue.EMPTY_DOCUMENT.equals(command.getValue().getVariablesBuffer());
  }

  private Either<Rejection, JobRecord> checkTaskListenerJobForDenyingWithCorrections(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    if (job.getJobKind() != JobKind.TASK_LISTENER) {
      return Either.right(job);
    }

    final var jobResult = command.getValue().getResult();
    if (jobResult.isDenied() && !jobResult.getCorrectedAttributes().isEmpty()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              """
              Expected to complete task listener job with corrections, but the job result is \
              denied. The corrections would be reverted by the denial. Either complete the job with \
              corrections without setting denied, or complete the job with a denied result but no \
              corrections."""));
    } else {
      return Either.right(job);
    }
  }

  private Either<Rejection, JobRecord> checkTaskListenerJobForUnknownPropertyCorrections(
      final TypedRecord<JobRecord> command, final JobRecord job) {
    if (job.getJobKind() != JobKind.TASK_LISTENER) {
      return Either.right(job);
    }

    final var optionalUnknownProperty =
        command.getValue().getResult().getCorrectedAttributes().stream()
            .filter(Predicate.not(CORRECTABLE_PROPERTIES::contains))
            .findAny();

    if (optionalUnknownProperty.isEmpty()) {
      return Either.right(job);
    }

    final var correctableProperties =
        CORRECTABLE_PROPERTIES.stream().sorted().collect(Collectors.joining(", ", "[", "]"));
    return Either.left(
        new Rejection(
            RejectionType.INVALID_ARGUMENT,
            """
            Expected to complete task listener job with a corrections result, \
            but property '%s' cannot be corrected. \
            Only the following properties can be corrected: %s."""
                .formatted(optionalUnknownProperty.get(), correctableProperties)));
  }

  private void acceptCommand(
      final TypedRecord<JobRecord> command,
      final CommandControl<JobRecord> commandControl,
      final JobRecord job) {
    job.setVariables(command.getValue().getVariablesBuffer());
    job.setResult(command.getValue().getResult());

    commandControl.accept(JobIntent.COMPLETED, job);
    jobMetrics.jobCompleted(job.getType(), job.getJobKind());
  }
}
