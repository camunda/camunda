/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el.impl;

import io.zeebe.el.Expression;
import org.camunda.feel.syntaxtree.ParsedExpression;

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

  public ParsedExpression getParsedExpression() {
    return expression;
  }

  @Override
  public String toString() {
    return "FeelExpression{" + "expression=" + expression + '}';
  }
}
