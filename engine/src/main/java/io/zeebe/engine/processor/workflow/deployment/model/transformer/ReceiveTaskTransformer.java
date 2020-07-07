/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableReceiveTask;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class ReceiveTaskTransformer implements ModelElementTransformer<ReceiveTask> {

  @Override
  public Class<ReceiveTask> getType() {
    return ReceiveTask.class;
  }

  @Override
  public void transform(final ReceiveTask element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableReceiveTask executableElement =
        workflow.getElementById(element.getId(), ExecutableReceiveTask.class);

    final Message message = element.getMessage();

    final ExecutableMessage executableMessage = context.getMessage(message.getId());
    executableElement.setMessage(executableMessage);

    bindLifecycle(executableElement);
  }

  private void bindLifecycle(final ExecutableReceiveTask executableElement) {
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    executableElement.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
  }
}
