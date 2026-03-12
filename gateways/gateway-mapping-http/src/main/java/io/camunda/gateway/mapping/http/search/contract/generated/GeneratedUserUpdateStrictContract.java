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
public record GeneratedUserUpdateStrictContract(
    String username, @Nullable String name, @Nullable String email) {

  public GeneratedUserUpdateStrictContract {
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
    private String name;
    private String email;

    private Builder() {}

    @Override
    public OptionalStep username(
        final String username, final ContractPolicy.FieldPolicy<String> policy) {
      this.username = username;
      this.usernamePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep email(final String email) {
      this.email = email;
      return this;
    }

    @Override
    public OptionalStep email(final String email, final ContractPolicy.FieldPolicy<String> policy) {
      this.email = policy.apply(email, Fields.EMAIL, null);
      return this;
    }

    @Override
    public GeneratedUserUpdateStrictContract build() {
      return new GeneratedUserUpdateStrictContract(
          applyRequiredPolicy(this.username, this.usernamePolicy, Fields.USERNAME),
          this.name,
          this.email);
    }
  }

  public interface UsernameStep {
    OptionalStep username(final String username, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep email(final String email);

    OptionalStep email(final String email, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedUserUpdateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef USERNAME =
        ContractPolicy.field("UserUpdateResult", "username");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("UserUpdateResult", "name");
    public static final ContractPolicy.FieldRef EMAIL =
        ContractPolicy.field("UserUpdateResult", "email");

    private Fields() {}
  }
}
