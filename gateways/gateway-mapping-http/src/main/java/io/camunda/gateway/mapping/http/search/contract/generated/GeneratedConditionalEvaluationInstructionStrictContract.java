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
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import io.camunda.gateway.mapping.http.util.KeyUtil;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedConditionalEvaluationInstructionStrictContract(
    @JsonProperty("tenantId") @Nullable String tenantId,
    @JsonProperty("processDefinitionKey") @Nullable String processDefinitionKey,
    @JsonProperty("variables") java.util.Map<String, Object> variables) {

  public GeneratedConditionalEvaluationInstructionStrictContract {
    Objects.requireNonNull(variables, "No variables provided.");
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
    if (processDefinitionKey != null)
      if (processDefinitionKey.isBlank())
        throw new IllegalArgumentException("processDefinitionKey must not be blank");
    if (processDefinitionKey != null)
      if (processDefinitionKey.length() > 25)
        throw new IllegalArgumentException(
            "The provided processDefinitionKey exceeds the limit of 25 characters.");
    if (processDefinitionKey != null)
      if (!processDefinitionKey.matches("^-?[0-9]+$"))
        throw new IllegalArgumentException(
            "The provided processDefinitionKey contains illegal characters. It must match the pattern '^-?[0-9]+$'.");
  }

  public static String coerceProcessDefinitionKey(final Object value) {
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
        "processDefinitionKey must be a String or Number, but was " + value.getClass().getName());
  }

  public static VariablesStep builder() {
    return new Builder();
  }

  public static final class Builder implements VariablesStep, OptionalStep {
    private String tenantId;
    private Object processDefinitionKey;
    private java.util.Map<String, Object> variables;

    private Builder() {}

    @Override
    public OptionalStep variables(final java.util.Map<String, Object> variables) {
      this.variables = variables;
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
    public OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey) {
      this.processDefinitionKey = processDefinitionKey;
      return this;
    }

    public Builder processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy) {
      this.processDefinitionKey =
          policy.apply(processDefinitionKey, Fields.PROCESS_DEFINITION_KEY, null);
      return this;
    }

    @Override
    public GeneratedConditionalEvaluationInstructionStrictContract build() {
      return new GeneratedConditionalEvaluationInstructionStrictContract(
          this.tenantId, coerceProcessDefinitionKey(this.processDefinitionKey), this.variables);
    }
  }

  public interface VariablesStep {
    OptionalStep variables(final java.util.Map<String, Object> variables);
  }

  public interface OptionalStep {
    OptionalStep tenantId(final @Nullable String tenantId);

    OptionalStep tenantId(
        final @Nullable String tenantId, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(final @Nullable String processDefinitionKey);

    OptionalStep processDefinitionKey(final @Nullable Object processDefinitionKey);

    OptionalStep processDefinitionKey(
        final @Nullable String processDefinitionKey,
        final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep processDefinitionKey(
        final @Nullable Object processDefinitionKey,
        final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedConditionalEvaluationInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TENANT_ID =
        ContractPolicy.field("ConditionalEvaluationInstruction", "tenantId");
    public static final ContractPolicy.FieldRef PROCESS_DEFINITION_KEY =
        ContractPolicy.field("ConditionalEvaluationInstruction", "processDefinitionKey");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("ConditionalEvaluationInstruction", "variables");

    private Fields() {}
  }
}
