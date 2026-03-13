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
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserStrictContract(
    String username, @Nullable String name, @Nullable String email) {

  public GeneratedUserStrictContract {
    Objects.requireNonNull(username, "username is required and must not be null");
  }

  public static UsernameStep builder() {
    return new Builder();
  }

  public static final class Builder implements UsernameStep, OptionalStep {
    private String username;
    private String name;
    private String email;

    private Builder() {}

    @Override
    public OptionalStep username(final String username) {
      this.username = username;
      return this;
    }

    @Override
    public OptionalStep name(final @Nullable String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(
        final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep email(final @Nullable String email) {
      this.email = email;
      return this;
    }

    @Override
    public OptionalStep email(
        final @Nullable String email, final ContractPolicy.FieldPolicy<String> policy) {
      this.email = policy.apply(email, Fields.EMAIL, null);
      return this;
    }

    @Override
    public GeneratedUserStrictContract build() {
      return new GeneratedUserStrictContract(this.username, this.name, this.email);
    }
  }

  public interface UsernameStep {
    OptionalStep username(final String username);
  }

  public interface OptionalStep {
    OptionalStep name(final @Nullable String name);

    OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep email(final @Nullable String email);

    OptionalStep email(
        final @Nullable String email, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedUserStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef USERNAME =
        ContractPolicy.field("UserResult", "username");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("UserResult", "name");
    public static final ContractPolicy.FieldRef EMAIL = ContractPolicy.field("UserResult", "email");

    private Fields() {}
  }
}
