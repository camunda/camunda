/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.validator;

import static io.camunda.zeebe.gateway.rest.validator.ErrorMessages.ERROR_MESSAGE_EMPTY_ATTRIBUTE;
import static io.camunda.zeebe.gateway.rest.validator.RequestValidator.validate;

import java.util.List;
import java.util.Optional;
import org.springframework.http.ProblemDetail;

public class ExpressionValidator {

  public static Optional<ProblemDetail> validateExpressionEvaluationRequest(
      final String expression, final String tenantId, final boolean isMultiTenancyEnabled) {
    return validate(
        violations -> {
          validateExpression(expression, violations);
          validateTenantId(tenantId, isMultiTenancyEnabled, violations);
        });
  }

  private static void validateExpression(final String expression, final List<String> violations) {
    if (expression == null || expression.isBlank()) {
      violations.add(ERROR_MESSAGE_EMPTY_ATTRIBUTE.formatted("expression"));
    }
  }

  private static void validateTenantId(
      final String tenantId, final boolean isMultiTenancyEnabled, final List<String> violations) {
    MultiTenancyValidator.validateTenantId(tenantId, isMultiTenancyEnabled, "Expression Evaluation")
        .ifLeft(problemDetail -> violations.add(problemDetail.getDetail()));
  }
}
