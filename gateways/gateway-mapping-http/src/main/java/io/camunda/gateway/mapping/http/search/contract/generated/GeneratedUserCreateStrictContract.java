/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/users.yaml#/components/schemas/UserCreateResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;


@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedUserCreateStrictContract(
    String username,
    @Nullable String name,
    @Nullable String email
) {

  public GeneratedUserCreateStrictContract {
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
    public OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }


    @Override
    public OptionalStep email(final @Nullable String email) {
      this.email = email;
      return this;
    }

    @Override
    public OptionalStep email(final @Nullable String email, final ContractPolicy.FieldPolicy<String> policy) {
      this.email = policy.apply(email, Fields.EMAIL, null);
      return this;
    }

    @Override
    public GeneratedUserCreateStrictContract build() {
      return new GeneratedUserCreateStrictContract(
          this.username,
          this.name,
          this.email);
    }
  }

  public interface UsernameStep {
    OptionalStep username(final String username);
  }

  public interface OptionalStep {
  OptionalStep name(final @Nullable String name);

  OptionalStep name(final @Nullable String name, final ContractPolicy.FieldPolicy<String> policy);


  OptionalStep email(final @Nullable String email);

  OptionalStep email(final @Nullable String email, final ContractPolicy.FieldPolicy<String> policy);


    GeneratedUserCreateStrictContract build();
  }


  public static final class Fields {
    public static final ContractPolicy.FieldRef USERNAME = ContractPolicy.field("UserCreateResult", "username");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("UserCreateResult", "name");
    public static final ContractPolicy.FieldRef EMAIL = ContractPolicy.field("UserCreateResult", "email");

    private Fields() {}
  }


}
