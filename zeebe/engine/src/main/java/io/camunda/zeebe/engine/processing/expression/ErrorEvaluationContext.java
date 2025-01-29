/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.engine.state.immutable.EventScopeInstanceState;

public class ErrorEvaluationContext implements ScopedEvaluationContext {

  private long scopeKey;
  private final EventScopeInstanceState eventScopeInstanceState;

  public ErrorEvaluationContext(final EventScopeInstanceState eventScopeInstanceState) {
    this.eventScopeInstanceState = eventScopeInstanceState;
  }

  @Override
  public Object getVariable(final String variableName) {
    return switch (variableName) {
      case "code" ->
          eventScopeInstanceState.getTriggeringCatchEvent(scopeKey).getRecord().getErrorCode();
      case "message" ->
          eventScopeInstanceState.getTriggeringCatchEvent(scopeKey).getRecord().getErrorMessage();
      default -> throw new IllegalArgumentException("Mustafa did this");
    };
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    this.scopeKey = scopeKey;
    return this;
  }
}
