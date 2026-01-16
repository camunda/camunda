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
import io.camunda.zeebe.protocol.impl.record.value.expression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;
import java.time.Duration;

public class ExpressionBehavior {

  private final ExpressionProcessor clusterExpressionProcessor;

  public ExpressionBehavior(
      final NamespacedEvaluationContext namespaceFullClusterContext,
      final ExpressionLanguage expressionLanguage,
      final Duration expressionEvaluationTimeout) {
    clusterExpressionProcessor =
        new ExpressionProcessor(
            expressionLanguage, namespaceFullClusterContext, expressionEvaluationTimeout);
  }

  public Either<Rejection, ExpressionRecord> resolveExpression(
      final Expression expression, final ExpressionRecord expressionRecord) {
    return evaluate(expression, expressionRecord.getTenantId())
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

  private Either<Rejection, EvaluationResult> evaluate(
      final Expression expression, final String tenantId) {
    return clusterExpressionProcessor
        .evaluateAnyExpression(expression, -1, tenantId)
        .mapLeft(this::mapEvaluationFailure)
        .flatMap(this::rejectIfEvaluationFailed);
  }

  private Rejection mapEvaluationFailure(final Failure failure) {
    return new Rejection(RejectionType.PROCESSING_ERROR, failure.getMessage());
  }

  private ExpressionRecord mapSuccess(
      final EvaluationResult evaluationResult, final ExpressionRecord expressionRecord) {
    return new ExpressionRecord()
        .setTenantId(expressionRecord.getTenantId())
        .setExpression(expressionRecord.getExpression())
        .setWarnings(
            evaluationResult.getWarnings().stream().map(EvaluationWarning::getMessage).toList())
        .setResultValue(evaluationResult.toBuffer());
  }
}
