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
public record GeneratedRoleCreateRequestStrictContract(
    String roleId, String name, @Nullable String description) {

  public GeneratedRoleCreateRequestStrictContract {
    Objects.requireNonNull(roleId, "roleId is required and must not be null");
    Objects.requireNonNull(name, "name is required and must not be null");
  }

  public static RoleIdStep builder() {
    return new Builder();
  }

  public static final class Builder implements RoleIdStep, NameStep, OptionalStep {
    private String roleId;
    private String name;
    private String description;

    private Builder() {}

    @Override
    public NameStep roleId(final String roleId) {
      this.roleId = roleId;
      return this;
    }

    @Override
    public OptionalStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep description(final @Nullable String description) {
      this.description = description;
      return this;
    }

    @Override
    public OptionalStep description(
        final @Nullable String description, final ContractPolicy.FieldPolicy<String> policy) {
      this.description = policy.apply(description, Fields.DESCRIPTION, null);
      return this;
    }

    @Override
    public GeneratedRoleCreateRequestStrictContract build() {
      return new GeneratedRoleCreateRequestStrictContract(this.roleId, this.name, this.description);
    }
  }

  public interface RoleIdStep {
    NameStep roleId(final String roleId);
  }

  public interface NameStep {
    OptionalStep name(final String name);
  }

  public interface OptionalStep {
    OptionalStep description(final @Nullable String description);

    OptionalStep description(
        final @Nullable String description, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedRoleCreateRequestStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef ROLE_ID =
        ContractPolicy.field("RoleCreateRequest", "roleId");
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("RoleCreateRequest", "name");
    public static final ContractPolicy.FieldRef DESCRIPTION =
        ContractPolicy.field("RoleCreateRequest", "description");

    private Fields() {}
  }
}
