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
public record GeneratedModifyProcessInstanceVariableInstructionStrictContract(
    java.util.Map<String, Object> variables, @Nullable String scopeId) {

  public GeneratedModifyProcessInstanceVariableInstructionStrictContract {
    Objects.requireNonNull(variables, "variables is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static VariablesStep builder() {
    return new Builder();
  }

  public static final class Builder implements VariablesStep, OptionalStep {
    private java.util.Map<String, Object> variables;
    private ContractPolicy.FieldPolicy<java.util.Map<String, Object>> variablesPolicy;
    private String scopeId;

    private Builder() {}

    @Override
    public OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = variables;
      this.variablesPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep scopeId(final String scopeId) {
      this.scopeId = scopeId;
      return this;
    }

    @Override
    public OptionalStep scopeId(
        final String scopeId, final ContractPolicy.FieldPolicy<String> policy) {
      this.scopeId = policy.apply(scopeId, Fields.SCOPE_ID, null);
      return this;
    }

    @Override
    public GeneratedModifyProcessInstanceVariableInstructionStrictContract build() {
      return new GeneratedModifyProcessInstanceVariableInstructionStrictContract(
          applyRequiredPolicy(this.variables, this.variablesPolicy, Fields.VARIABLES),
          this.scopeId);
    }
  }

  public interface VariablesStep {
    OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);
  }

  public interface OptionalStep {
    OptionalStep scopeId(final String scopeId);

    OptionalStep scopeId(final String scopeId, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedModifyProcessInstanceVariableInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("ModifyProcessInstanceVariableInstruction", "variables");
    public static final ContractPolicy.FieldRef SCOPE_ID =
        ContractPolicy.field("ModifyProcessInstanceVariableInstruction", "scopeId");

    private Fields() {}
  }
}
