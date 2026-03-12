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
public record GeneratedUserFilterStrictContract(
    @Nullable Object username, @Nullable Object name, @Nullable Object email) {

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
    private Object username;
    private Object name;
    private Object email;

    private Builder() {}

    @Override
    public OptionalStep username(final Object username) {
      this.username = username;
      return this;
    }

    @Override
    public OptionalStep username(
        final Object username, final ContractPolicy.FieldPolicy<Object> policy) {
      this.username = policy.apply(username, Fields.USERNAME, null);
      return this;
    }

    @Override
    public OptionalStep name(final Object name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy) {
      this.name = policy.apply(name, Fields.NAME, null);
      return this;
    }

    @Override
    public OptionalStep email(final Object email) {
      this.email = email;
      return this;
    }

    @Override
    public OptionalStep email(final Object email, final ContractPolicy.FieldPolicy<Object> policy) {
      this.email = policy.apply(email, Fields.EMAIL, null);
      return this;
    }

    @Override
    public GeneratedUserFilterStrictContract build() {
      return new GeneratedUserFilterStrictContract(this.username, this.name, this.email);
    }
  }

  public interface OptionalStep {
    OptionalStep username(final Object username);

    OptionalStep username(final Object username, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep name(final Object name);

    OptionalStep name(final Object name, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep email(final Object email);

    OptionalStep email(final Object email, final ContractPolicy.FieldPolicy<Object> policy);

    GeneratedUserFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef USERNAME =
        ContractPolicy.field("UserFilter", "username");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("UserFilter", "name");
    public static final ContractPolicy.FieldRef EMAIL = ContractPolicy.field("UserFilter", "email");

    private Fields() {}
  }
}
