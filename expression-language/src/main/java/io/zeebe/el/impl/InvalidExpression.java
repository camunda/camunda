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

public final class InvalidExpression implements Expression {

  private final String expression;
  private final String failureMessage;

  public InvalidExpression(final String expression, final String failureMessage) {
    this.expression = expression;
    this.failureMessage = failureMessage;
  }

  @Override
  public String getExpression() {
    return expression;
  }

  @Override
  public Optional<String> getVariableName() {
    return Optional.empty();
  }

  @Override
  public boolean isStatic() {
    return false;
  }

  @Override
  public boolean isValid() {
    return false;
  }

  @Override
  public String getFailureMessage() {
    return failureMessage;
  }
}
