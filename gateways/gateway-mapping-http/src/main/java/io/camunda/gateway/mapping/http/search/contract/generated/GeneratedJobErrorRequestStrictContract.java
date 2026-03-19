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
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobErrorRequestStrictContract(
    @JsonProperty("errorCode") String errorCode,
    @JsonProperty("errorMessage") @Nullable String errorMessage,
    @JsonProperty("variables") java.util.@Nullable Map<String, Object> variables) {

  public GeneratedJobErrorRequestStrictContract {
    Objects.requireNonNull(errorCode, "No errorCode provided.");
  }

  public static ErrorCodeStep builder() {
    return new Builder();
  }

  public static final class Builder implements ErrorCodeStep, OptionalStep {
    private String errorCode;
    private String errorMessage;
    private java.util.Map<String, Object> variables;

    private Builder() {}

    @Override
    public OptionalStep errorCode(final String errorCode) {
      this.errorCode = errorCode;
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
    public GeneratedJobErrorRequestStrictContract build() {
      return new GeneratedJobErrorRequestStrictContract(
          this.errorCode, this.errorMessage, this.variables);
    }
  }

  public interface ErrorCodeStep {
    OptionalStep errorCode(final String errorCode);
  }

  public interface OptionalStep {
    OptionalStep errorMessage(final @Nullable String errorMessage);

    OptionalStep errorMessage(
        final @Nullable String errorMessage, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
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
