/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import static io.camunda.zeebe.util.EnsureUtil.ensureGreaterThan;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public final class VariableEvaluationContext implements ScopedEvaluationContext {

  private final VariableState variableState;
  private final long scopeKey;

  public VariableEvaluationContext(final VariableState variableState) {
    this.variableState = variableState;
    scopeKey = -1;
  }

  private VariableEvaluationContext(final VariableState variableState, final long scopeKey) {
    this.variableState = variableState;
    this.scopeKey = scopeKey;
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    ensureGreaterThan("variable scope key", scopeKey, 0);
    return new VariableEvaluationContext(variableState, scopeKey);
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    if (scopeKey < 0) {
      return Either.left(null);
    }
    final var value = variableState.getVariable(scopeKey, BufferUtil.wrapString(variableName));
    return value != null && value.capacity() > 0 ? Either.left(value) : Either.left(null);
  }
}
