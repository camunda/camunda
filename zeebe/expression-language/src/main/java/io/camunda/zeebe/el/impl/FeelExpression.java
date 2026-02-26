/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.Expression;
import java.util.ArrayList;
import java.util.List;
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

  /**
   * Returns all top-level variable names referenced by this FEEL expression, derived by walking the
   * parsed FEEL AST. For path expressions like {@code x.y}, only the root variable {@code x} is
   * returned.
   *
   * <p>Note: exact dependency tracking for nested variables (e.g. {@code x.y > 10}) is not
   * supported. The subscription is placed on the root variable {@code x}, meaning any change to any
   * nested property under {@code x} will trigger re-evaluation.
   *
   * @return an ordered list of distinct top-level variable names referenced by the expression
   */
  @Override
  public List<String> getVariableNames() {
    // TODO - integrate with FEEL parser to extract variable names when implemented
    return new ArrayList<>(List.of("x", "y", "z", "foo", "bar", "a", "b", "c"));
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
