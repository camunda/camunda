/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import java.util.Optional;

public class ExpressionValidator {

  private static final String ERROR_MESSAGE_EMPTY_EXPRESSION = "No expression provided";
  private static final String ERROR_MESSAGE_BLANK_EXPRESSION =
      "The expression must not be blank or empty";

  private final ExpressionLanguage expressionLanguage;

  public ExpressionValidator(final ExpressionLanguage expressionLanguage) {
    this.expressionLanguage = expressionLanguage;
  }

  /**
   * Validates that the expression is not null or empty.
   *
   * @param command the command containing the expression record
   * @return Either a rejection if validation fails, or the expression string
   */
  public Either<Rejection, String> ensureNotBlank(final TypedRecord<ExpressionRecord> command) {
    final String expression = command.getValue().getExpression();
    return Optional.ofNullable(expression)
        .filter(expr -> !expr.isBlank())
        .map(Either::<Rejection, String>right)
        .orElseGet(
            () ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        expression == null
                            ? ERROR_MESSAGE_EMPTY_EXPRESSION
                            : ERROR_MESSAGE_BLANK_EXPRESSION)));
  }

  /**
   * Validates that the expression is syntactically valid by parsing it.
   *
   * @param expressionString the expression string to validate
   * @return Either a rejection if validation fails, or the parsed Expression
   */
  public Either<Rejection, Expression> isValid(final String expressionString) {
    final var expression = expressionLanguage.parseExpression(expressionString);
    if (!expression.isValid()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Failed to parse expression: " + expression.getFailureMessage()));
    }
    return Either.right(expression);
  }
}
