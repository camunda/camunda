/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableUserTask;

public final class UserTaskProcessor extends JobWorkerTaskSupportingProcessor<ExecutableUserTask> {

  public UserTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    super(bpmnBehaviors, stateTransitionBehavior);
  }

  @Override
  public Class<ExecutableUserTask> getType() {
    return ExecutableUserTask.class;
  }

  @Override
  protected boolean isJobBehavior(
      final ExecutableUserTask element, final BpmnElementContext context) {
    if (element.getUserTaskProperties() != null) {
      return false;
    }
    if (element.getJobWorkerProperties() == null) {
      throw new BpmnProcessingException(
          context, "Expected to process user task, but could not determine processing behavior");
    }
    return true;
  }

  @Override
  protected void onActivateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    // TODO will be added with https://github.com/camunda/zeebe/issues/15275
  }

  @Override
  protected void onCompleteInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    // TODO will be added with increment 3 of https://github.com/camunda/zeebe/issues/14938
  }

  @Override
  protected void onTerminateInternal(
      final ExecutableUserTask element, final BpmnElementContext context) {
    // TODO will be added with increment 2 of https://github.com/camunda/zeebe/issues/14938
  }
}
