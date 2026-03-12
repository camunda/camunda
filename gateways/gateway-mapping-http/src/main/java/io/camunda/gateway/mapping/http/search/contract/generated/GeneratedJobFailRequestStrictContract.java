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
public record GeneratedJobFailRequestStrictContract(
    @Nullable Integer retries,
    @Nullable String errorMessage,
    @Nullable Long retryBackOff,
    @Nullable java.util.Map<String, Object> variables) {

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
    private Integer retries;
    private String errorMessage;
    private Long retryBackOff;
    private java.util.Map<String, Object> variables;

    private Builder() {}

    @Override
    public OptionalStep retries(final Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
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
    public OptionalStep retryBackOff(final Long retryBackOff) {
      this.retryBackOff = retryBackOff;
      return this;
    }

    @Override
    public OptionalStep retryBackOff(
        final Long retryBackOff, final ContractPolicy.FieldPolicy<Long> policy) {
      this.retryBackOff = policy.apply(retryBackOff, Fields.RETRY_BACK_OFF, null);
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
    public GeneratedJobFailRequestStrictContract build() {
      return new GeneratedJobFailRequestStrictContract(
          this.retries, this.errorMessage, this.retryBackOff, this.variables);
    }
  }

  public interface OptionalStep {
    OptionalStep retries(final Integer retries);

    OptionalStep retries(final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep errorMessage(final String errorMessage);

    OptionalStep errorMessage(
        final String errorMessage, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep retryBackOff(final Long retryBackOff);

    OptionalStep retryBackOff(
        final Long retryBackOff, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep variables(final java.util.Map<String, Object> variables);

    OptionalStep variables(
        final java.util.Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    GeneratedJobFailRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("JobFailRequest", "retries");
    public static final ContractPolicy.FieldRef ERROR_MESSAGE =
        ContractPolicy.field("JobFailRequest", "errorMessage");
    public static final ContractPolicy.FieldRef RETRY_BACK_OFF =
        ContractPolicy.field("JobFailRequest", "retryBackOff");
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("JobFailRequest", "variables");

    private Fields() {}
  }
}
