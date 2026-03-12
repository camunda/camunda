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
public record GeneratedUseSourceParentKeyInstructionStrictContract(String ancestorScopeType) {

  public GeneratedUseSourceParentKeyInstructionStrictContract {
    Objects.requireNonNull(ancestorScopeType, "ancestorScopeType is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static AncestorScopeTypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements AncestorScopeTypeStep, OptionalStep {
    private String ancestorScopeType;
    private ContractPolicy.FieldPolicy<String> ancestorScopeTypePolicy;

    private Builder() {}

    @Override
    public OptionalStep ancestorScopeType(
        final String ancestorScopeType, final ContractPolicy.FieldPolicy<String> policy) {
      this.ancestorScopeType = ancestorScopeType;
      this.ancestorScopeTypePolicy = policy;
      return this;
    }

    @Override
    public GeneratedUseSourceParentKeyInstructionStrictContract build() {
      return new GeneratedUseSourceParentKeyInstructionStrictContract(
          applyRequiredPolicy(
              this.ancestorScopeType, this.ancestorScopeTypePolicy, Fields.ANCESTOR_SCOPE_TYPE));
    }
  }

  public interface AncestorScopeTypeStep {
    OptionalStep ancestorScopeType(
        final String ancestorScopeType, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedUseSourceParentKeyInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ANCESTOR_SCOPE_TYPE =
        ContractPolicy.field("UseSourceParentKeyInstruction", "ancestorScopeType");

    private Fields() {}
  }
}
