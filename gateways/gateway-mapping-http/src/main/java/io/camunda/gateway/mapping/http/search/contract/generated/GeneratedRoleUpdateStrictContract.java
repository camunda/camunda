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
public record GeneratedRoleUpdateStrictContract(
    String name, @Nullable String description, String roleId) {

  public GeneratedRoleUpdateStrictContract {
    Objects.requireNonNull(name, "name is required and must not be null");
    Objects.requireNonNull(roleId, "roleId is required and must not be null");
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder implements NameStep, RoleIdStep, OptionalStep {
    private String name;
    private String description;
    private String roleId;

    private Builder() {}

    @Override
    public RoleIdStep name(final String name) {
      this.name = name;
      return this;
    }

    @Override
    public OptionalStep roleId(final String roleId) {
      this.roleId = roleId;
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
    public GeneratedRoleUpdateStrictContract build() {
      return new GeneratedRoleUpdateStrictContract(this.name, this.description, this.roleId);
    }
  }

  public interface NameStep {
    RoleIdStep name(final String name);
  }

  public interface RoleIdStep {
    OptionalStep roleId(final String roleId);
  }

  public interface OptionalStep {
    OptionalStep description(final @Nullable String description);

    OptionalStep description(
        final @Nullable String description, final ContractPolicy.FieldPolicy<String> policy);

    GeneratedRoleUpdateStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME =
        ContractPolicy.field("RoleUpdateResult", "name");
    public static final ContractPolicy.FieldRef DESCRIPTION =
        ContractPolicy.field("RoleUpdateResult", "description");
    public static final ContractPolicy.FieldRef ROLE_ID =
        ContractPolicy.field("RoleUpdateResult", "roleId");

    private Fields() {}
  }
}
