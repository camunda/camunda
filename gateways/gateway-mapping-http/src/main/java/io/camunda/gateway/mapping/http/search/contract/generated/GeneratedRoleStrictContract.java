/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 *
 * GENERATED FILE - DO NOT EDIT.
 * Source: zeebe/gateway-protocol/src/main/proto/v2/roles.yaml#/components/schemas/RoleResult
 */
package io.camunda.gateway.mapping.http.search.contract.generated;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.gateway.mapping.http.search.contract.policy.ContractPolicy;
import jakarta.annotation.Generated;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonInclude(JsonInclude.Include.ALWAYS)
@NullMarked
@Generated(value = "io.camunda.gateway.mapping.http.tools.GenerateContractMappingPoc")
public record GeneratedRoleStrictContract(
    @JsonProperty("name") String name,
    @JsonProperty("roleId") String roleId,
    @JsonProperty("description") @Nullable String description) {

  public GeneratedRoleStrictContract {
    Objects.requireNonNull(name, "No name provided.");
    Objects.requireNonNull(roleId, "No roleId provided.");
  }

  public static NameStep builder() {
    return new Builder();
  }

  public static final class Builder implements NameStep, RoleIdStep, OptionalStep {
    private String name;
    private String roleId;
    private String description;

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
    public GeneratedRoleStrictContract build() {
      return new GeneratedRoleStrictContract(this.name, this.roleId, this.description);
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

    GeneratedRoleStrictContract build();
  }

  public static final class Fields {
    public static final ContractPolicy.FieldRef NAME = ContractPolicy.field("RoleResult", "name");
    public static final ContractPolicy.FieldRef ROLE_ID =
        ContractPolicy.field("RoleResult", "roleId");
    public static final ContractPolicy.FieldRef DESCRIPTION =
        ContractPolicy.field("RoleResult", "description");

    private Fields() {}
  }
}
