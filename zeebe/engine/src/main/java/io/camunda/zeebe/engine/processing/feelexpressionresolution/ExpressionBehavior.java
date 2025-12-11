/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.feelexpressionresolution;

import io.camunda.zeebe.el.EvaluationResult;
import io.camunda.zeebe.el.EvaluationWarning;
import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.expression.NamespacedEvaluationContext;
import io.camunda.zeebe.protocol.impl.record.value.feelexpression.ExpressionRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;

public class ExpressionBehavior {

  private final ExpressionProcessor clusterExpressionProcessor;
  private final ExpressionLanguage expressionLanguage;

  public ExpressionBehavior(
      final NamespacedEvaluationContext namespaceFullClusterContext,
      final ExpressionLanguage expressionLanguage) {
    clusterExpressionProcessor =
        new ExpressionProcessor(expressionLanguage, namespaceFullClusterContext);
    this.expressionLanguage = expressionLanguage;
  }

  public Either<Rejection, ExpressionRecord> resolveFeelExpressionClusterAtClusterLevelAndAbove(
      final ExpressionRecord expressionRecord) {
    return parseExpression(expressionRecord)
        .flatMap(
            expression ->
                evaluateAnyClusterLevelExpression(expression, expressionRecord.getTenantId()))
        .map(evaluationResult -> mapSuccess(evaluationResult, expressionRecord));
  }

  private Either<Rejection, EvaluationResult> mapResultFailure(
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

  private Either<Rejection, EvaluationResult> evaluateAnyClusterLevelExpression(
      final Expression expression, final String tenantId) {
    return clusterExpressionProcessor
        .evaluateRawExpression(expression, -1, tenantId)
        .mapLeft(this::mapEvaluationFailure)
        .flatMap(this::mapResultFailure);
  }

  private Either<Rejection, Expression> parseExpression(final ExpressionRecord expressionRecord) {
    final var expression = expressionLanguage.parseExpression(expressionRecord.getExpression());
    if (!expression.isValid()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Failed to parse expression: " + expression.getFailureMessage()));
    }
    return Either.right(expression);
  }

  private Rejection mapEvaluationFailure(final Failure failure) {
    return new Rejection(RejectionType.PROCESSING_ERROR, failure.getMessage());
  }

  private ExpressionRecord mapSuccess(
      final EvaluationResult evaluationResult, final ExpressionRecord expressionRecord) {
    return expressionRecord
        .setWarnings(
            evaluationResult.getWarnings().stream().map(EvaluationWarning::getMessage).toList())
        .setResultValue(evaluationResult.toBuffer());
  }
}
