/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.expression;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.EvaluationWarning;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.state.immutable.VariableState;
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.Map;

public class ExpressionBehavior {

  private final ExpressionProcessor clusterExpressionProcessor;
  private final VariableState variableState;
  private final ReferencedSecretCollector referencedSecretCollector;

  public ExpressionBehavior(
      final NamespacedEvaluationContext namespaceFullClusterContext,
      final ExpressionLanguage expressionLanguage,
      final Duration expressionEvaluationTimeout,
      final VariableState variableState,
      final ReferencedSecretCollector referencedSecretCollector) {
    this.referencedSecretCollector = referencedSecretCollector;
    // Resolve camunda.secrets.<name> references to their own placeholder string (as the engine
    // does for input mappings), so callers of the expression endpoint — e.g. inbound connectors,
    // which have no job to trigger job-activation resolution — receive the reference text instead
    // of null. Only that path is affected; camunda.vars.* and every other lookup forward unchanged.
    // The collector records references resolved from trusted sources so they can be reported.
    clusterExpressionProcessor =
        new ExpressionProcessor(
                expressionLanguage, namespaceFullClusterContext, expressionEvaluationTimeout)
            .withSecretReferenceContext(referencedSecretCollector);
    this.variableState = variableState;
  }

  /**
   * Evaluates the given expression with the following variable resolution priority (highest first):
   *
   * <ol>
   *   <li>Variables provided in the record body.
   *   <li>Process/element instance variables visible from the record's scope, walked up the scope
   *       tree.
   *   <li>Tenant-scoped cluster variables.
   *   <li>Global cluster variables.
   * </ol>
   */
  public Either<Rejection, ExpressionRecord> resolveExpression(
      final Expression expression, final ExpressionRecord expressionRecord) {
    // A single collector instance is reused across evaluations (stream processing is
    // single-threaded per partition). Only the endpoint's contexts hold a collector — the BPMN
    // path uses collector-free contexts — so a fresh reset here is enough to isolate this
    // evaluation's references.
    referencedSecretCollector.reset();
    final long scopeKey = expressionRecord.getScopeKey();
    final var variables = expressionRecord.getVariables();
    final var bodyContext =
        variables == null
            ? new InMemoryVariableEvaluationContext(Map.of())
            : new InMemoryVariableEvaluationContext(variables);

    ExpressionProcessor processor = clusterExpressionProcessor;
    if (scopeKey >= 0) {
      // Process/element instance variables: walked up from scopeKey by VariableState#getVariable.
      processor = processor.prependContext(new VariableEvaluationContext(variableState));
    }
    // Body-provided variables take precedence over everything else.
    processor = processor.prependContext(bodyContext);

    return processor
        .evaluateAnyExpression(expression, scopeKey, expressionRecord.getTenantId())
        .mapLeft(this::mapEvaluationFailure)
        .flatMap(this::rejectIfEvaluationFailed)
        .map(evaluationResult -> mapSuccess(evaluationResult, expressionRecord));
  }

  private Either<Rejection, EvaluationResult> rejectIfEvaluationFailed(
      final EvaluationResult evaluationResult) {
    if (evaluationResult.isFailure()) {
      return Either.left(
          new Rejection(
              RejectionType.PROCESSING_ERROR,
              "Failed to evaluate expression: " + evaluationResult.getFailureMessage()));
    } else {
      return Either.right(evaluationResult);
    }
  }

  private Rejection mapEvaluationFailure(final Failure failure) {
    return new Rejection(RejectionType.PROCESSING_ERROR, failure.getMessage());
  }

  private ExpressionRecord mapSuccess(
      final EvaluationResult evaluationResult, final ExpressionRecord expressionRecord) {
    final var mapped =
        new ExpressionRecord()
            .setTenantId(expressionRecord.getTenantId())
            .setExpression(expressionRecord.getExpression())
            .setVariables(expressionRecord.getVariablesBuffer())
            .setScopeKey(expressionRecord.getScopeKey())
            .setWarnings(
                evaluationResult.getWarnings().stream().map(EvaluationWarning::getMessage).toList())
            .setResultValue(evaluationResult.toBuffer());
    referencedSecretCollector
        .drain()
        .forEach(
            reference ->
                mapped.addReferencedSecret(reference.storeId(), reference.secretReference()));
    return mapped;
  }
}
