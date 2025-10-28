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

public interface ScopedEvaluationContext extends EvaluationContext {

  ScopedEvaluationContext NONE_INSTANCE = unused -> Either.left(null);

  default ScopedEvaluationContext processScoped(final long scopeKey) {
    return this;
  }

  default ScopedEvaluationContext tenantScoped(final String tenantId) {
    return this;
  }
}
