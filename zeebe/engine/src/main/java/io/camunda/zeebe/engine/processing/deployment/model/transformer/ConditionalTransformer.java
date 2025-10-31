/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCondition;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;

public class ConditionalTransformer implements ModelElementTransformer<ConditionalEventDefinition> {

  @Override
  public Class<ConditionalEventDefinition> getType() {
    return ConditionalEventDefinition.class;
  }

  @Override
  public void transform(final ConditionalEventDefinition element, final TransformContext context) {
    final String id = element.getId();
    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();

    final ExecutableCondition executableElement = new ExecutableCondition(id);

    if (element.getCondition() != null) {
      final Expression condition =
          expressionLanguage.parseExpression(element.getCondition().getTextContent());

      executableElement.setConditionExpression(condition);

      context.addCondition(executableElement);
    }
  }
}
