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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedJobCompletionRequestStrictContract(
    @JsonProperty("variables") java.util.@Nullable Map<String, Object> variables,
    @JsonProperty("result") @Nullable Object result) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private java.util.Map<String, Object> variables;
    private Object result;

    private Builder() {}

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
    public OptionalStep result(final @Nullable Object result) {
      this.result = result;
      return this;
    }

    @Override
    public OptionalStep result(
        final @Nullable Object result, final ContractPolicy.FieldPolicy<Object> policy) {
      this.result = policy.apply(result, Fields.RESULT, null);
      return this;
    }

    @Override
    public GeneratedJobCompletionRequestStrictContract build() {
      return new GeneratedJobCompletionRequestStrictContract(this.variables, this.result);
    }
  }

  public interface OptionalStep {
    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep result(final @Nullable Object result);

    OptionalStep result(
        final @Nullable Object result, final ContractPolicy.FieldPolicy<Object> policy);

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
