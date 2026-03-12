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

@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedGroupUserStrictContract(String username) {

  public GeneratedGroupUserStrictContract {
    Objects.requireNonNull(username, "username is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static UsernameStep builder() {
    return new Builder();
  }

  public static final class Builder implements UsernameStep, OptionalStep {
    private String username;
    private ContractPolicy.FieldPolicy<String> usernamePolicy;

    private Builder() {}

    @Override
    public OptionalStep username(
        final String username, final ContractPolicy.FieldPolicy<String> policy) {
      this.username = username;
      this.usernamePolicy = policy;
      return this;
    }

    @Override
    public GeneratedGroupUserStrictContract build() {
      return new GeneratedGroupUserStrictContract(
          applyRequiredPolicy(this.username, this.usernamePolicy, Fields.USERNAME));
    }
  }

  public interface UsernameStep {
    OptionalStep username(final String username, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    GeneratedGroupUserStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef USERNAME =
        ContractPolicy.field("GroupUserResult", "username");

    private Fields() {}
  }
}
