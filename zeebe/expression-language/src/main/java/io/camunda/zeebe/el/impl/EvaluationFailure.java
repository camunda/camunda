/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.el.impl;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.EvaluationWarning;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ResultType;
import java.time.Duration;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import org.agrona.DirectBuffer;

public final class EvaluationFailure implements EvaluationResult {

  private final Expression expression;
  private final String failureMessage;
  private final List<EvaluationWarning> warnings;

  public EvaluationFailure(final Expression expression, final String failureMessage) {
    this(expression, failureMessage, Collections.emptyList());
  }

  public EvaluationFailure(
      final Expression expression,
      final String failureMessage,
      final List<EvaluationWarning> warnings) {
    this.expression = expression;
    this.failureMessage = failureMessage;
    this.warnings = warnings;
  }

  @Override
  public String getExpression() {
    return expression.getExpression();
  }

  @Override
  public boolean isFailure() {
    return true;
  }

  @Override
  public String getFailureMessage() {
    return failureMessage;
  }

  @Override
  public List<EvaluationWarning> getWarnings() {
    return warnings;
  }

  @Override
  public ResultType getType() {
    return null;
  }

  @Override
  public DirectBuffer toBuffer() {
    return null;
  }

  @Override
  public String getString() {
    return null;
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
  public Duration getDuration() {
    return null;
  }

  @Override
  public Period getPeriod() {
    return null;
  }

  @Override
  public ZonedDateTime getDateTime() {
    return null;
  }

  @Override
  public List<DirectBuffer> getList() {
    return null;
  }

  @Override
  public List<String> getListOfStrings() {
    return null;
  }
}
