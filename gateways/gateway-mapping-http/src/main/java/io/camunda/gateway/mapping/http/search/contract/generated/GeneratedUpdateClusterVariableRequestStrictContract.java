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
public record GeneratedUpdateClusterVariableRequestStrictContract(
    java.util.Map<String, Object> value) {

  public GeneratedUpdateClusterVariableRequestStrictContract {
    Objects.requireNonNull(value, "value is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ValueStep builder() {
    return new Builder();
  }

  public static final class Builder implements ValueStep, OptionalStep {
    private java.util.Map<String, Object> value;
    private ContractPolicy.FieldPolicy<java.util.Map<String, Object>> valuePolicy;

    private Builder() {}

    @Override
    public OptionalStep value(
        final java.util.Map<String, Object> value,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.value = value;
      this.valuePolicy = policy;
      return this;
    }

    @Override
    public GeneratedUpdateClusterVariableRequestStrictContract build() {
      return new GeneratedUpdateClusterVariableRequestStrictContract(
          applyRequiredPolicy(this.value, this.valuePolicy, Fields.VALUE));
    }
  }

  public interface ValueStep {
    OptionalStep value(
        final java.util.Map<String, Object> value,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);
  }

  public interface OptionalStep {
    GeneratedUpdateClusterVariableRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("UpdateClusterVariableRequest", "value");

    private Fields() {}
  }
}
