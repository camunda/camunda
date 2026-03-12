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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionEvaluationByIdStrictContract(
    String decisionDefinitionId,
    @Nullable java.util.Map<String, Object> variables,
    @Nullable String tenantId) {

  public GeneratedDecisionEvaluationByIdStrictContract {
    Objects.requireNonNull(
        decisionDefinitionId, "decisionDefinitionId is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static DecisionDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements DecisionDefinitionIdStep, OptionalStep {
    private String decisionDefinitionId;
    private ContractPolicy.FieldPolicy<String> decisionDefinitionIdPolicy;
    private java.util.Map<String, Object> variables;
    private String tenantId;

    private Builder() {}

    @Override
    public OptionalStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy) {
      this.decisionDefinitionId = decisionDefinitionId;
      this.decisionDefinitionIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep variables(final java.util.Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep tenantId(final String tenantId) {
      this.tenantId = tenantId;
      return this;
    }

    @Override
    public OptionalStep tenantId(
        final String tenantId, final ContractPolicy.FieldPolicy<String> policy) {
      this.tenantId = policy.apply(tenantId, Fields.TENANT_ID, null);
      return this;
    }

    @Override
    public GeneratedDecisionEvaluationByIdStrictContract build() {
      return new GeneratedDecisionEvaluationByIdStrictContract(
          applyRequiredPolicy(
              this.decisionDefinitionId,
              this.decisionDefinitionIdPolicy,
              Fields.DECISION_DEFINITION_ID),
          this.variables,
          this.tenantId);
    }
  }

  public interface DecisionDefinitionIdStep {
    OptionalStep decisionDefinitionId(
        final String decisionDefinitionId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep variables(final java.util.Map<String, Object> variables);

    OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep tenantId(final String tenantId);

    OptionalStep tenantId(final String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedDecisionEvaluationByIdStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_ID =
        ContractPolicy.field("DecisionEvaluationById", "decisionDefinitionId");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("DecisionEvaluationById", "variables");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionEvaluationById", "tenantId");

    private Fields() {}
  }
}
