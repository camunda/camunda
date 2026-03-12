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
public record GeneratedRoleCreateStrictContract(
    String roleId, String name, @Nullable String description) {

  public GeneratedRoleCreateStrictContract {
    Objects.requireNonNull(roleId, "roleId is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
  }

  private static <T> T applyRequiredPolicy(
      final T value,
      final ContractPolicy.FieldPolicy<T> policy,
      final ContractPolicy.FieldRef field) {
    return java.util.Objects.requireNonNull(policy, field.fieldName() + " policy must not be null")
        .apply(value, field, null);
  }

  public static RoleIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements RoleIdStep, NameStep, OptionalStep {
    private String roleId;
    private ContractPolicy.FieldPolicy<String> roleIdPolicy;
    private String name;
    private ContractPolicy.FieldPolicy<String> namePolicy;
    private String description;

    private Builder() {}

    @Override
    public NameStep roleId(final String roleId, final ContractPolicy.FieldPolicy<String> policy) {
      this.roleId = roleId;
      this.roleIdPolicy = policy;
      return this;
    }

    @Override
    public OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy) {
      this.name = name;
      this.namePolicy = policy;
      return this;
    }

    @Override
    public OptionalStep description(final String description) {
      this.description = description;
      return this;
    }

    @Override
    public OptionalStep description(
        final String description, final ContractPolicy.FieldPolicy<String> policy) {
      this.description = policy.apply(description, Fields.DESCRIPTION, null);
      return this;
    }

    @Override
    public GeneratedRoleCreateStrictContract build() {
      return new GeneratedRoleCreateStrictContract(
          applyRequiredPolicy(this.roleId, this.roleIdPolicy, Fields.ROLE_ID),
          applyRequiredPolicy(this.name, this.namePolicy, Fields.NAME),
          this.description);
    }
  }

  public interface RoleIdStep {
    NameStep roleId(final String roleId, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface NameStep {
    OptionalStep name(final String name, final ContractPolicy.FieldPolicy<String> policy);
  }

  public interface OptionalStep {
    OptionalStep description(final String description);

    OptionalStep description(
        final String description, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedRoleCreateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ROLE_ID =
        ContractPolicy.field("RoleCreateResult", "roleId");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("RoleCreateResult", "name");
    public static final ContractPolicy.FieldRef DESCRIPTION =
        ContractPolicy.field("RoleCreateResult", "description");

    private Fields() {}
  }
}
