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
public record GeneratedVariableValueFilterPropertyStrictContract(String name, Object value) {

  public GeneratedVariableValueFilterPropertyStrictContract {
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(value, "value is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder implements NameStep, ValueStep, OptionalStep {
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private Object value;
    private ContractPolicy.FieldPolicy<Object> valuePolicy;

    private Builder() {}

    @Override
    public ValueStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep value(final Object value, final ContractPolicy.FieldPolicy<Object> policy) {
      this.value = value;
      this.valuePolicy = policy;
      return this;
    }

    @Override
    public GeneratedVariableValueFilterPropertyStrictContract build() {
      return new GeneratedVariableValueFilterPropertyStrictContract(
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          applyRequiredPolicy(this.value, this.valuePolicy, Fields.VALUE));
    }
  }

  public interface NameStep {
    ValueStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface ValueStep {
    OptionalStep value(final Object value, final ContractPolicy.FieldPolicy<Object> policy);
  }

  public interface OptionalStep {
    GeneratedVariableValueFilterPropertyStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("VariableValueFilterProperty", "name");
    public static final ContractPolicy.FieldRef VALUE =
        ContractPolicy.field("VariableValueFilterProperty", "value");

    private Fields() {}
  }
}
