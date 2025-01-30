/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.transformer;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.camunda.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import java.util.Optional;

public final class MessageTransformer implements ModelElementTransformer<Message> {

  @Override
  public Class<Message> getType() {
    return Message.class;
  }

  @Override
  public void transform(final Message element, final TransformContext context) {

    final String id = element.getId();
    final ExpressionLanguage expressionLanguage = context.getExpressionLanguage();

    final ExecutableMessage executableElement = new ExecutableMessage(id);
    final ExtensionElements extensionElements = element.getExtensionElements();

    if (extensionElements != null) {
      final Optional<ZeebeSubscription> subscription =
          extensionElements
              .getElementsQuery()
              .filterByType(ZeebeSubscription.class)
              .findSingleResult();
      if (subscription.isPresent()) {
        final Expression correlationKeyExpression =
            expressionLanguage.parseExpression(subscription.get().getCorrelationKey());

        executableElement.setCorrelationKeyExpression(correlationKeyExpression);
      }
    }

    if (element.getName() != null) {
      final Expression messageNameExpression =
          expressionLanguage.parseExpression(element.getName());

      executableElement.setMessageNameExpression(messageNameExpression);

      if (messageNameExpression.isStatic()) {
        final EvaluationResult messageNameResult =
            expressionLanguage.evaluateExpression(messageNameExpression, EvaluationContext.empty());

        if (messageNameResult.getType() == ResultType.STRING) {
          final String messageName = messageNameResult.getString();
          executableElement.setMessageName(messageName);
        }
      }

      context.addMessage(executableElement);
    }
  }
}
