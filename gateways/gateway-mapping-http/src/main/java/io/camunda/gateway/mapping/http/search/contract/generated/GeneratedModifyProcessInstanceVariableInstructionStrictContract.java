/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/process-instances.yaml#/components/schemas/ModifyProcessInstanceVariableInstruction
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedModifyProcessInstanceVariableInstructionStrictContract(
    @JsonProperty("variables") java.util.Map<String, Object> variables,
    @JsonProperty("scopeId") @Nullable String scopeId) {

  public GeneratedModifyProcessInstanceVariableInstructionStrictContract {
    Objects.requireNonNull(variables, "No variables provided.");
    if (scopeId == null) scopeId = "";
  }

  public static VariablesStep builder() {
    return new Builder();
  }

  public static final class Builder implements VariablesStep, OptionalStep {
    private java.util.Map<String, Object> variables;
    private String scopeId;

    private Builder() {}

    @Override
    public OptionalStep variables(final java.util.Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep scopeId(final @Nullable String scopeId) {
      this.scopeId = scopeId;
      return this;
    }

    @Override
    public OptionalStep scopeId(
        final @Nullable String scopeId, final ContractPolicy.FieldPolicy<String> policy) {
      this.scopeId = policy.apply(scopeId, Fields.SCOPE_ID, null);
      return this;
    }

    @Override
    public GeneratedModifyProcessInstanceVariableInstructionStrictContract build() {
      return new GeneratedModifyProcessInstanceVariableInstructionStrictContract(
          this.variables, this.scopeId);
    }
  }

  public interface VariablesStep {
    OptionalStep variables(final java.util.Map<String, Object> variables);
  }

  public interface OptionalStep {
    OptionalStep scopeId(final @Nullable String scopeId);

    OptionalStep scopeId(
        final @Nullable String scopeId, final ContractPolicy.FieldPolicy<String> policy);

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
