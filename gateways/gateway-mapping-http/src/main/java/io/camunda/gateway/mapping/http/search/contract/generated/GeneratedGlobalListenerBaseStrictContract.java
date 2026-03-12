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
public record GeneratedGlobalListenerBaseStrictContract(
    @Nullable String type,
    @Nullable Integer retries,
    @Nullable Boolean afterNonGlobal,
    @Nullable Integer priority) {

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
    private String type;
    private Integer retries;
    private Boolean afterNonGlobal;
    private Integer priority;

    private Builder() {}

    @Override
    public OptionalStep type(final String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy) {
      this.type = policy.apply(type, Fields.TYPE, null);
      return this;
    }

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
    public OptionalStep afterNonGlobal(final Boolean afterNonGlobal) {
      this.afterNonGlobal = afterNonGlobal;
      return this;
    }

    @Override
    public OptionalStep afterNonGlobal(
        final Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.afterNonGlobal = policy.apply(afterNonGlobal, Fields.AFTER_NON_GLOBAL, null);
      return this;
    }

    @Override
    public OptionalStep priority(final Integer priority) {
      this.priority = priority;
      return this;
    }

    @Override
    public OptionalStep priority(
        final Integer priority, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.priority = policy.apply(priority, Fields.PRIORITY, null);
      return this;
    }

    @Override
    public GeneratedGlobalListenerBaseStrictContract build() {
      return new GeneratedGlobalListenerBaseStrictContract(
          this.type, this.retries, this.afterNonGlobal, this.priority);
    }
  }

  public interface OptionalStep {
    OptionalStep type(final String type);

    OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep retries(final Integer retries);

    OptionalStep retries(final Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep afterNonGlobal(final Boolean afterNonGlobal);

    OptionalStep afterNonGlobal(
        final Boolean afterNonGlobal, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep priority(final Integer priority);

    OptionalStep priority(final Integer priority, final ContractPolicy.FieldPolicy<Integer> policy);

    GeneratedGlobalListenerBaseStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("GlobalListenerBase", "type");
    public static final ContractPolicy.FieldRef RETRIES =
        ContractPolicy.field("GlobalListenerBase", "retries");
    public static final ContractPolicy.FieldRef AFTER_NON_GLOBAL =
        ContractPolicy.field("GlobalListenerBase", "afterNonGlobal");
    public static final ContractPolicy.FieldRef PRIORITY =
        ContractPolicy.field("GlobalListenerBase", "priority");

    private Fields() {}
  }
}
