/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ClusterVariableEvaluationContext implements ScopedEvaluationContext {

  private final VariableState variableState;

  public ClusterVariableEvaluationContext(final VariableState variableState) {
    this.variableState = variableState;
  }

  @Override
  public Object getVariable(final String variableName) {
    final var value = variableState.getVariable(-1, BufferUtil.wrapString(variableName));

    return value != null && value.capacity() > 0 ? value : null;
  }
}
