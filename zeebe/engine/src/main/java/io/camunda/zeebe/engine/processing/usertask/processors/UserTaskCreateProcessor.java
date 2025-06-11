/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.UserTaskState;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeTaskListenerEventType;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.List;

@ExcludeAuthorizationCheck
public class UserTaskCreateProcessor implements UserTaskCommandProcessor {

  private final StateWriter stateWriter;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final UserTaskState userTaskState;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnUserTaskBehavior userTaskBehavior;

  public UserTaskCreateProcessor(
      final ProcessingState state,
      final Writers writers,
      final BpmnUserTaskBehavior userTaskBehavior,
      final BpmnJobBehavior jobBehavior) {
    elementInstanceState = state.getElementInstanceState();
    processState = state.getProcessState();
    userTaskState = state.getUserTaskState();
    stateWriter = writers.state();
    this.userTaskBehavior = userTaskBehavior;
    this.jobBehavior = jobBehavior;
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {

    // Current assumption: there can not be corrections of the assignee if there is an initial
    // assignee.
    final long userTaskKey = command.getKey();
    userTaskState
        .findInitialAssignee(userTaskKey)
        .ifPresentOrElse(
            // if there is initial assignee -> remove the assignee from UT record as we are going
            // to assign user task to initial assignee via assigning event
            initialAssignee -> {
              final var valueWithoutAssignee = userTaskRecord.copy().unsetAssignee();
              stateWriter.appendFollowUpEvent(
                  userTaskKey, UserTaskIntent.CREATED, valueWithoutAssignee);

              // clean up the changed attributes because we have already finished the creation,
              // and are now starting a new transition to assigning
              valueWithoutAssignee.resetChangedAttributes();
              assignUserTask(valueWithoutAssignee, initialAssignee);
            },
            () ->
                // if no initial assignee -> keep the assignee on the UT record in CREATED event
                // it could be a corrected assignee or no assignee at all
                stateWriter.appendFollowUpEvent(
                    userTaskKey, UserTaskIntent.CREATED, userTaskRecord));
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
