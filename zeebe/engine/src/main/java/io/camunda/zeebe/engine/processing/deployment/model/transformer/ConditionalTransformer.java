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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableConditional;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeConditionalFilter;
import java.util.Arrays;

public class ConditionalTransformer implements ModelElementTransformer<ConditionalEventDefinition> {

  @Override
  public Class<ConditionalEventDefinition> getType() {
    return ConditionalEventDefinition.class;
  }

  @Override
  public void transform(final ConditionalEventDefinition element, final TransformContext context) {
    final String id = element.getId();
    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();
    final ExecutableConditional executableElement = new ExecutableConditional(id);

    transformConditionExpression(element, expressionLanguage, executableElement);

    final var conditionalFilter = element.getSingleExtensionElement(ZeebeConditionalFilter.class);
    if (conditionalFilter != null) {
      transformVariableNames(conditionalFilter, executableElement);
      transformVariableEvents(conditionalFilter, executableElement);
    }

    context.addConditional(executableElement);
  }

  private static void transformVariableEvents(
      final ZeebeConditionalFilter conditionalFilter,
      final ExecutableConditional executableElement) {
    final var variableEvents = conditionalFilter.getVariableEvents();
    if (variableEvents != null && !variableEvents.isBlank()) {
      final var events = variableEvents.split(",");
      executableElement.setVariableEvents(
          Arrays.stream(events).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
  }

  private static void transformVariableNames(
      final ZeebeConditionalFilter conditionalFilter,
      final ExecutableConditional executableElement) {
    final var variableNames = conditionalFilter.getVariableNames();
    if (variableNames != null && !variableNames.isBlank()) {
      final var names = variableNames.split(",");
      executableElement.setVariableNames(
          Arrays.stream(names).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }
  }

  private static void transformConditionExpression(
      final ConditionalEventDefinition element,
      final ExpressionLanguage expressionLanguage,
      final ExecutableConditional executableElement) {
    if (element.getCondition() != null) {
      final Expression condition =
          expressionLanguage.parseExpression(element.getCondition().getTextContent());

      if (condition.isStatic()) {
        // already validated in RuntimeValidator, so this is just a safeguard
        throw new IllegalStateException(
            String.format(
                "The condition expression must be non-static. Found static expression '%s'.",
                condition.getExpression()));
      }

      executableElement.setConditionExpression(condition);
      // set raw condition expression string to be stored in the conditional subscription record
      executableElement.setCondition(element.getCondition().getTextContent());
    }
  }
}
