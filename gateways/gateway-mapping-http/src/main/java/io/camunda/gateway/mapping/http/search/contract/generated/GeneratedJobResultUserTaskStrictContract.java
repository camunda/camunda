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
public record GeneratedJobResultUserTaskStrictContract(
    @Nullable Boolean denied,
    @Nullable String deniedReason,
    @Nullable GeneratedJobResultCorrectionsStrictContract corrections,
    @Nullable String type) {

  public static GeneratedJobResultCorrectionsStrictContract coerceCorrections(final Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof GeneratedJobResultCorrectionsStrictContract strictValue) {
      return strictValue;
    }

    throw new IllegalArgumentException(
        "corrections must be a GeneratedJobResultCorrectionsStrictContract, but was "
            + value.getClass().getName());
  }

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
    private Boolean denied;
    private String deniedReason;
    private Object corrections;
    private String type;

    private Builder() {}

    @Override
    public OptionalStep denied(final Boolean denied) {
      this.denied = denied;
      return this;
    }

    @Override
    public OptionalStep denied(
        final Boolean denied, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.denied = policy.apply(denied, Fields.DENIED, null);
      return this;
    }

    @Override
    public OptionalStep deniedReason(final String deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    @Override
    public OptionalStep deniedReason(
        final String deniedReason, final ContractPolicy.FieldPolicy<String> policy) {
      this.deniedReason = policy.apply(deniedReason, Fields.DENIED_REASON, null);
      return this;
    }

    @Override
    public OptionalStep corrections(final GeneratedJobResultCorrectionsStrictContract corrections) {
      this.corrections = corrections;
      return this;
    }

    @Override
    public OptionalStep corrections(final Object corrections) {
      this.corrections = corrections;
      return this;
    }

    public Builder corrections(
        final GeneratedJobResultCorrectionsStrictContract corrections,
        final ContractPolicy.FieldPolicy<GeneratedJobResultCorrectionsStrictContract> policy) {
      this.corrections = policy.apply(corrections, Fields.CORRECTIONS, null);
      return this;
    }

    @Override
    public OptionalStep corrections(
        final Object corrections, final ContractPolicy.FieldPolicy<Object> policy) {
      this.corrections = policy.apply(corrections, Fields.CORRECTIONS, null);
      return this;
    }

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
    public GeneratedJobResultUserTaskStrictContract build() {
      return new GeneratedJobResultUserTaskStrictContract(
          this.denied, this.deniedReason, coerceCorrections(this.corrections), this.type);
    }
  }

  public interface OptionalStep {
    OptionalStep denied(final Boolean denied);

    OptionalStep denied(final Boolean denied, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep deniedReason(final String deniedReason);

    OptionalStep deniedReason(
        final String deniedReason, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep corrections(final GeneratedJobResultCorrectionsStrictContract corrections);

    OptionalStep corrections(final Object corrections);

    OptionalStep corrections(
        final GeneratedJobResultCorrectionsStrictContract corrections,
        final ContractPolicy.FieldPolicy<GeneratedJobResultCorrectionsStrictContract> policy);

    OptionalStep corrections(
        final Object corrections, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep type(final String type);

    OptionalStep type(final String type, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedJobResultUserTaskStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef DENIED =
        ContractPolicy.field("JobResultUserTask", "denied");
    public static final ContractPolicy.FieldRef DENIED_REASON =
        ContractPolicy.field("JobResultUserTask", "deniedReason");
    public static final ContractPolicy.FieldRef CORRECTIONS =
        ContractPolicy.field("JobResultUserTask", "corrections");
    public static final ContractPolicy.FieldRef TYPE =
        ContractPolicy.field("JobResultUserTask", "type");

    private Fields() {}
  }
}
