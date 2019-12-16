/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;

public final class SubProcessTransformer implements ModelElementTransformer<SubProcess> {

  @Override
  public Class<SubProcess> getType() {
    return SubProcess.class;
  }

  @Override
  public void transform(final SubProcess element, final TransformContext context) {
    final ExecutableWorkflow currentWorkflow = context.getCurrentWorkflow();
    final ExecutableFlowElementContainer subprocess =
        currentWorkflow.getElementById(element.getId(), ExecutableFlowElementContainer.class);

    if (element.triggeredByEvent()) {
      transformEventSubprocess(element, currentWorkflow, subprocess);
    }

    subprocess.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.CONTAINER_ELEMENT_ACTIVATED);
    subprocess.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
    subprocess.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.CONTAINER_ELEMENT_TERMINATING);
  }

  private void transformEventSubprocess(
      final SubProcess element,
      final ExecutableWorkflow currentWorkflow,
      final ExecutableFlowElementContainer subprocess) {
    final List<ExecutableCatchEvent> parentEvents;

    if (element.getScope() instanceof FlowNode) {
      final FlowNode scope = (FlowNode) element.getScope();
      final ExecutableFlowElementContainer parentSubProc =
          currentWorkflow.getElementById(scope.getId(), ExecutableFlowElementContainer.class);

      parentEvents = parentSubProc.getEvents();
    } else {
      // top-level start event
      parentEvents = currentWorkflow.getEvents();
    }

    final ExecutableStartEvent startEvent = subprocess.getStartEvents().iterator().next();

    parentEvents.add(startEvent);
    startEvent.setEventSubProcess(subprocess.getId());
    startEvent.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.EVENT_SUBPROC_EVENT_OCCURRED);
  }
}
