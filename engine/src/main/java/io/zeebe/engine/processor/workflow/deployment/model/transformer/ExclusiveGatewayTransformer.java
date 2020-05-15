/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class ExclusiveGatewayTransformer
    implements ModelElementTransformer<ExclusiveGateway> {

  @Override
  public Class<ExclusiveGateway> getType() {
    return ExclusiveGateway.class;
  }

  @Override
  public void transform(final ExclusiveGateway element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableExclusiveGateway gateway =
        workflow.getElementById(element.getId(), ExecutableExclusiveGateway.class);

    transformDefaultFlow(element, workflow, gateway);
    bindLifecycle(gateway);
  }

  private void bindLifecycle(final ExecutableExclusiveGateway gateway) {
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.BPMN_ELEMENT_PROCESSOR);
    gateway.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.BPMN_ELEMENT_PROCESSOR);
  }

  private void transformDefaultFlow(
      final ExclusiveGateway element,
      final ExecutableWorkflow workflow,
      final ExecutableExclusiveGateway gateway) {
    final SequenceFlow defaultFlowElement = element.getDefault();

    if (defaultFlowElement != null) {
      final String defaultFlowId = defaultFlowElement.getId();
      final ExecutableSequenceFlow defaultFlow =
          workflow.getElementById(defaultFlowId, ExecutableSequenceFlow.class);

      gateway.setDefaultFlow(defaultFlow);
    }
  }
}
