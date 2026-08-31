/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.util.Either;
import java.util.function.Function;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Bundles an {@link ExpressionProcessor} with a pre-bound scope key and tenant, so {@link
 * MappingResolver} implementations never receive those as separate parameters. The evaluation
 * context is pre-scoped to the element instance on construction -- {@link #getEvaluationContext()}
 * returns that already-scoped view, so resolvers can call {@link
 * ScopedEvaluationContext#getVariable} directly without calling {@code processScoped} themselves.
 */
@NullMarked
public final class MappingExpressionProcessor {

  private final ExpressionProcessor processor;
  private final ScopedEvaluationContext scopedContext;
  private final long scopeKey;
  private final String tenantId;
  private final MappingContext mappingContext;

  public MappingExpressionProcessor(
      final ExpressionProcessor processor, final MappingContext mappingContext) {
    this.processor = processor;
    this.mappingContext = mappingContext;
    this.scopeKey = mappingContext.scopeKey();
    this.tenantId = mappingContext.tenantId();
    this.scopedContext =
        processor.getEvaluationContext().processScoped(scopeKey).tenantScoped(tenantId);
  }

  private MappingExpressionProcessor(
      final ExpressionProcessor processor,
      final MappingContext mappingContext,
      final long scopeKey,
      final String tenantId) {
    this.processor = processor;
    this.mappingContext = mappingContext;
    this.scopeKey = scopeKey;
    this.tenantId = tenantId;
    this.scopedContext =
        processor.getEvaluationContext().processScoped(scopeKey).tenantScoped(tenantId);
  }

  /** Returns the mapping context associated with this activation. */
  public MappingContext getMappingContext() {
    return mappingContext;
  }

  /**
   * Returns the evaluation context already scoped to the element instance and tenant. Callers can
   * call {@link ScopedEvaluationContext#getVariable} directly without further scoping.
   */
  public ScopedEvaluationContext getEvaluationContext() {
    return scopedContext;
  }

  /**
   * Evaluates the given expression against this processor's pre-scoped context.
   *
   * @param source the expression to evaluate
   * @return either the evaluation result as a MsgPack buffer, or a failure
   */
  public Either<Failure, DirectBuffer> evaluateVariableMappingExpression(final Expression source) {
    // delegates scopeKey/tenantId to the underlying processor, which re-scopes its own context;
    // the pre-computed scopedContext field is only for direct getVariable lookups, not evaluation
    return processor.evaluateVariableMappingExpression(source, scopeKey, tenantId);
  }

  /**
   * Returns a new {@link MappingExpressionProcessor} with {@code ctx} prepended to the underlying
   * processor's evaluation context chain, preserving the same scope key and tenant binding.
   *
   * @param ctx the evaluation context to prepend (e.g. an in-flight result accumulator)
   * @return a new scoped processor with {@code ctx} as the outermost context layer
   */
  public MappingExpressionProcessor prependContext(final ScopedEvaluationContext ctx) {
    return new MappingExpressionProcessor(
        processor.prependContext(ctx), mappingContext, scopeKey, tenantId);
  }

  /**
   * Convenience overload: wraps {@code lookup} as a {@link ScopedEvaluationContext} (returning
   * {@code Either.left(lookup.apply(name))} for every name, where a {@code null} result means
   * absent) and prepends it.
   *
   * @param lookup maps a variable name to its value, or {@code null} if absent
   * @return a new scoped processor with the lookup as the outermost context layer
   */
  public MappingExpressionProcessor prependContext(
      final Function<String, @Nullable DirectBuffer> lookup) {
    return prependContext((ScopedEvaluationContext) name -> Either.left(lookup.apply(name)));
  }
}
