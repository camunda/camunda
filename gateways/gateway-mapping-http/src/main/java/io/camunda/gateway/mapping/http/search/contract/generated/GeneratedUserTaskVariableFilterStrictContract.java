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
import org.springframework.lang.Nullable;

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserTaskVariableFilterStrictContract(@Nullable Object name) {

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private Object name;

    private Builder() {}

    @Override
    public OptionalStep name(final Object name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public GeneratedUserTaskVariableFilterStrictContract build() {
      return new GeneratedUserTaskVariableFilterStrictContract(this.name);
    }
  }

  public interface OptionalStep {
    OptionalStep name(final Object name);

    OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedUserTaskVariableFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("UserTaskVariableFilter", "name");

    private Fields() {}
  }
}
