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
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnStreamProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;

@ExcludeAuthorizationCheck
public class UserTaskCancelProcessor implements UserTaskCommandProcessor {

  private BpmnStateBehavior stateBehavior;
  private ProcessState processState;
  private BpmnElementProcessor<ExecutableFlowElement> userTaskProcessor;

  public UserTaskCancelProcessor(
      final BpmnStateBehavior stateBehavior,
      final ProcessState processState,
      final BpmnStreamProcessor bpmnStreamProcessor) {
    this.stateBehavior = stateBehavior;
    this.processState = processState;
    userTaskProcessor = bpmnStreamProcessor.getProcessor(BpmnElementType.USER_TASK);
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
    final var context = buildContext(userTaskRecord.getElementInstanceKey());
    final var element = getUserTaskElement(userTaskRecord);
    userTaskProcessor.finalizeTerminate(element, context);
  }

  private BpmnElementContext buildContext(final long elementInstanceKey) {
    final var elementInstance = stateBehavior.getElementInstance(elementInstanceKey);
    final var context = new BpmnElementContextImpl();
    context.init(elementInstance.getKey(), elementInstance.getValue(), elementInstance.getState());
    return context;
  }

  private ExecutableUserTask getUserTaskElement(final UserTaskRecord userTaskRecord) {
    return processState.getFlowElement(
        userTaskRecord.getProcessDefinitionKey(),
        userTaskRecord.getTenantId(),
        userTaskRecord.getElementIdBuffer(),
        ExecutableUserTask.class);
  }
}
