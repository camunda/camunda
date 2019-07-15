/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.el;

import java.util.Set;
import org.agrona.DirectBuffer;
import scala.collection.JavaConverters;

public final class CompiledJsonCondition {
  private final String expression;
  private final JsonCondition condition;
  private final boolean isValid;
  private final String errorMessage;

  private CompiledJsonCondition(
      String expression, JsonCondition condition, boolean isValid, String errorMessage) {
    this.expression = expression;
    this.condition = condition;
    this.isValid = isValid;
    this.errorMessage = errorMessage;
  }

  public static CompiledJsonCondition success(String expression, JsonCondition condition) {
    return new CompiledJsonCondition(expression, condition, true, null);
  }

  public static CompiledJsonCondition fail(String expression, String errorMessage) {
    return new CompiledJsonCondition(expression, null, false, errorMessage);
  }

  public String getExpression() {
    return expression;
  }

  public JsonCondition getCondition() {
    return condition;
  }

  public boolean isValid() {
    return isValid;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public Set<DirectBuffer> getVariableNames() {
    return JavaConverters.setAsJavaSet(condition.variableNames());
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    builder.append("CompiledJsonCondition [expression=");
    builder.append(expression);
    builder.append(", errorMessage=");
    builder.append(errorMessage);
    builder.append("]");
    return builder.toString();
  }
}
