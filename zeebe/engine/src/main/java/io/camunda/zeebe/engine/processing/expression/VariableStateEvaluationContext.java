/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.engine.state.immutable.VariableState.Variable;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.stream.Stream;

public class VariableStateEvaluationContext implements ScopedEvaluationContext {

  private final VariableState variableState;
  private long scopeKey;

  public VariableStateEvaluationContext(final VariableState variableState) {
    this.variableState = variableState;
  }

  public VariableStateEvaluationContext(final VariableState variableState, final long scopeKey) {
    this.variableState = variableState;
    this.scopeKey = scopeKey;
  }

  @Override
  public Object getVariable(final String variableName) {
    if (scopeKey < 0) {
      return null;
    }

    final var value = variableState.getVariable(scopeKey, BufferUtil.wrapString(variableName));

    return value != null && value.capacity() > 0 ? value : null;
  }

  @Override
  public Stream<String> getVariables() {
    return variableState.getVariablesLocal(scopeKey).stream()
        .map(Variable::name)
        .map(BufferUtil::bufferAsString);
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    return new VariableStateEvaluationContext(variableState, scopeKey);
  }
}
