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
public record GeneratedJobCompletionRequestStrictContract(
    @Nullable java.util.Map<String, Object> variables, @Nullable Object result) {

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
    private java.util.Map<String, Object> variables;
    private Object result;

    private Builder() {}

    @Override
    public OptionalStep variables(final java.util.Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy) {
      this.variables = policy.apply(variables, Fields.VARIABLES, null);
      return this;
    }

    @Override
    public OptionalStep result(final Object result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep result(
        final Object result, final ContractPolicy.FieldPolicy<Object> policy) {
      this.result = policy.apply(result, Fields.RESULT, null);
      return this;
    }

    @Override
    public GeneratedJobCompletionRequestStrictContract build() {
      return new GeneratedJobCompletionRequestStrictContract(this.variables, this.result);
    }
  }

  public interface OptionalStep {
    OptionalStep variables(final java.util.Map<String, Object> variables);

    OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep result(final Object result);

    OptionalStep result(final Object result, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedJobCompletionRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("JobCompletionRequest", "variables");
    public static final ContractPolicy.FieldRef RESULT =
        ContractPolicy.field("JobCompletionRequest", "result");

    private Fields() {}
  }
}
