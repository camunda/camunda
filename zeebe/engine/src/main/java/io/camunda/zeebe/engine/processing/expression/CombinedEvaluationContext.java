/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class CombinedEvaluationContext implements ScopedEvaluationContext {

  private final List<ScopedEvaluationContext> contexts = new ArrayList<>();

  private CombinedEvaluationContext(final ScopedEvaluationContext... contexts) {
    Collections.addAll(this.contexts, contexts);
  }

  public static CombinedEvaluationContext withContexts(final ScopedEvaluationContext... contexts) {
    return new CombinedEvaluationContext(contexts);
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    final var scopedContexts =
        contexts.stream()
            .map(context -> context.scoped(scopeKey))
            .toArray(ScopedEvaluationContext[]::new);

    return CombinedEvaluationContext.withContexts(scopedContexts);
  }

  @Override
  public Object getVariable(final String variableName) {
    return contexts.stream()
        .map(context -> context.getVariable(variableName))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  @Override
  public Stream<String> getVariables() {
    return contexts.stream().flatMap(EvaluationContext::getVariables);
  }
}
