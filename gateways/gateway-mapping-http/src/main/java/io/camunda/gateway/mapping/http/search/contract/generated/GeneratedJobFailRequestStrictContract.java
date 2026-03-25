/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml#/components/schemas/JobFailRequest
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobFailRequestStrictContract(
    @JsonProperty("retries") @Nullable Integer retries,
    @JsonProperty("errorMessage") @Nullable String errorMessage,
    @JsonProperty("retryBackOff") @Nullable Long retryBackOff,
    @JsonProperty("variables") java.util.@Nullable Map<String, Object> variables) {

  public GeneratedJobFailRequestStrictContract {
    if (retries == null) retries = 0;
    if (retryBackOff == null) retryBackOff = 0L;
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
    public OptionalStep retries(final @Nullable Integer retries) {
      this.retries = retries;
      return this;
    }

    @Override
    public OptionalStep retries(
        final @Nullable Integer retries, final ContractPolicy.FieldPolicy<Integer> policy) {
      this.retries = policy.apply(retries, Fields.RETRIES, null);
      return this;
    }

    @Override
    public OptionalStep errorMessage(final @Nullable String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    @Override
    public OptionalStep errorMessage(
        final @Nullable String errorMessage, final ContractPolicy.FieldPolicy<String> policy) {
      this.errorMessage = policy.apply(errorMessage, Fields.ERROR_MESSAGE, null);
      return this;
    }

    @Override
    public OptionalStep retryBackOff(final @Nullable Long retryBackOff) {
      this.retryBackOff = retryBackOff;
      return this;
    }

    @Override
    public OptionalStep retryBackOff(
        final @Nullable Long retryBackOff, final ContractPolicy.FieldPolicy<Long> policy) {
      this.retryBackOff = policy.apply(retryBackOff, Fields.RETRY_BACK_OFF, null);
      return this;
    }

    @Override
    public OptionalStep variables(final java.util.@Nullable Map<String, Object> variables) {
      this.variables = variables;
      return this;
    }

    @Override
    public OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
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
    OptionalStep retries(final @Nullable Integer retries);

    OptionalStep retries(
        final @Nullable Integer retries, final ContractPolicy.FieldPolicy<Integer> policy);

    OptionalStep errorMessage(final @Nullable String errorMessage);

    OptionalStep errorMessage(
        final @Nullable String errorMessage, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep retryBackOff(final @Nullable Long retryBackOff);

    OptionalStep retryBackOff(
        final @Nullable Long retryBackOff, final ContractPolicy.FieldPolicy<Long> policy);

    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
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
