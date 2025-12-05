/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import io.camunda.zeebe.util.Either;
import org.agrona.DirectBuffer;

public class IsolatedEvaluationContext implements ScopedEvaluationContext {

  private final ScopedEvaluationContext wrappedContext;

  public IsolatedEvaluationContext(final ScopedEvaluationContext wrappedContext) {
    this.wrappedContext = wrappedContext;
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    return wrappedContext.getVariable(variableName);
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    // For NONE scope, we never want process scoping
    // Return self to prevent activation of process variable lookup
    return this;
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    // For NONE scope, we don't want tenant filtering either
    // Return self to prevent access to cluster variables
    return this;
  }
}
