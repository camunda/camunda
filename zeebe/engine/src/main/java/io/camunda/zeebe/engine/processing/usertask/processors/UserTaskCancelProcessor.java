/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask.processors;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnUserTaskBehavior;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

@ExcludeAuthorizationCheck
public class UserTaskCancelProcessor implements UserTaskCommandProcessor {

  private final ElementInstanceState elementInstanceState;
  private final TypedCommandWriter commandWriter;
  private final BpmnUserTaskBehavior userTaskBehavior;

  public UserTaskCancelProcessor(
      final ProcessingState state, final Writers writers, final BpmnBehaviors bpmnBehaviors) {
    elementInstanceState = state.getElementInstanceState();
    commandWriter = writers.command();
    userTaskBehavior = bpmnBehaviors.userTaskBehavior();
  }

  @Override
  public Either<Rejection, UserTaskRecord> validateCommand(
      final TypedRecord<UserTaskRecord> command) {
    return UserTaskCommandProcessor.super.validateCommand(command);
  }

  @Override
  public void onCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    // nothing to do, currently we have no cases when this method should be triggered
  }

  @Override
  public void onFinalizeCommand(
      final TypedRecord<UserTaskRecord> command, final UserTaskRecord userTaskRecord) {
    userTaskBehavior.userTaskCanceled(command.getKey());

    final var userTaskInstanceKey = userTaskRecord.getElementInstanceKey();
    final var userTaskInstance = elementInstanceState.getInstance(userTaskInstanceKey);
    commandWriter.appendFollowUpCommand(
        userTaskInstanceKey,
        ProcessInstanceIntent.CONTINUE_TERMINATING_ELEMENT,
        userTaskInstance.getValue());
  }
}
