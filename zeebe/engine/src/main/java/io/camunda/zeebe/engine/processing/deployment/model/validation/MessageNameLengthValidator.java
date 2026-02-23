/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.ResultType;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.util.Either;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class MessageNameLengthValidator implements ModelElementValidator<Message> {

  private final ExpressionLanguage expressionLanguage;
  private final int maxNameFieldLength;

  public MessageNameLengthValidator(
      final ExpressionLanguage expressionLanguage, final int maxNameFieldLength) {
    this.expressionLanguage = expressionLanguage;
    this.maxNameFieldLength = maxNameFieldLength;
  }

  @Override
  public Class<Message> getElementType() {
    return Message.class;
  }

  @Override
  public void validate(
      final Message element, final ValidationResultCollector validationResultCollector) {
    final String nameExpression = element.getName();
    if (nameExpression == null) {
      return;
    }

    final var expression = expressionLanguage.parseExpression(nameExpression);
    if (!expression.isStatic()) {
      return;
    }

    final EvaluationResult evaluationResult =
        expressionLanguage.evaluateExpression(expression, var -> Either.left(null));
    if (evaluationResult.isFailure() || evaluationResult.getType() != ResultType.STRING) {
      return;
    }

    final String evaluatedMessageName = evaluationResult.getString();
    if (evaluatedMessageName.length() > maxNameFieldLength) {
      validationResultCollector.addError(
          0,
          "Message names must not be longer than the configured max-name-length of "
              + maxNameFieldLength
              + " characters.");
    }
  }
}
