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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedExpressionEvaluationRequestStrictContract(
    String expression,
    @Nullable String tenantId,
    java.util.@Nullable Map<String, Object> variables) {

  public GeneratedExpressionEvaluationRequestStrictContract {
    Objects.requireNonNull(expression, "expression is required and must not be null");
  }

  public static ExpressionStep builder() {
    return new Builder();
  }

  public static final class Builder implements ExpressionStep, OptionalStep {
    private String expression;
    private String tenantId;
    private java.util.Map<String, Object> variables;

    private Builder() {}

    @Override
    public OptionalStep expression(final String expression) {
      this.expression = expression;
      return this;
    }

    @Override
    public OptionalStep tenantId(final @Nullable String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public OptionalStep variables(final java.util.@Nullable Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public GeneratedExpressionEvaluationRequestStrictContract build() {
      return new GeneratedExpressionEvaluationRequestStrictContract(
          this.expression, this.tenantId, this.variables);
    }
  }

  public interface ExpressionStep {
    OptionalStep expression(final String expression);
  }

  public interface OptionalStep {
    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    GeneratedExpressionEvaluationRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef EXPRESSION =
        ContractPolicy.field("ExpressionEvaluationRequest", "expression");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ExpressionEvaluationRequest", "tenantId");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("ExpressionEvaluationRequest", "variables");

    private Fields() {}
  }
}
