/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedExpressionEvaluationStrictContract(
    String expression, Object result, java.util.List<String> warnings) {

  public GeneratedExpressionEvaluationStrictContract {
    Objects.requireNonNull(expression, "expression is required and must not be null");
    Objects.requireNonNull(result, "result is required and must not be null");
    Objects.requireNonNull(warnings, "warnings is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ExpressionStep builder() {
    return new Builder();
  }

  public static final class Builder
      implements ExpressionStep, ResultStep, WarningsStep, OptionalStep {
    private String expression;
    private ContractPolicy.FieldPolicy<String> expressionPolicy;
    private Object result;
    private ContractPolicy.FieldPolicy<Object> resultPolicy;
    private java.util.List<String> warnings;
    private ContractPolicy.FieldPolicy<java.util.List<String>> warningsPolicy;

    private Builder() {}

    @Override
    public ResultStep expression(
        final String expression, final ContractPolicy.FieldPolicy<String> policy) {
      this.expression = expression;
      this.expressionPolicy = policy;
      return this;
    }

    @Override
    public WarningsStep result(
        final Object result, final ContractPolicy.FieldPolicy<Object> policy) {
      this.result = result;
      this.resultPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep warnings(
        final java.util.List<String> warnings,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy) {
      this.warnings = warnings;
      this.warningsPolicy = policy;
      return this;
    }

    @Override
    public GeneratedExpressionEvaluationStrictContract build() {
      return new GeneratedExpressionEvaluationStrictContract(
          applyRequiredPolicy(this.expression, this.expressionPolicy, Fields.EXPRESSION),
          applyRequiredPolicy(this.result, this.resultPolicy, Fields.RESULT),
          applyRequiredPolicy(this.warnings, this.warningsPolicy, Fields.WARNINGS));
    }
  }

  public interface ExpressionStep {
    ResultStep expression(final String expression, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ResultStep {
    WarningsStep result(final Object result, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface WarningsStep {
    OptionalStep warnings(
        final java.util.List<String> warnings,
        final ContractPolicy.FieldPolicy<java.util.List<String>> policy);
  }

  public interface OptionalStep {
    GeneratedExpressionEvaluationStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EXPRESSION =
        ContractPolicy.field("ExpressionEvaluationResult", "expression");
    public static final ContractPolicy.FieldRef RESULT =
        ContractPolicy.field("ExpressionEvaluationResult", "result");
    public static final ContractPolicy.FieldRef WARNINGS =
        ContractPolicy.field("ExpressionEvaluationResult", "warnings");

    private Fields() {}
  }
}
