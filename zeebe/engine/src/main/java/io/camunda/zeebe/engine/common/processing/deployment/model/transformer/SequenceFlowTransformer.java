/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.deployment.model.transformer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.common.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.ConditionExpression;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;

public final class SequenceFlowTransformer implements ModelElementTransformer<SequenceFlow> {
  @Override
  public Class<SequenceFlow> getType() {
    return SequenceFlow.class;
  }

  @Override
  public void transform(final SequenceFlow element, final TransformContext context) {
    final ExecutableProcess process = context.getCurrentProcess();
    final ExecutableSequenceFlow sequenceFlow =
        process.getElementById(element.getId(), ExecutableSequenceFlow.class);

    parseCondition(element, sequenceFlow, context.getExpressionLanguage());
    connectWithFlowNodes(element, process, sequenceFlow);
  }

  private void connectWithFlowNodes(
      final SequenceFlow element,
      final ExecutableProcess process,
      final ExecutableSequenceFlow sequenceFlow) {
    final ExecutableFlowNode source =
        process.getElementById(element.getSource().getId(), ExecutableFlowNode.class);
    final ExecutableFlowNode target =
        process.getElementById(element.getTarget().getId(), ExecutableFlowNode.class);

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
