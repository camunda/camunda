/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.variable;

import static io.camunda.zeebe.util.EnsureUtil.ensureGreaterThan;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor.EvaluationContextLookup;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.util.buffer.BufferUtil;

public record VariableStateEvaluationContextLookup(VariableState variableState)
    implements EvaluationContextLookup {

  @Override
  public EvaluationContext getContext(final long scopeKey) {
    ensureGreaterThan("variable scope key", scopeKey, 0);

    return (name) -> variableState.getVariable(scopeKey, BufferUtil.wrapString(name));
  }
}
