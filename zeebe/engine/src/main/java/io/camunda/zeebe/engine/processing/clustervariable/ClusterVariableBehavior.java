/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.clustervariable;

import io.camunda.zeebe.el.ExpressionLanguage;
import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.util.Either;

/**
 * Behavior for resolving cluster variable references using FEEL expressions. Provides utilities for
 * evaluating FEEL expressions against cluster variable state in a specific tenant context.
 */
public final class ClusterVariableBehavior {

  private final ExpressionProcessor expressionProcessor;
  private final ExpressionLanguage expressionLanguage;

  public ClusterVariableBehavior(
      final ExpressionProcessor expressionProcessor, final ExpressionLanguage expressionLanguage) {
    this.expressionProcessor = expressionProcessor;
    this.expressionLanguage = expressionLanguage;
  }

  /**
   * Resolves a cluster variable reference (FEEL expression) for a specific tenant.
   *
   * @param reference the FEEL expression to evaluate (e.g., "camunda.vars.tenant.MY_KEY")
   * @param tenantId the tenant ID in which to resolve the variables
   * @return Either a success with the resolved value as a String, or a failure with an error
   *     message
   */
  public Either<Rejection, String> resolveReference(final String reference, final String tenantId) {
    try {
      final var expression = expressionLanguage.parseExpression(reference);
      final var result = expressionProcessor.evaluateStringExpression(expression, -1, tenantId);
      return result
          .mapLeft(
              failure ->
                  new Rejection(
                      RejectionType.PROCESSING_ERROR,
                      String.format(
                          "Failed to evaluate expression '%s': %s",
                          reference, failure.getMessage())))
          .map(resolvedValue -> resolvedValue);
    } catch (final Exception e) {
      final String errorMessage =
          String.format("Error resolving reference '%s': %s", reference, e.getMessage());
      return Either.left(new Rejection(RejectionType.PROCESSING_ERROR, errorMessage));
    }
  }
}
