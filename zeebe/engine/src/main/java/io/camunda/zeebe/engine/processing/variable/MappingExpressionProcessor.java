/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.variable;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.util.Either;
import org.jspecify.annotations.NullMarked;

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
   * Returns an evaluation context scoped to {@link MappingContext#mergeTargetScopeKey()} instead of
   * the element instance: the scope an output mapping's result is merged into, as opposed to the
   * scope its source expressions are evaluated against.
   *
   * <p>Used by {@code OrderedOutputMappingResolver} only, to seed a nested output target's merge
   * from whatever the merge-target scope already holds, not from the completing element's own,
   * about-to-be-discarded scope — see <a
   * href="https://github.com/camunda/camunda/issues/35251">#35251</a>. No other resolver calls this
   * method.
   */
  public ScopedEvaluationContext getMergeTargetEvaluationContext() {
    return processor
        .getEvaluationContext()
        .processScoped(mappingContext.mergeTargetScopeKey())
        .tenantScoped(tenantId);
  }

  /**
   * Evaluates the given expression against this processor's pre-scoped context.
   *
   * <p>The result is returned un-serialized so that a later mapping reading it (via a resolver's
   * own {@link #prependContext}) sees it as the FEEL value it is, not as MessagePack.
   *
   * @param source the expression to evaluate
   * @return either the evaluation result, or a failure
   */
  public Either<Failure, EvaluationResult> evaluateVariableMappingExpression(
      final Expression source) {
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
}
