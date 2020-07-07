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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableEventBasedGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.EventBasedGateway;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.stream.Collectors;

public final class EventBasedGatewayTransformer
    implements ModelElementTransformer<EventBasedGateway> {

  @Override
  public Class<EventBasedGateway> getType() {
    return EventBasedGateway.class;
  }

  @Override
  public void transform(final EventBasedGateway element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableEventBasedGateway gateway =
        workflow.getElementById(element.getId(), ExecutableEventBasedGateway.class);

    final List<ExecutableCatchEvent> connectedEvents = getConnectedCatchEvents(gateway);
    gateway.setEvents(connectedEvents);

    bindLifecycle(gateway);
  }

  private List<ExecutableCatchEvent> getConnectedCatchEvents(
      final ExecutableEventBasedGateway gateway) {
    return gateway.getOutgoing().stream()
        .map(e -> (ExecutableCatchEvent) e.getTarget())
        .collect(Collectors.toList());
  }

  private void bindLifecycle(final ExecutableEventBasedGateway gateway) {
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
  }
}
