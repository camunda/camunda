/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/user-tasks.yaml#/components/schemas/UserTaskCompletionRequest
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
public record GeneratedUserTaskCompletionRequestStrictContract(
    @JsonProperty("variables") java.util.@Nullable Map<String, Object> variables,
    @JsonProperty("action") @Nullable String action) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private java.util.Map<String, Object> variables;
    private String action;

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
    public OptionalStep action(final @Nullable String action) {
      this.action = action;
      return this;
    }

    @Override
    public OptionalStep action(
        final @Nullable String action, final ContractPolicy.FieldPolicy<String> policy) {
      this.action = policy.apply(action, Fields.ACTION, null);
      return this;
    }

    @Override
    public GeneratedUserTaskCompletionRequestStrictContract build() {
      return new GeneratedUserTaskCompletionRequestStrictContract(this.variables, this.action);
    }
  }

  public interface OptionalStep {
    OptionalStep variables(final java.util.@Nullable Map<String, Object> variables);

    OptionalStep variables(
        final java.util.@Nullable Map<String, Object> variables,
        final ContractPolicy.FieldPolicy<java.util.Map<String, Object>> policy);

    OptionalStep action(final @Nullable String action);

    OptionalStep action(
        final @Nullable String action, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedUserTaskCompletionRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef VARIABLES =
        ContractPolicy.field("UserTaskCompletionRequest", "variables");
    public static final ContractPolicy.FieldRef ACTION =
        ContractPolicy.field("UserTaskCompletionRequest", "action");

    private Fields() {}
  }
}
