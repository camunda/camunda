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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.agrona.DirectBuffer;

public final class CombinedEvaluationContext implements ScopedEvaluationContext {
  private final List<ScopedEvaluationContext> contexts = new ArrayList<>();

  private CombinedEvaluationContext(final ScopedEvaluationContext... contexts) {
    Collections.addAll(this.contexts, contexts);
  }

  public static CombinedEvaluationContext withContexts(final ScopedEvaluationContext... contexts) {
    return new CombinedEvaluationContext(contexts);
  }

  @Override
  public ScopedEvaluationContext processScoped(final long scopeKey) {
    final var scopedContexts =
        contexts.stream()
            .map(context -> context.processScoped(scopeKey))
            .toArray(ScopedEvaluationContext[]::new);

    return CombinedEvaluationContext.withContexts(scopedContexts);
  }

  @Override
  public ScopedEvaluationContext tenantScoped(final String tenantId) {
    final var scopedContexts =
        contexts.stream()
            .map(context -> context.tenantScoped(tenantId))
            .toArray(ScopedEvaluationContext[]::new);

    return CombinedEvaluationContext.withContexts(scopedContexts);
  }

  @Override
  public Either<DirectBuffer, EvaluationContext> getVariable(final String variableName) {
    return contexts.stream()
        .map(context -> context.getVariable(variableName))
        .filter(Objects::nonNull)
        .filter(this::filterEmptyScopeAndValue)
        .findFirst()
        .orElse(Either.left(null));
  }

  private boolean filterEmptyScopeAndValue(
      final Either<DirectBuffer, EvaluationContext> directBufferEvaluationContextEither) {
    if (directBufferEvaluationContextEither.isLeft()) {
      final var buffer = directBufferEvaluationContextEither.getLeft();
      return buffer != null;
    } else if (directBufferEvaluationContextEither.isRight()) {
      final var value = directBufferEvaluationContextEither.get();
      return value != null;
    }
    throw new IllegalStateException("Either is neither left nor right");
  }
}
