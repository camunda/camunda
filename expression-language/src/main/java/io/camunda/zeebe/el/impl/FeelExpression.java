/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.el.impl;

import io.zeebe.el.Expression;
import java.util.Optional;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.syntaxtree.PathExpression;
import org.camunda.feel.syntaxtree.Ref;

public final class FeelExpression implements Expression {

  private final ParsedExpression expression;

  public FeelExpression(final ParsedExpression expression) {
    this.expression = expression;
  }

  @Override
  public String getExpression() {
    return expression.text();
  }

  @Override
  public Optional<String> getVariableName() {
    return extractVariableName(expression.expression());
  }

  @Override
  public boolean isStatic() {
    return false;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  private static Optional<String> extractVariableName(final Exp expression) {
    if (expression instanceof PathExpression) {
      final var path = (PathExpression) expression;
      return extractVariableName(path.path());
    }
    if (expression instanceof Ref) {
      final var ref = (Ref) expression;
      return Optional.of(ref.names().head());
    }
    return Optional.empty();
  }

  public ParsedExpression getParsedExpression() {
    return expression;
  }

  @Override
  public String toString() {
    return "FeelExpression{" + "expression=" + expression + '}';
  }
}
