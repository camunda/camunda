/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class NamespacedContext implements ScopedEvaluationContext {

  private final Map<String, ScopedEvaluationContext> namespaces = new HashMap<>();

  public static NamespacedContext create() {
    return new NamespacedContext();
  }

  public NamespacedContext register(final String namespace, final ScopedEvaluationContext context) {
    namespaces.put(namespace, context);
    return this;
  }

  @Override
  public ScopedEvaluationContext getVariable(final String variableName) {
    return namespaces.get(variableName);
  }

  @Override
  public Stream<String> getVariables() {
    return namespaces.keySet().stream();
  }

  @Override
  public ScopedEvaluationContext scoped(final long scopeKey) {
    final var context = new NamespacedContext();
    for (final var entry : namespaces.entrySet()) {
      context.register(entry.getKey(), entry.getValue().scoped(scopeKey));
    }
    return context;
  }
}
