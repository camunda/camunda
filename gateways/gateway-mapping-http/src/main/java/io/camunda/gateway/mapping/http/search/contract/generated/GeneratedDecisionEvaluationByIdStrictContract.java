/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/decision-definitions.yaml#/components/schemas/DecisionEvaluationById
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionEvaluationByIdStrictContract(
    @JsonProperty("decisionDefinitionId") String decisionDefinitionId,
    @JsonProperty("variables") java.util.@Nullable Map<String, Object> variables,
    @JsonProperty("tenantId") @Nullable String tenantId)
    implements GeneratedDecisionEvaluationInstructionStrictContract {

  public GeneratedDecisionEvaluationByIdStrictContract {
    Objects.requireNonNull(decisionDefinitionId, "No decisionDefinitionId provided.");
    if (decisionDefinitionId.isBlank())
      throw new IllegalArgumentException("decisionDefinitionId must not be blank");
    if (decisionDefinitionId.length() > 256)
      throw new IllegalArgumentException(
          "The provided decisionDefinitionId exceeds the limit of 256 characters.");
    if (!decisionDefinitionId.matches("^[A-Za-z0-9_@.+-]+$"))
      throw new IllegalArgumentException(
          "The provided decisionDefinitionId contains illegal characters. It must match the pattern '^[A-Za-z0-9_@.+-]+$'.");
    if (tenantId != null)
      if (tenantId.isBlank()) throw new IllegalArgumentException("tenantId must not be blank");
    if (tenantId != null)
      if (tenantId.length() > 256)
        throw new IllegalArgumentException(
            "The provided tenantId exceeds the limit of 256 characters.");
    if (tenantId != null)
      if (!tenantId.matches("^(<default>|[A-Za-z0-9_@.+-]+)$"))
        throw new IllegalArgumentException(
            "The provided tenantId contains illegal characters. It must match the pattern '^(<default>|[A-Za-z0-9_@.+-]+)$'.");
  }

  public static DecisionDefinitionIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements DecisionDefinitionIdStep, OptionalStep {
    private String decisionDefinitionId;
    private java.util.Map<String, Object> variables;
    private String tenantId;

    private Builder() {}

    @Override
    public OptionalStep decisionDefinitionId(final String decisionDefinitionId) {
      this.decisionDefinitionId = decisionDefinitionId;
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
    public GeneratedDecisionEvaluationByIdStrictContract build() {
      return new GeneratedDecisionEvaluationByIdStrictContract(
          this.decisionDefinitionId, this.variables, this.tenantId);
    }
  }

  public interface DecisionDefinitionIdStep {
    OptionalStep decisionDefinitionId(final String decisionDefinitionId);
  }

  public interface OptionalStep {
    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

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
