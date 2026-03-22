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
public record GeneratedUserUpdateRequestStrictContract(
    @JsonProperty("password") @Nullable String password,
    @JsonProperty("name") @Nullable String name,
    @JsonProperty("email") @Nullable String email) {

  public static OptionalStep builder() {
    return new Builder();
  }

  public static final class Builder implements OptionalStep {
    private String password;
    private String name;
    private String email;

    private Builder() {}

    @Override
    public OptionalStep password(final @Nullable String password) {
      this.password = password;
      return this;
    }

    @Override
    public OptionalStep password(
        final @Nullable String password, final ContractPolicy.FieldPolicy<String> policy) {
      this.password = policy.apply(password, Fields.PASSWORD, null);
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
    public GeneratedUserUpdateRequestStrictContract build() {
      return new GeneratedUserUpdateRequestStrictContract(this.password, this.name, this.email);
    }
  }

  public interface OptionalStep {
    OptionalStep password(final @Nullable String password);

    OptionalStep password(
        final @Nullable String password, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep name(final @Nullable String name);

    OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);

    OptionalStep email(final @Nullable String email);

    OptionalStep email(
        final @Nullable String email, final ContractPolicy.FieldPolicy<String> policy);

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
