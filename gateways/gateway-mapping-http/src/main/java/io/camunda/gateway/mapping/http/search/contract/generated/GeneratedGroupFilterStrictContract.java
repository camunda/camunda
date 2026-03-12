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
public record GeneratedGroupFilterStrictContract(@Nullable Object groupId, @Nullable String name) {

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
    private Object groupId;
    private String name;

    private Builder() {}

    @Override
    public OptionalStep groupId(final Object groupId) {
      this.groupId = groupId;
      return this;
    }

    @Override
    public OptionalStep groupId(
        final Object groupId, final ContractPolicy.FieldPolicy<Object> policy) {
      this.groupId = policy.apply(groupId, Fields.GROUP_ID, null);
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
    public GeneratedGroupFilterStrictContract build() {
      return new GeneratedGroupFilterStrictContract(this.groupId, this.name);
    }
  }

  public interface OptionalStep {
    OptionalStep groupId(final Object groupId);

    OptionalStep groupId(final Object groupId, final ContractPolicy.FieldPolicy<Object> policy);

    OptionalStep name(final String name);

    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedGroupFilterStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef GROUP_ID =
        ContractPolicy.field("GroupFilter", "groupId");
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("GroupFilter", "name");

    private Fields() {}
  }
}
