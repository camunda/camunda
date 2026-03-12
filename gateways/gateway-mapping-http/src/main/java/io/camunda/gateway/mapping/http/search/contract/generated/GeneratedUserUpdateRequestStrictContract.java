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
public record GeneratedUserUpdateRequestStrictContract(
    @Nullable String password, @Nullable String name, @Nullable String email) {

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
    private String password;
    private String name;
    private String email;

    private Builder() {}

    @Override
    public OptionalStep password(final String password) {
      this.password = password;
      return this;
    }

    @Override
    public OptionalStep password(
        final String password, final ContractPolicy.FieldPolicy<String> policy) {
      this.password = policy.apply(password, Fields.PASSWORD, null);
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
    public GeneratedUserUpdateRequestStrictContract build() {
      return new GeneratedUserUpdateRequestStrictContract(this.password, this.name, this.email);
    }
  }

  public interface OptionalStep {
    OptionalStep password(final String password);

    OptionalStep password(final String password, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep email(final String email);

    OptionalStep email(final String email, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedUserUpdateRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef PASSWORD =
        ContractPolicy.field("UserUpdateRequest", "password");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("UserUpdateRequest", "name");
    public static final ContractPolicy.FieldRef EMAIL =
        ContractPolicy.field("UserUpdateRequest", "email");

    private Fields() {}
  }
}
