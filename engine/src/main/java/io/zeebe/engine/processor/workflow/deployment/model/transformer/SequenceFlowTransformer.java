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
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processor.workflow.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.msgpack.el.CompiledJsonCondition;
import io.zeebe.msgpack.el.JsonConditionFactory;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

public class SequenceFlowTransformer implements ModelElementTransformer<SequenceFlow> {
  @Override
  public Class<SequenceFlow> getType() {
    return SequenceFlow.class;
  }

  @Override
  public void transform(final SequenceFlow element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableSequenceFlow sequenceFlow =
        workflow.getElementById(element.getId(), ExecutableSequenceFlow.class);

    compileCondition(element, sequenceFlow);
    connectWithFlowNodes(element, workflow, sequenceFlow);
    bindLifecycle(sequenceFlow);
  }

  private void bindLifecycle(final ExecutableSequenceFlow sequenceFlow) {
    final ExecutableFlowNode target = sequenceFlow.getTarget();
    if (target.getElementType() == BpmnElementType.PARALLEL_GATEWAY) {
      sequenceFlow.bindLifecycleState(
          WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, BpmnStep.PARALLEL_MERGE_SEQUENCE_FLOW_TAKEN);
    } else {
      sequenceFlow.bindLifecycleState(
          WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN, BpmnStep.SEQUENCE_FLOW_TAKEN);
    }
  }

  private void connectWithFlowNodes(
      final SequenceFlow element,
      final ExecutableWorkflow workflow,
      final ExecutableSequenceFlow sequenceFlow) {
    final ExecutableFlowNode source =
        workflow.getElementById(element.getSource().getId(), ExecutableFlowNode.class);
    final ExecutableFlowNode target =
        workflow.getElementById(element.getTarget().getId(), ExecutableFlowNode.class);

    source.addOutgoing(sequenceFlow);
    target.addIncoming(sequenceFlow);
    sequenceFlow.setTarget(target);
    sequenceFlow.setSource(source);
  }

  private void compileCondition(
      final SequenceFlow element, final ExecutableSequenceFlow sequenceFlow) {
    final ConditionExpression conditionExpression = element.getConditionExpression();
    if (conditionExpression != null) {
      final String rawExpression = conditionExpression.getTextContent();
      final CompiledJsonCondition compiledExpression =
          JsonConditionFactory.createCondition(rawExpression);
      sequenceFlow.setCondition(compiledExpression);
    }
  }
}
