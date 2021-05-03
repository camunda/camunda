/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el;

/**
 * A parser and interpreter for the expression language. An expression can be parsed and stored as
 * object. The parsed expression needs to be used to evaluate the expression with a given variable
 * context.
 *
 * <p>If the parsing or evaluation fails then it returns the result object that contains the failure
 * message, instead of throwing an exception.
 */
public interface ExpressionLanguage {

  /**
   * Parse the given string into an expression.
   *
   * @param expression the (raw) expression as string
   * @return the parsed expression, or the failure message if the expression is not valid
   */
  Expression parseExpression(String expression);

  /**
   * Evaluate the given expression with the given context variables.
   *
   * @param expression the parsed expression
   * @param context the context to evaluate the expression with
   * @return the result of the evaluation, or the failure message if the evaluation was not
   *     successful
   */
  EvaluationResult evaluateExpression(Expression expression, EvaluationContext context);
}
