/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;

public interface ScopedEvaluationContext extends EvaluationContext {

  default ScopedEvaluationContext scoped(final long scopeKey) {
    return this;
  }

  static ScopedEvaluationContext none() {
    return new ScopedEvaluationContext() {
      @Override
      public Object getVariable(final String variableName) {
        return null;
      }
    };
  }
}
