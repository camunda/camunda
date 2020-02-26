/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.el.impl;

import io.zeebe.el.EvaluationResult;
import io.zeebe.el.Expression;
import io.zeebe.el.ResultType;
import java.util.List;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class FeelEvaluationResult implements EvaluationResult {

  private final Expression expression;
  private final Object result;

  private final MessagePackConverter messagePackConverter = new MessagePackConverter();

  public FeelEvaluationResult(final Expression expression, final Object result) {
    this.expression = expression;
    this.result = result;
  }

  @Override
  public String getExpression() {
    return expression.getExpression();
  }

  @Override
  public boolean isFailure() {
    return false;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  @Override
  public ResultType getType() {
    if (result == null) {
      return ResultType.NULL;

    } else if (result instanceof String) {
      return ResultType.STRING;

    } else if (result instanceof Boolean) {
      return ResultType.BOOLEAN;

    } else if (result instanceof Number) {
      return ResultType.NUMBER;

    } else if (result instanceof List) {
      return ResultType.ARRAY;

    } else if (result instanceof Map) {
      return ResultType.OBJECT;
    }

    return null;
  }

  @Override
  public DirectBuffer toBuffer() {
    return messagePackConverter.writeMessagePack(result);
  }

  @Override
  public String getString() {
    if (result instanceof String) {
      final var stringVal = (String) result;
      return stringVal;
    }
    return null;
  }

  @Override
  public Boolean getBoolean() {
    if (result instanceof Boolean) {
      final var booleanVal = (Boolean) result;
      return booleanVal;
    }
    return null;
  }

  @Override
  public Number getNumber() {
    if (result instanceof Number) {
      final var numberVal = (Number) result;
      return numberVal;
    }
    return null;
  }
}
