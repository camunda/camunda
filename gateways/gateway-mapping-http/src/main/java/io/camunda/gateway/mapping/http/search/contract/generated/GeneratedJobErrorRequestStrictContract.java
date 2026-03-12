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
public record GeneratedJobErrorRequestStrictContract(
    String errorCode,
    @Nullable String errorMessage,
    @Nullable java.util.Map<String, Object> variables) {

  public GeneratedJobErrorRequestStrictContract {
    Objects.requireNonNull(errorCode, "errorCode is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static ErrorCodeStep builder() {
    return new Builder();
  }

  public static final class Builder implements ErrorCodeStep, OptionalStep {
    private String errorCode;
    private ContractPolicy.FieldPolicy<String> errorCodePolicy;
    private String errorMessage;
    private java.util.Map<String, Object> variables;

    private Builder() {}

    @Override
    public OptionalStep errorCode(
        final String errorCode, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorCode = errorCode;
      this.errorCodePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep errorMessage(final String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

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
    public GeneratedJobErrorRequestStrictContract build() {
      return new GeneratedJobErrorRequestStrictContract(
          applyRequiredPolicy(this.errorCode, this.errorCodePolicy, Fields.ERROR_CODE),
          this.errorMessage,
          this.variables);
    }
  }

  public interface ErrorCodeStep {
    OptionalStep errorCode(final String errorCode, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep errorMessage(final String errorMessage);

    OptionalStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep variables(final java.util.Map<String, Object> variables);

    OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    GeneratedJobErrorRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ERROR_CODE =
        ContractPolicy.field("JobErrorRequest", "errorCode");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("JobErrorRequest", "errorMessage");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("JobErrorRequest", "variables");

    private Fields() {}
  }
}
