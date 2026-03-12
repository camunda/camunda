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
public record GeneratedSetVariableRequestStrictContract(
    java.util.Map<String, Object> variables,
    @Nullable Boolean local,
    @Nullable Long operationReference) {

  public GeneratedSetVariableRequestStrictContract {
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
    private Boolean local;
    private Long operationReference;

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
    public OptionalStep local(final Boolean local) {
      this.local = local;
      return this;
    }

    @Override
    public OptionalStep local(
        final Boolean local, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.local = policy.apply(local, Fields.LOCAL, null);
      return this;
    }

    @Override
    public OptionalStep operationReference(final Long operationReference) {
      this.operationReference = operationReference;
      return this;
    }

    @Override
    public OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy) {
      this.operationReference = policy.apply(operationReference, Fields.OPERATION_REFERENCE, null);
      return this;
    }

    @Override
    public GeneratedSetVariableRequestStrictContract build() {
      return new GeneratedSetVariableRequestStrictContract(
          applyRequiredPolicy(this.variables, this.variablesPolicy, Fields.VARIABLES),
          this.local,
          this.operationReference);
    }
  }

  public interface VariablesStep {
    OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);
  }

  public interface OptionalStep {
    OptionalStep local(final Boolean local);

    OptionalStep local(final Boolean local, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep operationReference(final Long operationReference);

    OptionalStep operationReference(
        final Long operationReference, final ContractPolicy.FieldPolicy<Long> policy);

    GeneratedSetVariableRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("SetVariableRequest", "variables");
    public static final ContractPolicy.FieldRef LOCAL =
        ContractPolicy.field("SetVariableRequest", "local");
    public static final ContractPolicy.FieldRef OPERATION_REFERENCE =
        ContractPolicy.field("SetVariableRequest", "operationReference");

    private Fields() {}
  }
}
