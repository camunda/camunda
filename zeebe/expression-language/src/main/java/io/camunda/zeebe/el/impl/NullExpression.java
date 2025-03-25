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
import io.camunda.zeebe.msgpack.spec.MsgPackHelper;
import java.time.Duration;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/** This class handles a null expression */
public final class NullExpression implements Expression, EvaluationResult {

  private static final UnsafeBuffer NIL_VALUE = new UnsafeBuffer(MsgPackHelper.NIL);
  private final ResultType resultType;

  public NullExpression() {
    resultType = ResultType.NULL;
  }

  @Override
  public String getExpression() {
    return "null";
  }

  @Override
  public Optional<String> getVariableName() {
    return Optional.empty();
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
  public List<EvaluationWarning> getWarnings() {
    return Collections.emptyList();
  }

  @Override
  public ResultType getType() {
    return resultType;
  }

  @Override
  public DirectBuffer toBuffer() {
    return NIL_VALUE;
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
