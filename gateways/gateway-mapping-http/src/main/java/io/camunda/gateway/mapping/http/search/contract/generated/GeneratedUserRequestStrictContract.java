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
public record GeneratedUserRequestStrictContract(
    String password, String username, @Nullable String name, @Nullable String email) {

  public GeneratedUserRequestStrictContract {
    Objects.requireNonNull(password, "password is required and must not be null");
    Objects.requireNonNull(username, "username is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static PasswordStep builder() {
    return new Builder();
  }

  public static final class Builder implements PasswordStep, UsernameStep, OptionalStep {
    private String password;
    private ContractPolicy.FieldPolicy<String> passwordPolicy;
    private String username;
    private ContractPolicy.FieldPolicy<String> usernamePolicy;
    private String name;
    private String email;

    private Builder() {}

    @Override
    public UsernameStep password(
        final String password, final ContractPolicy.FieldPolicy<String> policy) {
      this.password = password;
      this.passwordPolicy = policy;
      return this;
    }

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
    public GeneratedUserRequestStrictContract build() {
      return new GeneratedUserRequestStrictContract(
          applyRequiredPolicy(this.password, this.passwordPolicy, Fields.PASSWORD),
          applyRequiredPolicy(this.username, this.usernamePolicy, Fields.USERNAME),
          this.name,
          this.email);
    }
  }

  public interface PasswordStep {
    UsernameStep password(final String password, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface UsernameStep {
    OptionalStep username(final String username, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep email(final String email);

    OptionalStep email(final String email, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedUserRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PASSWORD =
        ContractPolicy.field("UserRequest", "password");
    public static final ContractPolicy.FieldRef USERNAME =
        ContractPolicy.field("UserRequest", "username");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("UserRequest", "name");
    public static final ContractPolicy.FieldRef EMAIL =
        ContractPolicy.field("UserRequest", "email");

    private Fields() {}
  }
}
