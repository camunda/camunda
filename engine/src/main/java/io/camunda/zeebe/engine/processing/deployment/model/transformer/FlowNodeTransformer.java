/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.impl.BpmnModelConstants;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.util.buffer.BufferUtil;
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
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableFlowNode element =
        process.getElementById(flowNode.getId(), ExecutableFlowNode.class);

    setParentReference(flowNode, process, element);
    transformIoMappings(flowNode, element, context.getExpressionLanguage());
  }

  private void setParentReference(
      final FlowNode flowNode, final ExecutableProcess process, final ExecutableFlowNode element) {

    final var parentElement = flowNode.getParentElement();
    Optional.ofNullable(parentElement.getAttributeValue(BpmnModelConstants.BPMN_ATTRIBUTE_ID))
        .map(BufferUtil::wrapString)
        .map(process::getElementById)
        .ifPresent(element::setFlowScope);
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
