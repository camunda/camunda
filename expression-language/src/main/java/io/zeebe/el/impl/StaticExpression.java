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
import io.zeebe.util.buffer.BufferUtil;
import java.util.List;
import org.agrona.DirectBuffer;

public final class StaticExpression implements Expression, EvaluationResult {

  private final String expression;
  private final DirectBuffer result;

  public StaticExpression(final String expression) {
    this.expression = expression;
    result = BufferUtil.wrapString(expression);
  }

  @Override
  public String getExpression() {
    return expression;
  }

  @Override
  public boolean isStatic() {
    return true;
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public String getFailureMessage() {
    return null;
  }

  @Override
  public boolean isFailure() {
    return false;
  }

  @Override
  public ResultType getType() {
    return ResultType.STRING;
  }

  @Override
  public DirectBuffer toBuffer() {
    return result;
  }

  @Override
  public String getString() {
    return expression;
  }

  @Override
  public Boolean getBoolean() {
    return null;
  }

  @Override
  public Number getNumber() {
    return null;
  }

  @Override
  public List<DirectBuffer> getList() {
    return null;
  }
}
