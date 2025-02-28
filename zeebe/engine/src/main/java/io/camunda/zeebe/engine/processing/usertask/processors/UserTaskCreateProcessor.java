/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.identity.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState.LifecycleState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.List;

public final class UserTaskCreateProcessor implements UserTaskCommandProcessor {

  private static final String DEFAULT_ACTION = "create";

  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final UserTaskState userTaskState;
  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final UserTaskCommandPreconditionChecker preconditionChecker;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnUserTaskBehavior userTaskBehavior;

  public UserTaskCreateProcessor(
      final ProcessingState state,
      final Writers writers,
      final AuthorizationCheckBehavior authCheckBehavior,
      final BpmnUserTaskBehavior userTaskBehavior,
      final BpmnJobBehavior jobBehavior) {
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
    userTaskState = state.getUserTaskState();
    stateWriter = writers.state();
    responseWriter = writers.response();
    preconditionChecker =
        new UserTaskCommandPreconditionChecker(
            List.of(LifecycleState.CREATING),
            "create",
            state.getUserTaskState(),
            authCheckBehavior);
    this.userTaskBehavior = userTaskBehavior;
    this.jobBehavior = jobBehavior;
  }

  @Override
  public Either<Rejection, UserTaskRecord> validateCommand(
      final TypedRecord<UserTaskRecord> command) {
    return preconditionChecker.check(command);
  }

  @Override
  public void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    throw new UnsupportedOperationException("Not yet implemented");
    // possibly not needed at all
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    final long userTaskKey = command.getKey();

    final var intermediateAssignee = userTaskState.getIntermediateAssignee(userTaskKey);

    if (command.hasRequestMetadata()) {
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);
      responseWriter.writeEventOnCommand(
          userTaskKey, UserTaskIntent.CREATED, userTaskRecord, command);
    } else {
      final var recordRequestMetadata = userTaskState.findRecordRequestMetadata(userTaskKey);
      stateWriter.appendFollowUpEvent(userTaskKey, UserTaskIntent.CREATED, userTaskRecord);

      recordRequestMetadata.ifPresent(
          metadata ->
              responseWriter.writeResponse(
                  userTaskKey,
                  UserTaskIntent.CREATED,
                  userTaskRecord,
                  ValueType.USER_TASK,
                  metadata.getRequestId(),
                  metadata.getRequestStreamId()));
    }

    if (intermediateAssignee != null) {
      assignUserTask(userTaskRecord, intermediateAssignee);
    }
  }

  private void assignUserTask(final UserTaskRecord userTaskRecord, final String assignee) {
    userTaskBehavior.userTaskAssigning(userTaskRecord, assignee);

    final var element =
        processState.getFlowElement(
            userTaskRecord.getProcessDefinitionKey(),
            userTaskRecord.getTenantId(),
            userTaskRecord.getElementIdBuffer(),
            ExecutableUserTask.class);

    final var elementInstance =
        elementInstanceState.getInstance(userTaskRecord.getElementInstanceKey());

    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());

    element.getTaskListeners(ZeebeTaskListenerEventType.assigning).stream()
        .findFirst()
        .ifPresentOrElse(
            listener ->
                jobBehavior.createNewTaskListenerJob(
                    context, userTaskRecord, listener, List.of(UserTaskRecord.ASSIGNEE)),
            () -> userTaskBehavior.userTaskAssigned(userTaskRecord, assignee));
  }
}
