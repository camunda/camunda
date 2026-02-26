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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.camunda.feel.syntaxtree.Exp;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.syntaxtree.PathExpression;
import org.camunda.feel.syntaxtree.Ref;
import scala.Product;

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
    final Set<String> names = new LinkedHashSet<>();
    collectVariableNames(expression.expression(), names);
    return new ArrayList<>(names);
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

  /**
   * Recursively collects all top-level variable names from the given FEEL AST node. For {@link
   * PathExpression} nodes (e.g. {@code x.y}), only the root variable name is collected. All other
   * compound expressions are traversed recursively via the Scala {@link Product} interface.
   */
  private static void collectVariableNames(final Exp exp, final Set<String> names) {
    if (exp instanceof PathExpression) {
      // For path expressions like x.y.z, only collect the root variable (x).
      // Exact sub-property tracking is not supported; any change to x will trigger re-evaluation.
      extractVariableName(exp).ifPresent(names::add);
    } else if (exp instanceof Ref) {
      names.add(((Ref) exp).names().head());
    } else {
      // Recursively traverse child nodes via the Scala Product interface implemented by all
      // FEEL AST case classes.
      final scala.collection.Iterator<?> children = ((Product) exp).productIterator();
      while (children.hasNext()) {
        traverseChild(children.next(), names);
      }
    }
  }

  private static void traverseChild(final Object child, final Set<String> names) {
    if (child instanceof Exp) {
      collectVariableNames((Exp) child, names);
    } else if (child instanceof scala.collection.Iterable) {
      // Handle Scala sequences (e.g. List[Exp]) that appear as fields in compound expressions.
      final scala.collection.Iterator<?> iter = ((scala.collection.Iterable<?>) child).iterator();
      while (iter.hasNext()) {
        traverseChild(iter.next(), names);
      }
    }
  }

  public ParsedExpression getParsedExpression() {
    return expression;
  }

  @Override
  public String toString() {
    return "FeelExpression{" + "expression=" + expression + '}';
  }
}
