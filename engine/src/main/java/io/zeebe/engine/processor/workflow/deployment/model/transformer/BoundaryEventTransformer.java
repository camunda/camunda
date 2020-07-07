/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableBoundaryEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.Activity;
import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class BoundaryEventTransformer implements ModelElementTransformer<BoundaryEvent> {
  @Override
  public Class<BoundaryEvent> getType() {
    return BoundaryEvent.class;
  }

  @Override
  public void transform(final BoundaryEvent event, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableBoundaryEvent element =
        workflow.getElementById(event.getId(), ExecutableBoundaryEvent.class);

    element.setInterrupting(event.cancelActivity());

    attachToActivity(event, workflow, element);

    bindLifecycle(element);
  }

  private void attachToActivity(
      final BoundaryEvent event,
      final ExecutableWorkflow workflow,
      final ExecutableBoundaryEvent element) {
    final Activity attachedToActivity = event.getAttachedTo();
    final ExecutableActivity attachedToElement =
        workflow.getElementById(attachedToActivity.getId(), ExecutableActivity.class);

    attachedToElement.attach(element);
  }

  private void bindLifecycle(final ExecutableBoundaryEvent element) {
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    element.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
  }
}
