/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.deployment.model.transformer;

import io.zeebe.engine.processor.workflow.deployment.model.BpmnStep;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.FlowNode;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.msgpack.mapping.Mapping;
import io.zeebe.msgpack.mapping.MappingBuilder;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.Collection;

public class FlowNodeTransformer implements ModelElementTransformer<FlowNode> {

  private final MappingBuilder mappingBuilder = new MappingBuilder();

  @Override
  public Class<FlowNode> getType() {
    return FlowNode.class;
  }

  @Override
  public void transform(FlowNode flowNode, TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableFlowNode element =
        workflow.getElementById(flowNode.getId(), ExecutableFlowNode.class);

    transformIoMappings(flowNode, element, context);
    bindLifecycle(element);
  }

  private void bindLifecycle(ExecutableFlowNode element) {
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
      FlowNode element, final ExecutableFlowNode flowNode, TransformContext context) {
    final ZeebeIoMapping ioMapping = element.getSingleExtensionElement(ZeebeIoMapping.class);

    if (ioMapping != null) {
      final Collection<ZeebeInput> inputs = ioMapping.getInputs();
      inputs.forEach(i -> mappingBuilder.mapping(i.getSource(), i.getTarget()));

      final Mapping[] inputMappings = mappingBuilder.build();

      final Collection<ZeebeOutput> outputs = ioMapping.getOutputs();
      outputs.forEach(o -> mappingBuilder.mapping(o.getSource(), o.getTarget()));
      final Mapping[] outputMappings = mappingBuilder.build();

      flowNode.setInputMappings(inputMappings);
      flowNode.setOutputMappings(outputMappings);
    }
  }
}
