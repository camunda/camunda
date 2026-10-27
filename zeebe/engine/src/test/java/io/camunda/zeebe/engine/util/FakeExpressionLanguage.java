/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.el.impl.EvaluationFailure;
import io.camunda.zeebe.el.impl.StaticExpression;

/**
 * Simple fake expression language for usage in tests.
 *
 * <p>Feel free to extend this class to support more complex interactions.
 */
public final class FakeExpressionLanguage implements ExpressionLanguage {

  @Override
  public Expression parseExpression(final String expression) {
    return new StaticExpression(expression);
  }

  @Override
  public EvaluationResult evaluateExpression(
      final Expression expression, final EvaluationContext context) {
    return new EvaluationFailure(expression, "fake expression language");
  }
}
