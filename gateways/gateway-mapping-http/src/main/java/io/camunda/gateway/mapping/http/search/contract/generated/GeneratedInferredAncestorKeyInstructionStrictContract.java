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

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedInferredAncestorKeyInstructionStrictContract(String ancestorScopeType) {

  public GeneratedInferredAncestorKeyInstructionStrictContract {
    Objects.requireNonNull(ancestorScopeType, "ancestorScopeType is required and must not be null");
  }

  public static AncestorScopeTypeStep builder() {
    return new Builder();
  }

  public static final class Builder implements AncestorScopeTypeStep, OptionalStep {
    private String ancestorScopeType;

    private Builder() {}

    @Override
    public OptionalStep ancestorScopeType(final String ancestorScopeType) {
      this.ancestorScopeType = ancestorScopeType;
      return this;
    }

    @Override
    public GeneratedInferredAncestorKeyInstructionStrictContract build() {
      return new GeneratedInferredAncestorKeyInstructionStrictContract(this.ancestorScopeType);
    }
  }

  public interface AncestorScopeTypeStep {
    OptionalStep ancestorScopeType(final String ancestorScopeType);
  }

  public interface OptionalStep {
    GeneratedInferredAncestorKeyInstructionStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ANCESTOR_SCOPE_TYPE =
        ContractPolicy.field("InferredAncestorKeyInstruction", "ancestorScopeType");

    private Fields() {}
  }
}
