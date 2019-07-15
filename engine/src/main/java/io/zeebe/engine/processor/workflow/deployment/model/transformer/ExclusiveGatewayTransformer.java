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
import java.util.Collection;

public class ExclusiveGatewayTransformer implements ModelElementTransformer<ExclusiveGateway> {

  @Override
  public Class<ExclusiveGateway> getType() {
    return ExclusiveGateway.class;
  }

  @Override
  public void transform(ExclusiveGateway element, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableExclusiveGateway gateway =
        workflow.getElementById(element.getId(), ExecutableExclusiveGateway.class);

    transformDefaultFlow(element, workflow, gateway);
    bindLifecycle(gateway);
  }

  private void bindLifecycle(final ExecutableExclusiveGateway gateway) {
    final Collection<ExecutableSequenceFlow> outgoingFlows = gateway.getOutgoing();
    final boolean hasNoOutgoingFlows = outgoingFlows.size() == 0;
    final boolean hasSingleNonConditionalOutgoingFlow =
        outgoingFlows.size() == 1 && outgoingFlows.iterator().next().getCondition() == null;

    if (hasNoOutgoingFlows || hasSingleNonConditionalOutgoingFlow) {
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.FLOWOUT_ELEMENT_COMPLETED);
    } else {
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.EXCLUSIVE_GATEWAY_ELEMENT_ACTIVATING);
      gateway.bindLifecycleState(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.EXCLUSIVE_GATEWAY_ELEMENT_COMPLETED);
    }
  }

  private void transformDefaultFlow(
      ExclusiveGateway element,
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
