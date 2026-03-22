/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedDecisionEvaluationByKeyStrictContract(
    @JsonProperty("decisionDefinitionKey") String decisionDefinitionKey,
    @JsonProperty("variables") java.util.@Nullable Map<String, Object> variables,
    @JsonProperty("tenantId") @Nullable String tenantId)
    implements GeneratedDecisionEvaluationInstructionStrictContract {

  public GeneratedDecisionEvaluationByKeyStrictContract {
    Objects.requireNonNull(decisionDefinitionKey, "No decisionDefinitionKey provided.");
    if (decisionDefinitionKey.isBlank())
      throw new IllegalArgumentException("decisionDefinitionKey must not be blank");
    if (decisionDefinitionKey.length() > 25)
      throw new IllegalArgumentException(
          "The provided decisionDefinitionKey exceeds the limit of 25 characters.");
    if (!decisionDefinitionKey.matches("^-?[0-9]+$"))
      throw new IllegalArgumentException(
          "The provided decisionDefinitionKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
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

  public static String coerceDecisionDefinitionKey(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String stringValue) {
      return stringValue;
    }
    if (value instanceof Number numberValue) {
      return KeyUtil.keyToString(numberValue.longValue());
    }
    throw new IllegalArgumentException(
        "decisionDefinitionKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static DecisionDefinitionKeyStep builder() {
    return new Builder();
  }

  public static final class Builder implements DecisionDefinitionKeyStep, OptionalStep {
    private Object decisionDefinitionKey;
    private java.util.Map<String, Object> variables;
    private String tenantId;

    private Builder() {}

    @Override
    public OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey) {
      this.decisionDefinitionKey = decisionDefinitionKey;
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
    public GeneratedDecisionEvaluationByKeyStrictContract build() {
      return new GeneratedDecisionEvaluationByKeyStrictContract(
          coerceDecisionDefinitionKey(this.decisionDefinitionKey), this.variables, this.tenantId);
    }
  }

  public interface DecisionDefinitionKeyStep {
    OptionalStep decisionDefinitionKey(final Object decisionDefinitionKey);
  }

  public interface OptionalStep {
    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedDecisionEvaluationByKeyStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DECISION_DEFINITION_KEY =
        ContractPolicy.field("DecisionEvaluationByKey", "decisionDefinitionKey");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("DecisionEvaluationByKey", "variables");
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("DecisionEvaluationByKey", "tenantId");

    private Fields() {}
  }
}
