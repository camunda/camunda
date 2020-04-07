/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.Optional;

public final class FlowNodeTransformer implements ModelElementTransformer<FlowNode> {

  private final VariableMappingTransformer variableMappingTransformer =
      new VariableMappingTransformer();

  @Override
  public Class<FlowNode> getType() {
    return FlowNode.class;
  }

  @Override
  public void transform(final FlowNode flowNode, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableFlowNode element =
        workflow.getElementById(flowNode.getId(), ExecutableFlowNode.class);

    transformIoMappings(flowNode, element, context.getExpressionLanguage());
    bindLifecycle(element);
  }

  private void bindLifecycle(final ExecutableFlowNode element) {
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATING, BpmnStep.ELEMENT_ACTIVATING);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_ACTIVATED, BpmnStep.ELEMENT_ACTIVATED);
    element.bindLifecycleState(WorkflowInstanceIntent.EVENT_OCCURRED, BpmnStep.EVENT_OCCURRED);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETING, BpmnStep.ELEMENT_COMPLETING);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_COMPLETED, BpmnStep.ELEMENT_COMPLETED);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATING, BpmnStep.ELEMENT_TERMINATING);
    element.bindLifecycleState(
        WorkflowInstanceIntent.ELEMENT_TERMINATED, BpmnStep.ELEMENT_TERMINATED);
  }

  private void transformIoMappings(
      final FlowNode element,
      final ExecutableFlowNode flowNode,
      final ExpressionLanguage expressionLanguage) {

    final var ioMapping =
        Optional.ofNullable(element.getSingleExtensionElement(ZeebeIoMapping.class));

    ioMapping
        .map(ZeebeIoMapping::getInputs)
        .filter(mappings -> !mappings.isEmpty())
        .map(
            mappings ->
                variableMappingTransformer.transformInputMappings(mappings, expressionLanguage))
        .ifPresent(flowNode::setInputMappings);

    ioMapping
        .map(ZeebeIoMapping::getOutputs)
        .filter(mappings -> !mappings.isEmpty())
        .map(
            mappings ->
                variableMappingTransformer.transformOutputMappings(mappings, expressionLanguage))
        .ifPresent(flowNode::setOutputMappings);
  }
}
