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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class NamespacedEvaluationContext implements ScopedEvaluationContext {

  private final Map<String, ScopedEvaluationContext> namespaces = new HashMap<>();

  public static NamespacedEvaluationContext create() {
    return new NamespacedEvaluationContext();
  }

  public NamespacedEvaluationContext register(
      final String namespace, final ScopedEvaluationContext context) {
    namespaces.put(namespace, context);
    return this;
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    return Optional.ofNullable(namespaces.get(variableName))
        .<Either<DirectBuffer, EvaluationContext>>map(Either::right)
        .orElse(Either.left(null));
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    final var context = new NamespacedEvaluationContext();
    for (final var entry : namespaces.entrySet()) {
      context.register(entry.getKey(), entry.getValue().processScoped(scopeKey));
    }
    return context;
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    final var context = new NamespacedEvaluationContext();
    for (final var entry : namespaces.entrySet()) {
      context.register(entry.getKey(), entry.getValue().tenantScoped(tenantId));
    }
    return context;
  }
}
