/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@JsonDeserialize(using = JsonDeserializer.None.class)
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobResultUserTaskStrictContract(
    @JsonProperty("denied") @Nullable Boolean denied,
    @JsonProperty("deniedReason") @Nullable String deniedReason,
    @JsonProperty("corrections") @Nullable GeneratedJobResultCorrectionsStrictContract corrections,
    @JsonProperty("type") @Nullable String type)
    implements GeneratedJobStrictContract {

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
    public OptionalStep denied(final @Nullable Boolean denied) {
      this.denied = denied;
      return this;
    }

    @Override
    public OptionalStep denied(
        final @Nullable Boolean denied, final ContractPolicy.FieldPolicy<Boolean> policy) {
      this.denied = policy.apply(denied, Fields.DENIED, null);
      return this;
    }

    @Override
    public OptionalStep deniedReason(final @Nullable String deniedReason) {
      this.deniedReason = deniedReason;
      return this;
    }

    @Override
    public OptionalStep deniedReason(
        final @Nullable String deniedReason, final ContractPolicy.FieldPolicy<String> policy) {
      this.deniedReason = policy.apply(deniedReason, Fields.DENIED_REASON, null);
      return this;
    }

    @Override
    public OptionalStep corrections(
        final @Nullable GeneratedJobResultCorrectionsStrictContract corrections) {
      this.corrections = corrections;
      return this;
    }

    @Override
    public OptionalStep corrections(final @Nullable Object corrections) {
      this.corrections = corrections;
      return this;
    }

    public Builder corrections(
        final @Nullable GeneratedJobResultCorrectionsStrictContract corrections,
        final ContractPolicy.FieldPolicy<GeneratedJobResultCorrectionsStrictContract> policy) {
      this.corrections = policy.apply(corrections, Fields.CORRECTIONS, null);
      return this;
    }

    @Override
    public OptionalStep corrections(
        final @Nullable Object corrections, final ContractPolicy.FieldPolicy<Object> policy) {
      this.corrections = policy.apply(corrections, Fields.CORRECTIONS, null);
      return this;
    }

    @Override
    public OptionalStep type(final @Nullable String type) {
      this.type = type;
      return this;
    }

    @Override
    public OptionalStep type(
        final @Nullable String type, final ContractPolicy.FieldPolicy<String> policy) {
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
    OptionalStep denied(final @Nullable Boolean denied);

    OptionalStep denied(
        final @Nullable Boolean denied, final ContractPolicy.FieldPolicy<Boolean> policy);

    OptionalStep deniedReason(final @Nullable String deniedReason);

    OptionalStep deniedReason(
        final @Nullable String deniedReason, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep corrections(
        final @Nullable GeneratedJobResultCorrectionsStrictContract corrections);

    OptionalStep corrections(final @Nullable Object corrections);

    OptionalStep corrections(
        final @Nullable GeneratedJobResultCorrectionsStrictContract corrections,
        final ContractPolicy.FieldPolicy<GeneratedJobResultCorrectionsStrictContract> policy);

    OptionalStep corrections(
        final @Nullable Object corrections, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep type(final @Nullable String type);

    OptionalStep type(final @Nullable String type, final ContractPolicy.FieldPolicy<String> policy);

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
