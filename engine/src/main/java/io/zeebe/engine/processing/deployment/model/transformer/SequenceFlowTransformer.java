/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ConditionExpression;
import io.zeebe.model.bpmn.instance.SequenceFlow;

public final class SequenceFlowTransformer implements ModelElementTransformer<SequenceFlow> {
  @Override
  public Class<SequenceFlow> getType() {
    return SequenceFlow.class;
  }

  @Override
  public void transform(final SequenceFlow element, final TransformContext context) {
    final ExecutableWorkflow workflow = context.getCurrentWorkflow();
    final ExecutableSequenceFlow sequenceFlow =
        workflow.getElementById(element.getId(), ExecutableSequenceFlow.class);

    parseCondition(element, sequenceFlow, context.getExpressionLanguage());
    connectWithFlowNodes(element, workflow, sequenceFlow);
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

  private void parseCondition(
      final SequenceFlow element,
      final ExecutableSequenceFlow sequenceFlow,
      final ExpressionLanguage expressionLanguage) {

    final ConditionExpression conditionExpression = element.getConditionExpression();
    if (conditionExpression != null) {
      final String condition = conditionExpression.getTextContent();
      final Expression parsedCondition = expressionLanguage.parseExpression(condition);
      sequenceFlow.setCondition(parsedCondition);
    }
  }
}
