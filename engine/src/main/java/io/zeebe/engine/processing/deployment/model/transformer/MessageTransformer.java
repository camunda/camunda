/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.deployment.model.transformer;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ExpressionLanguage;
import io.zeebe.el.ResultType;
import io.zeebe.engine.processing.deployment.model.element.ExecutableMessage;
import io.zeebe.engine.processing.deployment.model.transformation.ModelElementTransformer;
import io.zeebe.engine.processing.deployment.model.transformation.TransformContext;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;

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
      final ZeebeSubscription subscription =
          extensionElements.getElementsQuery().filterByType(ZeebeSubscription.class).singleResult();
      final Expression correlationKeyExpression =
          expressionLanguage.parseExpression(subscription.getCorrelationKey());

      executableElement.setCorrelationKeyExpression(correlationKeyExpression);
    }

    if (element.getName() != null) {
      final Expression messageNameExpression =
          expressionLanguage.parseExpression(element.getName());

      executableElement.setMessageNameExpression(messageNameExpression);

      if (messageNameExpression.isStatic()) {
        final EvaluationResult messageNameResult =
            expressionLanguage.evaluateExpression(messageNameExpression, variable -> null);

        if (messageNameResult.getType() == ResultType.STRING) {
          final String messageName = messageNameResult.getString();
          executableElement.setMessageName(messageName);
        }
      }

      context.addMessage(executableElement);
    }
  }
}
